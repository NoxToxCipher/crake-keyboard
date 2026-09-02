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

package dev.patrickgold.florisboard.ime.clipboard

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Oracle for [calculateInSampleSize], the subsample chooser backing the off-main
 * clipboard image decode (perf item 8). Verifies the three properties the decode
 * relies on and cross-checks against an independent brute-force reference so a
 * huge clip is never inflated at full resolution, yet never blurred below the
 * requested cell width when the source is large enough.
 */
class ClipInSampleSizeTest : FunSpec({

    // Independent reference: the LARGEST power-of-two `s` with src/s >= req, else 1.
    // Structured differently from the implementation (ascending powers, tracking
    // the best) so a loop-direction / off-by-one bug in either would disagree.
    fun reference(src: Int, req: Int): Int {
        if (src <= 0 || req <= 0) return 1
        var best = 1
        var s = 1
        while (s <= src) {
            if (src / s >= req) best = s
            s *= 2
        }
        return best
    }

    fun isPowerOfTwo(n: Int): Boolean = n >= 1 && (n and (n - 1)) == 0

    test("calculateInSampleSize matches the reference and holds its invariants") {
        val widths = listOf(0, 1, 2, 3, 7, 16, 100, 199, 200, 201, 512, 1000, 1080, 4000, 8192, 12000)
        val reqs = listOf(0, 1, 16, 64, 100, 128, 250, 256, 300, 512, 1024, 4000)
        for (src in widths) {
            for (req in reqs) {
                val s = calculateInSampleSize(src, req)
                // 1. Always a valid power-of-two subsample.
                isPowerOfTwo(s) shouldBe true
                // 2. Never blur below the request when the source can satisfy it
                //    (else it clamps to 1 = no upsampling).
                if (src > 0 && req > 0) {
                    (src / s >= req || s == 1) shouldBe true
                    // 3. Maximal downsample: doubling would drop below the request.
                    (src / (s * 2) < req) shouldBe true
                }
                // 4. Independent reference agreement.
                s shouldBe reference(src, req)
            }
        }
    }

    test("degenerate inputs never subsample") {
        calculateInSampleSize(0, 100) shouldBe 1
        calculateInSampleSize(100, 0) shouldBe 1
        calculateInSampleSize(-5, 100) shouldBe 1
        calculateInSampleSize(100, -5) shouldBe 1
        // Source narrower than the cell: keep full resolution (no upsampling).
        calculateInSampleSize(120, 300) shouldBe 1
    }
})
