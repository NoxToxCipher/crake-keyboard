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

package dev.patrickgold.florisboard.ime.keyboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class EasterEggsTest : FunSpec({

    test("CSV parsing and encoding maintains clean sets of egg ids") {
        val raw = "eclectus_flight, bawen_cat, soccer_roll"
        val parsed = EasterEggs.parseIds(raw)
        parsed shouldHaveSize 3
        parsed shouldContain "eclectus_flight"
        parsed shouldContain "bawen_cat"
        parsed shouldContain "soccer_roll"

        val encoded = EasterEggs.encodeIds(parsed)
        encoded shouldBe "bawen_cat,eclectus_flight,soccer_roll"

        val withNew = EasterEggs.withId(encoded, "space_rain")
        EasterEggs.parseIds(withNew) shouldContain "space_rain"

        val without = EasterEggs.withoutId(withNew, "bawen_cat")
        EasterEggs.parseIds(without).contains("bawen_cat") shouldBe false
    }

    test("matchTriggerPhrase correctly resolves known egg trigger words") {
        EasterEggs.matchTriggerPhrase("luna").shouldNotBeNull().id shouldBe EasterEgg.LUNA_CRASH.id
        EasterEggs.matchTriggerPhrase("bawen").shouldNotBeNull().id shouldBe EasterEgg.BAWEN_CAT.id
        EasterEggs.matchTriggerPhrase("blackberry").shouldNotBeNull().id shouldBe EasterEgg.BLACKBERRY.id
        EasterEggs.matchTriggerPhrase("master chief").shouldNotBeNull().id shouldBe EasterEgg.MASTER_CHIEF_RUN.id
        EasterEggs.matchTriggerPhrase("sun conure").shouldNotBeNull().id shouldBe EasterEgg.SUN_CONURE_FLIGHT.id
        EasterEggs.matchTriggerPhrase("boba").shouldNotBeNull().id shouldBe EasterEgg.LUCIA_BOBA.id
        EasterEggs.matchTriggerPhrase("god of thunder").shouldNotBeNull().id shouldBe EasterEgg.THOR.id
        EasterEggs.matchTriggerPhrase("train").shouldNotBeNull().id shouldBe EasterEgg.STEAM_TRAIN.id
        EasterEggs.matchTriggerPhrase("noble train").shouldNotBeNull().id shouldBe EasterEgg.NOBLE_TRAIN.id
        EasterEggs.matchTriggerPhrase("battery").shouldNotBeNull().id shouldBe EasterEgg.BATTERY_OVERCHARGE.id
        EasterEggs.matchTriggerPhrase("sad").shouldNotBeNull().id shouldBe EasterEgg.SERENITY_GARDEN.id
        EasterEggs.matchTriggerPhrase("stress").shouldNotBeNull().id shouldBe EasterEgg.SERENITY_GARDEN.id
        EasterEggs.matchTriggerPhrase("unknown nonsense 12345").shouldBeNull()
        EasterEggs.matchTriggerPhrase("").shouldBeNull()
    }

    test("isRecorded and recordedEggs filter only identified eggs") {
        val recordedCsv = "luna_crash,bawen_cat"
        EasterEggs.isRecorded(recordedCsv, EasterEgg.LUNA_CRASH) shouldBe true
        EasterEggs.isRecorded(recordedCsv, EasterEgg.BAWEN_CAT) shouldBe true
        EasterEggs.isRecorded(recordedCsv, EasterEgg.THOR) shouldBe false

        val list = EasterEggs.recordedEggs(recordedCsv)
        list shouldHaveSize 2
        list.map { it.id } shouldContain EasterEgg.LUNA_CRASH.id
        list.map { it.id } shouldContain EasterEgg.BAWEN_CAT.id
    }

    test("EasterEgg enum contains exactly 36 pure word-triggered Easter Eggs") {
        EasterEgg.entries shouldHaveSize 36
        EasterEgg.entries.any { it.id == "power_surge" } shouldBe false
    }
})