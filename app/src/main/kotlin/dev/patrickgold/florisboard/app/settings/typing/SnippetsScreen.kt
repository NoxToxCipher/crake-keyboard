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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.CryptoChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardSurface = Color(0xFF0F172A)
private val CardBorder = Color(0xFF1E293B)
private val CyberEmerald = Color(0xFF10B981)
private val ElectricCyan = Color(0xFF06B6D4)
private val CyberAmber = Color(0xFFF59E0B)
private val CyberCrimson = Color(0xFFEF4444)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun SnippetsScreen() = FlorisScreen {
    title = "Smart Text & Crypto Snippets"
    previewFieldVisible = true
    scrollable = false

    val context = LocalContext.current
    val dictionaryManager = DictionaryManager.default()
    val scope = rememberCoroutineScope()

    var snippetList by remember { mutableStateOf(emptyList<UserDictionaryEntry>()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showAllChains by remember { mutableStateOf(false) }
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
        val filteredSnippets = remember(snippetList, searchQuery, selectedFilter) {
            snippetList.filter { entry ->
                val shortcut = entry.shortcut.orEmpty()
                val text = entry.word
                val chain = CryptoChain.detectFromShortcut(shortcut) ?: CryptoChain.detectFromAddress(text)

                val matchesFilter = when (selectedFilter) {
                    "CRYPTO" -> chain != null
                    "CUSTOM" -> chain == null
                    else -> true
                }

                val matchesSearch = if (searchQuery.isBlank()) {
                    true
                } else {
                    val q = searchQuery.trim().lowercase()
                    shortcut.lowercase().contains(q) ||
                        text.lowercase().contains(q) ||
                        (chain?.displayName?.lowercase()?.contains(q) == true) ||
                        (chain?.symbol?.lowercase()?.contains(q) == true)
                }

                matchesFilter && matchesSearch
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
        ) {
            // Header Hero Banner Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Title Header (Centered)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ElectricCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "AIR-GAPPED SNIPPET STUDIO",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Type shortcut triggers (e.g. !btc1, !time, !email) to instantly expand in any app",
                            fontSize = 11.sp,
                            color = TextMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section 1: Crypto Wallet Presets (Centered Header)
                        CenteredSectionHeader(
                            icon = Icons.Default.CurrencyBitcoin,
                            title = "SUPPORTED CRYPTO CHAINS",
                            color = CyberAmber,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // Core Featured Crypto Chains (3-4 in Focus)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CryptoCardPill("!sol1", "Solana", "SOL", Modifier.weight(1f)) {
                                editingEntry = null
                                inputTrigger = "!sol1"
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoCardPill("!btc1", "Bitcoin", "BTC", Modifier.weight(1f)) {
                                editingEntry = null
                                inputTrigger = "!btc1"
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoCardPill("!eth1", "Ethereum", "ETH", Modifier.weight(1f)) {
                                editingEntry = null
                                inputTrigger = "!eth1"
                                inputExpansion = ""
                                showDialog = true
                            }
                            CryptoCardPill("!xmr1", "Monero", "XMR", Modifier.weight(1f)) {
                                editingEntry = null
                                inputTrigger = "!xmr1"
                                inputExpansion = ""
                                showDialog = true
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Fading Horizontal Scroller for Additional Chains
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            0.0f to Color.Transparent,
                                            0.05f to Color.Black,
                                            0.95f to Color.Black,
                                            1.0f to Color.Transparent,
                                        ),
                                        blendMode = BlendMode.DstIn,
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val remainingChains = listOf(
                                    Triple("!trx1", "Tron", "TRX"),
                                    Triple("!ltc1", "Litecoin", "LTC"),
                                    Triple("!bnb1", "BNB Chain", "BNB"),
                                    Triple("!pol1", "Polygon", "POL"),
                                    Triple("!avax1", "Avalanche", "AVAX"),
                                    Triple("!base1", "Base", "BASE"),
                                    Triple("!ada1", "Cardano", "ADA"),
                                    Triple("!doge1", "Dogecoin", "DOGE"),
                                    Triple("!xrp1", "Ripple", "XRP"),
                                    Triple("!dot1", "Polkadot", "DOT"),
                                    Triple("!rune1", "THORChain", "RUNE"),
                                    Triple("!atom1", "Cosmos", "ATOM"),
                                )
                                for ((t, name, sym) in remainingChains) {
                                    CryptoPresetBadge(t, name, sym) { trigger ->
                                        editingEntry = null
                                        inputTrigger = trigger
                                        inputExpansion = ""
                                        showDialog = true
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section 2: Dynamic Macros (Centered Header)
                        CenteredSectionHeader(
                            icon = Icons.Default.AutoAwesome,
                            title = "BUILT-IN DYNAMIC MACROS",
                            color = ElectricCyan,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val now = remember { Date() }
                        val timeStr = remember { SimpleDateFormat("h:mm a", Locale.getDefault()).format(now) }
                        val dateStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now) }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            MacroPill("!time", timeStr, Modifier.weight(1f))
                            MacroPill("!date", dateStr, Modifier.weight(1f))
                            MacroPill("!now", "Full ISO", Modifier.weight(1f))
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Section 3: General Templates (Centered Header & Single-Line Balanced Pills)
                        CenteredSectionHeader(
                            icon = Icons.Default.Edit,
                            title = "GENERAL TEMPLATES",
                            color = CyberEmerald,
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            TemplatePill("!email", "name@example.com", Modifier.weight(1f)) { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                            TemplatePill("!addr", "123 Cyber St", Modifier.weight(1f)) { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                            TemplatePill("!shrug", "¯\\_(ツ)_/¯", Modifier.weight(1f)) { t, e ->
                                editingEntry = null
                                inputTrigger = t
                                inputExpansion = e
                                showDialog = true
                            }
                        }
                    }
                }
            }

            // Search Bar & Filter Tabs
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search shortcuts, addresses, chains...", color = TextMuted, fontSize = 12.5.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = CardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                        ),
                        shape = RoundedCornerShape(12.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip("ALL (${snippetList.size})", selectedFilter == "ALL") { selectedFilter = "ALL" }
                        FilterChip("CRYPTO", selectedFilter == "CRYPTO") { selectedFilter = "CRYPTO" }
                        FilterChip("CUSTOM", selectedFilter == "CUSTOM") { selectedFilter = "CUSTOM" }
                    }
                }
            }

            if (filteredSnippets.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(1.dp, CardBorder),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No snippets in this category" else "No matching snippets found",
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                fontSize = 14.sp,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap '+ Add Snippet' below or pick a preset above",
                                color = TextMuted,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                items(filteredSnippets, key = { it.id }) { entry ->
                    val shortcut = entry.shortcut ?: "!"
                    val detectedChain = CryptoChain.detectFromShortcut(shortcut) ?: CryptoChain.detectFromAddress(entry.word)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = CardSurface),
                        border = BorderStroke(
                            1.dp,
                            if (detectedChain != null) CyberAmber.copy(alpha = 0.45f) else CardBorder,
                        ),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (detectedChain != null) CyberAmber.copy(alpha = 0.15f) else CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            ) {
                                Text(
                                    text = shortcut,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (detectedChain != null) CyberAmber else CyberEmerald,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                if (detectedChain != null) {
                                    Text(
                                        text = "${detectedChain.displayName} (${detectedChain.symbol})",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = CyberAmber,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                }
                                Text(
                                    text = entry.word,
                                    fontSize = if (entry.word.length > 25) 12.sp else 13.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = if (detectedChain != null) FontFamily.Monospace else FontFamily.Default,
                                    color = Color.White,
                                    maxLines = 2,
                                    softWrap = true,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText(shortcut, entry.word))
                                        Toast.makeText(context, "Copied $shortcut to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy",
                                        tint = TextMuted,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingEntry = entry
                                        inputTrigger = entry.shortcut ?: "!"
                                        inputExpansion = entry.word
                                        showDialog = true
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Edit",
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        scope.launch(Dispatchers.IO) {
                                            dictionaryManager.florisUserDictionaryDao()?.delete(entry)
                                            refreshSnippets()
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
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
                            placeholder = { Text("e.g. !sol1, !btc1, !eth1, !xmr1, !addr", color = TextMuted, fontSize = 13.sp) },
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
                                    val finalTrigger = if (inputTrigger.startsWith("!")) inputTrigger.trim() else "!${inputTrigger.trim()}"
                                    val finalExpansion = inputExpansion.trim()
                                    if (finalTrigger.isNotBlank() && finalExpansion.isNotBlank()) {
                                        scope.launch(Dispatchers.IO) {
                                            val dao = dictionaryManager.florisUserDictionaryDao()
                                            val entryToSave = if (editingEntry != null) {
                                                editingEntry!!.copy(
                                                    word = finalExpansion,
                                                    shortcut = finalTrigger,
                                                    freq = 255,
                                                    locale = null,
                                                )
                                            } else {
                                                UserDictionaryEntry(
                                                    id = 0L,
                                                    word = finalExpansion,
                                                    shortcut = finalTrigger,
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
private fun CenteredSectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    color: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CardBorder,
            thickness = 1.dp,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(13.dp),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
            fontFamily = FontFamily.Monospace,
            color = color,
        )
        Spacer(modifier = Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = CardBorder,
            thickness = 1.dp,
        )
    }
}

@Composable
private fun CryptoCardPill(
    trigger: String,
    chainName: String,
    symbol: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131C2E)),
        border = BorderStroke(1.dp, CyberAmber.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = trigger,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = CyberAmber,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = symbol,
                fontSize = 9.5.sp,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TemplatePill(
    trigger: String,
    preview: String,
    modifier: Modifier = Modifier,
    onClick: (String, String) -> Unit,
) {
    Card(
        modifier = modifier.clickable { onClick(trigger, preview) },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1A24)),
        border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = trigger,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = CyberEmerald,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = preview,
                fontSize = 9.5.sp,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun FilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) ElectricCyan.copy(alpha = 0.15f) else Color(0xFF0F1624)),
        border = BorderStroke(1.dp, if (isSelected) ElectricCyan else Color(0xFF1E283A)),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) ElectricCyan else TextMuted,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun MacroPill(trigger: String, description: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1624)),
        border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = trigger,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ElectricCyan,
                maxLines = 1,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                fontSize = 9.5.sp,
                color = TextMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CryptoPresetBadge(
    trigger: String,
    chainName: String,
    symbol: String,
    onClick: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF151C2C)),
        border = BorderStroke(1.dp, CyberAmber.copy(alpha = 0.3f)),
        modifier = Modifier.clickable { onClick(trigger) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = trigger,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = CyberAmber,
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = "($symbol)",
                fontSize = 9.5.sp,
                color = TextMuted,
            )
        }
    }
}
