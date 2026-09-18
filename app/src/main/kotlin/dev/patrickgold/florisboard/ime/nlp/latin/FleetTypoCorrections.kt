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

package dev.patrickgold.florisboard.ime.nlp.latin

/**
 * The fleet typo hard map: a typed token on the left is offered as the word
 * on the right ahead of the engine's fuzzy search. Ingestion rules, learned
 * the hard way across audits:
 *
 *  - every entry must have a keyboard mechanism (neighbor slips,
 *    transpositions, dropped letters) - no mechanism, no entry;
 *  - the left side must not be a real word, standing abbreviation, or
 *    formal term. "iff" (if-and-only-if), "thks" (thanks), and "hwy"
 *    (highway) have all been removed on this rule - a hard map that
 *    rewrites a real token is corpus poison;
 *  - nor one slip away from a DIFFERENT common word than the one on the
 *    right: "jat" (hat/jar), "beither" (neither/either) and "widt" (width)
 *    were removed on this rule (hunt 2026-09-18) - the engine's slip model
 *    resolves those better than a fixed answer can.
 *
 * Since the 2026-09-18 hunt the provider also skips an entry whenever the
 * typed token is a word the native trie knows (shipped, learned from a
 * backspace revert, or in the personal dictionary), so the map can no
 * longer rewrite a real word and one revert switches an entry off. Keys the
 * engine already owns were dropped at the same time: "ifs" and "ans" (real
 * words), "toi" (shipped junk-band token), "cant"/"wont" (the contraction
 * stage restores the apostrophe). The remaining bare-contraction keys
 * (dont, didnt, isnt, ...) are shipped words too, so the gate defers them
 * to the same contraction stage; they now matter only if native is absent.
 *
 * Lives outside the provider so FleetTypoCorrectionsTest pins the REAL map;
 * the previous test asserted a private copy of itself and guarded nothing.
 */
object FleetTypoCorrections {
    val MAP: Map<String, String> = mapOf(
        "ckrdsct" to "correct",
        "iodated" to "updated",
        "phr" to "put",
        "fizdx" to "fixed",
        "aure" to "sure",
        "ghe" to "the",
        "becahsd" to "because",
        "adn" to "and",
        "teh" to "the",
        "taht" to "that",
        "waht" to "what",
        "thsi" to "this",
        "thier" to "their",
        "rhjs" to "this",
        "dobe" to "done",
        "thid" to "this",
        "whag" to "what",
        // Live specimen 2026-09-01: five adjacent-key slips in one long
        // word (n->m, o->i, a->s + transposition), beyond the fuzzy edit
        // budget for len 13 - exactly what the hard map is for.
        "mitificsitons" to "notifications",
        "actuly" to "actually",
        "actully" to "actually",
        "trigh" to "right",
        "tought" to "thought",
        "thoght" to "thought",
        "whcih" to "which",
        "becasue" to "because",
        "definitly" to "definitely",
        "definately" to "definitely",
        "seperate" to "separate",
        "occured" to "occurred",
        "untill" to "until",
        "realy" to "really",
        "downaloded" to "downloaded",
        "downlaoded" to "downloaded",
        "ttoing" to "typing",
        "hsing" to "using",
        "oerson" to "person",
        "keybaord" to "keyboard",
        "wjatsapp" to "WhatsApp",
        "whatssapp" to "WhatsApp",
        "watsapp" to "WhatsApp",
        "anorhwr" to "another",
        "anotehr" to "another",
        "anohter" to "another",
        "anothr" to "another",
        "telemetr" to "telemetry",
        "diffcult" to "difficult",
        "difficut" to "difficult",
        "encryted" to "encrypted",
        "encrpyted" to "encrypted",
        "soemthing" to "something",
        "appliaction" to "application",
        "messag" to "message",
        "recieve" to "receive",
        "recieved" to "received",
        "dont" to "don't",
        "didnt" to "didn't",
        "isnt" to "isn't",
        "arent" to "aren't",
        "couldnt" to "couldn't",
        "shouldnt" to "shouldn't",
        "wouldnt" to "wouldn't",
        "securtiy" to "security",
        "secuirty" to "security",
        "sceret" to "secret",
        "screet" to "secret",
        "gestrue" to "gesture",
        "gestue" to "gesture",
        "smooht" to "smooth",
        "pysics" to "physics",
        "noteapd" to "notepad",
        "glidinf" to "gliding",
        "glidign" to "gliding",
        "acvurare" to "accurate",
        "accurte" to "accurate",
        "accurat" to "accurate",
        "learing" to "learning",
        "machne" to "machine",
        "telemtry" to "telemetry",
        "telemtrics" to "telemetrics",
        "telemterics" to "telemetrics",
        "encryt" to "encrypt",
        "encrpyt" to "encrypt",
        "encrytion" to "encryption",
        "encrpytion" to "encryption",
        "notificaiton" to "notification",
        "notificaitons" to "notifications",
        "notifcations" to "notifications",
        "dynmaic" to "dynamic",
        "islnad" to "island",
        "isaldn" to "island",
        "capsul" to "capsule",
        "capusle" to "capsule",
        "firign" to "firing",
        "firinf" to "firing",
        "confirmaton" to "confirmation",
        "confiramtion" to "confirmation",
        "reciepient" to "recipient",
        "recipent" to "recipient",
        "xaiomi" to "Xiaomi",
        "xiaomoi" to "Xiaomi",
        "haptcis" to "haptics",
        "hapticx" to "haptics",
        "physcial" to "physical",
        "interactiv" to "interactive",
        "interactivty" to "interactivity",
        "recompositon" to "recomposition",
        "recompoistion" to "recomposition",
        "morhping" to "morphing",
        "moprh" to "morph",
        "fluidty" to "fluidity",
    )
}
