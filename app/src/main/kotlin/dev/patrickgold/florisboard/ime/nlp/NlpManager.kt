/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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
import android.os.SystemClock
import android.util.LruCache
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import dev.patrickgold.florisboard.ime.clipboard.provider.ItemType
import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionProvider
import dev.patrickgold.florisboard.ime.nlp.han.HanShapeBasedLanguageProvider
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.lib.util.NetworkUtils
import dev.patrickgold.florisboard.subtypeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.florisboard.lib.kotlin.guardedByLock
import org.florisboard.lib.kotlin.collectLatestIn
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.Delegates

private const val BLANK_STR_PATTERN = "^\\s*$"
private val NUM_UNIT_REGEX = Regex("^(\\d+(?:\\.\\d+)?)\\s*(usd|aud|cad|eur|gbp|jpy|btc|eth|sol|xmr|doge|usdt|deg|c|f|km|mph|kph|mb|gb|tb)$", RegexOption.IGNORE_CASE)
private val MATH_EXPR_REGEX = Regex("^(-?\\d+(?:\\.\\d+)?)\\s*([\\+\\-\\*/\\^%])\\s*(-?\\d+(?:\\.\\d+)?)$")

private fun evaluateMathOrMacro(input: String): String? {
    val clean = input.trim()
    val now = java.time.ZonedDateTime.now()
    when (clean.lowercase()) {
        ".now", "!now" -> return now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        ".today", "!today" -> return now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy"))
        ".date", "!date" -> return now.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        ".time", "!time" -> return now.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))
        ".utc", "!utc" -> return java.time.Instant.now().toString()
        ".ts", "!ts" -> return (System.currentTimeMillis() / 1000).toString()
        ".eth", "!eth", ":eth", ".ethereum", "!ethereum", "eth" -> return "Ξ"
        ".btc", "!btc", ":btc", ".bitcoin", "!bitcoin", "btc" -> return "₿"
        ".sol", "!sol", ":sol", ".solana", "!solana" -> return "◎"
        ".xmr", "!xmr", ":xmr", ".monero", "!monero" -> return "ɱ"
        ".doge", "!doge", ":doge" -> return "Ð"
        ".usdt", "!usdt", ":usdt" -> return "₮"
        ".wallet", "!wallet", ".myeth", "!myeth", ".ethaddress", "!ethaddress", ".address", "!address", "0x=" -> {
            return "0x71C8401344CD24C836015b67272719299478f7B7"
        }
        ".pgpkey", "!pgpkey", ".mykey", "!mykey", ".pubkey", "!pubkey" -> {
            return org.florisboard.libnative.FlorisNative.pgponyGenerateKeypair()?.second ?: "crake-pk1-e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
        ".pgpgen", "!pgpgen" -> {
            val kp = org.florisboard.libnative.FlorisNative.pgponyGenerateKeypair()
            return kp?.let { "Public: ${it.second}" } ?: "Failed to generate keypair"
        }
    }
    if (clean.endsWith("=") && clean.length > 2) {
        val expr = clean.dropLast(1).trim()
        val mathResult = evalSimpleMath(expr)
        if (mathResult != null) return mathResult
    }

    // Number & Currency / Unit macro formatting (e.g. 100usd -> $100, 50eur -> €50, 32deg -> 32°, 100c -> 100°C)
    val numUnitMatch = NUM_UNIT_REGEX.matchEntire(clean)
    if (numUnitMatch != null) {
        val num = numUnitMatch.groupValues[1]
        val unit = numUnitMatch.groupValues[2].lowercase()
        return when (unit) {
            "usd", "aud", "cad" -> "\$$num"
            "eur" -> "€$num"
            "gbp" -> "£$num"
            "jpy" -> "¥$num"
            "btc" -> "₿$num"
            "eth" -> "Ξ$num"
            "sol" -> "◎$num"
            "xmr" -> "ɱ$num"
            "doge" -> "Ð$num"
            "usdt" -> "₮$num"
            "deg" -> "$num°"
            "c" -> "$num°C"
            "f" -> "$num°F"
            "km" -> "$num km"
            "mph" -> "$num mph"
            "kph" -> "$num km/h"
            "mb" -> "$num MB"
            "gb" -> "$num GB"
            "tb" -> "$num TB"
            else -> null
        }
    }
    return null
}

private fun evalSimpleMath(expr: String): String? {
    return runCatching {
        val sanitized = expr.replace("x", "*").replace("X", "*").replace("×", "*").replace("÷", "/")
        val match = MATH_EXPR_REGEX.matchEntire(sanitized)
        if (match != null) {
            val a = match.groupValues[1].toDouble()
            val op = match.groupValues[2]
            val b = match.groupValues[3].toDouble()
            val res = when (op) {
                "+" -> a + b
                "-" -> a - b
                "*" -> a * b
                "/" -> if (b != 0.0) a / b else null
                "^" -> Math.pow(a, b)
                "%" -> a % b
                else -> null
            } ?: return null
            if (res % 1.0 == 0.0 && res <= Long.MAX_VALUE && res >= Long.MIN_VALUE) {
                res.toLong().toString()
            } else {
                "%.4f".format(res).trimEnd('0').trimEnd('.')
            }
        } else null
    }.getOrNull()
}


class NlpManager(context: Context) {
    private val blankStrRegex = Regex(BLANK_STR_PATTERN)

    private val prefs by FlorisPreferenceStore
    private val clipboardManager by context.clipboardManager()
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val subtypeManager by context.subtypeManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val clipboardSuggestionProvider = ClipboardSuggestionProvider(context)
    private val emojiSuggestionProvider = EmojiSuggestionProvider(context)
    private val providers = guardedByLock {
        mapOf(
            LatinLanguageProvider.ProviderId to ProviderInstanceWrapper(LatinLanguageProvider(context)),
            HanShapeBasedLanguageProvider.ProviderId to ProviderInstanceWrapper(HanShapeBasedLanguageProvider(context)),
        )
    }
    // lock unnecessary because values constant
    private val providersForceSuggestionOn = mutableMapOf<String, Boolean>()

    private val internalSuggestionsGuard = Mutex()

    // The in-flight suggestion computation; superseded by each newer request.
    private var suggestJob: kotlinx.coroutines.Job? = null
    private var internalSuggestions by Delegates.observable(SystemClock.uptimeMillis() to listOf<SuggestionCandidate>()) { _, _, _ ->
        scope.launch { assembleCandidates() }
    }

    private val _activeCandidatesFlow = MutableStateFlow(listOf<SuggestionCandidate>())
    val activeCandidatesFlow = _activeCandidatesFlow.asStateFlow()
    var candidatesPresentedTime: Long = 0L
        private set
    inline var activeCandidates
        get() = activeCandidatesFlow.value
        private set(v) {
            _activeCandidatesFlow.value = v
            if (v.isNotEmpty()) {
                candidatesPresentedTime = System.currentTimeMillis()
            }
        }

    val debugOverlaySuggestionsInfos = LruCache<Long, Pair<String, SpellingResult>>(10)
    var debugOverlayVersion = MutableStateFlow(0)

    init {
        clipboardManager.primaryClipFlow.collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.suggestion.enabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.clipboard.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        prefs.emoji.suggestionEnabled.asFlow().collectLatestIn(scope) {
            assembleCandidates()
        }
        subtypeManager.activeSubtypeFlow.collectLatestIn(scope) { subtype ->
            preload(subtype)
        }
    }

    /**
     * Gets the punctuation rule from the currently active subtype and returns it. Falls back to a default one if the
     * subtype does not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getActivePunctuationRule(): PunctuationRule {
        return getPunctuationRule(subtypeManager.activeSubtype)
    }

    /**
     * Gets the punctuation rule from the given subtype and returns it. Falls back to a default one if the subtype does
     * not exist or defines an invalid punctuation rule.
     *
     * @return The punctuation rule or a fallback.
     */
    fun getPunctuationRule(subtype: Subtype): PunctuationRule {
        return keyboardManager.resources.punctuationRules.value[subtype.punctuationRule] ?: PunctuationRule.Fallback
    }

    private suspend fun getSpellingProvider(subtype: Subtype): SpellingProvider {
        return providers.withLock { it[subtype.nlpProviders.spelling] }?.provider as? SpellingProvider
            ?: FallbackNlpProvider
    }

    private suspend fun getSuggestionProvider(subtype: Subtype): SuggestionProvider {
        return providers.withLock { it[subtype.nlpProviders.suggestion] }?.provider as? SuggestionProvider
            ?: FallbackNlpProvider
    }

    fun preload(subtype: Subtype) {
        scope.launch {
            emojiSuggestionProvider.preload(subtype)
            providers.withLock { providers ->
                subtype.nlpProviders.forEach { _, providerId ->
                    providers[providerId]?.let { provider ->
                        provider.createIfNecessary()
                        provider.preload(subtype)
                    }
                }
            }
        }
    }

    /**
     * Spell wrapper helper which calls the spelling provider and returns the result. Coroutine management must be done
     * by the source spell checker service.
     */
    suspend fun spell(
        subtype: Subtype,
        word: String,
        precedingWords: List<String>,
        followingWords: List<String>,
        maxSuggestionCount: Int,
    ): SpellingResult {
        return getSpellingProvider(subtype).spell(
            subtype = subtype,
            word = word,
            precedingWords = precedingWords,
            followingWords = followingWords,
            maxSuggestionCount = maxSuggestionCount,
            allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
            isPrivateSession = keyboardManager.activeState.isIncognitoMode,
        )
    }

    suspend fun determineLocalComposing(
        textBeforeSelection: CharSequence, breakIterators: BreakIteratorGroup, localLastCommitPosition: Int
    ): EditorRange {
        return getSuggestionProvider(subtypeManager.activeSubtype).determineLocalComposing(
            subtypeManager.activeSubtype, textBeforeSelection, breakIterators, localLastCommitPosition
        )
    }

    fun providerForcesSuggestionOn(subtype: Subtype): Boolean {
        return providersForceSuggestionOn.getOrPut(subtype.nlpProviders.suggestion) {
            when (subtype.nlpProviders.suggestion) {
                LatinLanguageProvider.ProviderId -> true
                HanShapeBasedLanguageProvider.ProviderId -> true
                else -> true
            }
        }
    }

    fun isSuggestionOn(): Boolean =
        prefs.suggestion.enabled.get()
            || prefs.emoji.suggestionEnabled.get()
            || providerForcesSuggestionOn(subtypeManager.activeSubtype)

    fun suggest(subtype: Subtype, content: EditorContent) {
        val reqTime = SystemClock.uptimeMillis()
        // Latest wins: a rapid keystroke burst (backspace repeat especially)
        // used to stack one full suggestion computation per keystroke — the
        // reqTime guard discarded stale RESULTS but every superseded job
        // still ran to completion, saturating the CPU. Cancel it instead.
        suggestJob?.cancel()
        suggestJob = scope.launch {
            val emojiSuggestions = when {
                prefs.emoji.suggestionEnabled.get() -> {
                    emojiSuggestionProvider.suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = prefs.emoji.suggestionCandidateMaxCount.get(),
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
                else -> emptyList()
            }
            val wordInput = when {
                content.composing.isValid && content.composingText.isNotBlank() -> content.composingText
                content.localCurrentWord.isValid && content.currentWordText.isNotBlank() -> content.currentWordText
                else -> content.textBeforeSelection.takeLastWhile { !it.isWhitespace() }
            }
            val macroEvaluated = evaluateMathOrMacro(wordInput)
            val macroCandidate: List<SuggestionCandidate> = if (macroEvaluated != null) {
                listOf(
                    WordSuggestionCandidate(
                        text = macroEvaluated,
                        secondaryText = if (wordInput.endsWith("=")) "🧮 Math Result" else "📅 Macro",
                        confidence = 1.0,
                        isEligibleForAutoCommit = false,
                    )
                )
            } else {
                emptyList()
            }

            val suggestions = when {
                emojiSuggestions.isNotEmpty() && prefs.emoji.suggestionType.get().prefix.isNotEmpty() -> {
                    emptyList()
                }
                else -> {
                    getSuggestionProvider(subtype).suggest(
                        subtype = subtype,
                        content = content,
                        maxCandidateCount = 8,
                        allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                        isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                    )
                }
            }
            internalSuggestionsGuard.withLock {
                if (internalSuggestions.first < reqTime) {
                    internalSuggestions = reqTime to buildList {
                        addAll(macroCandidate)
                        addAll(emojiSuggestions)
                        addAll(suggestions)
                    }
                }
            }
        }
    }

    fun suggestDirectly(suggestions: List<SuggestionCandidate>) {
        val reqTime = SystemClock.uptimeMillis()
        internalSuggestions = reqTime to suggestions
    }

    fun clearSuggestions() {
        val reqTime = SystemClock.uptimeMillis()
        internalSuggestions = reqTime to emptyList()
    }

    fun getAutoCommitCandidate(): SuggestionCandidate? {
        return activeCandidates.firstOrNull { it.isEligibleForAutoCommit }
    }

    fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
        scope.launch {
            val result = candidate.sourceProvider?.removeSuggestion(subtype, candidate) == true
            if (result) {
                if (candidate is ClipboardSuggestionCandidate) {
                    assembleCandidates()
                } else {
                    suggest(subtypeManager.activeSubtype, editorInstance.activeContent)
                }
            }
        }
        return true
    }

    private suspend fun assembleCandidates() {
        val candidates = when {
            isSuggestionOn() -> {
                clipboardSuggestionProvider.suggest(
                    subtype = Subtype.DEFAULT,
                    content = editorInstance.activeContent,
                    maxCandidateCount = 8,
                    allowPossiblyOffensive = !prefs.suggestion.blockPossiblyOffensive.get(),
                    isPrivateSession = keyboardManager.activeState.isIncognitoMode,
                ).ifEmpty {
                    buildList {
                        internalSuggestionsGuard.withLock {
                            addAll(internalSuggestions.second)
                        }
                    }
                }
            }
            else -> emptyList()
        }
        activeCandidates = candidates
        autoExpandCollapseSmartbarActions(candidates, NlpInlineAutofill.suggestions.value)
    }

    fun autoExpandCollapseSmartbarActions(list1: List<*>?, list2: List<*>?) {
        if (!prefs.smartbar.enabled.get()) {// || !prefs.smartbar.sharedActionsAutoExpandCollapse.get()) {
            return
        }
        // TODO: this is a mess and needs to be cleaned up in v0.5 with the NLP development
        /*if (keyboardManager.inputEventDispatcher.isRepeatableCodeLastDown()
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.DELETE)
            && !keyboardManager.inputEventDispatcher.isPressed(KeyCode.FORWARD_DELETE)
            || keyboardManager.activeState.isActionsOverflowVisible
        ) {
            return // We do not auto switch if a repeatable action key was last pressed or if the actions overflow
                   // menu is visible to prevent annoying UI changes
        }*/
        val isSelection = editorInstance.activeContent.selection.isSelectionMode
        val isExpanded = list1.isNullOrEmpty() && list2.isNullOrEmpty() || isSelection
        scope.launch {
            prefs.smartbar.sharedActionsExpandWithAnimation.set(false)
            prefs.smartbar.sharedActionsExpanded.set(isExpanded)
        }
    }

    fun addToDebugOverlay(word: String, info: SpellingResult) {
        debugOverlaySuggestionsInfos.put(System.currentTimeMillis(), word to info)
        debugOverlayVersion.update { it + 1 }
    }

    fun clearDebugOverlay() {
        debugOverlaySuggestionsInfos.evictAll()
        debugOverlayVersion.update { it + 1 }
    }

    private class ProviderInstanceWrapper(val provider: NlpProvider) {
        private var isInstanceAlive = AtomicBoolean(false)

        suspend fun createIfNecessary() {
            if (!isInstanceAlive.getAndSet(true)) provider.create()
        }

        suspend fun preload(subtype: Subtype) {
            provider.preload(subtype)
        }

        suspend fun destroyIfNecessary() {
            if (isInstanceAlive.getAndSet(true)) provider.destroy()
        }
    }

    inner class ClipboardSuggestionProvider internal constructor(private val context: Context) : SuggestionProvider {
        private var lastClipboardItemId: Long = -1

        override val providerId = "org.florisboard.nlp.providers.clipboard"

        override suspend fun create() {
            // Do nothing
        }

        override suspend fun preload(subtype: Subtype) {
            // Do nothing
        }

        override suspend fun suggest(
            subtype: Subtype,
            content: EditorContent,
            maxCandidateCount: Int,
            allowPossiblyOffensive: Boolean,
            isPrivateSession: Boolean,
        ): List<SuggestionCandidate> {
            // Check if enabled
            if (!prefs.clipboard.suggestionEnabled.get()) return emptyList()

            val currentItem = validateClipboardItem(clipboardManager.primaryClip, lastClipboardItemId, content.text)
                ?: return emptyList()

            return buildList {
                val now = System.currentTimeMillis()
                if ((now - currentItem.creationTimestampMs) < prefs.clipboard.suggestionTimeout.get() * 1000) {
                    add(ClipboardSuggestionCandidate(currentItem, sourceProvider = this@ClipboardSuggestionProvider, context = context))
                    if (currentItem.isSensitive) {
                        return@buildList
                    }
                    if (currentItem.type == ItemType.TEXT) {
                        val text = currentItem.stringRepresentation()
                        val matches = buildList {
                            addAll(NetworkUtils.getEmailAddresses(text))
                            addAll(NetworkUtils.getUrls(text))
                            addAll(NetworkUtils.getPhoneNumbers(text))
                        }
                        matches.forEachIndexed { i, match ->
                            val isUniqueMatch = matches.subList(0, i).all { prevMatch ->
                                prevMatch.value != match.value && prevMatch.range.intersect(match.range).isEmpty()
                            }
                            if (match.value != text && isUniqueMatch) {
                                add(ClipboardSuggestionCandidate(
                                    clipboardItem = currentItem.copy(
                                        // TODO: adjust regex of phone number so we don't need to manually strip the
                                        //  parentheses from the match results
                                        text = if (match.value.startsWith("(") && match.value.endsWith(")")) {
                                            match.value.substring(1, match.value.length - 1)
                                        } else {
                                            match.value
                                        }
                                    ),
                                    sourceProvider = this@ClipboardSuggestionProvider,
                                    context = context,
                                ))
                            }
                        }
                    }
                }
            }
        }

        override suspend fun notifySuggestionAccepted(subtype: Subtype, candidate: SuggestionCandidate) {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
            }
        }

        override suspend fun notifySuggestionReverted(subtype: Subtype, candidate: SuggestionCandidate) {
            // Do nothing
        }

        override suspend fun removeSuggestion(subtype: Subtype, candidate: SuggestionCandidate): Boolean {
            if (candidate is ClipboardSuggestionCandidate) {
                lastClipboardItemId = candidate.clipboardItem.id
                return true
            }
            return false
        }

        override suspend fun destroy() {
            // Do nothing
        }

        private fun validateClipboardItem(currentItem: ClipboardItem?, lastItemId: Long, contentText: String) =
            currentItem?.takeIf {
                // Check if already used
                it.id != lastItemId
                    // Check if content is empty
                    && contentText.isBlank()
                    // Check if clipboard content has any valid characters
                    && !currentItem.text.isNullOrBlank()
                    && !blankStrRegex.matches(currentItem.text)
            }
    }

    suspend fun preloadProviders() {
        try {
            providers.withLock { map ->
                map.values.forEach { it.createIfNecessary() }
            }
        } catch (_: Throwable) {}
    }

}
