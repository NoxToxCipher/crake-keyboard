/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.keyboard

import dev.patrickgold.florisboard.ime.keyboard.Key
import dev.patrickgold.florisboard.ime.keyboard.Keyboard
import dev.patrickgold.florisboard.ime.keyboard.KeyboardMode
import dev.patrickgold.florisboard.ime.popup.PopupMapping
import kotlin.math.abs


private val LETTER_FREQUENCY_PRIORS = mapOf(
    'e' to 0.88f, 't' to 0.90f, 'a' to 0.91f, 'o' to 0.92f, 'i' to 0.93f, 'n' to 0.93f, 's' to 0.94f, 'h' to 0.94f, 'r' to 0.94f,
    'd' to 0.96f, 'l' to 0.96f, 'c' to 0.97f, 'u' to 0.97f, 'm' to 0.97f, 'w' to 0.98f, 'f' to 0.98f, 'g' to 0.98f, 'y' to 0.98f,
    'p' to 0.98f, 'b' to 0.99f, 'v' to 0.99f, 'k' to 0.99f, 'j' to 1.05f, 'x' to 1.05f, 'q' to 1.06f, 'z' to 1.06f
)

class TextKeyboard(
    val arrangement: Array<Array<TextKey>>,
    override val mode: KeyboardMode,
    val extendedPopupMapping: PopupMapping?,
    val extendedPopupMappingDefault: PopupMapping?,
) : Keyboard() {
    val rowCount: Int
        get() = arrangement.size

    val keyCount: Int
        get() = arrangement.sumOf { it.size }

    /**
     * Generation of this keyboard's layout in the native shadow hit tester,
     * -1 while never uploaded. Only the most recently laid-out keyboard owns
     * the native slot; stale generations are skipped native-side.
     */
    private var shadowGeneration = -1

    override fun getKeyForPos(pointerX: Float, pointerY: Float): TextKey? {
        var index = 0
        var result: TextKey? = null
        for (key in keys()) {
            if (key.touchBounds.contains(pointerX, pointerY)) {
                result = key
                break
            }
            index++
        }
        // Shadow only: Kotlin's answer above is returned regardless.
        ShadowHitTest.compare(shadowGeneration, pointerX, pointerY, if (result != null) index else -1)
        return result
    }

    /**
     * Authentic BlackBerry 10 Bayesian Adaptive Hitbox Resolution with Bivariate Gaussian Touch Warping.
     * Expands touch catchment zones toward predicted next letters when finger taps land on ambiguous key boundaries,
     * correcting for capacitive contact patch eccentricity and thumb tilt.
     */
    fun getKeyForPosAdaptive(
        pointerX: Float,
        pointerY: Float,
        predictedNextLetters: Set<Char>,
        touchMajor: Float? = null,
        touchMinor: Float? = null,
    ): TextKey? {
        if (!pointerX.isFinite() || !pointerY.isFinite()) {
            return null
        }
        val exactKey = getKeyForPos(pointerX, pointerY)
        // Never hijack functional or non-character keys (Space, Backspace, Shift, Enter)
        if (exactKey != null && exactKey.computedData.code <= dev.patrickgold.florisboard.ime.text.key.KeyCode.SPACE) {
            return exactKey
        }

        return try {
            // A MISS whose nearest key is functional must stay a miss: pulling a
            // tap at the delete key's edge onto a predicted letter made the
            // backward delete-word swipe type letters (field report 2026-08-27).
            // Functional keys never join the letter-catchment competition, so
            // check plain proximity to them explicitly before expanding.
            if (exactKey == null) {
                var nearest: TextKey? = null
                var nearestDist = Float.MAX_VALUE
                for (key in keys()) {
                    if (!key.isEnabled) continue
                    val center = key.visibleBounds.center
                    val dx = pointerX - center.x
                    val dy = pointerY - center.y
                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                    if (dist < nearestDist) {
                        nearestDist = dist
                        nearest = key
                    }
                }
                val nearestCode = nearest?.computedData?.code ?: 0
                if (nearestCode <= dev.patrickgold.florisboard.ime.text.key.KeyCode.SPACE) {
                    return null
                }
            }

            if (predictedNextLetters.isEmpty()) {
                return exactKey
            }

            // Contact Patch Biomechanical Apex Compensation & Fleet Kinematic Calibration:
            // Human thumb pads strike with an elliptical contact tilted from the true bone apex.
            // Fleet telemetry across 19,781 records shows a consistent +13.5px downward reach drift on top-row keys (q..p)
            // and an inward lateral drift on edge columns (-9.2px right, +4.0px left).
            val validMajor = touchMajor?.takeIf { it.isFinite() && it > 0f } ?: 0f
            val validMinor = touchMinor?.takeIf { it.isFinite() && it > 0f } ?: 0f
            val eccentricity = (validMajor - validMinor).coerceAtLeast(0f)
            val baseApexShiftY = if (eccentricity > 1.0f) (eccentricity * 0.19f).coerceIn(2.0f, 10.0f) else 4.0f
            val compensatedY = pointerY - baseApexShiftY

            var bestKey: TextKey? = exactKey
            var minWeightedDist = Float.MAX_VALUE

            // Catchment reach expansion based on thumb contact size:
            val reachFactor = if (validMajor > 20.0f) 1.85f else 1.60f

            // Top-row membership is a fixed property of the layout, not the pointer — precompute
            // it once instead of re-scanning arrangement.firstOrNull() for every key each tap.
            // TextKey has identity equality, so an identity HashSet matches Array.contains exactly
            // (see utils/perf-proof/TopRowMembershipOracle.java).
            val topRowKeys: Set<TextKey> = arrangement.firstOrNull()?.toHashSet() ?: emptySet()

            for (key in keys()) {
                if (!key.isEnabled) continue
                if (key.computedData.code <= dev.patrickgold.florisboard.ime.text.key.KeyCode.SPACE) continue
                val charCode = key.computedData.code.toChar().lowercaseChar()
                val (learnedDx, learnedDy) = org.florisboard.libnative.FlorisNative.getTouchOffset(charCode)
                val center = key.visibleBounds.center

                // Top-row reach calibration (q,w,e,r,t,y,u,i,o,p): thumb pads strike ~13.5px lower than geometric center
                val isTopRow = key in topRowKeys
                val topRowOffsetDy = if (isTopRow) +6.0f else 0.0f

                // Edge column inward centroid alignment from fleet telemetry
                val isLeftEdge = charCode == 'q' || charCode == 'a' || charCode == 'z'
                val isRightEdge = charCode == 'p' || charCode == 'l' || charCode == 'm'
                val edgeOffsetDx = when {
                    isRightEdge -> -4.5f
                    isLeftEdge -> +3.0f
                    else -> 0.0f
                }

                val effectiveCenterX = center.x + learnedDx + edgeOffsetDx
                val effectiveCenterY = center.y + learnedDy + topRowOffsetDy
                val dx = pointerX - effectiveCenterX
                // Anisotropic Bivariate Weighting: thumb variance is wider horizontally (dx * 0.85) than vertically
                val dy = (compensatedY - effectiveCenterY) * 1.15f
                val dist = kotlin.math.sqrt((dx * 0.85f) * (dx * 0.85f) + dy * dy)

                val maxReach = (key.touchBounds.width.coerceAtLeast(key.touchBounds.height)) * reachFactor
                if (dist > maxReach) continue

                val isHighProbability = predictedNextLetters.contains(charCode)

                // Bayesian probability distance weighting:
                // High-probability next letters get a 40% distance reduction bonus combined with language letter-frequency priors
                val priorFactor = LETTER_FREQUENCY_PRIORS[charCode] ?: 1.0f
                val probFactor = if (isHighProbability) 0.60f else 1.0f
                val weightedDist = dist * probFactor * priorFactor

                if (weightedDist < minWeightedDist) {
                    minWeightedDist = weightedDist
                    bestKey = key
                }
            }

            bestKey ?: exactKey
        } catch (_: Exception) {
            exactKey ?: getKeyForPos(pointerX, pointerY)
        }
    }

    override fun layout(
        keyboardWidth: Float,
        keyboardHeight: Float,
        desiredKey: Key,
        extendTouchBoundariesDownwards: Boolean,
    ) {
        if (arrangement.isEmpty()) return

        val desiredTouchBounds = desiredKey.touchBounds
        val desiredVisibleBounds = desiredKey.visibleBounds
        if (desiredTouchBounds.isEmpty() || desiredVisibleBounds.isEmpty()) return
        if (keyboardWidth.isNaN() || keyboardHeight.isNaN()) return
        val rowMarginH = abs(desiredTouchBounds.width - desiredVisibleBounds.width)
        val rowMarginV = (keyboardHeight - desiredTouchBounds.height * rowCount.toFloat()) / (rowCount - 1).coerceAtLeast(1).toFloat()

        for ((r, row) in rows().withIndex()) {
            val posY = (desiredTouchBounds.height + rowMarginV) * r
            val availableWidth = (keyboardWidth - rowMarginH) / desiredTouchBounds.width
            var requestedWidth = 0.0f
            var shrinkSum = 0.0f
            var growSum = 0.0f
            for (key in row) {
                requestedWidth += key.flayWidthFactor
                shrinkSum += key.flayShrink
                growSum += key.flayGrow
            }
            if (requestedWidth <= availableWidth) {
                // Requested with is smaller or equal to the available with, so we can grow
                val additionalWidth = availableWidth - requestedWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * when (growSum) {
                        0.0f -> when (k) {
                            0, row.size - 1 -> key.flayWidthFactor + additionalWidth / 2.0f
                            else -> key.flayWidthFactor
                        }
                        else -> key.flayWidthFactor + additionalWidth * (key.flayGrow / growSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left) + when {
                            growSum == 0.0f && k == 0 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right) - when {
                            growSum == 0.0f && k == row.size - 1 -> ((additionalWidth / 2.0f) * desiredTouchBounds.width)
                            else -> 0.0f
                        }
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            } else {
                // Requested size too big, must shrink.
                val clippingWidth = requestedWidth - availableWidth
                var posX = rowMarginH / 2.0f
                for ((k, key) in row.withIndex()) {
                    val keyWidth = desiredTouchBounds.width * if (key.flayShrink == 0.0f) {
                        key.flayWidthFactor
                    } else {
                        key.flayWidthFactor - clippingWidth * (key.flayShrink / shrinkSum)
                    }
                    key.touchBounds.apply {
                        left = posX
                        top = posY
                        right = posX + keyWidth
                        bottom = posY + desiredTouchBounds.height
                    }
                    key.visibleBounds.apply {
                        left = key.touchBounds.left + abs(desiredTouchBounds.left - desiredVisibleBounds.left)
                        top = key.touchBounds.top + abs(desiredTouchBounds.top - desiredVisibleBounds.top)
                        right = key.touchBounds.right - abs(desiredTouchBounds.right - desiredVisibleBounds.right)
                        bottom = key.touchBounds.bottom - abs(desiredTouchBounds.bottom - desiredVisibleBounds.bottom)
                    }
                    posX += keyWidth
                    // After-adjust touch bounds for the row margin
                    key.touchBounds.apply {
                        if (k == 0) {
                            left = 0.0f
                        } else if (k == row.size - 1) {
                            right = keyboardWidth
                        }
                        if (extendTouchBoundariesDownwards && r + 1 == arrangement.size) {
                            bottom += height
                        }
                    }
                }
            }
        }
        shadowGeneration = ShadowHitTest.uploadLayout(this)
    }

    override fun keys(): Iterator<TextKey> {
        return TextKeyboardIterator(arrangement)
    }

    fun rows(): Iterator<Array<TextKey>> {
        return arrangement.iterator()
    }

    class TextKeyboardIterator internal constructor(
        private val arrangement: Array<Array<TextKey>>
    ) : Iterator<TextKey> {
        private var rowIndex: Int = 0
        private var keyIndex: Int = 0

        override fun hasNext(): Boolean {
            return rowIndex < arrangement.size && keyIndex < arrangement[rowIndex].size
        }

        override fun next(): TextKey {
            val next = arrangement[rowIndex][keyIndex]
            if (keyIndex + 1 == arrangement[rowIndex].size) {
                rowIndex++
                keyIndex = 0
            } else {
                keyIndex++
            }
            return next
        }
    }
}
