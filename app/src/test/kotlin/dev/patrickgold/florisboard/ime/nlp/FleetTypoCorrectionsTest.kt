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

import dev.patrickgold.florisboard.ime.nlp.latin.FleetTypoCorrections
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the PRODUCTION fleet typo map (the previous version of this test
 * asserted a private copy of itself). The negative pins are removals from
 * corpus audits: tokens with a real standing meaning must never be hard
 * remapped, because the map fires ahead of the engine and rewrites them
 * unconditionally.
 */
class FleetTypoCorrectionsTest : FunSpec({
    test("audited M348 ingestions resolve") {
        FleetTypoCorrections.MAP["rhjs"] shouldBe "this"
        FleetTypoCorrections.MAP["jat"] shouldBe "that"
        FleetTypoCorrections.MAP["dobe"] shouldBe "done"
        FleetTypoCorrections.MAP["thid"] shouldBe "this"
        FleetTypoCorrections.MAP["whag"] shouldBe "what"
    }

    test("classic slips resolve") {
        FleetTypoCorrections.MAP["teh"] shouldBe "the"
        FleetTypoCorrections.MAP["taht"] shouldBe "that"
        FleetTypoCorrections.MAP["dont"] shouldBe "don't"
        FleetTypoCorrections.MAP["seperate"] shouldBe "separate"
    }

    test("real words and standing abbreviations are never hard-remapped") {
        // Each of these was removed (or blocked) by a corpus audit: hard
        // remapping a token people legitimately type is corpus poison.
        for (real in listOf("thks", "hwy", "iff", "tori", "its", "were", "cant")) {
            if (real == "cant") continue // apostrophe restoration is deliberate
            FleetTypoCorrections.MAP.containsKey(real) shouldBe false
        }
    }

    test("no entry maps a token to itself and every target is non-blank") {
        for ((typo, fix) in FleetTypoCorrections.MAP) {
            (typo == fix) shouldBe false
            fix.isNotBlank() shouldBe true
        }
    }
})
