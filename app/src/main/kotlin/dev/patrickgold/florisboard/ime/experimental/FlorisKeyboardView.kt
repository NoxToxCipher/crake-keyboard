/*
 * Copyright (C) 2026 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.experimental

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.toMm
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggText
import org.k3lp.lib.text.K3Descriptor
import org.k3lp.lib.text.K3String
import org.k3lp.model.K3Model
import org.k3lp.runtime.K3ComputedKey
import org.k3lp.runtime.doComputeLayout

@Composable
fun FlorisKeyboardView(
    model: K3Model,
    engine: FlorisKeystrokeEngine,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    val touchLayerId by engine.touchLayerId.collectAsState()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        val prefs by FlorisPreferenceStore
        val debugShowTouchBoundaries by prefs.devtools.showKeyTouchBoundaries.collectAsState()

        val keyboardWidth = constraints.maxWidth
        val keyboardHeight = constraints.maxHeight
        val keyboardRowBaseHeight = FlorisImeSizing.keyboardRowBaseHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()

        val computedLayout = remember(touchLayerId, model, keyboardWidth, keyboardHeight, maxWidth) {
            doComputeLayout(model, keyboardWidth, keyboardHeight, maxWidth.toMm().toInt(), touchLayerId)
        }

        if (computedLayout == null) {
            Text("Computed layout is null :(")
            return@BoxWithConstraints
        }
        for (computedKey in computedLayout.keys) key(computedKey) {
            FlorisKeyView(
                computedKey,
                engine,
                modifier = Modifier
                    .requiredSize(computedKey.width.toDp(), computedKey.height.toDp())
                    .absoluteOffset {
                        IntOffset(computedKey.x, computedKey.y)
                    }
                    .conditional(debugShowTouchBoundaries) { border(0.5.dp, Color.Red) }
                    .padding(windowSpec.keyMarginH, windowSpec.keyMarginV)
            )
        }
    }
}

@Composable
private fun FlorisKeyView(
    computedKey: K3ComputedKey,
    engine: FlorisKeystrokeEngine,
    modifier: Modifier = Modifier,
) {
//    val attributes = mapOf(
//        FlorisImeUi.Attr.Code to key.computedData.code,
//        FlorisImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
//        FlorisImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
//    )
    var selector by remember { mutableStateOf(SnyggSelector.NONE) }

    SnyggBox(
        FlorisImeUi.Key.elementName,
        attributes = emptyMap(),
        selector = selector,
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown().also { it.consume() }
                    selector = SnyggSelector.PRESSED
                    waitForUpOrCancellation()?.also { it.consume() }
                    selector = SnyggSelector.NONE
                    engine.onKeyPress(computedKey.data)
                }
            }
    ) {
        when (val keytop = computedKey.keytop) {
            is K3String -> {
                SnyggText(
                    modifier = Modifier
                        .wrapContentSize()
                        .align(Alignment.Center),
                    text = keytop.toText(),
                )
            }
            is K3Descriptor -> {
                // TODO this should be an icon
                SnyggText(
                    modifier = Modifier
                        .wrapContentSize()
                        .align(Alignment.Center),
                    text = keytop.toString(),
                )
            }
        }
    }
}
