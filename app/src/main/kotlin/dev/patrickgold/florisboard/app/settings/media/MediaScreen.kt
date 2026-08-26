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

package dev.patrickgold.florisboard.app.settings.media

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EmojiSymbols
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistory
import dev.patrickgold.florisboard.ime.media.emoji.EmojiHistoryHelper
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSkinTone
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

private class ShouldDelete(val pinned: Boolean)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun MediaScreen() = FlorisScreen {
    title = "Emojis, Kaomojis & Media"
    previewFieldVisible = true
    iconSpaceReserved = true

    val prefs by FlorisPreferenceStore
    var shouldDelete by remember { mutableStateOf<ShouldDelete?>(null) }
    val scope = rememberCoroutineScope()

    content {
        // 1. EMOJI PALETTE & TONE
        CrakeSectionHeader(title = "Unicode Emoji & Skin Tones", badgeText = "PALETTE", accentColor = ElectricCyan)
        ListPreference(
            prefs.emoji.preferredSkinTone,
            title = "Default Skin Tone",
            entries = enumDisplayEntriesOf(EmojiSkinTone::class),
        )

        // 2. RECENT & PINNED HISTORY
        CrakeSectionHeader(title = "Recent & Pinned History", badgeText = "HISTORY", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.emoji.historyEnabled,
            title = "Enable Emoji History",
            summary = "Save recently used emojis and kaomojis for quick access",
            icon = Icons.Outlined.Schedule,
            accentColor = CyberEmerald,
        )
        DialogSliderPreference(
            primaryPref = prefs.emoji.historyPinnedMaxSize,
            secondaryPref = prefs.emoji.historyRecentMaxSize,
            title = "Maximum History Cache Size",
            primaryLabel = stringRes(R.string.emoji__history__pinned),
            secondaryLabel = stringRes(R.string.emoji__history__recent),
            valueLabel = { maxSize ->
                if (maxSize == EmojiHistory.MaxSizeUnlimited) {
                    stringRes(R.string.general__unlimited)
                } else {
                    pluralsRes(R.plurals.unit__items__written, maxSize, "v" to maxSize)
                }
            },
            min = 0,
            max = 120,
            stepIncrement = 1,
            enabledIf = { prefs.emoji.historyEnabled.get() },
        )

        // 3. EMOJI SUGGESTIONS
        CrakeSectionHeader(title = "Inline Emoji Suggestions", badgeText = "SMART", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.emoji.suggestionEnabled,
            title = "Suggest Emojis while Typing",
            summary = "Show matching emoji capsules in candidate bar when typing keywords",
            icon = Icons.Outlined.EmojiSymbols,
            accentColor = ElectricCyan,
        )
        ListPreference(
            prefs.emoji.suggestionType,
            title = "Emoji Suggestion Trigger Mode",
            entries = enumDisplayEntriesOf(EmojiSuggestionType::class),
            enabledIf = { prefs.emoji.suggestionEnabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.emoji.suggestionCandidateShowName,
            title = "Show Emoji Keyword Label",
            summary = "Display text name alongside suggested emoji pill",
            accentColor = CyberEmerald,
            enabledIf = { prefs.emoji.suggestionEnabled.get() },
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
