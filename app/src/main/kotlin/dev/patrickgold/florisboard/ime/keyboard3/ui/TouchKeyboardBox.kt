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

package dev.patrickgold.florisboard.ime.keyboard3.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.keyboard.FlorisImeSizing
import dev.patrickgold.florisboard.ime.keyboard3.ImeController
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.toMm
import org.k3lp.runtime.doComputeLayout

@Composable
fun TouchKeyboardBox(
    imeController: ImeController,
    modifier: Modifier = Modifier,
) = with(LocalDensity.current) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(FlorisImeSizing.keyboardUiHeight()),
    ) {
        val prefs by FlorisPreferenceStore
        val debugShowTouchBoundaries by prefs.devtools.showKeyTouchBoundaries.collectAsState()

        val keyboardWidth = constraints.maxWidth
        val keyboardHeight = constraints.maxHeight

        val windowController = LocalWindowController.current
        val windowSpec by windowController.activeWindowSpec.collectAsState()

        val imeState by imeController.activeState.collectAsState()
        val model by remember { derivedStateOf { imeState.model } }
        val touchLayerId by remember { derivedStateOf { imeState.touchLayerId } }

        val computedLayout = remember(touchLayerId, model, keyboardWidth, keyboardHeight, maxWidth) {
            doComputeLayout(model, keyboardWidth, keyboardHeight, maxWidth.toMm().toInt(), touchLayerId)
        }

        if (computedLayout == null) {
            Text("Computed layout is null :(")
            return@BoxWithConstraints
        }
        for (computedKey in computedLayout.keys) {
            if (computedKey.data.gap) {
                continue
            }
            key(computedKey) {
                val touchSize = DpSize(
                    width = computedKey.width.toDp(),
                    height = computedKey.height.toDp(),
                )
                val visibleSize = DpSize(
                    width = touchSize.width - windowSpec.keyMarginH * 2,
                    height = touchSize.height - windowSpec.keyMarginV * 2,
                )
                TouchKey(
                    computedKey,
                    touchSize,
                    visibleSize,
                    imeController,
                )
            }
        }
    }
}
