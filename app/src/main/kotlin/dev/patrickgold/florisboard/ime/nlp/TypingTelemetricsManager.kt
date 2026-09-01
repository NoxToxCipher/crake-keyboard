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

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class TypingTelemetrics(
    val totalWordsTyped: Int = 0,
    val tapWordsTyped: Int = 0,
    val glideWordsTyped: Int = 0,
    val glidePercentage: Float = 0.0f,
    val tapPercentage: Float = 0.0f,
    val averageWpm: Float = 0.0f,
    val peakWpm: Float = 0.0f,
    val averageCpm: Float = 0.0f,
    val averageFlightTimeMs: Long = 0L,
    val tapAccuracyPercent: Float = 100.0f,
    val glideAccuracyPercent: Float = 100.0f,
    val overallAccuracyPercent: Float = 100.0f,
    val totalTapKeystrokes: Int = 0,
    val totalBackspaces: Int = 0,
    val totalManualReverts: Int = 0,
    val totalAutocorrectSaves: Int = 0,
    val totalGlideStrokes: Int = 0,
    val totalGlideReverts: Int = 0,
    val averageGlideVelocity: Float = 0.0f,
    val averageGlideCurvature: Float = 0.0f,
)

object TypingTelemetricsManager {
    private val _telemetrics = MutableStateFlow(TypingTelemetrics())
    val telemetrics = _telemetrics.asStateFlow()

    suspend fun refreshTelemetrics(context: Context) = withContext(Dispatchers.IO) {
        val records = FlightRecorderManager.readRecentRecords(context, limit = 2000)
        val metrics = calculateMetrics(records)
        _telemetrics.value = metrics
    }

    fun calculateMetrics(records: List<String>): TypingTelemetrics {
        if (records.isEmpty()) {
            return TypingTelemetrics()
        }

        var totalTapWords = 0
        var totalGlideWords = 0
        var totalKeystrokes = 0
        var totalBackspaces = 0
        var totalManualReverts = 0
        var totalAutocorrectSaves = 0
        var totalGlideStrokes = 0
        var totalGlideReverts = 0

        val wpmList = mutableListOf<Float>()
        val cpmList = mutableListOf<Float>()
        val flightTimes = mutableListOf<Long>()
        val glideVelocities = mutableListOf<Float>()
        val glideCurvatures = mutableListOf<Float>()

        var lastCommittedWasGlide = false

        for (line in records) {
            val actionMatch = Regex("\"action\":\"([^\"]+)\"").find(line)
            val action = actionMatch?.groupValues?.getOrNull(1) ?: continue
            val modeMatch = Regex("\"mode\":\"([^\"]+)\"").find(line)
            val mode = modeMatch?.groupValues?.getOrNull(1) ?: "UNKNOWN"

            when (action) {
                "KEY_TAP" -> {
                    totalKeystrokes++
                    lastCommittedWasGlide = false
                    val wpmMatch = Regex("\"wpm\":([0-9.]+)").find(line)
                    wpmMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let {
                        if (it in 5.0f..300.0f) wpmList.add(it)
                    }
                    val cpmMatch = Regex("\"cpm\":([0-9.]+)").find(line)
                    cpmMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let {
                        if (it in 20.0f..1500.0f) cpmList.add(it)
                    }
                    val flightMatch = Regex("\"flightTimeMs\":([0-9]+)").find(line)
                    flightMatch?.groupValues?.getOrNull(1)?.toLongOrNull()?.let {
                        if (it in 10L..3000L) flightTimes.add(it)
                    }
                }
                "GLIDE_STROKE" -> {
                    totalGlideStrokes++
                    lastCommittedWasGlide = true
                    val velMatch = Regex("\"velocity\":([0-9.]+)").find(line)
                    velMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let {
                        if (it > 0f) glideVelocities.add(it)
                    }
                    val curMatch = Regex("\"curvature\":([0-9.]+)").find(line)
                    curMatch?.groupValues?.getOrNull(1)?.toFloatOrNull()?.let {
                        if (it > 0f) glideCurvatures.add(it)
                    }
                }
                "WORD_COMMITTED", "SUGGESTION_PICKED" -> {
                    if (mode == "GLIDING") {
                        totalGlideWords++
                        lastCommittedWasGlide = true
                    } else {
                        totalTapWords++
                        lastCommittedWasGlide = false
                    }
                    if (line.contains("\"isAutocorrected\":true") || line.contains("\"correctedTo\"")) {
                        totalAutocorrectSaves++
                    }
                }
                "BACKSPACE_DELETE" -> {
                    totalBackspaces++
                    if (lastCommittedWasGlide) {
                        totalGlideReverts++
                    }
                }
                "MANUAL_REVERT", "RETROACTIVE_REWIND" -> {
                    totalManualReverts++
                    if (lastCommittedWasGlide) {
                        totalGlideReverts++
                    }
                }
            }
        }

        val totalWords = (totalTapWords + totalGlideWords).coerceAtLeast(0)
        val glidePct = if (totalWords > 0) (totalGlideWords.toFloat() / totalWords * 100f) else 0f
        val tapPct = if (totalWords > 0) (totalTapWords.toFloat() / totalWords * 100f) else 0f

        val avgWpm = if (wpmList.isNotEmpty()) wpmList.average().toFloat() else 0f
        val peakWpm = if (wpmList.isNotEmpty()) wpmList.maxOrNull() ?: 0f else 0f
        val avgCpm = if (cpmList.isNotEmpty()) cpmList.average().toFloat() else 0f
        val avgFlight = if (flightTimes.isNotEmpty()) flightTimes.average().toLong() else 0L

        val tapAccuracy = if (totalKeystrokes > 0) {
            val errors = totalBackspaces + totalManualReverts
            ((totalKeystrokes - errors).coerceAtLeast(0).toFloat() / totalKeystrokes * 100f).coerceIn(0f, 100f)
        } else {
            100.0f
        }

        val glideAccuracy = if (totalGlideStrokes > 0) {
            ((totalGlideStrokes - totalGlideReverts).coerceAtLeast(0).toFloat() / totalGlideStrokes * 100f).coerceIn(0f, 100f)
        } else {
            100.0f
        }

        val overallAccuracy = if (totalWords > 0) {
            ((totalTapWords * tapAccuracy + totalGlideWords * glideAccuracy) / totalWords).coerceIn(0f, 100f)
        } else {
            100.0f
        }

        val avgGlideVel = if (glideVelocities.isNotEmpty()) glideVelocities.average().toFloat() else 0f
        val avgGlideCurv = if (glideCurvatures.isNotEmpty()) glideCurvatures.average().toFloat() else 0f

        return TypingTelemetrics(
            totalWordsTyped = totalWords,
            tapWordsTyped = totalTapWords,
            glideWordsTyped = totalGlideWords,
            glidePercentage = glidePct,
            tapPercentage = tapPct,
            averageWpm = avgWpm,
            peakWpm = peakWpm,
            averageCpm = avgCpm,
            averageFlightTimeMs = avgFlight,
            tapAccuracyPercent = tapAccuracy,
            glideAccuracyPercent = glideAccuracy,
            overallAccuracyPercent = overallAccuracy,
            totalTapKeystrokes = totalKeystrokes,
            totalBackspaces = totalBackspaces,
            totalManualReverts = totalManualReverts,
            totalAutocorrectSaves = totalAutocorrectSaves,
            totalGlideStrokes = totalGlideStrokes,
            totalGlideReverts = totalGlideReverts,
            averageGlideVelocity = avgGlideVel,
            averageGlideCurvature = avgGlideCurv,
        )
    }
}
