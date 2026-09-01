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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
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
import kotlin.math.abs

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
    val hintCount by prefs.internal.crakeNoteHintCount.collectAsState()
    // The appearance index (0,1,2) for the current open, or null when the
    // hint should not show this time. Local per-session dismiss on top.
    var hintForThisOpen by remember { mutableStateOf<Int?>(null) }
    var hintDismissed by remember { mutableStateOf(false) }

    // Blends with the app's other crake_*.enc caches; Keystore-sealed on top
    // of the PIN encryption, so an imaged file is doubly protected.
    val vaultStore = remember { LearnedStateStore(context.filesDir, "crake_pages.crkp") }
    var vaultBytes by remember { mutableStateOf(vaultStore.load() ?: ByteArray(0)) }

    val SECRET_AUTO_LOCK_TIMEOUT_MS = 60_000L // 60 seconds auto-locks secret notes

    var secretPin by remember { mutableStateOf<String?>(null) }
    var secretContent by remember { mutableStateOf("") }
    var lastSecretActivityTime by remember { mutableStateOf(0L) }
    var showPin by remember { mutableStateOf(false) }

    fun leaveSecret() {
        secretPin = null
        secretContent = ""
        showPin = false
        hintForThisOpen = null
        hintDismissed = false
        lastSecretActivityTime = 0L
    }

    fun checkAutoLock() {
        if (secretPin != null) {
            val now = System.currentTimeMillis()
            if (now - lastSecretActivityTime > SECRET_AUTO_LOCK_TIMEOUT_MS) {
                leaveSecret()
            }
        }
    }

    // Auto-lock countdown loop while secret notes are unlocked
    LaunchedEffect(secretPin, lastSecretActivityTime) {
        if (secretPin != null) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (System.currentTimeMillis() - lastSecretActivityTime > SECRET_AUTO_LOCK_TIMEOUT_MS) {
                    leaveSecret()
                    break
                }
            }
        }
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
        val closedPx = 0f
        val edgeWidth = 56.dp
        val edgePx = with(density) { edgeWidth.toPx() }
        val offsetX = remember { Animatable(closedPx) }
        val isOpen = offsetX.value > openPx * 0.45f

        fun animateTo(target: Float, initialVelocity: Float = 0f) {
            scope.launch {
                offsetX.animateTo(
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = 0.88f,
                        stiffness = Spring.StiffnessMediumLow,
                        visibilityThreshold = 0.5f,
                    ),
                    initialVelocity = initialVelocity,
                )
                if (target <= closedPx + 1f) {
                    checkAutoLock()
                }
            }
        }

        BackHandler(enabled = isOpen || showPin) {
            if (showPin) showPin = false else animateTo(closedPx)
        }

        // Check auto-lock on open. Claim one of the three hint appearances
        // the moment the note opens to the plain page.
        LaunchedEffect(isOpen) {
            if (isOpen) {
                checkAutoLock()
                if (secretPin != null) {
                    lastSecretActivityTime = System.currentTimeMillis()
                }
            }
            if (isOpen && secretPin == null && hintForThisOpen == null && hintCount < 3) {
                hintForThisOpen = hintCount
                prefs.internal.crakeNoteHintCount.set(hintCount + 1)
            }
        }

        // Layer 1: The Lined Paper Note (Persistent behind content with subtle parallax)
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(openWidth)
                .fillMaxHeight()
                .graphicsLayer {
                    val progress = (offsetX.value / openPx).coerceIn(0f, 1f)
                    translationX = (progress - 1f) * 60f
                    alpha = (progress * 1.5f).coerceIn(0f, 1f)
                }
        ) {
            LinedPaperNote(
                noteText = if (secretPin == null) plainNote else secretContent,
                onChange = { new ->
                    if (secretPin == null) {
                        scope.launch { prefs.internal.crakeNote.set(new) }
                    } else {
                        secretContent = new
                        lastSecretActivityTime = System.currentTimeMillis()
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 8.dp),
            )

            // The unmarked door on the red margin strip (left of the
            // writing area, so typing never triggers it). Two ways in,
            // both requiring intent: a long hold, or a downward swipe
            // running most of the line's length. Nothing labels it.
            var stripHeightPx by remember { mutableStateOf(1f) }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(48.dp)
                    .fillMaxHeight()
                    .onSizeChanged { stripHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { showPin = true })
                    }
                    .pointerInput(Unit) {
                        var travelled = 0f
                        detectVerticalDragGestures(
                            onDragStart = { travelled = 0f },
                            onVerticalDrag = { change, dy ->
                                travelled += dy
                                change.consume()
                            },
                            onDragEnd = {
                                if (travelled >= stripHeightPx * 0.6f) {
                                    showPin = true
                                }
                            },
                        )
                    },
            )

            // Shown to whoever found the note on the first three opens,
            // counting down so they are warned before it stops. Then gone.
            val idx = hintForThisOpen
            if (secretPin == null && idx != null && !hintDismissed) {
                val countdown = when (idx) {
                    0 -> "This hint will appear twice more, then never again. Please remember."
                    1 -> "This hint will appear once more, and then never again. Please remember."
                    else -> "This is the last time this hint will appear. Please remember."
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 50.dp, end = 16.dp, bottom = 18.dp),
                ) {
                    Surface(
                        color = Color(0xFF243447),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures { hintDismissed = true }
                        },
                    ) {
                        androidx.compose.material3.Text(
                            text = "Private page: hold the red line, or swipe down it.\n$countdown\nTap to dismiss.",
                            color = Color(0xFFF7F1E3),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                        )
                    }
                }
            }
        }

        // Layer 2: Main Foreground Content + Persistent Unified Touch Handler
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    val progress = (offsetX.value / openPx).coerceIn(0f, 1f)
                    shadowElevation = progress * 16f
                    clip = progress > 0.01f
                    shape = RoundedCornerShape(
                        topStart = (16f * progress).dp,
                        bottomStart = (16f * progress).dp,
                    )
                }
                .pointerInput(openPx) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val isOpened = offsetX.value > openPx * 0.4f
                        val canDrag = isOpened || startPos.x <= edgePx

                        if (!canDrag) return@awaitEachGesture

                        val velocityTracker = VelocityTracker()
                        velocityTracker.addPosition(down.uptimeMillis, down.position)
                        var isDragging = isOpened

                        while (true) {
                            val event = awaitPointerEvent()
                            val dragChange = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!dragChange.pressed) {
                                val vx = velocityTracker.calculateVelocity().x
                                val target = when {
                                    vx > 500f -> openPx
                                    vx < -500f -> closedPx
                                    offsetX.value > openPx * 0.45f -> openPx
                                    else -> closedPx
                                }
                                animateTo(target, vx)
                                break
                            }

                            velocityTracker.addPosition(dragChange.uptimeMillis, dragChange.position)
                            val totalDeltaX = dragChange.position.x - startPos.x
                            val totalDeltaY = dragChange.position.y - startPos.y

                            if (!isDragging) {
                                if (abs(totalDeltaX) > touchSlopPx && abs(totalDeltaX) > abs(totalDeltaY)) {
                                    isDragging = true
                                    dragChange.consume()
                                }
                            }

                            if (isDragging) {
                                dragChange.consume()
                                val newX = (offsetX.value + dragChange.positionChange().x).coerceIn(closedPx, openPx)
                                scope.launch { offsetX.snapTo(newX) }
                            }
                        }
                    }
                }
        ) {
            content()

            // Dismiss Scrim Overlay when open
            if (offsetX.value > 1f) {
                val scrimAlpha = (offsetX.value / openPx * 0.40f).coerceIn(0f, 0.40f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = scrimAlpha))
                        .pointerInput(Unit) {
                            detectTapGestures {
                                animateTo(closedPx)
                            }
                        }
                )
            }
        }

        if (showPin) {
            CrakePinScreen(
                onComplete = { pin ->
                    secretPin = pin
                    secretContent = FlorisNative.noteVaultOpen(vaultBytes, pin)
                    lastSecretActivityTime = System.currentTimeMillis()
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
