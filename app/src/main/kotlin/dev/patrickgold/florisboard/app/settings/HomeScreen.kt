/*
 * Copyright (C) 2021-2026 The Crake Contributors
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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.BorderStroke
import android.widget.Toast
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material.icons.filled.Refresh
import dev.patrickgold.florisboard.app.updater.UpdateManager
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Feedback
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextAlign
import dev.patrickgold.florisboard.ime.nlp.DiagnosticSyncManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Egg
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SmartButton
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Info
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.patrickgold.florisboard.app.setup.OnboardingFeatureCarousel
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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.ime.keyboard.EasterEggs
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.florisboard.lib.compose.FlorisCanvasIcon
import org.florisboard.lib.compose.FlorisErrorCard
import org.florisboard.lib.compose.FlorisWarningCard
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val CyberAmber = Color(0xFFF59E0B)
private val AmberGold = Color(0xFFFFB300)
private val NeonPink = Color(0xFFFF4081)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun HomeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__home__title)
    navigationIconVisible = false
    previewFieldVisible = true

    val navController = LocalNavController.current
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore

    content {
        val scope = rememberCoroutineScope()
        val isFlorisBoardEnabled by InputMethodUtils.observeIsFlorisboardEnabled(foregroundOnly = true)
        val testerOnboardingDismissed by prefs.updater.testerOnboardingDismissed.collectAsState()
        val testerNameConfirmed by prefs.updater.testerNameConfirmed.collectAsState()
        val currentTesterName by prefs.updater.testerName.collectAsState()
        val hasCustomTesterName = currentTesterName.isNotBlank() && !currentTesterName.equals("Tester", ignoreCase = true)
        var showTesterModal by remember {
            mutableStateOf(
                !testerOnboardingDismissed && !testerNameConfirmed && !hasCustomTesterName
            )
        }
        androidx.compose.runtime.LaunchedEffect(hasCustomTesterName) {
            if (hasCustomTesterName && (!testerNameConfirmed || !testerOnboardingDismissed)) {
                scope.launch {
                    prefs.updater.testerNameConfirmed.set(true)
                    prefs.updater.testerOnboardingDismissed.set(true)
                }
            }
        }
        var inputTesterName by remember {
            mutableStateOf(if (currentTesterName.isBlank() || currentTesterName.equals("Tester", ignoreCase = true)) "" else currentTesterName)
        }

        if (showTesterModal) {
            Dialog(
                onDismissRequest = {
                    scope.launch {
                        prefs.updater.testerNameConfirmed.set(true)
                        prefs.updater.testerOnboardingDismissed.set(true)
                    }
                    showTesterModal = false
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .padding(vertical = 16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                    border = BorderStroke(1.5.dp, ElectricCyan.copy(alpha = 0.6f)),
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ElectricCyan.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RocketLaunch,
                                    contentDescription = null,
                                    tint = ElectricCyan,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "7-DAY TESTER SPRINT",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp,
                                    color = ElectricCyan,
                                )
                                Text(
                                    text = "Aug 30 – Sep 6 Testing Sprint",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Welcome to our 1-week intensive keyboard test round! Here is how your device will collaborate to build the ultimate keyboard:",
                            fontSize = 12.sp,
                            color = Color(0xFFE2E8F0),
                            lineHeight = 16.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        TesterSprintPill(
                            icon = Icons.Default.Refresh,
                            title = "Automated Diagnostic Sync (Every 20 Min)",
                            desc = "Typing accuracy, spatial swipe deltas, and missed corrections sync in lightweight bundles every 20 minutes while active.",
                            accent = CyberEmerald,
                        )

                        TesterSprintPill(
                            icon = Icons.Default.FlashOn,
                            title = "Hourly Background Auto-Updates",
                            desc = "Checks for new milestone APKs once an hour and offers 1-tap in-app installation so you always run the latest optimizations.",
                            accent = ElectricCyan,
                        )

                        TesterSprintPill(
                            icon = Icons.Default.Security,
                            title = "On-Device Encryption & Ephemeral AI Processing",
                            desc = "All logs are encrypted on-device and decrypted exclusively by the AI assistant. Passwords, PINs, and usernames are completely filtered and never recorded. Raw logs are permanently destroyed after analytics.",
                            accent = CyberAmber,
                        )

                        TesterSprintPill(
                            icon = Icons.Default.SentimentSatisfiedAlt,
                            title = "Personalized Performance Graphs at Sprint End",
                            desc = "At the end of the sprint, you will receive a complete graphical report showing your error rate reduction, WPM speed gains, and custom spatial tuning!",
                            accent = Color(0xFFC084FC),
                        )

                        TesterSprintPill(
                            icon = Icons.Default.Gesture,
                            title = "How to Use Word Flicks & Quick Gestures",
                            desc = "Flick upward on any letter key to fling predicted words directly into your text without reaching for the top suggestion bar. Swipe spacebar to navigate cursor.",
                            accent = ElectricCyan,
                        )

                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Your Tester Identity / Name:",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = inputTesterName,
                            onValueChange = { inputTesterName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("e.g. Lochran, Samsung Friend, Hidaya", color = TextMuted, fontSize = 12.sp) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = CyberEmerald,
                                unfocusedBorderColor = CardBorder,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val name = inputTesterName.trim().ifEmpty { "Tester" }
                                scope.launch {
                                    prefs.updater.testerName.set(name)
                                    prefs.updater.testerNameConfirmed.set(true)
                                    prefs.updater.testerOnboardingDismissed.set(true)
                                    prefs.updater.autoCheckEnabled.set(true)
                                    prefs.updater.logSyncEnabled.set(true)
                                    showTesterModal = false
                                    DiagnosticSyncManager.performSync(silent = true)
                                    dev.patrickgold.florisboard.app.updater.UpdateManager.checkForUpdates(silent = true)
                                    Toast.makeText(context, "Tester handle set: $name!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = "Join Sprint & Start Testing (Let's Go!)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.5.sp,
                                color = Color(0xFF0F172A),
                            )
                        }
                    }
                }
            }
        }
        val isFlorisBoardSelected by InputMethodUtils.observeIsFlorisboardSelected(foregroundOnly = true)
        if (!isFlorisBoardEnabled) {
            FlorisErrorCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_enabled),
                onClick = { InputMethodUtils.showImeEnablerActivity(context) },
            )
        } else if (!isFlorisBoardSelected) {
            FlorisWarningCard(
                modifier = Modifier.padding(8.dp),
                showIcon = false,
                text = stringRes(R.string.settings__home__ime_not_selected),
                onClick = { InputMethodUtils.showImePicker(context) },
            )
        }

        // TOP PRIORITY: TESTER FEEDBACK & MANUAL UPDATE CHECKER
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
            border = BorderStroke(1.dp, ElectricCyan.copy(alpha = 0.6f)),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                            imageVector = Icons.Default.Feedback,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Tester Hub & Feedback",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                            maxLines = 1,
                        )
                        Text(
                            text = "Report bugs, screenshots & suggestions",
                            fontSize = 10.5.sp,
                            color = TextMuted,
                            maxLines = 1,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { navController.navigate(Routes.Devtools.TesterFeedback) },
                        colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = "Open",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF0F172A),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CardBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                // MANUAL UPDATE CHECKER ROW
                val updateStatus by UpdateManager.status.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Milestone ${UpdateManager.CURRENT_MILESTONE}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp,
                                color = CyberEmerald,
                                fontFamily = FontFamily.Monospace,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "ACTIVE SPRINT",
                                    color = CyberEmerald,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        when (val st = updateStatus) {
                            is UpdateManager.UpdateStatus.Checking -> {
                                Text("Checking GitHub for updates...", fontSize = 11.sp, color = ElectricCyan)
                            }
                            is UpdateManager.UpdateStatus.UpdateAvailable -> {
                                Text("Milestone ${st.release.milestone} Available!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AmberGold)
                            }
                            is UpdateManager.UpdateStatus.Downloading -> {
                                Text("Downloading: ${st.progressPercent}%", fontSize = 11.sp, color = ElectricCyan)
                            }
                            is UpdateManager.UpdateStatus.ReadyToInstall -> {
                                Text("Milestone ${st.release.milestone} Ready to Install", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = CyberEmerald)
                            }
                            is UpdateManager.UpdateStatus.UpToDate -> {
                                Text("No Update, Please Check Again Soon", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = CyberEmerald)
                            }
                            is UpdateManager.UpdateStatus.Error -> {
                                Text(st.message, fontSize = 10.5.sp, color = Color(0xFFEF4444))
                            }
                            else -> {
                                Text("Automatic hourly checks & instant manual trigger", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                    }

                    when (val st = updateStatus) {
                        is UpdateManager.UpdateStatus.UpdateAvailable -> {
                            Button(
                                onClick = { UpdateManager.downloadAndInstall(context, st.release) },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberGold),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF0F172A), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Update", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                            }
                        }
                        is UpdateManager.UpdateStatus.ReadyToInstall -> {
                            Button(
                                onClick = { UpdateManager.promptInstall(context, st.apkFile) },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberEmerald),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Install", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
                            }
                        }
                        is UpdateManager.UpdateStatus.Checking -> {
                            Button(
                                onClick = {},
                                enabled = false,
                                colors = ButtonDefaults.buttonColors(disabledContainerColor = CardBorder),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Checking...", fontSize = 11.sp, color = TextMuted)
                            }
                        }
                        else -> {
                            Button(
                                onClick = {
                                    Toast.makeText(context, "Checking for Crake updates...", Toast.LENGTH_SHORT).show()
                                    UpdateManager.checkForUpdates(silent = false)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CardBorder),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Check Now", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }

                if (updateStatus is UpdateManager.UpdateStatus.UpdateAvailable || updateStatus is UpdateManager.UpdateStatus.ReadyToInstall) {
                    val targetMilestone = when (val st = updateStatus) {
                        is UpdateManager.UpdateStatus.UpdateAvailable -> st.release.milestone
                        is UpdateManager.UpdateStatus.ReadyToInstall -> st.release.milestone
                        else -> UpdateManager.CURRENT_MILESTONE
                    }
                    val versionCount = (targetMilestone - UpdateManager.CURRENT_MILESTONE).coerceAtLeast(1)
                    val cumulativeChangelog = UpdateManager.getCumulativeChangelog(
                        fromMilestone = UpdateManager.CURRENT_MILESTONE,
                        toMilestone = targetMilestone,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(AmberGold.copy(alpha = 0.1f))
                            .border(1.dp, AmberGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Text(
                                text = "✨ What's New in this Update (M${UpdateManager.CURRENT_MILESTONE} ➔ M$targetMilestone • $versionCount version${if (versionCount > 1) "s" else ""}):",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = AmberGold,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = cumulativeChangelog,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 13.5.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = CardBorder, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))

                // TELEMETRY LEARNING & SPRINT ADDITIONS
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.8f))
                        .border(1.dp, CyberEmerald.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberEmerald)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Telemetry Engine Active",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyberEmerald,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "On-Device Neural Typing & Gesture Engine",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Learns your custom vocabulary & corrects fat-finger keystrokes locally with zero cloud telemetry or keystroke leakage.",
                        fontSize = 10.sp,
                        color = TextMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 13.sp,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Latest Additions (M${UpdateManager.CURRENT_MILESTONE}): ${UpdateManager.getMilestoneHighlights(UpdateManager.CURRENT_MILESTONE)}",
                        fontSize = 9.5.sp,
                        color = ElectricCyan.copy(alpha = 0.9f),
                        textAlign = TextAlign.Center,
                        lineHeight = 12.sp,
                    )
                }
            }
        }

        // WORD FLICK & QUICK GESTURES INTRODUCTORY GUIDE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElectricCyan.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Gesture,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "QUICK GESTURES & FLICKS",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = ElectricCyan,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
                            ) {
                                Text(
                                    text = "FAST TYPING",
                                    color = CyberEmerald,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                        Text(
                            text = "How to Use Word Flicks & Gestures",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0E17))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("⬆️ Upward Flick on Word", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = ElectricCyan)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Fling floating predicted words directly into the text field (BB10 flick typing).", fontSize = 9.5.sp, color = TextMuted, lineHeight = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0E17))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("⬇️ Downward Flick", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = CyberEmerald)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Instant punctuation (!, ?, ,, .) and secondary brackets.", fontSize = 9.5.sp, color = TextMuted, lineHeight = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0E17))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("↔️ Spacebar Swipe", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = AmberGold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Slide finger across spacebar for surgical cursor positioning.", fontSize = 9.5.sp, color = TextMuted, lineHeight = 12.sp)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0A0E17))
                            .border(1.dp, CardBorder, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                    ) {
                        Column {
                            Text("🌊 Glide Typing", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFFC084FC))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Drag smoothly across letter keys for fluid word predictions.", fontSize = 9.5.sp, color = TextMuted, lineHeight = 12.sp)
                        }
                    }
                }
            }
        }

        // CRAKE SECURITY COMMAND HUB CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0A0E17))
                                .border(1.dp, CardBorder, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            FlorisCanvasIcon(
                                modifier = Modifier.size(26.dp),
                                iconId = R.mipmap.floris_app_icon,
                                contentDescription = "Crake App Icon",
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "CRAKE KEYBOARD",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                color = Color.White,
                            )
                            Text(
                                text = "PRIVACY & SECURITY CORE",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                color = ElectricCyan,
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CyberEmerald.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = "ARMOR ACTIVE",
                            color = CyberEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = CardBorder, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Security Telemetry",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Telemetry Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "BIP-39 Secret Shield", fontSize = 12.sp, color = Color.White)
                    Text(
                        text = "ENGAGED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberEmerald,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Telemetry Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Boreal YARA Engine", fontSize = 12.sp, color = Color.White)
                    Text(
                        text = "SCANNING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Telemetry Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "MetaScrub Cleaner", fontSize = 12.sp, color = Color.White)
                    Text(
                        text = "ACTIVE",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberEmerald,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                // Telemetry Row 4
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "Tracker URL Scrubber", fontSize = 12.sp, color = Color.White)
                    Text(
                        text = "40+ STRIPPED",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricCyan,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        var showFeatureTourDialog by remember { mutableStateOf(false) }

        if (showFeatureTourDialog) {
            Dialog(
                onDismissRequest = { showFeatureTourDialog = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0A0E17).copy(alpha = 0.95f))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    OnboardingFeatureCarousel(
                        onFinish = { showFeatureTourDialog = false }
                    )
                }
            }
        }

        // CRAKE FEATURE DISCOVERY & INTERACTIVE TOUR HERO CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable { showFeatureTourDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131F33)),
            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.6f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CyberEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "Feature Tour & Studio",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(CyberEmerald.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "INTERACTIVE",
                                color = CyberEmerald,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Explore air-gapped privacy, customize themes & fonts, and test drive gestures.",
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        lineHeight = 15.sp,
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = CyberEmerald,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // CRAKE CYBER COMMAND HUB NAVIGATION CARDS
        CrakeNavTile(
            icon = Icons.Default.Spellcheck,
            title = "Typing & Prediction Core",
            summary = "5.3M word/s Radix Trie, Damerau-Levenshtein & autocorrect",
            badgeText = "SAFE RUST",
            accentColor = ElectricCyan,
            onClick = { navController.navigate(Routes.Settings.Typing) },
        )
        CrakeNavTile(
            icon = Icons.Default.Gesture,
            title = "Gestures & Glide Typing",
            summary = "Continuous glide trail, directional flicks & cursor drag",
            badgeText = "GESTURES",
            accentColor = ElectricCyan,
            onClick = { navController.navigate(Routes.Settings.Gestures) },
        )
        CrakeNavTile(
            icon = Icons.Default.FlashOn,
            title = "Smart Text Expansion & Snippets",
            summary = "Custom expansion triggers (!addr, !email, crypto macros)",
            badgeText = "MACROS",
            accentColor = ElectricCyan,
            onClick = { navController.navigate(Routes.Settings.Snippets) },
        )
        CrakeNavTile(
            icon = Icons.Default.Security,
            title = "Decoy Profiles & Duress PIN",
            summary = "Dual-profile decoy vault, duress trigger & panic zeroize",
            badgeText = "AIR-GAP",
            accentColor = CyberEmerald,
            onClick = { navController.navigate(Routes.Settings.Other) },
        )
        CrakeNavTile(
            icon = Icons.AutoMirrored.Outlined.Assignment,
            title = "Encrypted Clipboard Vault",
            summary = "ChaCha20-Poly1305 encrypted storage & auto-destruct",
            badgeText = "ENCRYPTED",
            accentColor = ElectricCyan,
            onClick = { navController.navigate(Routes.Settings.Clipboard) },
        )
        CrakeNavTile(
            icon = Icons.Default.QrCode2,
            title = "Air-Gapped Optical QR Sync",
            summary = "Zero-network animated QR pairing & cold offline backup",
            badgeText = "OPTICAL",
            accentColor = CyberEmerald,
            onClick = { navController.navigate(Routes.Settings.OpticalQrSync) },
        )
        CrakeNavTile(
            icon = Icons.Outlined.Palette,
            title = stringRes(R.string.settings__theme__title),
            summary = "OLED Obsidian, BB10 Chrome Frets & Cyber Cyan themes",
            badgeText = "THEME",
            accentColor = ElectricCyan,
            onClick = { navController.navigate(Routes.Settings.Theme) },
        )
        CrakeNavTile(
            icon = Icons.Default.Language,
            title = stringRes(R.string.settings__localization__title),
            summary = "Language subtypes, dictionaries & multilingual layouts",
            onClick = { navController.navigate(Routes.Settings.Localization) },
        )
        CrakeNavTile(
            icon = Icons.Default.TextFields,
            title = "Fonts",
            summary = "Legibility-tested fonts with research evidence",
            onClick = { navController.navigate(Routes.Settings.Fonts) },
        )
        val totalEggs = dev.patrickgold.florisboard.ime.keyboard.EasterEgg.entries.size
        val discoveredEggsCsv by prefs.easterEggs.discovered.collectAsState()
        val recordedEggsCsv by prefs.easterEggs.recorded.collectAsState()
        val discoveredCount = EasterEggs.discoveredEggs(discoveredEggsCsv).size.coerceAtMost(totalEggs)
        val recordedCount = EasterEggs.recordedEggs(recordedEggsCsv).size.coerceAtMost(totalEggs)
        CrakeNavTile(
            icon = Icons.Default.Egg,
            title = "Secret Easter Eggs",
            summary = "Discovered: $discoveredCount/$totalEggs • Solved: $recordedCount/$totalEggs",
            badgeText = "$discoveredCount/$totalEggs",
            accentColor = CyberEmerald,
            onClick = { navController.navigate(Routes.Settings.EasterEggs) },
        )

        CrakeNavTile(
            icon = Icons.Outlined.Keyboard,
            title = stringRes(R.string.settings__keyboard__title),
            summary = "Key height, long-press delays, haptics & audio packs",
            onClick = { navController.navigate(Routes.Settings.Keyboard) },
        )
        CrakeNavTile(
            icon = Icons.Default.SmartButton,
            title = stringRes(R.string.settings__smartbar__title),
            summary = "Action tiles, candidate capsules & quick actions bar",
            onClick = { navController.navigate(Routes.Settings.Smartbar) },
        )
        CrakeNavTile(
            icon = Icons.Default.SentimentSatisfiedAlt,
            title = stringRes(R.string.settings__media__title),
            summary = "4,000+ Unicode emojis, Kaomojis, Math & Crypto symbols",
            onClick = { navController.navigate(Routes.Settings.Media) },
        )
        CrakeNavTile(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__title),
            summary = "Safe Rust Core • Zero Leaks • Local Vault",
            badgeText = "v0.1.0",
            accentColor = CyberEmerald,
            onClick = { navController.navigate(Routes.Settings.About) },
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun CrakeNavTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    summary: String? = null,
    badgeText: String? = null,
    accentColor: Color = ElectricCyan,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(accentColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        lineHeight = 14.sp,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}


@Composable
private fun TesterSprintPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    desc: String,
    accent: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 10.5.sp,
                color = Color(0xFFCBD5E1),
                lineHeight = 14.sp,
            )
        }
    }
}
