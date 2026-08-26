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

package dev.patrickgold.florisboard.ime.nlp.latin

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * AES/GCM sealing for the learned-state blobs. Pure crypto lives here with
 * the key INJECTED, so the algorithm is unit-testable on the JVM; only
 * [LearnedStateStore] talks to the Android Keystore.
 *
 * Wire format: 12-byte random IV || GCM ciphertext+tag. The format label is
 * bound as AAD so a sealed blob cannot be replayed as some other file kind.
 */
object CrakeCrypto {
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    fun seal(key: SecretKey, plaintext: ByteArray, aad: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher.updateAAD(aad)
        val iv = cipher.iv
        check(iv.size == IV_LEN) { "unexpected IV length ${iv.size}" }
        return iv + cipher.doFinal(plaintext)
    }

    /** Returns null for anything that does not authenticate. */
    fun unseal(key: SecretKey, sealed: ByteArray, aad: ByteArray): ByteArray? {
        if (sealed.size <= IV_LEN) return null
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, sealed, 0, IV_LEN))
            cipher.updateAAD(aad)
            cipher.doFinal(sealed, IV_LEN, sealed.size - IV_LEN)
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Persistence for learned-state blobs, sealed with a hardware-backed
 * Android Keystore key (generated on first use, never leaves the device).
 *
 * Legacy migration: iteration-18 builds wrote plaintext files. On load,
 * the sealed file wins; if only the legacy plaintext exists it is read
 * once, and the next save writes sealed and deletes the plaintext.
 */
class LearnedStateStore(private val filesDir: File, private val name: String) {
    private val sealedFile get() = File(filesDir, "$name.enc")
    private val legacyFile get() = File(filesDir, name)
    private val aad get() = "crake-$name-v1".toByteArray()

    fun load(): ByteArray? {
        try {
            if (sealedFile.exists()) {
                return CrakeCrypto.unseal(key(), sealedFile.readBytes(), aad)
            }
            if (legacyFile.exists()) {
                return legacyFile.readBytes()
            }
        } catch (_: Exception) {
        }
        return null
    }

    fun save(data: ByteArray) {
        try {
            sealedFile.writeBytes(CrakeCrypto.seal(key(), data, aad))
            if (legacyFile.exists()) {
                legacyFile.delete()
            }
        } catch (_: Exception) {
            // Never let persistence failures reach the typing path.
        }
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "crake_learned_state"
    }
}
