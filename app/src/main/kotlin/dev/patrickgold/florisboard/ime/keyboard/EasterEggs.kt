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

package dev.patrickgold.florisboard.ime.keyboard

/**
 * Registry of the keyboard's hidden animations.
 *
 * The contract (owner ruling 2026-08-27): every egg is ON by default, and its
 * off switch appears in Settings only after the egg has fired once —
 * discovery is the price of the switch, so the surprise stays intact for
 * everyone else. Trigger matching is on-device, in-process, unstored and
 * untransmitted.
 */
enum class EasterEgg(val id: String, val label: String) {
    ECLECTUS_FLIGHT("eclectus_flight", "Eclectus flight"),
    SUN_CONURE_FLIGHT("sun_conure_flight", "Sun conure flight"),
    SOCCER_ROLL("soccer_roll", "Soccer roll"),
    SPACE_RAIN("space_rain", "Space rain"),
    MANGO_PULSE("mango_pulse", "Mango pulse"),
    MASTER_CHIEF_RUN("master_chief_run", "Spartan run"),
    ICE_SKATE_SWIRL("ice_skate_swirl", "Ice skate swirl"),
    BERRIES_FLOW("berries_flow", "Berries flow"),
    TRIBAL_WARS("tribal_wars", "Village raid"),
    BAWEN_CAT("bawen_cat", "Bawen the cat"),
    PUBG_PARACHUTE("pubg_parachute", "Parachute drop"),
    LUCIA_BOBA("lucia_boba", "Lucia's boba"),
    DUKU_FRUIT("duku_fruit", "Duku fruit"),
    CAR_DRIVE("car_drive", "Night drive"),
    CRYPTO_ROCKET("crypto_rocket", "Crypto rocket"),
    MURMUR_FLOCK("murmur_flock", "Starling murmuration"),
    LUNA_CRASH("luna_crash", "Luna crash"),
    SUNDAE("sundae", "Sundae"),
    TRAIN("train", "Train"),
    LOUIE_PAWS("louie_paws", "Louie's paws"),
    IROBOT("irobot", "Three laws"),
    ANDROID_BUGDROID("android_bugdroid", "Bugdroid"),
    ROSE_PETALS("rose_petals", "Rose petals"),
    XBOX_ACHIEVEMENT("xbox_achievement", "Achievement unlocked"),
    HIDDEN_HOODED("hidden_hooded", "Hooded figure"),
    SERENITY_GARDEN("serenity_garden", "Serenity garden"),
    SNIPER_DUDE("sniper_dude", "Sniper"),
    THOR("thor", "God of thunder"),
    MUSHU("mushu", "Mushu"),
    POWER_SURGE("power_surge", "Power surge"),
    BLACKBERRY("blackberry", "BlackBerry tribute"),
    EGG_WORD("egg_word", "The egg itself"),
}

/**
 * Pure helpers over the two CSV-backed preference strings that carry egg
 * state: `discovered` (which eggs have ever fired) and `disabled` (which
 * eggs the user switched off). CSV of stable ids, no ordering guarantees,
 * unknown ids are preserved so downgrades never destroy state.
 */
object EasterEggs {
    private const val SEPARATOR = ","

    fun parseIds(csv: String): Set<String> {
        if (csv.isBlank()) return emptySet()
        return csv.split(SEPARATOR).mapNotNull { entry ->
            entry.trim().ifEmpty { null }
        }.toSet()
    }

    fun encodeIds(ids: Set<String>): String {
        return ids.sorted().joinToString(SEPARATOR)
    }

    fun withId(csv: String, id: String): String = encodeIds(parseIds(csv) + id)

    fun withoutId(csv: String, id: String): String = encodeIds(parseIds(csv) - id)

    fun isDiscovered(discoveredCsv: String, egg: EasterEgg): Boolean {
        return egg.id in parseIds(discoveredCsv)
    }

    fun isEnabled(disabledCsv: String, egg: EasterEgg): Boolean {
        return egg.id !in parseIds(disabledCsv)
    }

    fun discoveredEggs(discoveredCsv: String): List<EasterEgg> {
        val ids = parseIds(discoveredCsv)
        return EasterEgg.entries.filter { it.id in ids }
    }
}
