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

package dev.patrickgold.florisboard.app.settings.keyboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.input.HapticVibrationMode
import dev.patrickgold.florisboard.ime.input.InputFeedbackActivationMode
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import org.florisboard.lib.android.systemVibratorOrNull
import org.florisboard.lib.android.vibrate
import org.florisboard.lib.compose.stringRes

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun InputFeedbackScreen() = FlorisScreen {
    title = "Haptics & Audio Feedback"
    previewFieldVisible = true
    iconSpaceReserved = false

    val context = LocalContext.current
    val vibrator = context.systemVibratorOrNull()

    content {
        // 1. HAPTIC VIBRATION
        CrakeSectionHeader(title = "Haptic Vibration Feedback", badgeText = "VIBRATION", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.inputFeedback.hapticEnabled,
            title = "Haptic Key Vibration",
            summary = "Tactile vibration feedback on key presses and gestures",
            icon = Icons.Default.Vibration,
            accentColor = CyberEmerald,
        )
        CrakeListPreference(
            prefs.inputFeedback.hapticVibrationMode,
            title = "Haptic Vibration Profile",
            enabledIf = { prefs.inputFeedback.hapticEnabled.get() },
            entries = enumDisplayEntriesOf(HapticVibrationMode::class),
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.hapticFeatKeyPress,
            title = "Vibrate on Key Press",
            summary = "Short tactile pulse when tapping letters and symbols",
            accentColor = CyberEmerald,
            enabledIf = { prefs.inputFeedback.hapticEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.hapticFeatKeyLongPress,
            title = "Vibrate on Long-Press",
            summary = "Haptic pulse when triggering long-press symbol popups",
            accentColor = CyberEmerald,
            enabledIf = { prefs.inputFeedback.hapticEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.hapticFeatGestureSwipe,
            title = "Vibrate on Swipe Flicks",
            summary = "Tactile confirmation on spacebar swipe or catapult flicks",
            accentColor = CyberEmerald,
            enabledIf = { prefs.inputFeedback.hapticEnabled.get() },
        )
        DialogSliderPreference(
            prefs.inputFeedback.hapticVibrationDuration,
            title = "Vibration Duration",
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 1,
            max = 100,
            stepIncrement = 1,
            onPreviewSelectedValue = { duration ->
                val strength = prefs.inputFeedback.hapticVibrationStrength.get()
                vibrator?.vibrate(duration, strength)
            },
            enabledIf = {
                prefs.inputFeedback.hapticEnabled.get() &&
                    prefs.inputFeedback.hapticVibrationMode.get() == HapticVibrationMode.USE_VIBRATOR_DIRECTLY &&
                    vibrator != null && vibrator.hasVibrator()
            },
        )

        // 2. AUDIO FEEDBACK
        CrakeSectionHeader(title = "Mechanical Audio Feedback", badgeText = "SOUND", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.inputFeedback.audioEnabled,
            title = "Keypress Audio Sounds",
            summary = "Audible clicks on typing and delete",
            icon = Icons.Default.VolumeUp,
            accentColor = ElectricCyan,
        )
        DialogSliderPreference(
            prefs.inputFeedback.audioVolume,
            title = "Audio Volume",
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 1,
            max = 100,
            stepIncrement = 1,
            enabledIf = { prefs.inputFeedback.audioEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.audioFeatKeyPress,
            title = "Sound on Key Press",
            summary = "Audio click on letter, symbol, and space presses",
            accentColor = ElectricCyan,
            enabledIf = { prefs.inputFeedback.audioEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.audioFeatKeyLongPress,
            title = "Sound on Long Press",
            summary = "Audio click when opening long-press popups",
            accentColor = ElectricCyan,
            enabledIf = { prefs.inputFeedback.audioEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.inputFeedback.audioFeatGestureSwipe,
            title = "Sound on Swipe Gesture",
            summary = "Audio feedback when executing swipe actions",
            accentColor = ElectricCyan,
            enabledIf = { prefs.inputFeedback.audioEnabled.get() },
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
