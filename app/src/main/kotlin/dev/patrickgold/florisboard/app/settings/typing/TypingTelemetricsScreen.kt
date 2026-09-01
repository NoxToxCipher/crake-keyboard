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

package dev.patrickgold.florisboard.app.settings.typing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.nlp.FlightRecorderManager
import dev.patrickgold.florisboard.ime.nlp.TelemetricsTimeWindow
import dev.patrickgold.florisboard.ime.nlp.TrendDirection
import dev.patrickgold.florisboard.ime.nlp.TypingTelemetricsManager
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import java.util.Locale

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val CyberAmber = Color(0xFFF59E0B)
private val NeonPink = Color(0xFFFF4081)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun TypingTelemetricsScreen() = FlorisScreen {
    title = "Typing Telemetrics"
    previewFieldVisible = false

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeSeries by TypingTelemetricsManager.timeSeriesData.collectAsState()
    val metrics = timeSeries.currentMetrics
    var liveTestText by remember { mutableStateOf("") }
    var isPurging by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        TypingTelemetricsManager.refreshTelemetrics(context)
    }

    content {
        // 1. Time Window Segmented Selector
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B101B)),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TelemetricsTimeWindow.entries.forEach { win ->
                    val isSelected = timeSeries.selectedWindow == win
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) ElectricCyan.copy(alpha = 0.22f) else Color.Transparent)
                            .clickable {
                                scope.launch {
                                    TypingTelemetricsManager.refreshTelemetrics(context, win)
                                }
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = win.label,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) ElectricCyan else TextMuted,
                        )
                    }
                }
            }
        }

        // 2. Trend & Evolution Diagnostic Card
        CrakeSectionHeader(title = "TREND & ADAPTATION OVER TIME")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, when (timeSeries.overallTrend) {
                TrendDirection.IMPROVING -> CyberEmerald.copy(alpha = 0.6f)
                TrendDirection.DEGRADING -> CyberAmber.copy(alpha = 0.6f)
                else -> CardBorder
            }),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val (icon, tint) = when (timeSeries.overallTrend) {
                            TrendDirection.IMPROVING -> Pair(Icons.Default.TrendingUp, CyberEmerald)
                            TrendDirection.DEGRADING -> Pair(Icons.Default.TrendingDown, CyberAmber)
                            TrendDirection.STEADY -> Pair(Icons.Default.TrendingFlat, ElectricCyan)
                            TrendDirection.INSUFFICIENT_DATA -> Pair(Icons.Default.Timeline, TextMuted)
                        }
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            val title = when (timeSeries.overallTrend) {
                                TrendDirection.IMPROVING -> "Improving Performance"
                                TrendDirection.DEGRADING -> "Fatigue / Strain Detected"
                                TrendDirection.STEADY -> "Steady & Consistent"
                                TrendDirection.INSUFFICIENT_DATA -> "Gathering History"
                            }
                            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                            val subtitle = when (timeSeries.selectedWindow) {
                                TelemetricsTimeWindow.LIVE_SESSION -> "Compared to prior hour"
                                TelemetricsTimeWindow.PAST_24_HOURS -> "Compared to yesterday"
                                TelemetricsTimeWindow.PAST_7_DAYS -> "Compared to prior 7 days"
                                TelemetricsTimeWindow.ALL_TIME -> "Historical trajectory"
                            }
                            Text(text = subtitle, fontSize = 11.sp, color = TextMuted)
                        }
                    }

                    // Trend Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(when (timeSeries.overallTrend) {
                                TrendDirection.IMPROVING -> CyberEmerald.copy(alpha = 0.15f)
                                TrendDirection.DEGRADING -> CyberAmber.copy(alpha = 0.15f)
                                else -> ElectricCyan.copy(alpha = 0.15f)
                            })
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = when (timeSeries.overallTrend) {
                                TrendDirection.IMPROVING -> "+ SPEED"
                                TrendDirection.DEGRADING -> "FATIGUE"
                                TrendDirection.STEADY -> "STEADY"
                                TrendDirection.INSUFFICIENT_DATA -> "BASELINE"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = when (timeSeries.overallTrend) {
                                TrendDirection.IMPROVING -> CyberEmerald
                                TrendDirection.DEGRADING -> CyberAmber
                                else -> ElectricCyan
                            },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Delta Stats Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(text = "SPEED DELTA", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        val wpmSign = if (timeSeries.deltaWpm >= 0f) "+" else ""
                        Text(
                            text = if (timeSeries.priorMetrics != null) String.format(Locale.US, "%s%.1f WPM", wpmSign, timeSeries.deltaWpm) else "--",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (timeSeries.deltaWpm >= 0f) CyberEmerald else CyberAmber,
                        )
                    }
                    Column {
                        Text(text = "ACCURACY DELTA", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        val accSign = if (timeSeries.deltaAccuracyPercent >= 0f) "+" else ""
                        Text(
                            text = if (timeSeries.priorMetrics != null) String.format(Locale.US, "%s%.1f%%", accSign, timeSeries.deltaAccuracyPercent) else "--",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (timeSeries.deltaAccuracyPercent >= 0f) CyberEmerald else CyberAmber,
                        )
                    }
                    Column {
                        Text(text = "GLIDE ADOPTION", fontSize = 10.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        val glideSign = if (timeSeries.deltaGlidePercent >= 0f) "+" else ""
                        Text(
                            text = if (timeSeries.priorMetrics != null) String.format(Locale.US, "%s%.1f%%", glideSign, timeSeries.deltaGlidePercent) else "--",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                        )
                    }
                }
            }
        }

        // 3. Section: Speed & Velocity
        CrakeSectionHeader(title = "TYPING SPEED & LATENCY")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Average Speed",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                    }
                    Text(
                        text = if (metrics.averageWpm > 0f) String.format(Locale.US, "%.1f WPM", metrics.averageWpm) else "-- WPM",
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricCyan,
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(text = "PEAK SPEED", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.peakWpm > 0f) String.format(Locale.US, "%.1f WPM", metrics.peakWpm) else "--",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberAmber,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Column {
                        Text(text = "CPM (CHARS/MIN)", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.averageCpm > 0f) String.format(Locale.US, "%.0f CPM", metrics.averageCpm) else "--",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Column {
                        Text(text = "FLIGHT LATENCY", fontSize = 11.sp, color = TextMuted, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (metrics.averageFlightTimeMs > 0L) "${metrics.averageFlightTimeMs} ms" else "--",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        // 4. Section: Input Distribution (Tap vs Glide)
        CrakeSectionHeader(title = "INPUT DISTRIBUTION (TAP VS. GLIDE)")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(ElectricCyan))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap: ${String.format(Locale.US, "%.1f%%", metrics.tapPercentage)} (${metrics.tapWordsTyped} w)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(CyberEmerald))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Glide: ${String.format(Locale.US, "%.1f%%", metrics.glidePercentage)} (${metrics.glideWordsTyped} w)",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                val glideProgress = if (metrics.totalWordsTyped > 0) (metrics.glidePercentage / 100f).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(ElectricCyan),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(glideProgress)
                            .height(14.dp)
                            .align(Alignment.CenterEnd)
                            .background(CyberEmerald),
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Total Recorded Words: ${metrics.totalWordsTyped}",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
            }
        }

        // 5. Section: Dual Accuracy Breakdown
        CrakeSectionHeader(title = "ACCURACY & EFFICIENCY")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Keyboard, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Tap Accuracy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", metrics.tapAccuracyPercent),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (metrics.tapAccuracyPercent >= 90f) CyberEmerald else CyberAmber,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "• ${metrics.totalTapKeystrokes} Keystrokes", fontSize = 11.sp, color = TextMuted)
                    Text(text = "• ${metrics.totalBackspaces} Backspaces", fontSize = 11.sp, color = TextMuted)
                    Text(text = "• ${metrics.totalAutocorrectSaves} Auto-Saves", fontSize = 11.sp, color = ElectricCyan)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Gesture, contentDescription = null, tint = CyberEmerald, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Glide Accuracy", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = String.format(Locale.US, "%.1f%%", metrics.glideAccuracyPercent),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (metrics.glideAccuracyPercent >= 85f) CyberEmerald else CyberAmber,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = "• ${metrics.totalGlideStrokes} Glides", fontSize = 11.sp, color = TextMuted)
                    Text(text = "• ${metrics.totalGlideReverts} Reverts", fontSize = 11.sp, color = TextMuted)
                    if (metrics.averageGlideVelocity > 0f) {
                        Text(text = "• ${String.format(Locale.US, "%.0f dp/s", metrics.averageGlideVelocity)}", fontSize = 11.sp, color = CyberEmerald)
                    }
                }
            }
        }

        // 6. Section: 7-Day Performance Timeline
        if (timeSeries.dailyBuckets.isNotEmpty()) {
            CrakeSectionHeader(title = "PAST 7 DAYS PERFORMANCE TIMELINE")
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        timeSeries.dailyBuckets.forEach { bucket ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = bucket.dayLabel,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (bucket.dayLabel == "Today") ElectricCyan else TextMuted,
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                val maxWpm = (timeSeries.dailyBuckets.map { it.averageWpm }.maxOrNull() ?: 100f).coerceAtLeast(40f)
                                val barHeightFraction = if (bucket.averageWpm > 0f) (bucket.averageWpm / maxWpm).coerceIn(0.15f, 1f) else 0.08f
                                Box(
                                    modifier = Modifier
                                        .width(16.dp)
                                        .height(48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0F172A)),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height((48 * barHeightFraction).dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (bucket.averageWpm > 0f) CyberEmerald else TextMuted.copy(alpha = 0.3f)),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (bucket.averageWpm > 0f) String.format(Locale.US, "%.0f", bucket.averageWpm) else "-",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7. Section: Live Typing Test Box
        CrakeSectionHeader(title = "LIVE TEST PREVIEW")
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Type or glide below to test and observe real-time telemetric calculations:",
                    fontSize = 12.sp,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = liveTestText,
                    onValueChange = {
                        liveTestText = it
                        scope.launch {
                            TypingTelemetricsManager.refreshTelemetrics(context)
                        }
                    },
                    placeholder = { Text("Glide or type here...", color = TextMuted) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF0B101B),
                        unfocusedContainerColor = Color(0xFF0B101B),
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                TypingTelemetricsManager.refreshTelemetrics(context)
                                context.showShortToast("Telemetrics refreshed")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan),
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Refresh", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            isPurging = true
                            scope.launch {
                                FlightRecorderManager.securePurgeDiagnostics(context)
                                TypingTelemetricsManager.refreshTelemetrics(context)
                                liveTestText = ""
                                isPurging = false
                                context.showShortToast("Telemetry logs reset")
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink),
                    ) {
                        Icon(imageVector = Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Reset Stats", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
