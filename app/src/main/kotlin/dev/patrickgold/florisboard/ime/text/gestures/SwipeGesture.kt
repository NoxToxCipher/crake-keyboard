/*
 * Copyright (C) 2020-2025 The FlorisBoard Contributors
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

import android.view.MotionEvent
import android.view.VelocityTracker
import androidx.compose.ui.unit.dp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.lib.Pointer
import dev.patrickgold.florisboard.lib.PointerMap
import dev.patrickgold.florisboard.lib.devtools.LogTopic
import dev.patrickgold.florisboard.lib.devtools.flogDebug
import dev.patrickgold.florisboard.lib.util.ViewUtils
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Wrapper class which holds all enums, interfaces and classes for detecting a swipe gesture.
 */
abstract class SwipeGesture {
    companion object {
        /**
         * A delete-key scrub may only begin once the stroke is older than
         * this. MEASURED, not chosen: real backspace flicks finish in
         * 61-103ms (same stroke class as glide flicks, see the 150ms glide
         * duration gate), while a deliberate character scrub is still in
         * contact well past 150ms. Without this window the flick's first
         * move samples start the scrub, the scrub's selection then blocks
         * the word-delete fallback on release, and whether a flick deletes
         * the word or a random few characters becomes a race between move
         * sampling and finger lift (field report 2026-08-28: "swipe left
         * to backspace an entire word doesn't work, again").
         */
        internal const val DELETE_SCRUB_MIN_AGE_MS = 150L
    }

    /**
     * Class which detects swipes based on given [MotionEvent]s. Only supports single-finger swipes
     * and ignores additional pointers provided, if any.
     *
     * @property listener The listener to report detected swipes to.
     */
    class Detector(private val listener: Listener) {
        companion object {
            /**
             * The clamped release-velocity threshold in dp/s. Old stored
             * prefs carry the historic 1900 default which no measured
             * stroke reaches, so anything above 1000 is remapped to 450;
             * in-range values are held to [200, 800] so no pref value can
             * make classification impossible (floor) or fire on taps
             * (ceiling). Pinned by SwipeClassifierTest.
             */
            internal fun clampThresholdSpeed(raw: Double): Double =
                if (raw > 1000.0) 450.0 else raw.coerceIn(200.0, 800.0)

            /**
             * Whole-stroke average velocity in dp/s from hardware event
             * timestamps. Exists because VelocityTracker returned 0.0/0.0
             * for strokes it was correctly fed (observed on the Xiaomi and
             * on a clean emulator, 2026-08-28); a broken tracker must
             * never be able to kill classification on its own. A degenerate
             * age (<=0) contributes 0 so the tracker remains the only
             * voice rather than dividing by zero. Pinned by
             * SwipeClassifierTest against real captured strokes.
             */
            internal fun averageVelocity(absDiffDp: Float, ageMs: Long): Double =
                if (ageMs > 0) kotlin.math.abs(absDiffDp) * 1000.0 / ageMs else 0.0

            /**
             * The TOUCH_UP swipe classification, pure so it can be replayed
             * in JVM tests. A stroke classifies when it travelled far enough
             * on either axis AND was fast enough on either axis, where speed
             * is the better of the tracker's reading and the whole-stroke
             * average — a real flick passes on the average alone (measured
             * 1494 dp/s on a live phone flick), a tap has no travel, and a
             * deliberate scrub averages well under any allowed threshold
             * (measured 222 dp/s).
             */
            internal fun classifiesAsSwipe(
                absDiffXDp: Float,
                absDiffYDp: Float,
                trackerVelXDp: Float,
                trackerVelYDp: Float,
                ageMs: Long,
                rawThresholdSpeed: Double,
                thresholdWidthDp: Double,
            ): Boolean {
                val thresholdSpeed = clampThresholdSpeed(rawThresholdSpeed)
                val velocityX = maxOf(kotlin.math.abs(trackerVelXDp).toDouble(), averageVelocity(absDiffXDp, ageMs))
                val velocityY = maxOf(kotlin.math.abs(trackerVelYDp).toDouble(), averageVelocity(absDiffYDp, ageMs))
                val maxTravel = maxOf(kotlin.math.abs(absDiffXDp), kotlin.math.abs(absDiffYDp))
                val maxVelocity = maxOf(velocityX, velocityY)

                // For high-velocity snappy flicks (e.g. upward letter word flick or backspace flick),
                // an absolute displacement of 14dp is distinct from accidental tap wobble (<=8dp)
                // and prevents dropping fast short thumb flicks on high-density displays.
                val effectiveThresholdWidth = if (maxVelocity >= thresholdSpeed) {
                    (thresholdWidthDp * 0.5).coerceAtLeast(14.0)
                } else {
                    thresholdWidthDp
                }

                return (maxTravel > effectiveThresholdWidth) && (maxVelocity > thresholdSpeed)
            }

            /**
             * Detects the direction of a finger swipe from the stroke's
             * total displacement. The returned angle bands are pinned by
             * SwipeClassifierTest with a real captured flick vector so the
             * LEFT band cannot silently narrow.
             */
            internal fun detectDirection(diffX: Double, diffY: Double): Direction {
                val diffAngle = angle(diffX, diffY) / 360.0
                return when {
                    diffAngle >= (1/16.0) && diffAngle < (3/16.0) ->        Direction.DOWN_RIGHT
                    diffAngle >= (3/16.0) && diffAngle < (5/16.0) ->        Direction.DOWN
                    diffAngle >= (5/16.0) && diffAngle < (7/16.0) ->        Direction.DOWN_LEFT
                    diffAngle >= (7/16.0) && diffAngle < (9/16.0) ->        Direction.LEFT
                    diffAngle >= (9/16.0) && diffAngle < (11/16.0) ->       Direction.UP_LEFT
                    diffAngle >= (11/16.0) && diffAngle < (13/16.0) ->      Direction.UP
                    diffAngle >= (13/16.0) && diffAngle < (15/16.0) ->      Direction.UP_RIGHT
                    else ->                                                 Direction.RIGHT
                }
            }

            /**
             * Calculates the angle based on the given x any y lengths. The returned angle is in degree
             * and goes clockwise, beginning with 0° at +x, 90° at +y, 180° at -y and 270° at -y.
             *
             * Coordinate system (based on the Android display coordinate system):
             *    -y
             * -x 00 +x
             *    +y
             */
            private fun angle(diffX: Double, diffY: Double): Double {
                return (Math.toDegrees(atan2(diffY, diffX)) + 360) % 360
            }
        }

        private val prefs by FlorisPreferenceStore

        var isEnabled: Boolean = true
        private var pointerMap: PointerMap<GesturePointer> = PointerMap { GesturePointer() }
        private val velocityTracker: VelocityTracker = VelocityTracker.obtain()

        fun onTouchEvent(event: MotionEvent) {
            if (!isEnabled) return
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                resetState()
            }
            velocityTracker.addMovement(event)
        }

        fun onTouchDown(event: MotionEvent, pointer: Pointer) {
            if (!isEnabled) return
            pointerMap.add(pointer.id, pointer.index)?.let { gesturePointer ->
                gesturePointer.firstX = ViewUtils.px2dp(event.getX(pointer.index))
                gesturePointer.firstY = ViewUtils.px2dp(event.getY(pointer.index))
                gesturePointer.lastX = gesturePointer.firstX
                gesturePointer.lastY = gesturePointer.firstY
            }
        }

        fun onTouchMove(event: MotionEvent, pointer: Pointer, alwaysTriggerOnMove: Boolean): Boolean {
            if (!isEnabled) return false
            pointerMap.findById(pointer.id)?.let { gesturePointer ->
                gesturePointer.index = pointer.index
                val currentX = ViewUtils.px2dp(event.getX(pointer.index))
                val currentY = ViewUtils.px2dp(event.getY(pointer.index))
                val absDiffX = currentX - gesturePointer.firstX
                val absDiffY = currentY - gesturePointer.firstY
                val relDiffX = currentX - gesturePointer.lastX
                val relDiffY = currentY - gesturePointer.lastY
                val thresholdWidth = prefs.gestures.swipeDistanceThreshold.get().dp.value.toDouble()
                val unitWidth = thresholdWidth / 4.0
                return if (alwaysTriggerOnMove || abs(relDiffX) > (thresholdWidth / 2.0) || abs(relDiffY) > (thresholdWidth / 2.0)) {
                    gesturePointer.lastX = currentX
                    gesturePointer.lastY = currentY
                    val direction = detectDirection(relDiffX.toDouble(), relDiffY.toDouble())
                    val newAbsUnitCountX = (absDiffX / unitWidth).toInt()
                    val newAbsUnitCountY = (absDiffY / unitWidth).toInt()
                    val relUnitCountX = newAbsUnitCountX - gesturePointer.absUnitCountX
                    val relUnitCountY = newAbsUnitCountY - gesturePointer.absUnitCountY
                    gesturePointer.absUnitCountX = newAbsUnitCountX
                    gesturePointer.absUnitCountY = newAbsUnitCountY
                    listener.onSwipe(Event(
                        direction = direction,
                        type = Type.TOUCH_MOVE,
                        pointer.id,
                        gesturePointer.absUnitCountX,
                        gesturePointer.absUnitCountY,
                        relUnitCountX,
                        relUnitCountY,
                        ageMs = event.eventTime - event.downTime,
                    ))
                } else {
                    false
                }
            }
            return false
        }

        fun onTouchUp(event: MotionEvent, pointer: Pointer): Boolean {
            if (!isEnabled) return false
            pointerMap.findById(pointer.id)?.let { gesturePointer ->
                val currentX = ViewUtils.px2dp(event.getX(pointer.index))
                val currentY = ViewUtils.px2dp(event.getY(pointer.index))
                val absDiffX = currentX - gesturePointer.firstX
                val absDiffY = currentY - gesturePointer.firstY
                velocityTracker.computeCurrentVelocity(1000)
                val trackerVelocityX = ViewUtils.px2dp(velocityTracker.getXVelocity(pointer.id))
                val trackerVelocityY = ViewUtils.px2dp(velocityTracker.getYVelocity(pointer.id))
                val ageMs = event.eventTime - event.downTime
                flogDebug(LogTopic.GESTURES) { "Velocity: tracker=$trackerVelocityX/$trackerVelocityY ageMs=$ageMs dp/s" }
                pointerMap.removeById(pointer.id)
                val rawThreshold = prefs.gestures.swipeVelocityThreshold.get().toDouble()
                val thresholdWidth = prefs.gestures.swipeDistanceThreshold.get().dp.value.toDouble()
                val unitWidth = (thresholdWidth / 4.0).coerceAtLeast(4.0)
                return if (classifiesAsSwipe(absDiffX, absDiffY, trackerVelocityX, trackerVelocityY, ageMs, rawThreshold, thresholdWidth)) {
                    val direction = detectDirection(absDiffX.toDouble(), absDiffY.toDouble())
                    gesturePointer.absUnitCountX = (absDiffX / unitWidth).toInt()
                    gesturePointer.absUnitCountY = (absDiffY / unitWidth).toInt()
                    listener.onSwipe(Event(
                        direction = direction,
                        type = Type.TOUCH_UP,
                        pointer.id,
                        gesturePointer.absUnitCountX,
                        gesturePointer.absUnitCountY,
                        gesturePointer.absUnitCountX,
                        gesturePointer.absUnitCountY,
                        ageMs = event.eventTime - event.downTime,
                    ))
                } else {
                    false
                }
            }
            return false
        }

        @Suppress("UNUSED_PARAMETER")
        fun onTouchCancel(event: MotionEvent, pointer: Pointer) {
            if (!isEnabled) return
            pointerMap.removeById(pointer.id)
        }

        /**
         * Resets the state.
         */
        private fun resetState() {
            pointerMap.clear()
            velocityTracker.clear()
        }

        class GesturePointer : Pointer() {
            var firstX: Float = 0.0f
            var firstY: Float = 0.0f
            var lastX: Float = 0.0f
            var lastY: Float = 0.0f
            var absUnitCountX: Int = 0
            var absUnitCountY: Int = 0

            override fun reset() {
                super.reset()
                firstX = 0.0f
                firstY = 0.0f
                lastX = 0.0f
                lastY = 0.0f
                absUnitCountX = 0
                absUnitCountY = 0
            }
        }
    }

    /**
     * An interface which provides an abstract callback function, which will be called for any
     * detected swipe event.
     */
    interface Listener {
        fun onSwipe(event: Event): Boolean
    }

    /**
     * Data class which describes a single gesture event.
     */
    data class Event(
        /** The direction of the swipe. */
        val direction: Direction,
        /** The type of the swipe. */
        val type: Type,
        /** The pointer ID of this event, corresponds to the value reported by the original MotionEvent. */
        val pointerId: Int,
        /** The unit count on the x-axis, measured from the first event (ACTION_DOWN). */
        val absUnitCountX: Int,
        /** The unit count on the y-axis, measured from the first event (ACTION_DOWN). */
        val absUnitCountY: Int,
        /** The unit count on the x-axis, measured from the last event (ACTION_MOVE). */
        val relUnitCountX: Int,
        /** The unit count on the y-axis, measured from the last event (ACTION_MOVE). */
        val relUnitCountY: Int,
        /** Milliseconds since the gesture's ACTION_DOWN, from hardware event timestamps. */
        val ageMs: Long = 0L,
    )

    /**
     * ENum which defines the direction of the detected swipe.
     */
    enum class Direction {
        UP_LEFT,
        UP,
        UP_RIGHT,
        RIGHT,
        DOWN_RIGHT,
        DOWN,
        DOWN_LEFT,
        LEFT;
    }

    /**
     * Enum which defines the type of the gesture.
     */
    enum class Type {
        TOUCH_UP,
        TOUCH_MOVE;
    }
}
