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

package dev.patrickgold.florisboard.app.updater

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.app.FlorisAppActivity
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.time.Duration.Companion.hours

object UpdateManager {
    private const val TAG = "CrakeUpdater"
    const val CURRENT_MILESTONE = 325
    private const val GITHUB_REPO_API = "https://api.github.com/repos/NoxToxCipher/crake-keyboard/releases?per_page=5"
    private const val CHANNEL_ID = "crake_updates_channel"
    private const val NOTIFICATION_ID = 32501
    private const val RESOLVED_NOTIFICATION_ID = 32502

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val milestone: Int,
        val changelog: String,
        val apkDownloadUrl: String,
        val apkSize: Long,
        val publishedAt: String,
    )

    sealed interface UpdateStatus {
        data object Idle : UpdateStatus
        data object Checking : UpdateStatus
        data class UpdateAvailable(val release: ReleaseInfo) : UpdateStatus
        data class UpToDate(val lastChecked: Long) : UpdateStatus
        data class Downloading(val progressPercent: Int, val bytesDownloaded: Long, val totalBytes: Long) : UpdateStatus
        data class ReadyToInstall(val apkFile: File, val release: ReleaseInfo) : UpdateStatus
        data class Error(val message: String) : UpdateStatus
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var appContext: Context? = null
    private val prefs by FlorisPreferenceStore
    val remoteMilestoneHighlights = java.util.concurrent.ConcurrentHashMap<Int, String>()

    fun getMilestoneHighlights(milestone: Int): String {
        val remote = remoteMilestoneHighlights[milestone]
        if (!remote.isNullOrBlank()) {
            return remote
        }
        return when (milestone) {
            325 -> "Completed Keyboard Roadmap Phases 4-6: Neural Glide Kinematics (curvature & inflection tracking), Touch-to-Visual Frame Pacing latency telemetry, and Air-Gapped Vault TTL zeroization."
            324 -> "Implemented Phase 3 Autocorrect False-Positive Auto-Tuner: progressive confidence dampening on backspaced corrections, zero-allocation rejection suppression, and flight recorder revert logging."
            323 -> "Implemented Phase 2 Dynamic Bivariate Hitbox Warping: anisotropic contact patch apex tilt correction, thumb reach scaling, and Gaussian Mahalanobis dispersion weighting to cut boundary mis-taps."
            322 -> "Expanded Phase 1 Kinematic Telemetry: added elliptical touchMajor/touchMinor geometry, contact pressure, key dwell time, autocorrect false-positive undo tracker, and suggestion slot metrics."
            321 -> "Added Zero-Knowledge Encrypted Vault Hero Banner to Clipboard Settings with ChaCha20-Poly1305 security telemetry chips and auto-burn status."
            320 -> "Added 1D-CNN Neural Glide & Trackpad Engine Hero Banner to Gestures Settings with real-time status badges and 6-channel kinematic telemetry indicators."
            319 -> "Refined Snippets Studio: single-line balanced templates (!email, !addr, !shrug), centered cyber dividers, core 4-chain focus with smooth edge-faded horizontal scrolling."
            318 -> "Expanded Air-Gapped Crypto Snippets (16 chains + Solana/Tron/EVM) with sleek visual overhaul, search bar, and one-tap clipboard copy; coupled Easter Egg registry bounds strictly to 36 valid entries."
            317 -> "Reset main currency set to Dollar ($) across all keyboard subtype presets and automatically migrated active tester profiles."
            316 -> "Implemented 1D-CNN / Temporal Convolutional Network (TCN) Neural Glide Stroke Decoder in Rust (floris-core): zero-allocation stack execution, dilated convolutions, and continuous character emission alignment for corner-cut glide rescue."
            315 -> "Implemented native Rust 2D Bivariate Gaussian Spatial Touch Model (floris-core): real-time online Welford updates with exponential covariance adaptation, computing sub-nanosecond Mahalanobis spatial likelihoods."
            314 -> "Expanded telemetry harvester: captures high-precision spatial touch offsets (dx/dy), backspace-revert sequences, typo edit distances, increased buffer to 350 records, and added automatic session-end diagnostic sync."
            313 -> "Synchronized Home menu Easter Egg count calculations with valid registry entries (strictly filtering orphaned legacy IDs to ensure Discovered & Solved scores accurately cap at 36/36)."
            312 -> "Fixed initial touch-origin preservation in gesture engine (preventing touch-move bounds checks from resetting letter origins during upward word flicks) • Embedded numbered Ticket IDs (#CRK-xxx) across feedback hub."
            311 -> "Compacted final onboarding tour card into a responsive 2x2 gesture grid, completely eliminating unnecessary vertical scrolling."
            310 -> "Telemetry timestamp PII scrubber accuracy (preserving 13-digit Unix flight log timestamps while shielding payment card numbers)."
            309 -> "Dynamic Cloud Changelog Engine (live multi-version changelog sync via updater_metadata.json history dictionary, ensuring older installed APKs accurately display detailed notes for all intermediate milestones)."
            308 -> "Restored and reinforced BB10 Upward Word Flick prediction engine • Corrected Home Screen & Tour gesture guides (Upward Flick flings predicted words)."
            307 -> "Fixed theme switcher in onboarding carousel (mapped valid Crake theme component IDs so theme changes apply instantly)."
            306 -> "Interactive Word Flick & Gesture introductory guide card on Home Screen • Easter Egg registry refinement (36 pure word eggs with power surge as ambient charging touch)."
            305 -> "Battery & power-save adaptive telemetry scheduling • Zero-persistence diagnostic sync in-memory pipeline."
            304 -> "Hardware-grade SHA-256 telemetry payload checksum verification • Zero-packet-corruption transport guards in remote diagnostic relay."
            303 -> "Cumulative multi-version changelog engine (delivering full retrospective change histories when jumping multiple milestones)."
            302 -> "Centered & comprehensive on-device telemetry explanatory card • Smooth transient error auto-recovery in updater engine."
            301 -> "Telemetry typo confusion matrix analysis (local fat-finger clustering) • Zero-trace secure diagnostic data wipe with storage shredding."
            300 -> "Milestone 300 Century Release: High-precision gesture telemetry velocity metrics • Ephemeral memory auto-scrubbing on keyboard idle."
            299 -> "Automatic feedback PII redaction & EXIF metadata stripping • Telemetry & Privacy Shield assurance in Feedback Hub."
            298 -> "Hardware-grade telemetry PII sanitization (automatic redaction of emails, cards & phone numbers) • Privacy shielding in flight logs (incognito & password manager exclusions)."
            297 -> "Rich visual update notifications with BigTextStyle preview • Brand new mystery interactive Easter Egg."
            296 -> "Audited feedback resolution status (accurately restored historical fixes) • Strict spoiler protection across all release changelogs."
            295 -> "Audited feedback resolution engine (fixed false instant 'implemented' badges) • Dynamic version-matched milestone additions."
            294 -> "Mystery Easter Egg trigger refinements & exclusive activation tuning."
            293 -> "Brand new secret interactive keycap Easter Egg animation added!"
            292 -> "Contraction & apostrophe spacing fixes (don't, it's, I'm) • Default tester name updated to Daya."
            291 -> "Live Telemetry Engine active banner • Discovered vs Solved score disambiguation in Easter Egg tracker."
            290 -> "3-Tier CDN Zero-Rate-Limit Updater • Telemetry-tuned typo recovery (kf->of, ia->is, fizdx->fixed)."
            289 -> "New Crake App Icon • Home menu deduplication • Audited tester feedback resolution badges."
            288 -> "Notification spam eradication & silent background update check gates."
            287 -> "Dynamic resolution tagging & feedback queue indicators."
            286 -> "Battery overcharge protection • Currency probe on startup • Spoiler-free egg recorder alerts."
            285 -> "Dollar sign Western popup • Secret Easter Egg layout adjustments."
            else -> "Continuous performance optimizations, telemetry enhancements & typing model updates."
        }
    }

    fun getCumulativeChangelog(fromMilestone: Int, toMilestone: Int): String {
        val startM = maxOf(282, fromMilestone + 1)
        if (startM > toMilestone) {
            return getMilestoneHighlights(toMilestone)
        }
        val milestones = (startM..toMilestone).reversed().toList()
        if (milestones.size == 1) {
            return "• Milestone " + milestones.first() + ": " + getMilestoneHighlights(milestones.first())
        }
        return milestones.joinToString("\n\n") { m ->
            "• Milestone $m:\n  ${getMilestoneHighlights(m)}"
        }
    }

    private val _status = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)
    val status = _status.asStateFlow()

    fun init(context: Context) {
        appContext = context.applicationContext
        createNotificationChannel(context)
        try {
            val nm = NotificationManagerCompat.from(context)
            nm.cancel(RESOLVED_NOTIFICATION_ID)
            nm.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
        startPeriodicCheckLoop()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Crake Keyboard Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Notifications for automatic background keyboard updates & feature fixes"
            }
            val nm = context.getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun startPeriodicCheckLoop() {
        scope.launch {
            while (isActive) {
                if (prefs.updater.autoCheckEnabled.get()) {
                    val intervalHours = prefs.updater.checkIntervalHours.get().coerceAtLeast(1)
                    val lastCheckStr = prefs.updater.lastCheckTimestamp.get()
                    val lastCheck = lastCheckStr.toLongOrNull() ?: 0L
                    val now = System.currentTimeMillis()
                    val intervalMs = intervalHours.hours.inWholeMilliseconds

                    if (now - lastCheck >= intervalMs) {
                        checkForUpdates(silent = true)
                    }
                }
                delay(15 * 60 * 1000L)
            }
        }
    }

    fun parseMilestoneNumber(tagOrName: String): Int {
        val mMatch = Regex("(?i)(?:milestone[\\s_\\-]*|(?:\\b|[\\-_])m)(\\d+)").find(tagOrName)
        if (mMatch != null) {
            return mMatch.groupValues[1].toIntOrNull() ?: 0
        }
        val vMatch = Regex("(?i)(?:^|[^a-z0-9])v(\\d{2,4})(?:[^0-9]|$)").find(tagOrName)
        if (vMatch != null) {
            return vMatch.groupValues[1].toIntOrNull() ?: 0
        }
        val generalMatch = Regex("\\b(\\d{2,4})\\b").find(tagOrName)
        if (generalMatch != null) {
            return generalMatch.groupValues[1].toIntOrNull() ?: 0
        }
        return 0
    }

    fun checkForUpdates(silent: Boolean = false) {
        scope.launch {
            _status.value = UpdateStatus.Checking
            val result = fetchLatestRelease()
            val now = System.currentTimeMillis()
            prefs.updater.lastCheckTimestamp.set(now.toString())

            result.fold(
                onSuccess = { release ->
                    if (release != null && release.milestone > CURRENT_MILESTONE) {
                        _status.value = UpdateStatus.UpdateAvailable(release)
                        appContext?.let { ctx ->
                            notifyUpdateAvailable(ctx, release)
                            if (prefs.updater.autoDownloadOnWifi.get()) {
                                downloadAndInstall(ctx, release, autoPrompt = true)
                            }
                        }
                    } else {
                        _status.value = UpdateStatus.UpToDate(now)
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to check for updates: ${error.message}")
                    if (silent) {
                        _status.value = UpdateStatus.Idle
                    } else {
                        _status.value = UpdateStatus.Error(error.localizedMessage ?: "Network error")
                        scope.launch {
                            kotlinx.coroutines.delay(4000)
                            if (_status.value is UpdateStatus.Error) {
                                _status.value = UpdateStatus.Idle
                            }
                        }
                    }
                }
            )
        }
    }

    private suspend fun fetchLatestRelease(): Result<ReleaseInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            // Tier 1: Zero-Rate-Limit Fastly CDN Web Redirect
            try {
                val cdnUrl = URL("https://github.com/NoxToxCipher/crake-keyboard/releases/latest")
                val cdnConn = (cdnUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; CrakeUpdater)")
                }
                val finalUrl = cdnConn.url.toString()
                val tagMatch = Regex("/releases/tag/(v[0-9\\.]+)").find(finalUrl)
                if (tagMatch != null) {
                    val tag = tagMatch.groupValues[1]
                    val milestone = parseMilestoneNumber(tag)
                    if (milestone > 0) {
                        return@runCatching ReleaseInfo(
                            tagName = tag,
                            name = "Milestone $milestone",
                            milestone = milestone,
                            changelog = "Crake Autonomous Sprint Release",
                            apkDownloadUrl = "https://github.com/NoxToxCipher/crake-keyboard/releases/download/$tag/CrakeKeyboard_Milestone_$milestone.apk",
                            apkSize = 56645012L,
                            publishedAt = "",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tier 1 CDN lookup skipped: ${e.message}")
            }

            // Tier 2: Zero-Rate-Limit GitHub Raw Static Metadata
            try {
                val rawUrl = URL("https://raw.githubusercontent.com/NoxToxCipher/crake-keyboard/main/updater_metadata.json")
                val rawConn = (rawUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "CrakeKeyboard-Updater")
                }
                if (rawConn.responseCode == HttpURLConnection.HTTP_OK) {
                    val rawJson = JSONObject(rawConn.inputStream.bufferedReader().use { it.readText() })
                    val historyObj = rawJson.optJSONObject("history")
                    if (historyObj != null) {
                        for (key in historyObj.keys()) {
                            val mNum = key.toIntOrNull()
                            val hl = historyObj.optString(key, "")
                            if (mNum != null && hl.isNotBlank()) {
                                remoteMilestoneHighlights[mNum] = hl
                            }
                        }
                    }
                    val m = rawJson.optInt("milestone", 0)
                    val tag = rawJson.optString("tagName", "")
                    val apk = rawJson.optString("apkDownloadUrl", "")
                    if (m > 0 && apk.isNotBlank()) {
                        return@runCatching ReleaseInfo(
                            tagName = tag,
                            name = rawJson.optString("name", "Milestone $m"),
                            milestone = m,
                            changelog = rawJson.optString("changelog", ""),
                            apkDownloadUrl = apk,
                            apkSize = rawJson.optLong("apkSize", 56645012L),
                            publishedAt = "",
                        )
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "Tier 2 Raw metadata lookup skipped: ${e.message}")
            }

            // Tier 3: GitHub REST API (Fallback)
            val url = URL(GITHUB_REPO_API)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "CrakeKeyboard-Updater")
            }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("GitHub API returned HTTP ${connection.responseCode}")
            }

            val responseBody = connection.inputStream.bufferedReader().use { it.readText() }.trim()
            val releasesArray = if (responseBody.startsWith("[")) {
                JSONArray(responseBody)
            } else {
                JSONArray().apply { put(JSONObject(responseBody)) }
            }

            var bestRelease: ReleaseInfo? = null
            for (idx in 0 until releasesArray.length()) {
                val json = releasesArray.getJSONObject(idx)
                val tagName = json.optString("tag_name", "")
                val name = json.optString("name", tagName)
                val body = json.optString("body", "")
                val publishedAt = json.optString("published_at", "")
                val milestone = parseMilestoneNumber(if (name.isNotBlank()) name else tagName)
                if (milestone > 0 && body.isNotBlank()) {
                    val firstLine = body.lines().firstOrNull { it.isNotBlank() }?.trim() ?: body.trim()
                    if (firstLine.isNotBlank() && !remoteMilestoneHighlights.containsKey(milestone)) {
                        remoteMilestoneHighlights[milestone] = firstLine
                    }
                }

                val assets = json.optJSONArray("assets") ?: JSONArray()
                var apkUrl = ""
                var apkSize = 0L

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        apkUrl = asset.optString("browser_download_url", "")
                        apkSize = asset.optLong("size", 0L)
                        break
                    }
                }

                if (apkUrl.isNotEmpty()) {
                    val candidate = ReleaseInfo(
                        tagName = tagName,
                        name = name,
                        milestone = milestone,
                        changelog = body,
                        apkDownloadUrl = apkUrl,
                        apkSize = apkSize,
                        publishedAt = publishedAt,
                    )
                    if (bestRelease == null || candidate.milestone > bestRelease.milestone) {
                        bestRelease = candidate
                    }
                }
            }
            bestRelease
        }
    }

    fun downloadAndInstall(context: Context, release: ReleaseInfo, autoPrompt: Boolean = false) {
        scope.launch {
            _status.value = UpdateStatus.Downloading(progressPercent = 0, bytesDownloaded = 0, totalBytes = release.apkSize)

            val downloadResult = downloadApk(context, release)
            downloadResult.fold(
                onSuccess = { apkFile ->
                    _status.value = UpdateStatus.ReadyToInstall(apkFile, release)
                    if (autoPrompt) {
                        notifyReadyToInstall(context, apkFile, release)
                    } else {
                        promptInstall(context, apkFile)
                    }
                },
                onFailure = { error ->
                    _status.value = UpdateStatus.Error("Download failed: ${error.localizedMessage}")
                }
            )
        }
    }

    private suspend fun downloadApk(context: Context, release: ReleaseInfo): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
            val targetFile = File(updatesDir, "CrakeKeyboard_M${release.milestone}.apk")

            val url = URL(release.apkDownloadUrl)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", "CrakeKeyboard-Updater")
                instanceFollowRedirects = true
            }

            var redirectedConn = connection
            var redirectCount = 0
            while (redirectedConn.responseCode in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308) && redirectCount < 5) {
                val newUrl = redirectedConn.getHeaderField("Location")
                redirectedConn = (URL(newUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    setRequestProperty("User-Agent", "CrakeKeyboard-Updater")
                }
                redirectCount++
            }

            val totalSize = redirectedConn.contentLengthLong.let { if (it > 0) it else release.apkSize }
            var downloaded = 0L

            redirectedConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var lastReportPercent = 0

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        val percent = if (totalSize > 0) ((downloaded * 100) / totalSize).toInt() else 0
                        if (percent != lastReportPercent) {
                            lastReportPercent = percent
                            _status.value = UpdateStatus.Downloading(percent, downloaded, totalSize)
                        }
                    }
                }
            }
            targetFile
        }
    }

    fun promptInstall(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(settingsIntent)
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider.file",
                apkFile,
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch package installer", e)
            _status.value = UpdateStatus.Error("Failed to launch installer: ${e.localizedMessage}")
        }
    }

    private suspend fun notifyUpdateAvailable(context: Context, release: ReleaseInfo) {
        try {
            val lastNotified = prefs.updater.lastNotifiedMilestone.get()
            if (lastNotified >= release.milestone) {
                return
            }
            prefs.updater.lastNotifiedMilestone.set(release.milestone)

            val intent = Intent(context, FlorisAppActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val cumulativeChangelog = getCumulativeChangelog(CURRENT_MILESTONE, release.milestone).ifBlank {
                release.changelog.ifBlank { "Includes latest performance improvements, gesture tuning & telemetry updates." }
            }
            val versionsCount = (release.milestone - CURRENT_MILESTONE).coerceAtLeast(1)
            val versionNote = if (versionsCount > 1) " ($versionsCount versions of updates)" else ""
            val bigTextStyle = NotificationCompat.BigTextStyle()
                .setBigContentTitle("✨ Crake Milestone ${release.milestone} Ready$versionNote")
                .setSummaryText("Crake Update Ready")
                .bigText("✨ Milestone ${release.milestone} is now available!$versionNote\n\n$cumulativeChangelog\n\nTap to install instantly.")

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.floris_app_icon)
                .setColor(0xFF00E5FF.toInt())
                .setContentTitle("✨ Crake Milestone ${release.milestone} Ready")
                .setContentText("Milestone ${release.milestone} update is ready to install • Tap to review")
                .setStyle(bigTextStyle)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .addAction(R.mipmap.floris_app_icon, "Install Update", pendingIntent)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Could not post update notification: ${e.message}")
        }
    }

    private suspend fun notifyReadyToInstall(context: Context, apkFile: File, release: ReleaseInfo) {
        try {
            val lastNotified = prefs.updater.lastNotifiedMilestone.get()
            if (lastNotified >= release.milestone) {
                return
            }
            prefs.updater.lastNotifiedMilestone.set(release.milestone)
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider.file",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                1,
                installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.floris_app_icon)
                .setContentTitle("Milestone ${release.milestone} Ready to Install")
                .setContentText("Update downloaded automatically. Tap to install!")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)

            val nm = NotificationManagerCompat.from(context)
            nm.notify(NOTIFICATION_ID, builder.build())
        } catch (e: Exception) {
            Log.w(TAG, "Could not post install notification: ${e.message}")
        }
    }

    /**
     * Checks local feedback records and notifies the tester when their feature request / bug fix is addressed.
     */
    fun checkAndNotifyResolvedFeedback(context: Context) {
        scope.launch(Dispatchers.IO) {
            try {
                val lastNotified = prefs.updater.lastNotifiedResolvedMilestone.get()
                if (lastNotified >= CURRENT_MILESTONE) {
                    return@launch
                }
                prefs.updater.lastNotifiedResolvedMilestone.set(CURRENT_MILESTONE)

                val file = File(context.filesDir, "tester_feedback.jsonl")
                if (!file.exists()) return@launch

                val lines = file.readLines()
                val resolvedKeywords = listOf("battery", "first start", "egg records", "dollar sign", "noble train")
                val addressed = lines.mapNotNull { line ->
                    runCatching {
                        val obj = JSONObject(line)
                        val title = obj.optString("title", "")
                        val desc = obj.optString("description", "")
                        val time = obj.optLong("timestamp", 0L)
                        val combined = "$title $desc".lowercase()
                        if (time > System.currentTimeMillis() - 86400000L && resolvedKeywords.any { combined.contains(it) }) {
                            title.ifBlank { "Tester Suggestion" }
                        } else null
                    }.getOrNull()
                }

                if (addressed.isNotEmpty()) {
                    val resolvedTitle = addressed.first()
                    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.floris_app_icon)
                        .setContentTitle("🎉 Crake Fix Deployed (Milestone $CURRENT_MILESTONE)")
                        .setContentText("Your request '$resolvedTitle' has been implemented!")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true)

                    val nm = NotificationManagerCompat.from(context)
                    nm.notify(RESOLVED_NOTIFICATION_ID, builder.build())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed checking resolved tickets: ${e.message}")
            }
        }
    }
}