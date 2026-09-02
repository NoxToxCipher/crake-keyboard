/*
 * Copyright (C) 2021-2026 The Crake Contributors
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

package dev.patrickgold.florisboard.ime.dictionary

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.HandlerThread
import android.provider.UserDictionary
import androidx.room.Room
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.FlorisLocale
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DictionaryManager private constructor(context: Context) {
    private val applicationContext: WeakReference<Context> = WeakReference(context.applicationContext ?: context)
    private val prefs by FlorisPreferenceStore

    private var florisUserDictionaryDatabase: FlorisUserDictionaryDatabase? = null
    private var systemUserDictionaryDatabase: SystemUserDictionaryDatabase? = null

    private val userDictionaryCache = UserDictionaryCache()
    @Volatile
    private var isCacheLoaded = false

    // System user-dictionary shortcut cache. The framework UserDictionary lives
    // behind a ContentProvider, so queryShortcut() was a cross-process IPC on
    // every keystroke. Snapshot ALL system entries once (exact-shortcut buckets,
    // each in the queryAll FREQUENCY-DESC order) and serve keystrokes from RAM;
    // a ContentObserver on UserDictionary.Words.CONTENT_URI re-warms the snapshot
    // whenever the system dictionary changes, so results stay consistent.
    // null = not yet warmed. Keyed by the EXACT (case-sensitive) shortcut string,
    // matching the provider's `SHORTCUT = ?` selection.
    @Volatile
    private var systemShortcutIndex: Map<String, List<UserDictionaryEntry>>? = null
    private var systemDictObserver: ContentObserver? = null
    private var systemDictObserverThread: HandlerThread? = null

    companion object {
        private var defaultInstance: DictionaryManager? = null

        fun init(applicationContext: Context): DictionaryManager {
            val instance = DictionaryManager(applicationContext)
            defaultInstance = instance
            instance.loadUserDictionariesIfNecessary()
            return instance
        }

        fun default(): DictionaryManager {
            val instance = defaultInstance
            if (instance != null) {
                return instance
            } else {
                throw UninitializedPropertyAccessException(
                    "${DictionaryManager::class.simpleName} has not been initialized previously. Make sure to call init(applicationContext) before using default()."
                )
            }
        }
    }

    fun queryUserDictionary(word: String, locale: FlorisLocale? = null): List<SuggestionCandidate> {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Dynamic Timestamp / Date Macros (Instant, in-memory, 0 allocations)
        val macroCandidates = UserDictionaryCache.evaluateMacros(trimmed)
        if (macroCandidates.isNotEmpty()) {
            return macroCandidates
        }

        // 2. Ensure in-memory cache is warmed
        if (!isCacheLoaded) {
            warmUserDictionaryCache()
        }

        // 3. Ultra-fast in-memory index query (0 SQLite disk I/O)
        val cachedShortcuts = userDictionaryCache.queryShortcuts(trimmed, locale)

        // 4. Optional System User Dictionary (if enabled in settings) — served
        //    from the in-memory snapshot instead of a per-keystroke ContentResolver IPC.
        val systemCandidates = if (prefs.dictionary.enableSystemUserDictionary.get()) {
            querySystemShortcutCached(trimmed, locale)
        } else {
            emptyList()
        }

        return cachedShortcuts + systemCandidates
    }

    @Synchronized
    fun warmUserDictionaryCache() {
        try {
            val dao = florisUserDictionaryDao()
            val entries = dao?.queryAll() ?: emptyList()
            userDictionaryCache.updateEntries(entries)
            isCacheLoaded = true
        } catch (e: Exception) {
            // Keep safe
        }
    }

    fun invalidateUserDictionaryCache() {
        warmUserDictionaryCache()
    }

    /**
     * Serves system user-dictionary shortcut hits from the in-memory snapshot,
     * byte-for-byte matching the old `systemUserDictionaryDao().queryShortcut(
     * shortcut, locale)` path: exact (case-sensitive) shortcut match, then the
     * provider's locale predicate — `LOCALE IS NULL` when [locale] is null, else
     * `LOCALE = localeTag OR LOCALE = language OR LOCALE IS NULL` — preserving the
     * FREQUENCY-DESC order captured by queryAll. Warms + starts observing on first
     * use so a runtime toggle of the setting still gets a live cache.
     */
    private fun querySystemShortcutCached(shortcut: String, locale: FlorisLocale?): List<SuggestionCandidate> {
        var index = systemShortcutIndex
        if (index == null) {
            ensureSystemUserDictionaryObserver()
            warmSystemUserDictionaryCache()
            index = systemShortcutIndex ?: return emptyList()
        }
        val bucket = index[shortcut] ?: return emptyList()
        return bucket
            .filter { entry ->
                val loc = entry.locale
                if (locale == null) {
                    loc == null
                } else {
                    loc == null || loc == locale.localeTag() || loc == locale.language
                }
            }
            .map { entry ->
                WordSuggestionCandidate(
                    text = entry.word,
                    secondaryText = "Snippet",
                    confidence = 1.0,
                    isEligibleForAutoCommit = true,
                )
            }
    }

    /**
     * Rebuilds the system-shortcut snapshot from a single queryAll IPC. Entries
     * with a null/blank shortcut are dropped (they can never match `SHORTCUT = ?`);
     * the rest bucket by their exact shortcut, each bucket keeping queryAll's
     * FREQUENCY-DESC order. Any failure leaves an empty (never null) index so the
     * hot path stays IPC-free.
     */
    @Synchronized
    fun warmSystemUserDictionaryCache() {
        try {
            if (!prefs.dictionary.enableSystemUserDictionary.get()) {
                systemShortcutIndex = emptyMap()
                return
            }
            val entries = systemUserDictionaryDao()?.queryAll() ?: emptyList()
            val newIndex = HashMap<String, MutableList<UserDictionaryEntry>>()
            for (entry in entries) {
                val shortcut = entry.shortcut
                if (!shortcut.isNullOrEmpty()) {
                    newIndex.getOrPut(shortcut) { mutableListOf() }.add(entry)
                }
            }
            systemShortcutIndex = newIndex
        } catch (e: Exception) {
            // Keep safe: an empty snapshot simply yields no system shortcuts,
            // exactly as a failed IPC did before.
            systemShortcutIndex = emptyMap()
        }
    }

    /**
     * Registers (once) a ContentObserver on UserDictionary.Words.CONTENT_URI that
     * re-warms the snapshot off the main thread whenever the system dictionary
     * changes. Idempotent; safe to call from any thread.
     */
    @Synchronized
    private fun ensureSystemUserDictionaryObserver() {
        if (systemDictObserver != null) return
        val context = applicationContext.get() ?: return
        val thread = HandlerThread("SystemUserDictObserver").apply { start() }
        val observer = object : ContentObserver(Handler(thread.looper)) {
            override fun onChange(selfChange: Boolean) {
                warmSystemUserDictionaryCache()
            }
        }
        try {
            context.contentResolver.registerContentObserver(
                UserDictionary.Words.CONTENT_URI,
                true,
                observer,
            )
            systemDictObserver = observer
            systemDictObserverThread = thread
        } catch (e: Exception) {
            thread.quitSafely()
        }
    }

    @Synchronized
    private fun unregisterSystemUserDictionaryObserver() {
        systemDictObserver?.let { observer ->
            try {
                applicationContext.get()?.contentResolver?.unregisterContentObserver(observer)
            } catch (e: Exception) {
                // ignore
            }
        }
        systemDictObserver = null
        systemDictObserverThread?.quitSafely()
        systemDictObserverThread = null
        systemShortcutIndex = null
    }

    fun spell(word: String, locale: FlorisLocale): Boolean {
        val florisDao = florisUserDictionaryDao()
        val systemDao = systemUserDictionaryDao()
        if (florisDao == null && systemDao == null) {
            return false
        }
        var ret = false
        ret = ret || (florisDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false)
        ret = ret || (florisDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false)
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ret = ret || (systemDao?.queryExactFuzzyLocale(word, locale)?.isNotEmpty() ?: false)
            ret = ret || (systemDao?.queryShortcut(word, locale)?.isNotEmpty() ?: false)
        }
        return ret
    }

    @Synchronized
    fun florisUserDictionaryDao(): UserDictionaryDao? {
        if (florisUserDictionaryDatabase == null) {
            val context = applicationContext.get() ?: return null
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        return florisUserDictionaryDatabase?.userDictionaryDao()
    }

    @Synchronized
    fun florisUserDictionaryDatabase(): FlorisUserDictionaryDatabase? {
        if (florisUserDictionaryDatabase == null) {
            florisUserDictionaryDao()
        }
        return florisUserDictionaryDatabase
    }

    @Synchronized
    fun systemUserDictionaryDao(): UserDictionaryDao? {
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            if (systemUserDictionaryDatabase == null) {
                val context = applicationContext.get() ?: return null
                systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
            }
            return systemUserDictionaryDatabase?.userDictionaryDao()
        }
        return null
    }

    @Synchronized
    fun systemUserDictionaryDatabase(): SystemUserDictionaryDatabase? {
        return if (prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase
        } else {
            null
        }
    }

    @Synchronized
    fun loadUserDictionariesIfNecessary() {
        val context = applicationContext.get() ?: return
        if (florisUserDictionaryDatabase == null) {
            florisUserDictionaryDatabase = Room.databaseBuilder(
                context,
                FlorisUserDictionaryDatabase::class.java,
                FlorisUserDictionaryDatabase.DB_FILE_NAME
            ).allowMainThreadQueries().build()
        }
        if (systemUserDictionaryDatabase == null && prefs.dictionary.enableSystemUserDictionary.get()) {
            systemUserDictionaryDatabase = SystemUserDictionaryDatabase(context)
        }
        if (prefs.dictionary.enableSystemUserDictionary.get()) {
            ensureSystemUserDictionaryObserver()
            warmSystemUserDictionaryCache()
        }
    }

    @Synchronized
    fun unloadUserDictionariesIfNecessary() {
        if (florisUserDictionaryDatabase != null) {
            florisUserDictionaryDatabase?.close()
            florisUserDictionaryDatabase = null
        }
        if (systemUserDictionaryDatabase != null) {
            systemUserDictionaryDatabase = null
        }
        unregisterSystemUserDictionaryObserver()
    }
}
