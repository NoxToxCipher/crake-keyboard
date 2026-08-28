/*
 * Copyright (C) 2025-2026 The FlorisBoard Contributors
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

package org.florisboard.libnative

/**
 * JNI interface to native floris-core NLP engine, crake-privacy Secret Shield, and Boreal YARA scanner.
 */
object FlorisNative {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("fl_native")
            isLoaded = true
        } catch (_: UnsatisfiedLinkError) {
            isLoaded = false
        }
    }

    data class ShieldInspectionResult(
        val isSecretDetected: Boolean,
        val warningMessage: String?,
        val redactedText: String,
    )

    data class ThreatResult(
        val ruleName: String,
        val category: String,
        val severity: String,
    )

    data class MetaScrubResult(
        val invisibleCharsRemoved: Int,
        val urlsSanitized: Boolean,
        val cleanedText: String,
    )

    data class NativeCandidate(
        val text: String,
        val isAutocorrect: Boolean,
    )

    data class ClipboardIncoming(
        val cleanedText: String,
        val isSensitive: Boolean,
    )

    fun isAvailable(): Boolean = isLoaded

    /**
     * Bulk-loads the shipped CRKD dictionary blob into the native trie in a
     * single JNI call. Returns the number of words loaded, or -1 if the blob
     * is rejected — callers must then fall back to the JSON path.
     */
    fun loadDictionaryBlob(data: ByteArray): Int {
        if (!isLoaded || data.isEmpty()) return -1
        return nativeNlpLoadDictBlob(data)
    }

    /**
     * Uploads a keyboard layout's touch bounds (flat [l,t,r,b] per key) for
     * shadow hit-testing. Returns the layout generation, or -1 on failure.
     */
    fun hitSetKeys(rects: FloatArray, chars: String = ""): Int {
        if (!isLoaded || rects.isEmpty()) return -1
        return nativeHitSetKeys(rects, chars)
    }

    /** Learned per-key touch offsets (CRKT blob) for persistence. */
    fun exportTouchOffsets(): ByteArray? {
        if (!isLoaded) return null
        return nativeHitExportOffsets().takeIf { it.size > 9 }
    }

    /** Restores per-key touch offsets; entries restored, or -1 on rejection. */
    fun importTouchOffsets(data: ByteArray): Int {
        if (!isLoaded || data.isEmpty()) return -1
        return nativeHitImportOffsets(data)
    }

    /**
     * Shadow hit test against the uploaded layout: key index, -1 for no key,
     * -2 when [generation] is no longer the current layout (skip, don't count).
     */
    fun hitTest(generation: Int, x: Float, y: Float): Int {
        if (!isLoaded) return -2
        return nativeHitTest(generation, x, y)
    }

    /**
     * Two-token spurious-space repair: returns the dictionary word the two
     * fragments were meant to be ("shou" + "kd" -> "should"), or null when
     * they should not merge. Legitimate word pairs never merge.
     */
    fun mergeRepair(prevWord: String, current: String, preceding: String = ""): String? {
        if (!isLoaded || prevWord.isEmpty() || current.isEmpty()) return null
        return nativeNlpMergeRepair(preceding, prevWord, current).takeIf { it.isNotEmpty() }
    }

    /** Records that the user wrote [nextWord] after [prevWord] (personal context). */
    fun recordPersonalBigram(prevWord: String, nextWord: String) {
        invalidateLetterPredictionMemo()
        if (!isLoaded || prevWord.isEmpty() || nextWord.isEmpty()) return
        nativeNlpRecordPersonalBigram(prevWord, nextWord)
    }

    /** Learned state (user words + correction habits) as a CRKL blob. */
    fun exportLearned(): ByteArray? {
        if (!isLoaded) return null
        return nativeNlpExportLearned().takeIf { it.isNotEmpty() }
    }

    /** Restores a CRKL blob; returns learned-word count or -1 on rejection. */
    fun importLearned(data: ByteArray): Int {
        invalidateLetterPredictionMemo()
        if (!isLoaded || data.isEmpty()) return -1
        return nativeNlpImportLearned(data)
    }

    /**
     * Three-fragment split repair ("cha" + "nbn" + "ges" -> "changes"), or
     * null. Fuzzy joins fire only under a unique context witness from
     * [preceding]; exact joins may fire witness-free.
     */
    fun mergeRepair3(first: String, second: String, third: String, preceding: String = ""): String? {
        if (!isLoaded || first.isEmpty() || second.isEmpty() || third.isEmpty()) return null
        return nativeNlpMergeRepair3(preceding, first, second, third).takeIf { it.isNotEmpty() }
    }

    fun insertWord(word: String, frequency: Int) {
        invalidateLetterPredictionMemo()
        if (!isLoaded) return
        // Never learn text if it is detected as a secret/mnemonic or threat
        val inspection = inspectSecret(word)
        if (inspection.isSecretDetected) return

        nativeNlpInsertWord(word, frequency)
    }

    /**
     * Loads the CRKB bigram language model used for context re-ranking.
     * Returns the pair count, or -1 on rejection (suggestions then simply
     * run without context re-ranking — no fallback needed).
     */
    fun loadBigramBlob(data: ByteArray): Int {
        if (!isLoaded || data.isEmpty()) return -1
        return nativeNlpLoadBigramBlob(data)
    }

    fun suggest(query: String, limit: Int = 3, prevWord: String = ""): List<NativeCandidate> {
        if (!isLoaded || query.isBlank()) return emptyList()
        val rawMatches = nativeNlpSuggestCtx(query, prevWord, limit)
        return rawMatches.map { raw ->
            val lastColon = raw.lastIndexOf(':')
            if (lastColon > 0) {
                val word = raw.substring(0, lastColon)
                val isAuto = raw.substring(lastColon + 1) == "1"
                NativeCandidate(word, isAuto)
            } else {
                NativeCandidate(raw, false)
            }
        }
    }

    /** Words most likely to FOLLOW prevWord (personal pairs first, then the
     * shipped language model) — the next-word suggestion row. */
    fun predictNextWords(
        prevWord: String,
        maxResults: Int = 3,
        includePersonal: Boolean = true,
    ): List<String> {
        if (!isLoaded) return emptyList()
        // Blank prev is valid: the native side answers sentence starters.
        return nativeNlpPredictNextWords(prevWord, maxResults, includePersonal).toList()
    }

    @JvmStatic
    private external fun nativeNlpPredictNextWords(
        prevWord: String,
        maxResults: Int,
        includePersonal: Boolean,
    ): Array<String>

    /** Single-entry memo for letter predictions: the tap-down handler, the
     * flick preview and the Compose bar all ask for the SAME (prefix, prev)
     * state between keystrokes, and the down-handler sits on the touch
     * path. Invalidated whenever learning mutates the model. */
    @Volatile private var letterPredictionMemo: Triple<String, String, Map<Char, String>>? = null

    internal fun invalidateLetterPredictionMemo() {
        letterPredictionMemo = null
    }

    /**
     * Memo-only variant for latency-critical callers (the touch-down
     * handler): returns the cached predictions when they match the given
     * state, or null WITHOUT crossing JNI. Callers fall back to their
     * non-predictive path on null; a background warmer keeps the memo
     * fresh between keystrokes.
     */
    fun predictNextLetterWordsCached(prefix: String, prevWord: String): Map<Char, String>? {
        if (!isLoaded) return null
        letterPredictionMemo?.let { (p, pw, cached) ->
            if (p == prefix && pw == prevWord) return cached
        }
        return null
    }

    fun predictNextLetterWords(prefix: String = "", prevWord: String = ""): Map<Char, String> {
        if (!isLoaded) return emptyMap()
        letterPredictionMemo?.let { (p, pw, cached) ->
            if (p == prefix && pw == prevWord) return cached
        }
        val rawMatches = nativeNlpPredictNextLetterWords(prefix, prevWord)
        val result = mutableMapOf<Char, String>()
        for (raw in rawMatches) {
            val colonIdx = raw.indexOf(':')
            if (colonIdx > 0 && raw.isNotEmpty()) {
                val ch = raw[0].lowercaseChar()
                val word = raw.substring(colonIdx + 1)
                result[ch] = word
            }
        }
        letterPredictionMemo = Triple(prefix, prevWord, result)
        return result
    }

    fun sanitizeUrl(rawUrl: String): String {
        if (!isLoaded || rawUrl.isBlank()) return rawUrl
        return nativeSanitizeUrl(rawUrl)
    }

    fun sanitizeText(rawText: String): String {
        if (!isLoaded || rawText.isBlank()) return rawText
        return nativeSanitizeText(rawText)
    }

    fun inspectSecret(rawText: String): ShieldInspectionResult {
        if (!isLoaded || rawText.isBlank()) {
            return ShieldInspectionResult(false, null, rawText)
        }
        val raw = nativeInspectSecret(rawText)
        val parts = raw.split("|", limit = 3)
        val isDetected = parts.getOrNull(0) == "1"
        val warning = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }
        val redacted = parts.getOrNull(2) ?: rawText
        return ShieldInspectionResult(isDetected, warning, redacted)
    }

    fun metascrubText(rawText: String): MetaScrubResult {
        if (!isLoaded || rawText.isBlank()) {
            return MetaScrubResult(0, false, rawText)
        }
        val raw = nativeMetaScrubText(rawText)
        val parts = raw.split("|", limit = 3)
        val removed = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val sanitized = parts.getOrNull(1) == "1"
        val cleaned = parts.getOrNull(2) ?: rawText
        return MetaScrubResult(removed, sanitized, cleaned)
    }

    fun scanThreats(rawText: String): List<ThreatResult> {
        if (!isLoaded || rawText.isBlank()) return emptyList()
        val rawMatches = nativeScanThreats(rawText)
        return rawMatches.mapNotNull { entry ->
            val parts = entry.split(":", limit = 3)
            if (parts.size == 3) {
                ThreatResult(parts[0], parts[1], parts[2])
            } else {
                null
            }
        }
    }

    fun generateQrMatrix(data: String): String {
        if (!isLoaded || data.isBlank()) return ""
        return nativeGenerateQrMatrix(data)
    }

    fun createSyncBundle(keyHex: String, rawData: String, chunkSize: Int = 128): List<String> {
        if (!isLoaded || rawData.isBlank()) return emptyList()
        return nativeCreateSyncBundle(keyHex, rawData, chunkSize).toList()
    }

    fun reassembleSyncBundle(keyHex: String, frames: List<String>): String {
        if (!isLoaded || frames.isEmpty()) return ""
        return nativeReassembleSyncBundle(keyHex, frames.toTypedArray())
    }

    data class GlidePoint(val x: Float, val y: Float, val timestamp: Long = 0L)

    fun glideSetLayout(
        codes: IntArray,
        chars: String,
        xs: FloatArray,
        ys: FloatArray,
        widths: FloatArray,
        heights: FloatArray,
    ) {
        if (!isLoaded) return
        nativeGlideSetLayout(codes, chars, xs, ys, widths, heights)
    }

    /** Glide match result: candidate words plus whether the best one is a
     * solid word safe to auto-commit. A display-only set (all junk-band
     * shape fits) must never be committed over the user's stroke. */
    data class GlideResult(val words: List<String>, val commitSafe: Boolean)

    fun glideMatch(points: List<GlidePoint>, maxResults: Int = 5, prevWord: String = ""): GlideResult {
        if (!isLoaded || points.size < 2) return GlideResult(emptyList(), false)
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }
        val times = LongArray(points.size) { points[it].timestamp }
        val raw = nativeGlideMatch(xs, ys, times, maxResults, prevWord).toList()
        // Leading empty string is the native sentinel for "display-only".
        return if (raw.firstOrNull() == "") {
            GlideResult(raw.drop(1), commitSafe = false)
        } else {
            GlideResult(raw, commitSafe = raw.isNotEmpty())
        }
    }

    @JvmStatic
    private external fun nativeGlideSetLayout(
        codes: IntArray,
        chars: String,
        xs: FloatArray,
        ys: FloatArray,
        widths: FloatArray,
        heights: FloatArray,
    )

    @JvmStatic
    private external fun nativeGlideMatch(
        xs: FloatArray,
        ys: FloatArray,
        times: LongArray,
        maxResults: Int,
        prevWord: String,
    ): Array<String>

    @JvmStatic
    /** The user rejected this correction for this typed token (backspace
     * revert). Two rejections and the engine stops auto-committing that
     * pair; the word stays a plain suggestion. */
    fun recordRejectedCorrection(typo: String, wrongSuggestion: String) {
        if (!isLoaded) return
        nativeNlpRecordRejectedCorrection(typo.trim(), wrongSuggestion.trim())
    }

    @JvmStatic
    private external fun nativeNlpRecordRejectedCorrection(typo: String, wrongSuggestion: String)

    fun recordPersonalCorrection(typo: String, intended: String) {
        if (!isLoaded || typo.isBlank() || intended.isBlank() || typo.equals(intended, ignoreCase = true)) return
        nativeNlpRecordPersonalCorrection(typo.trim(), intended.trim())
    }

    private external fun nativeNlpRecordPersonalCorrection(typo: String, intended: String)

    private external fun nativeNlpInsertWord(word: String, frequency: Int)

    private external fun nativeNlpLoadDictBlob(data: ByteArray): Int

    private external fun nativeHitSetKeys(rects: FloatArray, chars: String): Int

    private external fun nativeHitExportOffsets(): ByteArray

    private external fun nativeHitImportOffsets(data: ByteArray): Int

    private external fun nativeHitTest(generation: Int, x: Float, y: Float): Int

    private external fun nativeNlpMergeRepair(preceding: String, prevWord: String, current: String): String

    private external fun nativeNlpMergeRepair3(preceding: String, first: String, second: String, third: String): String

    private external fun nativeNlpRecordPersonalBigram(prevWord: String, nextWord: String)

    private external fun nativeNlpExportLearned(): ByteArray

    private external fun nativeNlpImportLearned(data: ByteArray): Int

    private external fun nativeNlpLoadBigramBlob(data: ByteArray): Int

    private external fun nativeNlpSuggestCtx(query: String, prevWord: String, limit: Int): Array<String>

    @JvmStatic
    private external fun nativeNlpSuggest(query: String, limit: Int): Array<String>

    @JvmStatic
    private external fun nativeNlpPredictNextLetterWords(query: String, prevWord: String): Array<String>

    @JvmStatic
    private external fun nativeSanitizeUrl(rawUrl: String): String

    @JvmStatic
    private external fun nativeSanitizeText(rawText: String): String

    @JvmStatic
    private external fun nativeMetaScrubText(rawText: String): String

    @JvmStatic
    private external fun nativeInspectSecret(rawText: String): String

    @JvmStatic
    private external fun nativeScanThreats(rawText: String): Array<String>

    @JvmStatic
    private external fun nativeGenerateQrMatrix(data: String): String

    @JvmStatic
    private external fun nativeCreateSyncBundle(keyHex: String, rawData: String, chunkSize: Int): Array<String>

    @JvmStatic
    private external fun nativeReassembleSyncBundle(keyHex: String, frames: Array<String>): String

    @JvmStatic
    external fun nativePgponyGenerateKeypair(): Array<String>

    @JvmStatic
    external fun nativePgponyEncrypt(plaintext: String, recipientPubkey: String): String?

    @JvmStatic
    external fun nativePgponyDecrypt(armoredText: String, privateKeyHex: String): String?

    @JvmStatic
    external fun nativePgponyIsArmored(text: String): Boolean

    fun pgponyGenerateKeypair(): Pair<String, String>? {
        if (!isAvailable()) return null
        val arr = nativePgponyGenerateKeypair()
        if (arr.size >= 2) {
            return Pair(arr[0], arr[1])
        }
        return null
    }

    fun pgponyEncrypt(plaintext: String, recipientPubkey: String): String? {
        if (!isAvailable()) return null
        return nativePgponyEncrypt(plaintext, recipientPubkey)
    }

    fun pgponyDecrypt(armoredText: String, privateKeyHex: String): String? {
        if (!isAvailable()) return null
        return nativePgponyDecrypt(armoredText, privateKeyHex)
    }

    fun pgponyIsArmored(text: String): Boolean {
        if (!isAvailable()) return false
        return nativePgponyIsArmored(text)
    }

    /**
     * Scrubs incoming clipboard text (invisible characters, URL trackers)
     * and classifies its sensitivity (Secret Shield + OTP heuristics) in a
     * single native call — the entry point for the copy path.
     */
    fun clipboardProcessText(rawText: String): ClipboardIncoming {
        if (!isLoaded || rawText.isBlank()) return ClipboardIncoming(rawText, false)
        val raw = nativeClipboardProcessText(rawText)
        if (raw.isNullOrEmpty()) return ClipboardIncoming(rawText, false)
        return ClipboardIncoming(raw.substring(1), raw[0] == '1')
    }

    /**
     * Retention sweep over the clipboard history: ids of clips the enabled
     * rules (size limit / age expiry / sensitive TTL) say to remove.
     * [flags] carries bit 0 = pinned, bit 1 = sensitive per clip.
     */
    fun clipboardRetentionSweep(
        ids: LongArray,
        flags: IntArray,
        createdMs: LongArray,
        nowMs: Long,
        limitEnabled: Boolean,
        maxUnpinned: Int,
        expiryEnabled: Boolean,
        expiryAfterMs: Long,
        sensitiveEnabled: Boolean,
        sensitiveAfterMs: Long,
    ): LongArray {
        if (!isLoaded || ids.isEmpty()) return LongArray(0)
        return nativeClipboardRetentionSweep(
            ids, flags, createdMs, nowMs,
            limitEnabled, maxUnpinned,
            expiryEnabled, expiryAfterMs,
            sensitiveEnabled, sensitiveAfterMs,
        ) ?: LongArray(0)
    }

    /**
     * Index of the first history clip duplicating the incoming one, or -1.
     * [contents] carries the text for text clips and the URI string for
     * media clips (empty string for absent values).
     */
    fun clipboardFindDuplicate(
        kinds: IntArray,
        contents: Array<String>,
        newKind: Int,
        newContent: String,
    ): Int {
        if (!isLoaded || kinds.isEmpty()) return -1
        return nativeClipboardFindDuplicate(kinds, contents, newKind, newContent)
    }

    /**
     * Display group per history clip: 0 pinned, 1 recent, 2 other.
     * [flags] carries bit 0 = pinned. Returns an empty array when the
     * native library is unavailable — callers degrade gracefully.
     */
    fun clipboardClassifyHistory(flags: IntArray, createdMs: LongArray, nowMs: Long): ByteArray {
        if (!isLoaded || flags.isEmpty()) return ByteArray(0)
        return nativeClipboardClassifyHistory(flags, createdMs, nowMs) ?: ByteArray(0)
    }

    /** AOSP-semantics MIME comparison where [desired] may be a pattern. */
    fun clipboardCompareMimeTypes(concrete: String, desired: String): Boolean {
        if (!isLoaded) return desired == "*/*" || concrete == desired
        return nativeClipboardCompareMimeTypes(concrete, desired)
    }

    @JvmStatic
    private external fun nativeClipboardProcessText(rawText: String): String?

    @JvmStatic
    private external fun nativeClipboardRetentionSweep(
        ids: LongArray,
        flags: IntArray,
        createdMs: LongArray,
        nowMs: Long,
        limitEnabled: Boolean,
        maxUnpinned: Int,
        expiryEnabled: Boolean,
        expiryAfterMs: Long,
        sensitiveEnabled: Boolean,
        sensitiveAfterMs: Long,
    ): LongArray?

    @JvmStatic
    private external fun nativeClipboardFindDuplicate(
        kinds: IntArray,
        contents: Array<String>,
        newKind: Int,
        newContent: String,
    ): Int

    @JvmStatic
    private external fun nativeClipboardClassifyHistory(
        flags: IntArray,
        createdMs: LongArray,
        nowMs: Long,
    ): ByteArray?

    @JvmStatic
    private external fun nativeClipboardCompareMimeTypes(concrete: String, desired: String): Boolean

    fun toBritishSpelling(word: String): String? {
        if (!isAvailable()) return null
        val res = nativeToBritishSpelling(word)
        return if (res.isNullOrEmpty()) null else res
    }

    private external fun nativeToBritishSpelling(word: String): String?

}
