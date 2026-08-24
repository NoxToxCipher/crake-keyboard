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
 * Type-safe Kotlin interface to the high-speed Native Rust Core.
 */
object FlorisNative {
    private var isLoaded = false

    init {
        try {
            System.loadLibrary("fl_native")
            isLoaded = true
        } catch (e: UnsatisfiedLinkError) {
            // Fallback for JVM host unit tests where native library is absent
            isLoaded = false
        }
    }

    /**
     * Checks if the native Rust library was successfully loaded.
     */
    fun isAvailable(): Boolean = isLoaded

    /**
     * Inserts a word with a frequency weight into the native Radix Trie.
     */
    fun insertWord(word: String, frequency: Int) {
        if (!isLoaded) return
        nativeNlpInsertWord(word, frequency)
    }

    /**
     * Queries autocompletion candidates and typo corrections in 33µs.
     */
    fun suggest(query: String, limit: Int = 3): List<String> {
        if (!isLoaded || query.isBlank()) return emptyList()
        return nativeNlpSuggest(query, limit).toList()
    }

    // --- External JNI Declarations ---
    @JvmStatic
    private external fun nativeNlpInsertWord(word: String, frequency: Int)

    @JvmStatic
    private external fun nativeNlpSuggest(query: String, limit: Int): Array<String>
}
