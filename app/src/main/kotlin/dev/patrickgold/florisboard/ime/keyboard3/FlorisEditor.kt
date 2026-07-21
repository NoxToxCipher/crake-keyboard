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

import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import org.k3lp.runtime.K3Editor
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

class FlorisEditor(
    val ic: WeakReference<InputConnection>,
    val info: FlorisEditorInfo,
) : K3Editor {
    override fun getSurroundingText(charsBefore: Int, charsAfter: Int): K3SurroundingText {
        val ic = ic.get() ?: return K3SurroundingText.Zero
        return K3SurroundingText(
            textBefore = ic.getTextBeforeCursor(charsBefore, 0)?.toString() ?: "",
            textSelected = ic.getSelectedText(0)?.toString() ?: "",
            textAfter = ic.getTextAfterCursor(charsAfter, 0)?.toString() ?: "",
        )
    }

    override fun replaceText(
        range: IntRange,
        text: String,
        newSelection: K3TextRange,
        newComposition: K3TextRange?,
    ) {
        val ic = ic.get() ?: return
        ic.beginBatchEdit()
        ic.finishComposingText()
        ic.setSelection(range.first, range.last + 1)
        ic.commitText(text, 1)
        ic.setSelection(newSelection.start, newSelection.end)
        setComposition(newComposition)
        ic.endBatchEdit()
    }

    override fun setComposition(newComposition: K3TextRange?) {
        val ic = ic.get() ?: return
        if (newComposition == null) {
            ic.finishComposingText()
        } else {
            ic.setComposingRegion(newComposition.start, newComposition.end)
        }
    }

    companion object {
        val Disconnected = FlorisEditor(
            ic = WeakReference(null),
            info = FlorisEditorInfo.Unspecified,
        )
    }
}
