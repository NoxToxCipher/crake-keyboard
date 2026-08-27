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
import dev.patrickgold.florisboard.ime.nlp.MergedWordSuggestionCandidate
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

    /**
     * Whether a load attempt succeeded. The map's non-emptiness used to be
     * the guard, but the blob path keeps the map empty (the corpus lives
     * native-side), so it needs its own flag — without it every
     * ensureLoaded() re-ran the full blob load. Guarded by the wordData lock.
     */
    private var dictLoaded = false

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
            if (!dictLoaded) {
                dictLoaded = if (loadFromBlob(words)) {
                    true
                } else {
                    loadFromJson(words)
                    // JSON semantics unchanged: retry on next call only while
                    // the map stayed empty (i.e. the load failed).
                    words.isNotEmpty()
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
            // No JVM mirror of the dictionary any more: the native corpus
            // store serves getListOfWords/getFrequencyForWord instead. The
            // map stays empty on this path — it only fills on JSON fallback.
            Log.i(
                "CrakeStartup",
                "dict load (blob): assetRead=${tRead - tStart}ms nativeLoad(1 JNI call)=${tNative - tRead}ms " +
                    "total=${tNative - tStart}ms words=$nativeCount",
            )
            flogInfo { "Loaded $nativeCount dictionary words from CRKD blob" }
            // Bigram LM rides the same load: ids in the CRKB table index the
            // dictionary blob's entry order, so it must load after the
            // dictionary. Optional — on any failure suggestions simply run
            // without context re-ranking.
            try {
                val bgBytes = appContext.assets.open("ime/dict/bigrams.crkb").use { it.readBytes() }
                val tBigram = SystemClock.elapsedRealtime()
                val pairs = FlorisNative.loadBigramBlob(bgBytes)
                Log.i(
                    "CrakeStartup",
                    "bigram load: read+parse=${SystemClock.elapsedRealtime() - tNative}ms " +
                        "(native=${SystemClock.elapsedRealtime() - tBigram}ms) pairs=$pairs",
                )
            } catch (e: Exception) {
                flogDebug { "CRKB bigram load failed (context re-ranking off): ${e.message}" }
            }
            // Restore what the user has taught this keyboard (learned words
            // + correction habits). App-private file; absent on first run.
            try {
                learnedStore.load()?.let {
                    val restored = FlorisNative.importLearned(it)
                    Log.i("CrakeStartup", "learned state restored: $restored words")
                }
                offsetsStore.load()?.let {
                    val keys = FlorisNative.importTouchOffsets(it)
                    Log.i("CrakeStartup", "touch offsets restored: $keys keys")
                }
            } catch (e: Exception) {
                flogDebug { "learned state restore failed: ${e.message}" }
            }
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
        // Nearest preceding word (when the spell checker provides sentence
        // context) lets the bigram re-ranker order replacements sensibly.
        val prev = precedingWords.firstOrNull().orEmpty()
        val suggestions = FlorisNative.suggest(word, maxSuggestionCount, prev)
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

        if (query.isBlank()) {
            // Nothing being composed: offer NEXT-WORD predictions for the
            // word just committed (personal pairs outrank the shipped LM).
            // Plain suggestions only — nothing here may auto-commit.
            val prevCommitted = content.textBeforeSelection
                .trimEnd()
                .takeLastWhile { it.isLetter() || it == '\'' }
                .toString()
            if (prevCommitted.isBlank() || !FlorisNative.isAvailable()) return emptyList()
            // Private sessions predict from the shipped model only: what
            // this keyboard has learned about its user stays off the
            // screen the user marked private.
            return FlorisNative.predictNextWords(
                prevCommitted,
                3,
                includePersonal = !isPrivateSession,
            ).map { word ->
                WordSuggestionCandidate(
                    text = word,
                    confidence = 0.5,
                    isEligibleForAutoCommit = false,
                    sourceProvider = this,
                )
            }
        }

        // Previous token, for spurious-space repair ("shou kd" -> "should").
        val prevToken = content.textBeforeSelection
            .dropLast(query.length)
            .trimEnd()
            .takeLastWhile { !it.isWhitespace() }
        // One more token back: context witness for two-slip merge repair
        // ("I ho or" -> the "I" arbitrates hope vs door).
        val precedingToken = content.textBeforeSelection
            .dropLast(query.length)
            .trimEnd()
            .dropLast(prevToken.length)
            .trimEnd()
            .takeLastWhile { !it.isWhitespace() }
            .toString()
        lastPrevToken = prevToken.toString()

        return buildList {
            // 0. Spurious mid-word space repair: offered first, never
            // auto-committed — the user taps it deliberately and BOTH
            // fragments are replaced (see MergedWordSuggestionCandidate).
            if (FlorisNative.isAvailable() && prevToken.isNotEmpty()) {
                val merged = FlorisNative.mergeRepair(prevToken, query, precedingToken)
                if (merged != null) {
                    add(
                        MergedWordSuggestionCandidate(
                            text = merged,
                            secondaryText = "$prevToken $query",
                            confidence = 0.9,
                            sourceProvider = this@LatinLanguageProvider,
                        )
                    )
                } else if (precedingToken.isNotEmpty()) {
                    // Three-fragment repair ("cha nbn ges"): witness is one
                    // more token back.
                    val witness = content.textBeforeSelection
                        .dropLast(query.length).trimEnd()
                        .dropLast(prevToken.length).trimEnd()
                        .dropLast(precedingToken.length).trimEnd()
                        .takeLastWhile { !it.isWhitespace() }
                        .toString()
                    val merged3 = FlorisNative.mergeRepair3(precedingToken, prevToken.toString(), query.toString(), witness)
                    if (merged3 != null) {
                        add(
                            MergedWordSuggestionCandidate(
                                text = merged3,
                                secondaryText = "$precedingToken $prevToken $query",
                                confidence = 0.9,
                                sourceProvider = this@LatinLanguageProvider,
                                fragments = 3,
                            )
                        )
                    }
                }
            }

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
                    val candidates = FlorisNative.suggest(cleanWordQuery, maxCandidateCount, prevToken)
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

    private var lastRevertedWord: String? = null
    private var lastRevertedTimestamp: Long = 0L

    /** Prev token captured during the most recent suggest() call, so an
     *  acceptance can record the personal bigram (prev, accepted). */
    @Volatile private var lastPrevToken: String = ""

    override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
        // Never log candidate content: on debug builds flogDebug writes to
        // logcat, and typed words must not leave the app even there.
        flogDebug { "suggestion accepted (${candidate.javaClass.simpleName})" }
        if (candidate is WordSuggestionCandidate) {
            val acceptedWord = candidate.text.toString()
            val now = SystemClock.elapsedRealtime()
            val reverted = lastRevertedWord
            if (reverted != null && now - lastRevertedTimestamp < 10000L && !reverted.equals(acceptedWord, ignoreCase = true)) {
                FlorisNative.recordPersonalCorrection(reverted, acceptedWord)
                lastRevertedWord = null
            }
            FlorisNative.insertWord(acceptedWord, 100)
            // Personal context: the user wrote acceptedWord after the last
            // observed previous token. Incognito never reaches this path.
            if (lastPrevToken.isNotEmpty()) {
                FlorisNative.recordPersonalBigram(lastPrevToken, acceptedWord)
            }
            persistLearnedState()
        }
    }

    /**
     * Writes the learned state to app-private storage so it survives
     * restarts. Called after learn events (rare, tap-driven); the blob is
     * KB-scale, capped in the native layer. Incognito sessions never reach
     * this path — acceptance callbacks are gated upstream.
     */
    private val learnedStore by lazy { LearnedStateStore(appContext.filesDir, "crake_learned.crkl") }
    private val offsetsStore by lazy { LearnedStateStore(appContext.filesDir, "crake_touch.crkt") }

    private fun persistLearnedState() {
        try {
            val data = FlorisNative.exportLearned() ?: return
            learnedStore.save(data)
            FlorisNative.exportTouchOffsets()?.let { offsetsStore.save(it) }
        } catch (e: Exception) {
            flogDebug { "learned state persist failed: ${e.message}" }
        }
    }

    override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
        flogDebug { "suggestion reverted (${candidate.javaClass.simpleName})" }
        lastRevertedWord = candidate.text.toString()
        lastRevertedTimestamp = SystemClock.elapsedRealtime()
    }

    override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        flogDebug { "suggestion removal requested (${candidate.javaClass.simpleName})" }
        return false
    }

    override suspend fun getListOfWords(subtype: Subtype): List<String> {
        ensureLoaded()
        // The map only holds data on the JSON fallback path; the blob path
        // keeps the corpus native-side. Same 49,981 words either way.
        val fromMap = wordData.withLock { it.keys.toList() }
        if (fromMap.isNotEmpty()) return fromMap
        return FlorisNative.corpusWords().toList()
    }

    override suspend fun getFrequencyForWord(subtype: Subtype, word: String): Double {
        ensureLoaded()
        val fromMap = wordData.withLock { it[word] }
        if (fromMap != null) return fromMap / 255.0
        return FlorisNative.corpusFrequency(word) / 255.0
    }
}
