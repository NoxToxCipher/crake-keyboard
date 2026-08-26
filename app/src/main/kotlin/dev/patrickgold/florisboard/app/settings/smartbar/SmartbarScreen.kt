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

package dev.patrickgold.florisboard.app.settings.smartbar

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.smartbar.CandidatesDisplayMode
import dev.patrickgold.florisboard.ime.smartbar.ExtendedActionsPlacement
import dev.patrickgold.florisboard.ime.smartbar.SmartbarLayout
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import org.florisboard.lib.compose.stringRes

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

@Composable
fun SmartbarScreen() = FlorisScreen {
    title = "Smartbar & Candidates"
    previewFieldVisible = true

    content {
        // 1. SMARTBAR PRIMARY CONTROLS
        CrakeSectionHeader(title = "Smartbar Top Action Bar", badgeText = "SMARTBAR", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.smartbar.enabled,
            title = "Enable Smartbar",
            summary = "Display top bar above keyboard for word suggestions and quick utility actions",
            icon = Icons.Default.SmartButton,
            accentColor = CyberEmerald,
        )
        ListPreference(
            listPref = prefs.smartbar.layout,
            title = "Smartbar Layout Mode",
            entries = enumDisplayEntriesOf(SmartbarLayout::class),
            enabledIf = { prefs.smartbar.enabled.get() },
        )

        // 2. CANDIDATE CAPSULES & ACTIONS
        CrakeSectionHeader(title = "Candidate Display & Action Placement", badgeText = "CAPSULES", accentColor = CyberEmerald)
        ListPreference(
            prefs.suggestion.displayMode,
            title = "Word Candidate Display Mode",
            entries = enumDisplayEntriesOf(CandidatesDisplayMode::class),
            enabledIf = { prefs.smartbar.enabled.get() },
            visibleIf = { prefs.smartbar.layout.get() != SmartbarLayout.ACTIONS_ONLY },
        )
        CrakeRadioPreference(
            pref = prefs.smartbar.flipToggles,
            title = "Flip Action Button Placement",
            summary = "Position action toggle arrow on the right side of the candidate bar",
            icon = Icons.Default.SwapHoriz,
            accentColor = ElectricCyan,
            enabledIf = { prefs.smartbar.enabled.get() },
            visibleIf = {
                prefs.smartbar.layout.get() == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED ||
                    prefs.smartbar.layout.get() == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED
            },
        )
        ListPreference(
            listPref = prefs.smartbar.extendedActionsPlacement,
            title = "Extended Actions Bar Placement",
            entries = enumDisplayEntriesOf(ExtendedActionsPlacement::class),
            enabledIf = { prefs.smartbar.enabled.get() },
            visibleIf = { prefs.smartbar.layout.get() == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED },
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
