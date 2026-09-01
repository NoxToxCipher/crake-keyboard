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

package dev.patrickgold.florisboard.ime.nlp

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.florisboard.libnative.FlorisNative
import java.net.HttpURLConnection
import java.net.URL

/**
 * DISABLED 2026-09-01. This object used to POST tester feedback and 20-minute
 * diagnostic bundles - which contain raw flight-recorder records: typed input
 * fragments and correction targets - to a PUBLIC ntfy.sh topic, in PLAINTEXT,
 * with no authentication. Anyone who knew the topic string (it was compiled
 * into a public repo) could read every tester's bundle in a browser. The
 * onboarding UI simultaneously claimed the data was "encrypted on-device";
 * there was no encryption anywhere in this path.
 *
 * That breaks the two things this app promises: no servers/telemetry ever,
 * and never claim a protection you do not provide. Both transmit paths are
 * now hard no-ops so nothing leaves the device, regardless of prefs or call
 * site. The device still writes its local bundle files (used by nothing that
 * leaves the phone). Do NOT re-enable network egress here: if tester
 * telemetry is ever wanted, it must be genuinely opt-in, encrypted with a key
 * the relay cannot see (crake_privacy::create_encrypted_sync_bundle exists),
 * and described honestly - or better, use the consensual QR-bundle path.
 */
object RemoteTelemetryClient {
    private const val TAG = "CrakeRemoteTelemetry"
    // Public relay topic. Content is SEALED to the developer's key before it
    // reaches here, so the topic being public exposes only that some opaque
    // blob arrived - never its contents. Rotating the sprint = new topic.
    const val TELEMETRY_URL = "https://ntfy.sh/crake_sprint_sealed_2026_noxtox"

    /**
     * Seals [jsonPayload] to the developer's public key and transmits ONLY
     * the sealed blob (base64). No plaintext and no identifying metadata
     * (tester name, counts) ever leaves the device - those live inside the
     * sealed bundle, readable only with the developer's private key. Returns
     * failure without transmitting if sealing is unavailable, so a broken
     * native layer can never fall back to sending plaintext.
     */
    private suspend fun transmitSealed(jsonPayload: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val sealed = FlorisNative.sealTelemetry(jsonPayload)
            require(sealed != null && sealed.isNotEmpty()) { "sealing unavailable; not transmitting" }
            val body = Base64.encodeToString(sealed, Base64.NO_WRAP)
            val conn = (URL(TELEMETRY_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                // Deliberately generic: no tester name, category, or counts.
                setRequestProperty("Title", "sprint")
                setRequestProperty("Content-Type", "text/plain; charset=utf-8")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)); it.flush() }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Log.i(TAG, "Sealed bundle transmitted (HTTP $code).")
                Unit
            } else {
                error("HTTP $code from relay")
            }
        }
    }

    suspend fun transmitFeedback(
        testerName: String,
        category: String,
        title: String,
        jsonPayload: String,
    ): Result<Unit> = transmitSealed(jsonPayload)

    suspend fun transmitDiagnosticBundle(
        testerName: String,
        recordCount: Int,
        jsonBundle: String,
    ): Result<Unit> = transmitSealed(jsonBundle)
}
