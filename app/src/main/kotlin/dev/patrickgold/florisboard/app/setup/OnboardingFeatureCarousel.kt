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

package dev.patrickgold.florisboard.app.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.CrakeFont
import dev.patrickgold.florisboard.ime.theme.CrakeFonts
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

private val CardBackground = Color(0xFF0F172A)
private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val NeonPurple = Color(0xFFA855F7)
private val CrimsonRed = Color(0xFFEF4444)
private val CyberAmber = Color(0xFFF59E0B)
private val GhostSlate = Color(0xFFF8FAFC)
private val TextMuted = Color(0xFF94A3B8)

enum class OnboardingThemeChoice(
    val label: String,
    val extensionId: String,
    val componentId: String,
    val accentColor: Color,
    val description: String,
) {
    GHOST_SLATE("Crake Ghost Titanium White", "org.florisboard.themes", "crake_ghost_white_borderless", GhostSlate, "Ultra-clean minimalist titanium slate (Borderless)"),
    ELECTRIC_CYAN("Crake Obsidian Cyan", "org.florisboard.themes", "crake_cyan_borderless", ElectricCyan, "High-contrast electric cyan (Borderless)"),
    CYBER_EMERALD("Crake Obsidian Emerald", "org.florisboard.themes", "crake_emerald_borderless", CyberEmerald, "Obsidian black with neon emerald accents (Borderless)"),
    NEON_PURPLE("Crake Obsidian Purple", "org.florisboard.themes", "crake_purple_borderless", NeonPurple, "Synthwave violet & cyberpunk glow (Borderless)"),
    CRIMSON_SPEEDSTER("Crake Obsidian Crimson", "org.florisboard.themes", "crake_crimson_borderless", CrimsonRed, "Race-tuned dynamic crimson (Borderless)"),
    CYBER_AMBER("Crake Obsidian Amber", "org.florisboard.themes", "crake_amber_borderless", CyberAmber, "Warm amber cockpit HUD aesthetic (Borderless)");
}

@Composable
fun OnboardingFeatureCarousel(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val totalPages = 5
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val themeManager by context.themeManager()

    val keyboardFont by prefs.fonts.keyboardFont.collectAsState()
    val dayThemeId by prefs.theme.dayThemeId.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top Header: Step Indicator & Skip
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Pill / Dot Progress Indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until totalPages) {
                    val isSelected = i == currentPage
                    val isPast = i < currentPage
                    val width by animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = tween(300),
                        label = "dotWidth"
                    )
                    val color by animateColorAsState(
                        targetValue = when {
                            isSelected -> CyberEmerald
                            isPast -> ElectricCyan.copy(alpha = 0.5f)
                            else -> CardBorder
                        },
                        animationSpec = tween(300),
                        label = "dotColor"
                    )
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(width)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { currentPage = i }
                    )
                }
            }

            TextButton(onClick = onFinish) {
                Text(
                    text = "Skip Tour",
                    color = TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main Card Surface with Animated Transition
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            AnimatedContent(
                targetState = currentPage,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally { it } + fadeIn()).togetherWith(slideOutHorizontally { -it } + fadeOut())
                    } else {
                        (slideInHorizontally { -it } + fadeIn()).togetherWith(slideOutHorizontally { it } + fadeOut())
                    }
                },
                label = "carouselCardAnimation",
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when (page) {
                        0 -> AirGappedPrivacyCard()
                        1 -> NativeRustEngineCard()
                        2 -> LiveThemeStudioCard(
                            currentThemeId = dayThemeId.componentId,
                            onSelectTheme = { theme ->
                                scope.launch {
                                    val extComp = ExtensionComponentName(theme.extensionId, theme.componentId)
                                    prefs.theme.dayThemeId.set(extComp)
                                    prefs.theme.nightThemeId.set(extComp)
                                    themeManager.previewThemeId.value = extComp
                                }
                            }
                        )
                        3 -> ScientificTypographyCard(
                            currentFont = keyboardFont,
                            onSelectFont = { font ->
                                scope.launch {
                                    prefs.fonts.keyboardFont.set(font)
                                    prefs.fonts.appFont.set(font)
                                }
                            }
                        )
                        4 -> ProGesturesAndTestDriveCard()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (currentPage > 0) {
                OutlinedButton(
                    onClick = { currentPage-- },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CardBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous",
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "Back", fontSize = 13.sp)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Button(
                onClick = {
                    if (currentPage < totalPages - 1) {
                        currentPage++
                    } else {
                        onFinish()
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberEmerald,
                    contentColor = Color.Black,
                ),
            ) {
                Text(
                    text = if (currentPage == totalPages - 1) "Start Typing with Crake" else "Next",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = if (currentPage == totalPages - 1) Icons.Default.RocketLaunch else Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Next",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CardHeaderBadge(
    icon: ImageVector,
    badgeText: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(badgeColor.copy(alpha = 0.15f))
            .border(1.dp, badgeColor.copy(alpha = 0.4f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = badgeColor,
            modifier = Modifier.size(28.dp),
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(badgeColor.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = badgeText,
            color = badgeColor,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = title,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        textAlign = TextAlign.Center,
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = subtitle,
        color = TextMuted,
        fontSize = 12.5.sp,
        textAlign = TextAlign.Center,
        lineHeight = 17.sp,
    )

    Spacer(modifier = Modifier.height(14.dp))
}

@Composable
private fun FeaturePillItem(
    emoji: String,
    title: String,
    detail: String,
    accentColor: Color = CyberEmerald,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 20.sp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
                Text(
                    text = detail,
                    color = TextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun AirGappedPrivacyCard() {
    CardHeaderBadge(
        icon = Icons.Default.Shield,
        badgeText = "AIR-GAPPED SECURITY",
        badgeColor = CyberEmerald,
        title = "100% On-Device Privacy",
        subtitle = "Crake never connects to the internet. Your keystrokes, passwords, and data stay physically isolated on your device.",
    )

    FeaturePillItem(
        emoji = "🚫",
        title = "Zero Network Permission",
        detail = "android.permission.INTERNET is completely absent from the app manifest.",
    )
    FeaturePillItem(
        emoji = "🧼",
        title = "Cryptographic Memory Zeroing",
        detail = "Transient typing buffers and clipboard caches are wiped immediately.",
    )
    FeaturePillItem(
        emoji = "🔒",
        title = "Local Intelligence Only",
        detail = "Dictionary lookups, spelling corrections, and learning occur 100% offline.",
    )
}

@Composable
private fun NativeRustEngineCard() {
    CardHeaderBadge(
        icon = Icons.Default.Speed,
        badgeText = "NATIVE RUST CORE",
        badgeColor = ElectricCyan,
        title = "5.3M Words/Sec Native Engine",
        subtitle = "Built with memory-safe compiled Rust (libnative) for sub-millisecond suggestions and fluid glide typing.",
    )

    FeaturePillItem(
        emoji = "⚡",
        title = "Microsecond Trie Lookups",
        detail = "Instant predictive suggestions with zero frame drops or input lag.",
        accentColor = ElectricCyan,
    )
    FeaturePillItem(
        emoji = "🎯",
        title = "Adaptive Beam Search",
        detail = "Spatial Damerau-Levenshtein distance handles clumsy thumb typos effortlessly.",
        accentColor = ElectricCyan,
    )
    FeaturePillItem(
        emoji = "🌊",
        title = "Kinematic Gesture Flow",
        detail = "Continuous dynamic time warping (DTW) for seamless swipe typing.",
        accentColor = ElectricCyan,
    )
}

@Composable
private fun LiveThemeStudioCard(
    currentThemeId: String,
    onSelectTheme: (OnboardingThemeChoice) -> Unit,
) {
    CardHeaderBadge(
        icon = Icons.Default.Palette,
        badgeText = "THEME STUDIO",
        badgeColor = NeonPurple,
        title = "Personalize Your Aesthetic",
        subtitle = "Tap a colorway below to apply it instantly and watch the keyboard preview update in real-time.",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (theme in OnboardingThemeChoice.entries) {
            val isSelected = currentThemeId == theme.componentId || (currentThemeId.startsWith("crake_") && theme.componentId.contains(currentThemeId.removePrefix("crake_").removeSuffix("_bordered").removeSuffix("_borderless")))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectTheme(theme) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF131A29),
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) theme.accentColor else CardBorder,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(theme.accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = theme.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        )
                        Text(
                            text = theme.description,
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScientificTypographyCard(
    currentFont: CrakeFont,
    onSelectFont: (CrakeFont) -> Unit,
) {
    CardHeaderBadge(
        icon = Icons.Default.TextFields,
        badgeText = "LEGIBILITY TYPOGRAPHY",
        badgeColor = CyberAmber,
        title = "Engineered Fonts",
        subtitle = "Scientifically designed letterforms disambiguate similar characters like I, l, 1, 0, and O at a glance.",
    )

    val fontOptions = listOf(
        Triple(
            CrakeFont.ATKINSON_HYPERLEGIBLE,
            "Atkinson Hyperlegible",
            "Designed by the Braille Institute for unmistakable character distinction."
        ),
        Triple(
            CrakeFont.B612,
            "B612 Cockpit",
            "Designed for Airbus cockpit avionics: readable under extreme conditions."
        ),
        Triple(
            CrakeFont.LEXEND,
            "Lexend Variable",
            "Scientifically tuned to improve reading speed and reduce cognitive friction."
        ),
        Triple(
            CrakeFont.SYSTEM,
            "System Default",
            "Standard clean Android operating system typeface."
        ),
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for ((font, name, desc) in fontOptions) {
            val isSelected = currentFont == font

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelectFont(font) },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF1E293B) else Color(0xFF131A29),
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) CyberAmber else CardBorder,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isSelected) CyberAmber.copy(alpha = 0.2f) else Color(0xFF1E293B)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Aa",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSelected) CyberAmber else Color.White,
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                        )
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = TextMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactGesturePill(
    emoji: String,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF162033)),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = emoji, fontSize = 16.sp)
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
                Text(
                    text = detail,
                    color = TextMuted,
                    fontSize = 9.sp,
                    lineHeight = 11.5.sp,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ProGesturesAndTestDriveCard() {
    var testText by remember { mutableStateOf("") }

    CardHeaderBadge(
        icon = Icons.Default.Gesture,
        badgeText = "PRO GESTURES & TEST DRIVE",
        badgeColor = CyberEmerald,
        title = "Supercharged Gestures",
        subtitle = "Master these lightning-fast gestures, then take your new keyboard for a test drive below:",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactGesturePill(
            modifier = Modifier.weight(1f),
            emoji = "⬆️",
            title = "Word Flick",
            detail = "Flick up on letters to insert predicted words",
        )
        CompactGesturePill(
            modifier = Modifier.weight(1f),
            emoji = "↔️",
            title = "Spacebar Scrub",
            detail = "Drag spacebar to move cursor precisely",
        )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CompactGesturePill(
            modifier = Modifier.weight(1f),
            emoji = "⌫",
            title = "Delete Flick",
            detail = "Swipe left from backspace to erase word",
        )
        CompactGesturePill(
            modifier = Modifier.weight(1f),
            emoji = "🏎️",
            title = "Easter Eggs",
            detail = "Type 'kart' or 'rocket' for animations",
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val savedTesterName by prefs.updater.testerName.collectAsState()
    val hasConfirmedName = savedTesterName.isNotBlank() && !savedTesterName.equals("Tester", ignoreCase = true)
    var isEditingName by remember { mutableStateOf(!hasConfirmedName) }
    var testerNameInput by remember { mutableStateOf(savedTesterName.takeUnless { it == "Tester" } ?: "") }

    androidx.compose.runtime.LaunchedEffect(hasConfirmedName) {
        if (hasConfirmedName) {
            scope.launch {
                prefs.updater.testerNameConfirmed.set(true)
                prefs.updater.testerOnboardingDismissed.set(true)
            }
        }
    }

    if (hasConfirmedName && !isEditingName) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF162033))
                .border(1.dp, CyberEmerald.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "🛡️", fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Tester Identity:",
                        fontSize = 10.sp,
                        color = TextMuted,
                    )
                    Text(
                        text = savedTesterName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = CyberEmerald,
                    )
                }
            }
            TextButton(
                onClick = { isEditingName = true },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("Edit", color = ElectricCyan, fontSize = 11.sp)
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Your Tester Username:",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "(identifies your test feedback)",
                fontSize = 10.sp,
                color = TextMuted,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = testerNameInput,
            onValueChange = {
                testerNameInput = it
                scope.launch {
                    val cleaned = it.trim().ifEmpty { "Tester" }
                    prefs.updater.testerName.set(cleaned)
                    prefs.updater.testerNameConfirmed.set(true)
                    prefs.updater.testerOnboardingDismissed.set(true)
                }
            },
            placeholder = {
                Text(
                    text = "e.g. Lochran, Overlord, Daya",
                    color = TextMuted,
                    fontSize = 11.sp,
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF162033),
                unfocusedContainerColor = Color(0xFF162033),
                focusedBorderColor = CyberEmerald,
                unfocusedBorderColor = CardBorder,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
            singleLine = true,
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Interactive Test Drive Box
    OutlinedTextField(
        value = testText,
        onValueChange = { testText = it },
        placeholder = {
            Text(
                text = "Tap here to test typing & gestures...",
                color = TextMuted,
                fontSize = 11.sp,
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp)),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0xFF162033),
            unfocusedContainerColor = Color(0xFF162033),
            focusedBorderColor = CyberEmerald,
            unfocusedBorderColor = CardBorder,
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
        ),
        singleLine = true,
    )
}
