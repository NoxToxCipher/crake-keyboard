/*
 * Copyright (C) 2026 The CrakeBoard Contributors
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

package dev.patrickgold.florisboard.ime.editor

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The two-token deletion span behind merged-word commits ("shou kd" ->
 * "should"). The span decides how many characters before the cursor get
 * replaced, so every shape it can meet is pinned here.
 */
class MergedTokenSpanTest : FunSpec({
    test("spans both fragments and the gap") {
        mergedTokenSpan("shou kd") shouldBe 7
        mergedTokenSpan("ni stakes") shouldBe 9
        mergedTokenSpan("deliberate lt") shouldBe "deliberate lt".length
    }

    test("spans only the trailing pair when text precedes it") {
        mergedTokenSpan("I said shou kd") shouldBe 7
        mergedTokenSpan("left my ni stakes") shouldBe 9
    }

    test("handles multiple spaces in the gap") {
        mergedTokenSpan("shou  kd") shouldBe 8
    }

    test("degrades to current token when no previous token exists") {
        mergedTokenSpan("stakes") shouldBe 6
        mergedTokenSpan("") shouldBe 0
    }

    test("three-fragment span covers all three tokens and their gaps") {
        mergedTokenSpan("cha nbn ges", 3) shouldBe "cha nbn ges".length
        mergedTokenSpan("all your cha nbn ges", 3) shouldBe "cha nbn ges".length
        // Degrades when fewer tokens exist than requested.
        mergedTokenSpan("nbn ges", 3) shouldBe "nbn ges".length
    }
})
