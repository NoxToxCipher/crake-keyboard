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

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * When punctuation is typed after punctuation, the keyboard removes the
 * space it had auto-inserted so the marks sit together. That rule used to
 * cover the colon and the semicolon, which made a smiley impossible to type
 * at the end of a sentence: "Hello. " + ":" came out as "Hello.:" and the
 * ")" closed up behind it (field report 2026-09-19).
 */
class PunctuationChainTest : FunSpec({
    test("a smiley after a sentence keeps its space") {
        punctuationChainsOnto('.', ":") shouldBe false
        punctuationChainsOnto('!', ":") shouldBe false
        punctuationChainsOnto('?', ":") shouldBe false
        punctuationChainsOnto('.', ";") shouldBe false
        punctuationChainsOnto('!', ";") shouldBe false
    }

    test("punctuation that really does chain still closes up") {
        punctuationChainsOnto('!', "!") shouldBe true
        punctuationChainsOnto('!', "?") shouldBe true
        punctuationChainsOnto('?', "!") shouldBe true
        punctuationChainsOnto('?', "?") shouldBe true
        punctuationChainsOnto('.', ".") shouldBe true
    }

    test("ordinary text never chains") {
        punctuationChainsOnto('o', ".") shouldBe false
        punctuationChainsOnto('e', "!") shouldBe false
        punctuationChainsOnto(' ', ".") shouldBe false
        punctuationChainsOnto('2', ":") shouldBe false
        punctuationChainsOnto(':', ")") shouldBe false
    }

    test("a colon already written does not pull the next mark onto it") {
        // "Note: " + "." must not become "Note:."
        punctuationChainsOnto(':', ".") shouldBe false
        punctuationChainsOnto(';', "!") shouldBe false
    }

    test("only single characters are considered") {
        punctuationChainsOnto('.', "..") shouldBe false
        punctuationChainsOnto('.', "") shouldBe false
    }
})
