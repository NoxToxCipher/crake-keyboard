/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.composing

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.florisboard.libnative.FlorisNative

/**
 * Kana dakuten/handakuten/small-kana toggling. The transform tables live in
 * the native core (floris-core composing::kana_unicode_actions); without the
 * native lib marks are appended untransformed.
 */
@Serializable
@SerialName("kana-unicode")
class KanaUnicode : Composer {
    override val id: String = "kana-unicode"
    override val label: String = "Kana Unicode"
    override val toRead: Int = 1

    override fun getActions(precedingText: String, toInsert: String): Pair<Int, String> {
        return FlorisNative.composerAction(id, precedingText, toInsert) ?: (0 to toInsert)
    }
}
