/*
 * Copyright (C) 2026 The Crake Contributors
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

import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import org.florisboard.libnative.FlorisNative
import java.util.ArrayDeque

/**
 * Multi-Token Retroactive Rewind Harvester.
 *
 * Captures delayed corrections where a user made a typo in word1, typed a space and started
 * typing word2, and then rapidly backspaced across the space boundary to erase and replace word1.
 *
 * Correlates the original typo with the replacement word and feeds it into:
 * 1. On-Device Real-Time NLP Learning (FlorisNative.recordPersonalCorrection).
 * 2. Privacy-Shielded Local Flight Recorder (FlightRecorderManager.logRetroactiveRewind).
 */
class TokenRewindTracker(
    private val maxHistory: Int = 5,
    private val rewindTimeoutMs: Long = 15_000L,
) {
    data class TokenEntry(
        val text: String,
        val length: Int,
        val timestamp: Long = System.currentTimeMillis(),
    )

    data class ActiveRewind(
        val erasedToken: String,
        val partialNextWordChars: Int,
        var rewindDepth: Int,
        val startTime: Long = System.currentTimeMillis(),
    )

    private val tokenHistory = ArrayDeque<TokenEntry>()
    private var pendingRewind: ActiveRewind? = null
    private var consecutiveBackspaces = 0
    private var lastBackspaceTime = 0L
    private var currentPartialWordLength = 0
    private var initialPartialWordChars = 0

    // Callback for testing & decoupling
    var onCorrectionCaptured: ((erased: String, replacement: String, rewindDepth: Int, cognitiveDelay: Int) -> Unit)? = null

    @Synchronized
    fun onCharacterTyped(char: String) {
        val now = System.currentTimeMillis()
        if (now - lastBackspaceTime > 1500L) {
            consecutiveBackspaces = 0
            initialPartialWordChars = 0
        }
        if (char == " " || char == "\n" || char == "." || char == "!" || char == "?" || char == ",") {
            currentPartialWordLength = 0
            initialPartialWordChars = 0
        } else {
            currentPartialWordLength += char.length
        }
    }

    @Synchronized
    fun onTokenCommitted(token: String, isRawTyping: Boolean = false, keyVariation: KeyVariation = KeyVariation.NORMAL, packageName: String? = null) {
        val cleanToken = token.trim()
        if (cleanToken.isEmpty()) return

        val now = System.currentTimeMillis()
        val rewind = pendingRewind

        // Check if this newly committed token replaces an erased token from an active rewind
        if (rewind != null && (now - rewind.startTime <= rewindTimeoutMs)) {
            val erased = rewind.erasedToken.trim()
            if (erased.isNotEmpty() && !erased.equals(cleanToken, ignoreCase = true) && erased.length >= 2 && cleanToken.length >= 2) {
                // High-value retroactive correction captured!
                onCorrectionCaptured?.invoke(erased, cleanToken, rewind.rewindDepth, rewind.partialNextWordChars)

                // 1. Train on-device Rust engine immediately
                try {
                    if (FlorisNative.isAvailable()) {
                        FlorisNative.recordPersonalCorrection(erased, cleanToken)
                    }
                } catch (e: Throwable) {
                    // Safety containment
                }

                // 2. Log enriched telemetry event
                FlightRecorderManager.logRetroactiveRewind(
                    erasedToken = erased,
                    replacementToken = cleanToken,
                    rewindDepth = rewind.rewindDepth,
                    cognitiveDelayChars = rewind.partialNextWordChars,
                    keyVariation = keyVariation,
                    packageName = packageName,
                )
            }
            pendingRewind = null
        }

        // Add to rolling history
        if (tokenHistory.size >= maxHistory) {
            tokenHistory.removeFirst()
        }
        tokenHistory.addLast(TokenEntry(cleanToken, cleanToken.length, now))
        currentPartialWordLength = 0
        initialPartialWordChars = 0
        consecutiveBackspaces = 0
    }

    @Synchronized
    fun onCharacterDeleted(textBefore: String) {
        val now = System.currentTimeMillis()
        if (now - lastBackspaceTime <= 2000L) {
            consecutiveBackspaces++
        } else {
            consecutiveBackspaces = 1
            initialPartialWordChars = currentPartialWordLength
        }
        if (consecutiveBackspaces == 1) {
            initialPartialWordChars = currentPartialWordLength
        }
        lastBackspaceTime = now

        val rewind = pendingRewind
        if (rewind != null) {
            rewind.rewindDepth++
            return
        }

        // Detect if backspacing has erased across a space boundary into a previous token
        if (consecutiveBackspaces >= 2 && tokenHistory.isNotEmpty()) {
            val lastCommitted = tokenHistory.peekLast() ?: return
            
            // If the current partial word was completely erased and we are now deleting the previous token
            if (currentPartialWordLength <= 0 || consecutiveBackspaces > initialPartialWordChars) {
                val targetToken = tokenHistory.removeLast()
                
                pendingRewind = ActiveRewind(
                    erasedToken = targetToken.text,
                    partialNextWordChars = initialPartialWordChars,
                    rewindDepth = consecutiveBackspaces,
                    startTime = now,
                )
            }
        }
        if (currentPartialWordLength > 0) {
            currentPartialWordLength--
        }
    }

    @Synchronized
    fun onExplicitSelectionOrCursorJump() {
        // Clear pending rewind on arbitrary jumps to prevent false correlation
        pendingRewind = null
        consecutiveBackspaces = 0
        currentPartialWordLength = 0
        initialPartialWordChars = 0
    }

    @Synchronized
    fun reset() {
        tokenHistory.clear()
        pendingRewind = null
        consecutiveBackspaces = 0
        currentPartialWordLength = 0
        initialPartialWordChars = 0
    }

    @Synchronized
    fun getHistoryTokens(): List<String> = tokenHistory.map { it.text }

    @Synchronized
    fun getPendingRewind(): ActiveRewind? = pendingRewind
}
