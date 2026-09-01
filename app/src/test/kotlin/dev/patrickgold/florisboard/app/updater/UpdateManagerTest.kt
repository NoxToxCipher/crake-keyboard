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

package dev.patrickgold.florisboard.app.updater

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UpdateManagerTest : FunSpec({

    test("UpdateManager.parseMilestoneNumber extracts milestone integer across varied tag conventions") {
        UpdateManager.parseMilestoneNumber("Milestone 278") shouldBe 278
        UpdateManager.parseMilestoneNumber("milestone_278") shouldBe 278
        UpdateManager.parseMilestoneNumber("milestone-278") shouldBe 278
        UpdateManager.parseMilestoneNumber("v278") shouldBe 278
        UpdateManager.parseMilestoneNumber("m278") shouldBe 278
        UpdateManager.parseMilestoneNumber("v0.4.0-alpha01-m278") shouldBe 278
        UpdateManager.parseMilestoneNumber("CrakeKeyboard_Milestone_279.apk") shouldBe 279
    }

    test("Milestone comparisons accurately trigger update availability") {
        val currentMilestone = UpdateManager.CURRENT_MILESTONE
        currentMilestone shouldBe 369
        val futureReleaseMilestone = currentMilestone + 1
        val pastReleaseMilestone = currentMilestone - 1

        (futureReleaseMilestone > currentMilestone) shouldBe true
        (pastReleaseMilestone > currentMilestone) shouldBe false
        (currentMilestone > currentMilestone) shouldBe false
    }

    test("UpdateManager.getCumulativeChangelog compiles multi-version retrospective changelog") {
        val changelog = UpdateManager.getCumulativeChangelog(fromMilestone = 299, toMilestone = 303)
        changelog.contains("Milestone 303") shouldBe true
        changelog.contains("Milestone 302") shouldBe true
        changelog.contains("Milestone 301") shouldBe true
        changelog.contains("Milestone 300") shouldBe true
    }

    test("remote milestone highlights override default fallback") {
        UpdateManager.remoteMilestoneHighlights[999] = "Custom Dynamic Cloud Highlight"
        UpdateManager.getMilestoneHighlights(999) shouldBe "Custom Dynamic Cloud Highlight"
    }

    test("Privacy Guard: Personal tester names are never mentioned in update highlights or changelogs") {
        // Direct method sanitizeChangelog test
        val testInput = "Ingested Charlton's typing slips and Charlton feedback."
        val sanitized = UpdateManager.sanitizeChangelog(testInput)
        sanitized.contains("Charlton", ignoreCase = true) shouldBe false
        sanitized shouldBe "Ingested Fleet Tester's typing slips and Fleet Tester feedback."

        // Milestone highlight entries
        for (m in 282..369) {
            val hl = UpdateManager.getMilestoneHighlights(m)
            hl.contains("Charlton", ignoreCase = true) shouldBe false
        }

        // Cumulative changelog
        val cumulative = UpdateManager.getCumulativeChangelog(fromMilestone = 330, toMilestone = 369)
        cumulative.contains("Charlton", ignoreCase = true) shouldBe false
    }
})