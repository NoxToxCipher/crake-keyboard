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

import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import org.k3lp.model.K3Model
import org.k3lp.runtime.K3InputMethod
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange

class FlorisInputMethod(initialModel: K3Model) : K3InputMethod(initialModel) {
    fun startInputView(
        editorConnection: FlorisEditorConnection,
        editorInfo: FlorisEditorInfo,
    ) {
        val initialSelection = editorInfo.initialSelection2
        val initialSurrounding = K3SurroundingText(
            textBefore = editorInfo.getInitialTextBeforeCursor(20)?.toString() ?: "",
            textSelected = editorInfo.getInitialSelectedText()?.toString() ?: "",
            textAfter = editorInfo.getInitialTextAfterCursor(20)?.toString() ?: "",
        )
        connect(editorConnection, initialSelection, initialSurrounding)
    }

    override fun notifySelectionUpdated(newSelection: K3TextRange) {
        super.notifySelectionUpdated(newSelection)
    }

    fun finishInputView() {
        disconnect()
    }
}
