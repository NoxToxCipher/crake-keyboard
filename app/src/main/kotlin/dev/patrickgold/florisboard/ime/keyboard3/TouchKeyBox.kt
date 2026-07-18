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

package dev.patrickgold.florisboard.ime.keyboard3

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggText
import org.k3lp.lib.text.K3Descriptor
import org.k3lp.lib.text.K3String
import org.k3lp.runtime.K3ComputedKey
import org.k3lp.runtime.K3KeystrokeEngine

@Composable
fun TouchKey(
    computedKey: K3ComputedKey,
    touchSize: DpSize,
    visibleSize: DpSize,
    engine: K3KeystrokeEngine,
) {
    val prefs by FlorisPreferenceStore
    val longPressDelay by prefs.keyboard.longPressDelay.collectAsState()

//    val attributes = mapOf(
//        FlorisImeUi.Attr.Code to key.computedData.code,
//        FlorisImeUi.Attr.Mode to evaluator.keyboard.mode.toString(),
//        FlorisImeUi.Attr.ShiftState to evaluator.state.inputShiftState.toString(),
//    )
    var selector by remember { mutableStateOf(SnyggSelector.NONE) }
    val isSuitableForBasicPopup: Boolean = computedKey.data.output.let { output ->
        output != null && output is K3String
    }
    val isSuitableForExtendedPopup: Boolean = !computedKey.data.longPressKeyIds.isNullOrEmpty()

    Box(
        modifier = Modifier
            .requiredSize(touchSize)
            .absoluteOffset { IntOffset(computedKey.x, computedKey.y) }
            .pointerInput(computedKey, longPressDelay) {
                awaitEachGesture {
                    awaitFirstDown().also { it.consume() }
                    selector = SnyggSelector.PRESSED
                    val type = determineKeyEventType(longPressDelay)
                    if (type == KeyEventType.KEY_PRESS) {
                        engine.onKeyPress(computedKey.data)
                    } else if (type == KeyEventType.LONG_PRESS) {
                        // TODO
                        waitForUpOrCancellation()?.let { it.consume() }
                    }
                    selector = SnyggSelector.NONE
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        SnyggBox(
            FlorisImeUi.Key.elementName,
            attributes = emptyMap(),
            selector = selector,
            modifier = Modifier
                .requiredSize(visibleSize),
        ) {
            TouchKeyDisplay(
                computedKey = computedKey,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (isSuitableForBasicPopup && selector == SnyggSelector.PRESSED) {
            TouchKeySimplePopupBox(
                modifier = Modifier
                    .requiredSize(
                        width = visibleSize.width * 1.1f,
                        height = visibleSize.height * 2.5f,
                    )
                    .offset(y = (visibleSize.height * -2.5f + visibleSize.height) / 2f),
                attributes = emptyMap(), // TODO
                shouldIndicateExtendedPopups = isSuitableForExtendedPopup,
            ) {
                TouchKeyDisplay(
                    computedKey = computedKey,
                    modifier = Modifier
                        .requiredSize(visibleSize),
                )
            }
        }
    }
}

@Composable
fun TouchKeyDisplay(
    computedKey: K3ComputedKey,
    modifier: Modifier = Modifier,
) {
    when (val keytop = computedKey.keytop) {
        is K3String -> {
            SnyggText(
                modifier = modifier
                    .wrapContentSize(),
                text = keytop.toText(),
            )
        }
        is K3Descriptor -> {
            // TODO this should be an icon
            SnyggText(
                modifier = modifier
                    .wrapContentSize(),
                text = keytop.toString(),
            )
        }
    }
}

private suspend fun AwaitPointerEventScope.determineKeyEventType(
    longPressDelay: Int,
): KeyEventType {
    var type = KeyEventType.CANCELLED
    try {
        withTimeout(longPressDelay.toLong()) {
            waitForUpOrCancellation()?.let {
                it.consume()
                type = KeyEventType.KEY_PRESS
            }
        }
    } catch (_: PointerEventTimeoutCancellationException) {
        type = KeyEventType.LONG_PRESS
    }
    return type
}

private enum class KeyEventType {
    CANCELLED,
    KEY_PRESS,
    LONG_PRESS,
}
