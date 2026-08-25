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
 * JNI interface to native floris-core NLP engine and crake-privacy engine.
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

    fun isAvailable(): Boolean = isLoaded

    fun insertWord(word: String, frequency: Int) {
        if (!isLoaded) return
        // Never learn text if it is detected as a secret/mnemonic
        val inspection = inspectSecret(word)
        if (inspection.isSecretDetected) return

        nativeNlpInsertWord(word, frequency)
    }

    fun suggest(query: String, limit: Int = 3): List<String> {
        if (!isLoaded || query.isBlank()) return emptyList()
        return nativeNlpSuggest(query, limit).toList()
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

    @JvmStatic
    private external fun nativeNlpInsertWord(word: String, frequency: Int)

    @JvmStatic
    private external fun nativeNlpSuggest(query: String, limit: Int): Array<String>

    @JvmStatic
    private external fun nativeSanitizeUrl(rawUrl: String): String

    @JvmStatic
    private external fun nativeSanitizeText(rawText: String): String

    @JvmStatic
    private external fun nativeInspectSecret(rawText: String): String
}
