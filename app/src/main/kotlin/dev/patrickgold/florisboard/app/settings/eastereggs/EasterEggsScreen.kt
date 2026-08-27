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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.EasterEgg
import dev.patrickgold.florisboard.ime.keyboard.EasterEggs
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState

/**
 * Lists ONLY the easter eggs this user has already discovered, each with its
 * own off switch. Undiscovered eggs stay invisible — discovery is the price
 * of the switch, so the surprise stays intact.
 */
@Composable
fun EasterEggsScreen() = FlorisScreen {
    title = "Easter eggs"
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore

    content {
        val discoveredCsv by prefs.easterEggs.discovered.collectAsState()
        val disabledCsv by prefs.easterEggs.disabled.collectAsState()
        val discovered = EasterEggs.discoveredEggs(discoveredCsv)
        val hiddenCount = EasterEgg.entries.size - discovered.size

        CrakeSectionHeader(title = "Discovered")
        if (discovered.isEmpty()) {
            Text(
                text = "Nothing here yet. Keep typing.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        for (egg in discovered) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = egg.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    Switch(
                        checked = EasterEggs.isEnabled(disabledCsv, egg),
                        onCheckedChange = { enabled: Boolean ->
                            prefs.easterEggs.setEggEnabled(egg, enabled)
                        },
                    )
                }
            }
        }
        if (hiddenCount > 0) {
            Text(
                text = if (hiddenCount == 1) {
                    "1 more still hidden somewhere…"
                } else {
                    "$hiddenCount more still hidden somewhere…"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
