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

package dev.patrickgold.florisboard.ime.text.gestures

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

/**
 * Replays REAL captured strokes through the TOUCH_UP swipe classifier.
 * The flick and tap fixtures below are live device captures from the
 * Xiaomi (2026-08-28, CrakeSwipeDbg probe): a broken VelocityTracker read
 * 0.0/0.0 on all of them, which killed every release-classified gesture —
 * the average-velocity rescue is what these tests protect. The scrub
 * fixture is a measured deliberate 600ms drag. If this test is failing
 * your change, you are re-breaking the delete-word flick; read
 * COORDINATION.md (2026-08-28, VelocityTracker entry) before weakening it.
 */
class SwipeClassifierTest : FunSpec({
    // Defaults as shipped: swipeVelocityThreshold=450, swipeDistanceThreshold=32.
    val defaultSpeed = 450.0
    val defaultWidth = 32.0

    // Live phone captures: absDiff dp, ageMs, tracker read 0.0 on every one.
    val flick1 = Triple(-153.86667f, 24.0f, 103L)
    val flick2 = Triple(-143.73334f, 26.400002f, 97L)
    val tapStill = Triple(0.0f, 0.0f, 59L)
    val tapWobble = Triple(-4.799988f, 8.0f, 65L)
    // Deliberate slow scrub, measured 222 dp/s average over 600ms.
    val slowScrub = Triple(-133.1f, 0.0f, 600L)

    fun classify(s: Triple<Float, Float, Long>, speed: Double = defaultSpeed, width: Double = defaultWidth) =
        SwipeGesture.Detector.classifiesAsSwipe(s.first, s.second, 0.0f, 0.0f, s.third, speed, width)

    test("real flick #1 classifies with the tracker dead") {
        classify(flick1) shouldBe true
    }

    test("real flick #2 classifies with the tracker dead") {
        classify(flick2) shouldBe true
    }

    test("real flicks classify even at the legacy 1900 stored threshold") {
        // Old installs carry gestures__swipe_velocity_threshold=1900; the
        // clamp remaps it to 450 so those users get a working flick too.
        classify(flick1, speed = 1900.0) shouldBe true
        classify(flick2, speed = 1900.0) shouldBe true
    }

    test("real taps never classify") {
        classify(tapStill) shouldBe false
        classify(tapWobble) shouldBe false
    }

    test("a deliberate slow scrub never classifies as a flick") {
        classify(slowScrub) shouldBe false
    }

    test("zero travel never classifies at any velocity - the distance gate holds") {
        SwipeGesture.Detector.classifiesAsSwipe(0.0f, 0.0f, 99999.0f, 99999.0f, 50L, defaultSpeed, defaultWidth) shouldBe false
    }

    test("a live tracker still classifies when stroke age is degenerate") {
        // ageMs<=0 means the average contributes nothing; the tracker must
        // remain sufficient on its own (this is the pre-rescue behavior).
        SwipeGesture.Detector.classifiesAsSwipe(-40.0f, 0.0f, 900.0f, 0.0f, 0L, defaultSpeed, defaultWidth) shouldBe true
    }

    test("no pref value can make classification impossible or trivial") {
        // The clamp keeps the effective threshold inside [200, 800] dp/s for
        // every conceivable stored value: a floor so garbage prefs cannot
        // demand impossible speeds, a ceiling so they cannot fire on taps.
        for (raw in listOf(0.0, 1.0, 100.0, 200.0, 450.0, 799.0, 800.0, 999.0, 1000.0, 1001.0, 1900.0, 100000.0)) {
            val clamped = SwipeGesture.Detector.clampThresholdSpeed(raw)
            (clamped in 200.0..800.0) shouldBe true
        }
    }

    test("legacy default 1900 remaps to 450 exactly") {
        SwipeGesture.Detector.clampThresholdSpeed(1900.0) shouldBe 450.0
    }

    test("average velocity math matches the live capture") {
        SwipeGesture.Detector.averageVelocity(-153.86667f, 103L) shouldBe (1493.85 plusOrMinus 0.1)
        SwipeGesture.Detector.averageVelocity(-133.1f, 600L) shouldBe (221.83 plusOrMinus 0.1)
        SwipeGesture.Detector.averageVelocity(100.0f, 0L) shouldBe 0.0
        SwipeGesture.Detector.averageVelocity(100.0f, -5L) shouldBe 0.0
    }

    test("the real flick vector reads as LEFT - the direction band holds") {
        SwipeGesture.Detector.detectDirection(-153.86667, 24.0) shouldBe SwipeGesture.Direction.LEFT
        SwipeGesture.Detector.detectDirection(-143.73334, 26.400002) shouldBe SwipeGesture.Direction.LEFT
    }

    test("diagonal flicks stay inside the leftward family") {
        // A thumb flick droops or rises; both diagonals must keep deleting.
        SwipeGesture.Detector.detectDirection(-100.0, 70.0) shouldBe SwipeGesture.Direction.DOWN_LEFT
        SwipeGesture.Detector.detectDirection(-100.0, -70.0) shouldBe SwipeGesture.Direction.UP_LEFT
    }

    test("quick snappy upward key flick classifies accurately") {
        // High-speed short thumb flick over letter keycap (e.g. 20dp in 40ms = 500 dp/s)
        val upwardFlick = Triple(0.0f, -20.0f, 40L)
        classify(upwardFlick) shouldBe true
        SwipeGesture.Detector.detectDirection(0.0, -20.0) shouldBe SwipeGesture.Direction.UP
    }
})
