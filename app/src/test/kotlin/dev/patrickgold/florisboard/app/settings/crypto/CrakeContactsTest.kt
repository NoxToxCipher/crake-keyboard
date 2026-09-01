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

package dev.patrickgold.florisboard.app.settings.crypto

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class CrakeContactsTest : FunSpec({
    val alice = CrakeContact("Alice", "crake-pk1-aaaa")
    val bob = CrakeContact("Bob", "crake-pk1-bbbb")

    test("serialize then parse round-trips a list") {
        val list = listOf(alice, bob)
        CrakeContacts.parse(CrakeContacts.serialize(list)) shouldBe list
    }

    test("empty and malformed lines are skipped, not crashed on") {
        val raw = "\n  \ncrake-pk1-aaaa|Alice\nnopipe\n|nolabel\ncrake-pk1-cccc|"
        CrakeContacts.parse(raw) shouldBe listOf(alice)
    }

    test("upsert replaces the entry with the same key, not appends") {
        val list = listOf(alice, bob)
        val updated = CrakeContacts.upsert(list, CrakeContact("Alice (work)", "crake-pk1-aaaa"))
        updated.count { it.key == "crake-pk1-aaaa" } shouldBe 1
        updated.first { it.key == "crake-pk1-aaaa" }.label shouldBe "Alice (work)"
    }

    test("upsert adds a genuinely new contact") {
        CrakeContacts.upsert(listOf(alice), bob).size shouldBe 2
    }

    test("remove drops only the matching key") {
        CrakeContacts.remove(listOf(alice, bob), "crake-pk1-aaaa") shouldBe listOf(bob)
    }

    test("a label with pipe or newline cannot break the format") {
        val nasty = CrakeContact("Ev|il\nName", "crake-pk1-eeee")
        val reparsed = CrakeContacts.parse(CrakeContacts.serialize(listOf(nasty)))
        reparsed.size shouldBe 1
        reparsed[0].key shouldBe "crake-pk1-eeee"
        reparsed[0].label.contains('|') shouldBe false
        reparsed[0].label.contains('\n') shouldBe false
    }
})
