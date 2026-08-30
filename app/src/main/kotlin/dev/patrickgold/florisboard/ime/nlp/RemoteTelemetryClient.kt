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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight HTTPS client for transmitting tester feedback and 20-minute diagnostic
 * sync bundles directly to the development AI assistant over the internet.
 *
 * Fully wireless, zero-config, works over cellular & Wi-Fi without needing USB/ADB cables.
 */
object RemoteTelemetryClient {
    private const val TAG = "CrakeRemoteTelemetry"
    const val TELEMETRY_URL = "https://ntfy.sh/crake_sprint_telemetry_2026_noxtox"

    suspend fun transmitFeedback(
        testerName: String,
        category: String,
        title: String,
        jsonPayload: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(TELEMETRY_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Title", "[$category] $title ($testerName)")
                setRequestProperty("Tags", "bulb,speech_balloon")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Log.i(TAG, "Feedback transmitted successfully over HTTPS (HTTP $code)")
                Unit
            } else {
                error("HTTP $code response from telemetry relay")
            }
        }
    }

    suspend fun transmitDiagnosticBundle(
        testerName: String,
        recordCount: Int,
        jsonBundle: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(TELEMETRY_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Title", "[DiagSync] $testerName ($recordCount records)")
                setRequestProperty("Tags", "chart_with_upwards_trend,satellite")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBundle)
                writer.flush()
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                Log.i(TAG, "Diagnostic bundle transmitted successfully over HTTPS (HTTP $code)")
                Unit
            } else {
                error("HTTP $code response from telemetry relay")
            }
        }
    }
}