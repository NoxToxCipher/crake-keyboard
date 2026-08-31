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

class FlightRecorderTest : FunSpec({

    test("FlightRecorder.Record serializes accurately to structured JSON") {
        val record = FlightRecorderManager.Record(
            timestamp = 1756500000000L,
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.AUTOCORRECTION,
            rawInput = "teh",
            correctedTo = "the",
            candidates = listOf("the", "they", "them"),
            contextBefore = "this is ",
            packageName = "com.google.android.apps.messaging",
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"mode\":\"TYPING\""
        jsonStr shouldContain "\"action\":\"AUTOCORRECTION\""
        jsonStr shouldContain "\"rawInput\":\"teh\""
        jsonStr shouldContain "\"correctedTo\":\"the\""
        jsonStr shouldContain "\"candidates\":[\"the\",\"they\",\"them\"]"
        jsonStr shouldContain "\"contextBefore\":\"this is \""
        jsonStr shouldContain "\"packageName\":\"com.google.android.apps.messaging\""
    }

    test("FlightRecorder logs glide gesture metrics accurately") {
        val record = FlightRecorderManager.Record(
            timestamp = 1756500000000L,
            mode = FlightRecorderManager.InputMode.GLIDING,
            action = FlightRecorderManager.ActionType.GLIDE_STROKE,
            rawInput = "keyboard",
            correctedTo = "keyboard",
            candidates = listOf("keyboard", "keyboards", "keycard"),
            gestureMetrics = FlightRecorderManager.GestureMetrics(
                pointCount = 42,
                durationMs = 380L,
                distanceDp = 185.5f,
            ),
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"mode\":\"GLIDING\""
        jsonStr shouldContain "\"action\":\"GLIDE_STROKE\""
        jsonStr shouldContain "\"gestureMetrics\":{\"pointCount\":42,\"durationMs\":380,\"distanceDp\":185.5}"
    }

    test("Missed correction is flagged when typo is uncorrected with candidates") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.MISSED_CORRECTION,
            rawInput = "dorrwct",
            correctedTo = "dorrwct",
            candidates = listOf("correct", "direct", "directs"),
            isTypo = true,
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"action\":\"MISSED_CORRECTION\""
        jsonStr shouldContain "\"isTypo\":true"
        jsonStr shouldContain "\"rawInput\":\"dorrwct\""
        jsonStr shouldContain "\"candidates\":[\"correct\",\"direct\",\"directs\"]"
    }

    test("Manual revert record maps backspaced typo to intended word") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.MANUAL_REVERT,
            rawInput = "dorrwct",
            intendedWord = "correct",
            candidates = listOf("correct", "direct"),
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"action\":\"MANUAL_REVERT\""
        jsonStr shouldContain "\"rawInput\":\"dorrwct\""
        jsonStr shouldContain "\"intendedWord\":\"correct\""
    }

    test("FlightRecorder serializes kinematic touch geometry and dwell time metrics") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.KEY_TAP,
            rawInput = "c",
            spatialOffset = "-3.5,12.2",
            touchMajor = 18.5f,
            touchMinor = 14.0f,
            pressure = 0.85f,
            dwellTimeMs = 45L,
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"rawInput\":\"c\""
        jsonStr shouldContain "\"spatialOffset\":\"-3.5,12.2\""
        jsonStr shouldContain "\"touchMajor\":18.5"
        jsonStr shouldContain "\"touchMinor\":14.0"
        jsonStr shouldContain "\"pressure\":0.85"
        jsonStr shouldContain "\"dwellTimeMs\":45"
    }

    test("FlightRecorder serializes autocorrectUndo and suggestionSlot metrics") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.SUGGESTION_PICKED,
            rawInput = "teh",
            correctedTo = "the",
            suggestionSlot = 0,
            trieSearchDurationUs = 240L,
            autocorrectUndo = true,
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"action\":\"SUGGESTION_PICKED\""
        jsonStr shouldContain "\"suggestionSlot\":0"
        jsonStr shouldContain "\"trieSearchDurationUs\":240"
        jsonStr shouldContain "\"autocorrectUndo\":true"
    }

    test("FlightRecorder serializes biometric touchOrientation and interKeyFlightTimeMs") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.KEY_TAP,
            rawInput = "y",
            spatialOffset = "4.2,-1.8",
            touchMajor = 22.0f,
            touchMinor = 15.5f,
            touchOrientation = 0.45f,
            dwellTimeMs = 52L,
            interKeyFlightTimeMs = 135L,
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"rawInput\":\"y\""
        jsonStr shouldContain "\"spatialOffset\":\"4.2,-1.8\""
        jsonStr shouldContain "\"touchOrientation\":0.45"
        jsonStr shouldContain "\"flightTimeMs\":135"
        jsonStr shouldContain "\"dwellTimeMs\":52"
    }

    test("FlightRecorder serializes Smartbar perception metrics and flick predictions") {
        val record = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.SUGGESTION_PICKED,
            rawInput = "Charl",
            correctedTo = "Charlton",
            suggestionSlot = 1,
            stripDwellMs = 185L,
            totalCandidates = 3,
            isFlickPrediction = false,
        )

        val jsonStr = record.toJsonString()
        jsonStr shouldContain "\"action\":\"SUGGESTION_PICKED\""
        jsonStr shouldContain "\"suggestionSlot\":1"
        jsonStr shouldContain "\"stripDwellMs\":185"
        jsonStr shouldContain "\"totalCandidates\":3"

        val flickRecord = FlightRecorderManager.Record(
            mode = FlightRecorderManager.InputMode.TYPING,
            action = FlightRecorderManager.ActionType.SUGGESTION_PICKED,
            correctedTo = "the",
            isFlickPrediction = true,
        )
        val flickJson = flickRecord.toJsonString()
        flickJson shouldContain "\"isFlickPrediction\":true"
    }

    test("FlorisNative touch offset query gracefully handles uninitialized native library in unit test context") {
        val offset = org.florisboard.libnative.FlorisNative.getTouchOffset('e')
        offset shouldBe Pair(0f, 0f)

        val allOffsets = org.florisboard.libnative.FlorisNative.getAllTouchOffsets()
        allOffsets shouldBe emptyList()
    }
})