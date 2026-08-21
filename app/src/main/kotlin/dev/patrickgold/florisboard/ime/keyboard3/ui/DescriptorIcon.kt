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

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRightAlt
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPasteGo
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FontDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardHide
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SentimentSatisfiedAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.ime.editor.ImeOptions
import dev.patrickgold.florisboard.ime.keyboard3.ImeState
import dev.patrickgold.florisboard.ime.window.ImeWindowConfig
import dev.patrickgold.florisboard.ime.window.ImeWindowMode
import dev.patrickgold.florisboard.ime.window.LocalWindowController
import dev.patrickgold.florisboard.imeController
import dev.patrickgold.florisboard.lib.compose.vectorResource
import org.florisboard.lib.compose.icons.ForwardDelete
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.k3lp.lib.text.K3Descriptor

@Composable
fun DescriptorIcon(
    descriptor: K3Descriptor,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imeController by context.imeController()
    // TODO this could break if ever used outside IME window
    val windowController = LocalWindowController.current

    val imeState by imeController.activeState.collectAsState()
    val windowConfig by windowController.activeWindowConfig.collectAsState()

    val imageVector = remember(descriptor, imeState) {
        when (descriptor.namespace) {
            "floris" -> when (descriptor.type) {
                "icon" -> context.florisIconByName(descriptor.name, imeState, windowConfig)
                else -> null
            }
            else -> null
        }
    }

    if (imageVector != null) {
        SnyggIcon(
            modifier = modifier,
            imageVector = imageVector,
        )
    }
}

private fun Context.florisIconByName(
    name: String,
    imeState: ImeState,
    windowConfig: ImeWindowConfig,
): ImageVector? {
    val imeOptions = imeState.editor.info.imeOptions
    val inputAttributes = imeState.editor.info.inputAttributes
    return when (name) {
        "accessibility_one_handed" -> vectorResource(R.drawable.ic_accessibility_one_handed)
        "arrow_down" -> Icons.Default.KeyboardArrowDown
        "arrow_left" -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        "arrow_right" -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        "arrow_up" -> Icons.Default.KeyboardArrowUp
        "backspace" -> Icons.AutoMirrored.Outlined.Backspace
        "clipboard_clear_primary_clip" -> Icons.Default.DeleteSweep
        "clipboard_copy" -> Icons.Default.ContentCopy
        "clipboard_cut" -> Icons.Default.ContentCut
        "clipboard_paste" -> Icons.Default.ContentPasteGo
        "clipboard_select_all" -> Icons.Default.SelectAll
        "drag_marker" -> {
            if (imeState.flags.debugShowDragAndDropHelpers) Icons.Default.Close else null
        }
        "enter" -> {
            if (imeOptions.flagNoEnterAction || inputAttributes.flagTextMultiLine) {
                Icons.AutoMirrored.Filled.KeyboardReturn
            } else {
                when (imeState.editor.info.imeOptions.action) {
                    ImeOptions.Action.DONE -> Icons.Default.Done
                    ImeOptions.Action.GO -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.NEXT -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.NONE -> Icons.AutoMirrored.Filled.KeyboardReturn
                    ImeOptions.Action.PREVIOUS -> Icons.AutoMirrored.Filled.ArrowRightAlt
                    ImeOptions.Action.SEARCH -> Icons.Default.Search
                    ImeOptions.Action.SEND -> Icons.AutoMirrored.Filled.Send
                    ImeOptions.Action.UNSPECIFIED -> Icons.AutoMirrored.Filled.KeyboardReturn
                }
            }
        }
        "forward_delete" -> Icons.AutoMirrored.Default.ForwardDelete
        "hide_keyboard" -> Icons.Default.KeyboardHide
        "language_switch" -> Icons.Default.Language
        "mode_media" -> Icons.Default.SentimentSatisfiedAlt
        "mode_clipboard" -> Icons.AutoMirrored.Outlined.Assignment
        "noop" -> Icons.Default.Close
        "redo" -> Icons.AutoMirrored.Filled.Redo
        "toggle_actions_overflow" -> Icons.Default.MoreHoriz
        "toggle_autocorrect" -> Icons.Default.FontDownload
        "toggle_floating_window" -> when (windowConfig.mode) {
            ImeWindowMode.FIXED -> vectorResource(R.drawable.ic_floating_keyboard)
            ImeWindowMode.FLOATING -> vectorResource(R.drawable.ic_floating_keyboard_disable)
        }
        "toggle_resize_mode" -> vectorResource(R.drawable.ic_resize)
        "undo" -> Icons.AutoMirrored.Filled.Undo
        "voice_keyboard" -> Icons.Default.KeyboardVoice
        // TODO shift???
        // TODO incognito mode???
        // TODO char width/kata/hira icons???
        else -> null
    }
}
