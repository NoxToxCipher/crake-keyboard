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

import android.icu.text.BreakIterator
import android.view.KeyEvent
import android.view.inputmethod.InputConnection
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.editor.FlorisEditorInfo
import dev.patrickgold.florisboard.ime.editor.InputAttributes
import dev.patrickgold.florisboard.ime.input.InputShiftState
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.media.emoji.EmojiSuggestionType
import dev.patrickgold.florisboard.ime.nlp.BreakIterators
import dev.patrickgold.florisboard.ime.text.key.KeyVariation
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.runBlocking
import org.florisboard.lib.kotlin.collectIn
import org.k3lp.model.K3Model
import org.k3lp.model.key.K3Key
import org.k3lp.model.layer.K3LayerId
import org.k3lp.runtime.K3InputMethod
import org.k3lp.runtime.K3SurroundingText
import org.k3lp.runtime.K3TextRange
import java.lang.ref.WeakReference
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class ImeController : K3InputMethod<ImeState, FlorisEditor, ImeController.UpdateImeStateScope>(
    initialState = ImeState(),
) {
    private val prefs by FlorisPreferenceStore
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val breakIterators = BreakIterators()

    init {
        combine(
            prefs.devtools.enabled.asFlow(),
            prefs.devtools.showDragAndDropHelpers.asFlow(),
        ) { devtoolsEnabled, showDragAndDropHelpers ->
            devtoolsEnabled && showDragAndDropHelpers
        }.collectIn(scope) { showDragAndDropHelpers ->
            updateState {
                state = state.copy(
                    flags = state.flags
                        .withDebugShowDragAndDropHelpers(showDragAndDropHelpers),
                )
            }
        }
    }

    fun sendTouchKeyDown(key: K3Key) {
        //
    }

    fun sendTouchKeyUp(key: K3Key) {
        //
    }

    fun sendTouchKeyCancel(key: K3Key) {
        //
    }

    fun sendTouchKeyRepeat(key: K3Key) {
        //
    }

    fun onHardwareKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // TODO
        return false
    }

    fun onHardwareKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        // TODO
        return false
    }

    fun snapshotState(): ImeState = activeState.value

    // TODO evaluate if we can move to a clean coroutine-based approach in FlorisBoard
    //  for now this helper can be used to update the state from non-suspending contexts
    inline fun updateStateBlocking(crossinline function: UpdateImeStateScope.() -> Unit) {
        runBlocking {
            updateState(function)
        }
    }

    override fun updateStateScopeOf(state: ImeState): UpdateImeStateScope {
        return UpdateImeStateScope(state)
    }

    inner class UpdateImeStateScope(
        state: ImeState,
    ) : UpdateStateScope<ImeState, FlorisEditor>(state) {
        fun handleStartInputView(
            ic: WeakReference<InputConnection>,
            info: FlorisEditorInfo,
        ) {
            val keyboardMode: KeyboardMode // TODO this should be merged with touchLayerId (& entry mode or smth)
            val keyVariation: KeyVariation
            when (info.inputAttributes.type) {
                InputAttributes.Type.NUMBER -> {
                    keyVariation = KeyVariation.NORMAL
                    keyboardMode = KeyboardMode.NUMERIC
                }
                InputAttributes.Type.PHONE -> {
                    keyVariation = KeyVariation.NORMAL
                    keyboardMode = KeyboardMode.PHONE
                }
                InputAttributes.Type.TEXT -> {
                    keyVariation = when (info.inputAttributes.variation) {
                        InputAttributes.Variation.EMAIL_ADDRESS,
                        InputAttributes.Variation.WEB_EMAIL_ADDRESS,
                            -> {
                            KeyVariation.EMAIL_ADDRESS
                        }
                        InputAttributes.Variation.PASSWORD,
                        InputAttributes.Variation.VISIBLE_PASSWORD,
                        InputAttributes.Variation.WEB_PASSWORD,
                            -> {
                            KeyVariation.PASSWORD
                        }
                        InputAttributes.Variation.URI -> {
                            KeyVariation.URI
                        }
                        else -> {
                            KeyVariation.NORMAL
                        }
                    }
                    keyboardMode = KeyboardMode.CHARACTERS
                }
                else -> {
                    keyVariation = KeyVariation.NORMAL
                    keyboardMode = KeyboardMode.CHARACTERS
                }
            }
            val initialSelection = info.initialSelection2
            val initialSurrounding = K3SurroundingText(
                textBefore = info.getInitialTextBeforeCursor(20)?.toString() ?: "",
                textSelected = info.getInitialSelectedText()?.toString() ?: "",
                textAfter = info.getInitialTextAfterCursor(20)?.toString() ?: "",
            )

            state = state.copy(
                editor = FlorisEditor(ic, info),
                flags = state.flags
                    .withKeyboardMode(keyboardMode)
                    .withKeyVariation(keyVariation)
                    .withImeUiMode(
                        if (state.flags.imeUiMode != ImeUiMode.CLIPBOARD || prefs.clipboard.historyHideOnNextTextField.get()) {
                            ImeUiMode.TEXT
                        } else {
                            state.flags.imeUiMode
                        }
                    )
                    .withActionsOverflowVisible(false)
                    .withActionsEditorVisible(false)
                    .withInputShiftState(
                        if (prefs.correction.rememberCapsLockState.get()) {
                            state.flags.inputShiftState
                        } else {
                            InputShiftState.UNSHIFTED
                        }
                    )
                    .withComposingEnabled(
                        when (keyboardMode) {
                            KeyboardMode.NUMERIC,
                            KeyboardMode.PHONE,
                                -> false
                            else -> keyVariation != KeyVariation.PASSWORD &&
                                prefs.suggestion.enabled.get()// &&
                            //!instance.inputAttributes.flagTextAutoComplete &&
                            //!instance.inputAttributes.flagTextNoSuggestions
                        }
                    )
                    .withIncognitoMode(
                        when (prefs.suggestion.incognitoMode.get()) {
                            IncognitoMode.FORCE_OFF -> false
                            IncognitoMode.FORCE_ON -> true
                            IncognitoMode.DYNAMIC_ON_OFF -> {
                                info.imeOptions.flagNoPersonalizedLearning ||
                                    prefs.suggestion.forceIncognitoModeFromDynamic.get()
                            }
                        }
                    )
            )
            reset(initialSelection, initialSurrounding)
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
            if (selection.isNotCollapsed()) {
                return null
            }
            // TODO rework how we get the primary locale
            val locale = FlorisLocale.fromTag(model.locales.getOrElse(0) { "" })
            return breakIterators.word(locale) {
                it.setText(surroundingText.textBefore)
                val end = it.last()
                val isWord = it.ruleStatus != BreakIterator.WORD_NONE
                if (isWord) {
                    val start = it.previous().let { pos ->
                        // Include Emoji indicator in local composing. This is required so that emoji suggestion indicator
                        // can be detected in the composing text.
                        (pos - 1).takeIf { updatedPos ->
                            surroundingText.textBefore.getOrNull(updatedPos) == EmojiSuggestionType.LEADING_COLON.prefix.first()
                        } ?: pos
                    }
                    val offset = (selection.min - surroundingText.textBefore.length).coerceAtLeast(0)
                    K3TextRange(start + offset, end + offset)
                } else {
                    K3TextRange.Zero
                }
            }
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
