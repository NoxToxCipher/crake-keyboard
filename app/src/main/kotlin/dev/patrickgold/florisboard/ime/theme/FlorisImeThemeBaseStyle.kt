/*
 * Copyright (C) 2022-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.text.key.KeyCode
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.SnyggStylesheet

val FlorisImeThemeBaseStyle = SnyggStylesheet.v2 {
    defines {
        "--primary" to rgbaColor(0, 229, 255)
        "--primary-variant" to rgbaColor(0, 180, 216)
        "--secondary" to rgbaColor(0, 229, 255)
        "--secondary-variant" to rgbaColor(0, 180, 216)
        "--background" to rgbaColor(10, 12, 18)
        "--background-variant" to rgbaColor(16, 20, 28)
        "--surface" to rgbaColor(22, 27, 38)
        "--surface-variant" to rgbaColor(35, 42, 58)

        "--on-primary" to rgbaColor(10, 12, 18)
        "--on-background" to rgbaColor(255, 255, 255)
        "--on-background-disabled" to rgbaColor(80, 90, 105)
        "--on-surface" to rgbaColor(255, 255, 255)

        "--shape" to roundedCornerShape(8.dp)
        "--shape-variant" to roundedCornerShape(12.dp)
    }

    FlorisImeUi.Window.elementName {
        background = `var`("--background")
        foreground = `var`("--on-background")
    }

    FlorisImeUi.Key.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(22.sp)
        shadowElevation = size(2.dp)
        shape = `var`("--shape")
        textMaxLines = textMaxLines(1)
    }
    FlorisImeUi.Key.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface-variant")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.ENTER)) {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.ENTER), selector = SnyggSelector.PRESSED) {
        background = `var`("--primary-variant")
        foreground = `var`("--on-primary")
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.SPACE)) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        fontSize = fontSize(12.sp)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(
        KeyCode.VIEW_CHARACTERS,
        KeyCode.VIEW_SYMBOLS,
        KeyCode.VIEW_SYMBOLS2,
    )) {
        fontSize = fontSize(18.sp)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(
        KeyCode.VIEW_NUMERIC,
        KeyCode.VIEW_NUMERIC_ADVANCED,
    )) {
        fontSize = fontSize(12.sp)
    }
    FlorisImeUi.Key.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.VIEW_NUMERIC_ADVANCED)) {
        textMaxLines = textMaxLines(2)
    }
    FlorisImeUi.Key.elementName(
        FlorisImeUi.Attr.Code to listOf(KeyCode.SHIFT),
        FlorisImeUi.Attr.ShiftState to listOf(InputShiftState.CAPS_LOCK.toString()),
    ) {
        foreground = rgbaColor(255, 152, 0)
    }
    FlorisImeUi.KeyHint.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-surface-variant")
        fontFamily = genericFontFamily(FontFamily.Monospace)
        fontSize = fontSize(12.sp)
        padding = padding(0.dp, 1.dp, 1.dp, 0.dp)
        textMaxLines = textMaxLines(1)
    }
    FlorisImeUi.KeyPopupBox.elementName {
        background = rgbaColor(16, 20, 30)
        foreground = rgbaColor(0, 229, 255)
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
        shadowElevation = size(6.dp)
    }
    FlorisImeUi.KeyPopupElement.elementName(selector = SnyggSelector.FOCUS) {
        background = rgbaColor(0, 229, 255)
        foreground = rgbaColor(10, 12, 18)
        shape = `var`("--shape")
    }
    FlorisImeUi.KeyPopupExtendedIndicator.elementName {
        fontSize = fontSize(16.sp)
        foreground = rgbaColor(0, 229, 255)
    }

    FlorisImeUi.Smartbar.elementName {
        fontSize = fontSize(18.sp)
    }
    FlorisImeUi.SmartbarSharedActionsToggle.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        margin = padding(6.dp)
        shape = circleShape()
        shadowElevation = size(2.dp)
    }
    FlorisImeUi.SmartbarExtendedActionsToggle.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(144, 144, 144)
        margin = padding(6.dp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarActionKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        shape = `var`("--shape")
    }
    FlorisImeUi.SmartbarActionKey.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }

    FlorisImeUi.SmartbarActionsOverflow.elementName {
        margin = padding(4.dp)
    }
    FlorisImeUi.SmartbarActionsOverflowCustomizeButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        fontSize = fontSize(14.sp)
        margin = padding(0.dp, 8.dp, 0.dp, 0.dp)
        shape = roundedCornerShape(24.dp)
    }
    FlorisImeUi.SmartbarActionTile.elementName {
        background = rgbaColor(20, 25, 36)
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(6.dp)
        shape = roundedCornerShape(12.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionTile.elementName(selector = SnyggSelector.PRESSED) {
        background = rgbaColor(32, 40, 58)
        foreground = rgbaColor(0, 229, 255)
    }
    FlorisImeUi.SmartbarActionTile.elementName(selector = SnyggSelector.DISABLED) {
        foreground = `var`("--on-background-disabled")
    }
    FlorisImeUi.SmartbarActionTileIcon.elementName {
        fontSize = fontSize(24.sp)
        margin = padding(0.dp, 0.dp, 0.dp, 8.dp)
    }

    FlorisImeUi.SmartbarActionsEditor.elementName {
        background = rgbaColor(10, 12, 18)
        foreground = `var`("--on-background")
        shape = roundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SmartbarActionsEditorHeader.elementName {
        background = rgbaColor(16, 20, 30)
        foreground = rgbaColor(0, 229, 255)
        fontSize = fontSize(16.sp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorHeaderButton.elementName {
        margin = padding(4.dp)
        shape = circleShape()
    }
    FlorisImeUi.SmartbarActionsEditorSubheader.elementName {
        foreground = rgbaColor(0, 229, 255)
        fontSize = fontSize(16.sp)
        fontWeight = fontWeight(FontWeight.Bold)
        padding = padding(12.dp, 16.dp, 12.dp, 8.dp)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorTileGrid.elementName {
        margin = padding(4.dp, 0.dp)
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName {
        margin = padding(4.dp)
        padding = padding(8.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(2)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.NOOP)) {
        foreground = `var`("--on-background-disabled")
    }
    FlorisImeUi.SmartbarActionsEditorTile.elementName(FlorisImeUi.Attr.Code to listOf(KeyCode.DRAG_MARKER)) {
        foreground = rgbaColor(0, 229, 255)
    }

    FlorisImeUi.SmartbarCandidateWord.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = rectangleShape()
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarCandidateWord.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.SmartbarCandidateWordSecondaryText.elementName {
        fontSize = fontSize(8.sp)
        margin = padding(0.dp, 2.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SmartbarCandidateClip.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = rgbaColor(220, 220, 220)
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(8.dp, 0.dp)
        shape = roundedCornerShape(8)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SmartbarCandidateClip.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }
    FlorisImeUi.SmartbarCandidateClipIcon.elementName {
        margin = padding(0.dp, 0.dp, 4.dp, 0.dp)
    }
    FlorisImeUi.SmartbarCandidateSpacer.elementName {
        foreground = rgbaColor(255, 255, 255, 0.25f)
    }

    FlorisImeUi.ClipboardHeader.elementName {
        foreground = `var`("--on-background")
        fontSize = fontSize(16.sp)
    }
    FlorisImeUi.ClipboardSubheader.elementName {
        fontSize = fontSize(14.sp)
        margin = padding(6.dp)
    }
    FlorisImeUi.ClipboardContent.elementName {
        padding = padding(10.dp)
    }
    FlorisImeUi.ClipboardItem.elementName {
        background = rgbaColor(18, 22, 32)
        foreground = `var`("--on-surface")
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = roundedCornerShape(10.dp)
        shadowElevation = size(3.dp)
        textMaxLines = textMaxLines(10)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.ClipboardItem.elementName(selector = SnyggSelector.PRESSED) {
        background = rgbaColor(28, 35, 52)
        foreground = rgbaColor(0, 229, 255)
    }
    FlorisImeUi.ClipboardItemPopup.elementName {
        background = rgbaColor(16, 20, 30)
        foreground = rgbaColor(0, 229, 255)
        fontSize = fontSize(14.sp)
        margin = padding(4.dp)
        padding = padding(12.dp, 8.dp)
        shape = roundedCornerShape(10.dp)
        shadowElevation = size(6.dp)
    }
    FlorisImeUi.ClipboardItemActions.elementName {
        background = rgbaColor(16, 20, 30)
        foreground = `var`("--on-surface")
        margin = padding(4.dp)
        shape = roundedCornerShape(10.dp)
        shadowElevation = size(6.dp)
    }
    FlorisImeUi.ClipboardItemAction.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(12.dp)
    }
    FlorisImeUi.ClipboardItemActionText.elementName {
        margin = padding(8.dp, 0.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.ClipboardHistoryDisabledButton.elementName {
        background = `var`("--primary")
        foreground = `var`("--on-primary")
        shape = roundedCornerShape(24.dp)
    }

    FlorisImeUi.MediaEmojiKey.elementName {
        background = rgbaColor(0, 0, 0, 0f)
        foreground = `var`("--on-background")
        fontSize = fontSize(22.sp)
        shape = `var`("--shape")
    }
    FlorisImeUi.MediaEmojiKey.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    FlorisImeUi.MediaEmojiTab.elementName {
        foreground = rgbaColor(150, 165, 185)
    }
    FlorisImeUi.MediaEmojiTab.elementName(selector = SnyggSelector.FOCUS) {
        foreground = rgbaColor(0, 229, 255)
    }
    FlorisImeUi.MediaBottomRow.elementName {
        background = `var`("--background")
    }
    FlorisImeUi.MediaBottomRowButton.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
        shape = `var`("--shape")
    }
    FlorisImeUi.MediaBottomRowButton.elementName(selector = SnyggSelector.PRESSED) {
        background = `var`("--surface-variant")
        foreground = rgbaColor(0, 229, 255)
    }

    FlorisImeUi.GlideTrail.elementName {
        foreground = `var`("--primary")
    }

    FlorisImeUi.InlineAutofillChip.elementName {
        background = `var`("--surface")
        foreground = `var`("--on-surface")
    }

    FlorisImeUi.IncognitoModeIndicator.elementName {
        foreground = rgbaColor(255, 255, 255, 0.067f)
    }

    FlorisImeUi.OneHandedPanel.elementName {
        background = rgbaColor(12, 16, 24)
        foreground = rgbaColor(238, 238, 238)
    }

    FlorisImeUi.SubtypePanel.elementName {
        background = rgbaColor(10, 12, 18)
        foreground = `var`("--on-background")
        shape = roundedCornerShape(20.dp, 20.dp, 0.dp, 0.dp)
    }
    FlorisImeUi.SubtypePanelHeader.elementName {
        background = rgbaColor(16, 20, 30)
        foreground = rgbaColor(0, 229, 255)
        fontSize = fontSize(18.sp)
        padding = padding(14.dp)
        textAlign = textAlign(TextAlign.Center)
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
    FlorisImeUi.SubtypePanelListItem.elementName {
        fontSize = fontSize(16.sp)
        padding = padding(16.dp)
    }
    FlorisImeUi.SubtypePanelListItemIconLeading.elementName {
        fontSize = fontSize(24.sp)
        foreground = rgbaColor(0, 229, 255)
        padding = padding(0.dp, 0.dp, 16.dp, 0.dp)
    }
    FlorisImeUi.SubtypePanelListItemText.elementName {
        textMaxLines = textMaxLines(1)
        textOverflow = textOverflow(TextOverflow.Ellipsis)
    }
}
