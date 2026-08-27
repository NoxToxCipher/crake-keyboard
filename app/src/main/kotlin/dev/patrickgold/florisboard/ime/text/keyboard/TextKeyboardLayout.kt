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
                if (glideEnabled && keyboard.mode == KeyboardMode.CHARACTERS) {
                    val keys = keyboard.keys().asSequence().toList()
                    glideTypingManager.setLayout(keys)
                }
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
        var mangoPulseTriggerTime by remember { mutableStateOf(0L) }
        var masterChiefRunTriggerTime by remember { mutableStateOf(0L) }
        var iceSkateSwirlTriggerTime by remember { mutableStateOf(0L) }
        var berriesFlowTriggerTime by remember { mutableStateOf(0L) }
        var tribalwarsTriggerTime by remember { mutableStateOf(0L) }
        var bawenCatTriggerTime by remember { mutableStateOf(0L) }
        var pubgParachuteTriggerTime by remember { mutableStateOf(0L) }
        var luciaBobaTriggerTime by remember { mutableStateOf(0L) }
        var dukuFruitTriggerTime by remember { mutableStateOf(0L) }
        var carDriveTriggerTime by remember { mutableStateOf(0L) }
        var cryptoRocketTriggerTime by remember { mutableStateOf(0L) }
        var murmurFlockTriggerTime by remember { mutableStateOf(0L) }
        var lunaCrashTriggerTime by remember { mutableStateOf(0L) }
        var sundaeTriggerTime by remember { mutableStateOf(0L) }
        var trainTriggerTime by remember { mutableStateOf(0L) }
        var isNobleTrainMode by remember { mutableStateOf(false) }
        var louiePawsTriggerTime by remember { mutableStateOf(0L) }
        var irobotTriggerTime by remember { mutableStateOf(0L) }
        var androidBugdroidTriggerTime by remember { mutableStateOf(0L) }
        var rosePetalsTriggerTime by remember { mutableStateOf(0L) }
        var xboxAchievementTriggerTime by remember { mutableStateOf(0L) }
        var hiddenHoodedTriggerTime by remember { mutableStateOf(0L) }
    var serenityGardenTriggerTime by remember { mutableStateOf(0L) }
    var sniperDudeTriggerTime by remember { mutableStateOf(0L) }
    var thorTriggerTime by remember { mutableStateOf(0L) }
    var mushuTriggerTime by remember { mutableStateOf(0L) }
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
            val rainKeys = listOf("rain", "rainy", "raining", "rainfall", "rainstorm")
            val isRainMatch = rainKeys.any { k ->
                val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && (d.isEmpty() || d == " "))
                }
            }
            if (isRainMatch) {
                spaceRainTriggerTime = System.currentTimeMillis()
            }
            val mangoKeys = listOf("mango", "mangoes", "mangos")
            if (mangoKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                mangoPulseTriggerTime = System.currentTimeMillis()
            }
            val chiefKeys = listOf("halo", "chief", "masterchief", "master chief", "117", "spartan", "cortana")
            val isChiefMatch = chiefKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isChiefMatch) {
                masterChiefRunTriggerTime = System.currentTimeMillis()
            }
            val skateKeys = listOf("rink", "skating", "iceskating", "ice skating", "skate", "figure skating")
            if (skateKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                iceSkateSwirlTriggerTime = System.currentTimeMillis()
            }
            val berryKeys = listOf("berry", "berries", "strawberry", "blueberry", "raspberry", "blackberry")
            if (berryKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
                berriesFlowTriggerTime = System.currentTimeMillis()
            }
            val fullTwKeys = listOf("tribalwars", "tribal wars", "tribal_wars")
            val shortTwKeys = listOf("tw")
            val isTwFullMatch = fullTwKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") }
            val isTwShortMatch = shortTwKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || tb == "$k$d"
                }
            }
            if (isTwFullMatch || isTwShortMatch) {
                tribalwarsTriggerTime = System.currentTimeMillis()
            }
            val bawenKeys = listOf("bawen")
            if (bawenKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") }) {
                bawenCatTriggerTime = System.currentTimeMillis()
            }
            val pubgKeys = listOf("pubg", "airdrop", "pochinki", "chicken dinner", "winner winner")
            if (pubgKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") }) {
                pubgParachuteTriggerTime = System.currentTimeMillis()
            }
            val luciaKeys = listOf("lucia")
            if (luciaKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") }) {
                luciaBobaTriggerTime = System.currentTimeMillis()
            }
            val dukuKeys = listOf("duku", "langsat", "longkong")
            if (dukuKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") }) {
                dukuFruitTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for car so compound words like 'cardboard', 'scar', 'sidecar' never trigger it!
            val carKeys = listOf("drive", "car", "driving", "cars", "driver", "drives", "drove", "aston martin", "aston")
            val isCarMatch = carKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isCarMatch) {
                carDriveTriggerTime = System.currentTimeMillis()
            }
            val cryptoKeys = listOf(
                "btc", "bitcoin", "eth", "ethereum", "sol", "solana",
                "arb", "arbitrum", "atom", "cosmos hub", "cosmos",
                "rune", "thorchain", "xmr", "monero", "ltc", "litecoin",
                "to the moon", "crypto"
            )
            val isCryptoMatch = cryptoKeys.any { k ->
                val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && (d.isEmpty() || d == " "))
                }
            }
            if (isCryptoMatch) {
                cryptoRocketTriggerTime = System.currentTimeMillis()
            }
            val murmurKeys = listOf("murmur", "flock", "murmuration", "starlings")
            if (murmurKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") }) {
                murmurFlockTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for LUNA/UST so words like 'just', 'must', 'dust', 'trust' never trigger it!
            val lunaKeys = listOf("terra", "luna", "ust", "lunc", "do kwon", "terra luna", "terra usd")
            val isLunaMatch = lunaKeys.any { k ->
                val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && (d.isEmpty() || d == " "))
                }
            }
            if (isLunaMatch) {
                lunaCrashTriggerTime = System.currentTimeMillis()
            }
            val sundaeKeys = listOf("sundae", "sundaes", "icecream", "ice cream", "gelato", "parfait")
            if (sundaeKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") }) {
                sundaeTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for train so words like 'training', 'trainer', 'strain', 'restrain' never trigger it!
            val nobleTrainKeys = listOf("noble train", "nobletrain", "noble_train", "sniping trains")
            val regularTrainKeys = listOf("train", "trains", "choo choo", "choochoo", "locomotive", "steam train")
            val isNobleTrainMatch = nobleTrainKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            val isRegularTrainMatch = regularTrainKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isNobleTrainMatch) {
                isNobleTrainMode = true
                trainTriggerTime = System.currentTimeMillis()
            } else if (isRegularTrainMatch) {
                isNobleTrainMode = false
                trainTriggerTime = System.currentTimeMillis()
            }
            val louieKeys = listOf("louie", "pitty", "pitbull", "red nose", "rednose", "red nose pitty")
            if (louieKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") }) {
                louiePawsTriggerTime = System.currentTimeMillis()
            }
            val fullAiKeys = listOf("artificial intelligence", "irobot", "i, robot", "ns5", "ns-5", "sonny", "viki", "three laws")
            val shortAiKeys = listOf("ai")
            val isAiFullMatch = fullAiKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") }
            val isAiShortMatch = shortAiKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || tb == "$k$d"
                }
            }
            if (isAiFullMatch || isAiShortMatch) {
                irobotTriggerTime = System.currentTimeMillis()
            }
            val androidKeys = listOf("android", "bugdroid", "green dude", "google android", "apk")
            if (androidKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") }) {
                androidBugdroidTriggerTime = System.currentTimeMillis()
            }
            val loveKeys = listOf("i love you", "iloveyou", "love you", "i <3 you", "i love u")
            if (loveKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") || tb.endsWith("$it!") || tb.endsWith("$it,") || tb.endsWith("$it?") || tb.endsWith("$it❤️") || tb.endsWith("$it🌹") }) {
                rosePetalsTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for Xbox
            val xboxKeys = listOf("xbox", "xbox 360", "series x", "series s", "xbox one", "game pass", "achievement unlocked", "gamertag", "majornelson")
            val isXboxMatch = xboxKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isXboxMatch) {
                xboxAchievementTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for 'hidden' (fires 8s later)
            val hiddenKeys = listOf("hidden", "assassin", "hooded figure", "ninja")
            val isHiddenMatch = hiddenKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isHiddenMatch) {
                hiddenHoodedTriggerTime = System.currentTimeMillis()
            }
            // Serenity garden trigger (literal keyword only, zero sentiment analysis)
            val serenityKeys = listOf("serenity", "zen garden")
            val isSerenityMatch = serenityKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isSerenityMatch) {
                serenityGardenTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for Sniper triggers
            val sniperKeys = listOf("snipe", "snipes", "sniper", "sniped", "sniping", "headshot", "360 noscope", "awp")
            val isSniperMatch = sniperKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                }
            }
            if (isSniperMatch) {
                sniperDudeTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for Thor (Case-insensitive, never triggers on Thorchain)
            val thorKeys = listOf("thor", "mjolnir", "god of thunder", "asgard", "odinson")
            val isThorMatch = thorKeys.any { k ->
                val delimiters = listOf(" ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb.equals("$k$d", ignoreCase = true) ||
                    tb.endsWith(" $k$d", ignoreCase = true) ||
                    tb.endsWith("\n$k$d", ignoreCase = true) ||
                    (comp.equals(k, ignoreCase = true) && d == " ")
                }
            }
            if (isThorMatch) {
                thorTriggerTime = System.currentTimeMillis()
            }
            // Strict word boundary isolation for Mushu the Dragon (Disney's Mulan)
            val mushuKeys = listOf("mushu", "mulan", "mulsn", "cri-kee", "dishonor on your cow", "dragon", "great stone dragon")
            val isMushuMatch = mushuKeys.any { k ->
                val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
                delimiters.any { d ->
                    tb.equals("$k$d", ignoreCase = true) ||
                    tb.endsWith(" $k$d", ignoreCase = true) ||
                    tb.endsWith("\n$k$d", ignoreCase = true) ||
                    (comp.equals(k, ignoreCase = true) && (d.isEmpty() || d == " "))
                }
            }
            if (isMushuMatch) {
                mushuTriggerTime = System.currentTimeMillis()
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

        // Mango Dual Elegant Pulse Easter Egg: Pulses twice softly in whisper-soft borderless honey mango glow
        if (mangoPulseTriggerTime > 0L) {
            val mangoProgress = remember(mangoPulseTriggerTime) { Animatable(0f) }
            LaunchedEffect(mangoPulseTriggerTime) {
                mangoProgress.snapTo(0f)
                mangoProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2800, easing = LinearEasing),
                )
                mangoPulseTriggerTime = 0L
            }
            if (mangoProgress.value in 0.001f..0.999f) {
                val t = mangoProgress.value
                val cycleProgress = if (t < 0.5f) t * 2.0f else (t - 0.5f) * 2.0f
                val sineWave = (kotlin.math.sin(cycleProgress * Math.PI)).toFloat()
                val pulseAlpha = sineWave * 0.12f // Whisper soft, delicate breathing radiance

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val cx = canvasW / 2f
                    val cy = canvasH / 2f

                    // Soft, Borderless, Organic Honey Mango Radial Aura (Zero hard lines or borders)
                    val maxDim = kotlin.math.max(canvasW, canvasH)
                    val mangoGradientPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = android.graphics.RadialGradient(
                            cx, cy, maxDim * 0.85f,
                            intArrayOf(
                                android.graphics.Color.argb((pulseAlpha * 255).toInt().coerceIn(0, 255), 255, 183, 3),   // Honey Gold
                                android.graphics.Color.argb((pulseAlpha * 160).toInt().coerceIn(0, 255), 251, 133, 0),  // Ripe Mango
                                android.graphics.Color.argb((pulseAlpha * 65).toInt().coerceIn(0, 255), 247, 127, 0),   // Warm Amber
                                android.graphics.Color.argb(0, 255, 183, 3)                                             // Pure 0% falloff
                            ),
                            floatArrayOf(0.0f, 0.40f, 0.70f, 1.0f),
                            android.graphics.Shader.TileMode.CLAMP
                        )
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, mangoGradientPaint)
                }
            }
        }

        // Mini Master Chief (Spartan-117) Bottom Fret Sprint Easter Egg
        if (masterChiefRunTriggerTime > 0L) {
            val chiefProgress = remember(masterChiefRunTriggerTime) { Animatable(0f) }
            LaunchedEffect(masterChiefRunTriggerTime) {
                chiefProgress.snapTo(0f)
                chiefProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2600, easing = LinearEasing),
                )
                masterChiefRunTriggerTime = 0L
            }
            if (chiefProgress.value in 0.001f..0.999f) {
                val t = chiefProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val w = this.size.width
                    val h = this.size.height
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val bottomFretY = h - (h / rowCount)

                    // Stride physics & vertical running bob
                    val stridePhase = t * 18f * Math.PI.toFloat()
                    val bobbing = kotlin.math.abs(kotlin.math.sin(stridePhase)) * 2f * density
                    val cx = (-35f * density) + t * (w + 70f * density)
                    val cy = bottomFretY - 14f * density + bobbing

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(cx, cy)
                    drawContext.canvas.nativeCanvas.rotate(6f) // Tactical sprint lean

                    // Paints for MJOLNIR Mark VI Powered Assault Armor
                    val armorPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF436B36.toInt() // Spartan Olive Drab
                        style = android.graphics.Paint.Style.FILL
                    }
                    val armorHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF5D8C4E.toInt() // Shoulder/Chest highlights
                        style = android.graphics.Paint.Style.FILL
                    }
                    val techsuitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF1C221D.toInt() // Dark slate under-suit
                        style = android.graphics.Paint.Style.FILL
                    }
                    val visorPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFB300.toInt() // Golden Amber Visor
                        style = android.graphics.Paint.Style.FILL
                    }
                    val visorShinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFF176.toInt() // Visor reflection glint
                        style = android.graphics.Paint.Style.FILL
                    }
                    val riflePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF262E35.toInt() // MA40 Assault Rifle
                        style = android.graphics.Paint.Style.FILL
                    }
                    val ammoCounterPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF00E5FF.toInt() // Cyan ammo readout
                        style = android.graphics.Paint.Style.FILL
                    }
                    val dustPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x66B0BEC5.toInt() // Sprint dust puff
                        style = android.graphics.Paint.Style.FILL
                    }

                    // 1. Sprint Dust Puffs behind boots
                    val legSwing = kotlin.math.sin(stridePhase)
                    if (kotlin.math.abs(legSwing) > 0.6f) {
                        drawContext.canvas.nativeCanvas.drawCircle(-8f * density, 11f * density, 2f * density, dustPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-12f * density, 12f * density, 1.4f * density, dustPaint)
                    }

                    // 2. Armored Legs (Dynamic Running Animation)
                    val leg1Angle = legSwing * 28f
                    val leg2Angle = -legSwing * 28f

                    // Back Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(0f, 3f * density)
                    drawContext.canvas.nativeCanvas.rotate(leg2Angle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.5f * density, 0f, 1.5f * density, 8f * density, 1f * density, 1f * density, techsuitPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.8f * density, 1f * density, 1.8f * density, 5.5f * density, 1f * density, 1f * density, armorPaint) // Thigh armor
                    drawContext.canvas.nativeCanvas.drawRoundRect(-2f * density, 6f * density, 2.5f * density, 9f * density, 1f * density, 1f * density, armorPaint) // Boot
                    drawContext.canvas.nativeCanvas.restore()

                    // 3. Torso & MJOLNIR Chestplate
                    drawContext.canvas.nativeCanvas.drawRoundRect(-3.5f * density, -4f * density, 3.5f * density, 4f * density, 2f * density, 2f * density, techsuitPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-4f * density, -5f * density, 4f * density, 1f * density, 1.5f * density, 1.5f * density, armorPaint) // Chestplate
                    drawContext.canvas.nativeCanvas.drawRoundRect(-3f * density, -4f * density, 3f * density, -0.5f * density, 1f * density, 1f * density, armorHighlightPaint)

                    // 4. Front Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(0f, 3f * density)
                    drawContext.canvas.nativeCanvas.rotate(leg1Angle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.5f * density, 0f, 1.5f * density, 8f * density, 1f * density, 1f * density, techsuitPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.8f * density, 1f * density, 1.8f * density, 5.5f * density, 1f * density, 1f * density, armorHighlightPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-2f * density, 6f * density, 2.5f * density, 9f * density, 1f * density, 1f * density, armorPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // 5. MA40 Assault Rifle held in forward tactical carry
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(1f * density, -1f * density)
                    drawContext.canvas.nativeCanvas.rotate(-10f)
                    // Rifle Body & Barrel
                    drawContext.canvas.nativeCanvas.drawRoundRect(-2f * density, -1f * density, 10f * density, 2f * density, 1f * density, 1f * density, riflePaint)
                    drawContext.canvas.nativeCanvas.drawRect(4f * density, -2f * density, 7f * density, -1f * density, riflePaint) // Scope cowl
                    drawContext.canvas.nativeCanvas.drawCircle(5.5f * density, -1.5f * density, 0.6f * density, ammoCounterPaint) // Cyan Display
                    drawContext.canvas.nativeCanvas.restore()

                    // 6. Armored Shoulders & Arms gripping rifle
                    drawContext.canvas.nativeCanvas.drawCircle(-2f * density, -2.5f * density, 2.2f * density, armorPaint) // Shoulder pauldron
                    drawContext.canvas.nativeCanvas.drawRoundRect(0f, -2f * density, 4.5f * density, 1.5f * density, 1f * density, 1f * density, armorHighlightPaint)

                    // 7. Master Chief Helmet & Golden Visor
                    drawContext.canvas.nativeCanvas.drawCircle(0f, -8f * density, 3.8f * density, armorPaint) // Helmet Dome
                    drawContext.canvas.nativeCanvas.drawRect(-3.5f * density, -10.5f * density, 2.5f * density, -8f * density, armorHighlightPaint) // Helmet Crest
                    // Golden Reflective Visor
                    val visorPath = android.graphics.Path().apply {
                        moveTo(0.5f * density, -9.2f * density)
                        lineTo(3.8f * density, -8.2f * density)
                        lineTo(3.4f * density, -6.6f * density)
                        lineTo(0.2f * density, -7.2f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(visorPath, visorPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(1.8f * density, -8f * density, 0.7f * density, visorShinePaint) // Visor reflection

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Figure Skating School Figures & Cursive Looping Swirl Easter Egg
        if (iceSkateSwirlTriggerTime > 0L) {
            val skateProgress = remember(iceSkateSwirlTriggerTime) { Animatable(0f) }
            LaunchedEffect(iceSkateSwirlTriggerTime) {
                skateProgress.snapTo(0f)
                skateProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
                )
                iceSkateSwirlTriggerTime = 0L
            }
            if (skateProgress.value in 0.001f..0.999f) {
                val t = skateProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Mathematical Parametric Model for Authentic Cursive Looping Figure Skating Tracing:
                    // Sweeping glide with 3 intricate self-crossing loops and spiral pirouettes
                    fun getSkatingPoint(u: Float): Pair<Float, Float> {
                        val baseX = (-30f * density) + u * (canvasW + 60f * density)
                        val baseY = canvasH * 0.48f +
                            (kotlin.math.sin(u * Math.PI.toFloat()) * canvasH * 0.18f) -
                            (kotlin.math.cos(u * 2f * Math.PI.toFloat()) * canvasH * 0.12f)

                        // 3 Intricate loops (Figure 8 / Cursive loops that self-intersect gracefully)
                        val loopPhase = u * 3.2f * 2f * Math.PI.toFloat()
                        val loopMod = kotlin.math.sin(u * Math.PI.toFloat()).coerceAtLeast(0f)
                        val radiusX = 26f * density * (0.3f + 0.9f * loopMod)
                        val radiusY = 40f * density * (0.3f + 0.9f * loopMod)

                        val px = baseX + kotlin.math.sin(loopPhase) * radiusX
                        val py = baseY + kotlin.math.cos(loopPhase) * radiusY
                        return Pair(px, py)
                    }

                    val steps = 140
                    val currentStep = (t * steps).toInt().coerceIn(2, steps)
                    val globalAlpha = if (t > 0.72f) (1f - (t - 0.72f) / 0.28f) else 1f

                    // 1. Double Blade Tracks (Inside Edge & Outside Edge)
                    val leftTrackPath = android.graphics.Path()
                    val rightTrackPath = android.graphics.Path()
                    val mistRibbonPath = android.graphics.Path()

                    val (initPx, initPy) = getSkatingPoint(0f)
                    val (nextPx, nextPy) = getSkatingPoint(0.01f)
                    val initDx = nextPx - initPx
                    val initDy = nextPy - initPy
                    val initLen = kotlin.math.sqrt(initDx * initDx + initDy * initDy).coerceAtLeast(0.001f)
                    val initNx = (-initDy / initLen) * (1.6f * density)
                    val initNy = (initDx / initLen) * (1.6f * density)

                    leftTrackPath.moveTo(initPx + initNx, initPy + initNy)
                    rightTrackPath.moveTo(initPx - initNx, initPy - initNy)
                    mistRibbonPath.moveTo(initPx, initPy)

                    for (i in 1..currentStep) {
                        val u = i.toFloat() / steps
                        val (px, py) = getSkatingPoint(u)
                        val (prevPx, prevPy) = getSkatingPoint((u - 0.008f).coerceAtLeast(0f))
                        val dx = px - prevPx
                        val dy = py - prevPy
                        val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
                        val nx = (-dy / len) * (1.6f * density)
                        val ny = (dx / len) * (1.6f * density)

                        leftTrackPath.lineTo(px + nx, py + ny)
                        rightTrackPath.lineTo(px - nx, py - ny)
                        mistRibbonPath.lineTo(px, py)
                    }

                    // A. Soft Cyan/Ice Ambient Glow Ribbon under the tracks
                    val mistPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((globalAlpha * 95).toInt().coerceIn(0, 255), 186, 230, 253) // Sky-100 ice mist
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 6.5f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    drawContext.canvas.nativeCanvas.drawPath(mistRibbonPath, mistPaint)

                    // B. Inside & Outside Dual Razor Carve Lines (Pure Silver Crystal)
                    val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((globalAlpha * 235).toInt().coerceIn(0, 255), 240, 249, 255)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.0f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftTrackPath, edgePaint)
                    drawContext.canvas.nativeCanvas.drawPath(rightTrackPath, edgePaint)

                    // 2. Leading Figure Skate Blade & Sparkling Frost Crystal Dust
                    val (headX, headY) = getSkatingPoint(t)

                    // Leading blade glint
                    val bladePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((globalAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(headX, headY, 2.6f * density, bladePaint)

                    // Floating Ice Shaving Sparkles (Spiral spray)
                    val sparklePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((globalAlpha * 210).toInt().coerceIn(0, 255), 224, 242, 254)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val sparkRadialOffsets = listOf(
                        Pair(-5f, -6f), Pair(-8f, 5f), Pair(-12f, -4f),
                        Pair(-16f, 7f), Pair(-20f, -6f), Pair(-24f, 4f),
                        Pair(-28f, -5f), Pair(-32f, 6f), Pair(-36f, -3f),
                        Pair(-40f, 4f)
                    )
                    for ((ox, oy) in sparkRadialOffsets) {
                        val spX = headX + ox * density
                        val spY = headY + oy * density
                        val spRadius = (0.9f + (kotlin.math.sin(t * 22f + ox).toFloat() * 0.55f).coerceAtLeast(0f)) * density
                        drawContext.canvas.nativeCanvas.drawCircle(spX, spY, spRadius, sparklePaint)
                    }

                    // 4-Point Diamond Crystal Glint at the leading blade tip
                    val diamondPath = android.graphics.Path().apply {
                        val s = 5.2f * density
                        moveTo(headX, headY - s)
                        quadTo(headX, headY, headX + s, headY)
                        quadTo(headX, headY, headX + s, headY)
                        quadTo(headX, headY, headX - s, headY)
                        quadTo(headX, headY, headX - s, headY)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(diamondPath, bladePaint)
                }
            }
        }

        // Berries Fret Inward-Fade & Fast Outward Flow Easter Egg
        if (berriesFlowTriggerTime > 0L) {
            val berryProgress = remember(berriesFlowTriggerTime) { Animatable(0f) }
            LaunchedEffect(berriesFlowTriggerTime) {
                berryProgress.snapTo(0f)
                berryProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
                )
                berriesFlowTriggerTime = 0L
            }
            if (berryProgress.value in 0.001f..0.999f) {
                val t = berryProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val cx = canvasW / 2f
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }

                    // Stage 1: 0.0 -> 0.28 (Soft fade into center with scale 0 -> 1)
                    // Stage 2: 0.28 -> 1.0 (Rapid outward flow left & right along fret frets)
                    val fadePhase = (t / 0.28f).coerceIn(0f, 1f)
                    val isFlowing = t >= 0.28f
                    val flowU = if (isFlowing) ((t - 0.28f) / 0.72f) else 0f
                    val flowDistance = (flowU * flowU) * (canvasW * 0.58f + 40f * density) // Exponential outward acceleration
                    val globalAlpha = (if (flowU > 0.7f) (1f - (flowU - 0.7f) / 0.3f) else 1f) * fadePhase
                    val scale = 0.3f + 0.7f * fadePhase

                    // Berry drawing helper functions
                    fun drawStrawberry(bx: Float, by: Float, bScale: Float, alpha: Float) {
                        val pRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 245).toInt().coerceIn(0, 255), 239, 68, 68)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pLeaf = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 34, 197, 94)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pSeed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 254, 240, 138)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val r = 5.5f * density * bScale
                        // Strawberry Body (Tapered oval)
                        val path = android.graphics.Path().apply {
                            moveTo(bx, by - r * 0.8f)
                            cubicTo(bx + r * 1.1f, by - r * 0.6f, bx + r * 0.9f, by + r * 0.8f, bx, by + r * 1.2f)
                            cubicTo(bx - r * 0.9f, by + r * 0.8f, bx - r * 1.1f, by - r * 0.6f, bx, by - r * 0.8f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(path, pRed)
                        // Calyx Leaf Crown
                        drawContext.canvas.nativeCanvas.drawCircle(bx, by - r * 0.9f, r * 0.38f, pLeaf)
                        drawContext.canvas.nativeCanvas.drawCircle(bx - r * 0.45f, by - r * 0.75f, r * 0.28f, pLeaf)
                        drawContext.canvas.nativeCanvas.drawCircle(bx + r * 0.45f, by - r * 0.75f, r * 0.28f, pLeaf)
                        // Seeds
                        drawContext.canvas.nativeCanvas.drawCircle(bx - r * 0.35f, by - r * 0.1f, r * 0.12f, pSeed)
                        drawContext.canvas.nativeCanvas.drawCircle(bx + r * 0.35f, by - r * 0.1f, r * 0.12f, pSeed)
                        drawContext.canvas.nativeCanvas.drawCircle(bx, by + r * 0.35f, r * 0.12f, pSeed)
                    }

                    fun drawBlueberry(bx: Float, by: Float, bScale: Float, alpha: Float) {
                        val pBlue = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 37, 99, 235) // Deep Sapphire
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pDarkCrown = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 250).toInt().coerceIn(0, 255), 30, 58, 138)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pGlint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val r = 4.8f * density * bScale
                        drawContext.canvas.nativeCanvas.drawCircle(bx, by, r, pBlue)
                        drawContext.canvas.nativeCanvas.drawCircle(bx, by - r * 0.4f, r * 0.32f, pDarkCrown)
                        drawContext.canvas.nativeCanvas.drawCircle(bx - r * 0.35f, by - r * 0.3f, r * 0.22f, pGlint)
                    }

                    fun drawRaspberry(bx: Float, by: Float, bScale: Float, alpha: Float) {
                        val pRasp = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 225, 29, 72) // Rose-600
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pShine = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 180).toInt().coerceIn(0, 255), 251, 113, 133)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val r = 2.2f * density * bScale
                        // Clustered drupelets
                        val drupeOffsets = listOf(
                            Pair(0f, -3f), Pair(-3f, -1f), Pair(3f, -1f),
                            Pair(-2f, 2f), Pair(2f, 2f), Pair(0f, 4f)
                        )
                        for ((dx, dy) in drupeOffsets) {
                            drawContext.canvas.nativeCanvas.drawCircle(bx + dx * density * bScale * 0.7f, by + dy * density * bScale * 0.7f, r, pRasp)
                            drawContext.canvas.nativeCanvas.drawCircle(bx + dx * density * bScale * 0.7f - 0.4f * density, by + dy * density * bScale * 0.7f - 0.4f * density, r * 0.35f, pShine)
                        }
                    }

                    fun drawBlackberry(bx: Float, by: Float, bScale: Float, alpha: Float) {
                        val pDark = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 245).toInt().coerceIn(0, 255), 88, 28, 135) // Deep Blackberry Purple
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pGlint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 192, 132, 252)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val r = 2.2f * density * bScale
                        val drupeOffsets = listOf(
                            Pair(0f, -3f), Pair(-3f, -1f), Pair(3f, -1f),
                            Pair(-2f, 2f), Pair(2f, 2f), Pair(0f, 4f)
                        )
                        for ((dx, dy) in drupeOffsets) {
                            drawContext.canvas.nativeCanvas.drawCircle(bx + dx * density * bScale * 0.7f, by + dy * density * bScale * 0.7f, r, pDark)
                            drawContext.canvas.nativeCanvas.drawCircle(bx + dx * density * bScale * 0.7f - 0.4f * density, by + dy * density * bScale * 0.7f - 0.4f * density, r * 0.32f, pGlint)
                        }
                    }

                    // For each fret line, draw berries streaming outward Left and Right
                    for ((fretIdx, fretY) in fretYs.withIndex()) {
                        val fretShift = if (fretIdx % 2 == 0) 1f else 0.85f
                        val streamOffsets = listOf(
                            Pair(0f * density, 0),
                            Pair(18f * density, 1),
                            Pair(36f * density, 2),
                            Pair(54f * density, 3)
                        )

                        for ((stOffset, berryType) in streamOffsets) {
                            val rightX = cx + (flowDistance * fretShift) + stOffset
                            val leftX = cx - (flowDistance * fretShift) - stOffset

                            // Right stream
                            when ((berryType + fretIdx) % 4) {
                                0 -> drawStrawberry(rightX, fretY, scale, globalAlpha)
                                1 -> drawBlueberry(rightX, fretY, scale, globalAlpha)
                                2 -> drawRaspberry(rightX, fretY, scale, globalAlpha)
                                else -> drawBlackberry(rightX, fretY, scale, globalAlpha)
                            }

                            // Left stream
                            when ((berryType + fretIdx + 2) % 4) {
                                0 -> drawStrawberry(leftX, fretY, scale, globalAlpha)
                                1 -> drawBlueberry(leftX, fretY, scale, globalAlpha)
                                2 -> drawRaspberry(leftX, fretY, scale, globalAlpha)
                                else -> drawBlackberry(leftX, fretY, scale, globalAlpha)
                            }
                        }
                    }
                }
            }
        }

        // Tribalwars Multi-Fret Phased Quotes Easter Egg
        if (tribalwarsTriggerTime > 0L) {
            val twProgress = remember(tribalwarsTriggerTime) { Animatable(0f) }
            LaunchedEffect(tribalwarsTriggerTime) {
                twProgress.snapTo(0f)
                twProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 12000, easing = LinearEasing),
                )
                tribalwarsTriggerTime = 0L
            }
            if (twProgress.value in 0.001f..0.999f) {
                val currentMs = twProgress.value * 12000f
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }

                    val fret1Y = fretYs.getOrNull(0) ?: (canvasH * 0.25f)
                    val fret2Y = fretYs.getOrNull(1) ?: (canvasH * 0.50f)
                    val fret3Y = fretYs.getOrNull(2) ?: (canvasH * 0.75f)

                    // Small, crisp medieval parchment gold text paint
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFEF08A.toInt() // Warm parchment gold
                        textSize = 9.8f * density
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        setShadowLayer(4f * density, 0f, 1f * density, 0xEE000000.toInt())
                    }

                    val finalPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFDE047.toInt() // Gleaming gold
                        textSize = 10.2f * density
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD_ITALIC)
                        setShadowLayer(5f * density, 0f, 1f * density, 0xFF000000.toInt())
                    }

                    // Fret 1: "Cat all their farms to level 1." (0ms -> 3800ms)
                    if (currentMs in 0f..3800f) {
                        val text = "Cat all their farms to level 1."
                        val textW = textPaint.measureText(text)
                        val u = (currentMs / 3800f).coerceIn(0f, 1f)
                        val alpha = (if (u < 0.15f) (u / 0.15f) else if (u > 0.85f) ((1f - u) / 0.15f) else 1f).coerceIn(0f, 1f)
                        val px = canvasW - u * (canvasW + textW + 30f * density)
                        val py = fret1Y - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        textPaint.alpha = (alpha * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText(text, px, py, textPaint)
                    }

                    // Fret 2: "7 villages before beginner protection is over." (2000ms -> 5800ms)
                    if (currentMs in 2000f..5800f) {
                        val text = "7 villages before beginner protection is over."
                        val textW = textPaint.measureText(text)
                        val u = ((currentMs - 2000f) / 3800f).coerceIn(0f, 1f)
                        val alpha = (if (u < 0.15f) (u / 0.15f) else if (u > 0.85f) ((1f - u) / 0.15f) else 1f).coerceIn(0f, 1f)
                        val px = canvasW - u * (canvasW + textW + 30f * density)
                        val py = fret2Y - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        textPaint.alpha = (alpha * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText(text, px, py, textPaint)
                    }

                    // Fret 3: "Sniping trains while at the urinal." (4000ms -> 7800ms)
                    if (currentMs in 4000f..7800f) {
                        val text = "Sniping trains while at the urinal."
                        val textW = textPaint.measureText(text)
                        val u = ((currentMs - 4000f) / 3800f).coerceIn(0f, 1f)
                        val alpha = (if (u < 0.15f) (u / 0.15f) else if (u > 0.85f) ((1f - u) / 0.15f) else 1f).coerceIn(0f, 1f)
                        val px = canvasW - u * (canvasW + textW + 30f * density)
                        val py = fret3Y - ((textPaint.descent() + textPaint.ascent()) / 2f)
                        textPaint.alpha = (alpha * 255).toInt()
                        drawContext.canvas.nativeCanvas.drawText(text, px, py, textPaint)
                    }

                    // All Three Frets: "No one understands ... Tribalwars is a way of life." (8000ms -> 12000ms)
                    if (currentMs in 8000f..12000f) {
                        val text = "No one understands ... Tribalwars is a way of life."
                        val textW = finalPaint.measureText(text)
                        val u = ((currentMs - 8000f) / 4000f).coerceIn(0f, 1f)
                        val alpha = (if (u < 0.12f) (u / 0.12f) else if (u > 0.88f) ((1f - u) / 0.12f) else 1f).coerceIn(0f, 1f)
                        val px = canvasW - u * (canvasW + textW + 30f * density)
                        finalPaint.alpha = (alpha * 255).toInt()

                        // Passes across all three frets simultaneously
                        val py1 = fret1Y - ((finalPaint.descent() + finalPaint.ascent()) / 2f)
                        val py2 = fret2Y - ((finalPaint.descent() + finalPaint.ascent()) / 2f)
                        val py3 = fret3Y - ((finalPaint.descent() + finalPaint.ascent()) / 2f)

                        drawContext.canvas.nativeCanvas.drawText(text, px, py1, finalPaint)
                        drawContext.canvas.nativeCanvas.drawText(text, px, py2, finalPaint)
                        drawContext.canvas.nativeCanvas.drawText(text, px, py3, finalPaint)
                    }
                }
            }
        }

        // Bawen Ginger & White Cat Face Easter Egg (1 second each on B -> A -> W -> E -> N)
        if (bawenCatTriggerTime > 0L) {
            val bawenProgress = remember(bawenCatTriggerTime) { Animatable(0f) }
            LaunchedEffect(bawenCatTriggerTime) {
                bawenProgress.snapTo(0f)
                bawenProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 5000, easing = LinearEasing),
                )
                bawenCatTriggerTime = 0L
            }
            if (bawenProgress.value in 0.001f..0.999f) {
                val currentMs = bawenProgress.value * 5000f
                val density = LocalDensity.current.density
                val letterSequence = listOf('b', 'a', 'w', 'e', 'n')
                val stepIdx = (currentMs / 1000f).toInt().coerceIn(0, 4)
                val targetChar = letterSequence[stepIdx]
                val stepU = ((currentMs - (stepIdx * 1000f)) / 1000f).coerceIn(0f, 1f)

                // Soft sine breathing fade in and out
                val alpha = (kotlin.math.sin(stepU * Math.PI.toFloat())).coerceIn(0f, 1f)
                val scale = 0.88f + 0.12f * alpha

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Find key center position for targetChar
                    var matchedKey: TextKey? = null
                    for (key in keyboard.keys()) {
                        if (key is TextKey) {
                            val code = key.computedData.code
                            val ch = code.toChar().lowercaseChar()
                            if (ch == targetChar || key.computedData.asString(true).equals(targetChar.toString(), ignoreCase = true)) {
                                matchedKey = key
                                break
                            }
                        }
                    }

                    val keyCenterX = if (matchedKey != null) {
                        matchedKey.visibleBounds.left + (matchedKey.visibleBounds.width / 2f)
                    } else {
                        canvasW * (0.2f + stepIdx * 0.15f)
                    }
                    val keyCenterY = if (matchedKey != null) {
                        matchedKey.visibleBounds.top + (matchedKey.visibleBounds.height / 2f)
                    } else {
                        canvasH * 0.5f
                    }

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(keyCenterX, keyCenterY)
                    drawContext.canvas.nativeCanvas.scale(scale, scale)

                    val r = 16.5f * density

                    // 1. Soft Ambient Cat Aura
                    val auraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 110).toInt().coerceIn(0, 255), 254, 215, 170) // Soft warm peach
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, r * 1.25f, auraPaint)

                    // Paints for Ginger & White Cat
                    val whitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val gingerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 249, 115, 22) // Classic Marmalade Orange
                        style = android.graphics.Paint.Style.FILL
                    }
                    val darkGingerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 217, 83, 4) // Ginger tabby stripes
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.2f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    val innerEarPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 251, 113, 133) // Soft Pink
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyeGreenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 16, 185, 129) // Emerald Green Eyes
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyePupilPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 15, 23, 42)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val glintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val nosePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 251, 113, 133)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val mouthPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 71, 85, 105)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.1f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    val whiskerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 148, 163, 184)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.9f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }

                    // 2. Ears (Left Ear White, Right Ear Ginger)
                    // Left Ear (White)
                    val leftEar = android.graphics.Path().apply {
                        moveTo(-r * 0.82f, -r * 0.3f)
                        lineTo(-r * 0.88f, -r * 1.15f)
                        lineTo(-r * 0.25f, -r * 0.82f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftEar, whitePaint)
                    // Left Inner Ear (Pink)
                    val leftInnerEar = android.graphics.Path().apply {
                        moveTo(-r * 0.76f, -r * 0.4f)
                        lineTo(-r * 0.80f, -r * 0.98f)
                        lineTo(-r * 0.35f, -r * 0.76f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftInnerEar, innerEarPaint)

                    // Right Ear (Ginger / Marmalade Orange)
                    val rightEar = android.graphics.Path().apply {
                        moveTo(r * 0.25f, -r * 0.82f)
                        lineTo(r * 0.88f, -r * 1.15f)
                        lineTo(r * 0.82f, -r * 0.3f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(rightEar, gingerPaint)
                    // Right Inner Ear (Pink)
                    val rightInnerEar = android.graphics.Path().apply {
                        moveTo(r * 0.35f, -r * 0.76f)
                        lineTo(r * 0.80f, -r * 0.98f)
                        lineTo(r * 0.76f, -r * 0.4f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(rightInnerEar, innerEarPaint)

                    // 3. Head Base (Fluffy White Round Face)
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, r, whitePaint)

                    // 4. Ginger Patches on Face (Right Forehead & Cheek Patch)
                    val gingerPatch = android.graphics.Path().apply {
                        moveTo(0f, -r)
                        cubicTo(r * 0.6f, -r * 0.9f, r, -r * 0.3f, r, 0.1f * r)
                        cubicTo(r * 0.8f, 0.5f * r, r * 0.3f, 0.2f * r, 0.15f * r, -0.1f * r)
                        cubicTo(0.05f * r, -0.4f * r, 0f, -0.7f * r, 0f, -r)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(gingerPatch, gingerPaint)

                    // Tabby Forehead Stripes
                    drawContext.canvas.nativeCanvas.drawLine(r * 0.35f, -r * 0.75f, r * 0.45f, -r * 0.5f, darkGingerPaint)
                    drawContext.canvas.nativeCanvas.drawLine(r * 0.55f, -r * 0.65f, r * 0.62f, -r * 0.4f, darkGingerPaint)

                    // 5. Big Emerald Cat Eyes with Sparkles
                    val eyeW = r * 0.32f
                    val eyeH = r * 0.38f
                    val eyeLeftX = -r * 0.42f
                    val eyeRightX = r * 0.42f
                    val eyeY = -r * 0.12f

                    // Left Eye
                    drawContext.canvas.nativeCanvas.drawOval(eyeLeftX - eyeW / 2f, eyeY - eyeH / 2f, eyeLeftX + eyeW / 2f, eyeY + eyeH / 2f, eyeGreenPaint)
                    drawContext.canvas.nativeCanvas.drawOval(eyeLeftX - eyeW * 0.25f, eyeY - eyeH * 0.42f, eyeLeftX + eyeW * 0.25f, eyeY + eyeH * 0.42f, eyePupilPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(eyeLeftX - eyeW * 0.15f, eyeY - eyeH * 0.2f, eyeW * 0.22f, glintPaint)

                    // Right Eye
                    drawContext.canvas.nativeCanvas.drawOval(eyeRightX - eyeW / 2f, eyeY - eyeH / 2f, eyeRightX + eyeW / 2f, eyeY + eyeH / 2f, eyeGreenPaint)
                    drawContext.canvas.nativeCanvas.drawOval(eyeRightX - eyeW * 0.25f, eyeY - eyeH * 0.42f, eyeRightX + eyeW * 0.25f, eyeY + eyeH * 0.42f, eyePupilPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(eyeRightX - eyeW * 0.15f, eyeY - eyeH * 0.2f, eyeW * 0.22f, glintPaint)

                    // 6. Pink Button Nose & ':3' Smile
                    val noseY = r * 0.22f
                    val nosePath = android.graphics.Path().apply {
                        moveTo(0f, noseY + r * 0.12f)
                        lineTo(-r * 0.14f, noseY - r * 0.08f)
                        lineTo(r * 0.14f, noseY - r * 0.08f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(nosePath, nosePaint)

                    // ':3' Cat Smile Mouth
                    val mouthY = noseY + r * 0.12f
                    val leftMouth = android.graphics.Path().apply {
                        moveTo(0f, mouthY)
                        cubicTo(-r * 0.12f, mouthY + r * 0.18f, -r * 0.25f, mouthY + r * 0.14f, -r * 0.32f, mouthY + r * 0.05f)
                    }
                    val rightMouth = android.graphics.Path().apply {
                        moveTo(0f, mouthY)
                        cubicTo(r * 0.12f, mouthY + r * 0.18f, r * 0.25f, mouthY + r * 0.14f, r * 0.32f, mouthY + r * 0.05f)
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftMouth, mouthPaint)
                    drawContext.canvas.nativeCanvas.drawPath(rightMouth, mouthPaint)

                    // 7. Whiskers (3 on each cheek)
                    // Left Whiskers
                    drawContext.canvas.nativeCanvas.drawLine(-r * 0.4f, r * 0.22f, -r * 1.25f, r * 0.08f, whiskerPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-r * 0.4f, r * 0.32f, -r * 1.30f, r * 0.32f, whiskerPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-r * 0.4f, r * 0.42f, -r * 1.22f, r * 0.54f, whiskerPaint)
                    // Right Whiskers
                    drawContext.canvas.nativeCanvas.drawLine(r * 0.4f, r * 0.22f, r * 1.25f, r * 0.08f, whiskerPaint)
                    drawContext.canvas.nativeCanvas.drawLine(r * 0.4f, r * 0.32f, r * 1.30f, r * 0.32f, whiskerPaint)
                    drawContext.canvas.nativeCanvas.drawLine(r * 0.4f, r * 0.42f, r * 1.22f, r * 0.54f, whiskerPaint)

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // PUBG Paratrooper Flying Down Top-Right to Bottom-Left Easter Egg
        if (pubgParachuteTriggerTime > 0L) {
            val pubgProgress = remember(pubgParachuteTriggerTime) { Animatable(0f) }
            LaunchedEffect(pubgParachuteTriggerTime) {
                pubgProgress.snapTo(0f)
                pubgProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3200, easing = LinearEasing),
                )
                pubgParachuteTriggerTime = 0L
            }
            if (pubgProgress.value in 0.001f..0.999f) {
                val t = pubgProgress.value
                val density = LocalDensity.current.density
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Diagonal path: Top-Right (canvasW + 35dp, -35dp) -> Bottom-Left (-45dp, canvasH + 45dp)
                    val startX = canvasW + 35f * density
                    val startY = -35f * density
                    val endX = -45f * density
                    val endY = canvasH + 45f * density

                    val posX = startX + t * (endX - startX)
                    val posY = startY + t * (endY - startY)

                    // Aerodynamic wind sway
                    val swayAngle = (kotlin.math.sin(t * 6f * Math.PI.toFloat()) * 9f) - 6f // Tilts naturally facing movement direction

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(posX, posY)
                    drawContext.canvas.nativeCanvas.rotate(swayAngle)

                    // 1. Red Airdrop Flare Smoke Puffs trailing behind canopy
                    val smokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x55EF4444.toInt() // Red smoke flare
                        style = android.graphics.Paint.Style.FILL
                    }
                    val smokeOffsets = listOf(
                        Pair(12f * density, -16f * density),
                        Pair(20f * density, -24f * density),
                        Pair(28f * density, -32f * density)
                    )
                    for ((sx, sy) in smokeOffsets) {
                        drawContext.canvas.nativeCanvas.drawCircle(sx, sy, 4f * density, smokePaint)
                    }

                    // 2. Parachute Canopy (Military Camo Green & Yellow Warning Stripe)
                    val canopyGreenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF15803D.toInt() // Military Olive
                        style = android.graphics.Paint.Style.FILL
                    }
                    val canopyDarkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF166534.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val canopyYellowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFEAB308.toInt() // Warning Yellow Stripe
                        style = android.graphics.Paint.Style.FILL
                    }
                    val canopyLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0F172A.toInt() // Rib lines
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1f * density
                    }

                    val cW = 20f * density
                    val cH = 12f * density
                    val cTopY = -22f * density

                    // Parachute Dome
                    val canopyPath = android.graphics.Path().apply {
                        moveTo(-cW, cTopY + cH)
                        cubicTo(-cW, cTopY - cH * 0.4f, cW, cTopY - cH * 0.4f, cW, cTopY + cH)
                        cubicTo(cW * 0.6f, cTopY + cH * 0.7f, -cW * 0.6f, cTopY + cH * 0.7f, -cW, cTopY + cH)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(canopyPath, canopyGreenPaint)
                    drawContext.canvas.nativeCanvas.drawRect(-cW * 0.28f, cTopY, cW * 0.28f, cTopY + cH * 0.85f, canopyYellowPaint)
                    drawContext.canvas.nativeCanvas.drawPath(canopyPath, canopyLinePaint)

                    // 3. Parachute Suspension Cord Lines
                    val cordPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFCBD5E1.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val harnessX = 0f
                    val harnessY = -4f * density
                    drawContext.canvas.nativeCanvas.drawLine(-cW * 0.95f, cTopY + cH, harnessX, harnessY, cordPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-cW * 0.35f, cTopY + cH * 0.8f, harnessX, harnessY, cordPaint)
                    drawContext.canvas.nativeCanvas.drawLine(cW * 0.35f, cTopY + cH * 0.8f, harnessX, harnessY, cordPaint)
                    drawContext.canvas.nativeCanvas.drawLine(cW * 0.95f, cTopY + cH, harnessX, harnessY, cordPaint)

                    // 4. Survivor Dude (Level 3 Spetsnaz Helmet + White Shirt & Tie + Pan)
                    val shirtPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val tiePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0F172A.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val jeansPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF1D4ED8.toInt() // Blue Denim
                        style = android.graphics.Paint.Style.FILL
                    }
                    val bootPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF334155.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val panPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0F172A.toInt() // Cast Iron Frying Pan
                        style = android.graphics.Paint.Style.FILL
                    }
                    val panRimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF94A3B8.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val helmetPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF1E293B.toInt() // Level 3 Helmet
                        style = android.graphics.Paint.Style.FILL
                    }
                    val visorSlitPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF64748B.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    // A. Torso (White Shirt) & Harness
                    drawContext.canvas.nativeCanvas.drawRoundRect(-2.8f * density, -4f * density, 2.8f * density, 4f * density, 1.2f * density, 1.2f * density, shirtPaint)
                    drawContext.canvas.nativeCanvas.drawRect(-0.6f * density, -3.5f * density, 0.6f * density, 1f * density, tiePaint) // Black Tie

                    // B. Cast Iron Frying Pan on Back / Buttocks
                    drawContext.canvas.nativeCanvas.drawCircle(3.2f * density, 2f * density, 3f * density, panPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(3.2f * density, 2f * density, 3f * density, panRimPaint)
                    drawContext.canvas.nativeCanvas.drawRect(5.5f * density, 1.2f * density, 7.8f * density, 2.8f * density, panPaint) // Pan handle

                    // C. Dangling Legs (Blue Jeans & Combat Boots)
                    val legFlutter = (kotlin.math.sin(t * 12f * Math.PI.toFloat()) * 3f)
                    // Left Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(-1.4f * density, 3.5f * density)
                    drawContext.canvas.nativeCanvas.rotate(-6f + legFlutter)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.2f * density, 0f, 1.2f * density, 6.5f * density, 1f * density, 1f * density, jeansPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.4f * density, 5.5f * density, 1.8f * density, 8f * density, 1f * density, 1f * density, bootPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // Right Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(1.4f * density, 3.5f * density)
                    drawContext.canvas.nativeCanvas.rotate(8f - legFlutter)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.2f * density, 0f, 1.2f * density, 6.5f * density, 1f * density, 1f * density, jeansPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(-1.4f * density, 5.5f * density, 1.8f * density, 8f * density, 1f * density, 1f * density, bootPaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // D. Arms gripping harness cords
                    drawContext.canvas.nativeCanvas.drawRoundRect(-3.5f * density, -3.5f * density, -1.8f * density, 1f * density, 1f * density, 1f * density, shirtPaint)
                    drawContext.canvas.nativeCanvas.drawRoundRect(1.8f * density, -3.5f * density, 3.5f * density, 1f * density, 1f * density, 1f * density, shirtPaint)

                    // E. Level 3 Spetsnaz Helmet Head
                    drawContext.canvas.nativeCanvas.drawCircle(0f, -7.5f * density, 3.8f * density, helmetPaint)
                    drawContext.canvas.nativeCanvas.drawRect(-3f * density, -7.8f * density, 1f * density, -6.6f * density, visorSlitPaint) // Visor Face Slit

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Lucia Boba Bubble Tea on Shift Key Easter Egg (10 Seconds Soft Fade & Floating)
        if (luciaBobaTriggerTime > 0L) {
            val bobaProgress = remember(luciaBobaTriggerTime) { Animatable(0f) }
            LaunchedEffect(luciaBobaTriggerTime) {
                bobaProgress.snapTo(0f)
                bobaProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 10000, easing = LinearEasing),
                )
                luciaBobaTriggerTime = 0L
            }
            if (bobaProgress.value in 0.001f..0.999f) {
                val u = bobaProgress.value
                val density = LocalDensity.current.density

                // 10s timeline: Fade in (0.0 -> 0.08), Hold (0.08 -> 0.92), Fade out (0.92 -> 1.0)
                val alpha = (when {
                    u < 0.08f -> u / 0.08f
                    u > 0.92f -> (1f - u) / 0.08f
                    else -> 1f
                }).coerceIn(0f, 1f)

                val breathe = kotlin.math.sin(u * 14f * Math.PI.toFloat()) * 1.5f * density
                val scale = 0.92f + 0.08f * (kotlin.math.sin(u * 10f * Math.PI.toFloat()) * 0.5f + 0.5f)

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Locate Shift key center
                    var shiftKey: TextKey? = null
                    for (key in keyboard.keys()) {
                        if (key is TextKey && key.computedData.code == KeyCode.SHIFT) {
                            shiftKey = key
                            break
                        }
                    }

                    val keyCenterX = if (shiftKey != null) {
                        shiftKey.visibleBounds.left + (shiftKey.visibleBounds.width / 2f)
                    } else {
                        canvasW * 0.12f
                    }
                    val keyCenterY = if (shiftKey != null) {
                        shiftKey.visibleBounds.top + (shiftKey.visibleBounds.height / 2f)
                    } else {
                        canvasH * 0.72f
                    }

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(keyCenterX, keyCenterY + breathe)
                    drawContext.canvas.nativeCanvas.scale(scale, scale)

                    // 1. Soft Ambient Kawaii Aura Glow
                    val auraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 120).toInt().coerceIn(0, 255), 244, 114, 182) // Pink glow
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, 18f * density, auraPaint)

                    val cupW = 13f * density
                    val cupH = 19f * density
                    val cupTop = -cupH * 0.42f
                    val cupBottom = cupTop + cupH

                    // 2. Boba Wide Straw (Pastel Pink)
                    val strawPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 236, 72, 153)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.4f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    drawContext.canvas.nativeCanvas.drawLine(2f * density, cupTop + 2f * density, 6.5f * density, cupTop - 8f * density, strawPaint)

                    // 3. Clear Tapered Boba Cup Body & Liquid Fill (Creamy Taro Lavender)
                    val liquidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 216, 180, 254) // Lavender Taro Milk Tea
                        style = android.graphics.Paint.Style.FILL
                    }
                    val cupOutlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.2f * density
                    }

                    val cupPath = android.graphics.Path().apply {
                        moveTo(-cupW * 0.5f, cupTop + 2f * density)
                        lineTo(-cupW * 0.4f, cupBottom - 2f * density)
                        quadTo(-cupW * 0.4f, cupBottom, -cupW * 0.25f, cupBottom)
                        lineTo(cupW * 0.25f, cupBottom)
                        quadTo(cupW * 0.4f, cupBottom, cupW * 0.4f, cupBottom - 2f * density)
                        lineTo(cupW * 0.5f, cupTop + 2f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(cupPath, liquidPaint)
                    drawContext.canvas.nativeCanvas.drawPath(cupPath, cupOutlinePaint)

                    // 4. Chewy Black Tapioca Boba Pearls
                    val bobaPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 30, 27, 75) // Dark Tapioca
                        style = android.graphics.Paint.Style.FILL
                    }
                    val bobaShine = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 220).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val pearlOffsets = listOf(
                        Pair(-3.2f, cupBottom - 2.8f * density),
                        Pair(0f, cupBottom - 2.5f * density),
                        Pair(3.2f, cupBottom - 2.8f * density),
                        Pair(-1.8f, cupBottom - 5.2f * density),
                        Pair(1.8f, cupBottom - 5.2f * density)
                    )
                    for ((px, py) in pearlOffsets) {
                        drawContext.canvas.nativeCanvas.drawCircle(px * density, py, 1.4f * density, bobaPaint)
                        drawContext.canvas.nativeCanvas.drawCircle((px - 0.4f) * density, py - 0.4f * density, 0.4f * density, bobaShine)
                    }

                    // 5. Cup Dome Lid & Rim
                    val lidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.2f * density
                    }
                    val lidFillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 160).toInt().coerceIn(0, 255), 241, 245, 249)
                        style = android.graphics.Paint.Style.FILL
                    }
                    // Dome Arch
                    drawContext.canvas.nativeCanvas.drawArc(
                        -cupW * 0.52f, cupTop - 4.5f * density,
                        cupW * 0.52f, cupTop + 2.5f * density,
                        180f, 180f, true, lidFillPaint
                    )
                    drawContext.canvas.nativeCanvas.drawArc(
                        -cupW * 0.52f, cupTop - 4.5f * density,
                        cupW * 0.52f, cupTop + 2.5f * density,
                        180f, 180f, false, lidPaint
                    )
                    // Sealing Collar
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -cupW * 0.55f, cupTop + 1f * density,
                        cupW * 0.55f, cupTop + 3.2f * density,
                        1f * density, 1f * density, lidPaint
                    )

                    // 6. Kawaii Cheeks & Closed Eyes on the Cup
                    val faceY = cupTop + cupH * 0.45f
                    val blushPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 230).toInt().coerceIn(0, 255), 251, 113, 133)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyeLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 71, 85, 105)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.9f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    // Blushing cheeks
                    drawContext.canvas.nativeCanvas.drawCircle(-3.2f * density, faceY + 1f * density, 1.1f * density, blushPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(3.2f * density, faceY + 1f * density, 1.1f * density, blushPaint)
                    // Happy closed eyes (^ ^)
                    drawContext.canvas.nativeCanvas.drawArc(
                        -3.6f * density, faceY - 1.5f * density,
                        -1.4f * density, faceY + 0.5f * density,
                        180f, 180f, false, eyeLinePaint
                    )
                    drawContext.canvas.nativeCanvas.drawArc(
                        1.4f * density, faceY - 1.5f * density,
                        3.6f * density, faceY + 0.5f * density,
                        180f, 180f, false, eyeLinePaint
                    )

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Duku Rare Fruit Dynamic Peeling & Blooming Pearl Arils Easter Egg
        if (dukuFruitTriggerTime > 0L) {
            val dukuProgress = remember(dukuFruitTriggerTime) { Animatable(0f) }
            LaunchedEffect(dukuFruitTriggerTime) {
                dukuProgress.snapTo(0f)
                dukuProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 6500, easing = LinearEasing),
                )
                dukuFruitTriggerTime = 0L
            }
            if (dukuProgress.value in 0.001f..0.999f) {
                val u = dukuProgress.value
                val currentMs = u * 6500f
                val density = LocalDensity.current.density

                // 6.5s lifecycle: Soft fade-in (0.0 -> 0.08), Hold (0.08 -> 0.90), Soft fade-out (0.90 -> 1.0)
                val alpha = (when {
                    u < 0.08f -> u / 0.08f
                    u > 0.90f -> (1f - u) / 0.10f
                    else -> 1f
                }).coerceIn(0f, 1f)

                // Pendulum swing on entry
                val swingAngle = if (currentMs < 1400f) {
                    val sU = (currentMs / 1400f)
                    kotlin.math.sin(sU * 3f * Math.PI.toFloat()) * 9f * (1f - sU)
                } else {
                    kotlin.math.sin(u * 6f * Math.PI.toFloat()) * 1.5f
                }
                val floatY = kotlin.math.sin(u * 8f * Math.PI.toFloat()) * 2f * density
                val scale = 0.95f + 0.05f * (kotlin.math.sin(u * 6f * Math.PI.toFloat()) * 0.5f + 0.5f)

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Position centered above Spacebar
                    val centerX = canvasW * 0.5f
                    val centerY = canvasH * 0.70f

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(centerX, centerY + floatY)
                    drawContext.canvas.nativeCanvas.rotate(swingAngle)
                    drawContext.canvas.nativeCanvas.scale(scale, scale)

                    // 1. Warm Golden Tropical Aura
                    val auraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 120).toInt().coerceIn(0, 255), 254, 240, 138)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, 42f * density, auraPaint)

                    // Paints for Duku Fruit
                    val dukuSkinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 234, 179, 8) // Sandy Golden-Fawn Buff
                        style = android.graphics.Paint.Style.FILL
                    }
                    val dukuInnerSkinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 254, 224, 71) // Pale inner rind
                        style = android.graphics.Paint.Style.FILL
                    }
                    val dukuShadePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 202, 138, 4) // Warm amber shade
                        style = android.graphics.Paint.Style.FILL
                    }
                    val dukuSpecklePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 180).toInt().coerceIn(0, 255), 180, 83, 9)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val twigPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 120, 53, 15) // Woody branch
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.5f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    val leafPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 22, 163, 74) // Emerald Leaf
                        style = android.graphics.Paint.Style.FILL
                    }
                    val leafVeinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 134, 239, 172)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val translucentArilPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 248, 250, 252) // Pearl-White Arils
                        style = android.graphics.Paint.Style.FILL
                    }
                    val arilGlowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 254, 243, 199) // Sweet translucent glow
                        style = android.graphics.Paint.Style.FILL
                    }
                    val arilOutlinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 160).toInt().coerceIn(0, 255), 203, 213, 225)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val glintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val seedShadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 90).toInt().coerceIn(0, 255), 63, 98, 18)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val sparklePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 254, 240, 138)
                        style = android.graphics.Paint.Style.FILL
                    }

                    // 2. Woody Twig & Emerald Leaves
                    drawContext.canvas.nativeCanvas.drawLine(-18f * density, -16f * density, 18f * density, -8f * density, twigPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-8f * density, -12f * density, -16f * density, 0f, twigPaint)
                    drawContext.canvas.nativeCanvas.drawLine(6f * density, -10f * density, 14f * density, 2f, twigPaint)

                    // Tropical Leaf (Left)
                    val leftLeaf = android.graphics.Path().apply {
                        moveTo(-14f * density, -15f * density)
                        quadTo(-28f * density, -26f * density, -36f * density, -20f * density)
                        quadTo(-24f * density, -10f * density, -14f * density, -15f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftLeaf, leafPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-14f * density, -15f * density, -34f * density, -20f * density, leafVeinPaint)

                    // 3. Sister Duku Fruit #1 (Left Whole Golden Globe)
                    val r1 = 11f * density
                    val cx1 = -16f * density
                    val cy1 = 2f * density
                    drawContext.canvas.nativeCanvas.drawCircle(cx1, cy1, r1, dukuSkinPaint)
                    drawContext.canvas.nativeCanvas.drawArc(cx1 - r1, cy1 - r1, cx1 + r1, cy1 + r1, 45f, 180f, false, dukuShadePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(cx1 - 3f * density, cy1 - 2f * density, 0.6f * density, dukuSpecklePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(cx1 + 2f * density, cy1 + 3f * density, 0.7f * density, dukuSpecklePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(cx1, cy1 - r1 * 0.85f, 1.4f * density, twigPaint)

                    // 4. Sister Duku Fruit #2 (Top Right Whole Golden Globe)
                    val r2 = 9.5f * density
                    val cx2 = 12f * density
                    val cy2 = -4f * density
                    drawContext.canvas.nativeCanvas.drawCircle(cx2, cy2, r2, dukuSkinPaint)
                    drawContext.canvas.nativeCanvas.drawArc(cx2 - r2, cy2 - r2, cx2 + r2, cy2 + r2, 45f, 180f, false, dukuShadePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(cx2 - 2f * density, cy2 + 2f * density, 0.6f * density, dukuSpecklePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(cx2, cy2 - r2 * 0.85f, 1.2f * density, twigPaint)

                    // 5. Dynamic Center Duku: Active Peeling & Blooming Translucent Pearl Arils!
                    val r3 = 13.5f * density
                    val cx3 = 3f * density
                    val cy3 = 10f * density

                    // Peeling Progress (0.0 before 1000ms, peels open 1000ms -> 2400ms, stays open)
                    val peelProgress = if (currentMs < 1000f) {
                        0f
                    } else if (currentMs < 2400f) {
                        (currentMs - 1000f) / 1400f
                    } else {
                        1f
                    }.coerceIn(0f, 1f)

                    // Aril Bloom Expansion (Expands outward gently 2400ms -> 4800ms)
                    val bloomExpansion = if (currentMs in 2400f..4800f) {
                        val bU = (currentMs - 2400f) / 2400f
                        kotlin.math.sin(bU * Math.PI.toFloat()) * 4.5f * density
                    } else {
                        0f
                    }

                    // A. Unpeeled Whole Sphere (Fades/shrinks as peel opens)
                    if (peelProgress < 1f) {
                        val unpeeledAlpha = ((1f - peelProgress) * 255).toInt()
                        val unpeeledPaint = android.graphics.Paint(dukuSkinPaint).apply {
                            color = android.graphics.Color.argb((alpha * unpeeledAlpha / 255f * 255).toInt().coerceIn(0, 255), 234, 179, 8)
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(cx3, cy3, r3, unpeeledPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(cx3 + 1f * density, cy3 - 2f * density, 0.6f * density, dukuSpecklePaint)
                    }

                    // B. 4 Dynamic Peeling Skin Petals (Curl backwards dynamically)
                    if (peelProgress > 0.05f) {
                        val peelSpread = peelProgress * 12f * density
                        // Left Flap
                        val leftFlap = android.graphics.Path().apply {
                            moveTo(cx3 - r3 * 0.4f, cy3)
                            quadTo(cx3 - r3 - peelSpread, cy3 + r3 * 0.4f, cx3 - r3 * 0.6f - peelSpread * 0.8f, cy3 + r3 * 1.1f)
                            quadTo(cx3 - r3 * 0.2f, cy3 + r3 * 0.8f, cx3 - r3 * 0.4f, cy3)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(leftFlap, dukuInnerSkinPaint)
                        drawContext.canvas.nativeCanvas.drawPath(leftFlap, dukuSkinPaint)

                        // Right Flap
                        val rightFlap = android.graphics.Path().apply {
                            moveTo(cx3 + r3 * 0.4f, cy3)
                            quadTo(cx3 + r3 + peelSpread, cy3 + r3 * 0.4f, cx3 + r3 * 0.6f + peelSpread * 0.8f, cy3 + r3 * 1.1f)
                            quadTo(cx3 + r3 * 0.2f, cy3 + r3 * 0.8f, cx3 + r3 * 0.4f, cy3)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(rightFlap, dukuInnerSkinPaint)
                        drawContext.canvas.nativeCanvas.drawPath(rightFlap, dukuSkinPaint)
                    }

                    // C. 5 Glistening Translucent Pearl-White Arils (Bloom & Expand with Specular Sparkles)
                    if (peelProgress > 0.15f) {
                        val arilAlphaMult = ((peelProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        val numSegments = 5
                        for (i in 0 until numSegments) {
                            val angle = (i * (2 * Math.PI / numSegments) - Math.PI / 2).toFloat()
                            val segDist = 4.2f * density + bloomExpansion
                            val segX = cx3 + kotlin.math.cos(angle) * segDist
                            val segY = cy3 + kotlin.math.sin(angle) * segDist
                            val segR = 4.8f * density

                            // Translucent Aril Base
                            arilGlowPaint.alpha = (alpha * arilAlphaMult * 200).toInt()
                            translucentArilPaint.alpha = (alpha * arilAlphaMult * 245).toInt()
                            arilOutlinePaint.alpha = (alpha * arilAlphaMult * 160).toInt()
                            glintPaint.alpha = (alpha * arilAlphaMult * 255).toInt()

                            drawContext.canvas.nativeCanvas.drawCircle(segX, segY, segR, arilGlowPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(segX, segY, segR * 0.92f, translucentArilPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(segX, segY, segR, arilOutlinePaint)

                            // Visible seed contour inside
                            if (i == 0 || i == 2) {
                                seedShadowPaint.alpha = (alpha * arilAlphaMult * 90).toInt()
                                drawContext.canvas.nativeCanvas.drawCircle(segX + 0.5f * density, segY + 0.5f * density, 1.8f * density, seedShadowPaint)
                            }

                            // Specular glint
                            drawContext.canvas.nativeCanvas.drawCircle(segX - 1.2f * density, segY - 1.2f * density, 1.1f * density, glintPaint)
                        }

                        // Central core
                        drawContext.canvas.nativeCanvas.drawCircle(cx3, cy3, 1.8f * density, dukuShadePaint)
                    }

                    // D. Orbiting Sweet Juice Sparkles & Twinkles during bloom (2400ms -> 5000ms)
                    if (currentMs in 2200f..5400f) {
                        val spkU = (currentMs - 2200f) / 3200f
                        val spkAlpha = (kotlin.math.sin(spkU * Math.PI.toFloat())).coerceIn(0f, 1f)
                        sparklePaint.alpha = (alpha * spkAlpha * 255).toInt()

                        val sparkles = listOf(
                            Pair(cx3 - 16f * density, cy3 - 12f * density),
                            Pair(cx3 + 18f * density, cy3 - 10f * density),
                            Pair(cx3 - 12f * density, cy3 + 16f * density),
                            Pair(cx3 + 14f * density, cy3 + 15f * density),
                            Pair(cx3, cy3 - 18f * density)
                        )
                        for ((sx, sy) in sparkles) {
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, 1.4f * density, sparklePaint)
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, 0.7f * density, glintPaint)
                        }
                    }

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Drive / Car Easter Egg: Generic Car on Top Fret (0-3.2s), then 5s later Aston Martin on Bottom Fret (5.0-7.2s)
        if (carDriveTriggerTime > 0L) {
            val carProgress = remember(carDriveTriggerTime) { Animatable(0f) }
            LaunchedEffect(carDriveTriggerTime) {
                carProgress.snapTo(0f)
                carProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 7200, easing = LinearEasing),
                )
                carDriveTriggerTime = 0L
            }
            if (carProgress.value in 0.001f..0.999f) {
                val currentMs = carProgress.value * 7200f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }

                    val fret1Y = fretYs.getOrNull(0) ?: (canvasH * 0.25f)
                    val fret3Y = fretYs.getOrNull(2) ?: (canvasH * 0.75f)

                    // ==========================================
                    // 1. GENERIC COMMUTER CAR (Top Fret: 0ms -> 3200ms)
                    // ==========================================
                    if (currentMs in 0f..3200f) {
                        val u1 = (currentMs / 3200f).coerceIn(0f, 1f)
                        val startX = -45f * density
                        val endX = canvasW + 45f * density
                        val carX = startX + u1 * (endX - startX)
                        val carY = fret1Y - 2.5f * density // Wheels resting on top fret line

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(carX, carY)

                        // Exhaust smoke puffs trailing behind
                        val smokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0x6694A3B8.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val s1 = ((currentMs * 0.02f) % 6f) * density
                        drawContext.canvas.nativeCanvas.drawCircle(-18f * density - s1, -3f * density, 2.2f * density, smokePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-24f * density - s1 * 1.5f, -5f * density, 3f * density, smokePaint)

                        val genericBodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0D9488.toInt() // Quirky Teal Commuter Car
                            style = android.graphics.Paint.Style.FILL
                        }
                        val genericRoofPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0F766E.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val genericWindowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFBAE6FD.toInt() // Light sky blue windows
                            style = android.graphics.Paint.Style.FILL
                        }
                        val wheelTirePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF1E293B.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val wheelCapPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFE2E8F0.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val headLightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFFEF08A.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val tailLightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFEF4444.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Lower Body
                        drawContext.canvas.nativeCanvas.drawRoundRect(-15f * density, -7f * density, 15f * density, 0f, 2f * density, 2f * density, genericBodyPaint)
                        // Cabin / Roof
                        val cabin = android.graphics.Path().apply {
                            moveTo(-8f * density, -7f * density)
                            lineTo(-5f * density, -13.5f * density)
                            lineTo(6f * density, -13.5f * density)
                            lineTo(10f * density, -7f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(cabin, genericRoofPaint)
                        // Windows
                        val window = android.graphics.Path().apply {
                            moveTo(-6.5f * density, -7.5f * density)
                            lineTo(-4.2f * density, -12.2f * density)
                            lineTo(4.8f * density, -12.2f * density)
                            lineTo(8.2f * density, -7.5f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(window, genericWindowPaint)
                        // Window Divider B-pillar
                        drawContext.canvas.nativeCanvas.drawLine(0.5f * density, -12.2f * density, 0.5f * density, -7.5f * density, genericRoofPaint)

                        // Headlight & Taillight
                        drawContext.canvas.nativeCanvas.drawRect(14f * density, -5.5f * density, 15.2f * density, -3f * density, headLightPaint)
                        drawContext.canvas.nativeCanvas.drawRect(-15.2f * density, -5.5f * density, -14f * density, -3f * density, tailLightPaint)

                        // Wheels (Spinning)
                        val spinAngle = (u1 * 360f * 8f) % 360f
                        // Rear Wheel
                        drawContext.canvas.nativeCanvas.drawCircle(-8.5f * density, 0f, 3.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-8.5f * density, 0f, 1.6f * density, wheelCapPaint)
                        // Front Wheel
                        drawContext.canvas.nativeCanvas.drawCircle(8.5f * density, 0f, 3.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(8.5f * density, 0f, 1.6f * density, wheelCapPaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =======================================================
                    // 2. ASTON MARTIN SUPERCAR (Bottom Fret: 5000ms -> 7200ms)
                    // =======================================================
                    if (currentMs in 5000f..7200f) {
                        val u2 = ((currentMs - 5000f) / 2200f).coerceIn(0f, 1f)
                        // Hyper-speed acceleration curve
                        val speedCurve = u2 * u2
                        val startX = -65f * density
                        val endX = canvasW + 75f * density
                        val carX = startX + speedCurve * (endX - startX)
                        val carY = fret3Y - 2f * density // Low-slung supercar stance on bottom fret

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(carX, carY)

                        // A. Supersonic Motion Blur & Speed Lines
                        val blurLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0x88064E3B.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.4f * density
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        drawContext.canvas.nativeCanvas.drawLine(-45f * density, -3f * density, -22f * density, -3f * density, blurLinePaint)
                        drawContext.canvas.nativeCanvas.drawLine(-60f * density, -7f * density, -20f * density, -7f * density, blurLinePaint)
                        drawContext.canvas.nativeCanvas.drawLine(-35f * density, -11f * density, -15f * density, -11f * density, blurLinePaint)

                        // B. Twin Exhaust Nitro Backfire Flames
                        val flameOuterPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFEF4444.toInt() // Crimson
                            style = android.graphics.Paint.Style.FILL
                        }
                        val flameInnerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFFBBF24.toInt() // Amber Nitro Core
                            style = android.graphics.Paint.Style.FILL
                        }
                        val flameLen = (12f + kotlin.math.sin(u2 * 30f) * 4f) * density
                        val flamePath = android.graphics.Path().apply {
                            moveTo(-22f * density, -2f * density)
                            lineTo(-22f * density - flameLen, -3f * density)
                            lineTo(-22f * density, -4f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(flamePath, flameOuterPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-22f * density - 3f * density, -3f * density, 1.8f * density, flameInnerPaint)

                        // C. Aston Martin Body Paints (British Racing Green)
                        val astonBodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF064E3B.toInt() // Deep British Racing Green
                            style = android.graphics.Paint.Style.FILL
                        }
                        val astonHighlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF047857.toInt() // Aerodynamic light reflection
                            style = android.graphics.Paint.Style.FILL
                        }
                        val astonGlassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xDD0F172A.toInt() // Low-profile dark tinted sports glass
                            style = android.graphics.Paint.Style.FILL
                        }
                        val grillePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFE2E8F0.toInt() // Iconic Aston Martin silver vane grille
                            style = android.graphics.Paint.Style.FILL
                        }
                        val laserHeadlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFF59E0B.toInt() // Amber LED Laser lights
                            style = android.graphics.Paint.Style.FILL
                        }
                        val slimTailLightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFDC2626.toInt() // Slim blade LED taillight
                            style = android.graphics.Paint.Style.FILL
                        }
                        val alloyRimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF94A3B8.toInt() // Diamond cut forged alloys
                            style = android.graphics.Paint.Style.FILL
                        }
                        val sportsTirePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF020617.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }

                        // D. Sleek Aerodynamic Low-Slung Silhouette
                        val bodyPath = android.graphics.Path().apply {
                            moveTo(-22f * density, -2.5f * density)
                            lineTo(-21f * density, -6.5f * density)
                            quadTo(-14f * density, -8.5f * density, -7f * density, -9.5f * density) // Rear haunch
                            lineTo(4f * density, -9.5f * density) // Low roofline
                            quadTo(12f * density, -7.5f * density, 18f * density, -5.5f * density) // Long sleek bonnet
                            lineTo(23f * density, -3f * density) // Shark nose front
                            lineTo(22f * density, -0.8f * density)
                            lineTo(-22f * density, -0.8f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(bodyPath, astonBodyPaint)

                        // Aero Roofline & Tinted Cockpit
                        val cockpitPath = android.graphics.Path().apply {
                            moveTo(-6f * density, -6.5f * density)
                            quadTo(-3f * density, -9.2f * density, 3f * density, -9.2f * density)
                            lineTo(9f * density, -6.5f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(cockpitPath, astonGlassPaint)

                        // Signature Aston Martin Sculpted Front Grille
                        drawContext.canvas.nativeCanvas.drawRoundRect(20f * density, -4f * density, 23.5f * density, -1.2f * density, 1f * density, 1f * density, grillePaint)

                        // Headlight Beam
                        drawContext.canvas.nativeCanvas.drawRect(18f * density, -5.5f * density, 21.5f * density, -4f * density, laserHeadlightPaint)
                        // Laser light cone
                        val lightConePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0x44F59E0B.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val conePath = android.graphics.Path().apply {
                            moveTo(22f * density, -5f * density)
                            lineTo(42f * density, -9f * density)
                            lineTo(42f * density, 1f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(conePath, lightConePaint)

                        // Slim Rear Blade LED Taillight
                        drawContext.canvas.nativeCanvas.drawRect(-22.2f * density, -6f * density, -20.5f * density, -4.5f * density, slimTailLightPaint)

                        // E. Low-Profile Supercar Sports Wheels (High-RPM Blur)
                        // Rear Sports Wheel
                        drawContext.canvas.nativeCanvas.drawCircle(-12f * density, 0f, 3.4f * density, sportsTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-12f * density, 0f, 2.1f * density, alloyRimPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-12f * density, 0f, 0.8f * density, sportsTirePaint)
                        // Front Sports Wheel
                        drawContext.canvas.nativeCanvas.drawCircle(13f * density, 0f, 3.4f * density, sportsTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(13f * density, 0f, 2.1f * density, alloyRimPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(13f * density, 0f, 0.8f * density, sportsTirePaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }

        // Crypto Moon Rocket Easter Egg (Bottom-Left to Top-Right Blastoff)
        if (cryptoRocketTriggerTime > 0L) {
            val rocketProgress = remember(cryptoRocketTriggerTime) { Animatable(0f) }
            LaunchedEffect(cryptoRocketTriggerTime) {
                rocketProgress.snapTo(0f)
                rocketProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
                )
                cryptoRocketTriggerTime = 0L
            }
            if (rocketProgress.value in 0.001f..0.999f) {
                val t = rocketProgress.value
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Diagonal path: Bottom-Left (-40dp, canvasH + 40dp) -> Top-Right (canvasW + 50dp, -50dp)
                    val startX = -40f * density
                    val startY = canvasH + 40f * density
                    val endX = canvasW + 50f * density
                    val endY = -50f * density

                    val posX = startX + t * (endX - startX)
                    val posY = startY + t * (endY - startY)

                    // Rocket pointing towards top-right (~ -36 degrees)
                    val baseAngle = -36f
                    val engineVibe = kotlin.math.sin(t * 30f * Math.PI.toFloat()) * 1.5f
                    val totalAngle = baseAngle + engineVibe

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(posX, posY)
                    drawContext.canvas.nativeCanvas.rotate(totalAngle)

                    // 1. Trail of Stardust Smoke & Golden Sparks
                    val stardustPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x55CBD5E1.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFDE047.toInt() // Golden moon spark
                        style = android.graphics.Paint.Style.FILL
                    }
                    val glintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    val sparkDist = (t * 20f * density)
                    drawContext.canvas.nativeCanvas.drawCircle(-25f * density, 0f, 4f * density, stardustPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-38f * density, -2f * density, 6f * density, stardustPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-52f * density, 3f * density, 8f * density, stardustPaint)

                    // Golden Sparkles
                    drawContext.canvas.nativeCanvas.drawCircle(-28f * density, -4f * density, 1.4f * density, sparkPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-35f * density, 5f * density, 1.6f * density, sparkPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-48f * density, -3f * density, 1.2f * density, sparkPaint)

                    // 2. Multilayered Roaring Thruster Flame
                    val flameOuterPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFEF4444.toInt() // Crimson plasma
                        style = android.graphics.Paint.Style.FILL
                    }
                    val flameMidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF97316.toInt() // Orange flame
                        style = android.graphics.Paint.Style.FILL
                    }
                    val flameCorePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFEF08A.toInt() // Bright yellow core
                        style = android.graphics.Paint.Style.FILL
                    }
                    val flameWhitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFFFFFF.toInt() // White hot jet
                        style = android.graphics.Paint.Style.FILL
                    }

                    val flameLen = (16f + kotlin.math.sin(t * 40f) * 4f) * density
                    // Outer flame cone
                    val outerFlame = android.graphics.Path().apply {
                        moveTo(-11f * density, -3.5f * density)
                        lineTo(-11f * density - flameLen, 0f)
                        lineTo(-11f * density, 3.5f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(outerFlame, flameOuterPaint)
                    // Mid flame
                    val midFlame = android.graphics.Path().apply {
                        moveTo(-11f * density, -2.5f * density)
                        lineTo(-11f * density - flameLen * 0.72f, 0f)
                        lineTo(-11f * density, 2.5f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(midFlame, flameMidPaint)
                    // Core flame
                    val coreFlame = android.graphics.Path().apply {
                        moveTo(-11f * density, -1.5f * density)
                        lineTo(-11f * density - flameLen * 0.45f, 0f)
                        lineTo(-11f * density, 1.5f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(coreFlame, flameCorePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-11f * density, 0f, 1.8f * density, flameWhitePaint)

                    // 3. Rocket Fuselage & Wings
                    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF8FAFC.toInt() // Pearl white spacecraft hull
                        style = android.graphics.Paint.Style.FILL
                    }
                    val shadePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFE2E8F0.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val nosePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF06B6D4.toInt() // Cosmic Cyan Nosecone
                        style = android.graphics.Paint.Style.FILL
                    }
                    val finPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0891B2.toInt() // Cyan Stabilizer Fins
                        style = android.graphics.Paint.Style.FILL
                    }
                    val windowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0284C7.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val windowRimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF94A3B8.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val goldEmblemPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF59E0B.toInt() // Golden Crypto Diamond
                        style = android.graphics.Paint.Style.FILL
                    }

                    // A. Rear Stabilizer Fins
                    val topFin = android.graphics.Path().apply {
                        moveTo(-6f * density, -3.5f * density)
                        lineTo(-12f * density, -8f * density)
                        lineTo(-10f * density, -3.5f * density)
                        close()
                    }
                    val bottomFin = android.graphics.Path().apply {
                        moveTo(-6f * density, 3.5f * density)
                        lineTo(-12f * density, 8f * density)
                        lineTo(-10f * density, 3.5f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(topFin, finPaint)
                    drawContext.canvas.nativeCanvas.drawPath(bottomFin, finPaint)

                    // B. Main Fuselage Hull
                    val hull = android.graphics.Path().apply {
                        moveTo(-11f * density, -3.8f * density)
                        lineTo(4f * density, -3.8f * density)
                        quadTo(12f * density, -3.5f * density, 18f * density, 0f)
                        quadTo(12f * density, 3.5f * density, 4f * density, 3.8f * density)
                        lineTo(-11f * density, 3.8f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(hull, bodyPaint)
                    drawContext.canvas.nativeCanvas.drawRect(-11f * density, 0f, 4f * density, 3.8f * density, shadePaint) // 3D Bottom shade

                    // C. Aerodynamic Nosecone
                    val nosecone = android.graphics.Path().apply {
                        moveTo(7f * density, -3.2f * density)
                        quadTo(13f * density, -2.8f * density, 18f * density, 0f)
                        quadTo(13f * density, 2.8f * density, 7f * density, 3.2f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(nosecone, nosePaint)

                    // D. Observation Porthole Window
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, 2.5f * density, windowPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, 2.5f * density, windowRimPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(-0.7f * density, -0.7f * density, 0.7f * density, glintPaint)

                    // E. Golden Crypto Diamond Badge on Fuselage
                    val badge = android.graphics.Path().apply {
                        moveTo(-5.5f * density, -1.8f * density)
                        lineTo(-4f * density, -3f * density)
                        lineTo(-2.5f * density, -1.8f * density)
                        lineTo(-4f * density, -0.6f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(badge, goldEmblemPaint)

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Murmur App Starling Murmuration & Majestic Eagle Swoop Easter Egg
        if (murmurFlockTriggerTime > 0L) {
            val flockProgress = remember(murmurFlockTriggerTime) { Animatable(0f) }
            LaunchedEffect(murmurFlockTriggerTime) {
                flockProgress.snapTo(0f)
                flockProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4800, easing = LinearEasing),
                )
                murmurFlockTriggerTime = 0L
            }
            if (flockProgress.value in 0.001f..0.999f) {
                val u = flockProgress.value
                val currentMs = u * 4800f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // 1. Twilight Sky Ambient Ribbon Glow
                    val skyAlpha = (kotlin.math.sin(u * Math.PI.toFloat()) * 0.16f).coerceIn(0f, 1f)
                    val duskGlowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((skyAlpha * 255).toInt(), 12, 19, 16) // Murmur dusk background
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, duskGlowPaint)

                    // Flock center progression from Left to Right with natural S-curve murmuration path
                    val flockCenterX = -60f * density + u * (canvasW + 120f * density)
                    val waveY = kotlin.math.sin(u * 2.8f * Math.PI.toFloat()) * (canvasH * 0.22f)
                    val flockCenterY = (canvasH * 0.50f) + waveY

                    // Majestic Eagle Parabolic Swoop Arc (1000ms -> 3200ms)
                    val eagleActive = currentMs in 1000f..3200f
                    val eagleU = if (eagleActive) ((currentMs - 1000f) / 2200f) else 0f
                    // Smooth curved swoop down from top-left, banking through the flock, and soaring up to the top-right
                    val eagleX = canvasW * (0.15f + eagleU * 0.70f)
                    val eagleY = (canvasH * 0.15f) + kotlin.math.sin(eagleU * Math.PI.toFloat()) * (canvasH * 0.62f)

                    // Eagle flight angle (tangent to the swoop curve)
                    val nextEagleU = (eagleU + 0.02f).coerceIn(0f, 1f)
                    val nextEagleX = canvasW * (0.15f + nextEagleU * 0.70f)
                    val nextEagleY = (canvasH * 0.15f) + kotlin.math.sin(nextEagleU * Math.PI.toFloat()) * (canvasH * 0.62f)
                    val eagleAngle = (kotlin.math.atan2((nextEagleY - eagleY).toDouble(), (nextEagleX - eagleX).toDouble()) * 180.0 / Math.PI).toFloat()

                    // Draw 48 individual boids with unique spatial offsets and depth
                    val numBirds = 48
                    for (i in 0 until numBirds) {
                        val seed = i * 137.5f // Golden angle distribution
                        val radiusFactor = kotlin.math.sqrt(i.toFloat() / numBirds.toFloat())
                        val angle = (seed % 360f) * (Math.PI.toFloat() / 180f)

                        // Cluster dimensions (Ribbon width & height)
                        val spreadX = kotlin.math.cos(angle) * radiusFactor * 34f * density
                        val spreadY = kotlin.math.sin(angle) * radiusFactor * 20f * density

                        // Individual bird position
                        var bx = flockCenterX + spreadX
                        var by = flockCenterY + spreadY

                        // Biological reaction when Eagle banks nearby
                        var agitated = 0f
                        if (eagleActive) {
                            val edx = bx - eagleX
                            val edy = by - eagleY
                            val distSq = edx * edx + edy * edy
                            val fleeRadius = 55f * density
                            if (distSq < fleeRadius * fleeRadius) {
                                val dist = kotlin.math.sqrt(distSq).coerceAtLeast(1f)
                                val push = (1f - (dist / fleeRadius)) * 26f * density
                                val pushDirY = if (spreadY >= 0) 1f else -1f // Flocks split gracefully above & below the eagle
                                bx += (edx / dist) * push * 0.7f
                                by += pushDirY * push * 1.3f
                                agitated = (1f - (dist / fleeRadius)).coerceIn(0f, 1f)
                            }
                        }

                        // Local heading vector
                        val nextU = (u + 0.015f).coerceIn(0f, 1f)
                        val nextWaveY = kotlin.math.sin(nextU * 2.8f * Math.PI.toFloat()) * (canvasH * 0.22f)
                        val vx = (canvasW + 120f * density) * 0.015f
                        val vy = nextWaveY - waveY + (kotlin.math.sin(currentMs * 0.008f + i) * 1.5f * density)
                        val speed = kotlin.math.sqrt(vx * vx + vy * vy).coerceAtLeast(0.1f)
                        val dirX = (vx / speed).toFloat()
                        val dirY = (vy / speed).toFloat()

                        // Bird scale & wing flap oscillation
                        val depth = 0.55f + 0.45f * ((i % 7) / 7f)
                        val wingFlap = kotlin.math.sin(currentMs * 0.022f + i * 0.8f) * 0.8f
                        val birdScale = (1.8f * density) * (0.8f + depth * 0.4f)

                        // Murmur Palette: Emerald (#43D183), Cobalt (#5B9BFF), Gold (#F4C542 on agitation), Silver (#E8EEF0)
                        val birdColor = when {
                            agitated > 0.25f -> 0xFFF4C542.toInt() // Agitation Gold
                            i % 5 == 0 -> 0xFF5B9BFF.toInt() // Cobalt
                            i % 7 == 0 -> 0xFFE8EEF0.toInt() // Silver
                            else -> 0xFF43D183.toInt() // Signature Murmur Emerald
                        }

                        val birdAlpha = (kotlin.math.sin(u * Math.PI.toFloat()) * (0.55f + depth * 0.45f)).coerceIn(0f, 1f)
                        val birdPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((birdAlpha * 255).toInt().coerceIn(0, 255), (birdColor shr 16) and 0xFF, (birdColor shr 8) and 0xFF, birdColor and 0xFF)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Draw streamlined delta-wing starling silhouette
                        val e = dirX * birdScale
                        val f = dirY * birdScale

                        val birdPath = android.graphics.Path().apply {
                            moveTo(bx + 4.5f * e, by + 4.5f * f)
                            lineTo(bx - 3.2f * e - (2.5f + wingFlap) * f, by - 3.2f * f + (2.5f + wingFlap) * e)
                            lineTo(bx - 1.8f * e, by - 1.8f * f)
                            lineTo(bx - 3.2f * e + (2.5f + wingFlap) * f, by - 3.2f * f - (2.5f + wingFlap) * e)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(birdPath, birdPaint)
                    }

                    // 2. Magnificent Vector Golden Eagle (Swooping with fingered wingtips & realistic raptor anatomy)
                    if (eagleActive) {
                        val eagleAlpha = (kotlin.math.sin(eagleU * Math.PI.toFloat())).coerceIn(0f, 1f)

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(eagleX, eagleY)
                        drawContext.canvas.nativeCanvas.rotate(eagleAngle)

                        val er = 12f * density // Majestic Eagle scale (3x bigger than starlings)
                        val wingBeat = kotlin.math.sin(currentMs * 0.016f) * 2.8f * density

                        // Eagle Paints
                        val eagleBodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((eagleAlpha * 255).toInt().coerceIn(0, 255), 38, 30, 24) // Deep Espresso / Dark Raptor Plumage
                            style = android.graphics.Paint.Style.FILL
                        }
                        val eagleMantlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((eagleAlpha * 255).toInt().coerceIn(0, 255), 217, 119, 6) // Golden Eagle Nape Feathers
                            style = android.graphics.Paint.Style.FILL
                        }
                        val eagleTailPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((eagleAlpha * 255).toInt().coerceIn(0, 255), 68, 64, 60)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val beakPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((eagleAlpha * 255).toInt().coerceIn(0, 255), 250, 204, 21) // Hooked Gold Beak
                            style = android.graphics.Paint.Style.FILL
                        }
                        val eyePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((eagleAlpha * 255).toInt().coerceIn(0, 255), 254, 240, 138) // Piercing Amber Eye
                            style = android.graphics.Paint.Style.FILL
                        }

                        // A. Broad Wedge/Fan Tail
                        val tail = android.graphics.Path().apply {
                            moveTo(-er * 0.4f, 0f)
                            lineTo(-er * 1.3f, -er * 0.45f)
                            lineTo(-er * 1.6f, 0f)
                            lineTo(-er * 1.3f, er * 0.45f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(tail, eagleTailPaint)

                        // B. Curved Swept Raptor Wings with Emarginated Fingered Primaries
                        // Left Wing
                        val leftWing = android.graphics.Path().apply {
                            moveTo(-er * 0.2f, 0f)
                            quadTo(er * 0.1f, -er * 0.8f, er * 0.2f, -er * 1.4f - wingBeat) // Leading wing edge
                            lineTo(-er * 0.1f, -er * 1.55f - wingBeat) // Primary feather 1
                            lineTo(-er * 0.25f, -er * 1.4f - wingBeat)
                            lineTo(-er * 0.4f, -er * 1.5f - wingBeat) // Primary feather 2
                            lineTo(-er * 0.5f, -er * 1.3f - wingBeat)
                            lineTo(-er * 0.65f, -er * 1.4f - wingBeat) // Primary feather 3
                            quadTo(-er * 0.7f, -er * 0.6f, -er * 0.4f, 0f) // Trailing edge
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(leftWing, eagleBodyPaint)

                        // Right Wing
                        val rightWing = android.graphics.Path().apply {
                            moveTo(-er * 0.2f, 0f)
                            quadTo(er * 0.1f, er * 0.8f, er * 0.2f, er * 1.4f + wingBeat)
                            lineTo(-er * 0.1f, er * 1.55f + wingBeat)
                            lineTo(-er * 0.25f, er * 1.4f + wingBeat)
                            lineTo(-er * 0.4f, er * 1.5f + wingBeat)
                            lineTo(-er * 0.5f, er * 1.3f + wingBeat)
                            lineTo(-er * 0.65f, er * 1.4f + wingBeat)
                            quadTo(-er * 0.7f, er * 0.6f, -er * 0.4f, 0f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(rightWing, eagleBodyPaint)

                        // C. Main Raptor Torso & Golden Mantle
                        val body = android.graphics.Path().apply {
                            moveTo(-er * 0.8f, 0f)
                            quadTo(-er * 0.2f, -er * 0.35f, er * 0.6f, -er * 0.22f)
                            lineTo(er * 1.1f, 0f)
                            lineTo(er * 0.6f, er * 0.22f)
                            quadTo(-er * 0.2f, er * 0.35f, -er * 0.8f, 0f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(body, eagleBodyPaint)

                        // Golden Mantle / Nape Crest
                        val mantle = android.graphics.Path().apply {
                            moveTo(0f, -er * 0.22f)
                            lineTo(er * 0.85f, 0f)
                            lineTo(0f, er * 0.22f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(mantle, eagleMantlePaint)

                        // D. Hooked Golden Beak & Amber Eye
                        val beak = android.graphics.Path().apply {
                            moveTo(er * 0.85f, -er * 0.12f)
                            lineTo(er * 1.45f, 0f)
                            lineTo(er * 1.25f, er * 0.18f) // Curved hook
                            lineTo(er * 0.85f, er * 0.12f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(beak, beakPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(er * 0.65f, -er * 0.08f, 0.9f * density, eyePaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }

        // Terra / LUNA Crashing Rocket Easter Egg (Ascent -> Depeg Death Spiral -> Catastrophic Explosion)
        if (lunaCrashTriggerTime > 0L) {
            val lunaProgress = remember(lunaCrashTriggerTime) { Animatable(0f) }
            LaunchedEffect(lunaCrashTriggerTime) {
                lunaProgress.snapTo(0f)
                lunaProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3500, easing = LinearEasing),
                )
                lunaCrashTriggerTime = 0L
            }
            if (lunaProgress.value in 0.001f..0.999f) {
                val currentMs = lunaProgress.value * 3500f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val apexX = canvasW * 0.44f
                    val apexY = canvasH * 0.32f
                    val impactX = canvasW * 0.65f
                    val impactY = canvasH * 0.78f

                    // Paints for Rocket
                    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFF8FAFC.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val nosePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF06B6D4.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val finPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0891B2.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val lunaBadgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFBBF24.toInt() // LUNA Yellow
                        style = android.graphics.Paint.Style.FILL
                    }
                    val blackSmokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0x881E293B.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    // ==========================================
                    // PHASE 1 & 2: ROCKET ASCENT & DEATH SPIRAL (0ms -> 1750ms)
                    // ==========================================
                    if (currentMs < 1750f) {
                        val posX: Float
                        val posY: Float
                        val angle: Float

                        if (currentMs < 1000f) {
                            // Phase 1: Hopeful Liftoff (0ms -> 1000ms)
                            val u1 = (currentMs / 1000f)
                            posX = -35f * density + u1 * (apexX - (-35f * density))
                            posY = (canvasH + 35f * density) + u1 * (apexY - (canvasH + 35f * density))
                            angle = -38f
                        } else {
                            // Phase 2: Depeg Death Spiral & Nose Dive (1000ms -> 1750ms)
                            val u2 = ((currentMs - 1000f) / 750f)
                            posX = apexX + u2 * (impactX - apexX)
                            posY = apexY + (u2 * u2) * (impactY - apexY)
                            // Wild spinning tumble into nose-dive
                            angle = -38f + u2 * 195f + (kotlin.math.sin(u2 * 16f) * 20f)

                            // Sputtering engine black smoke puffs
                            drawContext.canvas.nativeCanvas.drawCircle(posX - 12f * density, posY - 8f * density, 5f * density, blackSmokePaint)
                            drawContext.canvas.nativeCanvas.drawCircle(posX - 22f * density, posY - 16f * density, 8f * density, blackSmokePaint)
                        }

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(posX, posY)
                        drawContext.canvas.nativeCanvas.rotate(angle)

                        // Sputtering thruster flame if alive
                        if (currentMs < 1400f) {
                            val flamePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = if (currentMs < 1000f) 0xFFEF4444.toInt() else 0xFFF97316.toInt()
                                style = android.graphics.Paint.Style.FILL
                            }
                            val flameLen = (12f + kotlin.math.sin(currentMs * 0.05f) * 4f) * density
                            val flame = android.graphics.Path().apply {
                                moveTo(-11f * density, -3f * density)
                                lineTo(-11f * density - flameLen, 0f)
                                lineTo(-11f * density, 3f * density)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(flame, flamePaint)
                        }

                        // Fins
                        val topFin = android.graphics.Path().apply {
                            moveTo(-6f * density, -3.5f * density)
                            lineTo(-12f * density, -8f * density)
                            lineTo(-10f * density, -3.5f * density)
                            close()
                        }
                        val bottomFin = android.graphics.Path().apply {
                            moveTo(-6f * density, 3.5f * density)
                            lineTo(-12f * density, 8f * density)
                            lineTo(-10f * density, 3.5f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(topFin, finPaint)
                        drawContext.canvas.nativeCanvas.drawPath(bottomFin, finPaint)

                        // Hull
                        val hull = android.graphics.Path().apply {
                            moveTo(-11f * density, -3.8f * density)
                            lineTo(4f * density, -3.8f * density)
                            quadTo(12f * density, -3.5f * density, 18f * density, 0f)
                            quadTo(12f * density, 3.5f * density, 4f * density, 3.8f * density)
                            lineTo(-11f * density, 3.8f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(hull, bodyPaint)

                        // Nosecone
                        val nosecone = android.graphics.Path().apply {
                            moveTo(7f * density, -3.2f * density)
                            quadTo(13f * density, -2.8f * density, 18f * density, 0f)
                            quadTo(13f * density, 2.8f * density, 7f * density, 3.2f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(nosecone, nosePaint)

                        // LUNA Crescent Emblem
                        drawContext.canvas.nativeCanvas.drawCircle(-3f * density, 0f, 2.4f * density, lunaBadgePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-2f * density, 0f, 2.1f * density, bodyPaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =======================================================
                    // PHASE 3: CATASTROPHIC EXPLOSION & DEBRIS (1750ms -> 3500ms)
                    // =======================================================
                    if (currentMs >= 1750f) {
                        val expU = ((currentMs - 1750f) / 1750f).coerceIn(0f, 1f)
                        val expAlpha = (1f - expU).coerceIn(0f, 1f)

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(impactX, impactY)

                        // 1. Expanding Fireball Shockwaves
                        val blastRadius = (expU * 45f * density)
                        val blastOuter = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 220).toInt().coerceIn(0, 255), 239, 68, 68)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val blastMid = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 240).toInt().coerceIn(0, 255), 249, 115, 22)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val blastCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 255).toInt().coerceIn(0, 255), 254, 240, 138)
                            style = android.graphics.Paint.Style.FILL
                        }

                        drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, blastRadius, blastOuter)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, blastRadius * 0.65f, blastMid)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, blastRadius * 0.35f, blastCore)

                        // 2. Flying Shrapnel & Debris Pieces
                        val debrisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 255).toInt().coerceIn(0, 255), 248, 250, 252)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 255).toInt().coerceIn(0, 255), 253, 224, 71)
                            style = android.graphics.Paint.Style.FILL
                        }

                        val debrisAngles = listOf(20f, 55f, 95f, 135f, 175f, 215f, 260f, 310f, 345f)
                        for ((idx, deg) in debrisAngles.withIndex()) {
                            val rad = deg * (Math.PI.toFloat() / 180f)
                            val dist = (expU * (30f + idx * 4f) * density)
                            val dx = kotlin.math.cos(rad) * dist
                            val dy = kotlin.math.sin(rad) * dist + (expU * expU * 18f * density) // Gravity drop

                            drawContext.canvas.nativeCanvas.drawCircle(dx, dy, (1.8f * density), debrisPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(dx * 1.15f, dy * 1.15f, (1.1f * density), sparkPaint)
                        }

                        // 3. Comical Downward Depeg Arrow (📉) floating up with smoke
                        val chartPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((expAlpha * 255).toInt().coerceIn(0, 255), 239, 68, 68)
                            textSize = 14f * density
                            typeface = android.graphics.Typeface.create(android.graphics.Typeface.SANS_SERIF, android.graphics.Typeface.BOLD)
                        }
                        val smokeY = -blastRadius * 0.7f - (expU * 15f * density)
                        drawContext.canvas.nativeCanvas.drawText("📉 -99.99%", -22f * density, smokeY, chartPaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }

        // Sundae Ice Cream in Several Places Easter Egg (Artisanal Sundaes on Keys S, U, N, D, A, E)
        if (sundaeTriggerTime > 0L) {
            val sundaeProgress = remember(sundaeTriggerTime) { Animatable(0f) }
            LaunchedEffect(sundaeTriggerTime) {
                sundaeProgress.snapTo(0f)
                sundaeProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 6500, easing = LinearEasing),
                )
                sundaeTriggerTime = 0L
            }
            if (sundaeProgress.value in 0.001f..0.999f) {
                val u = sundaeProgress.value
                val currentMs = u * 6500f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // 1. Locate pixel-perfect keycap centers for S, U, N, D, A, E
                    val targetChars = listOf('s', 'u', 'n', 'd', 'a', 'e')
                    val sundaePositions = mutableListOf<androidx.compose.ui.geometry.Offset>()

                    for (targetChar in targetChars) {
                        var matchedKey: TextKey? = null
                        for (key in keyboard.keys()) {
                            if (key is TextKey) {
                                val code = key.computedData.code
                                val ch = code.toChar().lowercaseChar()
                                if (ch == targetChar || key.computedData.asString(true).equals(targetChar.toString(), ignoreCase = true)) {
                                    matchedKey = key
                                    break
                                }
                            }
                        }
                        if (matchedKey != null) {
                            val kx = matchedKey.visibleBounds.left + (matchedKey.visibleBounds.width / 2f)
                            val ky = matchedKey.visibleBounds.top + (matchedKey.visibleBounds.height / 2f)
                            sundaePositions.add(androidx.compose.ui.geometry.Offset(kx, ky))
                        }
                    }

                    // 2. Render each Sundae coupe with individual stagger bounce & floating bob
                    for ((idx, pos) in sundaePositions.withIndex()) {
                        val staggerMs = idx * 110f
                        val localMs = currentMs - staggerMs
                        if (localMs <= 0f) continue

                        val totalLife = 5800f
                        val lifeU = (localMs / totalLife).coerceIn(0f, 1f)
                        if (lifeU >= 1f) continue

                        // Smooth fade in (0-600ms), floating hold (600-5000ms), soft melt fade out (5000-5800ms)
                        val alpha = when {
                            localMs < 600f -> (localMs / 600f).coerceIn(0f, 1f)
                            localMs > 5000f -> ((totalLife - localMs) / 800f).coerceIn(0f, 1f)
                            else -> 1f
                        }

                        // Pop-in bounce overshoot scale
                        val bounceScale = if (localMs < 600f) {
                            val bt = localMs / 600f
                            1f + kotlin.math.sin(bt * Math.PI.toFloat()) * 0.25f
                        } else {
                            1f
                        }

                        // Gentle sweet floating bob
                        val bobY = kotlin.math.sin((currentMs * 0.003f) + idx * 1.2f) * (2.2f * density)

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(pos.x, pos.y + bobY)
                        drawContext.canvas.nativeCanvas.scale(bounceScale, bounceScale)

                        val sr = 9.5f * density // Sundae scale radius to fit keycap perfectly

                        // Paints
                        val glassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 200).toInt().coerceIn(0, 255), 226, 232, 240) // Translucent fluted glass
                            style = android.graphics.Paint.Style.FILL
                        }
                        val glassRimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 240).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 0.9f * density
                        }
                        val vanillaPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 254, 249, 195) // Vanilla cream
                            style = android.graphics.Paint.Style.FILL
                        }
                        val strawberryPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 251, 113, 133) // Strawberry pink
                            style = android.graphics.Paint.Style.FILL
                        }
                        val chocolatePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 120, 53, 15) // Belgian Chocolate
                            style = android.graphics.Paint.Style.FILL
                        }
                        val fudgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 69, 26, 3) // Hot fudge drizzle
                            style = android.graphics.Paint.Style.FILL
                        }
                        val whippedCreamPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255) // Cloud whipped cream
                            style = android.graphics.Paint.Style.FILL
                        }
                        val cherryPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 225, 29, 72) // Glazed Maraschino Cherry
                            style = android.graphics.Paint.Style.FILL
                        }
                        val stemPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 21, 128, 61) // Green stem
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 0.8f * density
                        }

                        // A. Fluted Glass Coupe Base & Tulip Bowl
                        // Base Foot
                        drawContext.canvas.nativeCanvas.drawRoundRect(-sr * 0.65f, sr * 0.85f, sr * 0.65f, sr * 1.05f, 1f * density, 1f * density, glassPaint)
                        // Stem
                        drawContext.canvas.nativeCanvas.drawRect(-sr * 0.15f, sr * 0.35f, sr * 0.15f, sr * 0.88f, glassPaint)
                        // Coupe Tulip Bowl
                        val coupePath = android.graphics.Path().apply {
                            moveTo(-sr * 0.95f, -sr * 0.05f)
                            quadTo(-sr * 0.85f, sr * 0.42f, 0f, sr * 0.42f)
                            quadTo(sr * 0.85f, sr * 0.42f, sr * 0.95f, -sr * 0.05f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(coupePath, glassPaint)
                        drawContext.canvas.nativeCanvas.drawPath(coupePath, glassRimPaint)

                        // B. Triple Scoops: Vanilla, Strawberry & Chocolate
                        // Vanilla scoop (Left)
                        drawContext.canvas.nativeCanvas.drawCircle(-sr * 0.38f, -sr * 0.22f, sr * 0.45f, vanillaPaint)
                        // Strawberry scoop (Right)
                        drawContext.canvas.nativeCanvas.drawCircle(sr * 0.38f, -sr * 0.22f, sr * 0.45f, strawberryPaint)
                        // Chocolate scoop (Center Crown)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, -sr * 0.50f, sr * 0.48f, chocolatePaint)

                        // C. Hot Fudge Drizzle Cascades
                        val fudgePath = android.graphics.Path().apply {
                            moveTo(-sr * 0.42f, -sr * 0.45f)
                            quadTo(-sr * 0.20f, -sr * 0.20f, -sr * 0.15f, -sr * 0.05f)
                            quadTo(0f, -sr * 0.30f, sr * 0.18f, -sr * 0.08f)
                            quadTo(sr * 0.35f, -sr * 0.35f, sr * 0.45f, -sr * 0.45f)
                            quadTo(0f, -sr * 0.68f, -sr * 0.42f, -sr * 0.45f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(fudgePath, fudgePaint)

                        // D. Fluffy Chantilly Whipped Cream Peak
                        val creamPath = android.graphics.Path().apply {
                            moveTo(-sr * 0.35f, -sr * 0.68f)
                            quadTo(-sr * 0.22f, -sr * 0.92f, 0f, -sr * 1.15f) // Swirl peak
                            quadTo(sr * 0.22f, -sr * 0.92f, sr * 0.35f, -sr * 0.68f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(creamPath, whippedCreamPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, -sr * 0.85f, sr * 0.28f, whippedCreamPaint)

                        // E. Ruby Glazed Maraschino Cherry with Stem
                        drawContext.canvas.nativeCanvas.drawCircle(0f, -sr * 1.22f, sr * 0.22f, cherryPaint)
                        // Cherry glint highlight
                        val glintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(-sr * 0.06f, -sr * 1.28f, sr * 0.06f, glintPaint)
                        // Stem
                        val stemPath = android.graphics.Path().apply {
                            moveTo(0f, -sr * 1.35f)
                            quadTo(sr * 0.25f, -sr * 1.55f, sr * 0.18f, -sr * 1.75f)
                        }
                        drawContext.canvas.nativeCanvas.drawPath(stemPath, stemPaint)

                        // F. Rainbow Confetti Sprinkles
                        val sprinkleYellow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 250, 204, 21)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val sprinkleCyan = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 56, 189, 248)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val sprinklePurple = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 192, 132, 252)
                            style = android.graphics.Paint.Style.FILL
                        }

                        drawContext.canvas.nativeCanvas.drawCircle(-sr * 0.18f, -sr * 0.65f, 0.9f * density, sprinkleYellow)
                        drawContext.canvas.nativeCanvas.drawCircle(sr * 0.18f, -sr * 0.58f, 0.9f * density, sprinkleCyan)
                        drawContext.canvas.nativeCanvas.drawCircle(-sr * 0.05f, -sr * 0.45f, 0.9f * density, sprinklePurple)
                        drawContext.canvas.nativeCanvas.drawCircle(sr * 0.28f, -sr * 0.35f, 0.8f * density, sprinkleYellow)

                        // G. Floating Aroma Sparkles
                        val sparkleU = (currentMs * 0.002f + idx * 0.9f) % 1f
                        val spX = kotlin.math.sin(sparkleU * 6.28f) * sr * 0.8f
                        val spY = -sr * 1.5f - sparkleU * sr * 1.2f
                        val spAlpha = ((1f - sparkleU) * alpha).coerceIn(0f, 1f)
                        val floatSparklePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((spAlpha * 255).toInt().coerceIn(0, 255), 254, 240, 138)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(spX, spY, 1.2f * density, floatSparklePaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }

        // Train & Royal Golden Noble Train on Middle Fret Easter Egg
        if (trainTriggerTime > 0L) {
            val isNoble = isNobleTrainMode
            val trainProgress = remember(trainTriggerTime) { Animatable(0f) }
            LaunchedEffect(trainTriggerTime) {
                trainProgress.snapTo(0f)
                trainProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4400, easing = LinearEasing),
                )
                trainTriggerTime = 0L
            }
            if (trainProgress.value in 0.001f..0.999f) {
                val u = trainProgress.value
                val currentMs = u * 4400f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val rowCount = (keyboard.rowCount).coerceAtLeast(4)
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }
                    val middleFretY = fretYs.getOrNull(1) ?: (canvasH * 0.50f)

                    // Train travels Left to Right along the middle fret rail
                    val trainLen = if (isNoble) (130f * density) else (100f * density)
                    val startX = -trainLen - 20f * density
                    val endX = canvasW + 40f * density
                    val trainHeadX = startX + u * (endX - startX)
                    val trainY = middleFretY - 2.5f * density // Wheels on middle fret

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(trainHeadX, trainY)

                    // 1. Headlight Light Beam Cone
                    val headlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isNoble) 0x55FEF08A.toInt() else 0x44FEF08A.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val beamPath = android.graphics.Path().apply {
                        moveTo(14f * density, -5.5f * density)
                        lineTo(45f * density, -9f * density)
                        lineTo(45f * density, 2f * density)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(beamPath, headlightPaint)

                    // 2. Chuffing Steam & Spark Puffs from Smokestack
                    val steamPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isNoble) 0x88FDE047.toInt() else 0x77CBD5E1.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFEF08A.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }

                    val chuffOffsets = listOf(
                        Pair(5f * density, -16f * density),
                        Pair(-4f * density, -22f * density),
                        Pair(-16f * density, -27f * density),
                        Pair(-30f * density, -30f * density)
                    )
                    for ((idx, offset) in chuffOffsets.withIndex()) {
                        val chuffSize = (3.5f + idx * 1.8f) * density
                        val bob = kotlin.math.sin(currentMs * 0.015f + idx) * 2f * density
                        drawContext.canvas.nativeCanvas.drawCircle(offset.first, offset.second + bob, chuffSize, steamPaint)
                        if (isNoble && idx % 2 == 0) {
                            drawContext.canvas.nativeCanvas.drawCircle(offset.first + 2f * density, offset.second + bob, 1.2f * density, sparkPaint)
                        }
                    }

                    // 3. Render Cars
                    val wheelTirePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF0F172A.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val wheelRimPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = if (isNoble) 0xFFF59E0B.toInt() else 0xFF94A3B8.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val windowLightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFFFEF08A.toInt()
                        style = android.graphics.Paint.Style.FILL
                    }
                    val couplingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = 0xFF475569.toInt()
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.4f * density
                    }

                    if (!isNoble) {
                        // ==========================================
                        // REGULAR STEAM TRAIN
                        // ==========================================
                        // A. Locomotive Engine (0dp)
                        val boilerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF1E293B.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val cabPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFDC2626.toInt() // Red Engine Cab
                            style = android.graphics.Paint.Style.FILL
                        }
                        val brassPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFF59E0B.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val cowcatcherPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFEF4444.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Boiler Cylinder
                        drawContext.canvas.nativeCanvas.drawRoundRect(0f, -8f * density, 14f * density, 0f, 2f * density, 2f * density, boilerPaint)
                        // Brass boiler bands
                        drawContext.canvas.nativeCanvas.drawRect(4f * density, -8f * density, 5.5f * density, 0f, brassPaint)
                        drawContext.canvas.nativeCanvas.drawRect(9f * density, -8f * density, 10.5f * density, 0f, brassPaint)
                        // Smokestack
                        drawContext.canvas.nativeCanvas.drawRect(9f * density, -13f * density, 12.5f * density, -8f * density, boilerPaint)
                        drawContext.canvas.nativeCanvas.drawRect(8f * density, -14.5f * density, 13.5f * density, -13f * density, brassPaint)
                        // Cowcatcher / Pilot
                        val plow = android.graphics.Path().apply {
                            moveTo(14f * density, 0f)
                            lineTo(18f * density, 0f)
                            lineTo(14f * density, -4f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(plow, cowcatcherPaint)

                        // Cab
                        drawContext.canvas.nativeCanvas.drawRoundRect(-10f * density, -12.5f * density, 0f, 0f, 1.5f * density, 1.5f * density, cabPaint)
                        // Cab Window
                        drawContext.canvas.nativeCanvas.drawRoundRect(-8f * density, -10.5f * density, -3f * density, -5.5f * density, 1f * density, 1f * density, windowLightPaint)

                        // Wheels (Engine)
                        drawContext.canvas.nativeCanvas.drawCircle(-6f * density, 0f, 3.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-6f * density, 0f, 1.6f * density, wheelRimPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(4f * density, 0f, 3.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(4f * density, 0f, 1.6f * density, wheelRimPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(11f * density, 0f, 2.2f * density, wheelTirePaint)

                        // Piston connecting rod
                        val rodPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFCBD5E1.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.2f * density
                        }
                        val pistonY = kotlin.math.sin(currentMs * 0.02f) * 1.2f * density
                        drawContext.canvas.nativeCanvas.drawLine(-6f * density, pistonY, 4f * density, pistonY, rodPaint)

                        // B. Coal Tender (-14dp -> -32dp)
                        drawContext.canvas.nativeCanvas.drawLine(-10f * density, -2f * density, -14f * density, -2f * density, couplingPaint)
                        val tenderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF0F172A.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val coalPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF020617.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawRoundRect(-32f * density, -7f * density, -14f * density, 0f, 1.5f * density, 1.5f * density, tenderPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-23f * density, -7.5f * density, 3.5f * density, coalPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-27f * density, 0f, 2.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-19f * density, 0f, 2.2f * density, wheelTirePaint)

                        // C. Passenger Coach 1 - Royal Blue (-36dp -> -60dp)
                        drawContext.canvas.nativeCanvas.drawLine(-32f * density, -2f * density, -36f * density, -2f * density, couplingPaint)
                        val coach1Paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF2563EB.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawRoundRect(-60f * density, -10f * density, -36f * density, 0f, 1.5f * density, 1.5f * density, coach1Paint)
                        // Windows
                        drawContext.canvas.nativeCanvas.drawRect(-56f * density, -8f * density, -51f * density, -4f * density, windowLightPaint)
                        drawContext.canvas.nativeCanvas.drawRect(-47f * density, -8f * density, -42f * density, -4f * density, windowLightPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-53f * density, 0f, 2.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-43f * density, 0f, 2.2f * density, wheelTirePaint)

                        // D. Passenger Coach 2 - Emerald Green (-64dp -> -88dp)
                        drawContext.canvas.nativeCanvas.drawLine(-60f * density, -2f * density, -64f * density, -2f * density, couplingPaint)
                        val coach2Paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF059669.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawRoundRect(-88f * density, -10f * density, -64f * density, 0f, 1.5f * density, 1.5f * density, coach2Paint)
                        // Windows
                        drawContext.canvas.nativeCanvas.drawRect(-84f * density, -8f * density, -79f * density, -4f * density, windowLightPaint)
                        drawContext.canvas.nativeCanvas.drawRect(-75f * density, -8f * density, -70f * density, -4f * density, windowLightPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-81f * density, 0f, 2.2f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-71f * density, 0f, 2.2f * density, wheelTirePaint)
                    } else {
                        // ==========================================
                        // ROYAL GOLDEN NOBLE TRAIN (4 NOBLE CARRIAGES)
                        // ==========================================
                        val nobleGoldPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFF59E0B.toInt() // Royal Gold
                            style = android.graphics.Paint.Style.FILL
                        }
                        val nobleGlintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFFDE047.toInt()
                            style = android.graphics.Paint.Style.FILL
                        }
                        val noblePurplePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF581C87.toInt() // Imperial Purple
                            style = android.graphics.Paint.Style.FILL
                        }
                        val royalCrownPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFFACC15.toInt() // Imperial Crown
                            style = android.graphics.Paint.Style.FILL
                        }

                        // A. Gilded Royal Locomotive
                        // Golden Boiler
                        drawContext.canvas.nativeCanvas.drawRoundRect(0f, -8f * density, 15f * density, 0f, 2f * density, 2f * density, nobleGoldPaint)
                        drawContext.canvas.nativeCanvas.drawRect(4f * density, -8f * density, 6f * density, 0f, nobleGlintPaint)
                        drawContext.canvas.nativeCanvas.drawRect(10f * density, -8f * density, 12f * density, 0f, nobleGlintPaint)
                        // Gilded Smokestack
                        drawContext.canvas.nativeCanvas.drawRect(10f * density, -13.5f * density, 13.5f * density, -8f * density, nobleGoldPaint)
                        drawContext.canvas.nativeCanvas.drawRect(9f * density, -15f * density, 14.5f * density, -13.5f * density, nobleGlintPaint)

                        // Royal Purple Cab
                        drawContext.canvas.nativeCanvas.drawRoundRect(-10f * density, -13f * density, 0f, 0f, 1.5f * density, 1.5f * density, noblePurplePaint)
                        drawContext.canvas.nativeCanvas.drawRoundRect(-8.5f * density, -11f * density, -3f * density, -6f * density, 1f * density, 1f * density, windowLightPaint)

                        // Imperial Crown on Cab Roof
                        val crown = android.graphics.Path().apply {
                            moveTo(-9f * density, -13f * density)
                            lineTo(-9f * density, -16.5f * density)
                            lineTo(-6.5f * density, -14.5f * density)
                            lineTo(-4.5f * density, -17.5f * density)
                            lineTo(-2.5f * density, -14.5f * density)
                            lineTo(0f, -16.5f * density)
                            lineTo(0f, -13f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(crown, royalCrownPaint)

                        // Gilded Wheels
                        drawContext.canvas.nativeCanvas.drawCircle(-6f * density, 0f, 3.4f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-6f * density, 0f, 2.1f * density, nobleGoldPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(4f * density, 0f, 3.4f * density, wheelTirePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(4f * density, 0f, 2.1f * density, nobleGoldPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(12f * density, 0f, 2.4f * density, nobleGoldPaint)

                        // B. 4 Royal Noble Carriages (Nobles 1, 2, 3, 4)
                        for (nobleIdx in 0 until 4) {
                            val startCarX = -14f * density - (nobleIdx * 28f * density)
                            val endCarX = startCarX - 22f * density

                            // Coupling link
                            drawContext.canvas.nativeCanvas.drawLine(startCarX + 4f * density, -2f * density, startCarX, -2f * density, couplingPaint)

                            // Noble Coach
                            drawContext.canvas.nativeCanvas.drawRoundRect(endCarX, -10.5f * density, startCarX, 0f, 2f * density, 2f * density, noblePurplePaint)
                            drawContext.canvas.nativeCanvas.drawRect(endCarX, -10.5f * density, startCarX, -9f * density, nobleGoldPaint) // Gold roof trim

                            // Noble Window & Gold Heraldry Crest
                            drawContext.canvas.nativeCanvas.drawRoundRect(endCarX + 4f * density, -8.5f * density, endCarX + 10f * density, -4f * density, 1f * density, 1f * density, windowLightPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(endCarX + 16f * density, -6.2f * density, 2.2f * density, royalCrownPaint) // Noble Crest

                            // Wheels
                            drawContext.canvas.nativeCanvas.drawCircle(endCarX + 5f * density, 0f, 2.4f * density, wheelTirePaint)
                            drawContext.canvas.nativeCanvas.drawCircle(endCarX + 5f * density, 0f, 1.2f * density, nobleGoldPaint)
                            drawContext.canvas.nativeCanvas.drawCircle(endCarX + 17f * density, 0f, 2.4f * density, wheelTirePaint)
                            drawContext.canvas.nativeCanvas.drawCircle(endCarX + 17f * density, 0f, 1.2f * density, nobleGoldPaint)
                        }
                    }

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Louie Red Nose Pitty Dog Paw Prints Easter Egg (Trotting Paws + Warm Red-Nose Copper Glow)
        if (louiePawsTriggerTime > 0L) {
            val pawsProgress = remember(louiePawsTriggerTime) { Animatable(0f) }
            LaunchedEffect(louiePawsTriggerTime) {
                pawsProgress.snapTo(0f)
                pawsProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4400, easing = LinearEasing),
                )
                louiePawsTriggerTime = 0L
            }
            if (pawsProgress.value in 0.001f..0.999f) {
                val currentMs = pawsProgress.value * 4400f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // 5 Trotting Dog Paw Prints (Left-Right-Left-Right Gait)
                    val pawSteps = listOf(
                        Triple(canvasW * 0.16f, canvasH * 0.72f, -24f),
                        Triple(canvasW * 0.32f, canvasH * 0.50f, -16f),
                        Triple(canvasW * 0.48f, canvasH * 0.66f, -22f),
                        Triple(canvasW * 0.65f, canvasH * 0.44f, -14f),
                        Triple(canvasW * 0.82f, canvasH * 0.32f, -18f)
                    )

                    for ((idx, step) in pawSteps.withIndex()) {
                        val staggerMs = idx * 280f
                        val localMs = currentMs - staggerMs
                        if (localMs <= 0f) continue

                        val totalLife = 3200f
                        val lifeU = (localMs / totalLife).coerceIn(0f, 1f)
                        if (lifeU >= 1f) continue

                        // Soft bounce impact on stamp down (0-300ms), hold, and gentle warm fade out (2400-3200ms)
                        val alpha = when {
                            localMs < 300f -> (localMs / 300f).coerceIn(0f, 1f)
                            localMs > 2400f -> ((totalLife - localMs) / 800f).coerceIn(0f, 1f)
                            else -> 1f
                        }

                        val stampBounce = if (localMs < 300f) {
                            val st = localMs / 300f
                            1f + kotlin.math.sin(st * Math.PI.toFloat()) * 0.35f
                        } else {
                            1f
                        }

                        val (px, py, baseAngle) = step

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(px, py)
                        drawContext.canvas.nativeCanvas.rotate(baseAngle)
                        drawContext.canvas.nativeCanvas.scale(stampBounce, stampBounce)

                        val pr = 10f * density // Dog paw radius

                        // Red Nose Pitty Color Palette: Rich warm chestnut / copper terracotta
                        val pittyAuraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 90).toInt().coerceIn(0, 255), 254, 215, 170) // Warm peach aura
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pittyMainPadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 194, 65, 12) // Warm Copper Chestnut
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pittyToePadPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 234, 88, 12) // Terracotta Red Nose Hue
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pittyClawPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 230).toInt().coerceIn(0, 255), 124, 45, 18) // Dark mahogany claw points
                            style = android.graphics.Paint.Style.FILL
                        }

                        // 1. Soft Impact Ring Aura
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, pr * 1.5f, pittyAuraPaint)

                        // 2. Large Central Metacarpal Pad (Chunky dog palm with heart contour)
                        val mainPad = android.graphics.Path().apply {
                            moveTo(-pr * 0.72f, pr * 0.15f)
                            quadTo(-pr * 0.85f, -pr * 0.35f, -pr * 0.45f, -pr * 0.55f)
                            quadTo(0f, -pr * 0.25f, pr * 0.45f, -pr * 0.55f)
                            quadTo(pr * 0.85f, -pr * 0.35f, pr * 0.72f, pr * 0.15f)
                            quadTo(pr * 0.55f, pr * 0.75f, 0f, pr * 0.65f)
                            quadTo(-pr * 0.55f, pr * 0.75f, -pr * 0.72f, pr * 0.15f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(mainPad, pittyMainPadPaint)

                        // 3. 4 Oval Digital Toe Pads with Claw Points
                        val toeOffsets = listOf(
                            Pair(-pr * 0.68f, -pr * 0.85f),
                            Pair(-pr * 0.24f, -pr * 1.15f),
                            Pair(pr * 0.24f, -pr * 1.15f),
                            Pair(pr * 0.68f, -pr * 0.85f)
                        )
                        for ((tx, ty) in toeOffsets) {
                            drawContext.canvas.nativeCanvas.drawOval(tx - pr * 0.22f, ty - pr * 0.32f, tx + pr * 0.22f, ty + pr * 0.32f, pittyToePadPaint)
                            // Subtle cute claw tip
                            drawContext.canvas.nativeCanvas.drawCircle(tx, ty - pr * 0.40f, pr * 0.08f, pittyClawPaint)
                        }

                        // 4. Sweet Red Nose Pitty Heart Tribute on the final paw
                        if (idx == pawSteps.size - 1 && localMs > 200f) {
                            val heartU = ((localMs - 200f) / 1000f).coerceIn(0f, 1f)
                            val heartFloatY = -pr * 1.6f - (heartU * 12f * density)
                            val heartAlpha = (alpha * (1f - heartU * 0.3f)).coerceIn(0f, 1f)
                            val redNoseHeartPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((heartAlpha * 255).toInt().coerceIn(0, 255), 225, 29, 72) // Red Nose Heart
                                style = android.graphics.Paint.Style.FILL
                            }
                            val heartPath = android.graphics.Path().apply {
                                moveTo(0f, heartFloatY)
                                cubicTo(-pr * 0.4f, heartFloatY - pr * 0.5f, -pr * 0.7f, heartFloatY + pr * 0.1f, 0f, heartFloatY + pr * 0.7f)
                                cubicTo(pr * 0.7f, heartFloatY + pr * 0.1f, pr * 0.4f, heartFloatY - pr * 0.5f, 0f, heartFloatY)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(heartPath, redNoseHeartPaint)
                        }

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }

        // I, Robot NS-5 Hyper-Athletic Ultra-Smooth Supersonic Running Squad (Sonny Vault + VIKI Red Pursuit)
        if (irobotTriggerTime > 0L) {
            val irobotProgress = remember(irobotTriggerTime) { Animatable(0f) }
            LaunchedEffect(irobotTriggerTime) {
                irobotProgress.snapTo(0f)
                irobotProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 2400, easing = LinearEasing),
                )
                irobotTriggerTime = 0L
            }
            if (irobotProgress.value in 0.001f..0.999f) {
                val totalMs = 2400f
                val currentMs = irobotProgress.value * totalMs
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val rowCount = (keyboard.rowCount).coerceAtLeast(4)
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }
                    val fret1Y = fretYs.getOrNull(0) ?: (canvasH * 0.25f)
                    val fret2Y = fretYs.getOrNull(1) ?: (canvasH * 0.50f)
                    val fret3Y = fretYs.getOrNull(2) ?: (canvasH * 0.75f)

                    // Hyper-Athletic, Smooth Kinematic NS-5 Android Runner
                    fun drawRunningNS5(
                        robotX: Float,
                        baseY: Float,
                        jumpOffset: Float,
                        runCycleMs: Float,
                        isVikiRed: Boolean,
                        scaleFactor: Float = 1.15f,
                        trailColor: Int = 0x8800D2FF.toInt()
                    ) {
                        // High-cadence athletic stride frequency (~4.2 Hz)
                        val runPhase = runCycleMs * 0.026f
                        val isAirborne = kotlin.math.abs(jumpOffset) > 2f * density

                        val sinP = kotlin.math.sin(runPhase)
                        val cosP = kotlin.math.cos(runPhase)

                        // Fluid athletic running vertical oscillation
                        val runBobY = if (!isAirborne) -kotlin.math.abs(sinP) * (3.8f * density) else 0f
                        val robotY = baseY + jumpOffset + runBobY

                        // Natural Olympic Athlete Forward Sprint Lean (head & chest tilt forward into +X direction)
                        val leanAngle = if (isAirborne) 8f else (12f + sinP * 2.0f)

                        // Authentic Olympic athlete stride kinematics (smooth high-knee drive & rear leg extension)
                        val hip1 = if (isAirborne) 32f else (sinP * 44f)
                        val hip2 = if (isAirborne) -36f else (-sinP * 44f)
                        val knee1 = if (isAirborne) 55f else (sinP.coerceAtLeast(0f) * 65f + cosP * 12f).coerceAtLeast(0f)
                        val knee2 = if (isAirborne) 45f else ((-sinP).coerceAtLeast(0f) * 65f - cosP * 12f).coerceAtLeast(0f)
                        val ankle1 = if (isAirborne) -10f else (-sinP * 18f)
                        val ankle2 = if (isAirborne) 15f else (sinP * 18f)

                        // Dynamic runner arm drive (opposite to leg phase, bent at elbow)
                        val arm1 = if (isAirborne) -36f else (-sinP * 46f)
                        val arm2 = if (isAirborne) 36f else (sinP * 46f)
                        val elbow1 = if (isAirborne) 45f else (48f + cosP * 20f)
                        val elbow2 = if (isAirborne) 55f else (48f - cosP * 20f)

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(robotX, robotY)
                        drawContext.canvas.nativeCanvas.scale(scaleFactor, scaleFactor)
                        drawContext.canvas.nativeCanvas.rotate(leanAngle)

                        // 1. Triple Multi-Frame Ghost Motion Blur Speed Trails
                        val ghostOffsets = listOf(
                            Pair(-9f * density, 0.40f),
                            Pair(-18f * density, 0.22f),
                            Pair(-28f * density, 0.10f)
                        )
                        for ((gx, ga) in ghostOffsets) {
                            val ghostPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((ga * 255).toInt().coerceIn(0, 255), (trailColor shr 16) and 0xFF, (trailColor shr 8) and 0xFF, trailColor and 0xFF)
                                style = android.graphics.Paint.Style.STROKE
                                strokeWidth = 1.4f * density
                                strokeCap = android.graphics.Paint.Cap.ROUND
                            }
                            drawContext.canvas.nativeCanvas.drawLine(gx, -10f * density, gx - 16f * density, -10f * density, ghostPaint)
                            drawContext.canvas.nativeCanvas.drawLine(gx + 2f * density, -18f * density, gx - 14f * density, -18f * density, ghostPaint)
                        }

                        // 2. High-Tech Color Palette: Pearlescent White Armor + Dark Titanium Joints + Optics
                        val chassisPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFF8FAFC.toInt() // Pearlescent White Ceramic
                            style = android.graphics.Paint.Style.FILL
                        }
                        val jointPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFF1E293B.toInt() // Carbon-Titanium Joints
                            style = android.graphics.Paint.Style.FILL
                        }
                        val limbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = 0xFFE2E8F0.toInt()
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2.6f * density
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        val opticColor = if (isVikiRed) 0xFFEF4444.toInt() else 0xFF00D2FF.toInt()
                        val opticCoreColor = if (isVikiRed) 0xFFDC2626.toInt() else 0xFF38BDF8.toInt()
                        val eyeGlowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = opticColor
                            style = android.graphics.Paint.Style.FILL
                        }
                        val eyeCorePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = opticCoreColor
                            style = android.graphics.Paint.Style.FILL
                        }

                        // A. Back Leg (Sculpted Thigh -> Articulated Knee -> Calf -> Ankle -> Toe)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(-1.5f * density, -2f * density)
                        drawContext.canvas.nativeCanvas.rotate(hip2)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 8.5f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 8.5f * density, 1.5f * density, jointPaint) // Knee
                        drawContext.canvas.nativeCanvas.translate(0f, 8.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(knee2)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 8.5f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.translate(0f, 8.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(ankle2)
                        drawContext.canvas.nativeCanvas.drawRoundRect(-1f * density, -1f * density, 4.2f * density, 1.6f * density, 1f * density, 1f * density, jointPaint) // Sprinting Foot
                        drawContext.canvas.nativeCanvas.restore()

                        // B. Back Arm (Shoulder -> Bicep -> Elbow -> Forearm -> Clenched Fist)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(-1.5f * density, -14.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(arm2)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 6.8f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 6.8f * density, 1.3f * density, jointPaint)
                        drawContext.canvas.nativeCanvas.translate(0f, 6.8f * density)
                        drawContext.canvas.nativeCanvas.rotate(elbow2)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 6.2f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 6.2f * density, 1.4f * density, chassisPaint) // Clenched Fist
                        drawContext.canvas.nativeCanvas.restore()

                        // C. Athletic Sculpted Torso (Tapered V-Shape Waist & Pelvis)
                        val torsoPath = android.graphics.Path().apply {
                            moveTo(-3.2f * density, -16.5f * density)
                            lineTo(4.2f * density, -16.5f * density) // Wide athletic shoulders
                            lineTo(2.2f * density, -2.5f * density)  // Tapered waist
                            lineTo(-2.2f * density, -2.5f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(torsoPath, chassisPaint)
                        // Glowing Chest Reactor Core (Sonny Blue vs VIKI Red)
                        drawContext.canvas.nativeCanvas.drawCircle(1.2f * density, -11.5f * density, 2.2f * density, eyeGlowPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(1.2f * density, -11.5f * density, 1.2f * density, eyeCorePaint)

                        // D. Neck Cervical Struts & Aerodynamic Sculpted NS-5 Head
                        drawContext.canvas.nativeCanvas.drawRect(-1f * density, -19f * density, 1f * density, -16.5f * density, jointPaint)
                        // Aerodynamic Streamlined Head Dome
                        val headPath = android.graphics.Path().apply {
                            moveTo(-2.8f * density, -25f * density)
                            cubicTo(0f, -27f * density, 5.2f * density, -26f * density, 5.2f * density, -21f * density) // Forward streamlined jaw
                            cubicTo(4.5f * density, -18.5f * density, 0f, -18.5f * density, -2.8f * density, -19.5f * density)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(headPath, chassisPaint)
                        // Piercing Photonic Optic Eyes (Glowing Cyan / Red)
                        drawContext.canvas.nativeCanvas.drawRoundRect(2.8f * density, -23.5f * density, 5.6f * density, -21.2f * density, 1f * density, 1f * density, eyeGlowPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(4.2f * density, -22.3f * density, 0.7f * density, eyeCorePaint)

                        // E. Front Leg (Sculpted Thigh -> Knee -> Calf -> Ankle -> Toe)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(1.5f * density, -2f * density)
                        drawContext.canvas.nativeCanvas.rotate(hip1)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 8.5f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 8.5f * density, 1.5f * density, jointPaint)
                        drawContext.canvas.nativeCanvas.translate(0f, 8.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(knee1)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 8.5f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.translate(0f, 8.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(ankle1)
                        drawContext.canvas.nativeCanvas.drawRoundRect(-1f * density, -1f * density, 4.2f * density, 1.6f * density, 1f * density, 1f * density, jointPaint)
                        drawContext.canvas.nativeCanvas.restore()

                        // F. Front Arm (Shoulder -> Bicep -> Elbow -> Forearm -> Clenched Fist)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(1.5f * density, -14.5f * density)
                        drawContext.canvas.nativeCanvas.rotate(arm1)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 6.8f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 6.8f * density, 1.3f * density, jointPaint)
                        drawContext.canvas.nativeCanvas.translate(0f, 6.8f * density)
                        drawContext.canvas.nativeCanvas.rotate(elbow1)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 6.2f * density, limbPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(0f, 6.2f * density, 1.4f * density, chassisPaint)
                        drawContext.canvas.nativeCanvas.restore()

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =======================================================
                    // 1. SONNY (The Hero NS-5 Leader on Middle Fret: 0ms -> 1800ms)
                    // =======================================================
                    val sonnyU = (currentMs / 1800f).coerceIn(0f, 1f)
                    val sonnyStartX = -50f * density
                    val sonnyEndX = canvasW + 55f * density
                    val sonnyX = sonnyStartX + sonnyU * (sonnyEndX - sonnyStartX)

                    // Acrobatic Parkour High Hurdle Vault in the center (u in 0.35 -> 0.65)
                    val jumpOffset = if (sonnyU in 0.35f..0.65f) {
                        val ju = (sonnyU - 0.35f) / 0.30f
                        -kotlin.math.sin(ju * Math.PI.toFloat()) * (28f * density)
                    } else {
                        0f
                    }

                    if (sonnyU < 1f) {
                        drawRunningNS5(
                            robotX = sonnyX,
                            baseY = fret2Y - 1f * density,
                            jumpOffset = jumpOffset,
                            runCycleMs = currentMs,
                            isVikiRed = false,
                            scaleFactor = 1.25f,
                            trailColor = 0xAA00D2FF.toInt()
                        )
                    }

                    // =======================================================
                    // 2. VIKI RED PURSUIT ROBOT #1 (Top Fret: 150ms -> 2000ms)
                    // =======================================================
                    if (currentMs > 150f) {
                        val viki1U = ((currentMs - 150f) / 1850f).coerceIn(0f, 1f)
                        if (viki1U < 1f) {
                            val viki1X = -55f * density + viki1U * (canvasW + 90f * density)
                            drawRunningNS5(
                                robotX = viki1X,
                                baseY = fret1Y - 1f * density,
                                jumpOffset = 0f,
                                runCycleMs = currentMs + 100f,
                                isVikiRed = true,
                                scaleFactor = 1.10f,
                                trailColor = 0xAAEF4444.toInt()
                            )
                        }
                    }

                    // =======================================================
                    // 3. VIKI RED PURSUIT ROBOT #2 (Bottom Fret: 300ms -> 2200ms)
                    // =======================================================
                    if (currentMs > 300f) {
                        val viki2U = ((currentMs - 300f) / 1900f).coerceIn(0f, 1f)
                        if (viki2U < 1f) {
                            val viki2X = -60f * density + viki2U * (canvasW + 95f * density)
                            drawRunningNS5(
                                robotX = viki2X,
                                baseY = fret3Y - 1f * density,
                                jumpOffset = 0f,
                                runCycleMs = currentMs + 200f,
                                isVikiRed = true,
                                scaleFactor = 1.15f,
                                trailColor = 0xAAEF4444.toInt()
                            )
                        }
                    }
                }
            }
        }

        // Android Little Green Dude (Canonical Google Bugdroid: Skitter Waddle, Multi-Fret Parkour Leaps, 360 Spin & Joy Dance)
        if (androidBugdroidTriggerTime > 0L) {
            val bugdroidProgress = remember(androidBugdroidTriggerTime) { Animatable(0f) }
            LaunchedEffect(androidBugdroidTriggerTime) {
                bugdroidProgress.snapTo(0f)
                bugdroidProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 5400, easing = LinearEasing),
                )
                androidBugdroidTriggerTime = 0L
            }
            if (bugdroidProgress.value in 0.001f..0.999f) {
                val totalMs = 5400f
                val currentMs = bugdroidProgress.value * totalMs
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val rowCount = (keyboard.rowCount).coerceAtLeast(4)
                    val fretYs = (1 until rowCount).map { row -> (canvasH / rowCount) * row }
                    val fret1Y = fretYs.getOrNull(0) ?: (canvasH * 0.25f)
                    val fret2Y = fretYs.getOrNull(1) ?: (canvasH * 0.50f)
                    val fret3Y = fretYs.getOrNull(2) ?: (canvasH * 0.75f)

                    // Master Alpha: Pop in (0-300ms), Active (300-4800ms), Melt out (4800-5400ms)
                    val alpha = when {
                        currentMs < 300f -> (currentMs / 300f).coerceIn(0f, 1f)
                        currentMs > 4800f -> ((totalMs - currentMs) / 600f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    // -------------------------------------------------------------
                    // Multi-Fret Dynamic Choreography & Movement Trajectory
                    // -------------------------------------------------------------
                    // Act 1 (0ms -> 1800ms): Skitter-waddle along Bottom Fret (Left -> Center-Right)
                    // Act 2 (1800ms -> 3400ms): Trampoline High Jump up to Middle & Top Frets with 360° Spin
                    // Act 3 (3400ms -> 5400ms): Land on Center Stage, Joyful Wiggle Dance & Double-Arm Wave
                    var bugdroidX = canvasW * 0.5f
                    var bugdroidY = fret3Y - 8f * density
                    var rotationAngle = 0f
                    var isAirborne = false
                    var waddlePhase = 0f

                    when {
                        currentMs < 1800f -> {
                            val u1 = (currentMs / 1800f).coerceIn(0f, 1f)
                            bugdroidX = (canvasW * 0.15f) + u1 * (canvasW * 0.40f)
                            waddlePhase = currentMs * 0.024f
                            val waddleBob = kotlin.math.abs(kotlin.math.sin(waddlePhase)) * (3.5f * density)
                            bugdroidY = fret3Y - (7f * density) - waddleBob
                            rotationAngle = kotlin.math.sin(waddlePhase) * 6f
                        }
                        currentMs < 3400f -> {
                            val u2 = ((currentMs - 1800f) / 1600f).coerceIn(0f, 1f)
                            isAirborne = true
                            // S-curve trajectory: from (0.55W, fret3Y) -> (0.80W, fret1Y) -> (0.50W, fret2Y)
                            bugdroidX = (canvasW * 0.55f) + kotlin.math.sin(u2 * Math.PI.toFloat()) * (canvasW * 0.25f)
                            val parabolicJump = -kotlin.math.sin(u2 * Math.PI.toFloat()) * (canvasH * 0.38f)
                            val fretTransitionY = fret3Y + u2 * (fret2Y - fret3Y)
                            bugdroidY = fretTransitionY + parabolicJump
                            rotationAngle = u2 * 360f // Joyous 360 backflip
                        }
                        else -> {
                            val u3 = ((currentMs - 3400f) / 2000f).coerceIn(0f, 1f)
                            bugdroidX = canvasW * 0.50f
                            val danceBob = kotlin.math.sin(u3 * 16f) * (2f * density)
                            bugdroidY = (fret2Y + 8f * density) + danceBob
                            rotationAngle = kotlin.math.sin(u3 * 12f) * 4f
                        }
                    }

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(bugdroidX, bugdroidY)
                    drawContext.canvas.nativeCanvas.rotate(rotationAngle)

                    val R = 8.5f * density // Canonical Bugdroid Head Radius Unit

                    // 1. Neon Green Android Aura & Stardust Sparkles during Jump
                    val auraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 85).toInt().coerceIn(0, 255), 61, 220, 132)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, R * 2.6f, auraPaint)

                    if (isAirborne) {
                        val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 254, 240, 138)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (spIdx in 0 until 8) {
                            val ang = spIdx * (Math.PI.toFloat() / 4f) + (currentMs * 0.01f)
                            val dist = R * (2.2f + (spIdx % 3) * 0.4f)
                            val sx = kotlin.math.cos(ang) * dist
                            val sy = kotlin.math.sin(ang) * dist
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, 1.3f * density, sparkPaint)
                        }
                    }

                    // 2. Canonical Android Green Paints (#3DDC84)
                    val bugdroidGreenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 61, 220, 132) // Official Android #3DDC84
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyeWhitePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val antennaLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((alpha * 255).toInt().coerceIn(0, 255), 61, 220, 132)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 1.6f * density
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }

                    // ==============================================================
                    // A. CANONICAL BUGDROID HEAD (Exact Semi-Circle + Collar Gap)
                    // ==============================================================
                    // Semi-circular dome from -R to +R with flat base at Y = 0
                    val headPath = android.graphics.Path().apply {
                        moveTo(-R, 0f)
                        arcTo(-R, -R, R, R, 180f, 180f, false)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(headPath, bugdroidGreenPaint)

                    // 2 White Round Eyes placed symmetrically at (+/- 0.42*R, -0.45*R)
                    val eyeRadius = 1.0f * density
                    drawContext.canvas.nativeCanvas.drawCircle(-R * 0.42f, -R * 0.45f, eyeRadius, eyeWhitePaint)
                    drawContext.canvas.nativeCanvas.drawCircle(R * 0.42f, -R * 0.45f, eyeRadius, eyeWhitePaint)

                    // 2 Symmetrical Antennae tilted at exact 30° with dynamic wiggle
                    val antennaWiggle = kotlin.math.sin(currentMs * 0.018f) * 5f
                    // Left Antenna (at -0.45*R on skull curve, tilted -30°)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(-R * 0.45f, -R * 0.82f)
                    drawContext.canvas.nativeCanvas.rotate(-30f + antennaWiggle)
                    drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, -R * 0.45f, antennaLinePaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // Right Antenna (at +0.45*R on skull curve, tilted +30°)
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(R * 0.45f, -R * 0.82f)
                    drawContext.canvas.nativeCanvas.rotate(30f - antennaWiggle)
                    drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, -R * 0.45f, antennaLinePaint)
                    drawContext.canvas.nativeCanvas.restore()

                    // ==============================================================
                    // B. CANONICAL TORSO (Flat Top Shoulders + Rounded Bottom Corners)
                    // ==============================================================
                    // Clear horizontal collar gap between head base (Y=0) and torso top (Y=collarGap)
                    val collarGap = 1.4f * density
                    val torsoTop = collarGap
                    val torsoH = R * 1.55f
                    val torsoBottom = torsoTop + torsoH
                    val cornerR = 2.8f * density

                    val torsoPath = android.graphics.Path().apply {
                        moveTo(-R, torsoTop)
                        lineTo(R, torsoTop) // Flat sharp top shoulders
                        lineTo(R, torsoBottom - cornerR)
                        quadTo(R, torsoBottom, R - cornerR, torsoBottom)
                        lineTo(-R + cornerR, torsoBottom)
                        quadTo(-R, torsoBottom, -R, torsoBottom - cornerR)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(torsoPath, bugdroidGreenPaint)

                    // ==============================================================
                    // C. CANONICAL PILL ARMS (Standalone Rounded Capsules on Left & Right)
                    // ==============================================================
                    val armW = 1.8f * density
                    val armH = R * 1.25f
                    val armGapX = R * 1.35f

                    // Dynamic arm angles based on current action (skitter, jump, or double wave)
                    val leftArmAngle = when {
                        currentMs > 3400f -> -60f + kotlin.math.sin(currentMs * 0.015f) * 20f // Double wave cheer
                        isAirborne -> -40f
                        else -> kotlin.math.sin(waddlePhase) * 28f // Waddle swing
                    }
                    val rightArmAngle = when {
                        currentMs > 3400f -> 60f - kotlin.math.sin(currentMs * 0.015f) * 20f // Double wave cheer
                        isAirborne -> 40f
                        else -> -kotlin.math.sin(waddlePhase) * 28f // Waddle swing
                    }

                    // Left Pill Arm
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(-armGapX, torsoTop + armW)
                    drawContext.canvas.nativeCanvas.rotate(leftArmAngle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -armW, -armW, armW, armH,
                        armW, armW, bugdroidGreenPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()

                    // Right Pill Arm
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(armGapX, torsoTop + armW)
                    drawContext.canvas.nativeCanvas.rotate(rightArmAngle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -armW, -armW, armW, armH,
                        armW, armW, bugdroidGreenPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()

                    // ==============================================================
                    // D. CANONICAL PILL LEGS (2 Standalone Rounded Capsules under Torso)
                    // ==============================================================
                    val legW = 1.8f * density
                    val legH = R * 0.70f
                    val legGapX = R * 0.45f
                    val legTop = torsoBottom - 1f * density

                    val leftLegAngle = if (!isAirborne) -kotlin.math.sin(waddlePhase) * 20f else -15f
                    val rightLegAngle = if (!isAirborne) kotlin.math.sin(waddlePhase) * 20f else 15f

                    // Left Pill Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(-legGapX, legTop)
                    drawContext.canvas.nativeCanvas.rotate(leftLegAngle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -legW, 0f, legW, legH,
                        legW, legW, bugdroidGreenPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()

                    // Right Pill Leg
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(legGapX, legTop)
                    drawContext.canvas.nativeCanvas.rotate(rightLegAngle)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        -legW, 0f, legW, legH,
                        legW, legW, bugdroidGreenPaint
                    )
                    drawContext.canvas.nativeCanvas.restore()

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }

        // Rose Petals in the Wind Easter Egg (Velvet Crimson Rose Petals Sweeping on Atmospheric Breeze)
        if (rosePetalsTriggerTime > 0L) {
            val roseProgress = remember(rosePetalsTriggerTime) { Animatable(0f) }
            LaunchedEffect(rosePetalsTriggerTime) {
                roseProgress.snapTo(0f)
                roseProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4600, easing = LinearEasing),
                )
                rosePetalsTriggerTime = 0L
            }
            if (roseProgress.value in 0.001f..0.999f) {
                val totalMs = 4600f
                val currentMs = roseProgress.value * totalMs
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Master Alpha: Soft romantic fade in (0-400ms), Hold, Poetic fade out (3900-4600ms)
                    val masterAlpha = when {
                        currentMs < 400f -> (currentMs / 400f).coerceIn(0f, 1f)
                        currentMs > 3900f -> ((totalMs - currentMs) / 700f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    // 1. Soft Warm Rose-Blush Atmosphere Glow across the Frets
                    val blushGlowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 45).toInt().coerceIn(0, 255), 251, 113, 133)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawRect(0f, 0f, canvasW, canvasH, blushGlowPaint)

                    // 2. 18 Wind-Blown Velvet Rose Petals
                    // Deterministic petal configs: (startRelX, startRelY, speedMul, swayFreq, swayAmp, spinRate, baseScale, colorVariant)
                    val petals = listOf(
                        floatArrayOf(-0.10f, 0.85f, 1.15f, 0.012f, 18f, 1.4f, 1.2f, 0f),
                        floatArrayOf(-0.05f, 0.65f, 0.95f, 0.015f, 14f, -1.8f, 0.9f, 1f),
                        floatArrayOf(-0.15f, 0.45f, 1.25f, 0.010f, 22f, 2.0f, 1.3f, 2f),
                        floatArrayOf(0.05f, 0.95f, 1.05f, 0.013f, 16f, -1.2f, 1.0f, 0f),
                        floatArrayOf(-0.08f, 0.30f, 1.30f, 0.016f, 20f, 2.4f, 0.85f, 1f),
                        floatArrayOf(0.12f, 0.75f, 1.10f, 0.014f, 15f, -1.6f, 1.15f, 2f),
                        floatArrayOf(-0.02f, 0.55f, 0.88f, 0.011f, 24f, 1.7f, 1.25f, 0f),
                        floatArrayOf(0.20f, 0.90f, 1.20f, 0.017f, 12f, -2.2f, 0.95f, 1f),
                        floatArrayOf(-0.12f, 0.70f, 1.00f, 0.013f, 19f, 1.5f, 1.10f, 2f),
                        floatArrayOf(0.02f, 0.40f, 1.22f, 0.015f, 17f, -1.9f, 1.30f, 0f),
                        floatArrayOf(-0.18f, 0.60f, 0.92f, 0.012f, 21f, 2.1f, 0.80f, 1f),
                        floatArrayOf(0.15f, 0.80f, 1.18f, 0.016f, 13f, -1.4f, 1.05f, 2f),
                        floatArrayOf(-0.06f, 0.50f, 1.08f, 0.014f, 18f, 1.8f, 1.20f, 0f),
                        floatArrayOf(0.08f, 0.35f, 1.28f, 0.011f, 23f, -2.5f, 0.90f, 1f),
                        floatArrayOf(-0.14f, 0.88f, 0.96f, 0.015f, 16f, 1.3f, 1.15f, 2f),
                        floatArrayOf(0.25f, 0.70f, 1.12f, 0.013f, 14f, -1.7f, 1.00f, 0f),
                        floatArrayOf(-0.04f, 0.42f, 1.24f, 0.017f, 20f, 2.3f, 0.85f, 1f),
                        floatArrayOf(0.18f, 0.52f, 1.04f, 0.012f, 15f, -1.5f, 1.25f, 2f)
                    )

                    // Palette: Velvet Crimson (#E11D48), Rich Rose (#BE123C), Soft Ruby (#FB7185)
                    val petalPaints = listOf(
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 225, 29, 72) // #E11D48
                            style = android.graphics.Paint.Style.FILL
                        },
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 190, 18, 60) // #BE123C
                            style = android.graphics.Paint.Style.FILL
                        },
                        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 251, 113, 133) // #FB7185
                            style = android.graphics.Paint.Style.FILL
                        }
                    )
                    val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 140).toInt().coerceIn(0, 255), 136, 19, 55) // #881337
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 0.8f * density
                    }
                    val dewdropPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 220).toInt().coerceIn(0, 255), 254, 240, 138)
                        style = android.graphics.Paint.Style.FILL
                    }

                    for ((idx, cfg) in petals.withIndex()) {
                        val startRelX = cfg[0]
                        val startRelY = cfg[1]
                        val speedMul = cfg[2]
                        val swayFreq = cfg[3]
                        val swayAmp = cfg[4]
                        val spinRate = cfg[5]
                        val baseScale = cfg[6]
                        val colorIdx = cfg[7].toInt()

                        // Progress along wind trajectory (Left/Bottom -> Right/Top)
                        val u = (currentMs * 0.00028f * speedMul).coerceIn(0f, 1.4f)
                        if (u > 1.3f) continue

                        val startX = canvasW * startRelX
                        val startY = canvasH * startRelY
                        val endX = canvasW * 1.18f
                        val endY = canvasH * -0.15f

                        val posX = startX + u * (endX - startX)
                        val swayY = kotlin.math.sin(currentMs * swayFreq + idx * 0.8f) * (swayAmp * density)
                        val posY = startY + u * (endY - startY) + swayY

                        // 3D Flutter and Tumble Rotation
                        val tumbleAngle = (currentMs * 0.06f * spinRate + idx * 45f) % 360f
                        val flutterX = kotlin.math.cos(currentMs * 0.008f * speedMul + idx) * baseScale

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(posX, posY)
                        drawContext.canvas.nativeCanvas.rotate(tumbleAngle)
                        drawContext.canvas.nativeCanvas.scale(flutterX, baseScale)

                        val pr = 6.5f * density // Base petal radius

                        // Curled Velvet Rose Petal Vector Path
                        val petalPath = android.graphics.Path().apply {
                            moveTo(0f, -pr * 1.2f)
                            cubicTo(-pr * 1.1f, -pr * 0.7f, -pr * 0.95f, pr * 0.7f, 0f, pr * 1.2f)
                            cubicTo(pr * 0.95f, pr * 0.7f, pr * 1.1f, -pr * 0.7f, 0f, -pr * 1.2f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(petalPath, petalPaints[colorIdx % petalPaints.size])
                        // Petal Central Vein
                        drawContext.canvas.nativeCanvas.drawLine(0f, -pr * 0.9f, 0f, pr * 0.8f, shadowPaint)

                        // Golden Dewdrop on selected petals
                        if (idx % 3 == 0) {
                            drawContext.canvas.nativeCanvas.drawCircle(pr * 0.3f, -pr * 0.2f, 0.9f * density, dewdropPaint)
                        }

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // 3. Floating Floral Stardust Pollen Sparkles
                    val pollenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 190).toInt().coerceIn(0, 255), 254, 240, 138)
                        style = android.graphics.Paint.Style.FILL
                    }
                    for (pIdx in 0 until 12) {
                        val pu = ((currentMs * 0.0003f + pIdx * 0.08f) % 1f)
                        val px = canvasW * pu
                        val py = (canvasH * (0.2f + (pIdx % 6) * 0.12f)) + kotlin.math.sin(currentMs * 0.005f + pIdx) * (10f * density)
                        val pAlpha = (kotlin.math.sin(pu * Math.PI.toFloat()) * masterAlpha).coerceIn(0f, 1f)
                        pollenPaint.alpha = (pAlpha * 200).toInt().coerceIn(0, 255)
                        drawContext.canvas.nativeCanvas.drawCircle(px, py, 1.2f * density, pollenPaint)
                    }
                }
            }
        }

        // 17. Subtle Integrated Xbox Achievement Unlocked Pop-Up (3.6s)
        if (xboxAchievementTriggerTime > 0L) {
            val xboxProgress = remember(xboxAchievementTriggerTime) { Animatable(0f) }
            LaunchedEffect(xboxAchievementTriggerTime) {
                xboxProgress.snapTo(0f)
                xboxProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3600, easing = LinearEasing),
                )
                xboxAchievementTriggerTime = 0L
            }
            if (xboxProgress.value in 0.001f..0.999f) {
                val totalMs = 3600f
                val currentMs = xboxProgress.value * totalMs
                val density = LocalDensity.current.density
                val progress = xboxProgress.value

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // Master Alpha (Smooth entrance 0..0.12, sustained 0.12..0.78, smooth dissolve 0.78..1.0)
                    val masterAlpha = when {
                        progress < 0.12f -> (progress / 0.12f).coerceIn(0f, 1f)
                        progress > 0.78f -> ((1f - progress) / 0.22f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    if (masterAlpha > 0.01f) {
                        val topFretY = 16f * density
                        val centerX = canvasW / 2f

                        // 1. Subtle Fret Laser Sheen along top bar
                        val sheenProg = ((progress * 1.8f) % 1.4f) - 0.2f
                        val sx = canvasW * sheenProg
                        val sheenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            shader = android.graphics.LinearGradient(
                                sx - 60f * density, topFretY, sx + 60f * density, topFretY,
                                intArrayOf(
                                    android.graphics.Color.argb(0, 16, 124, 16),
                                    android.graphics.Color.argb((masterAlpha * 140).toInt().coerceIn(0, 255), 82, 176, 67),
                                    android.graphics.Color.argb(0, 16, 124, 16)
                                ),
                                floatArrayOf(0f, 0.5f, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            strokeWidth = 1.8f * density
                            style = android.graphics.Paint.Style.STROKE
                        }
                        drawContext.canvas.nativeCanvas.drawLine(0f, topFretY, canvasW, topFretY, sheenPaint)

                        // 2. Compact Matte Achievement Pill
                        val pillWidth = if (progress < 0.2f) {
                            val u = (progress / 0.2f).coerceIn(0f, 1f)
                            val bounce = kotlin.math.sin(u * Math.PI.toFloat() * 0.5f)
                            (32f + bounce * 170f) * density
                        } else {
                            202f * density
                        }
                        val pillHeight = 22f * density
                        val pillLeft = centerX - pillWidth / 2f
                        val pillTop = topFretY - pillHeight / 2f
                        val pillRight = centerX + pillWidth / 2f
                        val pillBottom = topFretY + pillHeight / 2f
                        val cornerR = pillHeight / 2f

                        val pillBgPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 230).toInt().coerceIn(0, 255), 13, 31, 18)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val pillBorderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 210).toInt().coerceIn(0, 255), 16, 124, 16)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.1f * density
                        }

                        val pillRect = android.graphics.RectF(pillLeft, pillTop, pillRight, pillBottom)
                        drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, cornerR, cornerR, pillBgPaint)
                        drawContext.canvas.nativeCanvas.drawRoundRect(pillRect, cornerR, cornerR, pillBorderPaint)

                        // 3. Miniature Glowing Xbox 360 Sphere Icon on left of pill
                        val orbCenterX = pillLeft + 12f * density
                        val orbCenterY = topFretY
                        val orbR = 7f * density

                        // Orb Body
                        val orbPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 24, 26, 27)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(orbCenterX, orbCenterY, orbR, orbPaint)

                        // 4 Quadrant LED Arcs
                        val ledRingPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.4f * density
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        val arcRect = android.graphics.RectF(orbCenterX - orbR + 0.8f * density, orbCenterY - orbR + 0.8f * density, orbCenterX + orbR - 0.8f * density, orbCenterY + orbR - 0.8f * density)
                        val qProgress = (progress / 0.25f).coerceIn(0f, 1f)

                        for (q in 0 until 4) {
                            val qStartAngle = q * 90f + 12f
                            val qSweep = 66f
                            val isLit = if (progress < 0.25f) qProgress >= (q * 0.25f) else true
                            val qAlpha = if (isLit) (masterAlpha * 255).toInt() else (masterAlpha * 40).toInt()
                            ledRingPaint.color = android.graphics.Color.argb(qAlpha.coerceIn(0, 255), 82, 176, 67)
                            drawContext.canvas.nativeCanvas.drawArc(arcRect, qStartAngle, qSweep, false, ledRingPaint)
                        }

                        // 3D Xbox "X" Core
                        val xPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 240, 240, 240)
                            strokeWidth = 1.2f * density
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            style = android.graphics.Paint.Style.STROKE
                        }
                        val xr = 3.6f * density
                        drawContext.canvas.nativeCanvas.drawLine(orbCenterX - xr * 0.7f, orbCenterY - xr * 0.7f, orbCenterX + xr * 0.7f, orbCenterY + xr * 0.7f, xPaint)
                        drawContext.canvas.nativeCanvas.drawLine(orbCenterX + xr * 0.7f, orbCenterY - xr * 0.7f, orbCenterX - xr * 0.7f, orbCenterY + xr * 0.7f, xPaint)

                        // 4. Achievement Text & Gamerscore (Rendered when pill is unfolded)
                        if (progress > 0.12f) {
                            val textAlpha = ((progress - 0.12f) / 0.1f).coerceIn(0f, 1f) * masterAlpha

                            // Gamerscore G icon
                            val gBadgeX = orbCenterX + 16f * density
                            val gPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((textAlpha * 255).toInt().coerceIn(0, 255), 82, 176, 67)
                                textSize = 9.5f * density
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                            drawContext.canvas.nativeCanvas.drawText("(G) 1000G", gBadgeX, topFretY + 3.4f * density, gPaint)

                            // "Achievement Unlocked" Text
                            val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((textAlpha * 240).toInt().coerceIn(0, 255), 255, 255, 255)
                                textSize = 9f * density
                                typeface = android.graphics.Typeface.DEFAULT
                                textAlign = android.graphics.Paint.Align.LEFT
                            }
                            drawContext.canvas.nativeCanvas.drawText("Achievement Unlocked", gBadgeX + 46f * density, topFretY + 3.2f * density, titlePaint)
                        }

                        // 5. Micro Emerald Sparkles
                        val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 190).toInt().coerceIn(0, 255), 155, 240, 11)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (s in 0 until 6) {
                            val su = (progress * 1.5f + s * 0.18f) % 1f
                            val sx = pillLeft + (s * 32f * density) % pillWidth
                            val sy = pillTop - su * (14f * density)
                            val sa = kotlin.math.sin(su * Math.PI.toFloat()) * masterAlpha
                            sparkPaint.alpha = (sa * 220).toInt().coerceIn(0, 255)
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, 1.1f * density, sparkPaint)
                        }
                    }
                }
            }
        }

        val exactSpaceKey = remember(keyboard) {
            keyboard.keys().asSequence().firstOrNull { key ->
                key.label?.equals("space", ignoreCase = true) == true ||
                key.computedData.code == 32
            }
        }

        // 18. Delayed "Hidden" Hooded Assassin Easter Egg (Fires 8.0s after "hidden" is typed, runs for 3.5s)
        val exactHKey = remember(keyboard) {
            keyboard.keys().asSequence().firstOrNull { key ->
                key.label?.equals("h", ignoreCase = true) == true ||
                key.computedData.code == 'h'.code ||
                key.computedData.code == 'H'.code
            }
        }
        if (hiddenHoodedTriggerTime > 0L) {
            val hiddenProgress = remember(hiddenHoodedTriggerTime) { Animatable(0f) }
            LaunchedEffect(hiddenHoodedTriggerTime) {
                // 8.0 seconds suspense delay after word was typed
                kotlinx.coroutines.delay(8000L)
                hiddenProgress.snapTo(0f)
                hiddenProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3500, easing = LinearEasing),
                )
                hiddenHoodedTriggerTime = 0L
            }
            if (hiddenProgress.value in 0.001f..0.999f) {
                val progress = hiddenProgress.value
                val totalMs = 3500f
                val density = LocalDensity.current.density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height
                    val d = density

                    // Exact physical H Key location dynamically extracted from current device keyboard layout
                    val hBounds = exactHKey?.visibleBounds
                    val hLeft = hBounds?.left ?: (canvasW * 0.50f)
                    val hRight = hBounds?.right ?: (canvasW * 0.60f)
                    val hTop = hBounds?.top ?: (canvasH * 0.38f)
                    val hBottom = hBounds?.bottom ?: (canvasH * 0.60f)
                    val hCenterX = hBounds?.center?.x ?: ((hLeft + hRight) / 2f)
                    val hCenterY = hBounds?.center?.y ?: ((hTop + hBottom) / 2f)
                    val hWidth = hRight - hLeft
                    val hHeight = hBottom - hTop
                    val fretY = hBottom // The middle fret line right at the base of the H key

                    // Master Alpha
                    val masterAlpha = when {
                        progress < 0.05f -> (progress / 0.05f).coerceIn(0f, 1f)
                        progress > 0.92f -> ((1f - progress) / 0.08f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    // 1. Curtains / H Key Splitting (Opens 0.0..0.22, stays open 0.22..0.78, closes 0.78..1.0)
                    val splitFraction = when {
                        progress < 0.22f -> (progress / 0.22f).coerceIn(0f, 1f)
                        progress > 0.78f -> (1f - (progress - 0.78f) / 0.22f).coerceIn(0f, 1f)
                        else -> 1f
                    }
                    val splitOffset = splitFraction * (hWidth * 0.52f)

                    if (splitFraction > 0.01f) {
                        // Portal Void inside the split H exactly matching physical key bounds
                        val voidPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 8, 8, 12)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val voidRect = android.graphics.RectF(hLeft, hTop, hRight, hBottom)
                        drawContext.canvas.nativeCanvas.drawRoundRect(voidRect, 6f * d, 6f * d, voidPaint)

                        // Shadow Smoke Wisp from Portal
                        val smokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 130 * splitFraction).toInt().coerceIn(0, 255), 76, 29, 149)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (i in 0 until 5) {
                            val su = ((progress * 3f + i * 0.2f) % 1f)
                            val sx = hCenterX + kotlin.math.sin(su * 6f + i) * (8f * d)
                            val sy = hCenterY - su * (22f * d)
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, (3f + su * 4f) * d, smokePaint)
                        }

                        // Left Curtain Half (Black/Charcoal Velvet exactly matching left half of H key)
                        val curtainPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            shader = android.graphics.LinearGradient(
                                hLeft - splitOffset, hCenterY,
                                hCenterX - splitOffset, hCenterY,
                                intArrayOf(
                                    android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 20, 20, 25),
                                    android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 38, 38, 45),
                                    android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 15, 20)
                                ),
                                floatArrayOf(0f, 0.7f, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            style = android.graphics.Paint.Style.FILL
                        }
                        val leftRect = android.graphics.RectF(
                            hLeft - splitOffset, hTop,
                            hCenterX - splitOffset, hBottom
                        )
                        drawContext.canvas.nativeCanvas.drawRoundRect(leftRect, 6f * d, 6f * d, curtainPaint)

                        // Right Curtain Half (Black/Charcoal Velvet exactly matching right half of H key)
                        val rightRect = android.graphics.RectF(
                            hCenterX + splitOffset, hTop,
                            hRight + splitOffset, hBottom
                        )
                        drawContext.canvas.nativeCanvas.drawRoundRect(rightRect, 6f * d, 6f * d, curtainPaint)

                        // Split "H" glyph halves on parted curtains
                        val hPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 240).toInt().coerceIn(0, 255), 245, 245, 245)
                            textSize = 15f * d
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            textAlign = android.graphics.Paint.Align.CENTER
                        }
                        val hGlyphY = hCenterY + 5.5f * d
                        // Left vertical bar of 'H'
                        drawContext.canvas.nativeCanvas.drawText("I", hCenterX - splitOffset - (hWidth * 0.16f), hGlyphY, hPaint)
                        // Right vertical bar of 'H'
                        drawContext.canvas.nativeCanvas.drawText("I", hCenterX + splitOffset + (hWidth * 0.16f), hGlyphY, hPaint)
                    }

                    // 2. Black Hooded Figure Leaping Out & Sprinting Left on the Fret
                    if (progress in 0.18f..0.82f) {
                        val runProgress = ((progress - 0.18f) / 0.64f).coerceIn(0f, 1f)
                        val startX = hCenterX
                        val endX = -45f * d
                        val currentX = startX + runProgress * (endX - startX)

                        // Vertical landing arc from H key down to fret
                        val currentY = if (runProgress < 0.15f) {
                            val dropU = (runProgress / 0.15f)
                            hCenterY + dropU * (fretY - hCenterY) - kotlin.math.sin(dropU * Math.PI.toFloat()) * (12f * d)
                        } else {
                            fretY
                        }

                        // Running Gait & Lean
                        val runPhase = runProgress * 42f
                        val legAngle1 = kotlin.math.sin(runPhase) * 35f
                        val legAngle2 = -kotlin.math.sin(runPhase) * 35f
                        val cloakFlutter = kotlin.math.sin(runPhase * 1.5f) * (6f * d)

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(currentX, currentY)

                        // Athletic forward sprint lean towards left (-18 degrees)
                        drawContext.canvas.nativeCanvas.rotate(-18f)

                        // Shadow Figure Body Paints
                        val cloakPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 10, 10, 14)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val cloakShadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 24, 24, 30)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // A. Trailing Shadow Smoke on Fret
                        val trailPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 90).toInt().coerceIn(0, 255), 30, 27, 75)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (tIdx in 0 until 4) {
                            val tx = (tIdx * 8f * d) + 4f * d
                            val ty = kotlin.math.sin(runPhase + tIdx) * (3f * d)
                            drawContext.canvas.nativeCanvas.drawCircle(tx, ty, (4f - tIdx * 0.8f) * d, trailPaint)
                        }

                        // B. Sprinting Legs
                        val legPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 15, 20)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2.2f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.rotate(legAngle2)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 10f * d, legPaint)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 10f * d, 4f * d, 14f * d, legPaint)
                        drawContext.canvas.nativeCanvas.restore()

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.rotate(legAngle1)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 0f, 0f, 10f * d, legPaint)
                        drawContext.canvas.nativeCanvas.drawLine(0f, 10f * d, -4f * d, 14f * d, legPaint)
                        drawContext.canvas.nativeCanvas.restore()

                        // C. Billowing Hooded Cloak Torso
                        val cloakPath = android.graphics.Path().apply {
                            moveTo(-3f * d, -14f * d)
                            lineTo(10f * d + cloakFlutter, -2f * d)
                            lineTo(6f * d + cloakFlutter * 0.5f, 4f * d)
                            lineTo(-4f * d, 2f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(cloakPath, cloakPaint)

                        // D. Pointed Assassin Hood & Silhouette Head
                        val hoodPath = android.graphics.Path().apply {
                            moveTo(-6f * d, -16f * d)
                            lineTo(2f * d, -22f * d)
                            lineTo(4f * d, -14f * d)
                            lineTo(-4f * d, -12f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(hoodPath, cloakShadowPaint)

                        // E. Glowing Cyan Assassin Eyes
                        val eyePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 56, 189, 248)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(-3.2f * d, -15.5f * d, 0.9f * d, eyePaint)

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }


        // 19. Serenity Garden, Butterflies & Zen Koi (Sequential 35s Multi-Stage Happiness Cycle)
        if (serenityGardenTriggerTime > 0L) {
            val serenityProgress = remember(serenityGardenTriggerTime) { Animatable(0f) }
            LaunchedEffect(serenityGardenTriggerTime) {
                serenityProgress.snapTo(0f)
                serenityProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 52000, easing = LinearEasing),
                )
                serenityGardenTriggerTime = 0L
            }
            if (serenityProgress.value in 0.00001f..0.99999f) {
                val progress = serenityProgress.value
                val elapsedSec = progress * 52.0f
                val density = LocalDensity.current.density
                val d = density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    // =========================================================================
                    // STAGE 1: Smooth Botanical Vine (All the way to top) & Blossoms (0.0s - 12.0s)
                    // =========================================================================
                    if (elapsedSec in 0.0f..12.0f) {
                        // Smooth, continuous sub-pixel growth curve
                        val rawGrow = (elapsedSec / 5.0f).coerceIn(0f, 1f)
                        val growProgress = (1.0f - kotlin.math.cos(rawGrow * Math.PI.toFloat())) * 0.5f
                        val rawBloom = ((elapsedSec - 5.0f) / 5.0f).coerceIn(0f, 1f)
                        val bloomProgress = (1.0f - kotlin.math.cos(rawBloom * Math.PI.toFloat())) * 0.5f

                        val stageAlpha = if (elapsedSec > 10.0f) {
                            (1.0f - (elapsedSec - 10.0f) / 2.0f).coerceIn(0f, 1f)
                        } else if (elapsedSec < 0.5f) {
                            (0.35f + (elapsedSec / 0.5f) * 0.65f).coerceIn(0f, 1f)
                        } else {
                            1.0f
                        }

                        val stemPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((stageAlpha * 230).toInt().coerceIn(0, 255), 16, 185, 129)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.6f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        val leafPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((stageAlpha * 240).toInt().coerceIn(0, 255), 52, 211, 153)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Left & Right growing margins climbing all the way to top (y = 2dp)
                        val marginOffset = 6f * d
                        val totalSteps = 60
                        val currentSteps = (totalSteps * growProgress).toInt().coerceAtLeast(1)

                        for (side in 0..1) {
                            val isLeft = side == 0
                            val sign = if (isLeft) 1f else -1f
                            val baseX = if (isLeft) marginOffset else (canvasW - marginOffset)
                            val path = android.graphics.Path()
                            path.moveTo(baseX, canvasH)

                            for (i in 1..currentSteps) {
                                val u = i.toFloat() / totalSteps
                                // Climbs 100% of height all the way to top edge + curves gently inward at top
                                val py = canvasH - u * (canvasH - 3f * d)
                                val px = if (u < 0.85f) {
                                    baseX + kotlin.math.sin(u * 14f) * (4.5f * d) * sign
                                } else {
                                    // Gentle inward flourish along top corner fret
                                    val topU = (u - 0.85f) / 0.15f
                                    baseX + kotlin.math.sin(u * 14f) * (4.5f * d) * sign + (topU * 16f * d * sign)
                                }
                                path.lineTo(px, py)

                                // Smooth leaf buds unfurling progressively
                                if (i % 6 == 0) {
                                    val leafU = i.toFloat() / totalSteps
                                    val leafAge = ((growProgress - leafU) / 0.2f).coerceIn(0f, 1f)
                                    if (leafAge > 0.05f) {
                                        val leafAngle = kotlin.math.sin(u * 10f) * 45f + (30f * sign)
                                        drawContext.canvas.nativeCanvas.save()
                                        drawContext.canvas.nativeCanvas.translate(px, py)
                                        drawContext.canvas.nativeCanvas.rotate(leafAngle)
                                        drawContext.canvas.nativeCanvas.scale(leafAge, leafAge)
                                        val leafRect = android.graphics.RectF(0f, -1.8f * d, 5.5f * d, 1.8f * d)
                                        drawContext.canvas.nativeCanvas.drawOval(leafRect, leafPaint)
                                        drawContext.canvas.nativeCanvas.restore()
                                    }
                                }
                            }
                            drawContext.canvas.nativeCanvas.drawPath(path, stemPaint)

                            // Blossoming Flowers (unfurl from 5s to 10s smoothly)
                            if (bloomProgress > 0.02f) {
                                for (fIdx in 0..5) {
                                    val fu = 0.15f + fIdx * 0.15f
                                    if (fu <= growProgress) {
                                        val fy = canvasH - fu * (canvasH - 3f * d)
                                        val fx = if (fu < 0.85f) {
                                            baseX + kotlin.math.sin(fu * 14f) * (4.5f * d) * sign
                                        } else {
                                            val topU = (fu - 0.85f) / 0.15f
                                            baseX + kotlin.math.sin(fu * 14f) * (4.5f * d) * sign + (topU * 16f * d * sign)
                                        }
                                        val flowerScale = (bloomProgress * (1f + fIdx * 0.04f)).coerceIn(0f, 1f)

                                        drawContext.canvas.nativeCanvas.save()
                                        drawContext.canvas.nativeCanvas.translate(fx, fy)
                                        drawContext.canvas.nativeCanvas.scale(flowerScale, flowerScale)

                                        // 5 Blossom Petals (Soft Pink / Lilac Sakura)
                                        val petalPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            val petalColor = if (fIdx % 2 == 0) {
                                                android.graphics.Color.argb((stageAlpha * 240).toInt().coerceIn(0, 255), 244, 114, 182)
                                            } else {
                                                android.graphics.Color.argb((stageAlpha * 240).toInt().coerceIn(0, 255), 192, 132, 252)
                                            }
                                            color = petalColor
                                            style = android.graphics.Paint.Style.FILL
                                        }

                                        for (p in 0 until 5) {
                                            drawContext.canvas.nativeCanvas.save()
                                            drawContext.canvas.nativeCanvas.rotate(p * 72f)
                                            val petalRect = android.graphics.RectF(-1.2f * d, -4.5f * d, 1.2f * d, 0f)
                                            drawContext.canvas.nativeCanvas.drawOval(petalRect, petalPaint)
                                            drawContext.canvas.nativeCanvas.restore()
                                        }

                                        // Golden Blossom Core
                                        val corePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                            color = android.graphics.Color.argb((stageAlpha * 255).toInt().coerceIn(0, 255), 251, 191, 36)
                                            style = android.graphics.Paint.Style.FILL
                                        }
                                        drawContext.canvas.nativeCanvas.drawCircle(0f, 0f, 1.1f * d, corePaint)
                                        drawContext.canvas.nativeCanvas.restore()
                                    }
                                }
                            }
                        }
                    }

                    // =========================================================================
                    // STAGE 2: Authentic Richmond Birdwing (Ornithoptera richmondia) (15.0s - 17.8s)
                    // =========================================================================
                    if (elapsedSec in 15.0f..17.8f) {
                        val bf1U = ((elapsedSec - 15.0f) / 2.8f).coerceIn(0f, 1f)
                        val bf1Alpha = (kotlin.math.sin(bf1U * Math.PI.toFloat()) * 1.35f).coerceIn(0f, 1f)
                        val startX = -35f * d
                        val endX = canvasW + 35f * d
                        val currentX = startX + bf1U * (endX - startX)
                        // Majestic soaring canopy glide path
                        val currentY = (canvasH * 0.40f) + kotlin.math.sin(bf1U * 3f * Math.PI.toFloat()) * (20f * d)

                        // Deep, majestic wing flap cycle of large birdwings (~2.8 flaps/sec)
                        val flapCos = kotlin.math.cos(bf1U * 14f * Math.PI.toFloat())
                        val wingSpan = (18f * d) * (0.30f + 0.70f * kotlin.math.abs(flapCos))
                        val bankAngle = 10f + kotlin.math.cos(bf1U * 3f * Math.PI.toFloat()) * 14f

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(currentX, currentY)
                        drawContext.canvas.nativeCanvas.rotate(bankAngle)

                        // Authentic Color Palette (Male Richmond Birdwing)
                        val velvetBlack = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf1Alpha * 255).toInt().coerceIn(0, 255), 11, 13, 17)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val emeraldCosta = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf1Alpha * 255).toInt().coerceIn(0, 255), 16, 185, 129)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val emeraldGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf1Alpha * 240).toInt().coerceIn(0, 255), 52, 211, 153)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val scarletCollar = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf1Alpha * 255).toInt().coerceIn(0, 255), 239, 68, 68)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val goldenAbdomen = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf1Alpha * 255).toInt().coerceIn(0, 255), 251, 191, 36)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // -------------------------------------------------------------
                        // A. Elongated Triangular Forewings (Velvet Black + Emerald Costa)
                        // -------------------------------------------------------------
                        for (side in -1..1 step 2) {
                            val sign = side.toFloat()
                            val fwPath = android.graphics.Path().apply {
                                moveTo(0f, -2f * d)
                                lineTo(sign * wingSpan, -13f * d) // Apex
                                quadTo(sign * wingSpan * 0.95f, -6f * d, sign * wingSpan * 0.65f, 0f) // Outer margin
                                lineTo(0f, 0f) // Inner margin
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(fwPath, velvetBlack)

                            // Bold Iridescent Emerald Costa Stripe along upper leading edge
                            val costaPath = android.graphics.Path().apply {
                                moveTo(0f, -2f * d)
                                lineTo(sign * wingSpan, -13f * d)
                                quadTo(sign * wingSpan * 0.6f, -10f * d, 0f, -4f * d)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(costaPath, emeraldCosta)

                            // Sub-Marginal Emerald Streak in cell
                            val innerStreak = android.graphics.Path().apply {
                                moveTo(sign * wingSpan * 0.25f, -3f * d)
                                lineTo(sign * wingSpan * 0.70f, -6.5f * d)
                                lineTo(sign * wingSpan * 0.60f, -4.5f * d)
                                lineTo(sign * wingSpan * 0.20f, -1.8f * d)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(innerStreak, emeraldGlow)
                        }

                        // -------------------------------------------------------------
                        // B. Scalloped Hindwings (Emerald Green Field + Black Spots)
                        // -------------------------------------------------------------
                        for (side in -1..1 step 2) {
                            val sign = side.toFloat()
                            val hwWidth = wingSpan * 0.70f

                            // Hindwing outer black frame
                            val hwPath = android.graphics.Path().apply {
                                moveTo(0f, 0f)
                                lineTo(sign * hwWidth * 0.95f, 2f * d)
                                quadTo(sign * hwWidth * 0.85f, 9.5f * d, sign * hwWidth * 0.35f, 10.5f * d)
                                quadTo(0f, 8f * d, 0f, 2f * d)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(hwPath, velvetBlack)

                            // Vivid Emerald-Green Central Field
                            val hwGreenField = android.graphics.Path().apply {
                                moveTo(sign * 2f * d, 1f * d)
                                lineTo(sign * hwWidth * 0.80f, 2.8f * d)
                                quadTo(sign * hwWidth * 0.70f, 8.2f * d, sign * hwWidth * 0.35f, 8.8f * d)
                                quadTo(sign * 2f * d, 6.8f * d, sign * 2f * d, 2f * d)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(hwGreenField, emeraldCosta)

                            // Golden flash along inner abdominal margin
                            drawContext.canvas.nativeCanvas.drawOval(
                                android.graphics.RectF(sign * 0.5f * d, 2f * d, sign * 3.5f * d, 8f * d),
                                goldenAbdomen
                            )

                            // Characteristic 4 Sub-Marginal Black Spots in the emerald field
                            for (spot in 0 until 4) {
                                val su = 0.25f + spot * 0.20f
                                val spotX = sign * (su * hwWidth * 0.75f + 2f * d)
                                val spotY = 3.5f * d + spot * 1.3f * d
                                drawContext.canvas.nativeCanvas.drawCircle(spotX, spotY, 0.9f * d, velvetBlack)
                            }
                        }

                        // -------------------------------------------------------------
                        // C. Anatomy: Thorax with Green Stripe, Scarlet Collar & Golden Abdomen
                        // -------------------------------------------------------------
                        // 1. Signature Scarlet Red Patch on collar / under wing base
                        drawContext.canvas.nativeCanvas.drawCircle(0f, -4f * d, 1.8f * d, scarletCollar)

                        // 2. Abdomen (Bright golden yellow with black segment bands)
                        val abdomenRect = android.graphics.RectF(-1.2f * d, -1f * d, 1.2f * d, 9f * d)
                        drawContext.canvas.nativeCanvas.drawRoundRect(abdomenRect, 1.2f * d, 1.2f * d, goldenAbdomen)
                        val abSegPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = velvetBlack.color
                            strokeWidth = 0.7f * d
                            style = android.graphics.Paint.Style.STROKE
                        }
                        for (seg in 1..4) {
                            val sy = 1f * d + seg * 1.5f * d
                            drawContext.canvas.nativeCanvas.drawLine(-1f * d, sy, 1f * d, sy, abSegPaint)
                        }

                        // 3. Thorax (Velvet black with bright emerald green dorsal stripe)
                        val thoraxRect = android.graphics.RectF(-1.5f * d, -7f * d, 1.5f * d, -0.5f * d)
                        drawContext.canvas.nativeCanvas.drawRoundRect(thoraxRect, 1.5f * d, 1.5f * d, velvetBlack)
                        val greenStripePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = emeraldCosta.color
                            strokeWidth = 0.8f * d
                            style = android.graphics.Paint.Style.STROKE
                        }
                        drawContext.canvas.nativeCanvas.drawLine(0f, -6.5f * d, 0f, -1f * d, greenStripePaint)

                        // 4. Head & Antennae with club tips
                        drawContext.canvas.nativeCanvas.drawCircle(0f, -8f * d, 1.3f * d, velvetBlack)
                        val antPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = velvetBlack.color
                            strokeWidth = 0.7f * d
                            style = android.graphics.Paint.Style.STROKE
                        }
                        drawContext.canvas.nativeCanvas.drawLine(0f, -8f * d, -3.5f * d, -12.5f * d, antPaint)
                        drawContext.canvas.nativeCanvas.drawLine(0f, -8f * d, 3.5f * d, -12.5f * d, antPaint)
                        drawContext.canvas.nativeCanvas.drawCircle(-3.5f * d, -12.5f * d, 0.7f * d, velvetBlack)
                        drawContext.canvas.nativeCanvas.drawCircle(3.5f * d, -12.5f * d, 0.7f * d, velvetBlack)

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =========================================================================
                    // STAGE 3: Ulysses Butterfly (18.0s - 21.0s) - Right to Left
                    // =========================================================================
                    if (elapsedSec in 18.0f..21.0f) {
                        val bf2U = ((elapsedSec - 18.0f) / 3.0f).coerceIn(0f, 1f)
                        val bf2Alpha = (kotlin.math.sin(bf2U * Math.PI.toFloat()) * 1.3f).coerceIn(0f, 1f)
                        val startX = canvasW + 25f * d
                        val endX = -25f * d
                        val currentX = startX + bf2U * (endX - startX)
                        val currentY = (canvasH * 0.36f) + kotlin.math.sin(bf2U * 3f * Math.PI.toFloat() + 1.2f) * (20f * d)

                        val wingFlap = kotlin.math.cos(bf2U * 16f * Math.PI.toFloat())
                        val wingSpan = (13f * d) * (0.35f + 0.65f * kotlin.math.abs(wingFlap))

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(currentX, currentY)
                        drawContext.canvas.nativeCanvas.rotate(-15f - kotlin.math.cos(bf2U * 3f * Math.PI.toFloat()) * 12f)

                        // Ulysses: Deep Velvet Black Borders + Glowing Electric Sapphire Cyan Core
                        val ulyssesBlack = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf2Alpha * 255).toInt().coerceIn(0, 255), 15, 23, 42)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val electricBlue = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf2Alpha * 255).toInt().coerceIn(0, 255), 0, 229, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val royalBlue = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf2Alpha * 255).toInt().coerceIn(0, 255), 2, 132, 199)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // Black Wing Outer Perimeter
                        val leftWingPath = android.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(-wingSpan, -9f * d)
                            quadTo(-wingSpan * 0.7f, -15f * d, 0f, -4f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(leftWingPath, ulyssesBlack)

                        val rightWingPath = android.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(wingSpan, -9f * d)
                            quadTo(wingSpan * 0.7f, -15f * d, 0f, -4f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(rightWingPath, ulyssesBlack)

                        // Brilliant Electric Blue Glowing Core
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-wingSpan * 0.75f, -10f * d, -1.5f * d, -2f * d), electricBlue)
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(1.5f * d, -10f * d, wingSpan * 0.75f, -2f * d), electricBlue)

                        // Swallowtail Hindwings
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-wingSpan * 0.65f, -1f * d, -1f * d, 8f * d), royalBlue)
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(1f * d, -1f * d, wingSpan * 0.65f, 8f * d), royalBlue)

                        // Swallowtail extension tails
                        val tailPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = ulyssesBlack.color
                            strokeWidth = 1.2f * d
                            style = android.graphics.Paint.Style.STROKE
                        }
                        drawContext.canvas.nativeCanvas.drawLine(-wingSpan * 0.4f, 7f * d, -wingSpan * 0.5f, 11f * d, tailPaint)
                        drawContext.canvas.nativeCanvas.drawLine(wingSpan * 0.4f, 7f * d, wingSpan * 0.5f, 11f * d, tailPaint)

                        // Stardust trail sparkles behind Ulysses
                        val sparklePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((bf2Alpha * 200).toInt().coerceIn(0, 255), 56, 189, 248)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (sp in 0 until 4) {
                            val sx = (sp * 6f * d) + 4f * d
                            val sy = kotlin.math.sin(bf2U * 10f + sp) * (4f * d)
                            drawContext.canvas.nativeCanvas.drawCircle(sx, sy, (1.8f - sp * 0.35f) * d, sparklePaint)
                        }

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =========================================================================
                    // STAGE 4: Swarm of Sunshine Yellow Butterflies (24.0s - 27.5s) - Corner Stream
                    // =========================================================================
                    if (elapsedSec in 24.0f..27.5f) {
                        val swarmU = ((elapsedSec - 24.0f) / 3.5f).coerceIn(0f, 1f)
                        val swarmAlpha = (kotlin.math.sin(swarmU * Math.PI.toFloat()) * 1.3f).coerceIn(0f, 1f)

                        val yellowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((swarmAlpha * 255).toInt().coerceIn(0, 255), 253, 224, 71)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val yellowGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((swarmAlpha * 230).toInt().coerceIn(0, 255), 234, 179, 8)
                            style = android.graphics.Paint.Style.FILL
                        }

                        for (yb in 0..5) {
                            val ybOffset = yb * 0.14f
                            val ybProgress = (swarmU * 1.4f - ybOffset).coerceIn(0f, 1f)
                            if (ybProgress in 0.01f..0.99f) {
                                val cornerStartX = canvasW + 15f * d
                                val cornerStartY = canvasH + 10f * d
                                val cornerEndX = canvasW * 0.15f
                                val cornerEndY = -20f * d

                                // Playful swirling arc
                                val curX = cornerStartX + ybProgress * (cornerEndX - cornerStartX) + kotlin.math.sin(ybProgress * 8f + yb) * (14f * d)
                                val curY = cornerStartY + ybProgress * (cornerEndY - cornerStartY) + kotlin.math.cos(ybProgress * 6f + yb) * (12f * d)

                                val yWingFlap = kotlin.math.cos(ybProgress * 22f * Math.PI.toFloat() + yb)
                                val ySpan = (6.5f * d) * (0.35f + 0.65f * kotlin.math.abs(yWingFlap))

                                drawContext.canvas.nativeCanvas.save()
                                drawContext.canvas.nativeCanvas.translate(curX, curY)
                                drawContext.canvas.nativeCanvas.rotate(-35f + kotlin.math.sin(ybProgress * 8f + yb) * 20f)

                                // Tiny Yellow Wings
                                drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-ySpan, -5f * d, -0.6f * d, 0f), yellowPaint)
                                drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(0.6f * d, -5f * d, ySpan, 0f), yellowPaint)
                                drawContext.canvas.nativeCanvas.drawCircle(-ySpan * 0.4f, -2.5f * d, 1f * d, yellowGlow)
                                drawContext.canvas.nativeCanvas.drawCircle(ySpan * 0.4f, -2.5f * d, 1f * d, yellowGlow)

                                drawContext.canvas.nativeCanvas.restore()
                            }
                        }
                    }

                    // =========================================================================
                    // STAGE 5: Zen Calico Koi in the Spacebar (28.0s - 34.5s)
                    // =========================================================================
                    if (elapsedSec in 28.0f..34.5f) {
                        val koiU = ((elapsedSec - 28.0f) / 6.5f).coerceIn(0f, 1f)
                        val koiAlpha = if (koiU < 0.15f) {
                            (koiU / 0.15f).coerceIn(0f, 1f)
                        } else if (koiU > 0.85f) {
                            (1.0f - (koiU - 0.85f) / 0.15f).coerceIn(0f, 1f)
                        } else {
                            1.0f
                        }

                        val spaceBounds = exactSpaceKey?.visibleBounds
                        val sLeft = spaceBounds?.left ?: (canvasW * 0.25f)
                        val sRight = spaceBounds?.right ?: (canvasW * 0.75f)
                        val sTop = spaceBounds?.top ?: (canvasH * 0.80f)
                        val sBottom = spaceBounds?.bottom ?: (canvasH * 0.98f)
                        val sWidth = sRight - sLeft
                        val sCenterY = (sTop + sBottom) / 2f

                        val koiStartX = sLeft - 20f * d
                        val koiEndX = sRight + 20f * d
                        val koiX = koiStartX + koiU * (koiEndX - koiStartX)
                        val undulation = kotlin.math.sin(koiU * 16f * Math.PI.toFloat())
                        val koiY = sCenterY + undulation * (2.8f * d)
                        val koiAngle = kotlin.math.cos(koiU * 16f * Math.PI.toFloat()) * 14f

                        // 1. Concentric Bioluminescent Water Ripples along Spacebar
                        val ripplePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((koiAlpha * 120).toInt().coerceIn(0, 255), 0, 229, 255)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.1f * d
                        }
                        for (r in 0..2) {
                            val rU = (koiU * 5f + r * 0.33f) % 1f
                            val rRadius = (6f + rU * 18f) * d
                            ripplePaint.alpha = ((1f - rU) * koiAlpha * 100).toInt().coerceIn(0, 255)
                            drawContext.canvas.nativeCanvas.drawCircle(koiX - (r * 12f * d), koiY, rRadius, ripplePaint)
                        }

                        // 2. Calico Kohaku Koi Fish Body
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(koiX, koiY)
                        drawContext.canvas.nativeCanvas.rotate(koiAngle)

                        // Translucent Flowing Fins
                        val finPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((koiAlpha * 180).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val finUndulate = kotlin.math.sin(koiU * 24f * Math.PI.toFloat()) * (2f * d)

                        // Pectoral Fins
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-2f * d, -8f * d + finUndulate, 4f * d, -3f * d), finPaint)
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-2f * d, 3f * d, 4f * d, 8f * d - finUndulate), finPaint)

                        // Sinuous Undulating Caudal Tail Fin
                        val tailPath = android.graphics.Path().apply {
                            moveTo(-9f * d, 0f)
                            lineTo(-17f * d, -5f * d - finUndulate * 1.5f)
                            quadTo(-14f * d, 0f, -17f * d, 5f * d + finUndulate * 1.5f)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(tailPath, finPaint)

                        // Pearlescent White Body
                        val bodyWhite = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((koiAlpha * 255).toInt().coerceIn(0, 255), 248, 250, 252)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val koiBodyRect = android.graphics.RectF(-10f * d, -4f * d, 9f * d, 4f * d)
                        drawContext.canvas.nativeCanvas.drawOval(koiBodyRect, bodyWhite)

                        // Vibrant Orange/Crimson Kohaku Markings
                        val orangeMarking = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((koiAlpha * 255).toInt().coerceIn(0, 255), 234, 88, 12)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(2f * d, -3f * d, 7f * d, 3f * d), orangeMarking)
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-5f * d, -3.2f * d, 0f, 2.5f * d), orangeMarking)

                        // Black Sumi Ink Spot
                        val sumiInk = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((koiAlpha * 255).toInt().coerceIn(0, 255), 30, 41, 59)
                            style = android.graphics.Paint.Style.FILL
                        }
                        drawContext.canvas.nativeCanvas.drawCircle(-2f * d, 1.5f * d, 1.2f * d, sumiInk)

                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // =========================================================================
                    // STAGE 6: Accurate Cute Capybara Trotting on Middle Fret (45.0s - 49.8s)
                    // =========================================================================
                    if (elapsedSec in 45.0f..49.8f) {
                        val capyU = ((elapsedSec - 45.0f) / 4.8f).coerceIn(0f, 1f)
                        val capyAlpha = (kotlin.math.sin(capyU * Math.PI.toFloat()) * 1.4f).coerceIn(0f, 1f)

                        // Trots across the Middle Fret Line (Fret 2: exactly midway across keyboard)
                        val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                        val middleFretY = (canvasH / rowCount) * 2f
                        val startX = -35f * d
                        val endX = canvasW + 35f * d
                        val currentX = startX + capyU * (endX - startX)

                        // 4-beat trot gait cycle
                        val trotPhase = capyU * 32f * Math.PI.toFloat()
                        val trotBobY = kotlin.math.abs(kotlin.math.sin(trotPhase)) * (1.2f * d)
                        val capyY = middleFretY - 0.5f * d - trotBobY

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(currentX, capyY)

                        // Color Palette (Warm Cinnamon & Tan Capybara Fur)
                        val bodyDark = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 120, 53, 15) // #78350F
                            style = android.graphics.Paint.Style.FILL
                        }
                        val bodyMid = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 146, 64, 14) // #92400E
                            style = android.graphics.Paint.Style.FILL
                        }
                        val bodyTan = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 180, 83, 9) // #B45309
                            style = android.graphics.Paint.Style.FILL
                        }
                        val muzzleCream = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 217, 119, 6) // #D97706
                            style = android.graphics.Paint.Style.FILL
                        }
                        val darkFeature = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 69, 26, 3) // #451A03
                            style = android.graphics.Paint.Style.FILL
                        }
                        val eyeSpecular = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((capyAlpha * 90).toInt().coerceIn(0, 255), 0, 0, 0)
                            style = android.graphics.Paint.Style.FILL
                        }

                        // 1. Soft ground shadow on Fret 2
                        drawContext.canvas.nativeCanvas.drawOval(android.graphics.RectF(-14f * d, 0f, 14f * d, 2.2f * d), shadowPaint)

                        // 2. Short, Sturdy Trotting Legs (Alternating 4-beat gait)
                        val legFrontL = kotlin.math.sin(trotPhase) * (4f * d)
                        val legFrontR = -kotlin.math.sin(trotPhase) * (4f * d)
                        val legBackL = -kotlin.math.sin(trotPhase) * (4f * d)
                        val legBackR = kotlin.math.sin(trotPhase) * (4f * d)

                        val legPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = bodyDark.color
                            strokeWidth = 2.2f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            style = android.graphics.Paint.Style.STROKE
                        }

                        // Back Legs (Behind body)
                        drawContext.canvas.nativeCanvas.drawLine(-8f * d, -4f * d, -8f * d + legBackR, 0f, legPaint)
                        drawContext.canvas.nativeCanvas.drawLine(6f * d, -4f * d, 6f * d + legFrontR, 0f, legPaint)

                        // 3. Barrel-Shaped Stout Robust Capybara Body
                        val bodyRect = android.graphics.RectF(-13f * d, -11f * d, 7f * d, -2f * d)
                        drawContext.canvas.nativeCanvas.drawRoundRect(bodyRect, 5.5f * d, 4.5f * d, bodyMid)
                        // Lighter underbelly tone
                        val bellyRect = android.graphics.RectF(-10f * d, -6f * d, 4f * d, -2.5f * d)
                        drawContext.canvas.nativeCanvas.drawRoundRect(bellyRect, 3f * d, 2f * d, bodyTan)

                        // 4. Fore Legs (In front of body)
                        drawContext.canvas.nativeCanvas.drawLine(-9f * d, -4f * d, -9f * d + legBackL, 0f, legPaint)
                        drawContext.canvas.nativeCanvas.drawLine(5f * d, -4f * d, 5f * d + legFrontL, 0f, legPaint)

                        // 5. Classic Blunt, Squarish Capybara Head & Snout
                        val headBob = kotlin.math.sin(trotPhase * 0.5f) * (0.8f * d)
                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(7f * d, -8f * d + headBob)

                        // Robust squarish head profile
                        val headPath = android.graphics.Path().apply {
                            moveTo(-2f * d, -4f * d)
                            lineTo(6f * d, -3.5f * d) // High forehead
                            quadTo(8f * d, -3f * d, 8.5f * d, -0.5f * d) // Blunt squarish nose tip
                            lineTo(8.5f * d, 2.5f * d) // Deep muzzle
                            quadTo(6f * d, 3.5f * d, -1f * d, 2f * d) // Jawline
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(headPath, bodyMid)

                        // Muzzle highlight
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(3f * d, -1.5f * d, 8.2f * d, 2.8f * d), 1.5f * d, 1.5f * d, muzzleCream)

                        // High-Set Dark Nostrils
                        drawContext.canvas.nativeCanvas.drawCircle(7.6f * d, -0.8f * d, 0.7f * d, darkFeature)

                        // High-Set Dark Button Eye with White Specular Glint
                        drawContext.canvas.nativeCanvas.drawCircle(3.2f * d, -2.2f * d, 1.1f * d, darkFeature)
                        drawContext.canvas.nativeCanvas.drawCircle(3.0f * d, -2.5f * d, 0.4f * d, eyeSpecular)

                        // Small Rounded Ear set high on back of head
                        val earPath = android.graphics.Path().apply {
                            moveTo(-1.5f * d, -3.5f * d)
                            quadTo(-1.8f * d, -6.5f * d, 0.5f * d, -6f * d)
                            quadTo(1f * d, -3.8f * d, 0f, -3.2f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(earPath, darkFeature)

                        drawContext.canvas.nativeCanvas.restore()

                        drawContext.canvas.nativeCanvas.restore()
                    }
                }
            }
        }


        // 20. Tactical Crouching Sniper on Fret 3 (Aims 3.0s with laser, shoots off-screen left, shell ejects)
        if (sniperDudeTriggerTime > 0L) {
            val sniperProgress = remember(sniperDudeTriggerTime) { Animatable(0f) }
            LaunchedEffect(sniperDudeTriggerTime) {
                sniperProgress.snapTo(0f)
                sniperProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 4200, easing = LinearEasing),
                )
                sniperDudeTriggerTime = 0L
            }
            if (sniperProgress.value in 0.001f..0.999f) {
                val progress = sniperProgress.value
                val elapsedSec = progress * 4.2f
                val density = LocalDensity.current.density
                val d = density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val masterAlpha = if (elapsedSec > 3.8f) {
                        (1.0f - (elapsedSec - 3.8f) / 0.4f).coerceIn(0f, 1f)
                    } else if (elapsedSec < 0.25f) {
                        (elapsedSec / 0.25f).coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }

                    // Calculate 3rd Fret Line Y (Divider line above bottom spacebar row)
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val fret3Y = (canvasH / rowCount) * (rowCount - 1)
                    val sniperBaseX = canvasW * 0.84f

                    // Breathing & Aiming sway during 0s - 3s
                    val aimSwayY = if (elapsedSec < 3.0f) {
                        kotlin.math.sin(elapsedSec * 2.5f * Math.PI.toFloat()) * (1.2f * d)
                    } else {
                        0f
                    }

                    // Recoil Kickback impulse at 3.0s
                    val recoilX = if (elapsedSec in 3.0f..3.35f) {
                        val rProgress = (elapsedSec - 3.0f) / 0.35f
                        kotlin.math.sin(rProgress * Math.PI.toFloat()) * (7f * d)
                    } else {
                        0f
                    }

                    val sniperX = sniperBaseX + recoilX
                    val sniperY = fret3Y - 1f * d

                    // -----------------------------------------------------------------
                    // 1. Red Laser Sight Beam (0.0s - 3.0s, disappears on gunshot)
                    // -----------------------------------------------------------------
                    val barrelTipX = sniperX - 22f * d
                    val barrelTipY = sniperY - 5.5f * d + aimSwayY

                    if (elapsedSec < 3.0f) {
                        val laserAlpha = (masterAlpha * 0.85f).coerceIn(0f, 1f)
                        val laserBeam = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((laserAlpha * 230).toInt().coerceIn(0, 255), 239, 68, 68)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.0f * d
                        }
                        val laserGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((laserAlpha * 80).toInt().coerceIn(0, 255), 255, 0, 80)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 2.4f * d
                        }

                        val targetX = -50f * d
                        val targetY = barrelTipY + aimSwayY * 2f

                        drawContext.canvas.nativeCanvas.drawLine(barrelTipX, barrelTipY, targetX, targetY, laserGlow)
                        drawContext.canvas.nativeCanvas.drawLine(barrelTipX, barrelTipY, targetX, targetY, laserBeam)

                        // Scope lens reflection / glint
                        val glintPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val glintPulse = (kotlin.math.sin(elapsedSec * 6f) * 0.5f + 0.5f)
                        if (glintPulse > 0.4f) {
                            drawContext.canvas.nativeCanvas.drawCircle(sniperX - 9f * d, sniperY - 8.5f * d, 1.2f * d, glintPaint)
                        }
                    }

                    // -----------------------------------------------------------------
                    // 2. Gunshot: Explosive Muzzle Flash & Supersonic Tracer (3.0s - 3.35s)
                    // -----------------------------------------------------------------
                    if (elapsedSec in 3.0f..3.35f) {
                        val shotU = (elapsedSec - 3.0f) / 0.35f

                        // Muzzle Flash
                        if (shotU < 0.35f) {
                            val flashScale = (1.0f - shotU / 0.35f)
                            val flashOuter = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((flashScale * 255).toInt().coerceIn(0, 255), 245, 158, 11)
                                style = android.graphics.Paint.Style.FILL
                            }
                            val flashCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((flashScale * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                                style = android.graphics.Paint.Style.FILL
                            }
                            val flashPath = android.graphics.Path().apply {
                                val fx = barrelTipX
                                val fy = barrelTipY
                                val fw = 14f * d * flashScale
                                moveTo(fx, fy - 5f * d * flashScale)
                                lineTo(fx - fw, fy)
                                lineTo(fx, fy + 5f * d * flashScale)
                                lineTo(fx + 3f * d * flashScale, fy)
                                close()
                            }
                            drawContext.canvas.nativeCanvas.drawPath(flashPath, flashOuter)
                            drawContext.canvas.nativeCanvas.drawCircle(barrelTipX - 3f * d, barrelTipY, 3.5f * d * flashScale, flashCore)
                        }

                        // Supersonic Glowing Tracer Bullet (Rips right to left)
                        val tracerU = (shotU / 0.7f).coerceIn(0f, 1f)
                        val tracerStartX = barrelTipX
                        val tracerEndX = -60f * d
                        val tracerHeadX = tracerStartX + tracerU * (tracerEndX - tracerStartX)
                        val tracerTailX = (tracerHeadX + 45f * d).coerceAtMost(tracerStartX)

                        val tracerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(255, 254, 240, 138)
                            strokeWidth = 2.2f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            style = android.graphics.Paint.Style.STROKE
                        }
                        val tracerGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(160, 234, 88, 12)
                            strokeWidth = 4.5f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            style = android.graphics.Paint.Style.STROKE
                        }
                        drawContext.canvas.nativeCanvas.drawLine(tracerHeadX, barrelTipY, tracerTailX, barrelTipY, tracerGlow)
                        drawContext.canvas.nativeCanvas.drawLine(tracerHeadX, barrelTipY, tracerTailX, barrelTipY, tracerPaint)

                        // Dissipating Muzzle Smoke
                        val smokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(((1f - shotU) * 110).toInt().coerceIn(0, 255), 203, 213, 225)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (sm in 0..2) {
                            val smX = barrelTipX - (sm * 6f * d) - shotU * (12f * d)
                            val smY = barrelTipY - (sm * 2f * d) - shotU * (6f * d)
                            drawContext.canvas.nativeCanvas.drawCircle(smX, smY, (3f + sm * 1.5f + shotU * 6f) * d, smokePaint)
                        }
                    }

                    // -----------------------------------------------------------------
                    // 3. Brass Cartridge Shell Ejection (3.05s - 3.7s)
                    // -----------------------------------------------------------------
                    if (elapsedSec in 3.05f..3.7f) {
                        val shellU = (elapsedSec - 3.05f) / 0.65f
                        val chamberX = sniperX - 6f * d
                        val chamberY = sniperY - 6f * d

                        // Parabolic arc upward and to the right
                        val shellX = chamberX + shellU * (14f * d)
                        val shellY = chamberY - kotlin.math.sin(shellU * Math.PI.toFloat()) * (12f * d) + shellU * (8f * d)
                        val shellAngle = shellU * 720f

                        val shellPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 251, 191, 36)
                            style = android.graphics.Paint.Style.FILL
                        }

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(shellX, shellY)
                        drawContext.canvas.nativeCanvas.rotate(shellAngle)
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(-2f * d, -0.9f * d, 2f * d, 0.9f * d), 0.5f * d, 0.5f * d, shellPaint)
                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // -----------------------------------------------------------------
                    // 4. Tactical Sniper Stickman / Soldier Body & Rifle
                    // -----------------------------------------------------------------
                    val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 23, 42)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val bodyStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 23, 42)
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = 2.2f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                    }
                    val gearPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 30, 41, 59)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val riflePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 10, 10, 14)
                        style = android.graphics.Paint.Style.FILL
                    }

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(sniperX, sniperY)

                    // A. Legs & Crouch / Prone Knees on 3rd Fret Line
                    // Back Leg (folded under)
                    drawContext.canvas.nativeCanvas.drawLine(6f * d, 0f, 10f * d, 0f, bodyStroke)
                    drawContext.canvas.nativeCanvas.drawLine(10f * d, 0f, 4f * d, -4f * d, bodyStroke)
                    // Front Leg (braced)
                    drawContext.canvas.nativeCanvas.drawLine(2f * d, 0f, -3f * d, 0f, bodyStroke)
                    drawContext.canvas.nativeCanvas.drawLine(-3f * d, 0f, 0f, -5f * d, bodyStroke)

                    // B. Torso (Leaning forward into rifle stock)
                    val torsoPath = android.graphics.Path().apply {
                        moveTo(4f * d, -4f * d)
                        lineTo(-4f * d, -6.5f * d + aimSwayY * 0.4f)
                        lineTo(-3f * d, -11f * d + aimSwayY * 0.6f)
                        lineTo(5f * d, -7f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(torsoPath, bodyPaint)

                    // Tactical Backpack / Vest
                    drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(3f * d, -10f * d, 8f * d, -5f * d), 1.5f * d, 1.5f * d, gearPaint)

                    // C. Head with Tactical Ghillie / Helmet & Visor
                    val headCenterY = -12.5f * d + aimSwayY * 0.7f
                    drawContext.canvas.nativeCanvas.drawCircle(0f, headCenterY, 3.8f * d, bodyPaint)
                    // Helmet / Cap visor facing left
                    val visorPath = android.graphics.Path().apply {
                        moveTo(-2.5f * d, headCenterY - 2.5f * d)
                        lineTo(-6.5f * d, headCenterY - 1f * d)
                        lineTo(-2.5f * d, headCenterY)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(visorPath, gearPaint)

                    // Glowing Tactical Cyan Eye/Optic
                    val opticEye = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 0, 229, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(-2.2f * d, headCenterY - 0.5f * d, 0.8f * d, opticEye)

                    // D. High-Caliber Sniper Rifle & Bipod
                    val rifleY = -5.5f * d + aimSwayY

                    // Stock & Receiver
                    drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(1f * d, rifleY - 1.5f * d, 7f * d, rifleY + 1.2f * d), 1f * d, 1f * d, riflePaint)
                    // Chamber & Grip
                    drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(-8f * d, rifleY - 1.2f * d, 1f * d, rifleY + 1.2f * d), riflePaint)
                    // Long Precision Heavy Barrel
                    drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(-22f * d, rifleY - 0.7f * d, -8f * d, rifleY + 0.7f * d), riflePaint)
                    // Muzzle Brake
                    drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(-23.5f * d, rifleY - 1.2f * d, -22f * d, rifleY + 1.2f * d), riflePaint)

                    // High-Power Optic Scope
                    val scopeY = rifleY - 2.8f * d
                    drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(-10f * d, scopeY - 1.2f * d, -2f * d, scopeY + 1.2f * d), 0.8f * d, 0.8f * d, riflePaint)
                    // Scope Mounts
                    drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(-8.5f * d, scopeY + 0.8f * d, -7.5f * d, rifleY - 1.0f * d), riflePaint)
                    drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(-4.5f * d, scopeY + 0.8f * d, -3.5f * d, rifleY - 1.0f * d), riflePaint)

                    // Bipod resting on Fret 3
                    val bipodPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = riflePaint.color
                        strokeWidth = 1.1f * d
                        style = android.graphics.Paint.Style.STROKE
                    }
                    drawContext.canvas.nativeCanvas.drawLine(-14f * d, rifleY + 0.7f * d, -16f * d, 0f, bipodPaint)
                    drawContext.canvas.nativeCanvas.drawLine(-14f * d, rifleY + 0.7f * d, -12f * d, 0f, bipodPaint)

                    // Arms & Hands gripping the rifle
                    drawContext.canvas.nativeCanvas.drawLine(-2f * d, headCenterY + 4f * d, -5f * d, rifleY + 1f * d, bodyStroke)
                    drawContext.canvas.nativeCanvas.drawLine(-1f * d, headCenterY + 4f * d, 2f * d, rifleY + 0.5f * d, bodyStroke)

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }


        // 21. High-Definition Mini Mighty Thor Superhero Landing & Lightning Hammer Strike (3.5s)
        if (thorTriggerTime > 0L) {
            val thorProgress = remember(thorTriggerTime) { Animatable(0f) }
            LaunchedEffect(thorTriggerTime) {
                thorProgress.snapTo(0f)
                thorProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3500, easing = LinearEasing),
                )
                thorTriggerTime = 0L
            }
            if (thorProgress.value in 0.001f..0.999f) {
                val progress = thorProgress.value
                val elapsedSec = progress * 3.5f
                val density = LocalDensity.current.density
                val d = density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val masterAlpha = if (elapsedSec > 3.0f) {
                        (1.0f - (elapsedSec - 3.0f) / 0.5f).coerceIn(0f, 1f)
                    } else {
                        1.0f
                    }

                    // Bottom Fret Line (Fret 3: directly above spacebar)
                    val rowCount = if (keyboard.rowCount > 0) keyboard.rowCount else 4
                    val bottomFretY = (canvasH / rowCount) * (rowCount - 1)
                    val centerX = canvasW / 2f

                    // -------------------------------------------------------------
                    // STAGE A: Dynamic Diagonal Descent From The Right (0.0s - 0.45s)
                    // -------------------------------------------------------------
                    val landingTime = 0.45f
                    val isLanded = elapsedSec >= landingTime

                    val startX = centerX + 85f * d // Enters diagonally from the upper right
                    val startY = -40f * d

                    val currentThorX: Float
                    val currentThorY: Float

                    if (!isLanded) {
                        val fallU = (elapsedSec / landingTime).coerceIn(0f, 1f)
                        val fallEase = fallU * fallU // Powerful gravitational comet dive
                        currentThorX = startX + fallEase * (centerX - startX)
                        currentThorY = startY + fallEase * (bottomFretY - startY)

                        // Shimmering Diagonal Bifrost Comet Trail
                        val bifrostTrail = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            shader = android.graphics.LinearGradient(
                                startX, startY, currentThorX, currentThorY,
                                intArrayOf(
                                    android.graphics.Color.argb(0, 0, 229, 255),
                                    android.graphics.Color.argb(160, 59, 130, 246),
                                    android.graphics.Color.argb(220, 234, 179, 8),
                                    android.graphics.Color.argb(255, 255, 255, 255)
                                ),
                                floatArrayOf(0f, 0.4f, 0.8f, 1f),
                                android.graphics.Shader.TileMode.CLAMP
                            )
                            strokeWidth = 5.5f * d
                            style = android.graphics.Paint.Style.STROKE
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        drawContext.canvas.nativeCanvas.drawLine(startX, startY, currentThorX, currentThorY - 12f * d, bifrostTrail)
                    } else {
                        currentThorX = centerX
                        currentThorY = bottomFretY
                    }

                    // -------------------------------------------------------------
                    // STAGE B: Impact Shockwave & Ground Flash (0.45s - 1.2s)
                    // -------------------------------------------------------------
                    if (elapsedSec in 0.45f..1.2f) {
                        val impactU = (elapsedSec - 0.45f) / 0.75f
                        val shockRadius = impactU * (canvasW * 0.48f)
                        val shockAlpha = ((1f - impactU) * masterAlpha).coerceIn(0f, 1f)

                        // Dual Expanding Shockwave Rings
                        val shockPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((shockAlpha * 230).toInt().coerceIn(0, 255), 0, 229, 255)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = (3.5f * (1f - impactU * 0.6f)) * d
                        }
                        val outerShock = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((shockAlpha * 140).toInt().coerceIn(0, 255), 254, 240, 138)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = (1.8f * (1f - impactU * 0.6f)) * d
                        }
                        drawContext.canvas.nativeCanvas.drawOval(
                            android.graphics.RectF(centerX - shockRadius, bottomFretY - shockRadius * 0.28f, centerX + shockRadius, bottomFretY + shockRadius * 0.28f),
                            shockPaint
                        )
                        drawContext.canvas.nativeCanvas.drawOval(
                            android.graphics.RectF(centerX - shockRadius * 1.12f, bottomFretY - shockRadius * 0.32f, centerX + shockRadius * 1.12f, bottomFretY + shockRadius * 0.32f),
                            outerShock
                        )

                        // Golden/Cyan Ground Impact Flash at landing point
                        if (impactU < 0.35f) {
                            val flashScale = (1.0f - impactU / 0.35f)
                            val flashPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                color = android.graphics.Color.argb((flashScale * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                                style = android.graphics.Paint.Style.FILL
                            }
                            drawContext.canvas.nativeCanvas.drawCircle(centerX, bottomFretY - 4f * d, 22f * d * flashScale, flashPaint)
                        }
                    }

                    // -------------------------------------------------------------
                    // STAGE C: Violent Branching Electric Lightning (0.45s - 2.6s)
                    // -------------------------------------------------------------
                    if (elapsedSec in 0.45f..2.6f) {
                        val lightningU = (elapsedSec - 0.45f) / 2.15f
                        val lightningAlpha = (if (lightningU < 0.15f) lightningU / 0.15f else (1f - lightningU) * 1.2f).coerceIn(0f, 1f) * masterAlpha

                        val boltPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((lightningAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 1.5f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }
                        val glowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((lightningAlpha * 190).toInt().coerceIn(0, 255), 0, 229, 255)
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = 4.2f * d
                            strokeCap = android.graphics.Paint.Cap.ROUND
                        }

                        // Generate 6 Jagged Forked Lightning Bolts originating from Mjolnir on ground
                        val hammerGroundX = centerX + 9f * d
                        val hammerGroundY = bottomFretY - 2f * d

                        for (bIdx in 0..5) {
                            val seed = ((elapsedSec * 45f).toInt() + bIdx * 17)
                            val boltPath = android.graphics.Path().apply {
                                moveTo(hammerGroundX, hammerGroundY)
                                val dirSign = if (bIdx % 2 == 0) 1f else -1f
                                val reachW = (50f + (bIdx % 3) * 38f) * d
                                val reachH = (22f + (bIdx % 2) * 30f) * d

                                var cx = hammerGroundX
                                var cy = hammerGroundY
                                val segments = 5
                                for (s in 1..segments) {
                                    val su = s.toFloat() / segments
                                    val jitterX = ((seed * (s + 1) * 31) % 19 - 9) * 0.9f * d
                                    val jitterY = ((seed * (s + 2) * 43) % 15 - 7) * 0.9f * d
                                    cx = hammerGroundX + (su * reachW * dirSign) + jitterX
                                    cy = hammerGroundY - (su * reachH) + jitterY
                                    lineTo(cx, cy)
                                }
                            }
                            drawContext.canvas.nativeCanvas.drawPath(boltPath, glowPaint)
                            drawContext.canvas.nativeCanvas.drawPath(boltPath, boltPaint)
                        }

                        // Crackling Electric Sparks along bottom fret
                        val sparkPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((lightningAlpha * 255).toInt().coerceIn(0, 255), 224, 242, 254)
                            style = android.graphics.Paint.Style.FILL
                        }
                        for (sp in 0..8) {
                            val sparkU = ((elapsedSec * 8f + sp * 0.12f) % 1f)
                            val sparkX = centerX + (sp - 4) * (18f * d) + kotlin.math.sin(sparkU * 20f) * (6f * d)
                            val sparkY = bottomFretY - kotlin.math.sin(sparkU * Math.PI.toFloat()) * (12f * d)
                            drawContext.canvas.nativeCanvas.drawCircle(sparkX, sparkY, (0.9f + (sp % 3) * 0.4f) * d, sparkPaint)
                        }
                    }

                    // -------------------------------------------------------------
                    // STAGE D: High-Definition Mini Mighty Thor (Clean Vector Hero Figure)
                    // -------------------------------------------------------------
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(currentThorX, currentThorY)

                    // Hero Pose Interpolation:
                    // 0.45s - 1.8s: 3-point superhero landing (fist slammed on ground)
                    // 1.8s - 3.2s: Proud Asgardian champion stand (lifts Mjolnir skyward)
                    val isHeroRise = elapsedSec >= 1.8f
                    val riseU = if (isHeroRise) ((elapsedSec - 1.8f) / 0.5f).coerceIn(0f, 1f) else 0f
                    val riseEase = (1f - kotlin.math.cos(riseU * Math.PI.toFloat())) * 0.5f

                    // Palette (High-Contrast Hero Character Art)
                    val armorNavy = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 23, 42) // #0F172A
                        style = android.graphics.Paint.Style.FILL
                    }
                    val armorSilver = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 226, 232, 240) // #E2E8F0
                        style = android.graphics.Paint.Style.FILL
                    }
                    val silverBevel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 148, 163, 184) // #94A3B8
                        style = android.graphics.Paint.Style.FILL
                    }
                    val capeCrimson = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 225, 29, 72) // #E11D48
                        style = android.graphics.Paint.Style.FILL
                    }
                    val capeShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 159, 18, 57) // #9F1239
                        style = android.graphics.Paint.Style.FILL
                    }
                    val hairGold = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 251, 191, 36) // #FBBF24
                        style = android.graphics.Paint.Style.FILL
                    }
                    val hairShadow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 217, 119, 6) // #D97706
                        style = android.graphics.Paint.Style.FILL
                    }
                    val mjolnirSteel = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 100, 116, 139) // #64748B
                        style = android.graphics.Paint.Style.FILL
                    }
                    val runeCyan = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 0, 229, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val skinPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 254, 215, 170) // #FED7AA
                        style = android.graphics.Paint.Style.FILL
                    }

                    // 1. Billowing Royal Crimson Cape (Layered 3D Folds)
                    val capeBillow = kotlin.math.sin(elapsedSec * 9f) * (3f * d)
                    val capeBackPath = android.graphics.Path().apply {
                        moveTo(-4f * d, -15f * d - riseEase * (5f * d))
                        lineTo(-18f * d + capeBillow, -1f * d)
                        quadTo(-9f * d, 3f * d, -2f * d, -3f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(capeBackPath, capeShadow)

                    val capeFrontPath = android.graphics.Path().apply {
                        moveTo(-3f * d, -14f * d - riseEase * (5f * d))
                        lineTo(-15f * d + capeBillow * 0.8f, -2f * d)
                        quadTo(-7f * d, 2f * d, -1f * d, -4f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(capeFrontPath, capeCrimson)

                    // 2. Legs & Boots (Armored superhero landing / standing stance)
                    val legStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = armorNavy.color
                        strokeWidth = 2.8f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }
                    val bootSilver = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = armorSilver.color
                        style = android.graphics.Paint.Style.FILL
                    }

                    if (riseEase < 0.5f) {
                        // Superhero 3-point landing: rear leg braced back, front leg bent under
                        drawContext.canvas.nativeCanvas.drawLine(-6f * d, -5f * d, -10f * d, 0f, legStroke)
                        drawContext.canvas.nativeCanvas.drawLine(3f * d, -5f * d, 6f * d, -1f * d, legStroke)
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(-12f * d, -1.8f * d, -8f * d, 0.5f * d), 1f * d, 1f * d, bootSilver)
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(4f * d, -2.8f * d, 8f * d, -0.5f * d), 1f * d, 1f * d, bootSilver)
                    } else {
                        // Proud champion standing posture
                        drawContext.canvas.nativeCanvas.drawLine(-3.5f * d, -7f * d, -4.5f * d, 0f, legStroke)
                        drawContext.canvas.nativeCanvas.drawLine(3.5f * d, -7f * d, 4.5f * d, 0f, legStroke)
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(-6f * d, -2f * d, -3f * d, 0.5f * d), 1f * d, 1f * d, bootSilver)
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(3f * d, -2f * d, 6f * d, 0.5f * d), 1f * d, 1f * d, bootSilver)
                    }

                    // 3. Torso & Iconic Asgardian Scale Armor (6 Circular Discs with Bevels)
                    val torsoY = -13f * d - riseEase * (5.5f * d)
                    val torsoRect = android.graphics.RectF(-5.5f * d, torsoY, 5.5f * d, torsoY + 8f * d)
                    drawContext.canvas.nativeCanvas.drawRoundRect(torsoRect, 2.5f * d, 2.5f * d, armorNavy)

                    // Golden Asgardian Belt / Girdle of Strength (Megingjörð)
                    val beltRect = android.graphics.RectF(-5.2f * d, torsoY + 7f * d, 5.2f * d, torsoY + 8.4f * d)
                    drawContext.canvas.nativeCanvas.drawRoundRect(beltRect, 0.8f * d, 0.8f * d, hairGold)

                    // The 6 Iconic Embossed Silver Armor Discs
                    val discOffsets = listOf(
                        Pair(-2.8f * d, torsoY + 2.2f * d),
                        Pair(2.8f * d, torsoY + 2.2f * d),
                        Pair(-2.8f * d, torsoY + 4.6f * d),
                        Pair(2.8f * d, torsoY + 4.6f * d),
                        Pair(-2.8f * d, torsoY + 6.8f * d),
                        Pair(2.8f * d, torsoY + 6.8f * d)
                    )
                    for ((dx, dy) in discOffsets) {
                        drawContext.canvas.nativeCanvas.drawCircle(dx, dy, 1.4f * d, silverBevel)
                        drawContext.canvas.nativeCanvas.drawCircle(dx, dy, 1.1f * d, armorSilver)
                    }

                    // 4. Head, Flowing Golden Hair & Winged Silver Helmet
                    val headY = torsoY - 5.5f * d

                    // Flowing Norse Golden Hair cascading over shoulders
                    val hairLeft = android.graphics.Path().apply {
                        moveTo(-2.5f * d, headY - 2f * d)
                        quadTo(-6.5f * d, headY + 3f * d, -4.5f * d, headY + 7.5f * d)
                        lineTo(-2f * d, headY + 5.5f * d)
                        close()
                    }
                    val hairRight = android.graphics.Path().apply {
                        moveTo(2.5f * d, headY - 2f * d)
                        quadTo(6.5f * d, headY + 3f * d, 4.5f * d, headY + 7.5f * d)
                        lineTo(2f * d, headY + 5.5f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(hairLeft, hairShadow)
                    drawContext.canvas.nativeCanvas.drawPath(hairRight, hairShadow)
                    drawContext.canvas.nativeCanvas.drawCircle(-3.6f * d, headY + 2f * d, 2.2f * d, hairGold)
                    drawContext.canvas.nativeCanvas.drawCircle(3.6f * d, headY + 2f * d, 2.2f * d, hairGold)

                    // Hero Face & Jawline
                    val faceRect = android.graphics.RectF(-3.4f * d, headY - 3.4f * d, 3.4f * d, headY + 3.4f * d)
                    drawContext.canvas.nativeCanvas.drawRoundRect(faceRect, 3f * d, 3f * d, skinPaint)

                    // Polished Silver Winged Helmet
                    val helmetPath = android.graphics.Path().apply {
                        moveTo(-3.8f * d, headY)
                        cubicTo(-3.8f * d, headY - 5.5f * d, 3.8f * d, headY - 5.5f * d, 3.8f * d, headY)
                        lineTo(3.8f * d, headY - 1f * d)
                        lineTo(-3.8f * d, headY - 1f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(helmetPath, armorSilver)

                    // Wing Crests on Helmet (Left & Right Swept-Back Wings)
                    val leftWing = android.graphics.Path().apply {
                        moveTo(-3.4f * d, headY - 1.5f * d)
                        lineTo(-8.5f * d, headY - 8.5f * d)
                        lineTo(-3.2f * d, headY - 4.5f * d)
                        close()
                    }
                    val rightWing = android.graphics.Path().apply {
                        moveTo(3.4f * d, headY - 1.5f * d)
                        lineTo(8.5f * d, headY - 8.5f * d)
                        lineTo(3.2f * d, headY - 4.5f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(leftWing, armorSilver)
                    drawContext.canvas.nativeCanvas.drawPath(rightWing, armorSilver)
                    drawContext.canvas.nativeCanvas.drawCircle(0f, headY - 2.8f * d, 1.2f * d, silverBevel) // Center crest gem

                    // Piercing Glowing Blue God of Thunder Eyes
                    drawContext.canvas.nativeCanvas.drawCircle(-1.5f * d, headY + 0.5f * d, 0.85f * d, runeCyan)
                    drawContext.canvas.nativeCanvas.drawCircle(1.5f * d, headY + 0.5f * d, 0.85f * d, runeCyan)
                    drawContext.canvas.nativeCanvas.drawCircle(-1.5f * d, headY + 0.3f * d, 0.35f * d, armorSilver)
                    drawContext.canvas.nativeCanvas.drawCircle(1.5f * d, headY + 0.3f * d, 0.35f * d, armorSilver)

                    // 5. Legendary Hammer Mjolnir (High-Detail Uru Mallet with Runes)
                    if (riseEase < 0.5f) {
                        // Slammed firmly on fret line at right side
                        val hX = 8.5f * d
                        val hY = -1.5f * d
                        // Leather-wrapped Handle
                        drawContext.canvas.nativeCanvas.drawLine(hX, hY - 8.5f * d, hX, hY, legStroke)
                        drawContext.canvas.nativeCanvas.drawCircle(hX, hY - 8.5f * d, 1.2f * d, armorSilver) // Pommel
                        // Heavy Beveled Uru Hammer Head
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(hX - 5.5f * d, hY - 4.2f * d, hX + 5.5f * d, hY), 1.2f * d, 1.2f * d, mjolnirSteel)
                        drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(hX - 4.5f * d, hY - 3.5f * d, hX + 4.5f * d, hY - 0.7f * d), silverBevel)
                        // Glowing Norse Rune Engravings
                        drawContext.canvas.nativeCanvas.drawCircle(hX, hY - 2.1f * d, 1.1f * d, runeCyan)
                    } else {
                        // Proudly Raised Toward the Heavens
                        val hX = 9.5f * d
                        val hY = torsoY - 12f * d * riseEase
                        // Arm thrusting upward
                        drawContext.canvas.nativeCanvas.drawLine(4.5f * d, torsoY + 2f * d, hX, hY + 5f * d, legStroke)
                        drawContext.canvas.nativeCanvas.drawLine(hX, hY + 5f * d, hX, hY, legStroke)
                        drawContext.canvas.nativeCanvas.drawCircle(hX, hY + 5.5f * d, 1.2f * d, armorSilver)
                        // Uru Hammer Head Glowing in the Sky
                        drawContext.canvas.nativeCanvas.drawRoundRect(android.graphics.RectF(hX - 5.5f * d, hY - 4.2f * d, hX + 5.5f * d, hY), 1.2f * d, 1.2f * d, mjolnirSteel)
                        drawContext.canvas.nativeCanvas.drawRect(android.graphics.RectF(hX - 4.5f * d, hY - 3.5f * d, hX + 4.5f * d, hY - 0.7f * d), silverBevel)
                        drawContext.canvas.nativeCanvas.drawCircle(hX, hY - 2.1f * d, 1.3f * d, runeCyan)
                    }

                    drawContext.canvas.nativeCanvas.restore()
                }
            }
        }


        // 22. Authentic Mushu the Dragon (Disney's Mulan) Serpentine Flight & Fire Breath (3.8s)
        if (mushuTriggerTime > 0L) {
            val mushuProgress = remember(mushuTriggerTime) { Animatable(0f) }
            LaunchedEffect(mushuTriggerTime) {
                mushuProgress.snapTo(0f)
                mushuProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 3800, easing = LinearEasing),
                )
                mushuTriggerTime = 0L
            }
            if (mushuProgress.value in 0.001f..0.999f) {
                val progress = mushuProgress.value
                val elapsedSec = progress * 3.8f
                val density = LocalDensity.current.density
                val d = density

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val canvasW = this.size.width
                    val canvasH = this.size.height

                    val masterAlpha = when {
                        progress < 0.06f -> (progress / 0.06f).coerceIn(0f, 1f)
                        progress > 0.92f -> ((1f - progress) / 0.08f).coerceIn(0f, 1f)
                        else -> 1f
                    }

                    // Sinuous Dragon Flight Path across keyboard (Left to Right)
                    val startX = -65f * d
                    val endX = canvasW + 65f * d
                    val currentHeadX = startX + progress * (endX - startX)
                    val baseFlightY = canvasH * 0.42f
                    val undulationY = kotlin.math.sin(progress * 7f * Math.PI.toFloat()) * (24f * d)
                    val headY = baseFlightY + undulationY

                    // -------------------------------------------------------------
                    // 1. Celestial Dragon Clouds & Golden Sparks Trail
                    // -------------------------------------------------------------
                    val cloudPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 110).toInt().coerceIn(0, 255), 254, 240, 138)
                        style = android.graphics.Paint.Style.FILL
                    }
                    val redSmokePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 70).toInt().coerceIn(0, 255), 239, 68, 68)
                        style = android.graphics.Paint.Style.FILL
                    }

                    for (c in 0 until 7) {
                        val cu = (c * 0.14f)
                        val trailDist = (c + 1) * 12f * d
                        val trailX = currentHeadX - trailDist
                        val trailY = baseFlightY + kotlin.math.sin((progress - cu * 0.2f) * 7f * Math.PI.toFloat()) * (24f * d) + (c % 2 * 4f * d)
                        val cloudR = (4f + (c % 3) * 2.5f) * d
                        drawContext.canvas.nativeCanvas.drawCircle(trailX, trailY, cloudR, redSmokePaint)
                        drawContext.canvas.nativeCanvas.drawCircle(trailX - 2f * d, trailY - 1f * d, cloudR * 0.7f, cloudPaint)
                    }

                    // -------------------------------------------------------------
                    // 2. Mushu's Fire Puffs (Fiery Dragon Breath every ~1.2s)
                    // -------------------------------------------------------------
                    val fireCycle = (elapsedSec % 1.2f)
                    if (fireCycle < 0.45f) {
                        val fireU = fireCycle / 0.45f
                        val fireStartX = currentHeadX + 22f * d
                        val fireStartY = headY - 2f * d

                        val flameOuter = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(((1f - fireU) * masterAlpha * 240).toInt().coerceIn(0, 255), 239, 68, 68)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val flameInner = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(((1f - fireU) * masterAlpha * 255).toInt().coerceIn(0, 255), 251, 191, 36)
                            style = android.graphics.Paint.Style.FILL
                        }
                        val flameCore = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb(((1f - fireU) * masterAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                            style = android.graphics.Paint.Style.FILL
                        }

                        val flameDist = fireU * (28f * d)
                        val flameX = fireStartX + flameDist
                        val flameY = fireStartY - kotlin.math.sin(fireU * Math.PI.toFloat()) * (4f * d)
                        val flameR = (3f + fireU * 8f) * d

                        drawContext.canvas.nativeCanvas.drawCircle(flameX, flameY, flameR, flameOuter)
                        drawContext.canvas.nativeCanvas.drawCircle(flameX - 2f * d, flameY, flameR * 0.65f, flameInner)
                        drawContext.canvas.nativeCanvas.drawCircle(flameX - 4f * d, flameY, flameR * 0.35f, flameCore)
                    }

                    // -------------------------------------------------------------
                    // 3. Mushu Serpentine Body Kinematics (S-Curve Dragon Anatomy)
                    // -------------------------------------------------------------
                    // Color Palette (Official Disney Model)
                    val mushuRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 225, 29, 72) // #E11D48
                        style = android.graphics.Paint.Style.FILL
                    }
                    val mushuDarkRed = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 159, 18, 57) // #9F1239
                        style = android.graphics.Paint.Style.FILL
                    }
                    val yellowBelly = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 254, 240, 138) // #FEF08A
                        style = android.graphics.Paint.Style.FILL
                    }
                    val cyanHorns = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 6, 182, 212) // #06B6D4
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyeYellow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 253, 224, 71) // #FDE047
                        style = android.graphics.Paint.Style.FILL
                    }
                    val eyePupil = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 15, 23, 42) // #0F172A
                        style = android.graphics.Paint.Style.FILL
                    }
                    val whiskerPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 190, 18, 60)
                        strokeWidth = 1.1f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }

                    // Multi-Segment Serpentine Spine Coordinates
                    val spineSegments = 16
                    val spinePoints = ArrayList<Pair<Float, Float>>()

                    for (s in 0..spineSegments) {
                        val su = s.toFloat() / spineSegments
                        val segProgress = progress - su * 0.10f
                        val segX = startX + segProgress * (endX - startX)
                        val segY = baseFlightY + kotlin.math.sin(segProgress * 7f * Math.PI.toFloat()) * (24f * d)
                        spinePoints.add(Pair(segX, segY))
                    }

                    // A. Dorsal Spines (Cyan/Turquoise Spikes along back)
                    val spineSpikePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = cyanHorns.color
                        style = android.graphics.Paint.Style.FILL
                    }
                    for (s in 2 until spineSegments - 2) {
                        val pt = spinePoints[s]
                        val nextPt = spinePoints[s - 1]
                        val dx = nextPt.first - pt.first
                        val dy = nextPt.second - pt.second
                        val ang = kotlin.math.atan2(dy, dx)
                        val spikeHeight = (3.5f - s * 0.15f).coerceAtLeast(1.5f) * d

                        drawContext.canvas.nativeCanvas.save()
                        drawContext.canvas.nativeCanvas.translate(pt.first, pt.second)
                        drawContext.canvas.nativeCanvas.rotate(ang * 180f / Math.PI.toFloat())
                        val spikePath = android.graphics.Path().apply {
                            moveTo(0f, -2.5f * d)
                            lineTo(-2f * d, -2.5f * d - spikeHeight)
                            lineTo(2f * d, -2.5f * d)
                            close()
                        }
                        drawContext.canvas.nativeCanvas.drawPath(spikePath, spineSpikePaint)
                        drawContext.canvas.nativeCanvas.restore()
                    }

                    // B. Main Red Serpentine Body (Continuous segmented ribbon)
                    val bodyStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = mushuRed.color
                        strokeWidth = 6.5f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }
                    val bodyUnderlay = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = yellowBelly.color
                        strokeWidth = 2.4f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        strokeJoin = android.graphics.Paint.Join.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }

                    val bodyPath = android.graphics.Path().apply {
                        moveTo(spinePoints[0].first, spinePoints[0].second)
                        for (s in 1 until spinePoints.size) {
                            lineTo(spinePoints[s].first, spinePoints[s].second)
                        }
                    }
                    drawContext.canvas.nativeCanvas.drawPath(bodyPath, bodyStroke)

                    // Yellow Underbelly Ribbon
                    val bellyPath = android.graphics.Path().apply {
                        moveTo(spinePoints[0].first, spinePoints[0].second + 2f * d)
                        for (s in 1 until spinePoints.size - 2) {
                            lineTo(spinePoints[s].first, spinePoints[s].second + 2.2f * d)
                        }
                    }
                    drawContext.canvas.nativeCanvas.drawPath(bellyPath, bodyUnderlay)

                    // C. Fluffy Flame Tail Tip (Segment 15-16)
                    val tailEnd = spinePoints.last()
                    val tailAngle = kotlin.math.sin(progress * 14f) * 25f
                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(tailEnd.first, tailEnd.second)
                    drawContext.canvas.nativeCanvas.rotate(tailAngle)

                    val tailTuftPath = android.graphics.Path().apply {
                        moveTo(0f, 0f)
                        quadTo(-6f * d, -5f * d, -12f * d, -2f * d)
                        quadTo(-8f * d, 0f, -14f * d, 4f * d)
                        quadTo(-6f * d, 3f * d, 0f, 0f)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(tailTuftPath, mushuDarkRed)
                    drawContext.canvas.nativeCanvas.restore()

                    // D. 4 Slender Dragon Legs & 3-Toed Claws
                    val legStroke = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = mushuRed.color
                        strokeWidth = 2.0f * d
                        strokeCap = android.graphics.Paint.Cap.ROUND
                        style = android.graphics.Paint.Style.STROKE
                    }
                    // Forelegs (Near segment 3)
                    val forePt = spinePoints[3]
                    val legPhase1 = progress * 24f
                    drawContext.canvas.nativeCanvas.drawLine(forePt.first, forePt.second, forePt.first + kotlin.math.cos(legPhase1) * (6f * d), forePt.second + 6f * d, legStroke)
                    drawContext.canvas.nativeCanvas.drawLine(forePt.first - 3f * d, forePt.second, forePt.first - 3f * d - kotlin.math.cos(legPhase1) * (6f * d), forePt.second + 6f * d, legStroke)

                    // Hindlegs (Near segment 10)
                    val hindPt = spinePoints[10]
                    drawContext.canvas.nativeCanvas.drawLine(hindPt.first, hindPt.second, hindPt.first - kotlin.math.sin(legPhase1) * (6f * d), hindPt.second + 6f * d, legStroke)
                    drawContext.canvas.nativeCanvas.drawLine(hindPt.first - 3f * d, hindPt.second, hindPt.first - 3f * d + kotlin.math.sin(legPhase1) * (6f * d), hindPt.second + 6f * d, legStroke)

                    // -------------------------------------------------------------
                    // 4. Mushu's Iconic Expressive Head & Snout
                    // -------------------------------------------------------------
                    val headPt = spinePoints[0]
                    val headTilt = kotlin.math.cos(progress * 7f * Math.PI.toFloat()) * 18f

                    drawContext.canvas.nativeCanvas.save()
                    drawContext.canvas.nativeCanvas.translate(headPt.first, headPt.second)
                    drawContext.canvas.nativeCanvas.rotate(headTilt)

                    // A. Antler Horns (Cyan / Blue Stylized Antlers)
                    val hornPathLeft = android.graphics.Path().apply {
                        moveTo(-2f * d, -4f * d)
                        quadTo(-6f * d, -11f * d, -4f * d, -14f * d)
                        lineTo(-3f * d, -11f * d)
                        quadTo(-1f * d, -12f * d, 0f, -14f * d)
                        lineTo(-1f * d, -8f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(hornPathLeft, cyanHorns)

                    // B. Head Dome & Elongated Snout
                    val headJawPath = android.graphics.Path().apply {
                        moveTo(-4f * d, 1f * d)
                        lineTo(10f * d, 2f * d) // Upper snout
                        quadTo(12f * d, 0f, 13f * d, -3f * d) // Nose bridge
                        lineTo(4f * d, -4f * d) // Forehead
                        lineTo(-4f * d, -3f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(headJawPath, mushuRed)

                    // Under-snout / Lower Jaw
                    val lowerJaw = android.graphics.Path().apply {
                        moveTo(0f, 2f * d)
                        lineTo(8f * d, 3.5f * d)
                        lineTo(10f * d, 2f * d)
                        close()
                    }
                    drawContext.canvas.nativeCanvas.drawPath(lowerJaw, yellowBelly)

                    // C. Expressive Big Eyes (Yellow Sclera + Dark Pupils)
                    drawContext.canvas.nativeCanvas.drawCircle(3f * d, -5.5f * d, 2.5f * d, eyeYellow)
                    drawContext.canvas.nativeCanvas.drawCircle(4f * d, -5.5f * d, 1.2f * d, eyePupil)
                    drawContext.canvas.nativeCanvas.drawCircle(4.3f * d, -6f * d, 0.4f * d, yellowBelly)

                    // D. Flowing Whiskers (Trailing majestically in the wind)
                    val whiskerFlutter = kotlin.math.sin(progress * 28f) * (4f * d)
                    val whiskerPathTop = android.graphics.Path().apply {
                        moveTo(10f * d, -1f * d)
                        quadTo(4f * d, 4f * d + whiskerFlutter, -6f * d, 3f * d - whiskerFlutter)
                    }
                    val whiskerPathBot = android.graphics.Path().apply {
                        moveTo(9f * d, 1f * d)
                        quadTo(3f * d, 7f * d - whiskerFlutter, -8f * d, 6f * d + whiskerFlutter)
                    }
                    drawContext.canvas.nativeCanvas.drawPath(whiskerPathTop, whiskerPaint)
                    drawContext.canvas.nativeCanvas.drawPath(whiskerPathBot, whiskerPaint)

                    // E. Dragon Teeth & Tongue
                    val toothPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((masterAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
                        style = android.graphics.Paint.Style.FILL
                    }
                    drawContext.canvas.nativeCanvas.drawCircle(8.5f * d, 2.2f * d, 0.7f * d, toothPaint)
                    drawContext.canvas.nativeCanvas.drawCircle(6.5f * d, 2.2f * d, 0.7f * d, toothPaint)

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

    // BlackBerry Physical Keycap 3D Flip Easter Egg (15.8s: 0.8s 3D flip, 5s physical mode, 10s smooth fade)
    var bbTriggerTime by remember { androidx.compose.runtime.mutableLongStateOf(0L) }
    var lastBbSignature by remember { mutableStateOf("") }
    val bbProgress = remember { Animatable(0f) }

    LaunchedEffect(activeContent) {
        val tb = activeContent.textBeforeSelection.toString().lowercase()
        val comp = activeContent.composingText.lowercase()
        val bbKeys = listOf(
            "blackberry bold", "blackberry priv", "blackberry q10",
            "blackberry passport", "blackberry classic", "blackberry 9900",
            "blackberry key2", "rim blackberry"
        )
        val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
        val isBbMatch = bbKeys.any { k ->
            delimiters.any { d ->
                tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && (d.isEmpty() || d == " "))
            }
        }
        val signature = "$tb::$comp"
        if (isBbMatch && signature != lastBbSignature) {
            lastBbSignature = signature
            bbTriggerTime = System.currentTimeMillis()
        }
    }

    LaunchedEffect(bbTriggerTime) {
        if (bbTriggerTime > 0L) {
            bbProgress.snapTo(0f)
            bbProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 15800, easing = LinearEasing),
            )
            bbTriggerTime = 0L
        }
    }

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

    val keyBounds = key.visibleBounds
    val staggerMs = remember(keyBounds) {
        ((keyBounds.center.x * 0.4f + keyBounds.center.y * 0.6f) % 280f)
    }
    val elapsedMs = bbProgress.value * 15800f
    val localMs = (elapsedMs - staggerMs).coerceAtLeast(0f)

    // 1. 3D Keycap Flip Angle (0 to 650ms)
    val flipAngle = if (bbProgress.value > 0f && localMs in 0f..650f) {
        val u = (localMs / 650f)
        kotlin.math.sin(u * Math.PI.toFloat()) * 180f
    } else {
        0f
    }

    // 2. Physical Key Opacity: 5s pure physical look (localMs 600..5600), then 10s slow fade (5600..15600)
    val physicalAlpha = when {
        bbProgress.value <= 0f -> 0f
        localMs < 300f -> (localMs / 300f).coerceIn(0f, 1f)
        localMs <= 5600f -> 1f
        localMs <= 15600f -> (1f - (localMs - 5600f) / 10000f).coerceIn(0f, 1f)
        else -> 0f
    }

    SnyggBox(
        FlorisImeUi.Key.elementName,
        attributes = attributes,
        selector = selector,
        modifier = Modifier
            .requiredSize(size)
            .absoluteOffset { key.visibleBounds.topLeft.toIntOffset() }
            .graphicsLayer {
                if (flipAngle != 0f) {
                    rotationX = flipAngle
                    cameraDistance = 14f * density
                }
            },
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

        // Authentic BlackBerry Bold / Classic Sculpted Physical Keycap Overlay
        if (physicalAlpha > 0.005f) {
            val keyLabel = key.label ?: ""
            val isLeftHandKey = keyBounds.center.x < (keyBounds.width * 5f)

            androidx.compose.foundation.Canvas(
                modifier = Modifier.matchParentSize()
            ) {
                val canvasW = this.size.width
                val canvasH = this.size.height
                val d = density

                val cornerRadius = 4.5f * d

                // 1. Outer Chrome Bezel / Frame Accent
                val chromePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((physicalAlpha * 240).toInt().coerceIn(0, 255), 180, 185, 190)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.2f * d
                }
                val keyRect = android.graphics.RectF(0.8f * d, 0.8f * d, canvasW - 0.8f * d, canvasH - 0.8f * d)
                drawContext.canvas.nativeCanvas.drawRoundRect(keyRect, cornerRadius, cornerRadius, chromePaint)

                // 2. Sculpted Obsidian Keycap Body (Deep Gloss Resin with Ergonomic Top-Light Highlight)
                val bodyPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    shader = android.graphics.LinearGradient(
                        0f, 0f, 0f, canvasH,
                        intArrayOf(
                            android.graphics.Color.argb((physicalAlpha * 255).toInt().coerceIn(0, 255), 45, 48, 52),
                            android.graphics.Color.argb((physicalAlpha * 255).toInt().coerceIn(0, 255), 20, 22, 25),
                            android.graphics.Color.argb((physicalAlpha * 255).toInt().coerceIn(0, 255), 12, 13, 15)
                        ),
                        floatArrayOf(0f, 0.45f, 1f),
                        android.graphics.Shader.TileMode.CLAMP
                    )
                    style = android.graphics.Paint.Style.FILL
                }
                drawContext.canvas.nativeCanvas.drawRoundRect(keyRect, cornerRadius, cornerRadius, bodyPaint)

                // 3. Ergonomic Sculpted Thumb Wave / Bevel Crest (Signature BlackBerry Bold angled slope)
                val bevelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((physicalAlpha * 110).toInt().coerceIn(0, 255), 255, 255, 255)
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 1.0f * d
                }
                val bevelPath = android.graphics.Path().apply {
                    if (isLeftHandKey) {
                        moveTo(cornerRadius, 2.5f * d)
                        lineTo(canvasW - cornerRadius * 1.5f, 4.5f * d)
                    } else {
                        moveTo(cornerRadius * 1.5f, 4.5f * d)
                        lineTo(canvasW - cornerRadius, 2.5f * d)
                    }
                }
                drawContext.canvas.nativeCanvas.drawPath(bevelPath, bevelPaint)

                // 4. Horizontal Metallic Chrome Fret Strip at Top Edge of Key
                val fretPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb((physicalAlpha * 160).toInt().coerceIn(0, 255), 210, 215, 220)
                    style = android.graphics.Paint.Style.FILL
                }
                val fretRect = android.graphics.RectF(cornerRadius * 0.8f, 1.2f * d, canvasW - cornerRadius * 0.8f, 2.4f * d)
                drawContext.canvas.nativeCanvas.drawRoundRect(fretRect, 0.6f * d, 0.6f * d, fretPaint)

                // 5. Classic High-Contrast Pearl White Typography
                if (keyLabel.isNotBlank()) {
                    val textPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.argb((physicalAlpha * 255).toInt().coerceIn(0, 255), 248, 250, 252)
                        textSize = (if (keyLabel.length > 1) 11.5f else 15.5f) * d
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    val textY = canvasH / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
                    drawContext.canvas.nativeCanvas.drawText(keyLabel, canvasW / 2f, textY, textPaint)

                    // 6. Iconic BlackBerry Alt Red/Orange Digits (W=1, E=2, R=3, S=4, D=5, F=6, Z=7, X=8, C=9, M=0)
                    val altChar = when (keyLabel.uppercase()) {
                        "W" -> "1"
                        "E" -> "2"
                        "R" -> "3"
                        "S" -> "4"
                        "D" -> "5"
                        "F" -> "6"
                        "Z" -> "7"
                        "X" -> "8"
                        "C" -> "9"
                        "M" -> "0"
                        else -> null
                    }
                    if (altChar != null) {
                        val altPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.argb((physicalAlpha * 240).toInt().coerceIn(0, 255), 244, 63, 94)
                            textSize = 8.5f * d
                            typeface = android.graphics.Typeface.DEFAULT_BOLD
                            textAlign = android.graphics.Paint.Align.RIGHT
                        }
                        drawContext.canvas.nativeCanvas.drawText(altChar, canvasW - 4.5f * d, 10.5f * d, altPaint)
                    }
                }
            }
        }

        // Spacebar Rain Easter Egg (10 seconds smooth fade-in, rain droplets + ripples, and fade-out)
        if (key.computedData.code == KeyCode.SPACE) {
            val isRainActive = remember(activeContent) {
                val tb = activeContent.textBeforeSelection.toString().lowercase()
                val comp = activeContent.composingText.lowercase()
                val keys = listOf("rain", "rainy", "raining", "rainfall", "rainstorm")
                keys.any { k ->
                    val delimiters = listOf("", " ", ".", "!", ",", "?", "\n")
                    delimiters.any { d ->
                        tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && (d.isEmpty() || d == " "))
                    }
                }
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
                    isGliding = false
                } else {
                    isGliding = true
                }
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
            // A fast glide often starts BETWEEN keys; with no initialKey the
            // glide detector's character-start guard silently killed every
            // such stroke (field report 2026-08-27: glide stopped
            // triggering). Resolve the stroke's origin to the nearest key
            // for glide purposes only — activeKey stays null, so nothing is
            // pressed. Misses nearest a functional key resolve to null,
            // keeping the backward delete-word swipe decoupled from glide.
            if (pointer.initialKey == null && keyboard.mode == KeyboardMode.CHARACTERS) {
                pointer.initialKey = keyboard.getKeyForPosAdaptive(touchX, touchY, emptySet())
            }
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

        return when (initialKey.computedData.code) {
            KeyCode.DELETE -> {
                cancelGlideActive()
                handleDeleteSwipe(event)
            }
            KeyCode.SPACE, KeyCode.CJK_SPACE -> {
                cancelGlideActive()
                handleSpaceSwipe(event)
            }
            else -> when {
                (initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.SPACE ||
                    initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code == KeyCode.CJK_SPACE) &&
                    event.type == SwipeGesture.Type.TOUCH_MOVE -> {
                    cancelGlideActive()
                    handleSpaceSwipe(event)
                }
                initialKey.computedData.code == KeyCode.SHIFT && activeKey?.computedData?.code != KeyCode.SHIFT &&
                    event.type == SwipeGesture.Type.TOUCH_UP -> {
                    cancelGlideActive()
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
