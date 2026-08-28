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

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.devtools.Devtools
import kotlinx.coroutines.launch
import org.florisboard.lib.android.showShortToast
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.compose.florisScrollbar

private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)

@Composable
fun ExportDebugLogScreen() = FlorisScreen {
    title = "Debug Engine Log"
    scrollable = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardManager by context.clipboardManager()

    var debugLog by remember { mutableStateOf<List<String>?>(null) }
    var formattedDebugLog by remember { mutableStateOf<List<String>?>(null) }

    LaunchedEffect(Unit) {
        debugLog = Devtools.generateDebugLog(context, prefs, includeLogcat = true).lines()
        formattedDebugLog = Devtools.generateDebugLogForGithub(context, prefs, includeLogcat = true).lines()
    }

    bottomBar {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
        ) {
            Button(
                onClick = {
                    debugLog?.let {
                        clipboardManager.addNewPlaintext(it.joinToString("\n"))
                        scope.launch { context.showShortToast("Raw debug log copied to clipboard") }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = Color(0xFF0F172A),
                ),
                enabled = debugLog != null,
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Raw Log", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
            Button(
                onClick = {
                    formattedDebugLog?.let {
                        clipboardManager.addNewPlaintext(it.joinToString("\n"))
                        scope.launch { context.showShortToast("Markdown debug log copied to clipboard") }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = CyberEmerald,
                    contentColor = Color(0xFF0F172A),
                ),
                enabled = debugLog != null,
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = null)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Copy Markdown", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        }
    }

    content {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            val lazyListState = rememberLazyListState()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF080C14))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .florisScrollbar(lazyListState, isVertical = true)
                        .florisHorizontalScroll(),
                    state = lazyListState,
                ) {
                    val log = debugLog
                    if (log == null) {
                        item {
                            Text(
                                text = "Compiling diagnostic engine logs…",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    } else {
                        items(log) { logLine ->
                            val textColor = when {
                                logLine.contains("ERROR") || logLine.contains("FATAL") || logLine.contains("Exception") -> Color(0xFFEF4444)
                                logLine.contains("WARN") -> Color(0xFFF59E0B)
                                logLine.contains("INFO") -> ElectricCyan
                                logLine.startsWith("###") || logLine.startsWith("##") -> CyberEmerald
                                else -> Color(0xFFCBD5E1)
                            }
                            Text(
                                text = logLine,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = textColor,
                                softWrap = false,
                                lineHeight = 14.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
