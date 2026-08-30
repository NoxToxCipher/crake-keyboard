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

package dev.patrickgold.florisboard.app.devtools

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
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.FlorisUserDictionaryDatabase
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionArrangement
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisConfirmDeleteDialog
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidSettings
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.showLongToast
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val CyberAmber = Color(0xFFF59E0B)
private val TextMuted = Color(0xFF94A3B8)

class DebugOnPurposeCrashException : Exception(
    "Success! Purposeful test crash triggered for diagnostic inspection."
)

@Composable
fun DevtoolsScreen() = FlorisScreen {
    title = "Developer Diagnostics"
    previewFieldVisible = true

    val context = LocalContext.current
    val navController = LocalNavController.current
    val extensionManager by context.extensionManager()
    val scope = rememberCoroutineScope()

    val (showDialog, setShowDialog) = remember { mutableStateOf(false) }

    content {
        // Diagnostic Status Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Memory,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Safe Rust NLP Engine",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "5.3M word/s • Trie NLP • DTW Kinematics",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(ElectricCyan.copy(alpha = 0.15f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "DEBUG",
                        color = ElectricCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // 1. MASTER DIAGNOSTICS TOGGLE
        CrakeSectionHeader(title = "Diagnostics Mode", badgeText = "MASTER", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.devtools.enabled,
            title = "Enable Developer Mode",
            summary = "Activate internal diagnostics, overlays, and logging tools",
            icon = Icons.Default.Adb,
            accentColor = ElectricCyan,
        )

        // 2. VISUAL OVERLAYS
        Spacer(modifier = Modifier.height(10.dp))
        CrakeSectionHeader(title = "Visual Debug Overlays", badgeText = "OVERLAYS", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.devtools.showInputStateOverlay,
            title = "Input State Overlay",
            summary = "Display real-time cursor selection and composition span",
            accentColor = CyberEmerald,
            enabledIf = { prefs.devtools.enabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.devtools.showSpellingOverlay,
            title = "Spelling & NLP Engine Overlay",
            summary = "Show candidate tokens and Damerau-Levenshtein distances",
            accentColor = CyberEmerald,
            enabledIf = { prefs.devtools.enabled.get() },
        )
        if (AndroidVersion.ATLEAST_API30_R) {
            CrakeRadioPreference(
                pref = prefs.devtools.showInlineAutofillOverlay,
                title = "Inline Autofill Overlay",
                summary = "Display debug bounding boxes for autofill pills",
                accentColor = CyberEmerald,
                enabledIf = { prefs.devtools.enabled.get() },
            )
        }
        CrakeRadioPreference(
            pref = prefs.devtools.showKeyTouchBoundaries,
            title = "Key Touch Boundaries & Hitboxes",
            summary = "Draw expanded spatial hitboxes on active keycaps",
            accentColor = CyberEmerald,
            enabledIf = { prefs.devtools.enabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.devtools.showWindowResizeHandleBoundaries,
            title = "Window Resize Handle Boundaries",
            summary = "Highlight draggable edge anchors for floating keyboard",
            accentColor = CyberEmerald,
            enabledIf = { prefs.devtools.enabled.get() },
        )

        // 3. LOGS & DIAGNOSTIC ACTIONS
        Spacer(modifier = Modifier.height(10.dp))
        CrakeSectionHeader(title = "Engine Diagnostics & Actions", badgeText = "TOOLS", accentColor = ElectricCyan)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = { navController.navigate(Routes.Devtools.ExportDebugLog) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Export Debug Engine Log",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Inspect and share detailed runtime crash and touch telemetry",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = {
                scope.launch {
                    prefs.smartbar.actionArrangement.set(QuickActionArrangement.Default)
                    context.showLongToast(R.string.devtools__reset_quick_actions_to_default__toast_success)
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset Smartbar Actions",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Restore default quick action capsule arrangement",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = {
                scope.launch {
                    prefs.keyboard.windowConfig.reset().fold(
                        onSuccess = { context.showLongToast(R.string.devtools__reset_window_config__toast_success) },
                        onFailure = { error ->
                            context.showLongToast(R.string.devtools__reset_window_config__toast_failure, "message" to "${error.localizedMessage}")
                        },
                    )
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Reset Keyboard Dimensions",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reset fixed and floating window size parameters to stock",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // 4. FLIGHT RECORDER & NLP EVALUATION
        Spacer(modifier = Modifier.height(10.dp))
        CrakeSectionHeader(title = "Crake Flight Recorder", badgeText = "NLP EVAL", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.devtools.flightRecorderEnabled,
            title = "Record Key Actions & Gestures",
            summary = "Log typing taps, glide gestures, autocorrects, and missed typos for NLP evaluation",
            icon = Icons.Default.Terminal,
            accentColor = ElectricCyan,
        )
        CrakeRadioPreference(
            pref = prefs.devtools.flightRecorderIncludeSuggestions,
            title = "Include Candidate Suggestions",
            summary = "Attach trie and DTW candidate words to each flight record entry",
            icon = Icons.Default.Layers,
            accentColor = ElectricCyan,
            enabledIf = { prefs.devtools.flightRecorderEnabled.get() },
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = {
                scope.launch {
                    val records = dev.patrickgold.florisboard.ime.nlp.FlightRecorderManager.readRecentRecords(context, limit = 500)
                    if (records.isNotEmpty()) {
                        val text = records.joinToString("\n")
                        val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                        cm?.setPrimaryClip(android.content.ClipData.newPlainText("Crake Flight Recorder Log", text))
                        context.showLongToast("Copied ${records.size} flight records to clipboard")
                    } else {
                        context.showLongToast("Flight recorder log is currently empty")
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
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
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Copy Flight Recorder Log (.jsonl)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Copies the latest 500 recorded typing actions and typos to clipboard",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = {
                scope.launch {
                    val success = dev.patrickgold.florisboard.ime.nlp.FlightRecorderManager.clearLogFile(context)
                    if (success) {
                        context.showLongToast("Flight recorder log cleared")
                    }
                }
            },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Red.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Clear Flight Recorder Log",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Reset on-device circular flight recording buffer",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // 5. ANDROID SYSTEM CONFIGURATION
        Spacer(modifier = Modifier.height(10.dp))
        CrakeSectionHeader(title = "Android System Settings", badgeText = "SYSTEM", accentColor = CyberAmber)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = { navController.navigate(Routes.Devtools.AndroidLocales) },
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = CyberAmber,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Android System Locales",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Inspect all registered OS locale providers and BCP-47 tags",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
