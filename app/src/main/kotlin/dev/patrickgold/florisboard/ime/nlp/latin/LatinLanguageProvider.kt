/*
 * Copyright (C) 2022-2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.content.Context
import android.os.SystemClock
import android.util.Log
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.nlp.SpellingProvider
import dev.patrickgold.florisboard.ime.nlp.SpellingResult
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.devtools.flogInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.florisboard.lib.android.readText
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.libnative.FlorisNative
import java.nio.ByteBuffer
import java.nio.ByteOrder

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()
    private val wordData = guardedByLock { mutableMapOf<String, Int>() }
    private val wordDataSerializer = MapSerializer(String.serializer(), Int.serializer())

    override val providerId = ProviderId

    override val forcesSuggestionOn: Boolean
        get() = true

    override suspend fun create() {
        ensureLoaded()
    }

    override suspend fun destroy() {
    }

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        wordData.withLock { words ->
            if (words.isEmpty()) {
                if (!loadFromBlob(words)) {
                    loadFromJson(words)
                }
            }
        }
    }

    /**
     * Fast path: the CRKD binary blob (built by utils/gen_dict_blob.py). The
     * native trie fills from ONE JNI call; the Kotlin map (still consumed by
     * the legacy glide classifier) fills from a ByteBuffer scan of the same
     * bytes. Returns false on any failure so the JSON path takes over —
     * partial native inserts before a failure are harmless, inserts are
     * idempotent and the JSON path re-covers them.
     */
    private fun loadFromBlob(words: MutableMap<String, Int>): Boolean {
        if (!FlorisNative.isAvailable()) return false
        return try {
            val tStart = SystemClock.elapsedRealtime()
            val bytes = appContext.assets.open("ime/dict/data.crkd").use { it.readBytes() }
            val tRead = SystemClock.elapsedRealtime()
            val nativeCount = FlorisNative.loadDictionaryBlob(bytes)
            if (nativeCount < 0) return false
            val tNative = SystemClock.elapsedRealtime()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buf.position(9) // magic(4) + version(1) + count(4), validated natively
            repeat(nativeCount) {
                val wordBytes = ByteArray(buf.short.toInt() and 0xFFFF)
                buf.get(wordBytes)
                words[String(wordBytes, Charsets.UTF_8)] = buf.int
            }
            val tMap = SystemClock.elapsedRealtime()
            Log.i(
                "CrakeStartup",
                "dict load (blob): assetRead=${tRead - tStart}ms nativeLoad(1 JNI call)=${tNative - tRead}ms " +
                    "kotlinMap=${tMap - tNative}ms total=${tMap - tStart}ms words=$nativeCount",
            )
            flogInfo { "Loaded $nativeCount dictionary words from CRKD blob" }
            true
        } catch (e: Exception) {
            words.clear()
            flogDebug { "CRKD blob load failed, falling back to JSON: ${e.message}" }
            false
        }
    }

    private fun loadFromJson(words: MutableMap<String, Int>) {
        try {
            val tStart = SystemClock.elapsedRealtime()
            val rawData = appContext.assets.readText("ime/dict/data.json")
            val tRead = SystemClock.elapsedRealtime()
            val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
            val tParse = SystemClock.elapsedRealtime()
            words.putAll(jsonData)
            val tMap = SystemClock.elapsedRealtime()

            // Populate native Rust Trie with dictionary words
            for ((word, freq) in jsonData) {
                FlorisNative.insertWord(word, freq)
            }
            val tInsert = SystemClock.elapsedRealtime()
            Log.i(
                "CrakeStartup",
                "dict load: assetRead=${tRead - tStart}ms jsonParse=${tParse - tRead}ms " +
                    "kotlinMap=${tMap - tParse}ms nativeInsert(${jsonData.size} JNI calls)=${tInsert - tMap}ms " +
                    "total=${tInsert - tStart}ms",
            )
            flogInfo { "Loaded ${jsonData.size} dictionary words into native Rust Trie" }
        } catch (e: Exception) {
            flogDebug { "Error loading dictionary: ${e.message}" }
        }
    }

    override suspend fun preload(subtype: Subtype) {
        ensureLoaded()
    }

    override suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): SpellingResult {
        if (word.isBlank()) return SpellingResult.unspecified()
        ensureLoaded()
        val suggestions = FlorisNative.suggest(word, maxSuggestionCount)
        val wordList = suggestions.map { it.text }
        return if (wordList.any { it.equals(word, ignoreCase = true) }) {
            SpellingResult.validWord()
        } else if (wordList.isNotEmpty()) {
            SpellingResult.typo(wordList.toTypedArray())
        } else {
            SpellingResult.unspecified()
        }
    }

    override suspend fun suggest(
        subtype: Subtype,
        content: EditorContent,
        maxCandidateCount: Int,
        allowPossiblyOffensive: Boolean,
        isPrivateSession: Boolean,
    ): List<SuggestionCandidate> {
        ensureLoaded()
        val query = when {
            content.textBeforeSelection.isNotEmpty() -> content.textBeforeSelection.takeLastWhile { !it.isWhitespace() }
            content.composingText.isNotBlank() -> content.composingText
            else -> content.currentWordText
        }.trim()

        if (query.isBlank()) return emptyList()

        return buildList {
            // 1. Check Smart Text Expansion & User Snippets First
            try {
                val snippetCandidates = DictionaryManager.default().queryUserDictionary(query, subtype.primaryLocale)
                addAll(snippetCandidates)
            } catch (e: Exception) {
                // Ignore
            }

            // 2. Native Safe Rust Trie Word Predictions
            if (FlorisNative.isAvailable()) {
                val cleanWordQuery = query.takeLastWhile { it.isLetter() || it == '\'' }
                if (cleanWordQuery.isNotBlank()) {
                    val candidates = FlorisNative.suggest(cleanWordQuery, maxCandidateCount)
                    for ((index, candidate) in candidates.withIndex()) {
                        // Avoid duplicates if snippet already added
                        if (none { it.text.toString().equals(candidate.text, ignoreCase = true) }) {
                            add(
                                WordSuggestionCandidate(
                                    text = candidate.text,
                                    secondaryText = null,
                                    confidence = 0.9 - (index * 0.1),
                                    isEligibleForAutoCommit = candidate.isAutocorrect,
                                    sourceProvider = this@LatinLanguageProvider,
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
        if (candidate is WordSuggestionCandidate) {
            FlorisNative.insertWord(candidate.text.toString(), 100)
        }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { candidate.toString() }
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { candidate.toString() }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        ensureLoaded()
        return wordData.withLock { it.keys.toList() }
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        ensureLoaded()
        return (wordData.withLock { it[word] } ?: 0) / 255.0
    }
}
