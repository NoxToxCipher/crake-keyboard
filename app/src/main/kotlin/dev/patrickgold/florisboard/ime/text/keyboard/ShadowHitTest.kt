/*
 * Copyright (C) 2026 The CrakeBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.keyboard

import android.util.Log
import dev.patrickgold.florisboard.BuildConfig
import org.florisboard.libnative.FlorisNative
import java.util.concurrent.atomic.AtomicLong

/**
 * Shadow-mode comparator for the Rust hit tester (floris-core `hit_test`).
 *
 * Kotlin's `TextKeyboard.getKeyForPos` stays authoritative; on every resolved
 * touch the same point is handed to the native tester and the answers are
 * compared. Nothing the user sees depends on the native result — this exists
 * only to build the divergence record that decides whether Rust may take
 * over key resolution later. Debug builds only, so release carries none of
 * this work on its touch path.
 *
 * Read the record in logcat under the `CrakeShadow` tag: divergences log
 * immediately (they should never happen), a summary line lands every
 * [SUMMARY_EVERY] comparisons.
 */
object ShadowHitTest {
    private const val TAG = "CrakeShadow"
    private const val SUMMARY_EVERY = 1000L

    val enabled: Boolean
        get() = BuildConfig.DEBUG && FlorisNative.isAvailable()

    private val comparisons = AtomicLong(0)
    private val divergences = AtomicLong(0)
    private val skippedStale = AtomicLong(0)

    /** Uploads a laid-out keyboard's touch bounds; returns the generation or -1. */
    fun uploadLayout(keyboard: TextKeyboard): Int {
        if (!enabled) return -1
        return try {
            val flat = FloatArray(keyboard.keyCount * 4)
            // Per-key labels so the native side can learn per-key touch
            // offsets; non-letter keys get a placeholder and are ignored.
            val labels = StringBuilder(keyboard.keyCount)
            var i = 0
            for (key in keyboard.keys()) {
                flat[i++] = key.touchBounds.left
                flat[i++] = key.touchBounds.top
                flat[i++] = key.touchBounds.right
                flat[i++] = key.touchBounds.bottom
                val code = key.computedData.code
                labels.append(if (code in 32..0xFFFF) code.toChar() else ' ')
            }
            FlorisNative.hitSetKeys(flat, labels.toString())
        } catch (e: Throwable) {
            Log.w(TAG, "layout upload failed: $e")
            -1
        }
    }

    /**
     * Compares Kotlin's answer ([kotlinIndex], -1 for null) against the
     * native tester. Never throws and never changes the caller's result.
     */
    fun compare(generation: Int, x: Float, y: Float, kotlinIndex: Int) {
        if (!enabled || generation < 0) return
        try {
            val nativeIndex = FlorisNative.hitTest(generation, x, y)
            if (nativeIndex == -2) {
                skippedStale.incrementAndGet()
                return
            }
            val n = comparisons.incrementAndGet()
            if (nativeIndex != kotlinIndex) {
                val d = divergences.incrementAndGet()
                Log.w(
                    TAG,
                    "DIVERGENCE #$d at ($x, $y) gen=$generation: kotlin=$kotlinIndex native=$nativeIndex",
                )
            }
            // n == 1 proves in the field that the shadow is actually running;
            // without it, silence could also mean "never engaged".
            if (n == 1L || n % SUMMARY_EVERY == 0L) {
                Log.i(
                    TAG,
                    "hit tests: $n, divergences: ${divergences.get()}, stale-skipped: ${skippedStale.get()}",
                )
            }
        } catch (e: Throwable) {
            Log.w(TAG, "shadow compare failed: $e")
        }
    }
}
