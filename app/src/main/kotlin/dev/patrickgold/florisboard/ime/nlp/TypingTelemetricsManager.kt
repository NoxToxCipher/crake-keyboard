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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TelemetricsTimeWindow(val label: String) {
    LIVE_SESSION("Live (1h)"),
    PAST_24_HOURS("Today (24h)"),
    PAST_7_DAYS("7 Days"),
    ALL_TIME("All-Time"),
}

enum class TrendDirection {
    IMPROVING,
    STEADY,
    DEGRADING,
    INSUFFICIENT_DATA,
}

data class DailyTrendBucket(
    val dayLabel: String,
    val dateKey: String,
    val wordCount: Int,
    val averageWpm: Float,
    val overallAccuracy: Float,
    val glidePercentage: Float,
)

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

data class TimeSeriesTelemetrics(
    val selectedWindow: TelemetricsTimeWindow = TelemetricsTimeWindow.ALL_TIME,
    val currentMetrics: TypingTelemetrics = TypingTelemetrics(),
    val priorMetrics: TypingTelemetrics? = null,
    val deltaWpm: Float = 0.0f,
    val deltaAccuracyPercent: Float = 0.0f,
    val deltaGlidePercent: Float = 0.0f,
    val speedTrend: TrendDirection = TrendDirection.INSUFFICIENT_DATA,
    val accuracyTrend: TrendDirection = TrendDirection.INSUFFICIENT_DATA,
    val overallTrend: TrendDirection = TrendDirection.INSUFFICIENT_DATA,
    val dailyBuckets: List<DailyTrendBucket> = emptyList(),
)

object TypingTelemetricsManager {
    private val _timeSeriesData = MutableStateFlow(TimeSeriesTelemetrics())
    val timeSeriesData = _timeSeriesData.asStateFlow()

    suspend fun refreshTelemetrics(context: Context, timeWindow: TelemetricsTimeWindow = _timeSeriesData.value.selectedWindow) = withContext(Dispatchers.IO) {
        val records = FlightRecorderManager.readRecentRecords(context, limit = 2000)
        val data = calculateTimeSeries(records, timeWindow)
        _timeSeriesData.value = data
    }

    fun setTimeWindow(context: Context, window: TelemetricsTimeWindow) {
        _timeSeriesData.value = _timeSeriesData.value.copy(selectedWindow = window)
    }

    data class ParsedRecord(
        val timestamp: Long,
        val action: String,
        val mode: String,
        val wpm: Float?,
        val cpm: Float?,
        val flightTimeMs: Long?,
        val velocity: Float?,
        val curvature: Float?,
        val isAutocorrected: Boolean,
        val rawLine: String,
    )

    private fun extractString(line: String, key: String): String? {
        val keyIdx = line.indexOf(key)
        if (keyIdx < 0) return null
        val start = keyIdx + key.length
        val end = line.indexOf('"', start)
        if (end < 0) return null
        return line.substring(start, end)
    }

    private fun extractLong(line: String, key: String): Long? {
        val keyIdx = line.indexOf(key)
        if (keyIdx < 0) return null
        val start = keyIdx + key.length
        var end = start
        while (end < line.length && line[end] in '0'..'9') {
            end++
        }
        if (end == start) return null
        return line.substring(start, end).toLongOrNull()
    }

    private fun extractFloat(line: String, key: String): Float? {
        val keyIdx = line.indexOf(key)
        if (keyIdx < 0) return null
        val start = keyIdx + key.length
        var end = start
        while (end < line.length && (line[end] in '0'..'9' || line[end] == '.')) {
            end++
        }
        if (end == start) return null
        return line.substring(start, end).toFloatOrNull()
    }

    private fun parseLine(line: String): ParsedRecord? {
        val action = extractString(line, "\"action\":\"") ?: return null
        val mode = extractString(line, "\"mode\":\"") ?: "UNKNOWN"
        val timestamp = extractLong(line, "\"timestamp\":") ?: 0L

        val wpm = extractFloat(line, "\"wpm\":")?.takeIf { it in 5.0f..300.0f }
        val cpm = extractFloat(line, "\"cpm\":")?.takeIf { it in 20.0f..1500.0f }
        val flight = extractLong(line, "\"flightTimeMs\":")?.takeIf { it in 10L..3000L }
        val vel = extractFloat(line, "\"velocity\":")?.takeIf { it > 0f }
        val curv = extractFloat(line, "\"curvature\":")?.takeIf { it > 0f }
        val isAutocorrected = line.contains("\"isAutocorrected\":true") || line.contains("\"correctedTo\"")

        return ParsedRecord(
            timestamp = timestamp,
            action = action,
            mode = mode,
            wpm = wpm,
            cpm = cpm,
            flightTimeMs = flight,
            velocity = vel,
            curvature = curv,
            isAutocorrected = isAutocorrected,
            rawLine = line,
        )
    }

    fun calculateTimeSeries(records: List<String>, window: TelemetricsTimeWindow, now: Long = System.currentTimeMillis()): TimeSeriesTelemetrics {
        if (records.isEmpty()) {
            return TimeSeriesTelemetrics(selectedWindow = window)
        }

        val parsed = records.mapNotNull { parseLine(it) }
        if (parsed.isEmpty()) {
            return TimeSeriesTelemetrics(selectedWindow = window)
        }

        val oneHourMs = 3600_000L
        val oneDayMs = 86400_000L
        val sevenDaysMs = 7 * oneDayMs

        val (currentWindowRecords, priorWindowRecords) = when (window) {
            TelemetricsTimeWindow.LIVE_SESSION -> {
                val current = parsed.filter { it.timestamp >= now - oneHourMs || it.timestamp == 0L }
                val prior = parsed.filter { it.timestamp in (now - 2 * oneHourMs)..<(now - oneHourMs) }
                Pair(current, prior)
            }
            TelemetricsTimeWindow.PAST_24_HOURS -> {
                val current = parsed.filter { it.timestamp >= now - oneDayMs || it.timestamp == 0L }
                val prior = parsed.filter { it.timestamp in (now - 2 * oneDayMs)..<(now - oneDayMs) }
                Pair(current, prior)
            }
            TelemetricsTimeWindow.PAST_7_DAYS -> {
                val current = parsed.filter { it.timestamp >= now - sevenDaysMs || it.timestamp == 0L }
                val prior = parsed.filter { it.timestamp in (now - 2 * sevenDaysMs)..<(now - sevenDaysMs) }
                Pair(current, prior)
            }
            TelemetricsTimeWindow.ALL_TIME -> {
                val midpoint = parsed.size / 2
                val current = parsed
                val prior = if (parsed.size >= 10) parsed.take(midpoint) else emptyList()
                Pair(current, prior)
            }
        }

        val currentMetrics = computeMetricsFromParsed(currentWindowRecords)
        val priorMetrics = if (priorWindowRecords.isNotEmpty()) computeMetricsFromParsed(priorWindowRecords) else null

        val deltaWpm = if (priorMetrics != null && priorMetrics.averageWpm > 0f) {
            currentMetrics.averageWpm - priorMetrics.averageWpm
        } else {
            0.0f
        }

        val deltaAcc = if (priorMetrics != null && priorMetrics.totalWordsTyped > 0) {
            currentMetrics.overallAccuracyPercent - priorMetrics.overallAccuracyPercent
        } else {
            0.0f
        }

        val deltaGlide = if (priorMetrics != null && priorMetrics.totalWordsTyped > 0) {
            currentMetrics.glidePercentage - priorMetrics.glidePercentage
        } else {
            0.0f
        }

        val speedTrend = when {
            priorMetrics == null || priorMetrics.averageWpm == 0f -> TrendDirection.INSUFFICIENT_DATA
            deltaWpm >= 1.5f -> TrendDirection.IMPROVING
            deltaWpm <= -2.0f -> TrendDirection.DEGRADING
            else -> TrendDirection.STEADY
        }

        val accTrend = when {
            priorMetrics == null || priorMetrics.totalWordsTyped == 0 -> TrendDirection.INSUFFICIENT_DATA
            deltaAcc >= 1.0f -> TrendDirection.IMPROVING
            deltaAcc <= -2.0f -> TrendDirection.DEGRADING
            else -> TrendDirection.STEADY
        }

        val overallTrend = when {
            speedTrend == TrendDirection.INSUFFICIENT_DATA -> TrendDirection.INSUFFICIENT_DATA
            speedTrend == TrendDirection.DEGRADING || accTrend == TrendDirection.DEGRADING -> TrendDirection.DEGRADING
            speedTrend == TrendDirection.IMPROVING || accTrend == TrendDirection.IMPROVING -> TrendDirection.IMPROVING
            else -> TrendDirection.STEADY
        }

        val dailyBuckets = computeDailyBuckets(parsed, now)

        return TimeSeriesTelemetrics(
            selectedWindow = window,
            currentMetrics = currentMetrics,
            priorMetrics = priorMetrics,
            deltaWpm = deltaWpm,
            deltaAccuracyPercent = deltaAcc,
            deltaGlidePercent = deltaGlide,
            speedTrend = speedTrend,
            accuracyTrend = accTrend,
            overallTrend = overallTrend,
            dailyBuckets = dailyBuckets,
        )
    }

    private fun computeDailyBuckets(records: List<ParsedRecord>, now: Long): List<DailyTrendBucket> {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val labelFormat = SimpleDateFormat("EEE", Locale.US)
        val oneDayMs = 86400_000L

        val dailyBucketsList = ArrayList<DailyTrendBucket>(7)
        val buckets = Array(7) { mutableListOf<ParsedRecord>() }

        for (rec in records) {
            val ts = if (rec.timestamp > 0) rec.timestamp else now
            val diff = now - ts
            if (diff >= 0) {
                val dayOffset = (diff / oneDayMs).toInt()
                if (dayOffset in 0..6) {
                    buckets[dayOffset].add(rec)
                }
            }
        }

        for (dayOffset in 6 downTo 0) {
            val dayTime = now - (dayOffset * oneDayMs)
            val dateKey = dateFormat.format(Date(dayTime))
            val dayLabel = if (dayOffset == 0) "Today" else labelFormat.format(Date(dayTime))
            val dayRecords = buckets[dayOffset]
            val m = computeMetricsFromParsed(dayRecords)
            dailyBucketsList.add(
                DailyTrendBucket(
                    dayLabel = dayLabel,
                    dateKey = dateKey,
                    wordCount = m.totalWordsTyped,
                    averageWpm = m.averageWpm,
                    overallAccuracy = m.overallAccuracyPercent,
                    glidePercentage = m.glidePercentage,
                )
            )
        }

        return dailyBucketsList
    }

    fun computeMetricsFromParsed(records: List<ParsedRecord>): TypingTelemetrics {
        if (records.isEmpty()) return TypingTelemetrics()

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

        for (rec in records) {
            when (rec.action) {
                "KEY_TAP" -> {
                    totalKeystrokes++
                    lastCommittedWasGlide = false
                    rec.wpm?.let { wpmList.add(it) }
                    rec.cpm?.let { cpmList.add(it) }
                    rec.flightTimeMs?.let { flightTimes.add(it) }
                }
                "GLIDE_STROKE" -> {
                    totalGlideStrokes++
                    lastCommittedWasGlide = true
                    rec.velocity?.let { glideVelocities.add(it) }
                    rec.curvature?.let { glideCurvatures.add(it) }
                }
                "WORD_COMMITTED", "SUGGESTION_PICKED" -> {
                    if (rec.mode == "GLIDING") {
                        totalGlideWords++
                        lastCommittedWasGlide = true
                    } else {
                        totalTapWords++
                        lastCommittedWasGlide = false
                    }
                    if (rec.isAutocorrected) {
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

    fun calculateMetrics(records: List<String>): TypingTelemetrics {
        return calculateTimeSeries(records, TelemetricsTimeWindow.ALL_TIME).currentMetrics
    }
}
