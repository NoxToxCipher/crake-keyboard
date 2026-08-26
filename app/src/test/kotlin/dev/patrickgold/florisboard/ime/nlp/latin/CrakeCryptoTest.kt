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

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import javax.crypto.KeyGenerator

/**
 * The sealing algorithm behind the learned-state files, tested with a
 * locally generated AES key (the Android Keystore only supplies the key at
 * runtime; the crypto itself is identical).
 */
class CrakeCryptoTest : FunSpec({
    fun key() = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    val aad = "crake-test-v1".toByteArray()

    test("seals and unseals a learned blob") {
        val k = key()
        val secret = "roratus meshtastic thay->that".toByteArray()
        val sealed = CrakeCrypto.seal(k, secret, aad)
        sealed.toList() shouldNotBe secret.toList()
        CrakeCrypto.unseal(k, sealed, aad)?.toList() shouldBe secret.toList()
    }

    test("every flipped bit is rejected") {
        val k = key()
        val sealed = CrakeCrypto.seal(k, "learned words".toByteArray(), aad)
        for (i in sealed.indices step 7) {
            val tampered = sealed.copyOf()
            tampered[i] = (tampered[i].toInt() xor 0x01).toByte()
            CrakeCrypto.unseal(k, tampered, aad) shouldBe null
        }
    }

    test("wrong key and wrong AAD are rejected, truncation too") {
        val k = key()
        val sealed = CrakeCrypto.seal(k, "learned words".toByteArray(), aad)
        CrakeCrypto.unseal(key(), sealed, aad) shouldBe null
        CrakeCrypto.unseal(k, sealed, "other-label".toByteArray()) shouldBe null
        CrakeCrypto.unseal(k, sealed.copyOf(8), aad) shouldBe null
        CrakeCrypto.unseal(k, ByteArray(0), aad) shouldBe null
    }

    test("identical plaintexts seal to different ciphertexts") {
        val k = key()
        val a = CrakeCrypto.seal(k, "same".toByteArray(), aad)
        val b = CrakeCrypto.seal(k, "same".toByteArray(), aad)
        a.toList() shouldNotBe b.toList()
    }
})
