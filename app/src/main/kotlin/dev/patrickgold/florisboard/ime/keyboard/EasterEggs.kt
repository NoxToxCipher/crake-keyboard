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
 * Registry of the keyboard's hidden animations and secret easter eggs.
 *
 * Each easter egg is ON by default. Users can see how many they have triggered
 * and how many they have recorded/identified by guessing the trigger words.
 * Once an easter egg is correctly recorded, its individual toggle is unlocked.
 */
enum class EasterEgg(
    val id: String,
    val label: String,
    val description: String,
    val triggerWords: List<String>,
) {
    BATTERY_OVERCHARGE(
        "battery_overcharge",
        "Overcharged Battery",
        "Lightning-charged plasma battery HUD on the Smartbar",
        listOf("battery", "batteries", "supercharge", "overcharge", "power", "charge"),
    ),
    ECLECTUS_FLIGHT(
        "eclectus_flight",
        "Eclectus Flight",
        "Emerald & scarlet eclectus parrots in tandem flight",
        listOf("eclectus", "ecky", "eckies", "roratus"),
    ),
    SUN_CONURE_FLIGHT(
        "sun_conure_flight",
        "Sun Conure Flight",
        "Fast golden conure sweeping right to left across top fret",
        listOf("sun conure", "sunconure", "conure", "solstice"),
    ),
    SOCCER_ROLL(
        "soccer_roll",
        "Soccer Roll",
        "Soccer ball rolling across top and bottom frets",
        listOf("soccer", "football", "futbol"),
    ),
    SPACE_RAIN(
        "space_rain",
        "Spacebar Rain",
        "Gentle rain droplets and ripples washing over spacebar",
        listOf("rain", "rainy", "raining", "rainfall", "rainstorm"),
    ),
    MANGO_PULSE(
        "mango_pulse",
        "Mango Pulse",
        "Soft honey-mango dual ambient pulse in whisper-soft borderless glow",
        listOf("mango", "mangoes", "mangos"),
    ),
    MASTER_CHIEF_RUN(
        "master_chief_run",
        "Spartan Run",
        "Mini Master Chief (Spartan-117) sprint along bottom fret",
        listOf("halo", "chief", "masterchief", "master chief", "117", "spartan"),
    ),
    ICE_SKATE_SWIRL(
        "ice_skate_swirl",
        "Ice Skate Swirl",
        "Figure skating cursive looping swirl gliding across keyboard",
        listOf("rink", "skating", "iceskating", "ice skating", "skate", "figures"),
    ),
    BERRIES_FLOW(
        "berries_flow",
        "Berries Flow",
        "Inward fade & outward crimson berry flow across frets",
        listOf("berry", "berries", "strawberry", "blueberry", "raspberry"),
    ),
    TRIBAL_WARS(
        "tribal_wars",
        "Village Raid",
        "Tribal Wars medieval quotes phasing along keyboard frets",
        listOf("tribalwars", "tribal wars", "tribal_wars", "tw"),
    ),
    BAWEN_CAT(
        "bawen_cat",
        "Bawen the Cat",
        "Ginger & white cat face peering softly across keys",
        listOf("bawen"),
    ),
    PUBG_PARACHUTE(
        "pubg_parachute",
        "Parachute Drop",
        "PUBG airdrop paratrooper gliding across keycaps",
        listOf("pubg", "airdrop", "pochinki", "chicken dinner", "winner winner"),
    ),
    LUCIA_BOBA(
        "lucia_boba",
        "Lucia's Boba",
        "Boba bubble tea cup resting softly on Shift key",
        listOf("lucia", "boba", "bubble tea"),
    ),
    DUKU_FRUIT(
        "duku_fruit",
        "Duku Fruit",
        "Rare fruit dynamic peeling & blooming translucent pearl arils",
        listOf("duku", "langsat", "longkong"),
    ),
    CAR_DRIVE(
        "car_drive",
        "Night Drive",
        "Sportscar & Aston Martin cruising top & bottom frets",
        listOf("drive", "car", "driving", "cars", "driver", "drives", "drove"),
    ),
    CRYPTO_ROCKET(
        "crypto_rocket",
        "Crypto Rocket",
        "Moon rocket blasting off bottom-left to top-right",
        listOf("bitcoin", "crypto", "to the moon", "moon", "btc", "eth", "solana"),
    ),
    MURMUR_FLOCK(
        "murmur_flock",
        "Starling Murmuration",
        "Murmur bird flock swooping across layout with majestic eagle",
        listOf("murmur", "flock", "murmuration", "starlings"),
    ),
    LUNA_CRASH(
        "luna_crash",
        "Luna Crash",
        "Rocket ascent into depeg death spiral and catastrophic explosion",
        listOf("terra", "luna", "ust", "lunc", "do kwon", "terra luna"),
    ),
    SUNDAE(
        "sundae",
        "Artisanal Sundae",
        "Sundae ice creams landing on S-U-N-D-A-E keys",
        listOf("sundae", "sundaes", "icecream", "ice cream", "gelato", "parfait"),
    ),
    STEAM_TRAIN(
        "steam_train",
        "Steam Locomotive",
        "Classic puffing steam locomotive cruising middle fret",
        listOf("train", "trains", "choo choo", "choochoo", "locomotive", "steam train"),
    ),
    NOBLE_TRAIN(
        "noble_train",
        "Royal Noble Train",
        "Royal Golden 4-carriage noble train with purple velvet coaches & crests",
        listOf("noble train", "nobletrain", "noble_train", "sniping trains", "noble"),
    ),
    LOUIE_PAWS(
        "louie_paws",
        "Louie's Paws",
        "Red nose pitty paw prints trotting with warm copper glow",
        listOf("louie", "pitty", "pitbull", "red nose", "rednose"),
    ),
    IROBOT(
        "irobot",
        "Three Laws",
        "Sonny & NS-5 futuristic cybernetic scanning pulse",
        listOf("artificial intelligence", "irobot", "i, robot", "ns5", "ns-5", "sonny", "asimov", "ai"),
    ),
    ANDROID_BUGDROID(
        "android_bugdroid",
        "Bugdroid",
        "Green Android mascot waving on keyboard",
        listOf("android", "bugdroid", "green dude", "google android", "apk"),
    ),
    ROSE_PETALS(
        "rose_petals",
        "Rose Petals",
        "Velvet crimson rose petals sweeping on atmospheric breeze",
        listOf("i love you", "iloveyou", "love you", "i <3 you", "i love u", "rose", "roses"),
    ),
    XBOX_ACHIEVEMENT(
        "xbox_achievement",
        "Achievement Unlocked",
        "Xbox achievement unlocked banner & chime popup",
        listOf("xbox", "xbox 360", "series x", "series s", "xbox one", "game pass"),
    ),
    HIDDEN_HOODED(
        "hidden_hooded",
        "Hooded Figure",
        "Hooded assassin emerging silently from shadows",
        listOf("hidden", "assassin", "hooded figure", "ninja"),
    ),
    SERENITY_GARDEN(
        "serenity_garden",
        "Serenity Garden",
        "Cherry blossoms & peaceful garden koi ripples",
        listOf("zen", "serenity", "peace", "calm", "meditate", "breathe", "relax", "sad", "stress", "stressed", "anxious", "anxiety", "depressed", "unhappy", "zen garden", "garden"),
    ),
    SNIPER_DUDE(
        "sniper_dude",
        "Sniper Crosshair",
        "Tactical scope crosshair laser sweep across frets",
        listOf("snipe", "snipes", "sniper", "sniped", "sniping", "headshot"),
    ),
    THOR(
        "thor",
        "God of Thunder",
        "Mjolnir lightning strike crackling across keyboard",
        listOf("thor", "mjolnir", "god of thunder", "asgard", "odinson"),
    ),
    MUSHU(
        "mushu",
        "Mushu Dragon",
        "Guardian dragon flame swirl & gong chime",
        listOf("mushu", "mulan", "cri-kee", "dishonor on your cow"),
    ),
    POWER_SURGE(
        "power_surge",
        "Power Surge",
        "Quantum core energy surge when plugged into charger",
        listOf("power", "charge", "plugged in", "surge", "quantum"),
    ),
    BLACKBERRY(
        "blackberry",
        "BlackBerry Tribute",
        "3D physical mechanical keycap tactile flip",
        listOf("blackberry", "bb10", "bold 9900", "passport", "rim"),
    ),
    GO_KART(
        "go_kart",
        "Mini Go-Karts",
        "3 miniature racing karts zooming across keyboard rows",
        listOf("go-kart", "gokart", "kart", "karting", "go kart", "gokarts"),
    ),
    EGG_WORD(
        "egg_word",
        "The Egg Itself",
        "Golden mysterious glowing Crake egg badge",
        listOf("egg", "easter egg", "easteregg", "easter", "crake egg"),
    ),
    LICORICE(
        "licorice",
        "Licorice the Guinea Pig",
        "Sweet sleeping black guinea pig resting peacefully on the Enter key",
        listOf("licorice"),
    ),
}

/**
 * Pure helpers over CSV-backed preference strings for discovered, recorded, and disabled eggs.
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

    fun isRecorded(recordedCsv: String, egg: EasterEgg): Boolean {
        return egg.id in parseIds(recordedCsv)
    }

    fun isEnabled(disabledCsv: String, egg: EasterEgg): Boolean {
        return egg.id !in parseIds(disabledCsv)
    }

    fun discoveredEggs(discoveredCsv: String): List<EasterEgg> {
        val ids = parseIds(discoveredCsv)
        return EasterEgg.entries.filter { it.id in ids }
    }

    fun recordedEggs(recordedCsv: String): List<EasterEgg> {
        val ids = parseIds(recordedCsv)
        return EasterEgg.entries.filter { it.id in ids }
    }

    /**
     * Matches a user-provided guess string against the registry of Easter Eggs.
     * Normalized case-insensitively with trimmed whitespace.
     */
    fun matchTriggerPhrase(phrase: String): EasterEgg? {
        val clean = phrase.trim().lowercase()
        if (clean.isEmpty()) return null
        for (egg in EasterEgg.entries) {
            if (egg.triggerWords.any { trigger ->
                val trig = trigger.lowercase()
                clean == trig || clean == trig.replace(" ", "")
            }) {
                return egg
            }
        }
        val cleanTokens = clean.split(Regex("""[\s_\-,.]+""")).filter { it.isNotBlank() }
        for (egg in EasterEgg.entries) {
            if (egg.triggerWords.any { trigger ->
                val trig = trigger.lowercase()
                trig in cleanTokens
            }) {
                return egg
            }
        }
        return null
    }
}