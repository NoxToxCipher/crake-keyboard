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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TokenRewindTrackerTest : FunSpec({

    test("Normal sequential typing without rewind does not trigger correction") {
        val tracker = TokenRewindTracker()
        var capturedCount = 0
        tracker.onCorrectionCaptured = { _, _, _, _ -> capturedCount++ }

        tracker.onTokenCommitted("hello")
        tracker.onCharacterTyped(" ")
        tracker.onTokenCommitted("world")
        tracker.onCharacterTyped(" ")

        capturedCount shouldBe 0
        tracker.getHistoryTokens() shouldBe listOf("hello", "world")
        tracker.getPendingRewind() shouldBe null
    }

    test("Multi-token backspace rewind captures delayed typo and replacement word") {
        val tracker = TokenRewindTracker()
        var capturedErased = ""
        var capturedReplacement = ""
        var capturedRewindDepth = 0
        var capturedDelay = 0

        tracker.onCorrectionCaptured = { erased, replacement, rewindDepth, delay ->
            capturedErased = erased
            capturedReplacement = replacement
            capturedRewindDepth = rewindDepth
            capturedDelay = delay
        }

        // 1. User types word1: "toi" + space
        tracker.onTokenCommitted("toi")
        tracker.onCharacterTyped(" ")

        // 2. User starts typing word2: "ar" (2 characters into next word)
        tracker.onCharacterTyped("a")
        tracker.onCharacterTyped("r")

        // 3. User realizes mistake in word1, presses backspace 5 times
        // Delete 'r'
        tracker.onCharacterDeleted("toi a")
        // Delete 'a'
        tracker.onCharacterDeleted("toi ")
        // Delete ' ' -> boundary crossing into 'toi'!
        tracker.onCharacterDeleted("toi")
        // Delete 'i'
        tracker.onCharacterDeleted("to")
        // Delete 'o'
        tracker.onCharacterDeleted("t")

        tracker.getPendingRewind() shouldNotBe null
        tracker.getPendingRewind()?.erasedToken shouldBe "toi"

        // 4. User types correct replacement word: "you" + space
        tracker.onCharacterTyped("y")
        tracker.onCharacterTyped("o")
        tracker.onCharacterTyped("u")
        tracker.onTokenCommitted("you")

        // 5. Verification
        capturedErased shouldBe "toi"
        capturedReplacement shouldBe "you"
        capturedDelay shouldBe 2 // User typed 2 chars of word2 before backspacing
        capturedRewindDepth shouldBe 5
        tracker.getPendingRewind() shouldBe null
    }

    test("Identical word retyping does not produce false positive") {
        val tracker = TokenRewindTracker()
        var capturedCount = 0
        tracker.onCorrectionCaptured = { _, _, _, _ -> capturedCount++ }

        tracker.onTokenCommitted("test")
        tracker.onCharacterTyped(" ")
        tracker.onCharacterTyped("a")

        tracker.onCharacterDeleted("test ")
        tracker.onCharacterDeleted("test")
        tracker.onCharacterDeleted("tes")

        // Retypes the exact same word
        tracker.onTokenCommitted("test")

        capturedCount shouldBe 0
    }

    test("An auto-committed replacement is not learned as the user's correction") {
        // Field loop (2026-09-13): the user erases a word, retypes it with a
        // slip, and the ENGINE's auto-commit lands ("lile" -> "lille"). That
        // word was never chosen by the user, yet it was recorded as their
        // correction and boosted +15 each time, until the wrong word outranked
        // the right one. Only a raw-typed or tapped replacement may teach.
        val tracker = TokenRewindTracker()
        var capturedCount = 0
        tracker.onCorrectionCaptured = { _, _, _, _ -> capturedCount++ }

        tracker.onTokenCommitted("like")
        tracker.onCharacterTyped(" ")
        tracker.onCharacterTyped("i")
        tracker.onCharacterDeleted("like ")
        tracker.onCharacterDeleted("like")
        tracker.onCharacterDeleted("lik")
        tracker.getPendingRewind()?.erasedToken shouldBe "like"

        // The engine replaces the retyped slip on space: not the user's word.
        tracker.onTokenCommitted("lille", learnAsCorrection = false)

        capturedCount shouldBe 0
        // The rewind is consumed either way: the next typed word is not a
        // replacement for the erased one.
        tracker.getPendingRewind() shouldBe null
        tracker.getHistoryTokens() shouldBe listOf("lille")
    }

    test("Erasing an engine word and typing another teaches the TYPED slip, never the engine's word") {
        // Review 2026-09-13: "thde" auto-committed "this"; the user erased
        // it and typed "the". The lesson is thde -> the. Recording this ->
        // the would have made every real "this" auto-correct away.
        val tracker = TokenRewindTracker()
        var captured: Pair<String, String>? = null
        tracker.onCorrectionCaptured = { erased, replacement, _, _ -> captured = erased to replacement }

        tracker.onTokenCommitted("this", learnAsCorrection = false, typedOriginal = "thde")
        tracker.onCharacterTyped(" ")
        tracker.onCharacterTyped("m")
        tracker.onCharacterDeleted("this ")
        tracker.onCharacterDeleted("this")
        tracker.onCharacterDeleted("thi")
        tracker.getPendingRewind()?.erasedToken shouldBe "thde"

        tracker.onTokenCommitted("the")
        captured shouldBe ("thde" to "the")
    }

    test("An engine word with no typed original is consumed by a rewind but teaches nothing") {
        val tracker = TokenRewindTracker()
        var capturedCount = 0
        tracker.onCorrectionCaptured = { _, _, _, _ -> capturedCount++ }

        tracker.onTokenCommitted("this", learnAsCorrection = false)
        tracker.onCharacterTyped(" ")
        tracker.onCharacterTyped("m")
        tracker.onCharacterDeleted("this ")
        tracker.onCharacterDeleted("this")
        tracker.onCharacterDeleted("thi")
        tracker.onTokenCommitted("the")

        capturedCount shouldBe 0
        tracker.getPendingRewind() shouldBe null
    }

    test("Explicit cursor repositioning cancels pending rewind") {
        val tracker = TokenRewindTracker()
        var capturedCount = 0
        tracker.onCorrectionCaptured = { _, _, _, _ -> capturedCount++ }

        tracker.onTokenCommitted("typo")
        tracker.onCharacterTyped(" ")
        tracker.onCharacterTyped("n")

        tracker.onCharacterDeleted("typo ")
        tracker.onCharacterDeleted("typo")

        tracker.getPendingRewind() shouldNotBe null

        // User taps somewhere else on screen
        tracker.onExplicitSelectionOrCursorJump()

        tracker.getPendingRewind() shouldBe null

        // Committing a new word in a different location does not correlate to old rewind
        tracker.onTokenCommitted("unrelated")
        capturedCount shouldBe 0
    }
})
