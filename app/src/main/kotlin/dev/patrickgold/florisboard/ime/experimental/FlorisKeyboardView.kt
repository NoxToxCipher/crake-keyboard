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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import org.florisboard.lib.compose.toMm
import org.k3lp.model.K3Model
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
        val keyboardWidth = constraints.maxWidth
        val keyboardHeight = constraints.maxHeight
        val keyboardRowBaseHeight = FlorisImeSizing.keyboardRowBaseHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()
        val keyMarginH by remember { derivedStateOf { windowSpec.keyMarginH.toPx() } }
        val keyMarginV by remember { derivedStateOf { windowSpec.keyMarginV.toPx() } }

        val computedLayout = remember(touchLayerId, model, keyboardWidth, keyboardHeight, maxWidth) {
            doComputeLayout(model, keyboardWidth, keyboardHeight, maxWidth.toMm().toInt(), touchLayerId)
        }

        if (computedLayout == null) {
            Text("Computed layout is null :(")
            return@BoxWithConstraints
        }
        for (computedKey in computedLayout.keys) key(computedKey) {
            Box(
                modifier = Modifier
                    .size(computedKey.width.toDp(), computedKey.height.toDp())
                    .absoluteOffset {
                        IntOffset(computedKey.x, computedKey.y)
                    }
                    .clickable {
                        engine.onKeyPress(computedKey.data)
                    }
                    .border(1.dp, Color.Red)
            )
        }
    }
}
