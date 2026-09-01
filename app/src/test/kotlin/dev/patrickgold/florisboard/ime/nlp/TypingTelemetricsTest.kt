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
        val series = TypingTelemetricsManager.calculateTimeSeries(emptyList(), TelemetricsTimeWindow.ALL_TIME)
        series.currentMetrics.totalWordsTyped shouldBe 0
        series.currentMetrics.tapWordsTyped shouldBe 0
        series.currentMetrics.glideWordsTyped shouldBe 0
        series.currentMetrics.glidePercentage shouldBe 0f
        series.currentMetrics.tapPercentage shouldBe 0f
        series.currentMetrics.averageWpm shouldBe 0f
        series.currentMetrics.tapAccuracyPercent shouldBe 100f
        series.currentMetrics.glideAccuracyPercent shouldBe 100f
        series.currentMetrics.overallAccuracyPercent shouldBe 100f
        series.overallTrend shouldBe TrendDirection.INSUFFICIENT_DATA
    }

    test("accurately calculates typing speed, glide percentage, and tap accuracy") {
        val now = 1756708800000L
        val sampleRecords = listOf(
            // Tap events
            "{\"timestamp\":$now,\"action\":\"KEY_TAP\",\"wpm\":60.0,\"cpm\":300.0,\"flightTimeMs\":150}",
            "{\"timestamp\":$now,\"action\":\"KEY_TAP\",\"wpm\":80.0,\"cpm\":400.0,\"flightTimeMs\":130}",
            "{\"timestamp\":$now,\"action\":\"KEY_TAP\",\"wpm\":100.0,\"cpm\":500.0,\"flightTimeMs\":110}",
            // 1 backspace while typing
            "{\"timestamp\":$now,\"action\":\"BACKSPACE_DELETE\"}",
            "{\"timestamp\":$now,\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"hello\",\"committedTo\":\"hello\"}",
            "{\"timestamp\":$now,\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"world\",\"committedTo\":\"world\"}",
            "{\"timestamp\":$now,\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"test\",\"committedTo\":\"test\"}",
            // 2 Glide events
            "{\"timestamp\":$now,\"action\":\"GLIDE_STROKE\",\"velocity\":450.0,\"curvature\":1.2}",
            "{\"timestamp\":$now,\"action\":\"WORD_COMMITTED\",\"mode\":\"GLIDING\",\"rawInput\":\"keyboard\",\"committedTo\":\"keyboard\"}",
            "{\"timestamp\":$now,\"action\":\"GLIDE_STROKE\",\"velocity\":480.0,\"curvature\":1.1}",
            "{\"timestamp\":$now,\"action\":\"WORD_COMMITTED\",\"mode\":\"GLIDING\",\"rawInput\":\"typing\",\"committedTo\":\"typing\"}"
        )

        val series = TypingTelemetricsManager.calculateTimeSeries(sampleRecords, TelemetricsTimeWindow.ALL_TIME, now)
        val metrics = series.currentMetrics
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

    test("accurately tracks improvement trends over time") {
        val now = 1756708800000L
        val yesterday = now - 3600_000L * 30 // 30 hours ago (prior 24h window)
        val today = now - 3600_000L * 2     // 2 hours ago (current 24h window)

        val records = listOf(
            // Prior 24h: 50 WPM
            "{\"timestamp\":$yesterday,\"action\":\"KEY_TAP\",\"wpm\":50.0,\"cpm\":250.0}",
            "{\"timestamp\":$yesterday,\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"old\",\"committedTo\":\"old\"}",
            // Today (current 24h): 70 WPM (+20 WPM improvement)
            "{\"timestamp\":$today,\"action\":\"KEY_TAP\",\"wpm\":70.0,\"cpm\":350.0}",
            "{\"timestamp\":$today,\"action\":\"WORD_COMMITTED\",\"mode\":\"TYPING\",\"rawInput\":\"new\",\"committedTo\":\"new\"}"
        )

        val series = TypingTelemetricsManager.calculateTimeSeries(records, TelemetricsTimeWindow.PAST_24_HOURS, now)
        series.deltaWpm shouldBe (20.0f plusOrMinus 0.1f)
        series.speedTrend shouldBe TrendDirection.IMPROVING
        series.overallTrend shouldBe TrendDirection.IMPROVING
    }
})
