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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.window.Dialog
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.CryptoChain
import dev.patrickgold.florisboard.lib.util.ValidationResult
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
private val CyberAmber = Color(0xFFF59E0B)
private val CyberCrimson = Color(0xFFEF4444)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun SnippetsScreen() = FlorisScreen {
    title = "Smart Text & Crypto Snippets"
    previewFieldVisible = true
    scrollable = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var snippetList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var showDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<UserDictionaryEntry?>(null) }
    var inputTrigger by remember { mutableStateOf("") }
    var inputExpansion by remember { mutableStateOf("") }

    fun refreshSnippets() {
        scope.launch(Dispatchers.IO) {
            val dao = dictionaryManager.florisUserDictionaryDao()
            val entries = dao?.queryAll() ?: emptyList()
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
                editingEntry = null
                inputTrigger = "!"
                inputExpansion = ""
                showDialog = true
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
                                    .clip(RoundedCornerShape(10.dp))
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
                                    text = "Smart Snippets & Crypto Vault",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = "Type shortcut triggers (!addr, !btc1, !eth1) to expand live",
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // CRYPTO WALLET SHORTCUTS SECTION
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CurrencyBitcoin,
                                contentDescription = null,
                                tint = CyberAmber,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "CRYPTO WALLET SHORTCUTS (TAP TO ADD)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = CyberAmber,
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        // Scrollable Crypto Presets Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CryptoPresetBadge("!btc1", "Bitcoin 1", "bc1q...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!btc2", "Bitcoin 2", "bc1q...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!eth1", "Ethereum", "0x...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!xmr1", "Monero", "48...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!ltc1", "Litecoin", "ltc1...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!rune1", "THORChain", "thor1...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!atom1", "Cosmos", "cosmos1...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoPresetBadge("!arb1", "Arbitrum", "0x...") { t ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = ""
                                showDialog = true
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "BUILT-IN DYNAMIC MACROS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ElectricCyan,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val now = remember { Date() }
                        val timeStr = remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
                        val dateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            MacroPill("!time", timeStr, Modifier.weight(1f))
                            MacroPill("!date", dateStr, Modifier.weight(1f))
                            MacroPill("!now", "Full ISO", Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "GENERAL PRESETS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CyberEmerald,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PresetBadge("!email", "name@example.com") { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                            PresetBadge("!addr", "123 Cyber St, Suite 404") { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                            PresetBadge("!shrug", "¯\\_(ツ)_/¯") { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                        }
                    }
                }
            }

            // Custom Snippets Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SAVED SHORTCUTS (${snippetList.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextMuted,
                    )
                }
            }

            if (snippetList.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
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
                                text = "No custom snippets yet",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Snippet' below or pick a Crypto/Macro preset above",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                items(snippetList, key = { it.id }) { entry ->
                    val shortcut = entry.shortcut ?: "!"
                    val detectedChain = CryptoChain.detectFromShortcut(shortcut) ?: CryptoChain.detectFromAddress(entry.word)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(
                            1.dp,
                            if (detectedChain != null) CyberAmber.copy(alpha = 0.4f) else CardBorder,
                        ),
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
                                    .background(if (detectedChain != null) CyberAmber.copy(alpha = 0.15f) else CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = shortcut,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (detectedChain != null) CyberAmber else CyberEmerald,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (detectedChain != null) {
                                    Text(
                                        text = "${detectedChain.displayName} (${detectedChain.symbol})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberAmber,
                                    )
                                    Spacer(modifier = Modifier.height(1.dp))
                                }
                                Text(
                                    text = entry.word,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = if (detectedChain != null) FontFamily.Monospace else FontFamily.Default,
                                    color = Color.White,
                                    maxLines = 2,
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingEntry = entry
                                    inputTrigger = entry.shortcut ?: "!"
                                    inputExpansion = entry.word
                                    showDialog = true
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

        // Custom Add / Edit Snippet Modal Dialog with Live Crypto Validation
        if (showDialog) {
            Dialog(onDismissRequest = { showDialog = false }) {
                val detectedChain = CryptoChain.detectFromShortcut(inputTrigger) ?: CryptoChain.detectFromAddress(inputExpansion)
                val validationResult = remember(inputTrigger, inputExpansion, detectedChain) {
                    if (detectedChain != null && detectedChain != CryptoChain.UNKNOWN) {
                        detectedChain.validate(inputExpansion)
                    } else {
                        null
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(
                        1.dp,
                        if (detectedChain != null) CyberAmber.copy(alpha = 0.5f) else CardBorder,
                    ),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (detectedChain != null) CyberAmber.copy(alpha = 0.15f) else ElectricCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = if (detectedChain != null) Icons.Default.CurrencyBitcoin else Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = if (detectedChain != null) CyberAmber else ElectricCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (editingEntry == null) "NEW SMART SNIPPET" else "EDIT SMART SNIPPET",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "Shortcut Trigger",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricCyan,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = inputTrigger,
                            onValueChange = { inputTrigger = it },
                            placeholder = { Text("e.g. !btc1, !eth1, !xmr1, !addr", color = TextMuted, fontSize = 13.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricCyan,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = if (detectedChain != null) "${detectedChain.displayName} (${detectedChain.symbol}) Wallet Address" else "Expanded Phrase or Template",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (detectedChain != null) CyberAmber else CyberEmerald,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = inputExpansion,
                            onValueChange = { inputExpansion = it },
                            placeholder = {
                                Text(
                                    if (detectedChain != null) "Paste ${detectedChain.symbol} address (${detectedChain.prefixHint})" else "e.g. 742 Evergreen Terrace, Springfield",
                                    color = TextMuted,
                                    fontSize = 12.sp,
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = if (detectedChain != null) CyberAmber else CyberEmerald,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                        )

                        // Real-Time Address Validation Feedback Box
                        if (validationResult != null && inputExpansion.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (validationResult.isValid) CyberEmerald.copy(alpha = 0.12f) else CyberCrimson.copy(alpha = 0.12f),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (validationResult.isValid) CyberEmerald.copy(alpha = 0.4f) else CyberCrimson.copy(alpha = 0.4f),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = if (validationResult.isValid) Icons.Default.CheckCircle else Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = if (validationResult.isValid) CyberEmerald else CyberCrimson,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = validationResult.message,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (validationResult.isValid) CyberEmerald else CyberCrimson,
                                        lineHeight = 14.sp,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, CardBorder),
                                onClick = { showDialog = false },
                            ) {
                                Text("Cancel", color = TextMuted, fontSize = 13.sp)
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (detectedChain != null) CyberAmber else ElectricCyan,
                                ),
                                onClick = {
                                    if (inputTrigger.isNotBlank() && inputExpansion.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            val dao = dictionaryManager.florisUserDictionaryDao()
                                            val entryToSave = if (editingEntry != null) {
                                                editingEntry!!.copy(
                                                    word = inputExpansion.trim(),
                                                    shortcut = inputTrigger.trim(),
                                                    freq = 255,
                                                    locale = null,
                                                )
                                            } else {
                                                UserDictionaryEntry(
                                                    id = 0L,
                                                    word = inputExpansion.trim(),
                                                    shortcut = inputTrigger.trim(),
                                                    freq = 255,
                                                    locale = null,
                                                )
                                            }

                                            if (editingEntry != null) {
                                                dao?.update(entryToSave)
                                            } else {
                                                dao?.insert(entryToSave)
                                            }
                                            refreshSnippets()
                                        }
                                        showDialog = false
                                    }
                                },
                            ) {
                                Text(
                                    text = if (editingEntry == null) "Save" else "Update",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroPill(trigger: String, description: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1624)),
        border = BorderStroke(1.dp, Color(0xFF1E283A)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = trigger,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ElectricCyan,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 10.sp,
                color = TextMuted,
                maxLines = 1,
            )
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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1624)),
        border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onClick(trigger, expansion) },
    ) {
        Text(
            text = trigger,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = CyberEmerald,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun CryptoPresetBadge(
    trigger: String,
    chainName: String,
    sample: String,
    onClick: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C2C)),
        border = BorderStroke(1.dp, CyberAmber.copy(alpha = 0.4f)),
        modifier = Modifier.clickable { onClick(trigger) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = trigger,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = CyberAmber,
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "($chainName)",
                fontSize = 10.sp,
                color = TextMuted,
            )
        }
    }
}
