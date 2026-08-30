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
import io.kotest.matchers.string.shouldContain
import org.json.JSONObject

class DiagnosticSyncManagerTest : FunSpec({

    test("sanitizeRecord filters sensitive credit card patterns and emails") {
        val emailRecord = """{"action":"KEY_TAP","mode":"TYPING","word":"user@domain.com","target":"user@domain.com"}"""

        val sanitizedEmail = DiagnosticSyncManager.sanitizeRecord(emailRecord)
        sanitizedEmail shouldBe """{"action":"KEY_TAP","mode":"TYPING","word":"[FILTERED_EMAIL]","target":"[FILTERED_EMAIL]"}"""

        val cardRecord = """{"action":"KEY_TAP","mode":"TYPING","word":"4532 1234 5678 9012"}"""
        val sanitizedCard = DiagnosticSyncManager.sanitizeRecord(cardRecord)
        sanitizedCard shouldBe """{"action":"KEY_TAP","mode":"TYPING","word":"[FILTERED_SENSITIVE]"}"""

        val normalRecord = """{"action":"KEY_TAP","mode":"TYPING","word":"hello","target":"hello"}"""
        val sanitizedNormal = DiagnosticSyncManager.sanitizeRecord(normalRecord)
        sanitizedNormal shouldContain "hello"
    }

    test("Sprint name constant is defined accurately") {
        DiagnosticSyncManager.SPRINT_NAME shouldContain "7-Day Sprint"
    }
})