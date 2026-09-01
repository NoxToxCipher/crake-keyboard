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

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.latin.LearnedStateStore
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.compose.DisposableLifecycleEffect
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

    // Dynamic Window FLAG_SECURE Shield: blocks screenshots, screen recorders,
    // and the recents thumbnail ONLY while the secret notes are on screen (the
    // PIN pad or an unlocked secret page). The plain notepad and every other
    // part of the app stay freely screenshotable. The flag is cleared whenever
    // the vault is not active, and again on dispose, so it can never linger
    // onto another screen.
    val isVaultActive = showPin || secretPin != null
    DisposableEffect(isVaultActive) {
        val window = context.findActivity()?.window
        if (isVaultActive) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    // Immediate App-Switch & Screen-Off Locking: Wipes plaintext RAM and seals vault on onPause
    DisposableLifecycleEffect(
        onResume = { checkAutoLock() },
        onPause = { leaveSecret() },
    )

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
        val screenWidth = maxWidth
        val openWidth = if (screenWidth * 0.82f < 340.dp) screenWidth * 0.82f else 340.dp
        val openPx = with(density) { openWidth.toPx() }
        val closedPx = 0f
        val edgeWidth = 56.dp
        val edgePx = with(density) { edgeWidth.toPx() }
        val offsetX = remember { Animatable(closedPx) }
        // Derived so a sub-pixel drag/spring frame does NOT recompose the
        // whole wrapped home screen - these flip at most twice per open/close.
        // The visual slide is driven entirely in the draw phase (graphicsLayer
        // / drawBehind read offsetX.value there).
        val isOpen by remember { derivedStateOf { offsetX.value > openPx * 0.45f } }
        val isInteracting by remember { derivedStateOf { offsetX.value > 1f } }

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

        BackHandler(enabled = isOpen || showPin || offsetX.value > 5f) {
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
                isSecret = secretPin != null,
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

            // Right seam drag zone: allows dragging left from the note edge to close
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(48.dp)
                    .pointerInput(openPx) {
                        var velocityTracker = VelocityTracker()
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(0L, offset)
                            },
                            onDragEnd = {
                                val vx = velocityTracker.calculateVelocity().x
                                val target = when {
                                    vx < -350f -> closedPx
                                    vx > 350f -> openPx
                                    offsetX.value > openPx * 0.4f -> openPx
                                    else -> closedPx
                                }
                                animateTo(target, vx)
                            },
                            onDragCancel = {
                                val target = if (offsetX.value > openPx * 0.4f) openPx else closedPx
                                animateTo(target)
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val newX = (offsetX.value + dragAmount).coerceIn(closedPx, openPx)
                                scope.launch { offsetX.snapTo(newX) }
                            }
                        )
                    }
            )

            // Dedicated Close Note Button in top right of note
            Surface(
                onClick = { animateTo(closedPx) },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF243447).copy(alpha = 0.92f),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Note",
                        tint = Color(0xFFF7F1E3),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Close",
                        color = Color(0xFFF7F1E3),
                        fontSize = 11.5.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    )
                }
            }

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

        // Layer 2: Main Foreground Content
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
        ) {
            content()

            if (isInteracting) {
                // Unified Tap-to-Close and Drag-to-Close overlay over the entire shifted foreground menu
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            val a = (offsetX.value / openPx * 0.40f).coerceIn(0f, 0.40f)
                            if (a > 0.001f) drawRect(Color.Black, alpha = a)
                        }
                        .pointerInput(openPx) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)
                                var totalDragX = 0f
                                var isDragging = false

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull() ?: break
                                    if (!change.pressed) {
                                        if (!isDragging) {
                                            // Tap anywhere on the side menu -> slide back immediately!
                                            animateTo(closedPx)
                                        } else {
                                            // Drag release -> spring to open or closed based on velocity & position
                                            val vx = velocityTracker.calculateVelocity().x
                                            val target = when {
                                                vx < -350f -> closedPx
                                                vx > 350f -> openPx
                                                offsetX.value > openPx * 0.4f -> openPx
                                                else -> closedPx
                                            }
                                            animateTo(target, vx)
                                        }
                                        break
                                    } else {
                                        val dragX = change.positionChange().x
                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        if (!isDragging && abs(totalDragX + dragX) > 8f) {
                                            isDragging = true
                                        }
                                        if (isDragging) {
                                            change.consume()
                                            totalDragX += dragX
                                            val newX = (offsetX.value + dragX).coerceIn(closedPx, openPx)
                                            scope.launch { offsetX.snapTo(newX) }
                                        }
                                    }
                                }
                            }
                        }
                )
            } else {
                // When completely closed, provide a 56dp edge grab strip on the left to pull open
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxHeight()
                        .width(edgeWidth)
                        .pointerInput(openPx) {
                            var velocityTracker = VelocityTracker()
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    velocityTracker = VelocityTracker()
                                    velocityTracker.addPosition(0L, offset)
                                },
                                onDragEnd = {
                                    val vx = velocityTracker.calculateVelocity().x
                                    val target = when {
                                        vx > 350f -> openPx
                                        vx < -350f -> closedPx
                                        offsetX.value > openPx * 0.35f -> openPx
                                        else -> closedPx
                                    }
                                    animateTo(target, vx)
                                },
                                onDragCancel = {
                                    val target = if (offsetX.value > openPx * 0.35f) openPx else closedPx
                                    animateTo(target)
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    val newX = (offsetX.value + dragAmount).coerceIn(closedPx, openPx)
                                    scope.launch { offsetX.snapTo(newX) }
                                }
                            )
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
    isSecret: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val paper = Color(0xFFF7F1E3)
    val ink = Color(0xFF243447)
    val rule = Color(0xFFAEC8DF)
    val margin = Color(0xFFE0A5A5)
    val lineHeight = 26.sp

    val cursorBrush = remember(ink) { SolidColor(ink) }

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
                val strokeRule = 1.dp.toPx()
                val strokeMargin = 1.5.dp.toPx()
                val marginX = 42.dp.toPx()
                val endX = size.width
                val maxY = size.height - 10.dp.toPx()

                var y = topPad + lh * 0.86f
                while (y < maxY) {
                    drawLine(rule, Offset(0f, y), Offset(endX, y), strokeWidth = strokeRule)
                    y += lh
                }
                drawLine(margin, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = strokeMargin)
            }
            BasicTextField(
                value = noteText,
                onValueChange = { new ->
                    if (new.length <= NOTE_MAX_CHARS && new.count { it == '\n' } < NOTE_MAX_LINES) {
                        onChange(new)
                    }
                },
                keyboardOptions = if (isSecret) {
                    KeyboardOptions(
                        autoCorrect = false,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Default,
                    )
                } else {
                    KeyboardOptions.Default
                },
                textStyle = TextStyle(
                    color = ink,
                    fontSize = 16.sp,
                    lineHeight = lineHeight,
                    fontFamily = FontFamily.Cursive,
                ),
                cursorBrush = cursorBrush,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 50.dp, end = 14.dp, top = 14.dp, bottom = 10.dp),
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
