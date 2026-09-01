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

package dev.patrickgold.florisboard.app.island

import androidx.compose.ui.graphics.Color
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DynamicIslandManagerTest : FunSpec({

    beforeTest {
        DynamicIslandManager.dismiss()
    }

    test("posting notification updates currentNotification state") {
        val notif = IslandNotification(
            id = "test_notif_1",
            title = "Test Update",
            subtitle = "Test subtitle description",
            emoji = "🚀",
            accentColor = Color(0xFF00E5FF),
            durationMs = 0L,
            priority = IslandPriority.NORMAL,
        )

        DynamicIslandManager.post(notif)
        val current = DynamicIslandManager.currentNotification.value
        current shouldNotBe null
        current?.id shouldBe "test_notif_1"
        current?.title shouldBe "Test Update"
        current?.emoji shouldBe "🚀"
    }

    test("higher priority notification preempts lower priority") {
        val lowNotif = IslandNotification(
            id = "low_1",
            title = "Low Priority",
            priority = IslandPriority.LOW,
            durationMs = 0L,
        )
        val urgentNotif = IslandNotification(
            id = "urgent_1",
            title = "Urgent Priority",
            priority = IslandPriority.URGENT,
            durationMs = 0L,
        )

        DynamicIslandManager.post(lowNotif)
        DynamicIslandManager.currentNotification.value?.id shouldBe "low_1"

        DynamicIslandManager.post(urgentNotif)
        DynamicIslandManager.currentNotification.value?.id shouldBe "urgent_1"
    }

    test("lower priority notification does not preempt active higher priority") {
        val highNotif = IslandNotification(
            id = "high_1",
            title = "High Priority",
            priority = IslandPriority.HIGH,
            durationMs = 0L,
        )
        val lowNotif = IslandNotification(
            id = "low_2",
            title = "Low Priority",
            priority = IslandPriority.LOW,
            durationMs = 0L,
        )

        DynamicIslandManager.post(highNotif)
        DynamicIslandManager.currentNotification.value?.id shouldBe "high_1"

        DynamicIslandManager.post(lowNotif)
        // High priority remains active
        DynamicIslandManager.currentNotification.value?.id shouldBe "high_1"
    }

    test("progress update updates live state and subtitle") {
        val downloadNotif = IslandNotification(
            id = "dl_1",
            title = "Downloading Package",
            subtitle = "0%",
            progress = 0.0f,
            durationMs = 0L,
            priority = IslandPriority.URGENT,
        )

        DynamicIslandManager.post(downloadNotif)
        DynamicIslandManager.updateProgress("dl_1", 0.55f, "55% completed")

        val current = DynamicIslandManager.currentNotification.value
        current shouldNotBe null
        current?.progress shouldBe 0.55f
        current?.subtitle shouldBe "55% completed"
    }

    test("expand and collapse toggle state correctly") {
        val notif = IslandNotification(
            id = "test_toggle",
            title = "Expand Test",
            durationMs = 0L,
        )
        DynamicIslandManager.post(notif)
        DynamicIslandManager.isExpanded.value shouldBe false

        DynamicIslandManager.setExpanded(true)
        DynamicIslandManager.isExpanded.value shouldBe true

        DynamicIslandManager.toggleExpanded()
        DynamicIslandManager.isExpanded.value shouldBe false
    }

    test("dismiss clears active notification") {
        val notif = IslandNotification(
            id = "test_dismiss",
            title = "Dismiss Test",
            durationMs = 0L,
        )
        DynamicIslandManager.post(notif)
        DynamicIslandManager.currentNotification.value shouldNotBe null

        DynamicIslandManager.dismiss("test_dismiss")
        DynamicIslandManager.currentNotification.value shouldBe null
    }
})
