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

package dev.patrickgold.florisboard.app.settings.gestures

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val CyberAmber = Color(0xFFF59E0B)
private val TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun GesturesScreen() = FlorisScreen {
    title = "Gestures & Flicks"
    previewFieldVisible = true

    content {
        // Hero Banner Card: 1D-CNN Neural Glide & Trackpad Engine
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "1D-CNN NEURAL GLIDE ENGINE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                        )
                        Text(
                            text = "Sub-3µs Rust gesture decoder with 6-channel kinematics & temporal convolutions",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(CyberEmerald.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.FlashOn,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "RUST 1D-CNN ACTIVE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = CyberEmerald,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(ElectricCyan.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "6-CHANNEL KINEMATICS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                        )
                    }
                }
            }
        }

        // 1. GLIDE TYPING
        CrakeSectionHeader(title = "Continuous Glide Typing", badgeText = "GLIDE", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.glide.enabled,
            title = "Continuous Glide Typing",
            summary = "Slide finger across letters to type words continuously",
            icon = Icons.Default.Gesture,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.glide.showTrail,
            title = "Neon Aurora Particle Trail",
            summary = "Draw electric cyan particle trail following gesture strokes",
            accentColor = ElectricCyan,
            enabledIf = { prefs.glide.enabled.get() },
        )
        DialogSliderPreference(
            prefs.glide.trailDuration,
            title = "Trail Fade Duration",
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 50,
            max = 600,
            stepIncrement = 25,
            enabledIf = { prefs.glide.enabled.get() && prefs.glide.showTrail.get() },
        )
        CrakeRadioPreference(
            pref = prefs.glide.immediateBackspaceDeletesWord,
            title = "Backspace Deletes Whole Glided Word",
            summary = "Pressing backspace right after a glided word deletes the whole word",
            accentColor = CyberEmerald,
            enabledIf = { prefs.glide.enabled.get() },
        )

        // 2. SPACE BAR GESTURES
        CrakeSectionHeader(title = "Space Bar Trackpad Gestures", badgeText = "TRACKPAD", accentColor = ElectricCyan)
        CrakeListPreference(
            prefs.gestures.spaceBarSwipeLeft,
            title = "Space Bar Swipe Left (Cursor Move)",
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        CrakeListPreference(
            prefs.gestures.spaceBarSwipeRight,
            title = "Space Bar Swipe Right (Cursor Move)",
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        CrakeListPreference(
            prefs.gestures.spaceBarSwipeUp,
            title = "Space Bar Swipe Up",
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )
        CrakeListPreference(
            prefs.gestures.spaceBarLongPress,
            title = "Space Bar Long-Press",
            entries = enumDisplayEntriesOf(SwipeAction::class, "general"),
        )

        // 3. DELETE KEY GESTURES
        CrakeSectionHeader(title = "Delete Key Gestures", badgeText = "DELETE", accentColor = CyberEmerald)
        CrakeListPreference(
            prefs.gestures.deleteKeySwipeLeft,
            title = "Delete Key Swipe Left (Quick Erase)",
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteSwipe"),
        )
        CrakeListPreference(
            prefs.gestures.deleteKeyLongPress,
            title = "Delete Key Long-Press",
            entries = enumDisplayEntriesOf(SwipeAction::class, "deleteLongPress"),
        )

        // 4. SENSITIVITY & THRESHOLDS
        CrakeSectionHeader(title = "Gesture Sensitivity", badgeText = "PHYSICS", accentColor = ElectricCyan)
        DialogSliderPreference(
            prefs.gestures.swipeVelocityThreshold,
            title = "Swipe Velocity Threshold",
            valueLabel = { "${it} dp/s" },
            min = 100,
            max = 2000,
            stepIncrement = 50,
        )
        DialogSliderPreference(
            prefs.gestures.swipeDistanceThreshold,
            title = "Swipe Distance Threshold",
            valueLabel = { "${it} dp" },
            min = 10,
            max = 100,
            stepIncrement = 5,
        )
    }
}
