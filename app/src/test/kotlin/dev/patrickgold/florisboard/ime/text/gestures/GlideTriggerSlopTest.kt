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
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Pins the glide trigger threshold to its MEASURED value. Captured
 * tap-slides travel up to 0.63 key-widths; the shortest real glide nets
 * about 1.76. The trigger must sit inside that gap, with a floor for
 * tiny keys and NO ceiling (a ceiling readmits the stray band on tall
 * keys — that exact change shipped twice and caused junk word commits
 * mid-typing both times). If this test is failing your change, read
 * COORDINATION.md before weakening it: the agreed path to a lower
 * trigger is a velocity gate validated on v2 timestamped traces.
 */
class GlideTriggerSlopTest : FunSpec({
    test("typical key size lands inside the measured stray/real gap") {
        val slop = GlideTypingGesture.Detector.triggerSlopFor(34.5f)
        // 0.85 key-widths at a typical 34.5dp key
        slop shouldBe 29.325f
        // strictly above the stray band (0.63 kw = 21.7dp here)
        slop shouldBeGreaterThan 34.5f * 0.63f
    }

    test("tiny keys keep the absolute floor") {
        GlideTypingGesture.Detector.triggerSlopFor(10f) shouldBe 24f
    }

    test("tall keys are NOT ceiling-capped") {
        // 60dp keys: 0.85 * 60 = 51 — a 18-20dp ceiling here is the bug
        GlideTypingGesture.Detector.triggerSlopFor(60f) shouldBe 51f
    }
})
