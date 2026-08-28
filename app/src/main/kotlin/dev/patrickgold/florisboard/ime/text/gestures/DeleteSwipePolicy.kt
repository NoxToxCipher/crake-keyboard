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

/**
 * Every decision the delete-key swipe makes, extracted pure so the whole
 * chain is replayable in JVM tests. This gesture has broken three separate
 * ways (glide coupling ate it; the flick's own scrub selection blocked the
 * word-delete fallback; VelocityTracker read 0.0 and killed classification),
 * so no decision in it is allowed to live inline in the controller where a
 * refactor can silently change it. If DeleteSwipePolicyTest is failing your
 * change, the gesture's contract is what you changed — read COORDINATION.md
 * before weakening the test.
 */
object DeleteSwipePolicy {
    private val LEFTWARD = setOf(
        SwipeGesture.Direction.LEFT,
        SwipeGesture.Direction.UP_LEFT,
        SwipeGesture.Direction.DOWN_LEFT,
    )

    /**
     * Whether the precise character scrub may begin at this stroke age.
     * A flick (measured 61-103ms) must never scrub: its selection would
     * block the word-delete fallback on release. Only the DELETE pref is
     * held back — the SELECT pref has no fallback to land on, so holding
     * it would turn short scrubs into nothing at all.
     */
    fun scrubMayBegin(action: SwipeAction, ageMs: Long): Boolean =
        action != SwipeAction.DELETE_CHARACTERS_PRECISELY ||
            ageMs >= SwipeGesture.DELETE_SCRUB_MIN_AGE_MS

    /**
     * The action a classified leftward release executes, or null for none.
     * DELETE_CHARACTERS_PRECISELY falls back to a whole-word delete when no
     * scrub selection exists — that fallback is the entire flick feature;
     * a selection means the user was scrubbing and release must not eat a
     * word on top of it.
     */
    fun onUpAction(
        direction: SwipeGesture.Direction,
        action: SwipeAction,
        hasSelection: Boolean,
    ): SwipeAction? {
        if (direction !in LEFTWARD) return null
        return when (action) {
            SwipeAction.DELETE_WORD, SwipeAction.DELETE_CHARACTER -> action
            SwipeAction.DELETE_CHARACTERS_PRECISELY -> if (!hasSelection) SwipeAction.DELETE_WORD else null
            else -> null
        }
    }

    /**
     * Whether a classified release on the delete key is consumed (true even
     * when onUpAction returns null for DELETE_CHARACTERS_PRECISELY with a
     * selection — the dispatch layer deletes the selection instead, and the
     * release must not fall through to type a key).
     */
    fun consumesUp(direction: SwipeGesture.Direction, action: SwipeAction): Boolean =
        direction in LEFTWARD && (
            action == SwipeAction.DELETE_WORD ||
                action == SwipeAction.DELETE_CHARACTER ||
                action == SwipeAction.DELETE_CHARACTERS_PRECISELY
            )
}
