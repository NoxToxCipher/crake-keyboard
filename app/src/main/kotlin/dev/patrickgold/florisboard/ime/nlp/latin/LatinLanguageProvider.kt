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
import dev.patrickgold.florisboard.appContext
import dev.patrickgold.florisboard.ime.core.Subtype
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

    private suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        wordData.withLock { words ->
            if (words.isEmpty()) {
                try {
                    val rawData = appContext.assets.readText("ime/dict/data.json")
                    val jsonData = Json.decodeFromString(wordDataSerializer, rawData)
                    words.putAll(jsonData)

                    // Populate native Rust Trie with dictionary words
                    for ((word, freq) in jsonData) {
                        FlorisNative.insertWord(word, freq)
                    }
                    flogInfo { "Loaded ${jsonData.size} dictionary words into native Rust Trie" }
                } catch (e: Exception) {
                    flogDebug { "Error loading dictionary: ${e.message}" }
                }
            }
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
        return if (suggestions.contains(word)) {
            SpellingResult.validWord()
        } else if (suggestions.isNotEmpty()) {
            SpellingResult.typo(suggestions.toTypedArray())
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
            content.composingText.isNotBlank() -> content.composingText
            content.currentWordText.isNotBlank() -> content.currentWordText
            else -> content.textBeforeSelection.takeLastWhile { it.isLetter() || it == '\'' }
        }.trim()

        if (query.isBlank()) return emptyList()
        if (!FlorisNative.isAvailable()) return emptyList()

        val rawCandidates = FlorisNative.suggest(query, maxCandidateCount)
        return rawCandidates.mapIndexed { index, candidate ->
            WordSuggestionCandidate(
                text = candidate,
                secondaryText = null,
                confidence = 1.0 - (index * 0.1),
                isEligibleForAutoCommit = false, // Suggestions are tap-to-complete, preventing spacebar from hijacking typed words
                sourceProvider = this@LatinLanguageProvider,
            )
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
        return wordData.withLock { it.getOrDefault(word, 0) / 255.0 }
    }

    override suspend fun destroy() {
    }
}
