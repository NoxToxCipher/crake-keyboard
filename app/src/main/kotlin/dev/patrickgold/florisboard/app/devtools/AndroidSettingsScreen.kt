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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.material.ui.JetPrefAlertDialog
import org.florisboard.lib.android.AndroidSettings
import org.florisboard.lib.compose.stringRes

@Composable
fun AndroidSettingsScreen(name: String?) = FlorisScreen {
    title = when (name) {
        AndroidSettings.Global.groupId -> "Global Settings"
        AndroidSettings.Secure.groupId -> "Secure Settings"
        AndroidSettings.System.groupId -> "System Settings"
        else -> "Android Settings"
    }
    scrollable = false

    val context = LocalContext.current

    val settingsGroup = when (name) {
        AndroidSettings.Global.groupId -> AndroidSettings.Global
        AndroidSettings.Secure.groupId -> AndroidSettings.Secure
        AndroidSettings.System.groupId -> AndroidSettings.System
        else -> AndroidSettings.Global
    }
    val nameValueTable = remember(name) { settingsGroup.getAllKeys().toList() }
    var dialogKey by remember { mutableStateOf<String?>(null) }

    content {
        LazyColumn {
            items(nameValueTable) { (fieldName, key) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { dialogKey = key }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fieldName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = key,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                        )
                    }
                }
                HorizontalDivider(
                    color = Color(0xFF1E293B).copy(alpha = 0.5f),
                    thickness = 0.5.dp,
                )
            }
        }

        if (dialogKey != null) {
            JetPrefAlertDialog(
                title = dialogKey!!,
                onDismiss = { dialogKey = null },
            ) {
                SelectionContainer {
                    Text(
                        text = remember {
                            (settingsGroup.getString(context, dialogKey!!) ?: "(null)").ifBlank { "(blank)" }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = Color(0xFF00E5A3),
                    )
                }
            }
        }
    }
}
