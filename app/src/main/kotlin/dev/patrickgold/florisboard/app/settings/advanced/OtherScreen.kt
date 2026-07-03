/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.app.settings.advanced

import android.app.LocaleManager
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Adb
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.core.net.toUri
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.AppTheme
import dev.patrickgold.florisboard.app.FlorisPreferenceModel
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.core.DisplayLanguageNamesIn
import dev.patrickgold.florisboard.lib.FlorisLocale
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import dev.patrickgold.jetpref.datastore.ui.ColorPickerPreference
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.PreferenceUiScope
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import dev.patrickgold.jetpref.datastore.ui.isMaterialYou
import dev.patrickgold.jetpref.datastore.ui.listPrefEntries
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.android.systemServiceOrNull
import org.florisboard.lib.color.ColorMappings
import org.florisboard.lib.compose.stringRes


@Composable
fun OtherScreen() = FlorisScreen {
    title = stringRes(R.string.settings__other__title)
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        ListPreference(
            prefs.other.settingsTheme,
            icon = Icons.Default.Palette,
            title = stringRes(R.string.pref__other__settings_theme__label),
            entries = enumDisplayEntriesOf(AppTheme::class),
        )
        ColorPickerPreference(
            pref = prefs.other.accentColor,
            title = stringRes(R.string.pref__other__settings_accent_color__label),
            defaultValueLabel = stringRes(R.string.action__default),
            icon = Icons.Default.FormatColorFill,
            defaultColors = ColorMappings.colors,
            showAlphaSlider = false,
            enableAdvancedLayout = true,
            colorOverride = {
                if (it.isMaterialYou(context)) {
                    Color.Unspecified
                } else {
                    it
                }
            }
        )
        LanguagePicker()
        SwitchPreference(
            prefs.other.showAppIcon,
            icon = Icons.Default.Preview,
            title = stringRes(R.string.pref__other__show_app_icon__label),
            summary = when {
                AndroidVersion.ATLEAST_API29_Q -> stringRes(R.string.pref__other__show_app_icon__summary_atleast_q)
                else -> null
            },
            enabledIf = { AndroidVersion.ATMOST_API28_P },
        )
        Preference(
            icon = ImageVector.vectorResource(R.drawable.ic_keyboard_keys),
            title = stringRes(R.string.physical_keyboard__title),
            onClick = { navController.navigate(Routes.Settings.PhysicalKeyboard) },
        )
        Preference(
            icon = Icons.Default.Adb,
            title = stringRes(R.string.devtools__title),
            onClick = { navController.navigate(Routes.Devtools.Home) },
        )

        PreferenceGroup(title = stringRes(R.string.backup_and_restore__title)) {
            Preference(
                onClick = { navController.navigate(Routes.Settings.Backup) },
                icon = Icons.Default.Archive,
                title = stringRes(R.string.backup_and_restore__back_up__title),
                summary = stringRes(R.string.backup_and_restore__back_up__summary),
            )
            Preference(
                onClick = { navController.navigate(Routes.Settings.Restore) },
                icon = Icons.Default.SettingsBackupRestore,
                title = stringRes(R.string.backup_and_restore__restore__title),
                summary = stringRes(R.string.backup_and_restore__restore__summary),
            )
        }
    }
}


@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.LanguagePicker() {
    if (AndroidVersion.ATLEAST_API33_T) {
        SystemLanguagePicker()
    } else {
        FlorisLanguagePicker()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.SystemLanguagePicker() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localeManager = remember { context.systemServiceOrNull(LocaleManager::class) } ?: return
    val systemLocaleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        scope.launch {
            if (localeManager.applicationLocales.isEmpty) {
                prefs.other.settingsLanguage.set("auto")
            } else {
                prefs.other.settingsLanguage.set(localeManager.applicationLocales.toLanguageTags())
            }
        }
    }
    val localeSummary = remember {
        val locales = localeManager.applicationLocales
        if (locales.isEmpty) {
            context.stringRes(R.string.settings__system_default)
        } else {
            val activeLocale = locales.get(0)
            val displayLanguageNamesIn = prefs.localization.displayLanguageNamesIn.get()
            when (displayLanguageNamesIn) {
                DisplayLanguageNamesIn.SYSTEM_LOCALE -> activeLocale?.displayName ?: ""
                DisplayLanguageNamesIn.NATIVE_LOCALE -> activeLocale?.getDisplayName(activeLocale) ?: ""
            }
        }
    }
    Preference(
        icon = Icons.Default.Language,
        title = stringRes(R.string.pref__other__settings_language__label),
        summary = localeSummary,
        onClick = {
            val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
            systemLocaleLauncher.launch(intent)
        },
    )
}

@Composable
private fun PreferenceUiScope<FlorisPreferenceModel>.FlorisLanguagePicker() {
    ListPreference(
        prefs.other.settingsLanguage,
        icon = Icons.Default.Language,
        title = stringRes(R.string.pref__other__settings_language__label),
        entries = listPrefEntries {
            listOf("auto").plus(BuildConfig.LOCALES)
                .forEach { languageTag ->
                    if (languageTag == "auto") {
                        entry(
                            key = "auto",
                            label = stringRes(R.string.settings__system_default),
                        )
                    } else {
                        val displayLanguageNamesIn by prefs.localization.displayLanguageNamesIn.collectAsState()
                        val locale = FlorisLocale.fromTag(languageTag)
                        entry(locale.languageTag(), when (displayLanguageNamesIn) {
                            DisplayLanguageNamesIn.SYSTEM_LOCALE -> locale.displayName()
                            DisplayLanguageNamesIn.NATIVE_LOCALE -> locale.displayName(locale)
                        })
                    }
                }
        },
    )
}
