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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.input.CapitalizationBehavior
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.landscapeinput.LandscapeInputUiMode
import dev.patrickgold.florisboard.ime.smartbar.IncognitoDisplayMode
import dev.patrickgold.florisboard.ime.text.key.KeyHintMode
import dev.patrickgold.florisboard.ime.text.key.UtilityKeyAction
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun KeyboardScreen() = FlorisScreen {
    title = "Keyboard & Keycaps"
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        // 1. KEY ROWS & SHORTCUTS
        CrakeSectionHeader(title = "Key Rows & Primary Controls", badgeText = "LAYOUT", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.keyboard.numberRow,
            title = "Dedicated Number Row",
            summary = "Always display numbers 0-9 as a dedicated top row above letters",
            icon = Icons.Default.Numbers,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.keyboard.utilityKeyEnabled,
            title = "Dedicated Utility Key",
            summary = "Show quick action key next to the spacebar",
            accentColor = ElectricCyan,
        )
        ListPreference(
            prefs.keyboard.utilityKeyAction,
            title = "Utility Key Action",
            entries = enumDisplayEntriesOf(UtilityKeyAction::class),
            visibleIf = { prefs.keyboard.utilityKeyEnabled.get() },
        )
        ListPreference(
            prefs.keyboard.spaceBarMode,
            title = "Space Bar Mode",
            entries = enumDisplayEntriesOf(SpaceBarMode::class),
        )
        ListPreference(
            prefs.keyboard.capitalizationBehavior,
            title = "Shift Key Capitalization Mode",
            entries = enumDisplayEntriesOf(CapitalizationBehavior::class),
        )

        // 2. KEYPRESS & POPUPS
        CrakeSectionHeader(title = "Keypress & Popups", badgeText = "TOUCH", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.keyboard.popupEnabled,
            title = "Keypress Character Popup",
            summary = "Show floating character magnifier above touched keycap",
            icon = Icons.Default.TouchApp,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.keyboard.mergeHintPopupsEnabled,
            title = "Merge Symbol Hints in Popup",
            summary = "Show long-press symbol alternatives inside character popup",
            accentColor = ElectricCyan,
            enabledIf = { prefs.keyboard.popupEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.keyboard.spaceBarSwitchesToCharacters,
            title = "Space Switches back to Letters",
            summary = "Automatically return to alphabet keyboard after pressing space in symbols mode",
            icon = Icons.Default.SpaceBar,
            accentColor = CyberEmerald,
        )
        DialogSliderPreference(
            prefs.keyboard.longPressDelay,
            title = "Long-Press Delay",
            valueLabel = { stringRes(R.string.unit__milliseconds__symbol, "v" to it) },
            min = 100,
            max = 700,
            stepIncrement = 10,
        )

        // 3. DIMENSIONS & SPACING
        CrakeSectionHeader(title = "Dimensions & Geometry", badgeText = "DISPLAY", accentColor = ElectricCyan)
        DialogSliderPreference(
            primaryPref = prefs.keyboard.fontSizeMultiplierPortrait,
            secondaryPref = prefs.keyboard.fontSizeMultiplierLandscape,
            title = "Key Legend Font Size",
            primaryLabel = stringRes(R.string.screen_orientation__portrait),
            secondaryLabel = stringRes(R.string.screen_orientation__landscape),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50,
            max = 150,
            stepIncrement = 5,
        )
        DialogSliderPreference(
            primaryPref = prefs.keyboard.keySpacingVertical,
            secondaryPref = prefs.keyboard.keySpacingHorizontal,
            title = "Keycap Spacing",
            primaryLabel = stringRes(R.string.screen_orientation__vertical),
            secondaryLabel = stringRes(R.string.screen_orientation__horizontal),
            valueLabel = { stringRes(R.string.unit__percent__symbol, "v" to it) },
            min = 50,
            max = 150,
            stepIncrement = 5,
        )
        ListPreference(
            prefs.keyboard.landscapeInputUiMode,
            title = "Landscape Input Mode",
            entries = enumDisplayEntriesOf(LandscapeInputUiMode::class),
        )

        // 4. HAPTICS & SOUND
        CrakeSectionHeader(title = "Haptics & Audio Feedback", badgeText = "TACTILE", accentColor = CyberEmerald)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = { navController.navigate(Routes.Settings.InputFeedback) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Vibration,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Haptic Vibration & Mechanical Audio",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Configure vibration intensity, Nothing Phone haptics & key sounds",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
