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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

/**
 * Pins the delete-key swipe contract end to end. Each test names the
 * regression it guards against — this gesture has already broken three
 * separate ways and every one of them reached the user's daily build.
 * If this test is failing your change, read COORDINATION.md before
 * weakening it.
 */
class DeleteSwipePolicyTest : FunSpec({
    val leftward = listOf(
        SwipeGesture.Direction.LEFT,
        SwipeGesture.Direction.UP_LEFT,
        SwipeGesture.Direction.DOWN_LEFT,
    )
    val notLeftward = SwipeGesture.Direction.entries - leftward.toSet()

    test("THE flick contract: leftward release, default pref, no selection -> whole word deleted") {
        // This single decision is the delete-word flick feature for every
        // user on default settings. Regression 2026-08-28: the flick's own
        // scrub selection made hasSelection true and this fallback never ran.
        for (dir in leftward) {
            DeleteSwipePolicy.onUpAction(dir, SwipeAction.DELETE_CHARACTERS_PRECISELY, hasSelection = false) shouldBe SwipeAction.DELETE_WORD
        }
    }

    test("a real scrub selection keeps the word intact on release") {
        // The user scrubbed a precise selection; release deletes that
        // selection (dispatch layer) and must NOT eat a word on top.
        for (dir in leftward) {
            DeleteSwipePolicy.onUpAction(dir, SwipeAction.DELETE_CHARACTERS_PRECISELY, hasSelection = true).shouldBeNull()
            DeleteSwipePolicy.consumesUp(dir, SwipeAction.DELETE_CHARACTERS_PRECISELY) shouldBe true
        }
    }

    test("explicit DELETE_WORD and DELETE_CHARACTER prefs pass through") {
        for (dir in leftward) {
            DeleteSwipePolicy.onUpAction(dir, SwipeAction.DELETE_WORD, false) shouldBe SwipeAction.DELETE_WORD
            DeleteSwipePolicy.onUpAction(dir, SwipeAction.DELETE_CHARACTER, false) shouldBe SwipeAction.DELETE_CHARACTER
        }
    }

    test("non-leftward releases never delete and never consume") {
        for (dir in notLeftward) {
            DeleteSwipePolicy.onUpAction(dir, SwipeAction.DELETE_WORD, false).shouldBeNull()
            DeleteSwipePolicy.consumesUp(dir, SwipeAction.DELETE_WORD) shouldBe false
        }
    }

    test("non-delete prefs never delete and never consume") {
        for (action in listOf(SwipeAction.NO_ACTION, SwipeAction.SELECT_CHARACTERS_PRECISELY, SwipeAction.SELECT_WORDS_PRECISELY)) {
            DeleteSwipePolicy.onUpAction(SwipeGesture.Direction.LEFT, action, false).shouldBeNull()
            DeleteSwipePolicy.consumesUp(SwipeGesture.Direction.LEFT, action) shouldBe false
        }
    }

    test("a consumed release always exists wherever an action fires") {
        // If onUpAction returns an action but consumesUp said false, the
        // release would both delete AND type the re-targeted key.
        for (dir in SwipeGesture.Direction.entries) {
            for (action in SwipeAction.entries) {
                for (sel in listOf(true, false)) {
                    if (DeleteSwipePolicy.onUpAction(dir, action, sel) != null) {
                        DeleteSwipePolicy.consumesUp(dir, action) shouldBe true
                    }
                }
            }
        }
    }

    test("the scrub holds through the whole measured flick window") {
        // Flicks measure 61-103ms; scrubbing inside that window creates the
        // selection that blocks the word-delete fallback.
        for (age in listOf(0L, 25L, 61L, 103L, 149L)) {
            DeleteSwipePolicy.scrubMayBegin(SwipeAction.DELETE_CHARACTERS_PRECISELY, age) shouldBe false
        }
        DeleteSwipePolicy.scrubMayBegin(SwipeAction.DELETE_CHARACTERS_PRECISELY, 150L) shouldBe true
        DeleteSwipePolicy.scrubMayBegin(SwipeAction.DELETE_CHARACTERS_PRECISELY, 600L) shouldBe true
    }

    test("the SELECT pref is never held back - it has no fallback to land on") {
        for (age in listOf(0L, 25L, 149L, 600L)) {
            DeleteSwipePolicy.scrubMayBegin(SwipeAction.SELECT_CHARACTERS_PRECISELY, age) shouldBe true
        }
    }

    test("end-to-end replay of the real captured flick: classify, direction, action") {
        // The full chain on live phone data (tracker dead, default prefs):
        // classification -> direction -> policy must land on DELETE_WORD.
        val classified = SwipeGesture.Detector.classifiesAsSwipe(
            -153.86667f, 24.0f, 0.0f, 0.0f, 103L, 450.0, 32.0,
        )
        classified shouldBe true
        val direction = SwipeGesture.Detector.detectDirection(-153.86667, 24.0)
        direction shouldBe SwipeGesture.Direction.LEFT
        DeleteSwipePolicy.onUpAction(direction, SwipeAction.DELETE_CHARACTERS_PRECISELY, hasSelection = false) shouldBe SwipeAction.DELETE_WORD
    }
})
