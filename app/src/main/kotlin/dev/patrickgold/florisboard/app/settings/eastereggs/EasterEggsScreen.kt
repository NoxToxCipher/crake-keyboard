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

package dev.patrickgold.florisboard.app.settings.eastereggs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.EasterEgg
import dev.patrickgold.florisboard.ime.keyboard.EasterEggs
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

/**
 * Lists ONLY the easter eggs this user has already discovered, each with its
 * own off switch. Undiscovered eggs stay invisible — discovery is the price
 * of the switch, so the surprise stays intact.
 */
@Composable
fun EasterEggsScreen() = FlorisScreen {
    title = "Discovered Easter Eggs"
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore

    content {
        val discoveredCsv by prefs.easterEggs.discovered.collectAsState()
        val disabledCsv by prefs.easterEggs.disabled.collectAsState()
        val discovered = EasterEggs.discoveredEggs(discoveredCsv)
        val hiddenCount = EasterEgg.entries.size - discovered.size

        CrakeSectionHeader(title = "Discovered Triggers", badgeText = "${discovered.size} ACTIVE", accentColor = ElectricCyan)
        if (discovered.isEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Text(
                    text = "No easter eggs discovered yet. Keep typing to uncover hidden triggers.",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }
        for (egg in discovered) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = egg.label,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                    }
                    Switch(
                        checked = EasterEggs.isEnabled(disabledCsv, egg),
                        onCheckedChange = { enabled: Boolean ->
                            prefs.easterEggs.setEggEnabled(egg, enabled)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberEmerald,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardBorder,
                        )
                    )
                }
            }
        }
        if (hiddenCount > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (hiddenCount == 1) {
                    "1 more egg still hidden in the keyboard…"
                } else {
                    "$hiddenCount more eggs still hidden in the keyboard…"
                },
                fontSize = 11.5.sp,
                color = TextMuted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
