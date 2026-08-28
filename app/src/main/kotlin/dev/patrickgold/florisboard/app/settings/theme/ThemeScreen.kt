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

package dev.patrickgold.florisboard.app.settings.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LinearScale
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.app.ext.AddonManagementReferenceBox
import dev.patrickgold.florisboard.app.ext.ExtensionListScreenType
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeManager
import dev.patrickgold.florisboard.ime.theme.ThemeMode
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import dev.patrickgold.florisboard.lib.compose.CrakeRadioPreference
import dev.patrickgold.florisboard.lib.compose.CrakeRadioIndicator
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.Preference
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun ThemeScreen() = FlorisScreen {
    title = stringRes(R.string.settings__theme__title)
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val navController = LocalNavController.current
    val themeManager by context.themeManager()
    val extensionManager by context.extensionManager()
    val scope = rememberCoroutineScope()

    val indexedThemeExtensions by extensionManager.themes.collectAsState()
    val allThemes = remember(indexedThemeExtensions) {
        indexedThemeExtensions.flatMap { ext ->
            ext.themes.map { comp -> Pair(ext.meta.id, comp) }
        }.sortedBy { it.second.label }
    }

    val dayThemeId by prefs.theme.dayThemeId.collectAsState()
    val nightThemeId by prefs.theme.nightThemeId.collectAsState()
    val activeThemeInfo by themeManager.activeThemeInfo.collectAsState()

    fun getThemeAccentColor(themeId: String): Color {
        return when {
            "purple" in themeId -> Color(0xFFA855F7)
            "crimson" in themeId -> Color(0xFFEF4444)
            "sakura" in themeId -> Color(0xFFEC4899)
            "emerald" in themeId -> Color(0xFF00E5A3)
            "amber" in themeId -> Color(0xFFF59E0B)
            "ghost" in themeId -> Color(0xFFF8FAFC)
            else -> Color(0xFF00D2FF)
        }
    }

    content {
        CrakeSectionHeader(title = "CYBERPUNK COLORWAYS", badgeText = "14 THEMES")

        for ((extensionId, comp) in allThemes) {
            val isSelected = (dayThemeId.componentId == comp.id && dayThemeId.extensionId == extensionId) ||
                (activeThemeInfo.name.componentId == comp.id && activeThemeInfo.name.extensionId == extensionId)
            val accent = getThemeAccentColor(comp.id)
            val isBorderless = "borderless" in comp.id

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        val extComponentName = ExtensionComponentName(extensionId, comp.id)
                        scope.launch {
                            prefs.theme.dayThemeId.set(extComponentName)
                            prefs.theme.nightThemeId.set(extComponentName)
                            themeManager.previewThemeId.value = extComponentName
                        }
                    },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) Color(0xFF162033) else CardSurface,
                ),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) accent.copy(alpha = 0.5f) else CardBorder,
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(accent),
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = comp.label,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isBorderless) Color(0xFF1E293B) else accent.copy(alpha = 0.15f))
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = if (isBorderless) "BORDERLESS" else "TITANIUM FRETS",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = if (isBorderless) TextMuted else accent,
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    CrakeRadioIndicator(
                        selected = isSelected,
                        enabled = true,
                        accentColor = accent,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        CrakeSectionHeader(title = "BLACKBERRY 10 TITANIUM FRETS", badgeText = "GEOMETRIC")

        CrakeRadioPreference(
            pref = prefs.theme.showFretsOnBorderless,
            title = "Show Frets on Borderless Themes",
            summary = "Render metallic fret bars and cyan touch pulse when using borderless themes",
            icon = Icons.Default.LinearScale,
            accentColor = CyberEmerald,
        )

        Spacer(modifier = Modifier.height(14.dp))
        CrakeSectionHeader(title = "SYSTEM OVERRIDES & DAY/NIGHT")

        CrakeListPreference(
            prefs.theme.mode,
            icon = Icons.Default.BrightnessAuto,
            title = stringRes(R.string.pref__theme__mode__label),
            entries = enumDisplayEntriesOf(ThemeMode::class),
        )

        Spacer(modifier = Modifier.height(16.dp))
        AddonManagementReferenceBox(type = ExtensionListScreenType.EXT_THEME)
        Spacer(modifier = Modifier.height(20.dp))
    }
}
