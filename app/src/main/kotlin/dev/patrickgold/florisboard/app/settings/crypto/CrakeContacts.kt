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

package dev.patrickgold.florisboard.app.settings.crypto

/**
 * Labeled public keys for encrypt-in-place. Contacts hold only PUBLIC keys,
 * so they are stored as plain prefs text: one contact per line as
 * "key|label". A crake-pk1- key contains no pipe or newline, and labels are
 * single-line, so the first pipe splits cleanly.
 */
data class CrakeContact(val label: String, val key: String)

object CrakeContacts {
    fun parse(raw: String): List<CrakeContact> =
        raw.lineSequence()
            .mapNotNull { line ->
                val trimmed = line.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val pipe = trimmed.indexOf('|')
                if (pipe <= 0) return@mapNotNull null
                val key = trimmed.substring(0, pipe).trim()
                val label = trimmed.substring(pipe + 1).trim()
                if (key.isEmpty() || label.isEmpty()) null else CrakeContact(label, key)
            }
            .toList()

    fun serialize(contacts: List<CrakeContact>): String =
        contacts.joinToString("\n") { "${it.key.trim()}|${sanitizeLabel(it.label)}" }

    /** Adds or replaces a contact by key, returning the new list. */
    fun upsert(contacts: List<CrakeContact>, contact: CrakeContact): List<CrakeContact> {
        val cleaned = CrakeContact(sanitizeLabel(contact.label), contact.key.trim())
        val without = contacts.filterNot { it.key.trim() == cleaned.key }
        return without + cleaned
    }

    fun remove(contacts: List<CrakeContact>, key: String): List<CrakeContact> =
        contacts.filterNot { it.key.trim() == key.trim() }

    /** Labels are single-line; strip anything that would break the format. */
    private fun sanitizeLabel(label: String): String =
        label.replace('\n', ' ').replace('|', '/').trim()
}
