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

package dev.patrickgold.florisboard.app.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch

/**
 * The Deck-style note peek: the whole homepage can be pulled to the right,
 * revealing a single lined-paper notes page tucked behind its left edge. A
 * thin paper sliver stays visible at rest as the affordance. The note is
 * one page, stored locally in prefs, and never leaves the device.
 */
@Composable
fun CrakeNotePeek(content: @Composable () -> Unit) {
    val prefs by FlorisPreferenceStore
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val noteText by prefs.internal.crakeNote.collectAsState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val openWidth = if (maxWidth * 0.82f < 340.dp) maxWidth * 0.82f else 340.dp
        val openPx = with(density) { openWidth.toPx() }
        val peekPx = with(density) { 10.dp.toPx() }
        val offsetX = remember { Animatable(peekPx) }
        var lastDragDelta by remember { mutableStateOf(0f) }
        val isOpen = offsetX.value > openPx * 0.6f

        fun animateTo(target: Float) {
            scope.launch {
                offsetX.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            }
        }

        fun settle() {
            val target = when {
                lastDragDelta > 6f -> openPx
                lastDragDelta < -6f -> peekPx
                offsetX.value > (peekPx + openPx) / 2f -> openPx
                else -> peekPx
            }
            animateTo(target)
        }

        BackHandler(enabled = isOpen) { animateTo(peekPx) }

        LinedPaperNote(
            noteText = noteText,
            onChange = { scope.launch { prefs.internal.crakeNote.set(it) } },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(openWidth)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .pointerInput(isOpen) {
                    if (!isOpen) detectTapGestures { animateTo(openPx) }
                },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(openPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { lastDragDelta = 0f },
                        onDragEnd = { settle() },
                        onDragCancel = { settle() },
                        onHorizontalDrag = { change, delta ->
                            lastDragDelta = delta
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(peekPx, openPx))
                            }
                        },
                    )
                }
                .pointerInput(isOpen) {
                    // The pushed-aside page closes on tap, standard drawer manners.
                    if (isOpen) detectTapGestures { animateTo(peekPx) }
                },
        ) {
            content()
            if (isOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            }
        }
    }
}

private const val NOTE_MAX_LINES = 24
private const val NOTE_MAX_CHARS = 1600

/**
 * One page of lined paper: cream stock, blue rules matched to the text line
 * height (both in sp so font scaling keeps them aligned), a red margin
 * line, and nothing else. The page cannot grow - it is deliberately one
 * page, like the paper it is drawn as.
 */
@Composable
private fun LinedPaperNote(
    noteText: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paper = Color(0xFFF7F1E3)
    val ink = Color(0xFF243447)
    val rule = Color(0xFFAEC8DF)
    val margin = Color(0xFFE0A5A5)
    val lineHeight = 26.sp

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
        color = paper,
        shadowElevation = 10.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lh = lineHeight.toPx()
                val topPad = 14.dp.toPx()
                // A rule under each text line; 0.86 sits it near the baseline.
                var y = topPad + lh * 0.86f
                while (y < size.height - 10.dp.toPx()) {
                    drawLine(rule, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    y += lh
                }
                val marginX = 42.dp.toPx()
                drawLine(margin, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = 1.5.dp.toPx())
            }
            BasicTextField(
                value = noteText,
                onValueChange = { new ->
                    if (new.length <= NOTE_MAX_CHARS && new.count { it == '\n' } < NOTE_MAX_LINES) {
                        onChange(new)
                    }
                },
                textStyle = TextStyle(
                    color = ink,
                    fontSize = 16.sp,
                    lineHeight = lineHeight,
                    fontFamily = FontFamily.Cursive,
                ),
                cursorBrush = SolidColor(ink),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 50.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            )
        }
    }
}
