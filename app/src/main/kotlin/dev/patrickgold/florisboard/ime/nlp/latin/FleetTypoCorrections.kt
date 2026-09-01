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
 *    rewrites a real token is corpus poison.
 *
 * Lives outside the provider so FleetTypoCorrectionsTest pins the REAL map;
 * the previous test asserted a private copy of itself and guarded nothing.
 */
object FleetTypoCorrections {
    val MAP: Map<String, String> = mapOf(
        "toi" to "you",
        "ckrdsct" to "correct",
        "iodated" to "updated",
        "phr" to "put",
        "fizdx" to "fixed",
        "aure" to "sure",
        "ghe" to "the",
        "becahsd" to "because",
        "ifs" to "it's",
        "adn" to "and",
        "teh" to "the",
        "taht" to "that",
        "waht" to "what",
        "thsi" to "this",
        "thier" to "their",
        "widt" to "with",
        "rhjs" to "this",
        "jat" to "that",
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
        "beither" to "brother",
        "ttoing" to "typing",
        "hsing" to "using",
        "oerson" to "person",
        "keybaord" to "keyboard",
        "ans" to "and",
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
        "cant" to "can't",
        "wont" to "won't",
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
    )
}
