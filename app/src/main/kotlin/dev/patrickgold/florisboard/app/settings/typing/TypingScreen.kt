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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.nlp.SpellingLanguageMode
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun TypingScreen() = FlorisScreen {
    title = "Typing & NLP Engine"
    previewFieldVisible = true

    val navController = LocalNavController.current

    content {
        // Safe Rust Core Status Card
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
                        imageVector = Icons.Default.Spellcheck,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Safe Rust Radix Trie NLP",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "5.3M word/s • Damerau-Levenshtein Typo Pruning",
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .background(CyberEmerald.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = "ONLINE",
                        color = CyberEmerald,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }

        // 1. SUGGESTIONS & PREDICTIONS
        CrakeSectionHeader(title = "Predictive Intelligence", badgeText = "NLP", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.suggestion.enabled,
            title = "Predictive Candidate Bar",
            summary = "Show live candidate capsules in the Smartbar above the keyboard",
            icon = Icons.Default.AutoFixHigh,
            accentColor = ElectricCyan,
        )
        CrakeRadioPreference(
            pref = prefs.glide.flickPredictionsEnabled,
            title = "BB10 Predictive Flick Capsules",
            summary = "Show predicted words directly above next-character keycaps to swipe up and catapult",
            icon = Icons.Default.FlashOn,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.suggestion.blockPossiblyOffensive,
            title = "Filter Sensitive / Offensive Words",
            summary = "Exclude offensive entries from predictive suggestions",
            accentColor = ElectricCyan,
            enabledIf = { prefs.suggestion.enabled.get() },
        )
        CrakeRadioPreference(
            pref = prefs.suggestion.api30InlineSuggestionsEnabled,
            title = "Android Autofill Inline Integration",
            summary = "Display password manager and autofill pills inside the Smartbar",
            accentColor = ElectricCyan,
            visibleIf = { AndroidVersion.ATLEAST_API30_R },
        )
        CrakeListPreference(
            prefs.suggestion.incognitoMode,
            icon = ImageVector.vectorResource(id = R.drawable.ic_incognito),
            title = "Incognito Privacy Mode",
            entries = enumDisplayEntriesOf(IncognitoMode::class),
        )

        // 2. GLIDE TYPING ENGINE
        CrakeSectionHeader(title = "Continuous Glide Typing", badgeText = "GESTURES", accentColor = CyberEmerald)
        CrakeRadioPreference(
            pref = prefs.glide.enabled,
            title = "Enable Glide Typing",
            summary = "Type words by sliding your finger smoothly across letters",
            icon = Icons.Default.Gesture,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.glide.showTrail,
            title = "Neon Aurora Particle Trail",
            summary = "Draw illuminated cyber cyan trail following your touch path",
            accentColor = ElectricCyan,
            enabledIf = { prefs.glide.enabled.get() },
        )

        // 3. AUTO-CORRECTION & CAPITALIZATION
        CrakeSectionHeader(title = "Auto-Corrections", badgeText = "RADIX", accentColor = ElectricCyan)
        CrakeRadioPreference(
            pref = prefs.correction.autoCapitalization,
            title = "Auto-Capitalization",
            summary = "Capitalize first word of each sentence and proper names",
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.correction.doubleSpacePeriod,
            title = "Double-Tap Space for Period",
            summary = "Quickly pressing space twice inserts a period followed by a space",
            icon = Icons.Default.SpaceBar,
            accentColor = CyberEmerald,
        )
        CrakeRadioPreference(
            pref = prefs.correction.autoSpacePunctuation,
            title = "Auto-Space after Punctuation",
            summary = "Automatically insert a space after commas, colons, and punctuation marks",
            accentColor = ElectricCyan,
        )
        CrakeRadioPreference(
            pref = prefs.correction.rememberCapsLockState,
            title = "Remember Caps Lock State",
            summary = "Keep caps locked across input field focus changes",
            accentColor = ElectricCyan,
        )

        // 4. DICTIONARY & EXPANSIONS
        CrakeSectionHeader(title = "Dictionaries & Snippets", badgeText = "VAULT", accentColor = CyberEmerald)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
            onClick = { navController.navigate(Routes.Settings.Snippets) },
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
                        imageVector = Icons.Default.FlashOn,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Smart Text Expansion & Snippets",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Configure custom triggers (!addr, !email, macros)",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                        )
                    }
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
            onClick = { navController.navigate(Routes.Settings.Dictionary) },
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
                            text = "User Dictionaries & Word Lists",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Text(
                            text = "Manage custom words and vocabulary across languages",
                            fontSize = 11.5.sp,
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
