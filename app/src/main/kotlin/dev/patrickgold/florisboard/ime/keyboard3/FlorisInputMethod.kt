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
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import kotlinx.coroutines.runBlocking
import org.k3lp.model.K3Model
import org.k3lp.model.key.K3Key
import org.k3lp.model.layer.K3LayerId
import org.k3lp.runtime.K3InputMethod
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference

class FlorisInputMethod : K3InputMethod<FlorisInputMethodState, FlorisEditor, FlorisInputMethod.UpdateStateScope>(
    initialState = FlorisInputMethodState(),
) {
    private val prefs by FlorisPreferenceStore

    // TODO evaluate if we can move to a clean coroutine-based approach in FlorisBoard
    //  for now this helper can be used to update the state from non-suspending contexts
    inline fun updateStateBlocking(crossinline function: UpdateStateScope.() -> Unit) {
        runBlocking {
            updateState(function)
        }
    }

    fun onTouchKeyDown(key: K3Key) {
        //
    }

    fun onTouchKeyUp(key: K3Key) {
        //
    }

    fun onTouchKeyCancel(key: K3Key) {
        //
    }

    fun onTouchKeyRepeat(key: K3Key) {
        //
    }

    override fun updateStateScopeOf(state: FlorisInputMethodState): UpdateStateScope {
        return UpdateStateScope(state)
    }

    class UpdateStateScope(
        state: FlorisInputMethodState,
    ) : K3InputMethod.UpdateStateScope<FlorisInputMethodState, FlorisEditor>(state) {
        fun handleStartInputView(
            ic: InputConnection,
            info: FlorisEditorInfo,
        ) {
            val initialSelection = info.initialSelection2
            val initialSurrounding = K3SurroundingText(
                textBefore = info.getInitialTextBeforeCursor(20)?.toString() ?: "",
                textSelected = info.getInitialSelectedText()?.toString() ?: "",
                textAfter = info.getInitialTextAfterCursor(20)?.toString() ?: "",
            )
            reset(initialSelection, initialSurrounding)
            state = state.copy(editor = FlorisEditor(WeakReference(ic), info))
        }

        fun handleFinishInputView() {
            reset()
            state = state.copy(editor = FlorisEditor.Disconnected)
        }

        override fun evaluateCompositionOf(
            model: K3Model,
            selection: K3TextRange,
            surroundingText: K3SurroundingText
        ): K3TextRange? {
            // TODO needs to use proper word iterator based on language etc.
            if (selection.isNotCollapsed()) {
                return null
            }
            // redneck word splitter, just for testing :)
            val len = surroundingText.textBefore.split(" ").last().length
            return K3TextRange(selection.min - len, selection.min)
        }

        private fun K3Key.isShiftKey(): Boolean {
            return when (state.touchLayerId) {
                LAYER_BASE -> layerId == LAYER_SHIFT || layerId == LAYER_CAPS
                LAYER_SHIFT -> layerId == LAYER_BASE || layerId == LAYER_CAPS
                LAYER_CAPS -> layerId == LAYER_BASE || layerId == LAYER_SHIFT
                else -> false
            }
        }
    }

    companion object {
        private val LAYER_BASE = K3LayerId.BASE
        private val LAYER_SHIFT = K3LayerId("shift")
        private val LAYER_CAPS = K3LayerId("caps")
    }
}
