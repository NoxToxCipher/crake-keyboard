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
import io.kotest.matchers.floats.plusOrMinus

class TypingTelemetricsTest : FunSpec({
    test("empty records return zeroed telemetrics with 100% baseline accuracy") {
        val metrics = TypingTelemetricsManager.calculateMetrics(emptyList())
        metrics.totalWordsTyped shouldBe 0
        metrics.tapWordsTyped shouldBe 0
        metrics.glideWordsTyped shouldBe 0
        metrics.glidePercentage shouldBe 0f
        metrics.tapPercentage shouldBe 0f
        metrics.averageWpm shouldBe 0f
        metrics.tapAccuracyPercent shouldBe 100f
        metrics.glideAccuracyPercent shouldBe 100f
        metrics.overallAccuracyPercent shouldBe 100f
    }

    test("accurately calculates typing speed, glide percentage, and tap accuracy") {
        val sampleRecords = listOf(
            // Tap events
            "{\"action\":\"KEY_TAP\",\"wpm\":60.0,\"cpm\":300.0,\"flightTimeMs\":150}",
            "{\"action\":\"KEY_TAP\",\"wpm\":80.0,\"cpm\":400.0,\"flightTimeMs\":130}",
            "{\"action\":\"KEY_TAP\",\"wpm\":100.0,\"cpm\":500.0,\"flightTimeMs\":110}",
            // 1 backspace while typing
            "{\"action\":\"BACKSPACE_DELETE\"}",
            "{\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"hello\",\"committedTo\":\"hello\"}",
            "{\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"world\",\"committedTo\":\"world\"}",
            "{\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"test\",\"committedTo\":\"test\"}",
            // 2 Glide events
            "{\"action\":\"GLIDE_STROKE\",\"velocity\":450.0,\"curvature\":1.2}",
            "{\"action\":\"WORD_COMMITTED\",\"mode\":\"GLIDING\",\"rawInput\":\"keyboard\",\"committedTo\":\"keyboard\"}",
            "{\"action\":\"GLIDE_STROKE\",\"velocity\":480.0,\"curvature\":1.1}",
            "{\"action\":\"WORD_COMMITTED\",\"mode\":\"GLIDING\",\"rawInput\":\"typing\",\"committedTo\":\"typing\"}"
        )

        val metrics = TypingTelemetricsManager.calculateMetrics(sampleRecords)
        metrics.totalWordsTyped shouldBe 5
        metrics.tapWordsTyped shouldBe 3
        metrics.glideWordsTyped shouldBe 2

        metrics.tapPercentage shouldBe (60.0f plusOrMinus 0.1f)
        metrics.glidePercentage shouldBe (40.0f plusOrMinus 0.1f)

        metrics.averageWpm shouldBe (80.0f plusOrMinus 0.1f)
        metrics.peakWpm shouldBe (100.0f plusOrMinus 0.1f)
        metrics.averageCpm shouldBe (400.0f plusOrMinus 0.1f)
        metrics.averageFlightTimeMs shouldBe 130L

        // 3 keystrokes, 1 backspace -> 2 clean / 3 = 66.67% tap accuracy
        metrics.tapAccuracyPercent shouldBe (66.67f plusOrMinus 0.5f)

        // 2 glides, 0 reverts -> 100% glide accuracy
        metrics.glideAccuracyPercent shouldBe 100f
        metrics.totalGlideStrokes shouldBe 2
        metrics.averageGlideVelocity shouldBe (465.0f plusOrMinus 1.0f)
    }
})
