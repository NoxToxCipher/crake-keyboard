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

package dev.patrickgold.florisboard.app.apptheme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import dev.patrickgold.jetpref.datastore.model.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.patrickgold.florisboard.app.AppTheme

private val CrakeObsidianDark = darkColorScheme(
    primary = Color(0xFF00D2FF),
    onPrimary = Color(0xFF0A0E17),
    primaryContainer = Color(0xFF131A29),
    onPrimaryContainer = Color(0xFF00D2FF),
    secondary = Color(0xFF00E5A3),
    onSecondary = Color(0xFF0A0E17),
    secondaryContainer = Color(0xFF131A29),
    onSecondaryContainer = Color(0xFF00E5A3),
    tertiary = Color(0xFF00E5A3),
    background = Color(0xFF0A0E17),
    onBackground = Color(0xFFF8FAFC),
    surface = Color(0xFF131A29),
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = Color(0xFF1A2234),
    onSurfaceVariant = Color(0xFF94A3B8),
    surfaceContainer = Color(0xFF131A29),
    surfaceContainerHigh = Color(0xFF1A2234),
    surfaceContainerHighest = Color(0xFF222D42),
    surfaceContainerLow = Color(0xFF0E131F),
    surfaceContainerLowest = Color(0xFF0A0E17),
    outline = Color(0xFF222D42),
    outlineVariant = Color(0xFF1A2234),
)

@Composable
fun getColorScheme(
    theme: AppTheme,
): ColorScheme {
    return CrakeObsidianDark
}

fun ColorScheme.amoled(): ColorScheme {
    return this.copy(background = Color.Black, surface = Color(0xFF0A0E17))
}

@Composable
fun FlorisAppTheme(
    theme: AppTheme,
    content: @Composable () -> Unit,
) {
    val colors = CrakeObsidianDark

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        }
    }

    // Whole-app font choice: rebuild the Material typography with the
    // chosen family on every style. SYSTEM keeps the stock look.
    val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
    val appFontChoice by prefs.fonts.appFont.collectAsState()
    val context = LocalView.current.context
    val typography = remember(appFontChoice) {
        val family = dev.patrickgold.florisboard.ime.theme.CrakeFonts.familyOf(context, appFontChoice)
        if (family == null) Typography else Typography.withFontFamily(family)
    }

    MaterialTheme(
        colorScheme = colors,
        typography = typography,
        content = content,
    )
}

private fun androidx.compose.material3.Typography.withFontFamily(
    family: androidx.compose.ui.text.font.FontFamily,
): androidx.compose.material3.Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
