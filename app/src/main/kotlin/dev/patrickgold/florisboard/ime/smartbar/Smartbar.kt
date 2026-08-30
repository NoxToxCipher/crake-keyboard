package dev.patrickgold.florisboard.ime.smartbar

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.nlp.NlpInlineAutofill
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionButton
import dev.patrickgold.florisboard.ime.smartbar.quickaction.QuickActionsRow
import dev.patrickgold.florisboard.ime.smartbar.quickaction.ToggleOverflowPanelAction
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.lib.android.AndroidVersion
import org.florisboard.lib.compose.horizontalTween
import org.florisboard.lib.compose.verticalTween
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggIconButton
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.rememberSnyggThemeQuery

const val AnimationDuration = 200

val VerticalEnterTransition = EnterTransition.verticalTween(AnimationDuration)
val VerticalExitTransition = ExitTransition.verticalTween(AnimationDuration)

private val HorizontalEnterTransition = EnterTransition.horizontalTween(AnimationDuration)
private val HorizontalExitTransition = ExitTransition.horizontalTween(AnimationDuration)

private val NoEnterTransition = EnterTransition.horizontalTween(0)
private val NoExitTransition = ExitTransition.horizontalTween(0)

private val AnimationTween = tween<Float>(AnimationDuration)
private val NoAnimationTween = tween<Float>(0)

@Composable
fun Smartbar() {
    val prefs by FlorisPreferenceStore
    val smartbarEnabled by prefs.smartbar.enabled.collectAsState()
    val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.collectAsState()

    AnimatedVisibility(
        visible = smartbarEnabled,
        enter = VerticalEnterTransition,
        exit = VerticalExitTransition,
    ) {
        when (extendedActionsPlacement) {
            ExtendedActionsPlacement.ABOVE_CANDIDATES -> {
                SnyggColumn(FlorisImeUi.Smartbar.elementName) {
                    SmartbarSecondaryRow()
                    SmartbarMainRow()
                }
            }

            ExtendedActionsPlacement.BELOW_CANDIDATES -> {
                SnyggColumn(FlorisImeUi.Smartbar.elementName) {
                    SmartbarMainRow()
                    SmartbarSecondaryRow()
                }
            }

            ExtendedActionsPlacement.OVERLAY_APP_UI -> {
                SnyggBox(FlorisImeUi.Smartbar.elementName,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(FlorisImeSizing.smartbarHeight),
                    allowClip = false,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(FlorisImeSizing.smartbarHeight * 2)
                            .absoluteOffset(y = -FlorisImeSizing.smartbarHeight),
                        contentAlignment = Alignment.BottomStart,
                    ) {
                        SmartbarSecondaryRow()
                    }
                    SmartbarMainRow()
                }
            }
        }
    }
}

@Composable
private fun SmartbarMainRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val scope = rememberCoroutineScope()

    val inlineSuggestions by NlpInlineAutofill.suggestions.collectAsState()
    LaunchedEffect(inlineSuggestions) {
        nlpManager.autoExpandCollapseSmartbarActions(null, inlineSuggestions)
    }
    val shouldShowInlineSuggestionsUi = AndroidVersion.ATLEAST_API30_R && inlineSuggestions.isNotEmpty()

    val smartbarLayout by prefs.smartbar.layout.collectAsState()
    val flipToggles by prefs.smartbar.flipToggles.collectAsState()
    val sharedActionsExpanded by prefs.smartbar.sharedActionsExpanded.collectAsState()
    val extendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.collectAsState()

    val shouldAnimate by prefs.smartbar.sharedActionsExpandWithAnimation.collectAsState()

    @Composable
    fun SharedActionsToggle() {
        SnyggIconButton(
            elementName = FlorisImeUi.SmartbarSharedActionsToggle.elementName,
            onClick = {
                if (/* was */ sharedActionsExpanded) {
                    keyboardManager.activeState.isActionsOverflowVisible = false
                }
                scope.launch {
                    prefs.smartbar.sharedActionsExpanded.set(!sharedActionsExpanded)
                }
            },
            modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
        ) {
            val transition = updateTransition(sharedActionsExpanded, label = "sharedActionsExpandedToggleBtn")
            val rotation by transition.animateFloat(
                transitionSpec = {
                    if (shouldAnimate) AnimationTween else NoAnimationTween
                },
                label = "rotation",
            ) {
                if (it) 180f else 0f
            }
            val arrowIcon = if (flipToggles) {
                Icons.AutoMirrored.Default.KeyboardArrowLeft
            } else {
                Icons.AutoMirrored.Default.KeyboardArrowRight
            }
            val incognitoIcon = ImageVector.vectorResource(id = R.drawable.ic_incognito)
            val incognitoDisplayMode = prefs.keyboard.incognitoDisplayMode.collectAsState()
            val isIncognitoMode = keyboardManager.activeState.isIncognitoMode
            val icon = if (isIncognitoMode) {
                when (incognitoDisplayMode.value) {
                    IncognitoDisplayMode.REPLACE_SHARED_ACTIONS_TOGGLE -> incognitoIcon
                    IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD -> arrowIcon
                }
            } else {
                arrowIcon
            }
            SnyggIcon(
                modifier = Modifier.rotate(if (incognitoDisplayMode.value == IncognitoDisplayMode.DISPLAY_BEHIND_KEYBOARD) rotation else 0f),
                imageVector = icon,
            )
        }
    }

    @Composable
    fun RowScope.CenterContent() {
        val expanded = sharedActionsExpanded && smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        ) {
            val enterTransition = if (shouldAnimate) HorizontalEnterTransition else NoEnterTransition
            val exitTransition = if (shouldAnimate) HorizontalExitTransition else NoExitTransition
            this@CenterContent.AnimatedVisibility(
                visible = !expanded,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                if (shouldShowInlineSuggestionsUi) {
                    InlineSuggestionsUi(inlineSuggestions)
                } else {
                    CandidatesRow()
                }
            }
            this@CenterContent.AnimatedVisibility(
                visible = expanded,
                enter = enterTransition,
                exit = exitTransition,
            ) {
                QuickActionsRow(
                    FlorisImeUi.SmartbarSharedActionsRow.elementName,
                    modifier = modifier.fillMaxSize(),
                )
            }
        }
    }

    @Composable
    fun ExtendedActionsToggle() {
        SnyggIconButton(
            FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
            onClick = {
                if (/* was */ extendedActionsExpanded) {
                    keyboardManager.activeState.isActionsOverflowVisible = false
                }
                scope.launch {
                    prefs.smartbar.extendedActionsExpanded.set(!extendedActionsExpanded)
                }
            },
            modifier = Modifier.sizeIn(maxHeight = FlorisImeSizing.smartbarHeight).aspectRatio(1f)
        ) {
            val transition = updateTransition(extendedActionsExpanded, label = "smartbarSecondaryRowToggleBtn")
            val alpha by transition.animateFloat(label = "alpha") { if (it) 1f else 0f }
            val rotation by transition.animateFloat(label = "rotation") { if (it) 180f else 0f }
            // Expanded icon
            SnyggIcon(
                FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
                modifier = Modifier
                    .alpha(alpha)
                    .rotate(rotation),
                imageVector = Icons.Default.UnfoldLess,
            )
            // Not expanded icon
            SnyggIcon(
                FlorisImeUi.SmartbarExtendedActionsToggle.elementName,
                modifier = Modifier
                    .alpha(1f - alpha)
                    .rotate(rotation - 180f),
                imageVector = Icons.Default.UnfoldMore,
            )
        }
    }

    @Composable
    fun StickyAction() {
        val actionArrangement by prefs.smartbar.actionArrangement.collectAsState()
        val evaluator by keyboardManager.activeSmartbarEvaluator.collectAsState()

        val action = when {
            actionArrangement.stickyAction != null -> {
                actionArrangement.stickyAction
            }

            smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED && sharedActionsExpanded -> {
                ToggleOverflowPanelAction
            }

            else -> null
        }

        androidx.compose.foundation.layout.Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (action != null) {
                QuickActionButton(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    action = action,
                    evaluator = evaluator,
                )
            } else {
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .aspectRatio(1f),
                )
            }
            BatteryIndicatorWidget(
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }

    SideEffect {
        if (!shouldAnimate) {
            scope.launch {
                prefs.smartbar.sharedActionsExpandWithAnimation.set(true)
            }
        }
    }

    SnyggRow(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.smartbarHeight),
    ) {
        when (smartbarLayout) {
            SmartbarLayout.SUGGESTIONS_ONLY -> {
                if (shouldShowInlineSuggestionsUi) {
                    InlineSuggestionsUi(inlineSuggestions)
                } else {
                    CandidatesRow()
                }
            }

            SmartbarLayout.ACTIONS_ONLY -> {
                if (shouldShowInlineSuggestionsUi) {
                    InlineSuggestionsUi(inlineSuggestions)
                } else {
                    QuickActionsRow(FlorisImeUi.SmartbarSharedActionsRow.elementName)
                }
            }

            SmartbarLayout.SUGGESTIONS_ACTIONS_SHARED -> {
                if (!flipToggles) {
                    SharedActionsToggle()
                    CenterContent()
                    StickyAction()
                } else {
                    StickyAction()
                    CenterContent()
                    SharedActionsToggle()
                }
            }

            SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED -> {
                if (!flipToggles) {
                    ExtendedActionsToggle()
                    CenterContent()
                    StickyAction()
                } else {
                    StickyAction()
                    CenterContent()
                    ExtendedActionsToggle()
                }
            }
        }
    }
}

@Composable
private fun SmartbarSecondaryRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val smartbarLayout by prefs.smartbar.layout.collectAsState()
    val secondaryRowStyle = rememberSnyggThemeQuery(FlorisImeUi.SmartbarExtendedActionsRow.elementName)
    val windowStyle = rememberSnyggThemeQuery(FlorisImeUi.Window.elementName)
    val extendedActionsExpanded by prefs.smartbar.extendedActionsExpanded.collectAsState()
    val extendedActionsPlacement by prefs.smartbar.extendedActionsPlacement.collectAsState()
    val background = secondaryRowStyle.background().let { color ->
        if (extendedActionsPlacement == ExtendedActionsPlacement.OVERLAY_APP_UI) {
            if (color.isUnspecified || color.alpha == 0f) {
                windowStyle.background(default = Color.Black)
            } else {
                color
            }
        } else {
            color
        }
    }

    AnimatedVisibility(
        visible = smartbarLayout == SmartbarLayout.SUGGESTIONS_ACTIONS_EXTENDED && extendedActionsExpanded,
        enter = VerticalEnterTransition,
        exit = VerticalExitTransition,
    ) {
        QuickActionsRow(
            FlorisImeUi.SmartbarExtendedActionsRow.elementName,
            modifier = modifier
                .fillMaxWidth()
                .height(FlorisImeSizing.smartbarHeight)
                .background(background),
        )
    }
}

@Composable
fun BatteryIndicatorWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val editorInstance by context.editorInstance()
    val activeContent by editorInstance.activeContentFlow.collectAsState()

    var overchargeTriggerTime by remember { mutableStateOf(0L) }
    LaunchedEffect(activeContent) {
        val tb = activeContent.textBeforeSelection.toString().lowercase()
        val comp = activeContent.composingText.lowercase()
        val batteryKeys = listOf("battery", "batteries", "supercharge", "overcharge", "power")
        if (batteryKeys.any { tb.endsWith(it) || tb.endsWith("$it ") || comp == it }) {
            overchargeTriggerTime = System.currentTimeMillis()
        }
    }

    val batteryAnim = remember(overchargeTriggerTime) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(overchargeTriggerTime) {
        if (overchargeTriggerTime > 0L) {
            // Smooth 300ms fade in
            batteryAnim.animateTo(1f, tween(300, easing = androidx.compose.animation.core.LinearEasing))
            // Hold full supercharge for 4200ms
            kotlinx.coroutines.delay(4200L)
            // Smooth 500ms fade out completely
            batteryAnim.animateTo(0f, tween(500, easing = androidx.compose.animation.core.LinearEasing))
            overchargeTriggerTime = 0L
        }
    }

    val overchargeAlpha = batteryAnim.value

    if (overchargeAlpha > 0f) {
        val batteryManager = remember { context.getSystemService(Context.BATTERY_SERVICE) as? android.os.BatteryManager }
        val realLevel = remember {
            batteryManager?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.coerceIn(0, 100) ?: 100
        }
        OverchargeBatteryCanvas(modifier = modifier, overchargeAlpha = overchargeAlpha, realLevel = realLevel)
    }
}

@Composable
private fun OverchargeBatteryCanvas(
    modifier: Modifier,
    overchargeAlpha: Float,
    realLevel: Int,
) {
    val density = LocalDensity.current.density
    val infiniteLightningTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "LightningTransition")
    val lightningPhase by infiniteLightningTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(280, easing = androidx.compose.animation.core.LinearEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Restart,
        ),
        label = "LightningCycle",
    )

    val energyPulse by infiniteLightningTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.25f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "EnergyPulse",
    )

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .width(28.dp)
            .height(FlorisImeSizing.smartbarHeight)
            .graphicsLayer {
                val vibration = (kotlin.math.sin(lightningPhase * 2 * Math.PI) * 2f * overchargeAlpha).toFloat()
                translationY = vibration
                scaleX = 1f + (energyPulse - 1f) * overchargeAlpha * 0.4f
                scaleY = 1f + (energyPulse - 1f) * overchargeAlpha * 0.4f
            }
    ) {
        val canvasW = this.size.width
        val canvasH = this.size.height
        val cx = canvasW / 2f
        val cy = canvasH / 2f

        val battW = 18f * density
        val battH = 9.5f * density
        val left = cx - battW / 2f
        val top = cy - battH / 2f
        val right = left + battW
        val bottom = top + battH
        val corner = 2.5f * density

        // 1. Overcharged Outer Neon Aura Glow
        val auraPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((overchargeAlpha * 120 * (energyPulse - 0.7f)).toInt().coerceIn(0, 255), 0, 240, 255)
            style = android.graphics.Paint.Style.FILL
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(left - 4f * density, top - 4f * density, right + 6f * density, bottom + 4f * density, corner * 2.5f, corner * 2.5f, auraPaint)

        val yellowAura = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((overchargeAlpha * 160).toInt().coerceIn(0, 255), 255, 230, 0)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.8f * density
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(left - 2f * density, top - 2f * density, right + 4.5f * density, bottom + 2f * density, corner * 1.8f, corner * 1.8f, yellowAura)

        // 2. Battery Shell Border & Terminal Nub (Electric Gold)
        val shellPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((overchargeAlpha * 255).toInt().coerceIn(0, 255), 255, 235, 59)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 1.2f * density
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(left, top, right, bottom, corner, corner, shellPaint)

        // Terminal Positive Nub
        val nubPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = shellPaint.color
            style = android.graphics.Paint.Style.FILL
        }
        val nubW = 1.8f * density
        val nubH = 4.2f * density
        drawContext.canvas.nativeCanvas.drawRoundRect(right + 0.5f, cy - nubH / 2f, right + nubW + 0.5f, cy + nubH / 2f, 1f * density, 1f * density, nubPaint)

        // 3. Battery Fill (Neon Supercharged Plasma)
        val fillPadding = 1.6f * density
        val fillMaxW = (battW - fillPadding * 2)
        val currentFillW = fillMaxW * 1.0f

        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            val r = (0 + overchargeAlpha * 255).toInt().coerceIn(0, 255)
            val g = 240
            val b = (255 - overchargeAlpha * 100).toInt().coerceIn(0, 255)
            val a = (overchargeAlpha * 230).toInt().coerceIn(0, 255)
            color = android.graphics.Color.argb(a, r, g, b)
            style = android.graphics.Paint.Style.FILL
        }
        drawContext.canvas.nativeCanvas.drawRoundRect(
            left + fillPadding,
            top + fillPadding,
            left + fillPadding + currentFillW,
            bottom - fillPadding,
            corner * 0.6f,
            corner * 0.6f,
            fillPaint
        )

        // 4. Energized Electric Lightning Bolt
        val boltPath = android.graphics.Path().apply {
            val bx = cx - 1f * density
            val by = cy
            moveTo(bx + 1.5f * density, by - 3.8f * density)
            lineTo(bx - 2.8f * density, by + 0.4f * density)
            lineTo(bx - 0.4f * density, by + 0.4f * density)
            lineTo(bx - 1.4f * density, by + 3.8f * density)
            lineTo(bx + 2.8f * density, by - 0.4f * density)
            lineTo(bx + 0.4f * density, by - 0.4f * density)
            close()
        }
        val boltPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((overchargeAlpha * 255).toInt().coerceIn(0, 255), 255, 255, 255)
            style = android.graphics.Paint.Style.FILL
        }
        val boltGlow = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb((overchargeAlpha * 200).toInt().coerceIn(0, 255), 255, 230, 0)
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 0.9f * density
        }
        drawContext.canvas.nativeCanvas.drawPath(boltPath, boltGlow)
        drawContext.canvas.nativeCanvas.drawPath(boltPath, boltPaint)
    }
}
