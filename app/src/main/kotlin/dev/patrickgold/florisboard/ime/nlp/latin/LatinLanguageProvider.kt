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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.libnative.FlorisNative

class LatinLanguageProvider(context: Context) : SpellingProvider, SuggestionProvider {
    companion object {
        const val ProviderId = "org.florisboard.nlp.providers.latin"
    }

    private val appContext by context.appContext()

    override val providerId = ProviderId

    override suspend fun create() {
        // Native core is initialized automatically by FlorisNative
    }

    override suspend fun preload(subtype: Subtype) = withContext(Dispatchers.IO) {
        // Preload standard words into native Trie if needed
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
        val query = content.composingText.toString().ifBlank { return emptyList() }
        if (!FlorisNative.isAvailable()) return emptyList()

        val rawCandidates = FlorisNative.suggest(query, maxCandidateCount)
        return rawCandidates.mapIndexed { index, candidate ->
            WordSuggestionCandidate(
                text = candidate,
                secondaryText = null,
                confidence = 1.0 - (index * 0.1),
                isEligibleForAutoCommit = index == 0,
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
        return emptyList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        return 1.0
    }

    override suspend fun destroy() {
        // Native cleanup handled on process teardown
    }
}
