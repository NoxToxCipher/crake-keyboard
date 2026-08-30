/*
 * Copyright (C) 2026 The Crake Contributors
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

package dev.patrickgold.florisboard.ime.nlp

import android.content.Context
import android.util.Log
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * On-device local flight recorder for recording typing actions, gesture flows,
 * autocorrection decisions, and missed corrections for NLP debugging and offline evaluation.
 *
 * 100% Air-Gapped: All logs remain local on device. Excluded from password fields and incognito.
 */
object FlightRecorderManager {
    private const val TAG = "CrakeFlightRecorder"
    private const val LOG_FILE_NAME = "flight_recorder.jsonl"
    private const val MAX_LOG_SIZE_BYTES = 5 * 1024 * 1024L // 5MB circular buffer

    enum class InputMode {
        TYPING,
        GLIDING,
        UNKNOWN,
    }

    enum class ActionType {
        KEY_TAP,
        GLIDE_STROKE,
        AUTOCORRECTION,
        MISSED_CORRECTION,
        MANUAL_REVERT,
        WORD_COMMITTED,
        SUGGESTION_PICKED,
    }

    data class GestureMetrics(
        val pointCount: Int,
        val durationMs: Long,
        val distanceDp: Float = 0.0f,
    ) {
        fun toJsonString(): String = buildString {
            append("{\"pointCount\":").append(pointCount)
            append(",\"durationMs\":").append(durationMs)
            if (distanceDp > 0.0f) append(",\"distanceDp\":").append(distanceDp)
            append("}")
        }
    }

    data class Record(
        val timestamp: Long = System.currentTimeMillis(),
        val mode: InputMode,
        val action: ActionType,
        val rawInput: String? = null,
        val correctedTo: String? = null,
        val intendedWord: String? = null,
        val candidates: List<String>? = null,
        val gestureMetrics: GestureMetrics? = null,
        val contextBefore: String? = null,
        val packageName: String? = null,
        val isTypo: Boolean = false,
    ) {
        fun toJsonString(): String = buildString {
            append("{\"timestamp\":").append(timestamp)
            val isoFormatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            append(",\"time\":\"").append(isoFormatter.format(Date(timestamp))).append("\"")
            append(",\"mode\":\"").append(mode.name).append("\"")
            append(",\"action\":\"").append(action.name).append("\"")
            rawInput?.let { append(",\"rawInput\":\"").append(escapeJson(it)).append("\"") }
            correctedTo?.let { append(",\"correctedTo\":\"").append(escapeJson(it)).append("\"") }
            intendedWord?.let { append(",\"intendedWord\":\"").append(escapeJson(it)).append("\"") }
            if (isTypo) append(",\"isTypo\":true")
            candidates?.let { list ->
                append(",\"candidates\":[")
                list.forEachIndexed { i, cand ->
                    if (i > 0) append(",")
                    append("\"").append(escapeJson(cand)).append("\"")
                }
                append("]")
            }
            gestureMetrics?.let {
                append(",\"gestureMetrics\":").append(it.toJsonString())
            }
            contextBefore?.let { append(",\"contextBefore\":\"").append(escapeJson(it)).append("\"") }
            packageName?.let { append(",\"packageName\":\"").append(escapeJson(it)).append("\"") }
            append("}")
        }

        private fun escapeJson(s: String): String {
            return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recordChannel = Channel<Record>(capacity = 512)
    private var appContext: Context? = null
    private val prefs by FlorisPreferenceStore

    // Recent word tracker for detecting manual reverts and backspace retyping
    @Volatile
    private var lastCommittedWord: String? = null
    @Volatile
    private var lastCommittedMode: InputMode = InputMode.TYPING
    @Volatile
    private var lastCommittedTime: Long = 0L
    @Volatile
    private var lastRawInput: String? = null
    @Volatile
    private var lastCandidates: List<String> = emptyList()

    private val _recentEventsCount = MutableStateFlow(0)
    val recentEventsCount = _recentEventsCount.asStateFlow()

    init {
        scope.launch {
            processRecordQueue()
        }
    }

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val PHONE_REGEX = Regex("(\\+?\\d{1,3}[- ]?)?\\(?\\d{3}\\)?[- ]?\\d{3}[- ]?\\d{4}")
    private val CREDIT_CARD_REGEX = Regex("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13}|6(?:011|5[0-9]{2})[0-9]{12})\\b")

    fun sanitizePii(text: String?): String? {
        if (text == null) return null
        var sanitized = text
        sanitized = EMAIL_REGEX.replace(sanitized, "[EMAIL]")
        sanitized = CREDIT_CARD_REGEX.replace(sanitized, "[CARD]")
        sanitized = PHONE_REGEX.replace(sanitized, "[PHONE]")
        return sanitized
    }

    private fun isLoggingAllowed(keyVariation: KeyVariation? = null, packageName: String? = null): Boolean {
        if (!prefs.devtools.flightRecorderEnabled.get()) return false
        if (keyVariation == KeyVariation.PASSWORD) return false
        if (packageName != null) {
            val pkg = packageName.lowercase()
            if (pkg.contains("keepass") || pkg.contains("bitwarden") || pkg.contains("1password") ||
                pkg.contains("authenticator") || pkg.contains("keychain") || pkg.contains("dashlane") ||
                pkg.contains("lastpass") || pkg.contains("nordpass") || pkg.contains("vault")) {
                return false
            }
        }
        return true
    }

    fun logKeyTap(
        keyLabel: String,
        contextBefore: String? = null,
        keyVariation: KeyVariation? = null,
        packageName: String? = null,
    ) {
        if (!isLoggingAllowed(keyVariation, packageName)) return
        val record = Record(
            mode = InputMode.TYPING,
            action = ActionType.KEY_TAP,
            rawInput = keyLabel,
            contextBefore = sanitizePii(contextBefore?.takeLast(32)),
            packageName = packageName,
        )
        recordChannel.trySend(record)
    }

    fun logGlideStroke(
        pointCount: Int,
        durationMs: Long,
        chosenWord: String?,
        topCandidates: List<String>,
        contextBefore: String? = null,
        keyVariation: KeyVariation? = null,
        packageName: String? = null,
    ) {
        if (!isLoggingAllowed(keyVariation, packageName)) return
        val record = Record(
            mode = InputMode.GLIDING,
            action = ActionType.GLIDE_STROKE,
            rawInput = chosenWord,
            correctedTo = chosenWord,
            candidates = if (prefs.devtools.flightRecorderIncludeSuggestions.get()) topCandidates.take(8) else null,
            gestureMetrics = GestureMetrics(pointCount, durationMs),
            contextBefore = sanitizePii(contextBefore?.takeLast(32)),
            packageName = packageName,
        )
        lastCommittedWord = chosenWord
        lastCommittedMode = InputMode.GLIDING
        lastCommittedTime = System.currentTimeMillis()
        lastCandidates = topCandidates
        recordChannel.trySend(record)
    }

    fun logWordCommitted(
        rawInput: String,
        committedWord: String,
        mode: InputMode,
        topCandidates: List<String>,
        isAutocorrected: Boolean,
        isKnownWord: Boolean,
        contextBefore: String? = null,
        keyVariation: KeyVariation? = null,
        packageName: String? = null,
    ) {
        if (!isLoggingAllowed(keyVariation, packageName)) return

        val isMissedCorrection = !isKnownWord && !isAutocorrected && rawInput.length >= 2 && topCandidates.isNotEmpty()
        val action = when {
            isAutocorrected && rawInput != committedWord -> ActionType.AUTOCORRECTION
            isMissedCorrection -> ActionType.MISSED_CORRECTION
            else -> ActionType.WORD_COMMITTED
        }

        val record = Record(
            mode = mode,
            action = action,
            rawInput = sanitizePii(rawInput),
            correctedTo = sanitizePii(committedWord),
            candidates = if (prefs.devtools.flightRecorderIncludeSuggestions.get()) topCandidates.take(8) else null,
            isTypo = isMissedCorrection,
            contextBefore = sanitizePii(contextBefore?.takeLast(32)),
            packageName = packageName,
        )

        lastRawInput = rawInput
        lastCommittedWord = committedWord
        lastCommittedMode = mode
        lastCommittedTime = System.currentTimeMillis()
        lastCandidates = topCandidates

        recordChannel.trySend(record)
    }

    fun logManualRevertOrRetype(
        deletedWord: String,
        retypedWord: String?,
        contextBefore: String? = null,
        keyVariation: KeyVariation? = null,
        packageName: String? = null,
    ) {
        if (!isLoggingAllowed(keyVariation, packageName)) return
        val record = Record(
            mode = lastCommittedMode,
            action = ActionType.MANUAL_REVERT,
            rawInput = sanitizePii(deletedWord),
            intendedWord = sanitizePii(retypedWord),
            candidates = lastCandidates.take(8),
            contextBefore = sanitizePii(contextBefore?.takeLast(32)),
            packageName = packageName,
        )
        recordChannel.trySend(record)
    }

    fun logSuggestionPicked(
        rawPrefix: String,
        selectedWord: String,
        allCandidates: List<String>,
        mode: InputMode = InputMode.TYPING,
        contextBefore: String? = null,
        keyVariation: KeyVariation? = null,
        packageName: String? = null,
    ) {
        if (!isLoggingAllowed(keyVariation, packageName)) return
        val record = Record(
            mode = mode,
            action = ActionType.SUGGESTION_PICKED,
            rawInput = sanitizePii(rawPrefix),
            correctedTo = sanitizePii(selectedWord),
            candidates = allCandidates.take(8),
            contextBefore = sanitizePii(contextBefore?.takeLast(32)),
            packageName = packageName,
        )
        lastCommittedWord = selectedWord
        lastCommittedMode = mode
        lastCommittedTime = System.currentTimeMillis()
        recordChannel.trySend(record)
    }

    private suspend fun processRecordQueue() {
        for (record in recordChannel) {
            val jsonLine = record.toJsonString()
            Log.i(TAG, jsonLine)
            _recentEventsCount.value += 1
            appContext?.let { ctx ->
                writeJsonLineToFile(ctx, jsonLine)
            }
        }
    }

    private fun writeJsonLineToFile(context: Context, jsonLine: String) {
        runCatching {
            val file = File(context.filesDir, LOG_FILE_NAME)
            if (file.exists() && file.length() > MAX_LOG_SIZE_BYTES) {
                rotateLogFile(file)
            }
            FileWriter(file, true).use { writer ->
                writer.append(jsonLine).append("\n")
            }
        }
    }

    private fun rotateLogFile(file: File) {
        runCatching {
            val lines = file.readLines()
            if (lines.size > 2000) {
                val retained = lines.takeLast(1000)
                file.writeText(retained.joinToString("\n") + "\n")
            }
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }

    suspend fun readRecentRecords(context: Context, limit: Int = 100): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val file = getLogFile(context)
            if (file.exists()) {
                file.readLines().takeLast(limit)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())
    }

    suspend fun clearLogFile(context: Context): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = getLogFile(context)
            if (file.exists()) {
                file.delete()
            } else {
                true
            }
        }.getOrDefault(false)
    }
}