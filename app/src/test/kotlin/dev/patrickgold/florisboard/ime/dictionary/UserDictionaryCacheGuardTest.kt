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

package dev.patrickgold.florisboard.ime.dictionary

import dev.patrickgold.florisboard.lib.FlorisLocale
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UserDictionaryCacheGuardTest : FunSpec({

    test("Macro Guard: Dynamic macros (!time, !date, !now, !t, !d, !datetime) always resolve to valid formatted strings") {
        val testEntries = listOf(
            "!time" to "Snippet • Time",
            "!t" to "Snippet • Time",
            "!date" to "Snippet • Date",
            "!d" to "Snippet • Date",
            "!now" to "Snippet • Now",
            "!datetime" to "Snippet • Now",
            "!b" to "BBCode • Bold",
            "!i" to "BBCode • Italic",
            "!u" to "BBCode • Underline",
            "!s" to "BBCode • Strikethrough",
            "!quote" to "BBCode • Quote",
            "!spoiler" to "BBCode • Spoiler",
            "!url" to "BBCode • URL",
            "!img" to "BBCode • Image",
            "!code" to "BBCode • Code",
            "!color" to "BBCode • Color",
            "!size" to "BBCode • Size",
            "!cords" to "BBCode • Village Coord",
            "!coords" to "BBCode • Village Coord",
            "!coord" to "BBCode • Village Coord",
            "!player" to "BBCode • Player",
            "!tribe" to "BBCode • Tribe",
            "!ally" to "BBCode • Tribe",
            "!claim" to "BBCode • Claim",
            "!report" to "BBCode • Report",
            "!sos" to "BBCode • Defense SOS",
        )

        for ((macro, expectedCategory) in testEntries) {
            val resolved = UserDictionaryCache.evaluateMacros(macro)
            resolved.shouldHaveSize(1)
            val candidate = resolved.first()
            candidate.secondaryText shouldBe expectedCategory
            candidate.isEligibleForAutoCommit shouldBe true
            candidate.confidence shouldBe 1.0
            candidate.text.toString().isNotBlank() shouldBe true
        }

        UserDictionaryCache.evaluateMacros("!b").first().text.toString() shouldBe "[b][/b]"
        UserDictionaryCache.evaluateMacros("!u").first().text.toString() shouldBe "[u][/u]"
        UserDictionaryCache.evaluateMacros("!cords").first().text.toString() shouldBe "[coord][/coord]"
        UserDictionaryCache.evaluateMacros("!coords").first().text.toString() shouldBe "[coord][/coord]"
        UserDictionaryCache.evaluateMacros("!coord").first().text.toString() shouldBe "[coord][/coord]"
        UserDictionaryCache.evaluateMacros("!player").first().text.toString() shouldBe "[player][/player]"
        UserDictionaryCache.evaluateMacros("!tribe").first().text.toString() shouldBe "[tribe][/tribe]"
        UserDictionaryCache.evaluateMacros("!claim").first().text.toString() shouldBe "[claim][/claim]"
    }

    test("Case Insensitivity Guard: Upper and lower case shortcut triggers match correctly") {
        val sampleEntries = listOf(
            UserDictionaryEntry(id = 1, word = "123 Main Street", freq = 250, locale = null, shortcut = "!addr"),
            UserDictionaryEntry(id = 2, word = "hello@example.com", freq = 200, locale = "en_US", shortcut = "myemail"),
        )

        val cache = UserDictionaryCache()
        cache.updateEntries(sampleEntries)

        // Exact match
        val res1 = cache.queryShortcuts("!addr", null)
        res1.shouldHaveSize(1)
        res1.first().text shouldBe "123 Main Street"

        // Uppercase query
        val res2 = cache.queryShortcuts("!ADDR", null)
        res2.shouldHaveSize(1)
        res2.first().text shouldBe "123 Main Street"

        // Mixed case query
        val res3 = cache.queryShortcuts("MyEmail", FlorisLocale.from("en", "US"))
        res3.shouldHaveSize(1)
        res3.first().text shouldBe "hello@example.com"
    }

    test("Locale Matching Guard: Locale-specific shortcuts only match compatible or wildcard locales") {
        val sampleEntries = listOf(
            UserDictionaryEntry(id = 1, word = "Bonjour", freq = 200, locale = "fr_FR", shortcut = "!greet"),
            UserDictionaryEntry(id = 2, word = "Hello", freq = 200, locale = "en_US", shortcut = "!greet"),
            UserDictionaryEntry(id = 3, word = "Universal", freq = 200, locale = null, shortcut = "!univ"),
        )

        val cache = UserDictionaryCache()
        cache.updateEntries(sampleEntries)

        // Query with en_US should return en_US entry
        val enMatch = cache.queryShortcuts("!greet", FlorisLocale.from("en", "US"))
        enMatch.shouldHaveSize(1)
        enMatch.first().text shouldBe "Hello"

        // Query with fr_FR should return fr_FR entry
        val frMatch = cache.queryShortcuts("!greet", FlorisLocale.from("fr", "FR"))
        frMatch.shouldHaveSize(1)
        frMatch.first().text shouldBe "Bonjour"

        // Wildcard entry (locale = null) matches any query locale
        val univMatch = cache.queryShortcuts("!univ", FlorisLocale.from("de", "DE"))
        univMatch.shouldHaveSize(1)
        univMatch.first().text shouldBe "Universal"
    }

    test("Cache Invalidation Guard: Insert, update, delete, and clear immediately reflect in query results") {
        val cache = UserDictionaryCache()
        val entry1 = UserDictionaryEntry(id = 1, word = "Initial Value", freq = 200, locale = null, shortcut = "!key")

        cache.updateEntries(listOf(entry1))
        cache.queryShortcuts("!key", null).first().text shouldBe "Initial Value"

        // Update
        val updatedEntry1 = entry1.copy(word = "Updated Value")
        cache.updateEntries(listOf(updatedEntry1))
        cache.queryShortcuts("!key", null).first().text shouldBe "Updated Value"

        // Delete (empty list)
        cache.updateEntries(emptyList())
        cache.queryShortcuts("!key", null).shouldBeEmpty()
    }

    test("Concurrency Guard: 16 parallel threads never deadlock or throw exceptions during simultaneous reads and writes") {
        val cache = UserDictionaryCache()
        val threadCount = 16
        val operationsPerThread = 500
        val executor = Executors.newFixedThreadPool(threadCount)
        val latch = CountDownLatch(threadCount)
        val exceptions = AtomicInteger(0)

        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until operationsPerThread) {
                        if (i % 20 == 0) {
                            // Writer
                            cache.updateEntries(
                                listOf(
                                    UserDictionaryEntry(id = (i % 10).toLong(), word = "Word_$i", freq = 100, locale = null, shortcut = "!s_${i % 10}")
                                )
                            )
                        } else {
                            // Reader
                            cache.queryShortcuts("!s_${i % 10}", null)
                            UserDictionaryCache.evaluateMacros("!time")
                        }
                    }
                } catch (e: Throwable) {
                    exceptions.incrementAndGet()
                } finally {
                    latch.countDown()
                }
            }
        }

        latch.await(5, TimeUnit.SECONDS) shouldBe true
        exceptions.get() shouldBe 0
        executor.shutdown()
    }
})
