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

package dev.patrickgold.florisboard.ime.nlp

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FleetTypoCorrectionsTest : FunSpec({
    test("fleet typo correction map correctly resolves common slips") {
        val fleetTypos = mapOf(
            "toi" to "you",
            "ckrdsct" to "correct",
            "iodated" to "updated",
            "phr" to "put",
            "fizdx" to "fixed",
            "aure" to "sure",
            "ghe" to "the",
            "becahsd" to "because",
            "ifs" to "it's",
            "adn" to "and",
            "teh" to "the",
            "taht" to "that",
            "waht" to "what",
            "thsi" to "this",
            "thier" to "their",
            "widt" to "with",
            "rhjs" to "this",
            "jat" to "that",
            "dobe" to "done",
            "thks" to "this",
            "thid" to "this",
            "whag" to "what",
            "hwy" to "why",
            "actuly" to "actually",
            "actully" to "actually",
            "trigh" to "right",
            "tought" to "thought",
            "thoght" to "thought",
            "whcih" to "which",
            "becasue" to "because",
            "definitly" to "definitely",
            "definately" to "definitely",
            "seperate" to "separate",
            "occured" to "occurred",
            "untill" to "until",
            "realy" to "really",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "didnt" to "didn't",
            "isnt" to "isn't",
            "arent" to "aren't",
            "couldnt" to "couldn't",
            "shouldnt" to "shouldn't",
            "wouldnt" to "wouldn't",
        )

        for ((typo, expected) in fleetTypos) {
            fleetTypos[typo] shouldBe expected
        }
    }
})
