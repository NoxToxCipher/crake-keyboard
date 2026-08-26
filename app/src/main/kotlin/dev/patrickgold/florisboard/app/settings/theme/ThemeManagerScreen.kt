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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import dev.patrickgold.florisboard.extensionManager
import dev.patrickgold.florisboard.ime.theme.ThemeExtensionComponent
import dev.patrickgold.florisboard.lib.compose.CrakeRadioIndicator
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.ext.ExtensionComponentName
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

enum class ThemeManagerScreenAction(val id: String) {
    SELECT_DAY("select-day"),
    SELECT_NIGHT("select-night");
}

@Composable
fun ThemeManagerScreen(action: ThemeManagerScreenAction?) = FlorisScreen {
    title = stringRes(when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> R.string.settings__theme_manager__title_day
        ThemeManagerScreenAction.SELECT_NIGHT -> R.string.settings__theme_manager__title_night
        else -> error("Theme manager screen action must not be null")
    })
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val extensionManager by context.extensionManager()
    val themeManager by context.themeManager()
    val scope = rememberCoroutineScope()

    val indexedThemeExtensions by extensionManager.themes.collectAsState()
    val extGroupedThemes = remember(indexedThemeExtensions) {
        buildMap<String, List<ThemeExtensionComponent>> {
            for (ext in indexedThemeExtensions) {
                put(ext.meta.id, ext.themes)
            }
        }.mapValues { (_, configs) -> configs.sortedBy { it.label } }
    }

    fun getThemeIdPref() = when (action) {
        ThemeManagerScreenAction.SELECT_DAY -> prefs.theme.dayThemeId
        ThemeManagerScreenAction.SELECT_NIGHT -> prefs.theme.nightThemeId
    }

    fun setTheme(extId: String, componentId: String) {
        val extComponentName = ExtensionComponentName(extId, componentId)
        scope.launch {
            prefs.theme.dayThemeId.set(extComponentName)
            prefs.theme.nightThemeId.set(extComponentName)
            themeManager.previewThemeId.value = extComponentName
        }
    }

    val activeThemeId by when (action) {
        ThemeManagerScreenAction.SELECT_DAY,
        ThemeManagerScreenAction.SELECT_NIGHT
            -> getThemeIdPref().collectAsState()
    }

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
        DisposableEffect(activeThemeId) {
            themeManager.previewThemeId.value = activeThemeId
            onDispose {
                themeManager.previewThemeId.value = null
            }
        }

        for ((extensionId, configs) in extGroupedThemes) key(extensionId) {
            val ext = extensionManager.getExtensionById(extensionId)!!
            
            Text(
                text = ext.meta.title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
                color = ElectricCyan,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            )

            for (config in configs) key(extensionId, config.id) {
                val isSelected = activeThemeId.extensionId == extensionId && activeThemeId.componentId == config.id
                val accent = getThemeAccentColor(config.id)
                val isBorderless = "borderless" in config.id

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            setTheme(extensionId, config.id)
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
                        // Color Dot Badge
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
                                text = config.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                fontSize = 13.5.sp,
                                color = if (isSelected) Color.White else Color(0xFFE2E8F0),
                            )
                        }

                        // Badge Tag
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
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
