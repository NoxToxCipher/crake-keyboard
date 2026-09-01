/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.ime.clipboard

import android.content.ClipData
import android.content.Context
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDao
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardHistoryDatabase
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import java.io.Closeable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.florisboard.lib.android.AndroidClipboardManager
import org.florisboard.lib.android.AndroidClipboardManager_OnPrimaryClipChangedListener
import org.florisboard.lib.android.clearPrimaryClipAnyApi
import org.florisboard.lib.android.setOrClearPrimaryClip
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.libnative.FlorisNative
import org.florisboard.lib.android.systemService
import org.florisboard.lib.kotlin.tryOrNull

/**
 * [ClipboardManager] manages the clipboard and clipboard history.
 *
 * Also just going to document how all the classes here work.
 *
 * [ClipboardManager] handles storage and retrieval of clipboard items. All manipulation of the
 * clipboard goes through here.
 */
class ClipboardManager(
    context: Context,
) : AndroidClipboardManager_OnPrimaryClipChangedListener, Closeable {
    companion object {
        // Ephemeral Auto-Destruct polling interval (2 seconds high resolution)
        private const val INTERVAL = 2 * 1000L

        /**
         * Helper to compare two MIME types, where one may be a pattern.
         * Decided by the native clipboard policy engine (AOSP semantics).
         * @param concreteType A fully-specified MIME type.
         * @param desiredType A desired MIME type that may be a pattern such as * / *.
         * @return Returns true if the two MIME types match.
         */
        fun compareMimeTypes(concreteType: String, desiredType: String): Boolean {
            return FlorisNative.clipboardCompareMimeTypes(concreteType, desiredType)
        }
    }

    private val prefs by FlorisPreferenceStore
    private val appContext by context.appContext()
    private val editorInstance by context.editorInstance()
    private val systemClipboardManager = context.systemService(AndroidClipboardManager::class)

    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanUpJob: Job
    private var clipHistoryDb: ClipboardHistoryDatabase? = null
    private val clipHistoryDao: ClipboardHistoryDao? get() = clipHistoryDb?.clipboardItemDao()

    val historyFlow: StateFlow<ClipboardHistory>
        field = MutableStateFlow(ClipboardHistory.EMPTY)
    val currentHistory: ClipboardHistory
        get() = historyFlow.value

    private val primaryClipLastFromCallbackGuard = Mutex(locked = false)
    private var primaryClipLastFromCallback: ClipData? = null
    val primaryClipFlow: StateFlow<ClipboardItem?>
        field = MutableStateFlow(null)
    inline var primaryClip
        get() = primaryClipFlow.value
        private set(v) {
            primaryClipFlow.value = v
        }

    init {
        systemClipboardManager.addPrimaryClipChangedListener(this)
        cleanUpJob = ioScope.launch {
            while (isActive) {
                delay(INTERVAL)
                enforceExpiryDate(currentHistory)
            }
        }
    }

    fun initializeForContext(context: Context) {
        ioScope.launch {
            if (clipHistoryDb == null) {
                clipHistoryDb = ClipboardHistoryDatabase.new(context.applicationContext)
                withContext(Dispatchers.Main) {
                    clipHistoryDao?.getAllAsFlow()?.collect { items ->
                        updateHistory(items)
                    }
                }
            }
        }
    }

    private fun updateHistory(items: List<ClipboardItem>) {
        val itemsSorted = items.sortedByDescending { it.creationTimestampMs }
        val clipHistory = ClipboardHistory(itemsSorted)
        enforceHistoryLimit(clipHistory)
        historyFlow.value = clipHistory
    }

    /**
     * Sets the current primary clip without updating the internal clipboard history.
     */
    fun updatePrimaryClip(item: ClipboardItem?) {
        primaryClip = item
        if (prefs.clipboard.useInternalClipboard.get()) {
            val syncBehavior = prefs.clipboard.syncToSystem.get()
            val clipData = item?.toClipData(appContext)
            if (clipData != null && syncBehavior.shouldSyncSet) {
                systemClipboardManager.setPrimaryClip(clipData)
            } else if (clipData == null && syncBehavior.shouldSyncClear) {
                systemClipboardManager.clearPrimaryClipAnyApi()
            }
        } else {
            systemClipboardManager.setOrClearPrimaryClip(item?.toClipData(appContext))
        }
    }

    /**
     * Called by system clipboard when the system primary clip has changed.
     */
    override fun onPrimaryClipChanged() {
        val syncBehavior = prefs.clipboard.syncToFloris.get()
        if (!prefs.clipboard.useInternalClipboard.get() || syncBehavior != ClipboardSyncBehavior.NO_EVENTS) {
            val systemPrimaryClip = systemClipboardManager.primaryClip
            ioScope.launch {
                val isDuplicate: Boolean
                primaryClipLastFromCallbackGuard.withLock {
                    val a = primaryClipLastFromCallback?.getItemAt(0)
                    val b = systemPrimaryClip?.getItemAt(0)
                    isDuplicate = when {
                        a === b -> true
                        a == null || b == null -> false
                        else -> a.text == b.text && a.uri == b.uri
                    }
                    primaryClipLastFromCallback = systemPrimaryClip
                }
                if (isDuplicate) return@launch

                val internalPrimaryClip = primaryClip

                if (systemPrimaryClip == null) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (systemPrimaryClip.getItemAt(0).let { it.text == null && it.uri == null }) {
                    if (syncBehavior.shouldSyncClear) {
                        primaryClip = null
                    }
                    return@launch
                }

                if (!syncBehavior.shouldSyncSet) {
                    return@launch
                }

                val isEqual = internalPrimaryClip?.isEqualTo(systemPrimaryClip) == true
                if (!isEqual) {
                    var item = ClipboardItem.fromClipData(appContext, systemPrimaryClip, cloneUri = true)
                    val clipText = item.text
                    if (item.type == ItemType.TEXT && clipText != null) {
                        val processed = FlorisNative.clipboardProcessText(clipText)
                        item = item.copy(
                            text = processed.cleanedText,
                            isSensitive = item.isSensitive || processed.isSensitive,
                        )
                    }
                    primaryClip = item
                    insertOrMoveBeginning(item)
                }
            }
        }
    }

    /**
     * Change the current text on clipboard, update history (if enabled).
     */
    private fun addNewClip(item: ClipboardItem) {
        insertOrMoveBeginning(item)
        updatePrimaryClip(item)
    }

    /**
     * Wraps some plaintext in a ClipData and calls [addNewClip]
     */
    fun addNewPlaintext(newText: String) {
        val processed = FlorisNative.clipboardProcessText(newText)
        val newData = ClipboardItem.text(processed.cleanedText).copy(isSensitive = processed.isSensitive)
        addNewClip(newData)
    }

    /**
     * Adds a new item to the clipboard history (if enabled).
     */
    fun moveToTop(item: ClipboardItem) {
        ioScope.launch {
            val updated = item.copy(
                isPinned = true,
                creationTimestampMs = System.currentTimeMillis()
            )
            clipHistoryDao?.update(updated)
        }
    }

    /**
     * Adds a new item to the clipboard history (if enabled) with robust deduplication.
     */
    private fun insertOrMoveBeginning(newItem: ClipboardItem) {
        if (prefs.clipboard.historyEnabled.get()) {
            val history = currentHistory.all
            val duplicateIndex = FlorisNative.clipboardFindDuplicate(
                kinds = IntArray(history.size) { history[it].type.value },
                contents = Array(history.size) { history[it].dedupContent() },
                newKind = newItem.type.value,
                newContent = newItem.dedupContent(),
            )
            val historyElement = history.getOrNull(duplicateIndex)
            if (historyElement != null) {
                moveToTheBeginning(
                    oldItem = historyElement,
                    newItem = if (historyElement.isPinned) {
                        newItem.copy(isPinned = true, id = historyElement.id)
                    } else {
                        newItem.copy(id = historyElement.id)
                    }
                )
            } else {
                insertClip(newItem)
            }
        }
    }

    private fun enforceHistoryLimit(clipHistory: ClipboardHistory) {
        if (prefs.clipboard.historySizeLimitEnabled.get()) {
            val removedIds = retentionSweep(
                clipHistory.all,
                limitEnabled = true,
                maxUnpinned = prefs.clipboard.historySizeLimit.get(),
                expiryEnabled = false,
                expiryAfterMs = 0,
                sensitiveEnabled = false,
                sensitiveAfterMs = 0,
            )
            removeItemsAndBackingMedia(clipHistory.all.filter { it.id in removedIds })
        }
    }

    /**
     * Removes the given history items: clears the primary clip if it is one
     * of them, deletes each item's backing media file, then deletes the rows.
     * Every path that removes rows outside the user's direct control must go
     * through here so media files are never orphaned.
     */
    private fun removeItemsAndBackingMedia(itemsToRemove: Collection<ClipboardItem>) {
        if (itemsToRemove.isEmpty()) return
        val currentPrimary = primaryClip
        if (currentPrimary != null && itemsToRemove.any { it.id == currentPrimary.id || (it.text == currentPrimary.text && it.type == currentPrimary.type) }) {
            updatePrimaryClip(null)
            systemClipboardManager.clearPrimaryClipAnyApi()
        }
        ioScope.launch {
            for (item in itemsToRemove) {
                item.close(appContext)
            }
            clipHistoryDao?.delete(itemsToRemove.toList())
        }
    }

    private fun enforceExpiryDate(clipHistory: ClipboardHistory) {
        val autoCleanOld = prefs.clipboard.historyAutoCleanOldEnabled.get()
        val autoCleanSensitive = prefs.clipboard.historyAutoCleanSensitiveEnabled.get()
        if (!autoCleanOld && !autoCleanSensitive) return
        val itemsToRemove: Set<ClipboardItem> = if (FlorisNative.isAvailable()) {
            val removedIds = retentionSweep(
                clipHistory.all,
                limitEnabled = false,
                maxUnpinned = 0,
                expiryEnabled = autoCleanOld,
                expiryAfterMs = prefs.clipboard.historyAutoCleanOldAfter.get() * 60_000L,
                sensitiveEnabled = autoCleanSensitive,
                sensitiveAfterMs = prefs.clipboard.historyAutoCleanSensitiveAfter.get() * 1_000L,
            )
            clipHistory.all.filter { it.id in removedIds }.toSet()
        } else if (autoCleanSensitive) {
            // Fail safe without the native policy engine: over-delete
            // sensitive clips rather than let them linger past their TTL.
            clipHistory.all.filter { it.isSensitive }.toSet()
        } else {
            emptySet()
        }
        removeItemsAndBackingMedia(itemsToRemove)
    }

    /**
     * Asks the native clipboard policy engine which history clips the given
     * retention rules say to remove, returning their ids.
     */
    private fun retentionSweep(
        history: List<ClipboardItem>,
        limitEnabled: Boolean,
        maxUnpinned: Int,
        expiryEnabled: Boolean,
        expiryAfterMs: Long,
        sensitiveEnabled: Boolean,
        sensitiveAfterMs: Long,
    ): LongArray {
        return FlorisNative.clipboardRetentionSweep(
            ids = LongArray(history.size) { history[it].id },
            flags = IntArray(history.size) { history[it].metaFlags() },
            createdMs = LongArray(history.size) { history[it].creationTimestampMs },
            nowMs = System.currentTimeMillis(),
            limitEnabled = limitEnabled,
            maxUnpinned = maxUnpinned,
            expiryEnabled = expiryEnabled,
            expiryAfterMs = expiryAfterMs,
            sensitiveEnabled = sensitiveEnabled,
            sensitiveAfterMs = sensitiveAfterMs,
        )
    }

    private fun ClipboardItem.metaFlags(): Int {
        return (if (isPinned) 1 else 0) or (if (isSensitive) 2 else 0)
    }

    private fun ClipboardItem.dedupContent(): String {
        return if (type == ItemType.TEXT) text ?: "" else uri?.toString() ?: ""
    }

    private fun moveToTheBeginning(oldItem: ClipboardItem, newItem: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.delete(oldItem.id)
            clipHistoryDao?.insert(newItem)
        }
    }

    fun insertClip(item: ClipboardItem) {
        ioScope.launch {
            val id = clipHistoryDao?.insert(item)
            item.id = id ?: 0
        }
    }

    fun clearExactHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            for (item in items) {
                item.close(appContext)
            }
            clipHistoryDao?.delete(items)
        }
    }

    /**
     * Clears all unpinned items from the clipboard history
     */
    fun clearHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                // Only unpinned rows are deleted below — closing a pinned
                // item here would orphan its row from its backing media file.
                if (!item.isPinned) {
                    item.close(appContext)
                }
            }
            clipHistoryDao?.deleteAllUnpinned()
        }
    }

    /**
     * Clears the full clipboard history
     */
    fun clearFullHistory() {
        ioScope.launch {
            for (item in currentHistory.all) {
                item.close(appContext)
            }
            clipHistoryDao?.deleteAll()
        }
    }


    /**
     * Restore the clipboard history from a [List]
     *
     * @param items the [ClipboardItem] list with the new items
     */
    fun restoreHistory(items: List<ClipboardItem>) {
        ioScope.launch {
            val currentHistory = currentHistory.all
            for (item in items) {
                if (!currentHistory.map { it.copy(id = 0) }.contains(item.copy(id = 0))) {
                    insertClip(item.copy(id = 0))
                }
            }
        }
    }

    fun deleteClip(item: ClipboardItem, onlyIfUnpinned: Boolean) {
        ioScope.launch {
            val rowsDeleted = if (onlyIfUnpinned) {
                clipHistoryDao?.deleteIfUnpinned(item.id) ?: 0
            } else {
                clipHistoryDao?.delete(item.id) ?: 0
            }
            // Delete the backing media file only when a row was actually
            // removed — a surviving row (e.g. it got pinned) must keep its
            // media file.
            if (rowsDeleted > 0) {
                tryOrNull {
                    val uri = item.uri
                    if (uri != null) {
                        appContext.contentResolver.delete(uri, null, null)
                    }
                }
            }
        }
    }

    fun pinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = true))
        }
    }

    fun unpinClip(item: ClipboardItem) {
        ioScope.launch {
            clipHistoryDao?.update(item.copy(isPinned = false))
        }
    }

    fun pasteItem(item: ClipboardItem) {
        val editorInstance by appContext.editorInstance()
        editorInstance.commitClipboardItem(item).also { result ->
            if (!result) {
                appContext.showShortToastSync("Failed to paste item.")
            }
        }
    }

    /**
     * Returns true if the editor can accept the clip item, else false.
     */
    fun canBePasted(clipItem: ClipboardItem?): Boolean {
        if (clipItem == null) return false

        return clipItem.mimeTypes.contains("text/plain") || editorInstance.activeInfo.contentMimeTypes.any { editorType ->
            clipItem.mimeTypes.any { clipType ->
                compareMimeTypes(clipType, editorType)
            }
        }
    }

    /**
     * Cleans up.
     *
     * Unregisters the system clipboard listener, cancels clipboard clean ups.
     */
    override fun close() {
        systemClipboardManager.removePrimaryClipChangedListener(this)
        cleanUpJob.cancel()
    }
}
