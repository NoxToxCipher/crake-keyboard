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

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    suspend fun transmitFeedback(
        testerName: String,
        category: String,
        title: String,
        jsonPayload: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Remote telemetry is disabled; feedback stays on-device.")
        Result.failure(UnsupportedOperationException("Remote telemetry disabled: nothing is transmitted off-device."))
    }

    suspend fun transmitDiagnosticBundle(
        testerName: String,
        recordCount: Int,
        jsonBundle: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.i(TAG, "Remote telemetry is disabled; diagnostic bundle stays on-device.")
        Result.failure(UnsupportedOperationException("Remote telemetry disabled: nothing is transmitted off-device."))
    }
}
