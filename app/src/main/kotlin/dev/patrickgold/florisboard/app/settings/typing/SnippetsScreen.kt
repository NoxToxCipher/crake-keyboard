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

package dev.patrickgold.florisboard.app.settings.typing

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import dev.patrickgold.jetpref.material.ui.JetPrefTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun SnippetsScreen() = FlorisScreen {
    title = "Smart Text Expansion & Snippets"
    previewFieldVisible = true
    scrollable = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var snippetList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var snippetForDialog by remember { mutableStateOf<UserDictionaryEntry?>(null) }
    var isNewSnippet by remember { mutableStateOf(false) }

    fun refreshSnippets() {
        scope.launch(Dispatchers.IO) {
            val dao = dictionaryManager.florisUserDictionaryDao()
            val entries = dao?.queryAll() ?: emptyList()
            // Filter entries that have a shortcut defined
            val shortcutsOnly = entries.filter { !it.shortcut.isNullOrBlank() }
            withContext(Dispatchers.Main) {
                snippetList = shortcutsOnly
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshSnippets()
    }

    floatingActionButton {
        ExtendedFloatingActionButton(
            icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black) },
            text = { Text("Add Snippet", fontWeight = FontWeight.Bold, color = Color.Black) },
            containerColor = ElectricCyan,
            onClick = {
                snippetForDialog = UserDictionaryEntry(
                    id = 0,
                    word = "",
                    freq = 255,
                    locale = null,
                    shortcut = "!",
                )
                isNewSnippet = true
            },
        )
    }

    content {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
        ) {
            // Header Info Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElectricCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "DYNAMIC EXPANSION CORE",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Type short triggers to expand phrases, addresses, & macros",
                                    fontSize = 11.5.sp,
                                    color = TextMuted,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = CardBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(10.dp))

                        // Quick Macro Info
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = "Built-in Macro: !time", fontSize = 11.5.sp, color = TextMuted)
                            Text(
                                text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()),
                                fontSize = 11.5.sp,
                                color = CyberEmerald,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(text = "Built-in Macro: !date", fontSize = 11.5.sp, color = TextMuted)
                            Text(
                                text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
                                fontSize = 11.5.sp,
                                color = CyberEmerald,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            // Quick Preset Templates
            item {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                    text = "Quick Presets",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PresetBadge("!shrug", "¯\\_(ツ)_/¯") { shortcut, word ->
                        scope.launch(Dispatchers.IO) {
                            dictionaryManager.florisUserDictionaryDao()?.insert(
                                UserDictionaryEntry(0, word, 255, null, shortcut)
                            )
                            refreshSnippets()
                        }
                    }
                    PresetBadge("!flip", "(╯°□°)╯︵ ┻━┻") { shortcut, word ->
                        scope.launch(Dispatchers.IO) {
                            dictionaryManager.florisUserDictionaryDao()?.insert(
                                UserDictionaryEntry(0, word, 255, null, shortcut)
                            )
                            refreshSnippets()
                        }
                    }
                    PresetBadge("!email", "user@example.com") { shortcut, word ->
                        snippetForDialog = UserDictionaryEntry(0, word, 255, null, shortcut)
                        isNewSnippet = true
                    }
                }
            }

            // Snippet List Header
            item {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                    text = "Custom Snippets (${snippetList.size})",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                )
            }

            if (snippetList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No custom snippets created yet",
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Tap '+ Add Snippet' below or select a quick preset above",
                                fontSize = 11.5.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
            } else {
                items(snippetList, key = { it.id }) { entry ->
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
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = entry.shortcut ?: "!",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = CyberEmerald,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.word,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    maxLines = 2,
                                )
                            }
                            IconButton(
                                onClick = {
                                    snippetForDialog = entry
                                    isNewSnippet = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit",
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        dictionaryManager.florisUserDictionaryDao()?.delete(entry)
                                        refreshSnippets()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = Color(0xFFFF5252),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Add / Edit Snippet Dialog
        snippetForDialog?.let { entry ->
            var triggerText by remember { mutableStateOf(entry.shortcut ?: "!") }
            var expansionText by remember { mutableStateOf(entry.word) }

            JetPrefAlertDialog(
                title = if (isNewSnippet) "Add Smart Snippet" else "Edit Smart Snippet",
                confirmLabel = "Save",
                dismissLabel = "Cancel",
                onDismiss = { snippetForDialog = null },
                onConfirm = {
                    if (triggerText.isNotBlank() && expansionText.isNotBlank()) {
                        scope.launch(Dispatchers.IO) {
                            val dao = dictionaryManager.florisUserDictionaryDao()
                            val updatedEntry = entry.copy(
                                word = expansionText.trim(),
                                shortcut = triggerText.trim(),
                                freq = 255,
                                locale = null,
                            )
                            if (isNewSnippet) {
                                dao?.insert(updatedEntry)
                            } else {
                                dao?.update(updatedEntry)
                            }
                            refreshSnippets()
                        }
                    }
                    snippetForDialog = null
                },
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(
                        text = "Shortcut Trigger (e.g. !addr, !email)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JetPrefTextField(
                        value = triggerText,
                        onValueChange = { triggerText = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Expanded Phrase or Template",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ElectricCyan,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    JetPrefTextField(
                        value = expansionText,
                        onValueChange = { expansionText = it },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetBadge(
    trigger: String,
    expansion: String,
    onClick: (String, String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        onClick = { onClick(trigger, expansion) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "+ " + trigger,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricCyan,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
