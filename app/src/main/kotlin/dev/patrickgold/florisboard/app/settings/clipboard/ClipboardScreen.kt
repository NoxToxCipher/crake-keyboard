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
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
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
import dev.patrickgold.florisboard.ime.clipboard.CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO
import dev.patrickgold.florisboard.ime.clipboard.ClipboardSyncBehavior
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.pluralsRes
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun ClipboardScreen() = FlorisScreen {
    title = "Encrypted Clipboard Vault"
    previewFieldVisible = true

    content {
        // Hero Banner Card: Zero-Knowledge Encrypted Vault
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
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AIR-GAPPED ENCRYPTED VAULT",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.White,
                        )
                        Text(
                            text = "ChaCha20-Poly1305 on-device encrypted clipboard sandbox with ephemeral auto-burn",
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
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = CyberEmerald,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "ISOLATED SANDBOX",
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
                            text = "AUTO-SHRED ACTIVE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                        )
                    }
                }
            }
        }

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
            min = 1,
            max = 30,
            stepIncrement = 1,
        )

        // 4. VAULT DISPLAY STYLE
        CrakeSectionHeader(title = "Vault Display Style", badgeText = "VIEW", accentColor = CyberEmerald)
        DialogSliderPreference(
            prefs.clipboard.historyNumGridColumnsPortrait,
            title = "Clipboard Columns (Portrait)",
            valueLabel = { if (it == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) "Auto-Fit" else it.toString() },
            min = 1,
            max = 5,
            stepIncrement = 1,
        )
        DialogSliderPreference(
            prefs.clipboard.historyNumGridColumnsLandscape,
            title = "Clipboard Columns (Landscape)",
            valueLabel = { if (it == CLIPBOARD_HISTORY_NUM_GRID_COLUMNS_AUTO) "Auto-Fit" else it.toString() },
            min = 1,
            max = 5,
            stepIncrement = 1,
        )
    }
}
