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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.k3lp.model.K3Model
import org.k3lp.model.key.K3Key
import org.k3lp.model.layer.K3LayerId
import org.k3lp.runtime.K3KeystrokeEngine

class FlorisKeystrokeEngine(model: K3Model) : K3KeystrokeEngine(model) {
    init {
        onProcessNamespacedDescriptor("floris") { descriptor ->
            when (descriptor.type) {
                "action" -> when (descriptor.name) {
                    "undo" -> TODO()
                    "redo" -> TODO()
                    else -> false
                }
                else -> false
            }
        }
    }

    val touchLayerId: StateFlow<K3LayerId>
        field = MutableStateFlow(K3LayerId.BASE)

    override fun onKeyPress(key: K3Key): Boolean {
        val layerId = key.layerId
        if (layerId != null) {
            touchLayerId.value = layerId
            return true
        }
        return super.onKeyPress(key)
    }
}
