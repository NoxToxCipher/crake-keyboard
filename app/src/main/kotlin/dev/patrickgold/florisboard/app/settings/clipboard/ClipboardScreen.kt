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

package dev.patrickgold.florisboard.app.settings.clipboard

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Timer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ClipboardScreen() = FlorisScreen {
    title = "Encrypted Clipboard Vault"
    previewFieldVisible = true

    content {
        // 1. ISOLATED VAULT STORAGE
        CrakeSectionHeader(title = "ChaCha20-Poly1305 Encrypted Vault", badgeText = "ENCRYPTED", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.clipboard.useInternalClipboard,
            title = "Internal Encrypted Storage",
            summary = "Store clips in private AES-GCM / ChaCha20 sandbox isolated from background apps",
            icon = Icons.Default.Lock,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.clipboard.historyEnabled,
            title = "Save Clipboard History",
            summary = "Maintain recent clips for quick multi-paste",
            icon = Icons.AutoMirrored.Outlined.Assignment,
            accentColor = ElectricCyan,
        )
        CrakeListPreference(
            prefs.clipboard.syncToFloris,
            title = "Sync from System Clipboard",
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabledIf = { prefs.clipboard.useInternalClipboard.get() },
        )
        CrakeListPreference(
            prefs.clipboard.syncToSystem,
            title = "Sync to System Clipboard",
            entries = enumDisplayEntriesOf(ClipboardSyncBehavior::class),
            enabledIf = { prefs.clipboard.useInternalClipboard.get() },
        )

        // 2. AUTO-DESTRUCT & TIME LIMITS
        CrakeSectionHeader(title = "Auto-Wipe & Sensitive Purge", badgeText = "AUTO-BURN", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.clipboard.historyAutoCleanOldEnabled,
            title = "Ephemeral Auto-Wipe Timer",
            summary = "Automatically shred copied clips after the configured timeout",
            icon = Icons.Default.Timer,
            accentColor = CyberEmerald,
        )
        DialogSliderPreference(
            prefs.clipboard.historyAutoCleanOldAfter,
            title = "Auto-Wipe Expiry Delay",
            valueLabel = { pluralsRes(R.plurals.unit__minutes__written, it, "v" to it) },
            min = 1,
            max = 120,
            stepIncrement = 5,
        )
        CrakeRadioPreference(
            pref = prefs.clipboard.historyAutoCleanSensitiveEnabled,
            title = "Auto-Scrub Sensitive Passwords & OTPs",
            summary = "Sanitize clips marked sensitive or containing crypto seed phrases",
            accentColor = CyberEmerald,
            visibleIf = { AndroidVersion.ATLEAST_API33_T },
        )

        // 3. SMARTBAR SUGGESTIONS
        CrakeSectionHeader(title = "Smartbar Inline Clipboard", badgeText = "SMART", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.clipboard.suggestionEnabled,
            title = "Suggest Copied Items in Smartbar",
            summary = "Show quick 1-tap paste capsule in the Smartbar",
            icon = Icons.Default.AutoAwesome,
            accentColor = ElectricCyan,
        )
        DialogSliderPreference(
            prefs.clipboard.suggestionTimeout,
            title = "Suggestion Expiry Timeout",
            valueLabel = { stringRes(R.string.pref__clipboard__suggestion_timeout__summary, "v" to it) },
            min = 10,
            max = 180,
            stepIncrement = 5,
            enabledIf = { prefs.clipboard.suggestionEnabled.get() },
        )

        Spacer(modifier = Modifier.height(20.dp))
    }
}
