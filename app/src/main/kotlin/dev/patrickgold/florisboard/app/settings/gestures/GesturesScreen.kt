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

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import org.florisboard.lib.compose.stringRes

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun GesturesScreen() = FlorisScreen {
    title = "Gestures & Flicks"
    previewFieldVisible = true

    content {
        // 1. GLIDE TYPING
        CrakeSectionHeader(title = "Continuous Glide Typing", badgeText = "GLIDE", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.glide.enabled,
            title = "Continuous Glide Typing",
            summary = "Slide finger across letters to type words smoothly",
            icon = Icons.Default.Gesture,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.glide.showTrail,
            title = "Neon Aurora Particle Trail",
            summary = "Draw animated electric cyan glow following your glide path",
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
            summary = "Pressing backspace right after a glided word deletes the entire word",
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
            title = "Swipe Velocity Trigger Threshold",
            valueLabel = { stringRes(R.string.unit__display_pixel_per_seconds__symbol, "v" to it) },
            min = 400,
            max = 4000,
            stepIncrement = 100,
        )
        DialogSliderPreference(
            prefs.gestures.swipeDistanceThreshold,
            title = "Swipe Distance Trigger Threshold",
            valueLabel = { stringRes(R.string.unit__display_pixel__symbol, "v" to it) },
            min = 12,
            max = 72,
            stepIncrement = 1,
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
