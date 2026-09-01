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

import dev.patrickgold.florisboard.ime.nlp.latin.LearnedStateStore
import org.florisboard.libnative.FlorisNative
import java.io.File

/**
 * Holds this device's public-key identity for encrypt-in-place. The private
 * key is generated on-device, kept sealed at rest by the Android Keystore
 * (via LearnedStateStore), and never displayed or exported by default - only
 * the shareable public key leaves. The same identity is used by the in-place
 * keyboard action, so it lives outside any one screen.
 */
class CrakeIdentityStore(filesDir: File) {
    // Blends with the app's other crake_*.enc files.
    private val store = LearnedStateStore(filesDir, "crake_identity.crkid")

    /** The stored private key hex, or null if no identity exists yet. */
    fun privateKeyHex(): String? {
        val bytes = store.load() ?: return null
        val hex = bytes.toString(Charsets.UTF_8).trim()
        return hex.ifEmpty { null }
    }

    /** The shareable public key (crake-pk1-...), generating an identity on first use. */
    fun publicKey(): String? {
        val priv = privateKeyHex() ?: run {
            if (!generate()) return null
            privateKeyHex() ?: return null
        }
        return FlorisNative.cryptoDerivePublic(priv)
    }

    /** True if an identity already exists. */
    fun exists(): Boolean = privateKeyHex() != null

    /**
     * Generates and stores a new identity, replacing any existing one.
     * Returns false if the native layer is unavailable (nothing is changed).
     */
    fun generate(): Boolean {
        val kp = FlorisNative.cryptoGenerateKeypair() ?: return false
        store.save(kp.first.toByteArray(Charsets.UTF_8))
        return true
    }
}
