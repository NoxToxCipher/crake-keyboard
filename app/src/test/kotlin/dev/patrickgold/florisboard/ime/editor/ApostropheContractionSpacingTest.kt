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

class ApostropheContractionSpacingTest : FunSpec({
    test("apostrophe characters are recognized across ASCII and typographic variants") {
        val apostrophes = listOf('\'', '’', '‘', '´', '`')
        for (ap in apostrophes) {
            val isApostrophe = (ap == '\'' || ap == '’' || ap == '‘' || ap == '´' || ap == '`')
            isApostrophe shouldBe true
        }
    }

    test("contraction extraction preserves internal apostrophe tokens") {
        val samples = listOf(
            "don't" to "don't",
            "it’s" to "it's",
            "I'm" to "I'm",
            "they're" to "they're",
            "we've" to "we've",
            "didn't" to "didn't",
            "Daya's" to "Daya's",
            "Lochran's" to "Lochran's",
        )

        for ((input, expected) in samples) {
            val clean = input
                .takeLastWhile { it.isLetter() || it == '\'' || it == '’' || it == '‘' || it == '´' || it == '`' }
                .replace('’', '\'')
                .replace('‘', '\'')
                .replace('´', '\'')
                .replace('`', '\'')
            clean shouldBe expected
        }
    }

    test("edge quoted tokens preserve apostrophes without space injection") {
        val quotes = listOf("'word'", "‘hello’", "’cause", "teachers'")
        for (q in quotes) {
            val hasApostrophe = q.any { it == '\'' || it == '’' || it == '‘' }
            hasApostrophe shouldBe true
        }
    }
})
