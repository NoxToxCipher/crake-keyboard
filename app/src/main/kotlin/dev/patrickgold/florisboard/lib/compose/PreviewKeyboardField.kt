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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.util.InputMethodUtils
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToastSync
import org.florisboard.lib.compose.stringRes
import org.florisboard.lib.compose.verticalTween

private const val AnimationDuration = 200

private val PreviewEnterTransition = EnterTransition.verticalTween(AnimationDuration)
private val PreviewExitTransition = ExitTransition.verticalTween(AnimationDuration)

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

val LocalPreviewFieldController = staticCompositionLocalOf<PreviewFieldController?> { null }

@Composable
fun rememberPreviewFieldController(): PreviewFieldController {
    return remember { PreviewFieldController() }
}

class PreviewFieldController {
    val focusRequester = FocusRequester()
    var isVisible by mutableStateOf(false)
    var text by mutableStateOf(TextFieldValue(""))
}

@Composable
fun PreviewKeyboardField(
    controller: PreviewFieldController,
    modifier: Modifier = Modifier,
    hint: String = "⚡ Test your Crake setup & type here...",
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var showQuickHubDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = controller.isVisible,
        enter = PreviewEnterTransition,
        exit = PreviewExitTransition,
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0E17))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            ) {
                SelectionContainer {
                    TextField(
                        modifier = Modifier
                            .height(56.dp)
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Back) {
                                    focusManager.clearFocus()
                                }
                                false
                            }
                            .focusRequester(controller.focusRequester),
                        value = controller.text,
                        onValueChange = { controller.text = it },
                        textStyle = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = 14.sp,
                            textDirection = TextDirection.ContentOrLtr,
                        ),
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
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
                        },
                        placeholder = {
                            Text(
                                text = hint,
                                color = TextMuted,
                                fontSize = 13.sp,
                                overflow = TextOverflow.Ellipsis,
                                maxLines = 1,
                            )
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 4.dp),
                            ) {
                                if (controller.text.text.isNotEmpty()) {
                                    IconButton(
                                        onClick = { controller.text = TextFieldValue("") },
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear",
                                            tint = TextMuted,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(
                                        onClick = { showQuickHubDialog = true },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Tune,
                                            contentDescription = "Crake Quick Hub",
                                            tint = CyberEmerald,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        },
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = true),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            cursorColor = ElectricCyan,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        }

        // CRAKE MODERN QUICK HUB MODAL
        if (showQuickHubDialog) {
            val prefs by FlorisPreferenceStore
            val scope = rememberCoroutineScope()
            val glideEnabled by prefs.glide.enabled.observeAsState()
            val hapticEnabled by prefs.inputFeedback.hapticEnabled.observeAsState()

            Dialog(onDismissRequest = { showQuickHubDialog = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(CyberEmerald.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = null,
                                        tint = CyberEmerald,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "CRAKE // STATUS",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color.White,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CyberEmerald.copy(alpha = 0.15f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "ACTIVE IME",
                                    color = CyberEmerald,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = CardBorder, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Active Keyboard Profile Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0E131F)),
                            border = BorderStroke(1.dp, CardBorder),
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Language,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "Crake Keyboard",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color.White,
                                        )
                                        Text(
                                            text = "BB10 Frets • Safe Rust Core",
                                            fontSize = 11.sp,
                                            color = TextMuted,
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Active",
                                    tint = CyberEmerald,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // Glide Typing Toggle Pill
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (glideEnabled) ElectricCyan.copy(alpha = 0.12f) else Color(0xFF0E131F)
                                ),
                                border = BorderStroke(1.dp, if (glideEnabled) ElectricCyan.copy(alpha = 0.5f) else CardBorder),
                                onClick = {
                                    scope.launch {
                                        prefs.glide.enabled.set(!glideEnabled)
                                    }
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Gesture,
                                        contentDescription = null,
                                        tint = if (glideEnabled) ElectricCyan else TextMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "Glide Typing",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        color = if (glideEnabled) Color.White else TextMuted,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = if (glideEnabled) "ON" else "OFF",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (glideEnabled) ElectricCyan else TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }

                            // Haptics Toggle Pill
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (hapticEnabled) CyberEmerald.copy(alpha = 0.12f) else Color(0xFF0E131F)
                                ),
                                border = BorderStroke(1.dp, if (hapticEnabled) CyberEmerald.copy(alpha = 0.5f) else CardBorder),
                                onClick = {
                                    scope.launch {
                                        prefs.inputFeedback.hapticEnabled.set(!hapticEnabled)
                                    }
                                },
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Vibration,
                                        contentDescription = null,
                                        tint = if (hapticEnabled) CyberEmerald else TextMuted,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = "Haptics",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                        color = if (hapticEnabled) Color.White else TextMuted,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        modifier = Modifier.fillMaxWidth(),
                                        text = if (hapticEnabled) "ON" else "OFF",
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = if (hapticEnabled) CyberEmerald else TextMuted,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Switch System Keyboard Button (Optional)
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, CardBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextMuted),
                            onClick = {
                                showQuickHubDialog = false
                                InputMethodUtils.showImePicker(context)
                            },
                        ) {
                            Text(
                                text = "Open Android System Switcher",
                                fontSize = 12.sp,
                                color = TextMuted,
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Close Button
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan),
                            onClick = { showQuickHubDialog = false },
                        ) {
                            Text(
                                text = "Done",
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
