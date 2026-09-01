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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.latin.LearnedStateStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.libnative.FlorisNative

/**
 * The Deck-style note peek: the whole homepage can be pulled to the right,
 * revealing a single lined-paper notes page tucked behind its left edge.
 *
 * That visible page is an ordinary, unencrypted scratchpad - deliberately
 * mundane, so it reads as "just a notepad" to anyone who finds it. Behind it
 * is the part that is never mentioned anywhere in the app: a long-press on
 * the red margin line summons the Crake PIN. Whatever PIN you enter opens the
 * encrypted page sealed under it - your page, or a fresh blank one - and the
 * screen never says whether a PIN is "right". See note_vault (Rust) for the
 * deniable model and its honest limits.
 */
@Composable
fun CrakeNotePeek(content: @Composable () -> Unit) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val plainNote by prefs.internal.crakeNote.collectAsState()

    // Blends with the app's other crake_*.enc caches; Keystore-sealed on top
    // of the PIN encryption, so an imaged file is doubly protected.
    val vaultStore = remember { LearnedStateStore(context.filesDir, "crake_pages.crkp") }
    var vaultBytes by remember { mutableStateOf(vaultStore.load() ?: ByteArray(0)) }

    var secretPin by remember { mutableStateOf<String?>(null) }
    var secretContent by remember { mutableStateOf("") }
    var showPin by remember { mutableStateOf(false) }

    fun leaveSecret() {
        secretPin = null
        secretContent = ""
        showPin = false
    }

    // Debounced save of the secret page so the PIN key derivation does not
    // run on every keystroke.
    LaunchedEffect(secretContent, secretPin) {
        val pin = secretPin ?: return@LaunchedEffect
        kotlinx.coroutines.delay(600)
        val updated = FlorisNative.noteVaultSave(vaultBytes, pin, secretContent)
        vaultBytes = updated
        vaultStore.save(updated)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val openWidth = if (maxWidth * 0.82f < 340.dp) maxWidth * 0.82f else 340.dp
        val openPx = with(density) { openWidth.toPx() }
        // Fully closed = 0: no sliver, no shadow, nothing at rest. The page
        // is meant to be sought out, not stumbled on. Opening is a deliberate
        // pull-right that must START at the very left edge, so a stray
        // horizontal swipe in the body never reveals it and nothing on
        // screen hints it exists.
        val closedPx = 0f
        val edgePx = with(density) { 24.dp.toPx() }
        val offsetX = remember { Animatable(closedPx) }
        var lastDragDelta by remember { mutableStateOf(0f) }
        var openArmed by remember { mutableStateOf(false) }
        val isOpen = offsetX.value > openPx * 0.6f

        fun animateTo(target: Float) {
            scope.launch {
                offsetX.animateTo(target, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
            }
            if (target <= closedPx + 1f) leaveSecret()
        }

        fun settle() {
            val target = when {
                lastDragDelta > 6f -> openPx
                lastDragDelta < -6f -> closedPx
                offsetX.value > openPx / 2f -> openPx
                else -> closedPx
            }
            animateTo(target)
        }

        BackHandler(enabled = isOpen || showPin) {
            if (showPin) showPin = false else animateTo(closedPx)
        }

        // The paper is composed ONLY while the drawer is off its resting
        // edge. At rest it does not exist, so it cannot bleed through the
        // homepage's transparent gaps - nothing hints the page is there.
        if (offsetX.value > 0.5f) {
            Box(modifier = Modifier.align(Alignment.CenterStart).width(openWidth).fillMaxHeight()) {
                LinedPaperNote(
                    noteText = if (secretPin == null) plainNote else secretContent,
                    onChange = { new ->
                        if (secretPin == null) {
                            scope.launch { prefs.internal.crakeNote.set(new) }
                        } else {
                            secretContent = new
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                )
                // The unmarked door: a long-press on the red margin strip
                // (left of the writing area, so typing never triggers it)
                // opens the PIN. Nothing labels it.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(48.dp)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = { showPin = true })
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationX = offsetX.value }
                .pointerInput(openPx) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            lastDragDelta = 0f
                            // Only a pull that begins at the left edge (or any
                            // drag while already open, to close) moves the
                            // page. A swipe in the middle does nothing, so the
                            // page is never revealed by accident.
                            openArmed = isOpen || offset.x < edgePx
                        },
                        onDragEnd = { if (openArmed) settle() },
                        onDragCancel = { if (openArmed) settle() },
                        onHorizontalDrag = { change, delta ->
                            if (!openArmed) return@detectHorizontalDragGestures
                            lastDragDelta = delta
                            change.consume()
                            scope.launch {
                                offsetX.snapTo((offsetX.value + delta).coerceIn(closedPx, openPx))
                            }
                        },
                    )
                }
                .pointerInput(isOpen) {
                    if (isOpen) detectTapGestures { animateTo(closedPx) }
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

        if (showPin) {
            CrakePinScreen(
                onComplete = { pin ->
                    secretPin = pin
                    secretContent = FlorisNative.noteVaultOpen(vaultBytes, pin)
                    showPin = false
                },
                onCancel = { showPin = false },
            )
        }
    }
}

private const val NOTE_MAX_LINES = 24
private const val NOTE_MAX_CHARS = 1600

/**
 * One page of lined paper: cream stock, blue rules matched to the text line
 * height (both in sp so font scaling keeps them aligned), a red margin line,
 * and nothing else. The page cannot grow - it is deliberately one page, like
 * the paper it is drawn as.
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
