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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.R
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.layout.fillMaxSize

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.mutableIntStateOf
import android.content.Context
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import dev.patrickgold.florisboard.FlorisImeService
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.glideTypingManager
import dev.patrickgold.florisboard.ime.editor.OperationScope
import dev.patrickgold.florisboard.ime.editor.OperationUnit
import dev.patrickgold.florisboard.ime.input.InputEventDispatcher
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.ComputingEvaluator
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.keyboard.SpaceBarMode
import dev.patrickgold.florisboard.ime.keyboard.computeLabel
import dev.patrickgold.florisboard.ime.popup.ExceptionsForKeyCodes
import dev.patrickgold.florisboard.ime.popup.PopupUiController
import dev.patrickgold.florisboard.ime.popup.rememberPopupUiController
import dev.patrickgold.florisboard.ime.text.gestures.GlideTypingGesture
import dev.patrickgold.florisboard.ime.text.gestures.SwipeAction
import dev.patrickgold.florisboard.ime.text.gestures.SwipeGesture
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import dev.patrickgold.florisboard.ime.text.key.KeyType
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.themeManager
import dev.patrickgold.florisboard.lib.FlorisRect
import dev.patrickgold.florisboard.lib.Pointer
import dev.patrickgold.florisboard.lib.PointerMap
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.toIntOffset
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.isActive
import org.florisboard.lib.android.isOrientationLandscape
import org.florisboard.lib.compose.DisposableLifecycleEffect
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggText
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery
import kotlin.math.abs
import kotlin.math.sqrt

@SuppressLint("UnusedBoxWithConstraintsScope")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TextKeyboardLayout(
    modifier: Modifier = Modifier,
    evaluator: ComputingEvaluator,
): Unit = with(LocalDensity.current) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val glideTypingManager by context.glideTypingManager()
    val editorInstance by context.editorInstance()

    val themeManager by context.themeManager()
    val activeThemeInfo by themeManager.activeThemeInfo.collectAsState()
    val activeThemeCompId = activeThemeInfo.name.componentId
    val isBorderlessTheme = "borderless" in activeThemeCompId
    val chameleonEnabled by prefs.theme.chameleonAppAccentMatcher.collectAsState()
    val packageName = editorInstance.activeInfo.packageName
    val themeAccentColor = remember(activeThemeCompId, chameleonEnabled, packageName) {
        val pkg = (packageName ?: "").lowercase()
        if (chameleonEnabled && pkg.isNotBlank()) {
            when {
                pkg.contains("whatsapp") || pkg.contains("signal") || pkg.contains("wechat") -> Color(0xFF00E5A3)
                pkg.contains("telegram") || pkg.contains("twitter") || pkg.contains("bluesky") -> Color(0xFF00D2FF)
                pkg.contains("discord") || pkg.contains("twitch") -> Color(0xFFA78BFA)
                pkg.contains("reddit") || pkg.contains("youtube") -> Color(0xFFFF4500)
                pkg.contains("slack") || pkg.contains("github") || pkg.contains("obsidian") -> Color(0xFFF59E0B)
                pkg.contains("spotify") -> Color(0xFF1DB954)
                else -> Color(0xFF00D2FF)
            }
        } else {
            when {
                "purple" in activeThemeCompId -> Color(0xFFA855F7)
                "crimson" in activeThemeCompId -> Color(0xFFEF4444)
                "sakura" in activeThemeCompId -> Color(0xFFEC4899)
                "emerald" in activeThemeCompId -> Color(0xFF00E5A3)
                "amber" in activeThemeCompId -> Color(0xFFF59E0B)
                "ghost" in activeThemeCompId -> Color(0xFFF8FAFC)
                else -> Color(0xFF00D2FF)
            }
        }
    }

    var powerSurgeTrigger by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val powerSurgeAnim = remember { Animatable(0f) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: Intent?) {
                if (intent?.action == Intent.ACTION_POWER_CONNECTED) {
                    powerSurgeTrigger++
                }
            }
        }
        val filter = IntentFilter(Intent.ACTION_POWER_CONNECTED)
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
        } catch (_: Throwable) {
            try { context.registerReceiver(receiver, filter) } catch (_: Throwable) {}
        }
        onDispose {
            try { context.unregisterReceiver(receiver) } catch (_: Throwable) {}
        }
    }

    LaunchedEffect(powerSurgeTrigger) {
        if (powerSurgeTrigger > 0) {
            powerSurgeAnim.snapTo(0f)
            powerSurgeAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1100, easing = LinearEasing),
            )
        }
    }

    val keyboard = evaluator.keyboard as TextKeyboard
    val glideEnabledInternal by prefs.glide.enabled.collectAsState()
    val glideEnabled = glideEnabledInternal && evaluator.editorInfo.isRichInputEditor &&
        evaluator.state.keyVariation != KeyVariation.PASSWORD
    val glideShowTrail by prefs.glide.showTrail.collectAsState()
    val glideTrailStyle = rememberSnyggThemeQuery(FlorisImeUi.GlideTrail.elementName)
    val glideTrailColor = glideTrailStyle.foreground(default = Color.Green)

    val controller = remember { TextKeyboardLayoutController(context) }.also {
        it.keyboard = keyboard
        if (glideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            val keys = keyboard.keys().asSequence().toList()
            glideTypingManager.setLayout(keys)
        }
    }
    val touchEventChannel = remember { Channel<MotionEvent>(64) }

    fun resetAllKeys() {
        try {
            val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
            controller.onTouchEventInternal(event)
            controller.popupUiController.hide()
            event.recycle()
        } catch (_: Throwable) {
            // Ignore
        }
    }

    DisposableEffect(Unit) {
        controller.glideTypingDetector.registerListener(controller)
        controller.glideTypingDetector.registerListener(glideTypingManager)
        onDispose {
            controller.glideTypingDetector.unregisterListener(controller)
            controller.glideTypingDetector.unregisterListener(glideTypingManager)
            resetAllKeys()
        }
    }

    DisposableLifecycleEffect(
        onResume = { /* Do nothing */ },
        onPause = { resetAllKeys() },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight())
            .onGloballyPositioned { coords ->
                controller.size = coords.size.toSize()
            }
            .pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN,
                    MotionEvent.ACTION_POINTER_DOWN,
                    MotionEvent.ACTION_MOVE,
                    MotionEvent.ACTION_POINTER_UP,
                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL,
                        -> {
                        val clonedEvent = MotionEvent.obtain(event)
                        touchEventChannel
                            .trySend(clonedEvent)
                            .onFailure {
                                // Make sure to prevent MotionEvent memory leakage
                                // in case the input channel is full
                                clonedEvent.recycle()
                            }
                        return@pointerInteropFilter true
                    }
                }
                return@pointerInteropFilter false
            }
            .drawWithContent {
                drawContent()
                if (glideEnabled && glideShowTrail) {
                    val targetDist = 3.0f
                    val radius = 20.0f

                    val radiusReductionFactor = 0.99f
                    if (controller.fadingGlideRadius > 0) {
                        controller.drawGlideTrail(
                            this,
                            controller.fadingGlide,
                            targetDist,
                            controller.fadingGlideRadius,
                            radiusReductionFactor,
                            glideTrailColor,
                        )
                    }
                    if (controller.isGliding && controller.glideDataForDrawing.isNotEmpty()) {
                        controller.drawGlideTrail(
                            this, controller.glideDataForDrawing, targetDist, radius,
                            radiusReductionFactor, glideTrailColor,
                        )
                    }
                }
            },
    ) {
        // FIXME (when rewriting TextKeyboardLayout): constrains.maxWidth is not stable!
        val keyboardWidth = constraints.maxWidth.toFloat()
        val keyboardHeight = constraints.maxHeight.toFloat()
        val keyboardRowBaseHeight = FlorisImeSizing.keyboardRowBaseHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()
        val keyMarginH by remember { derivedStateOf { windowSpec.keyMarginH.toPx() } }
        val keyMarginV by remember { derivedStateOf { windowSpec.keyMarginV.toPx() } }

        val desiredKey = remember(
            keyboard, keyboardWidth, keyboardHeight, keyMarginH, keyMarginV,
            keyboardRowBaseHeight, evaluator
        ) {
            TextKey(data = TextKeyData.UNSPECIFIED).also { desiredKey ->
                desiredKey.touchBounds.apply {
                    width = keyboardWidth / 10f
                    height = when (keyboard.mode) {
                        KeyboardMode.CHARACTERS,
                        KeyboardMode.NUMERIC_ADVANCED,
                        KeyboardMode.SYMBOLS,
                        KeyboardMode.SYMBOLS2 -> {
                            (keyboardHeight / keyboard.rowCount)
                                .coerceAtMost(keyboardRowBaseHeight.toPx() * 1.12f)
                        }
                        else -> keyboardRowBaseHeight.toPx()
                    }
                }
                desiredKey.visibleBounds.applyFrom(desiredKey.touchBounds).deflateBy(keyMarginH, keyMarginV)
                keyboard.layout(keyboardWidth, keyboardHeight, desiredKey, true)
            }
        }

        val desiredKeyHack = rememberUpdatedState(desiredKey) // TODO quick'n'dirty hack
        val popupUiController = rememberPopupUiController(
            key1 = keyboard,
            key2 = Unit, // TODO quick'n'dirty hack
            boundsProvider = { key ->
                val keyPopupWidth: Float
                val keyPopupHeight: Float
                when {
                    configuration.isOrientationLandscape() -> {
                        keyPopupWidth = desiredKeyHack.value.visibleBounds.width * 1.0f
                        keyPopupHeight = desiredKeyHack.value.visibleBounds.height * 3.0f
                    }
                    else -> {
                        keyPopupWidth = desiredKeyHack.value.visibleBounds.width * 1.1f
                        keyPopupHeight = desiredKeyHack.value.visibleBounds.height * 2.5f
                    }
                }
                val keyPopupDiffX = (key.visibleBounds.width - keyPopupWidth) / 2.0f
                FlorisRect.new().apply {
                    left = key.visibleBounds.left + keyPopupDiffX
                    top = key.visibleBounds.bottom - keyPopupHeight
                    right = left + keyPopupWidth
                    bottom = top + keyPopupHeight
                }
            },
            isSuitableForBasicPopup = { key ->
                if (key is TextKey) {
                    val keyCode = key.computedData.code
                    val keyType = key.computedData.type
                    val numeric = keyboard.mode == KeyboardMode.NUMERIC ||
                        keyboard.mode == KeyboardMode.PHONE || keyboard.mode == KeyboardMode.PHONE2 ||
                        keyboard.mode == KeyboardMode.NUMERIC_ADVANCED && keyType == KeyType.NUMERIC
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE && !numeric
                } else {
                    true
                }
            },
            isSuitableForExtendedPopup = { key ->
                if (key is TextKey) {
                    val keyCode = key.computedData.code
                    keyCode > KeyCode.SPACE && keyCode != KeyCode.CJK_SPACE || ExceptionsForKeyCodes.contains(keyCode)
                } else {
                    true
                }
            },
        )
        popupUiController.evaluator = evaluator
        popupUiController.keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
        controller.popupUiController = popupUiController
        val debugShowTouchBoundaries by prefs.devtools.showKeyTouchBoundaries.collectAsState()
        val flickPredictionsEnabled by prefs.glide.flickPredictionsEnabled.collectAsState()
        val activeContent by editorInstance.activeContentFlow.collectAsState()
        var isEasterEggActive by remember { mutableStateOf(false) }

        LaunchedEffect(activeContent) {
            val textBefore = activeContent.textBeforeSelection.toString()
            val composing = activeContent.composingText
            if (textBefore.endsWith("egg", ignoreCase = true) || composing.equals("egg", ignoreCase = true) ||
                textBefore.endsWith(" egg", ignoreCase = true) || textBefore.endsWith("egg ", ignoreCase = true)) {
                isEasterEggActive = true
                kotlinx.coroutines.delay(10_000L)
                isEasterEggActive = false
            }
        }

        var eclectusFlightTriggerTime by remember { mutableStateOf(0L) }
        var sunConureFlightTriggerTime by remember { mutableStateOf(0L) }
        var soccerRollTriggerTime by remember { mutableStateOf(0L) }
        var spaceRainTriggerTime by remember { mutableStateOf(0L) }
        LaunchedEffect(activeContent) {
            val tb = activeContent.textBeforeSelection.toString().lowercase()
            val comp = activeContent.composingText.lowercase()
            val eclectusKeys = listOf("eclectus", "ecky", "eckies", "roratus")
            if (eclectusKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                eclectusFlightTriggerTime = System.currentTimeMillis()
            }
            val sunConureKeys = listOf("sun conure", "sunconure", "conure")
            if (sunConureKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                sunConureFlightTriggerTime = System.currentTimeMillis()
            }
            val soccerKeys = listOf("soccer", "football", "futbol")
            if (soccerKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                soccerRollTriggerTime = System.currentTimeMillis()
            }
            val rainKeys = listOf("rain", "rainy", "raining")
            if (rainKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                spaceRainTriggerTime = System.currentTimeMillis()
            }
        }

        val currentWord = remember(activeContent) {
            when {
                activeContent.composing.isValid && activeContent.composingText.isNotBlank() -> {
                    activeContent.composingText
                }
                activeContent.localCurrentWord.isValid && activeContent.currentWordText.isNotBlank() -> {
                    activeContent.currentWordText
                }
                else -> {
                    activeContent.textBeforeSelection.takeLastWhile { it.isLetter() || it == '\'' }.toString()
                }
            }
        }
        val textBefore = activeContent.textBeforeSelection.toString()
        val prevWord = remember(textBefore, currentWord) {
            val beforeCurrent = if (currentWord.isNotEmpty()) {
                textBefore.dropLast(currentWord.length).trimEnd()
            } else {
                textBefore.trimEnd()
            }
            beforeCurrent.takeLastWhile { it.isLetter() || it == '\'' }
        }
        val flickPredictions = remember(currentWord, prevWord, flickPredictionsEnabled) {
            if (flickPredictionsEnabled && org.florisboard.libnative.FlorisNative.isAvailable()) {
                org.florisboard.libnative.FlorisNative.predictNextLetterWords(currentWord, prevWord)
            } else {
                emptyMap()
            }
        }
        val infiniteTransition = rememberInfiniteTransition(label = "FretPulseTransition")
        val fretCyanPulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 0.90f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "FretCyanPulseAlpha"
        )
        val pulseSpread by infiniteTransition.animateFloat(
            initialValue = 0.10f,
            targetValue = 0.38f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "FretPulseSpread"
        )

        val leftCore = (0.5f - pulseSpread).coerceAtLeast(0.05f)
        val rightCore = (0.5f + pulseSpread).coerceAtMost(0.95f)
        val leftFade = (leftCore - 0.12f).coerceAtLeast(0f)
        val rightFade = (rightCore + 0.12f).coerceAtMost(1f)

        // Authentic BlackBerry 10 Dual-Tone Metallic Fret Lines (Centered perfectly between rows on all pages)
        // Easter Egg: Quantum Core Power Surge when plugged into power!
        if (powerSurgeAnim.value > 0f && powerSurgeAnim.value < 1f) {
            val surgeProgress = powerSurgeAnim.value
            val surgeY = keyboardHeight * (1.0f - surgeProgress)
            val surgeAlpha = when {
                surgeProgress < 0.2f -> surgeProgress / 0.2f
                surgeProgress > 0.8f -> (1.0f - surgeProgress) / 0.2f
                else -> 1.0f
            }

            // 1. Horizontal Superconductor Energy Wave Scanning Upwards
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .absoluteOffset { IntOffset(0, (surgeY - 12.dp.toPx()).toInt()) }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeAccentColor.copy(alpha = 0.28f * surgeAlpha),
                                Color.White.copy(alpha = 0.55f * surgeAlpha),
                                themeAccentColor.copy(alpha = 0.28f * surgeAlpha),
                                Color.Transparent,
                            )
                        )
                    )
            )

            // 2. Central Specular Plasma Line
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .absoluteOffset { IntOffset(0, surgeY.toInt()) }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeAccentColor.copy(alpha = 0.5f * surgeAlpha),
                                Color.White.copy(alpha = 0.95f * surgeAlpha),
                                themeAccentColor.copy(alpha = 0.5f * surgeAlpha),
                                Color.Transparent,
                            )
                        )
                    )
            )

            // 3. Subtle Charging Port Floor Pulse at the Bottom
            if (surgeProgress < 0.6f) {
                val floorAlpha = (1.0f - (surgeProgress / 0.6f)) * 0.45f
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    themeAccentColor.copy(alpha = floorAlpha * 0.7f),
                                    Color.White.copy(alpha = floorAlpha),
                                    themeAccentColor.copy(alpha = floorAlpha * 0.7f),
                                    Color.Transparent,
                                )
                            )
                        )
                )
            }
        }

        val showFretsOnBorderless by prefs.theme.showFretsOnBorderless.collectAsState()
        val shouldShowFrets = !isBorderlessTheme || showFretsOnBorderless
        if (shouldShowFrets && keyboard.mode in setOf(KeyboardMode.CHARACTERS, KeyboardMode.SYMBOLS, KeyboardMode.SYMBOLS2, KeyboardMode.NUMERIC, KeyboardMode.NUMERIC_ADVANCED, KeyboardMode.PHONE, KeyboardMode.PHONE2)) {
            val fretPositions = remember(keyboard, keyboard.mode, keyboardWidth, keyboardHeight, desiredKeyHack.value) {
                val rowBounds = keyboard.keys().asSequence()
                    .groupBy { it.touchBounds.top.toInt() }
                    .values
                    .mapNotNull { keysInRow ->
                        val top = keysInRow.minOfOrNull { it.visibleBounds.top }
                        val bottom = keysInRow.maxOfOrNull { it.visibleBounds.bottom }
                        if (top != null && bottom != null && bottom > top) top to bottom else null
                    }
                    .sortedBy { it.first }

                val positions = mutableListOf<Int>()
                if (rowBounds.size > 1) {
                    for (i in 1 until rowBounds.size) {
                        val prevBottom = rowBounds[i - 1].second
                        val currentTop = rowBounds[i].first
                        val centerBetweenRows = ((prevBottom + currentTop) / 2f).toInt()
                        if (centerBetweenRows > 0) {
                            positions.add(centerBetweenRows)
                        }
                    }
                } else if (keyboard.rowCount > 1 && keyboardHeight > 0f) {
                    // 100% reliable geometric fallback
                    val rowH = keyboardHeight / keyboard.rowCount
                    for (i in 1 until keyboard.rowCount) {
                        positions.add((i * rowH).toInt())
                    }
                }
                positions
            }
            for (fretY in fretPositions) {
                // Top Specular Chrome Highlight with Outward-Radiating Cyan Breathing Wave
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .absoluteOffset { IntOffset(0, fretY - 1) }
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Transparent,
                                    leftFade to themeAccentColor.copy(alpha = 0.12f * fretCyanPulseAlpha),
                                    leftCore to Color(0x60CBD5E1),
                                    0.5f to themeAccentColor.copy(alpha = fretCyanPulseAlpha),
                                    rightCore to Color(0x60CBD5E1),
                                    rightFade to themeAccentColor.copy(alpha = 0.12f * fretCyanPulseAlpha),
                                    1.0f to Color.Transparent,
                                )
                            )
                        )
                )
                // Bottom Deep Ambient Shadow Line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .absoluteOffset { IntOffset(0, fretY) }
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0x15000000),
                                    Color(0x70000000),
                                    Color(0xA0000000),
                                    Color(0x70000000),
                                    Color(0x15000000),
                                )
                            )
                        )
                )
            }
        }

        for (textKey in keyboard.keys()) {
            val keyLabel = evaluator.computeLabel(textKey.computedData)?.lowercase() ?: ""
            val charCode = if (keyLabel.length == 1) keyLabel[0] else textKey.computedData.code.toChar().lowercaseChar()
            val hasFlick = flickPredictions.containsKey(charCode)
            TextKeyButton(
                textKey, evaluator, desiredKey,
                debugShowTouchBoundaries,
                hideHint = hasFlick,
            )
        }

        // Authentic BlackBerry 10 Floating Fret Word Overlay Layer
        if (flickPredictionsEnabled && flickPredictions.isNotEmpty() && keyboard.mode == KeyboardMode.CHARACTERS) {
            for (textKey in keyboard.keys()) {
                val keyLabel = evaluator.computeLabel(textKey.computedData)?.lowercase() ?: ""
                val charCode = if (keyLabel.length == 1) keyLabel[0] else textKey.computedData.code.toChar().lowercaseChar()
                val flickWord = flickPredictions[charCode] ?: continue

                val extraChars = (flickWord.length - 3).coerceAtLeast(0)
                val widthFactor = (1.25f + extraChars * 0.14f).coerceIn(1.25f, 2.4f)
                val dynamicWidth = textKey.visibleBounds.width * widthFactor
                val halfWidth = dynamicWidth / 2f

                Box(
                    modifier = Modifier
                        .requiredSize(
                            width = dynamicWidth.toDp(),
                            height = 20.dp,
                        )
                        .absoluteOffset {
                            IntOffset(
                                x = (textKey.visibleBounds.center.x - halfWidth).toInt(),
                                y = (textKey.visibleBounds.top - 10.dp.toPx()).toInt(),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .background(
                                color = androidx.compose.ui.graphics.Color(0x30000000),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(5.dp),
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    ) {
                        androidx.compose.material3.Text(
                            text = flickWord,
                            fontSize = 11.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = themeAccentColor.copy(alpha = 0.92f),
                            style = androidx.compose.ui.text.TextStyle(
                                shadow = androidx.compose.ui.graphics.Shadow(
                                    color = androidx.compose.ui.graphics.Color(0xE0000000),
                                    offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                    blurRadius = 3f,
                                )
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }

        // BlackBerry 10 Flick Catapult Particle & Glow Animation Layer
        val activeCatapult = controller.activeCatapult
        if (activeCatapult != null) {
            val animProgress = remember(activeCatapult.timestamp) { Animatable(0f) }
            LaunchedEffect(activeCatapult.timestamp) {
                animProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 260,
                        easing = FastOutSlowInEasing,
                    )
                )
                controller.activeCatapult = null
            }
            val progress = animProgress.value
            val yOffset = (progress * -75f).dp
            val alpha = (1f - progress).coerceIn(0f, 1f)
            val scale = 1f + (progress * 0.30f)

            // Luminous upward velocity beam trail
            Box(
                modifier = Modifier
                    .requiredSize(40.dp, 80.dp)
                    .absoluteOffset {
                        IntOffset(
                            x = (activeCatapult.position.x - 20.dp.toPx()).toInt(),
                            y = (activeCatapult.position.y - 40.dp.toPx() + (yOffset.toPx() * 0.6f)).toInt(),
                        )
                    }
                    .graphicsLayer {
                        this.alpha = alpha * 0.7f
                    }
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                themeAccentColor.copy(alpha = 0.8f * alpha),
                                themeAccentColor.copy(alpha = 0.2f * alpha),
                                androidx.compose.ui.graphics.Color.Transparent,
                            )
                        ),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                    )
            )

            // Catapult Word Capsule with Specular Ring Glow
            Box(
                modifier = Modifier
                    .requiredSize(110.dp, 36.dp)
                    .absoluteOffset {
                        IntOffset(
                            x = (activeCatapult.position.x - 55.dp.toPx()).toInt(),
                            y = (activeCatapult.position.y - 18.dp.toPx() + yOffset.toPx()).toInt(),
                        )
                    }
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleX = scale
                        this.scaleY = scale
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = androidx.compose.ui.graphics.Color(0x40000000),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .border(
                            width = 1.dp,
                            color = themeAccentColor.copy(alpha = alpha * 0.7f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    androidx.compose.material3.Text(
                        text = activeCatapult.word,
                        fontSize = 12.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = themeAccentColor.copy(alpha = alpha),
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = androidx.compose.ui.graphics.Color(0xE0000000),
                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                blurRadius = 4f,
                            )
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        // Roratus Live Wallpaper Easter Egg: Eclectus Parrots Tandem Flight
        if (eclectusFlightTriggerTime > 0L) {
            val flightProgress = remember(eclectusFlightTriggerTime) { Animatable(0f) }
            LaunchedEffect(eclectusFlightTriggerTime) {
                flightProgress.snapTo(0f)
                flightProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4200, easing = LinearEasing),
                )
                eclectusFlightTriggerTime = 0L
            }
            if (flightProgress.value in 0.001f..0.999f) {
                val t = flightProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height

                    // Smooth Swooping Bezier Curve across keyboard (full screen width + full off-screen exit)
                    val startX = -100f * density
                    val endX = w + 220f * density
                    val u = 1f - t
                    val cx = u * u * startX + 2 * u * t * (w * 0.52f) + t * t * endX
                    val cy = u * u * (h * 0.70f) + 2 * u * t * (h * 0.15f) + t * t * (h * 0.52f)

                    // Velocity Heading
                    val vx = 2 * u * (w * 0.5f - startX) + 2 * t * (endX - w * 0.5f)
                    val vy = 2 * u * (h * 0.15f - h * 0.70f) + 2 * t * (h * 0.55f - h * 0.15f)
                    val angleDeg = Math.toDegrees(Math.atan2(vy.toDouble(), vx.toDouble())).toFloat()

                    val flapSin = kotlin.math.sin(t * 36f)
                    val wingSpanFactor = 0.55f + 0.45f * flapSin

                    // Function to draw one Eclectus parrot
                    fun drawParrot(x: Float, y: Float, isMale: Boolean, scale: Float) {
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(x, y)
                        drawContext.canvas.nativeCanvas.rotate(angleDeg)
                        drawContext.canvas.nativeCanvas.scale(scale * density, scale * density)

                        val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isMale) 0xFF18A957.toInt() else 0xFFE5484D.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val wingSheenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isMale) 0xFF3FD680.toInt() else 0xFFFF5C63.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.5f
                        }
                        val underwingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isMale) 0xFF2E6FD6.toInt() else 0xFF7C5CD6.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val beakPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = if (isMale) 0xFFF6813C.toInt() else 0xFF111815.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }

                        val bodyPath = android.graphics.Path().apply {
                            moveTo(18f, 0f)
                            lineTo(6f, 4f)
                            lineTo(-12f, 3f)
                            lineTo(-24f, 1.5f)
                            lineTo(-24f, -1.5f)
                            lineTo(-12f, -3f)
                            lineTo(6f, -4f)
                            close()
                        }

                        val wingSpread = 22f * wingSpanFactor
                        val leftWing = android.graphics.Path().apply {
                            moveTo(4f, -2f)
                            lineTo(-6f, -wingSpread)
                            lineTo(-14f, -wingSpread * 0.85f)
                            lineTo(-10f, -2f)
                            close()
                        }
                        val rightWing = android.graphics.Path().apply {
                            moveTo(4f, 2f)
                            lineTo(-6f, wingSpread)
                            lineTo(-14f, wingSpread * 0.85f)
                            lineTo(-10f, 2f)
                            close()
                        }

                        val beakPath = android.graphics.Path().apply {
                            moveTo(18f, 0f)
                            lineTo(8f, 3.5f)
                            lineTo(8f, -3.5f)
                            close()
                        }

                        // Draw wings
                        drawContext.canvas.nativeCanvas.drawPath(leftWing, underwingPaint)
                        drawContext.canvas.nativeCanvas.drawPath(rightWing, underwingPaint)
                        drawContext.canvas.nativeCanvas.drawPath(leftWing, wingSheenPaint)
                        drawContext.canvas.nativeCanvas.drawPath(rightWing, wingSheenPaint)

                        // Draw body
                        drawContext.canvas.nativeCanvas.drawPath(bodyPath, bodyPaint)

                        // Female Royal Violet band
                        if (!isMale) {
                            val bandPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = 0xFF7C5CD6.toInt()
                                style = android.graphics.Paint.Style.FILL
                            }
                            val bandPath = android.graphics.Path().apply {
                                moveTo(4f, -3.5f)
                                lineTo(-4f, -3f)
                                lineTo(-4f, 3f)
                                lineTo(4f, 3.5f)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(bandPath, bandPaint)
                        }

                        // Draw beak
                        drawContext.canvas.nativeCanvas.drawPath(beakPath, beakPaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // 1. Male Eclectus (Leader)
                    drawParrot(cx, cy, isMale = true, scale = 0.85f)

                    // 2. Female Eclectus (Tandem Follower: -48px behind, -22px above)
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val femaleX = cx - (48f * density * kotlin.math.cos(rad).toFloat()) + (20f * density * kotlin.math.sin(rad).toFloat())
                    val femaleY = cy - (48f * density * kotlin.math.sin(rad).toFloat()) - (20f * density * kotlin.math.cos(rad).toFloat())
                    drawParrot(femaleX, femaleY, isMale = false, scale = 0.80f)
                }
            }
        }

        // Solstice Easter Egg: Fast Golden Sun Conure Flight (Right to Left)
        if (sunConureFlightTriggerTime > 0L) {
            val conureProgress = remember(sunConureFlightTriggerTime) { Animatable(0f) }
            LaunchedEffect(sunConureFlightTriggerTime) {
                conureProgress.snapTo(0f)
                conureProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2100, easing = LinearEasing),
                )
                sunConureFlightTriggerTime = 0L
            }
            if (conureProgress.value in 0.001f..0.999f) {
                val t = conureProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height

                    // Quick Energetic Right-to-Left Swoop across keyboard
                    val startX = w + 100f * density
                    val endX = -120f * density
                    val u = 1f - t
                    val cx = u * u * startX + 2 * u * t * (w * 0.48f) + t * t * endX
                    val cy = u * u * (h * 0.35f) + 2 * u * t * (h * 0.75f) + t * t * (h * 0.25f)

                    // Velocity Heading pointing in direction of flight
                    val vx = 2 * u * (w * 0.48f - startX) + 2 * t * (endX - w * 0.48f)
                    val vy = 2 * u * (h * 0.75f - h * 0.35f) + 2 * t * (h * 0.25f - h * 0.75f)
                    val angleDeg = Math.toDegrees(Math.atan2(vy.toDouble(), vx.toDouble())).toFloat()

                    val flapSin = kotlin.math.sin(t * 44f) // Rapid wing beats
                    val wingSpanFactor = 0.55f + 0.45f * flapSin

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(cx, cy)
                    drawContext.canvas.nativeCanvas.rotate(angleDeg)
                    val scale = 0.90f
                    drawContext.canvas.nativeCanvas.scale(scale * density, scale * density)

                    // Radiant Sun Conure Color Palette (Predominantly Brilliant Sunshine Yellow)
                    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFEA00.toInt() // Brilliant Canary / Pure Sunshine Yellow
                        style = android.graphics.Paint.Style.FILL
                    }
                    val wingYellowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFD600.toInt() // Luminous Sunburst Yellow wing plumage
                        style = android.graphics.Paint.Style.FILL
                    }
                    val wingSheenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFF59D.toInt() // Warm Sunlit Highlight
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.5f
                    }
                    val maskPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFF9100.toInt() // Warm Tangerine cheek blush
                        style = android.graphics.Paint.Style.FILL
                    }
                    val wingBluePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF2979FF.toInt() // Royal Cobalt primary wingtip rim
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.2f
                    }
                    val beakPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF212121.toInt() // Charcoal beak
                        style = android.graphics.Paint.Style.FILL
                    }

                    val bodyPath = android.graphics.Path().apply {
                        moveTo(18f, 0f)
                        lineTo(6f, 4f)
                        lineTo(-12f, 3f)
                        lineTo(-24f, 1.5f) // Conure tapered tail
                        lineTo(-24f, -1.5f)
                        lineTo(-12f, -3f)
                        lineTo(6f, -4f)
                        close()
                    }

                    val maskPath = android.graphics.Path().apply {
                        moveTo(16f, 0f)
                        lineTo(7f, 3.5f)
                        lineTo(1f, 2.5f)
                        lineTo(1f, -2.5f)
                        lineTo(7f, -3.5f)
                        close()
                    }

                    val wingSpread = 22f * wingSpanFactor
                    val leftWing = android.graphics.Path().apply {
                        moveTo(4f, -2f)
                        lineTo(-6f, -wingSpread)
                        lineTo(-14f, -wingSpread * 0.85f)
                        lineTo(-10f, -2f)
                        close()
                    }
                    val rightWing = android.graphics.Path().apply {
                        moveTo(4f, 2f)
                        lineTo(-6f, wingSpread)
                        lineTo(-14f, wingSpread * 0.85f)
                        lineTo(-10f, 2f)
                        close()
                    }

                    val beakPath = android.graphics.Path().apply {
                        moveTo(18f, 0f)
                        lineTo(8f, 3.5f)
                        lineTo(8f, -3.5f)
                        close()
                    }

                    // Draw Conure Wings (Luminous Yellow with subtle Cobalt rim)
                    drawContext.canvas.nativeCanvas.drawPath(leftWing, wingYellowPaint)
                    drawContext.canvas.nativeCanvas.drawPath(rightWing, wingYellowPaint)
                    drawContext.canvas.nativeCanvas.drawPath(leftWing, wingSheenPaint)
                    drawContext.canvas.nativeCanvas.drawPath(rightWing, wingSheenPaint)
                    drawContext.canvas.nativeCanvas.drawPath(leftWing, wingBluePaint)
                    drawContext.canvas.nativeCanvas.drawPath(rightWing, wingBluePaint)

                    // Draw Golden Yellow Body
                    drawContext.canvas.nativeCanvas.drawPath(bodyPath, bodyPaint)

                    // Draw Fiery Orange Mask & Cheeks
                    drawContext.canvas.nativeCanvas.drawPath(maskPath, maskPaint)

                    // Draw Slate Beak
                    drawContext.canvas.nativeCanvas.drawPath(beakPath, beakPaint)

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Soccer Ball Fret Roll Easter Egg: Top Fret (L -> R) then Bottom Fret (R -> L)
        if (soccerRollTriggerTime > 0L) {
            val soccerProgress = remember(soccerRollTriggerTime) { Animatable(0f) }
            LaunchedEffect(soccerRollTriggerTime) {
                soccerProgress.snapTo(0f)
                soccerProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3800, easing = LinearEasing),
                )
                soccerRollTriggerTime = 0L
            }
            if (soccerProgress.value in 0.001f..0.999f) {
                val t = soccerProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = size.width
                    val h = size.height
                    val ballRadius = 14f * density

                    // Determine top and bottom fret lines
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val topFretY = h / rowCount
                    val bottomFretY = h - (h / rowCount)

                    val cx: Float
                    val cy: Float
                    val rotDeg: Float

                    if (t < 0.5f) {
                        // Phase 1: Roll across top fret from Left to Right
                        val p = t * 2.0f
                        cx = (-ballRadius * 2.5f) + p * (w + ballRadius * 5f)
                        cy = topFretY - ballRadius + 1f
                        rotDeg = p * 720f
                    } else {
                        // Phase 2: Roll across bottom fret from Right to Left
                        val p = (t - 0.5f) * 2.0f
                        cx = (w + ballRadius * 2.5f) - p * (w + ballRadius * 5f)
                        cy = bottomFretY - ballRadius + 1f
                        rotDeg = -p * 720f
                    }

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(cx, cy)
                    drawContext.canvas.nativeCanvas.rotate(rotDeg)

                    // Authentic Telstar Truncated Icosahedron Paints
                    val whiteLeatherPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val blackPentagonPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF121416.toInt() // Deep pitch black leather
                        style = android.graphics.Paint.Style.FILL
                    }
                    val seamStitchPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF2B303A.toInt() // Authentic seam stitch
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.3f
                    }
                    val ballOutlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF181A1B.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.6f
                    }

                    // 1. Base White Sphere with subtle 3D depth
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, ballRadius, whiteLeatherPaint)

                    // 2. Center Pentagon (Black)
                    val centerPentagon = android.graphics.Path()
                    val rCenter = ballRadius * 0.36f
                    for (i in 0 until 5) {
                        val angle = Math.toRadians((i * 72.0 - 90.0))
                        val px = (rCenter * kotlin.math.cos(angle)).toFloat()
                        val py = (rCenter * kotlin.math.sin(angle)).toFloat()
                        if (i == 0) centerPentagon.moveTo(px, py) else centerPentagon.lineTo(px, py)
                    }
                    centerPentagon.close()
                    drawContext.canvas.nativeCanvas.drawPath(centerPentagon, blackPentagonPaint)
                    drawContext.canvas.nativeCanvas.drawPath(centerPentagon, seamStitchPaint)

                    // 3. Surrounding 5 Black Pentagons & Connecting Hexagonal Seams
                    val rInnerSpoke = ballRadius * 0.65f
                    for (i in 0 until 5) {
                        val aCenter = Math.toRadians((i * 72.0 - 90.0))
                        val aNext = Math.toRadians(((i + 1) * 72.0 - 90.0))
                        val aMid = (aCenter + aNext) / 2.0

                        // Spoke from center pentagon vertex outward
                        val vx = (rCenter * kotlin.math.cos(aCenter)).toFloat()
                        val vy = (rCenter * kotlin.math.sin(aCenter)).toFloat()
                        val sx = (rInnerSpoke * kotlin.math.cos(aCenter)).toFloat()
                        val sy = (rInnerSpoke * kotlin.math.sin(aCenter)).toFloat()
                        drawContext.canvas.nativeCanvas.drawLine(vx, vy, sx, sy, seamStitchPaint)

                        // Hexagonal cross-bridges
                        val sxNext = (rInnerSpoke * kotlin.math.cos(aNext)).toFloat()
                        val syNext = (rInnerSpoke * kotlin.math.sin(aNext)).toFloat()
                        drawContext.canvas.nativeCanvas.drawLine(sx, sy, sxNext, syNext, seamStitchPaint)

                        // Outer Black Pentagon on perimeter
                        val outerPent = android.graphics.Path()
                        val pPeakX = (ballRadius * 0.62f * kotlin.math.cos(aMid)).toFloat()
                        val pPeakY = (ballRadius * 0.62f * kotlin.math.sin(aMid)).toFloat()
                        val pEdge1X = (ballRadius * kotlin.math.cos(aMid - 0.32)).toFloat()
                        val pEdge1Y = (ballRadius * kotlin.math.sin(aMid - 0.32)).toFloat()
                        val pEdge2X = (ballRadius * kotlin.math.cos(aMid + 0.32)).toFloat()
                        val pEdge2Y = (ballRadius * kotlin.math.sin(aMid + 0.32)).toFloat()
                        val pMid1X = (ballRadius * 0.88f * kotlin.math.cos(aMid - 0.42)).toFloat()
                        val pMid1Y = (ballRadius * 0.88f * kotlin.math.sin(aMid - 0.42)).toFloat()
                        val pMid2X = (ballRadius * 0.88f * kotlin.math.cos(aMid + 0.42)).toFloat()
                        val pMid2Y = (ballRadius * 0.88f * kotlin.math.sin(aMid + 0.42)).toFloat()

                        outerPent.moveTo(pPeakX, pPeakY)
                        outerPent.lineTo(pMid1X, pMid1Y)
                        outerPent.lineTo(pEdge1X, pEdge1Y)
                        outerPent.lineTo(pEdge2X, pEdge2Y)
                        outerPent.lineTo(pMid2X, pMid2Y)
                        outerPent.close()

                        drawContext.canvas.nativeCanvas.drawPath(outerPent, blackPentagonPaint)
                        drawContext.canvas.nativeCanvas.drawPath(outerPent, seamStitchPaint)
                    }

                    // 4. Photorealistic 3D Curvature & Specular Lighting Overlay
                    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(
                            ballRadius * 0.25f, ballRadius * 0.25f, ballRadius * 1.1f,
                            intArrayOf(0x00000000, 0x1A000000, 0x66000000),
                            floatArrayOf(0.4f, 0.75f, 1.0f),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, ballRadius, shadowPaint)

                    val highlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x66FFFFFF.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(-ballRadius * 0.38f, -ballRadius * 0.38f, ballRadius * 0.32f, highlightPaint)

                    // Outer perimeter outline
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, ballRadius, ballOutlinePaint)

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        popupUiController.RenderPopups()
    }

    LaunchedEffect(Unit) {
        for (event in touchEventChannel) {
            if (!isActive) break
            controller.onTouchEventInternal(event)
            event.recycle()
        }
    }
}

@Composable
private fun TextKeyButton(
    key: TextKey,
    evaluator: ComputingEvaluator,
    desiredKey: TextKey,
    debugShowTouchBoundaries: Boolean,
    hideHint: Boolean = false,
) = with(LocalDensity.current) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val editorInstance by context.editorInstance()
    val activeContent by editorInstance.activeContentFlow.collectAsState()
    val themeManager by context.themeManager()
    val activeThemeInfo by themeManager.activeThemeInfo.collectAsState()
    val activeThemeCompId = activeThemeInfo.name.componentId
    val isBorderlessTheme = "borderless" in activeThemeCompId
    val chameleonEnabled by prefs.theme.chameleonAppAccentMatcher.collectAsState()
    val packageName = editorInstance.activeInfo.packageName
    val themeAccentColor = remember(activeThemeCompId, chameleonEnabled, packageName) {
        val pkg = (packageName ?: "").lowercase()
        if (chameleonEnabled && pkg.isNotBlank()) {
            when {
                pkg.contains("whatsapp") || pkg.contains("signal") || pkg.contains("wechat") -> Color(0xFF00E5A3)
                pkg.contains("telegram") || pkg.contains("twitter") || pkg.contains("bluesky") -> Color(0xFF00D2FF)
                pkg.contains("discord") || pkg.contains("twitch") -> Color(0xFFA78BFA)
                pkg.contains("reddit") || pkg.contains("youtube") -> Color(0xFFFF4500)
                pkg.contains("slack") || pkg.contains("github") || pkg.contains("obsidian") -> Color(0xFFF59E0B)
                pkg.contains("spotify") -> Color(0xFF1DB954)
                else -> Color(0xFF00D2FF)
            }
        } else {
            when {
                "purple" in activeThemeCompId -> Color(0xFFA855F7)
                "crimson" in activeThemeCompId -> Color(0xFFEF4444)
                "sakura" in activeThemeCompId -> Color(0xFFEC4899)
                "emerald" in activeThemeCompId -> Color(0xFF00E5A3)
                "amber" in activeThemeCompId -> Color(0xFFF59E0B)
                "ghost" in activeThemeCompId -> Color(0xFFF8FAFC)
                else -> Color(0xFF00D2FF)
            }
        }
    }

    var eggTriggerTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastEggSignature by remember { mutableStateOf("") }
    val eggAlphaAnim = remember { Animatable(0f) }

    var sunConureTriggerTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastSunConureSignature by remember { mutableStateOf("") }
    val sunConurePulseAlpha = remember { Animatable(0f) }

    LaunchedEffect(activeContent) {
        val textBefore = activeContent.textBeforeSelection.toString()
        val composing = activeContent.composingText
        val isEgg = textBefore.endsWith("egg", ignoreCase = true) || composing.equals("egg", ignoreCase = true) ||
            textBefore.endsWith(" egg", ignoreCase = true) || textBefore.endsWith("egg ", ignoreCase = true)

        val signature = "$textBefore::$composing"
        if (isEgg && signature != lastEggSignature) {
            lastEggSignature = signature
            eggTriggerTime = System.currentTimeMillis()
        }

        val combined = "$textBefore $composing".lowercase()
        val isSunConure = combined.contains("sun conure") || combined.contains("sunconure") || combined.contains("sun con ure")
        if (isSunConure && signature != lastSunConureSignature) {
            lastSunConureSignature = signature
            sunConureTriggerTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(sunConureTriggerTime) {
        if (sunConureTriggerTime > 0L) {
            // Elegant smooth fade in once (450ms)
            sunConurePulseAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 450, easing = EaseOutCubic),
            )
            // Sits majestically on the Shift key for 9.1 seconds
            kotlinx.coroutines.delay(9100L)
            // Elegant smooth fade out once (450ms)
            sunConurePulseAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 450, easing = EaseInCubic),
            )
        }
    }

    LaunchedEffect(eggTriggerTime) {
        if (eggTriggerTime > 0L) {
            // Elegant smooth fade in (350ms)
            eggAlphaAnim.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 350, easing = EaseOutCubic),
            )
            // Hold for 9.3 seconds (total active window = exactly 10.0 seconds)
            kotlinx.coroutines.delay(9300L)
            // Elegant smooth fade out (350ms)
            eggAlphaAnim.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350, easing = EaseInCubic),
            )
        }
    }
    val attributes = mapOf(
        FlorisImeUi.Attr.Code to key.computedData.code,
        FlorisImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
        FlorisImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
    )
    val selector = when {
        !key.isEnabled -> SnyggSelector.DISABLED
        key.isPressed -> SnyggSelector.PRESSED
        else -> SnyggSelector.NONE
    }
    val size = remember(key, desiredKey) {
        key.visibleBounds.size.toDpSize()
    }
    SnyggBox(
        FlorisImeUi.Key.elementName,
        attributes = attributes,
        selector = selector,
        modifier = Modifier
            .requiredSize(size)
            .absoluteOffset { key.visibleBounds.topLeft.toIntOffset() },
    ) {
        val isTelPadKey = key.computedData.type == KeyType.NUMERIC && evaluator.keyboard.mode == KeyboardMode.PHONE

        key.label?.let { label ->
            var customLabel = label
            if (key.computedData.code == KeyCode.SPACE) {
                val prefs by FlorisPreferenceStore
                val spaceBarMode by prefs.keyboard.spaceBarMode.collectAsState()
                when (spaceBarMode) {
                    SpaceBarMode.NOTHING -> return@let
                    SpaceBarMode.CURRENT_LANGUAGE -> {}
                    SpaceBarMode.SPACE_BAR_KEY -> customLabel = "␣"
                }
            }
            SnyggText(
                modifier = Modifier
                    .wrapContentSize()
                    .align(if (isTelPadKey) BiasAlignment(-0.5f, 0f) else Alignment.Center)
                    .graphicsLayer {
                        if (key.isPressed && key.isEnabled) {
                            scaleX = 1.12f
                            scaleY = 1.12f
                        }
                    },
                text = customLabel,
            )
        }
        if (!hideHint) {
            key.hintedLabel?.let { hintedLabel ->
                SnyggText(
                    elementName = FlorisImeUi.KeyHint.elementName,
                    attributes = attributes,
                    selector = selector,
                    modifier = Modifier
                        .wrapContentSize()
                        .align(if (isTelPadKey) BiasAlignment(0.5f, 0f) else Alignment.TopEnd),
                    text = hintedLabel,
                )
            }
        }
        if (key.isPressed && key.isEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0x4000E5FF),
                                Color(0x1200E5FF),
                                Color.Transparent,
                            )
                        ),
                        shape = RoundedCornerShape(6.dp),
                    )
            )
        }

        // Spacebar Rain Easter Egg (10 seconds smooth fade-in, rain droplets + ripples, and fade-out)
        if (key.computedData.code == KeyCode.SPACE) {
            val isRainActive = remember(activeContent) {
                val tb = activeContent.textBeforeSelection.toString().lowercase()
                val comp = activeContent.composingText.lowercase()
                val keys = listOf("rain", "rainy", "raining")
                keys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }
            }
            var rainStartTime by remember { mutableStateOf(0L) }
            LaunchedEffect(isRainActive) {
                if (isRainActive) {
                    rainStartTime = System.currentTimeMillis()
                }
            }

            val rainAlphaAnim = remember { Animatable(0f) }
            LaunchedEffect(rainStartTime) {
                if (rainStartTime > 0L) {
                    // Smooth 500ms fade in
                    rainAlphaAnim.animateTo(1f, tween(500, easing = LinearEasing))
                    // Rain for 9000ms
                    kotlinx.coroutines.delay(9000L)
                    // Smooth 500ms fade out
                    rainAlphaAnim.animateTo(0f, tween(500, easing = LinearEasing))
                    rainStartTime = 0L
                }
            }

            if (rainAlphaAnim.value > 0f) {
                val infiniteRainTransition = rememberInfiniteTransition(label = "SpaceRainInfinite")
                val rainTimeFast by infiniteRainTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(420, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                    label = "RainCycleFast",
                )
                val rainAlpha = rainAlphaAnim.value
                val densityScale = density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val cornerR = 6f * densityScale

                    fun makeArgb(r: Int, g: Int, b: Int, alpha: Float): Int {
                        val a = (alpha.coerceIn(0f, 1f) * 255f).toInt()
                        return (a shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
                    }

                    // 1. Wet Glass / Storm Sky Tint (Deep Slate Blue, never green)
                    val bgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(15, 23, 42, 0.45f * rainAlpha) // Deep slate storm
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawRoundRect(0f, 0f, canvasW, canvasH, cornerR, cornerR, bgPaint)

                    val glassGlintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(255, 255, 255, 0.12f * rainAlpha) // Pure translucent light sheen
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawRoundRect(1f * densityScale, 1f * densityScale, canvasW - 1f * densityScale, canvasH * 0.38f, cornerR, cornerR, glassGlintPaint)

                    // 2. Stationary Surface Condensation Water Beads (Pure crystal water dewdrops)
                    val beadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(148, 163, 184, 0.55f * rainAlpha) // Glassy water refraction
                        style = android.graphics.Paint.Style.FILL
                    }
                    val beadHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(255, 255, 255, 0.85f * rainAlpha) // Pure white glint
                        style = android.graphics.Paint.Style.FILL
                    }
                    val fixedBeadCount = 8
                    for (b in 0 until fixedBeadCount) {
                        val bx = (canvasW / (fixedBeadCount + 1).toFloat()) * (b + 1).toFloat() + ((b * 23) % 13 - 6).toFloat() * densityScale
                        val by = canvasH * (0.25f + ((b * 31) % 50) / 100f)
                        val br = (1.5f + (b % 3) * 0.6f) * densityScale
                        drawContext.canvas.nativeCanvas.drawCircle(bx, by, br, beadPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(bx - br * 0.35f, by - br * 0.35f, br * 0.45f, beadHighlightPaint)
                    }

                    // 3. Layer 1: Background Fine Drizzle (Pure Ice White / Silver streaks)
                    val drizzlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(224, 242, 254, 0.38f * rainAlpha) // Silver drizzle
                        strokeWidth = 0.8f * densityScale
                        style = android.graphics.Paint.Style.STROKE
                    }
                    val drizzleCount = 18
                    for (i in 0 until drizzleCount) {
                        val dx = 1.6f * densityScale
                        val seedX = (canvasW / (drizzleCount + 1).toFloat()) * (i + 1).toFloat() + ((i * 19) % 9 - 4).toFloat() * densityScale
                        val phase = ((rainTimeFast * 1.3f + (i.toFloat() * 0.13f)) % 1.0f)
                        val dropLen = (8f + ((i % 3) * 3).toFloat()) * densityScale
                        val startY = phase * (canvasH + dropLen * 2f) - dropLen
                        val endY = startY + dropLen
                        val startX = seedX - (phase * dx)
                        val endX = startX + dx

                        if (endY > 0f && startY < canvasH) {
                            drawContext.canvas.nativeCanvas.drawLine(startX, startY, endX, endY, drizzlePaint)
                        }
                    }

                    // 4. Layer 2: Foreground Crisp Raindrops with Pure White Tips & Concentric Ripples
                    val dropPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(240, 249, 255, 0.75f * rainAlpha) // Translucent crisp rain
                        strokeWidth = 1.4f * densityScale
                        style = android.graphics.Paint.Style.STROKE
                    }
                    val dropHeadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = makeArgb(255, 255, 255, 0.95f * rainAlpha) // Pure white droplet bead
                        style = android.graphics.Paint.Style.FILL
                    }
                    val ripplePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        strokeWidth = 1.1f * densityScale
                        style = android.graphics.Paint.Style.STROKE
                    }
                    val splashParticlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        style = android.graphics.Paint.Style.FILL
                    }

                    val mainDropCount = 10
                    for (i in 0 until mainDropCount) {
                        val dx = 2.2f * densityScale
                        val seedX = (canvasW / (mainDropCount + 1).toFloat()) * (i + 1).toFloat() + ((i * 37) % 15 - 7).toFloat() * densityScale
                        val phase = ((rainTimeFast + (i.toFloat() * 0.23f)) % 1.0f)
                        val dropLen = (12f + ((i % 4) * 4).toFloat()) * densityScale
                        val startY = phase * (canvasH + dropLen * 1.5f) - dropLen
                        val endY = startY + dropLen
                        val startX = seedX - (phase * dx)
                        val endX = startX + dx
                        val bottomImpactY = canvasH - 2f * densityScale

                        // Rain Streak & Leading Droplet Head
                        if (endY > 0f && startY < bottomImpactY) {
                            drawContext.canvas.nativeCanvas.drawLine(startX, startY, endX, endY, dropPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(endX, endY, 0.9f * densityScale, dropHeadPaint)
                        }

                        // Surface Impact: Double Ripple & Splash Micro-droplets
                        if (phase > 0.65f) {
                            val splashProgress = (phase - 0.65f) / 0.35f
                            val ripRadius1 = splashProgress * 9f * densityScale
                            val ripRadius2 = splashProgress * 5f * densityScale
                            val ripAlpha = (1f - splashProgress) * rainAlpha * 0.70f
                            ripplePaint.color = makeArgb(186, 230, 253, ripAlpha)

                            // Concentric Puddle Ellipses
                            drawContext.canvas.nativeCanvas.drawOval(
                                seedX - ripRadius1, bottomImpactY - ripRadius1 * 0.32f,
                                seedX + ripRadius1, bottomImpactY + ripRadius1 * 0.32f,
                                ripplePaint
                            )
                            if (splashProgress > 0.15f) {
                                drawContext.canvas.nativeCanvas.drawOval(
                                    seedX - ripRadius2, bottomImpactY - ripRadius2 * 0.32f,
                                    seedX + ripRadius2, bottomImpactY + ripRadius2 * 0.32f,
                                    ripplePaint
                                )
                            }

                            // Microscopic Splash Droplets bouncing upward
                            if (splashProgress < 0.6f) {
                                val splashRise = (1f - (splashProgress / 0.6f)) * 4.5f * densityScale
                                val partAlpha = (1f - splashProgress) * rainAlpha * 0.90f
                                splashParticlePaint.color = makeArgb(255, 255, 255, partAlpha)
                                drawContext.canvas.nativeCanvas.drawCircle(seedX - 2.5f * densityScale, bottomImpactY - splashRise, 0.8f * densityScale, splashParticlePaint)
                                drawContext.canvas.nativeCanvas.drawCircle(seedX + 3.0f * densityScale, bottomImpactY - splashRise * 0.8f, 0.7f * densityScale, splashParticlePaint)
                            }
                        }
                    }
                }
            }
        }

        if (key.computedData.code == KeyCode.SHIFT && eggAlphaAnim.value > 0f) {
            val eggImage = painterResource(id = R.drawable.ic_crake_easter_egg)
            Image(
                painter = eggImage,
                contentDescription = "Easter Egg",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(24.dp)
                    .graphicsLayer {
                        alpha = eggAlphaAnim.value
                        val baseScale = 0.88f + 0.12f * eggAlphaAnim.value
                        scaleX = baseScale * (if (key.isPressed && key.isEnabled) 1.25f else 1.0f)
                        scaleY = baseScale * (if (key.isPressed && key.isEnabled) 1.25f else 1.0f)
                    }
            )
        }
        if (key.computedData.code == KeyCode.SHIFT && sunConurePulseAlpha.value > 0f) {
            val sunConureImage = painterResource(id = R.drawable.ic_sun_conure)
            Image(
                painter = sunConureImage,
                contentDescription = "Sun Conure",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(26.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        alpha = sunConurePulseAlpha.value
                        val baseScale = 0.90f + 0.10f * sunConurePulseAlpha.value
                        scaleX = baseScale * (if (key.isPressed && key.isEnabled) 1.25f else 1.0f)
                        scaleY = baseScale * (if (key.isPressed && key.isEnabled) 1.25f else 1.0f)
                    }
            )
        }
        if (key.computedData.code != KeyCode.SHIFT || (eggAlphaAnim.value < 1f && sunConurePulseAlpha.value < 1f)) {
            key.foregroundImageVector?.let { imageVector ->
                SnyggIcon(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .graphicsLayer {
                            if (key.computedData.code == KeyCode.SHIFT) {
                                alpha = (1f - eggAlphaAnim.value) * (1f - sunConurePulseAlpha.value)
                            }
                            if (key.isPressed && key.isEnabled) {
                                scaleX = 1.12f
                                scaleY = 1.12f
                            }
                        },
                    imageVector = imageVector,
                    contentDescription = null,
                )
            }
        }
        if (key.computedData.code == KeyCode.SHIFT) {
            val shiftState = evaluator.state.inputShiftState
            val isShifted = shiftState != InputShiftState.UNSHIFTED
            val isLocked = shiftState == InputShiftState.CAPS_LOCK

            if (isLocked) {
                // Inward Glowing Border Halo for CAPS LOCK
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .border(
                            width = 1.5.dp,
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    themeAccentColor.copy(alpha = 0.95f),
                                    themeAccentColor.copy(alpha = 0.5f),
                                    Color.Transparent,
                                ),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    themeAccentColor.copy(alpha = 0.25f),
                                    Color.Transparent,
                                ),
                            ),
                            shape = RoundedCornerShape(8.dp),
                        )
                )
            }

            if (isShifted) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 4.dp)
                        .requiredSize(width = if (isLocked) 18.dp else 8.dp, height = 2.5.dp)
                        .background(
                            color = themeAccentColor,
                            shape = RoundedCornerShape(2.dp),
                        )
                )
            }
        }
        // Tactile Homing Fret Indicators for Dvorak (U, H) & QWERTY (F, J)
        val lowerLabel = (evaluator.computeLabel(key.computedData) ?: "").lowercase()
        val isHomingKey = lowerLabel == "u" || lowerLabel == "h" || lowerLabel == "f" || lowerLabel == "j"
        if (isHomingKey && key.isEnabled && evaluator.keyboard.mode == KeyboardMode.CHARACTERS) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .requiredSize(width = 6.dp, height = 1.5.dp)
                    .background(
                        color = themeAccentColor.copy(alpha = 0.65f),
                        shape = RoundedCornerShape(1.dp),
                    )
            )
        }
        if (key.computedData.code == KeyCode.SPACE) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .requiredSize(width = 54.dp, height = 2.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                themeAccentColor.copy(alpha = 0.38f),
                                themeAccentColor.copy(alpha = 0.85f),
                                themeAccentColor.copy(alpha = 0.38f),
                                Color.Transparent,
                            )
                        ),
                        shape = RoundedCornerShape(1.dp),
                    )
            )
        }
        if (key.computedData.code == KeyCode.ENTER) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .border(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                themeAccentColor.copy(alpha = 0.85f),
                                Color(0xFF00B4D8).copy(alpha = 0.35f),
                            )
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
            )
        }
        if (key.computedData.code == KeyCode.DELETE && key.isPressed) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                            colors = listOf(
                                Color(0x45FF1744),
                                Color(0x15FF1744),
                                Color.Transparent,
                            )
                        ),
                        shape = RoundedCornerShape(6.dp),
                    )
            )
        }
    }
    if (debugShowTouchBoundaries) {
        Box(
            modifier = Modifier
                .requiredSize(key.touchBounds.size.toDpSize())
                .absoluteOffset { key.touchBounds.topLeft.toIntOffset() }
                .border(Dp.Hairline, Color.Red),
        )
    }
}

@Suppress("unused_parameter")
private class TextKeyboardLayoutController(
    context: Context,
) : SwipeGesture.Listener, GlideTypingGesture.Listener {
    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val glideTypingManager by context.glideTypingManager()

    private val inputEventDispatcher get() = keyboardManager.inputEventDispatcher
    private val inputFeedbackController get() = FlorisImeService.inputFeedbackController()
    private val keyHintConfiguration = prefs.keyboard.keyHintConfiguration()
    private val pointerMap: PointerMap<TouchPointer> = PointerMap { TouchPointer() }
    lateinit var popupUiController: PopupUiController

    private var initSelectionStart: Int = 0
    private var initSelectionEnd: Int = 0
    var isGliding by mutableStateOf(false)

    val glideTypingDetector = GlideTypingGesture.Detector(context)
    val glideDataForDrawing = mutableStateListOf<Pair<GlideTypingGesture.Detector.Position, Long>>()
    val fadingGlide = mutableStateListOf<Pair<GlideTypingGesture.Detector.Position, Long>>()
    var fadingGlideRadius by mutableFloatStateOf(0.0f)
    private val swipeGestureDetector = SwipeGesture.Detector(this)

    data class CatapultEffect(
        val word: String,
        val position: Offset,
        val timestamp: Long = System.currentTimeMillis(),
    )
    var activeCatapult by mutableStateOf<CatapultEffect?>(null)

    lateinit var keyboard: TextKeyboard
    var size = Size.Zero

    val isGlideEnabled: Boolean get() = prefs.glide.enabled.get() && editorInstance.activeInfo.isRichInputEditor &&
        keyboardManager.activeState.keyVariation != KeyVariation.PASSWORD

    fun cancelGlideActive() {
        glideTypingDetector.cancel()
        glideTypingManager.cancelGlide()
        glideDataForDrawing.clear()
        isGliding = false
    }

    fun onTouchEventInternal(event: MotionEvent) {
        flogDebug { "event=$event" }
        swipeGestureDetector.onTouchEvent(event)
        if (isGlideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
            val glidePointer = pointerMap.firstOrNull()
            val isNotBlocked = glidePointer?.hasTriggeredLongPress != true
            if (isNotBlocked && glideTypingDetector.onTouchEvent(event, glidePointer?.initialKey)) {
                for (pointer in pointerMap) {
                    if (pointer.activeKey != null) {
                        onTouchCancelInternal(event, pointer)
                    }
                }
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    pointerMap.clear()
                }
                isGliding = true
                return
            }
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val oldPointer = pointerMap.findById(pointerId)
                if (oldPointer != null) {
                    swipeGestureDetector.onTouchCancel(event, oldPointer)
                    onTouchCancelInternal(event, oldPointer)
                    pointerMap.removeById(oldPointer.id)
                }
                // Search for active character keys and cancel them
                for (pointer in pointerMap) {
                    val activeKey = pointer.activeKey
                    if (activeKey != null && popupUiController.isSuitableForPopups(activeKey)) {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchUpInternal(event, pointer)
                    }
                }
                val pointer = pointerMap.add(pointerId, pointerIndex)
                if (pointer != null) {
                    swipeGestureDetector.onTouchDown(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (pointerIndex in 0 until event.pointerCount) {
                    val pointerId = event.getPointerId(pointerIndex)
                    val pointer = pointerMap.findById(pointerId)
                    if (pointer != null) {
                        pointer.index = pointerIndex
                        val alwaysTriggerOnMove = (pointer.hasTriggeredGestureMove
                            && (pointer.initialKey?.computedData?.code == KeyCode.DELETE
                            && prefs.gestures.deleteKeySwipeLeft.get().let {
                                it == SwipeAction.DELETE_CHARACTERS_PRECISELY || it == SwipeAction.SELECT_CHARACTERS_PRECISELY
                            }
                            || pointer.initialKey?.computedData?.code == KeyCode.SPACE
                            || pointer.initialKey?.computedData?.code == KeyCode.CJK_SPACE))
                        if (swipeGestureDetector.onTouchMove(event, pointer, alwaysTriggerOnMove) || pointer.hasTriggeredGestureMove) {
                            pointer.hasTriggeredGestureMove = true
                            pointer.activeKey?.let { activeKey ->
                                inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            }
                        } else {
                            onTouchMoveInternal(event, pointer)
                        }
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                val pointer = pointerMap.findById(pointerId)
                if (pointer != null) {
                    pointer.index = pointerIndex
                    if (swipeGestureDetector.onTouchUp(event, pointer) || pointer.hasTriggeredGestureMove) {
                        cancelGlideActive()
                        if (pointer.hasTriggeredGestureMove && pointer.initialKey?.computedData?.code == KeyCode.DELETE) {
                            val selection = editorInstance.activeContent.selection
                            if (selection.isSelectionMode) {
                                editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                            }
                        }
                        onTouchCancelInternal(event, pointer)
                    } else {
                        onTouchUpInternal(event, pointer)
                    }
                    pointerMap.removeById(pointer.id)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                for (pointer in pointerMap) {
                    if (pointer.id == pointerId) {
                        pointer.index = pointerIndex
                        if (swipeGestureDetector.onTouchUp(event, pointer) || pointer.hasTriggeredGestureMove) {
                            cancelGlideActive()
                            if (pointer.hasTriggeredGestureMove &&
                                pointer.initialKey?.computedData?.code == KeyCode.DELETE &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_CHARACTERS_PRECISELY &&
                                prefs.gestures.deleteKeySwipeLeft.get() != SwipeAction.SELECT_WORDS_PRECISELY) {
                                val selection = editorInstance.activeContent.selection
                                if (selection.isSelectionMode) {
                                    editorInstance.deleteBackwards(OperationUnit.CHARACTERS)
                                }
                            }
                            onTouchCancelInternal(event, pointer)
                        } else {
                            onTouchUpInternal(event, pointer)
                        }
                    } else {
                        swipeGestureDetector.onTouchCancel(event, pointer)
                        onTouchCancelInternal(event, pointer)
                    }
                }
                pointerMap.clear()
            }
            MotionEvent.ACTION_CANCEL -> {
                for (pointer in pointerMap) {
                    swipeGestureDetector.onTouchCancel(event, pointer)
                    onTouchCancelInternal(event, pointer)
                }
                pointerMap.clear()
            }
        }
    }

    private fun onTouchDownInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val touchX = event.getX(pointer.index)
        val touchY = event.getY(pointer.index)
        val key = if (prefs.keyboard.adaptiveHitboxExpansion.get() && keyboard.mode == KeyboardMode.CHARACTERS) {
            val activeContent = editorInstance.activeContent
            val textBefore = activeContent.textBeforeSelection.toString()
            val composing = activeContent.composingText
            val prefix = when {
                activeContent.composing.isValid && composing.isNotBlank() -> composing
                activeContent.localCurrentWord.isValid && activeContent.currentWordText.isNotBlank() -> activeContent.currentWordText
                else -> textBefore.takeLastWhile { it.isLetter() || it == '\'' }.toString()
            }
            val beforePrefix = if (prefix.isNotEmpty()) {
                textBefore.dropLast(prefix.length).trimEnd()
            } else {
                textBefore.trimEnd()
            }
            val prevWord = beforePrefix.takeLastWhile { it.isLetter() || it == '\'' }
            val predictedLetters = if (org.florisboard.libnative.FlorisNative.isAvailable()) {
                org.florisboard.libnative.FlorisNative.predictNextLetterWords(prefix, prevWord).keys
            } else {
                emptySet()
            }
            keyboard.getKeyForPosAdaptive(touchX, touchY, predictedLetters)
        } else {
            keyboard.getKeyForPos(touchX, touchY)
        }
        if (key != null && key.isEnabled) {
            key.computedDataOnDown = key.computedData
            pointer.pressedKeyInfo = inputEventDispatcher.sendDown(
                data = key.computedData,
                onLongPress = onLongPress@ {
                    pointer.hasTriggeredLongPress = true
                    when (key.computedData.code) {
                        KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                            when (prefs.gestures.spaceBarLongPress.get()) {
                                SwipeAction.NO_ACTION,
                                SwipeAction.INSERT_SPACE -> {
                                }
                                else -> {
                                    keyboardManager.executeSwipeAction(prefs.gestures.spaceBarLongPress.get())
                                }
                            }
                            true
                        }
                        KeyCode.SHIFT -> {
                            if (inputEventDispatcher.isUninterruptedEventSequence(key.computedData)) {
                                inputEventDispatcher.sendDownUp(TextKeyData.CAPS_LOCK)
                                inputFeedbackController?.keyLongPress(key.computedData)
                            }
                            // We always return false here to prevent blockade for the up touch event
                            false
                        }
                        KeyCode.LANGUAGE_SWITCH -> {
                            inputEventDispatcher.sendDownUp(TextKeyData.SYSTEM_INPUT_METHOD_PICKER)
                            true
                        }
                        else -> {
                            if (popupUiController.isSuitableForPopups(key) && key.computedPopups.getPopupKeys(
                                    keyHintConfiguration
                                ).isNotEmpty()
                            ) {
                                popupUiController.extend(key, size)
                                inputFeedbackController?.keyLongPress(key.computedData)
                                true
                            } else {
                                false
                            }
                        }
                    }
                },
            )
            if (prefs.keyboard.popupEnabled.get() && popupUiController.isSuitableForPopups(key)) {
                popupUiController.show(key)
            }
            inputFeedbackController?.keyPress(key.computedData)
            key.isPressed = true
            if (pointer.initialKey == null) {
                pointer.initialKey = key
            }
            pointer.activeKey = key
            initSelectionStart = editorInstance.activeContent.selection.start
            initSelectionEnd = editorInstance.activeContent.selection.end
        } else {
            pointer.activeKey = null
        }
    }

    private fun onTouchMoveInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            if (popupUiController.isShowingExtendedPopup) {
                val x = event.getX(pointer.index)
                val y = event.getY(pointer.index)
                if (!popupUiController.propagateMotionEvent(activeKey, x, y)) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            } else {
                if ((event.getX(pointer.index) < activeKey.visibleBounds.left - 0.1f * activeKey.visibleBounds.width)
                    || (event.getX(pointer.index) > activeKey.visibleBounds.right + 0.1f * activeKey.visibleBounds.width)
                    || (event.getY(pointer.index) < activeKey.visibleBounds.top - 0.35f * activeKey.visibleBounds.height)
                    || (event.getY(pointer.index) > activeKey.visibleBounds.bottom + 0.35f * activeKey.visibleBounds.height)
                ) {
                    onTouchCancelInternal(event, pointer)
                    onTouchDownInternal(event, pointer)
                }
            }
        }
    }

    private fun onTouchUpInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val initialKey = pointer.initialKey
        val activeKey = pointer.activeKey
        if (initialKey != null && activeKey != null) {
            activeKey.isPressed = false
            if (popupUiController.isSuitableForPopups(activeKey)) {
                val retData = popupUiController.getActiveKeyData(activeKey)
                if (retData != null && !pointer.hasTriggeredGestureMove) {
                    if (retData == activeKey.computedData) {
                        if (activeKey.computedData != activeKey.computedDataOnDown) {
                            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                            inputEventDispatcher.sendDownUp(activeKey.computedData)
                        } else {
                            inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                        }
                    } else {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(retData)
                    }
                } else {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                }
                popupUiController.hide()
            } else {
                if (pointer.hasTriggeredGestureMove) {
                    inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                } else {
                    if (activeKey.computedData != activeKey.computedDataOnDown) {
                        inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
                        inputEventDispatcher.sendDownUp(activeKey.computedData)
                    } else {
                        inputEventDispatcher.sendUp(activeKey.computedDataOnDown)
                    }
                }
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    private fun onTouchCancelInternal(event: MotionEvent, pointer: TouchPointer) {
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW) { "pointer=$pointer" }
        pointer.pressedKeyInfo?.cancelJobs()
        pointer.pressedKeyInfo = null

        if (pointer.hasTriggeredMassSelection) {
            pointer.hasTriggeredMassSelection = false
            editorInstance.massSelection.end()
        }

        val activeKey = pointer.activeKey
        if (activeKey != null) {
            activeKey.isPressed = false
            inputEventDispatcher.sendCancel(activeKey.computedDataOnDown)
            if (popupUiController.isSuitableForPopups(activeKey)) {
                popupUiController.hide()
            }
            pointer.activeKey = null
        }
        pointer.hasTriggeredGestureMove = false
    }

    override fun onSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false
        val initialKey = pointer.initialKey ?: return false
        val activeKey = pointer.activeKey
        flogDebug(LogTopic.TEXT_KEYBOARD_VIEW)
        cancelGlideActive()

        return when (initialKey.computedData.code) {
            KeyCode.DELETE -> handleDeleteSwipe(event)
            KeyCode.SPACE, KeyCode.CJK_SPACE -> handleSpaceSwipe(event)
            else -> when {
                (initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.SPACE ||
                    initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.CJK_SPACE) &&
                    event.type == SwipeGesture.Type.TOUCH_MOVE -> handleSpaceSwipe(event)
                initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code != KeyCode.SHIFT &&
                    event.type == SwipeGesture.Type.TOUCH_UP -> {
                    activeKey?.let {
                        inputEventDispatcher.sendUp(popupUiController.getActiveKeyData(it) ?: it.computedDataOnDown)
                    }
                    inputEventDispatcher.sendCancel(TextKeyData.SHIFT)
                    true
                }
                initialKey.computedData.code > KeyCode.SPACE && !popupUiController.isShowingExtendedPopup -> when {
                    !pointer.hasTriggeredGestureMove -> when (event.type) {
                        SwipeGesture.Type.TOUCH_UP -> {
                            val isUpwardFlick = event.direction == SwipeGesture.Direction.UP ||
                                                event.direction == SwipeGesture.Direction.UP_LEFT ||
                                                event.direction == SwipeGesture.Direction.UP_RIGHT
                            if (isUpwardFlick && prefs.glide.flickPredictionsEnabled.get()) {
                                val charCode = initialKey.computedData.code.toChar().lowercaseChar()
                                val activeContent = editorInstance.activeContent
                                val textBefore = activeContent.textBeforeSelection.toString()
                                val composing = activeContent.composingText
                                val prefix = when {
                                    activeContent.composing.isValid && composing.isNotBlank() -> composing
                                    activeContent.localCurrentWord.isValid && activeContent.currentWordText.isNotBlank() -> activeContent.currentWordText
                                    else -> textBefore.takeLastWhile { it.isLetter() || it == '\'' }.toString()
                                }
                                val beforePrefix = if (prefix.isNotEmpty()) {
                                    textBefore.dropLast(prefix.length).trimEnd()
                                } else {
                                    textBefore.trimEnd()
                                }
                                val prevWord = beforePrefix.takeLastWhile { it.isLetter() || it == '\'' }
                                val predictions = if (org.florisboard.libnative.FlorisNative.isAvailable()) {
                                    org.florisboard.libnative.FlorisNative.predictNextLetterWords(prefix, prevWord)
                                } else {
                                    emptyMap()
                                }
                                val predictedWord = predictions[charCode]
                                if (predictedWord != null) {
                                    glideTypingDetector.cancel()
                                    isGliding = false
                                    activeCatapult = CatapultEffect(predictedWord, initialKey.visibleBounds.center)
                                    keyboardManager.commitFlickPrediction(predictedWord)
                                    inputFeedbackController?.flickCommit(initialKey.computedData)
                                    return true
                                }
                            }

                            val swipeAction = when (event.direction) {
                                SwipeGesture.Direction.UP -> prefs.gestures.swipeUp.get()
                                SwipeGesture.Direction.DOWN -> prefs.gestures.swipeDown.get()
                                SwipeGesture.Direction.LEFT -> prefs.gestures.swipeLeft.get()
                                SwipeGesture.Direction.RIGHT -> prefs.gestures.swipeRight.get()
                                else -> SwipeAction.NO_ACTION
                            }
                            if (swipeAction != SwipeAction.NO_ACTION) {
                                glideTypingDetector.cancel()
                                isGliding = false
                                keyboardManager.executeSwipeAction(swipeAction)
                                true
                            } else {
                                false
                            }
                        }
                        else -> false
                    }
                    else -> false
                }
                else -> false
            }
        }
    }

    private fun handleDeleteSwipe(event: SwipeGesture.Event): Boolean {
        if (editorInstance.activeInfo.isRawInputEditor) return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (prefs.gestures.deleteKeySwipeLeft.get()) {
                SwipeAction.DELETE_CHARACTERS_PRECISELY, SwipeAction.SELECT_CHARACTERS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX - 1,
                                unit = OperationUnit.CHARACTERS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                SwipeAction.DELETE_WORDS_PRECISELY, SwipeAction.SELECT_WORDS_PRECISELY -> {
                    if (abs(event.relUnitCountX) > 0) {
                        inputFeedbackController?.gestureMovingSwipe(TextKeyData.DELETE)
                    }
                    val activeSelection = editorInstance.activeContent.selection
                    if (activeSelection.isValid) {
                        if (!inputEventDispatcher.isPressed(KeyCode.SHIFT)) {
                            // Backward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.BEFORE_CURSOR,
                            )
                        } else {
                            // Forward select
                            editorInstance.setSelectionSurrounding(
                                n = -event.absUnitCountX / 2 - 1,
                                unit = OperationUnit.WORDS,
                                scope = OperationScope.AFTER_CURSOR,
                            )
                        }
                    }
                    true
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> {
                if (event.direction == SwipeGesture.Direction.LEFT &&
                    prefs.gestures.deleteKeySwipeLeft.get() == SwipeAction.DELETE_WORD
                ) {
                    keyboardManager.executeSwipeAction(prefs.gestures.deleteKeySwipeLeft.get())
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun handleSpaceSwipe(event: SwipeGesture.Event): Boolean {
        val pointer = pointerMap.findById(event.pointerId) ?: return false

        return when (event.type) {
            SwipeGesture.Type.TOUCH_MOVE -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    val action = prefs.gestures.spaceBarSwipeLeft.get()
                    if (action == SwipeAction.MOVE_CURSOR_LEFT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_LEFT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    val action = prefs.gestures.spaceBarSwipeRight.get()
                    if (action == SwipeAction.MOVE_CURSOR_RIGHT) {
                        abs(event.relUnitCountX).let {
                            val count = if (!pointer.hasTriggeredGestureMove) it - 1 else it
                            if (count > 0) {
                                inputFeedbackController?.gestureMovingSwipe(TextKeyData.SPACE)
                                if (!pointer.hasTriggeredMassSelection) {
                                    pointer.hasTriggeredMassSelection = true
                                    editorInstance.massSelection.begin()
                                }
                                keyboardManager.handleArrow(KeyCode.ARROW_RIGHT, count)
                            }
                        }
                        true
                    } else {
                        action != SwipeAction.NO_ACTION
                    }
                }
                else -> false
            }
            SwipeGesture.Type.TOUCH_UP -> when (event.direction) {
                SwipeGesture.Direction.LEFT -> {
                    prefs.gestures.spaceBarSwipeLeft.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_LEFT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                SwipeGesture.Direction.RIGHT -> {
                    prefs.gestures.spaceBarSwipeRight.get().let {
                        when {
                            it == SwipeAction.NO_ACTION -> {
                                false
                            }
                            it != SwipeAction.MOVE_CURSOR_RIGHT -> {
                                keyboardManager.executeSwipeAction(it)
                                true
                            }
                            else -> {
                                false
                            }
                        }
                    }
                }
                else -> {
                    if (event.absUnitCountY < -6) {
                        keyboardManager.executeSwipeAction(prefs.gestures.spaceBarSwipeUp.get())
                        true
                    } else {
                        false
                    }
                }
            }
        }
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        if (isGlideEnabled) {
            glideDataForDrawing.add(point to System.currentTimeMillis())
        }
    }

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        onGlideCancelled()
    }

    override fun onGlideCancelled() {
        if (prefs.glide.showTrail.get()) {
            fadingGlide.clear()
            fadingGlide.addAll(glideDataForDrawing)

            val animator = ValueAnimator.ofFloat(20.0f, 0.0f)
            animator.interpolator = AccelerateInterpolator()
            animator.duration = prefs.glide.trailDuration.get().toLong()
            animator.addUpdateListener {
                fadingGlideRadius = it.animatedValue as Float
            }
            animator.start()

            glideDataForDrawing.clear()
            isGliding = false
        }
    }

    fun drawGlideTrail(
        drawScope: ContentDrawScope,
        gestureData: MutableList<Pair<GlideTypingGesture.Detector.Position, Long>>,
        targetDist: Float,
        initialRadius: Float,
        radiusReductionFactor: Float,
        color: Color,
    ) {
        var radius = initialRadius
        var drawnPoints = 0
        var prevX = gestureData.lastOrNull()?.first?.x ?: 0.0f
        var prevY = gestureData.lastOrNull()?.first?.y ?: 0.0f
        val time = System.currentTimeMillis()
        val trailDuration = prefs.glide.trailDuration.get().coerceAtLeast(100)

        outer@ for (i in gestureData.size - 1 downTo 1) {
            val age = time - gestureData[i - 1].second
            if (age > trailDuration) break

            val alphaFactor = ((trailDuration - age).toFloat() / trailDuration).coerceIn(0f, 1f)
            val dx = prevX - gestureData[i - 1].first.x
            val dy = prevY - gestureData[i - 1].first.y
            val dist = sqrt(dx * dx + dy * dy)

            val numPoints = (dist / targetDist).toInt()
            for (j in 0 until numPoints) {
                radius *= radiusReductionFactor
                val intermediateX =
                    gestureData[i].first.x * (1 - j.toFloat() / numPoints) + gestureData[i - 1].first.x * (j.toFloat() / numPoints)
                val intermediateY =
                    gestureData[i].first.y * (1 - j.toFloat() / numPoints) + gestureData[i - 1].first.y * (j.toFloat() / numPoints)
                val centerOffset = Offset(intermediateX, intermediateY)

                // 1. Soft Outer Neon Glow Halo
                drawScope.drawCircle(
                    color = color.copy(alpha = 0.22f * alphaFactor),
                    radius = radius * 1.85f,
                    center = centerOffset,
                )
                // 2. Main Electric Cyan Ribbon Body
                drawScope.drawCircle(
                    color = color.copy(alpha = 0.85f * alphaFactor),
                    radius = radius,
                    center = centerOffset,
                )
                // 3. Specular White Center Core
                drawScope.drawCircle(
                    color = Color.White.copy(alpha = 0.65f * alphaFactor),
                    radius = radius * 0.38f,
                    center = centerOffset,
                )

                drawnPoints += 1
                prevX = intermediateX
                prevY = intermediateY
            }
        }
    }

    private class TouchPointer : Pointer() {
        var initialKey: TextKey? = null
        var activeKey: TextKey? = null
        var hasTriggeredGestureMove: Boolean = false
        var hasTriggeredLongPress: Boolean = false
        var hasTriggeredMassSelection: Boolean = false
        var pressedKeyInfo: InputEventDispatcher.PressedKeyInfo? = null

        override fun reset() {
            super.reset()
            initialKey = null
            activeKey = null
            hasTriggeredGestureMove = false
            hasTriggeredLongPress = false
            hasTriggeredMassSelection = false
            pressedKeyInfo = null
        }

        override fun toString(): String {
            return "${TouchPointer::class.simpleName} { id=$id, index=$index, initialKey=$initialKey, activeKey=$activeKey }"
        }
    }
}
