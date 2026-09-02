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

package dev.patrickgold.florisboard.app.settings.dictionary

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
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun DictionaryScreen() = FlorisScreen {
    title = "Dictionaries & Vocabulary"
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        // 1. CRAKE ENCRYPTED USER DICTIONARY
        CrakeSectionHeader(title = "Crake Internal Vocabulary", badgeText = "ENCRYPTED", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.dictionary.enableFlorisUserDictionary,
            title = "Encrypted Local User Dictionary",
            summary = "Private offline SQLite word list for learned vocabulary and custom shortcuts",
            icon = Icons.Default.Lock,
            accentColor = CyberEmerald,
        )
        val florisDictEnabled by prefs.dictionary.enableFlorisUserDictionary.asFlow().collectAsState(prefs.dictionary.enableFlorisUserDictionary.get())
        if (florisDictEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.FLORIS)) },
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
                            imageVector = Icons.AutoMirrored.Filled.LibraryBooks,
                            contentDescription = null,
                            tint = CyberEmerald,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Manage Crake User Words",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = Color.White,
                            )
                            Text(
                                text = "Add, edit, or delete custom words and custom shortcuts",
                                fontSize = 11.5.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }

        // 2. ANDROID SYSTEM DICTIONARY
        Spacer(modifier = Modifier.height(10.dp))
        CrakeSectionHeader(title = "Android System Dictionary", badgeText = "SYSTEM", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.dictionary.enableSystemUserDictionary,
            title = "Android System User Dictionary",
            summary = "Sync words from Android OS shared user dictionary",
            icon = Icons.Default.Android,
            accentColor = ElectricCyan,
        )
        val systemDictEnabled by prefs.dictionary.enableSystemUserDictionary.asFlow().collectAsState(prefs.dictionary.enableSystemUserDictionary.get())
        if (systemDictEnabled) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                onClick = { navController.navigate(Routes.Settings.UserDictionary(UserDictionaryType.SYSTEM)) },
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
                            imageVector = Icons.Default.Spellcheck,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Manage System Words",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = Color.White,
                            )
                            Text(
                                text = "View and edit Android OS shared dictionary words",
                                fontSize = 11.5.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
