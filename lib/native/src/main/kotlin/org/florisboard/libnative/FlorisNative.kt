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
     * The static dictionary corpus as loaded from the CRKD blob, in blob
     * order. Empty until [loadDictionaryBlob] has succeeded. Learned words
     * never appear here — this is the exact contents the JVM word map used
     * to duplicate.
     */
    fun corpusWords(): Array<String> {
        if (!isLoaded) return emptyArray()
        return nativeNlpCorpusWords()
    }

    /** Frequency of a corpus word, 0 when absent (the map-lookup contract). */
    fun corpusFrequency(word: String): Int {
        if (!isLoaded || word.isEmpty()) return 0
        return nativeNlpCorpusFreq(word)
    }

    /**
     * Uploads a keyboard layout's touch bounds (flat [l,t,r,b] per key) for
     * shadow hit-testing. Returns the layout generation, or -1 on failure.
     */
    fun hitSetKeys(rects: FloatArray): Int {
        if (!isLoaded || rects.isEmpty()) return -1
        return nativeHitSetKeys(rects)
    }

    /**
     * Shadow hit test against the uploaded layout: key index, -1 for no key,
     * -2 when [generation] is no longer the current layout (skip, don't count).
     */
    fun hitTest(generation: Int, x: Float, y: Float): Int {
        if (!isLoaded) return -2
        return nativeHitTest(generation, x, y)
    }

    fun insertWord(word: String, frequency: Int) {
        if (!isLoaded) return
        // Never learn text if it is detected as a secret/mnemonic or threat
        val inspection = inspectSecret(word)
        if (inspection.isSecretDetected) return

        nativeNlpInsertWord(word, frequency)
    }

    fun suggest(query: String, limit: Int = 3): List<NativeCandidate> {
        if (!isLoaded || query.isBlank()) return emptyList()
        val rawMatches = nativeNlpSuggest(query, limit)
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

    fun predictNextLetterWords(prefix: String = "", prevWord: String = ""): Map<Char, String> {
        if (!isLoaded) return emptyMap()
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

    data class GlidePoint(val x: Float, val y: Float)

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

    fun glideMatch(points: List<GlidePoint>, maxResults: Int = 5): List<String> {
        if (!isLoaded || points.size < 2) return emptyList()
        val xs = FloatArray(points.size) { points[it].x }
        val ys = FloatArray(points.size) { points[it].y }
        return nativeGlideMatch(xs, ys, maxResults).toList()
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
        maxResults: Int,
    ): Array<String>

    @JvmStatic
    fun recordPersonalCorrection(typo: String, intended: String) {
        if (!isLoaded || typo.isBlank() || intended.isBlank() || typo.equals(intended, ignoreCase = true)) return
        nativeNlpRecordPersonalCorrection(typo.trim(), intended.trim())
    }

    private external fun nativeNlpRecordPersonalCorrection(typo: String, intended: String)

    private external fun nativeNlpInsertWord(word: String, frequency: Int)

    private external fun nativeNlpLoadDictBlob(data: ByteArray): Int

    private external fun nativeHitSetKeys(rects: FloatArray): Int

    private external fun nativeHitTest(generation: Int, x: Float, y: Float): Int

    private external fun nativeNlpCorpusWords(): Array<String>

    private external fun nativeNlpCorpusFreq(word: String): Int

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
}
