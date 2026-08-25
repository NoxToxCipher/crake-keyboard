package org.florisboard.libnative

/**
 * JNI bindings for the native floris-core NLP and predictive text engine.
 */
object FlorisNative {

    init {
        try {
            System.loadLibrary("fl_native_rust")
        } catch (e: UnsatisfiedLinkError) {
            e.printStackTrace()
        }
    }

    data class SuggestionResult(
        val word: String,
        val isAutocorrect: Boolean,
    )

    fun insertWord(word: String, frequency: Int) {
        try {
            nativeNlpInsertWord(word, frequency)
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    fun suggest(query: String, limit: Int = 5): List<SuggestionResult> {
        return try {
            val raw = nativeNlpSuggest(query, limit) ?: return emptyList()
            raw.mapNotNull { item ->
                val parts = item.split(":", limit = 2)
                if (parts.isNotEmpty()) {
                    val word = parts[0]
                    val isAuto = parts.getOrNull(1) == "1"
                    SuggestionResult(word, isAuto)
                } else {
                    null
                }
            }
        } catch (_: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    fun predictNextLetterWords(query: String): List<Pair<Char, String>> {
        return try {
            val raw = nativeNlpPredictNextLetterWords(query) ?: return emptyList()
            raw.mapNotNull { item ->
                val parts = item.split(":", limit = 2)
                if (parts.size == 2 && parts[0].isNotEmpty()) {
                    parts[0][0] to parts[1]
                } else {
                    null
                }
            }
        } catch (_: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    fun updateGlideLayout(
        codes: IntArray,
        chars: String,
        xs: FloatArray,
        ys: FloatArray,
        widths: FloatArray,
        heights: FloatArray,
    ) {
        try {
            nativeGlideSetLayout(codes, chars, xs, ys, widths, heights)
        } catch (_: UnsatisfiedLinkError) {
        }
    }

    fun matchGlide(xs: FloatArray, ys: FloatArray, maxResults: Int = 5): List<String> {
        return try {
            val raw = nativeGlideMatch(xs, ys, maxResults) ?: return emptyList()
            raw.toList()
        } catch (_: UnsatisfiedLinkError) {
            emptyList()
        }
    }

    // Native JNI functions implemented in lib.rs
    private external fun nativeNlpInsertWord(word: String, frequency: Int)
    private external fun nativeNlpSuggest(query: String, limit: Int): Array<String>?
    private external fun nativeNlpPredictNextLetterWords(query: String): Array<String>?
    private external fun nativeGlideSetLayout(
        codes: IntArray, chars: String, xs: FloatArray, ys: FloatArray, widths: FloatArray, heights: FloatArray,
    )
    private external fun nativeGlideMatch(xs: FloatArray, ys: FloatArray, maxResults: Int): Array<String>?
}
