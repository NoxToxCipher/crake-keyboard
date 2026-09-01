/*
 * Copyright (C) 2026 The Crake Contributors
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

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RepeatingPunctuationTest : FunSpec({
    test("double exclamation marks correctly collapse space and attach to preceding word") {
        val punctChars = listOf('!', '?', '.', ';', ':')
        
        fun isRepeating(char: String, textBefore: String): Pair<Boolean, Boolean> {
            val isPunctuationChar = char.length == 1 && punctChars.contains(char[0])
            val hasTrailingSpace = textBefore.endsWith(' ')
            val textBeforeTrimmed = if (hasTrailingSpace) textBefore.trimEnd() else textBefore
            val isRepeatingPunctuation = isPunctuationChar && textBeforeTrimmed.isNotEmpty() &&
                punctChars.contains(textBeforeTrimmed.last())
            return Pair(isRepeatingPunctuation, hasTrailingSpace)
        }

        // After typing "word", user types "!" -> text becomes "word! " (auto-space)
        val (rep1, delSpace1) = isRepeating("!", "word! ")
        rep1 shouldBe true
        delSpace1 shouldBe true

        // After deleting space and adding "!", text becomes "word!! "
        val (rep2, delSpace2) = isRepeating("!", "word!! ")
        rep2 shouldBe true
        delSpace2 shouldBe true

        // Without auto-space: text is "word!"
        val (rep3, delSpace3) = isRepeating("!", "word!")
        rep3 shouldBe true
        delSpace3 shouldBe false

        // Interrobang: text is "word! ", next char is "?"
        val (rep4, delSpace4) = isRepeating("?", "word! ")
        rep4 shouldBe true
        delSpace4 shouldBe true
    }
})
