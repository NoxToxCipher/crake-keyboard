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
