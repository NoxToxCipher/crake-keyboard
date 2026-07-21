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

import org.k3lp.model.K3Model
import org.k3lp.model.layer.K3LayerId
import org.k3lp.runtime.K3Content
import org.k3lp.runtime.K3InputMethodState

class FlorisInputMethodState(
    model: K3Model = FlorisEmptyK3Model,
    editor: FlorisEditor = FlorisEditor.Disconnected,
    content: K3Content = K3Content.Empty,
    touchLayerId: K3LayerId = K3LayerId.BASE,
) : K3InputMethodState<FlorisInputMethodState, FlorisEditor>(
    model, editor, content, touchLayerId,
) {
    override fun copy(
        model: K3Model,
        editor: FlorisEditor,
        content: K3Content,
        touchLayerId: K3LayerId,
    ) = FlorisInputMethodState(model, editor, content, touchLayerId)

//    fun copy(
//        model: K3Model = this.model,
//        editor: FlorisEditor = this.editor,
//        content: K3Content = this.content,
//        touchLayerId: K3LayerId = this.touchLayerId,
//    ) = FlorisInputMethodState(model, editor, content, touchLayerId)
}
