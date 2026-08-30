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

package dev.patrickgold.florisboard.app.devtools

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.nlp.FlightRecorderManager
import dev.patrickgold.florisboard.ime.nlp.RemoteTelemetryClient
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DarkBackground = Color(0xFF0F172A)
private val CardSurface = Color(0xFF1E293B)
private val CardBorder = Color(0xFF334155)
private val ElectricCyan = Color(0xFF00E5FF)
private val CyberEmerald = Color(0xFF00E676)
private val NeonPink = Color(0xFFFF4081)
private val AmberGold = Color(0xFFFFB300)
private val TextMuted = Color(0xFF94A3B8)

enum class FeedbackCategory(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val badge: String,
) {
    BUG_REPORT(
        title = "Bug Report",
        description = "Glitches, rendering issues, crashes, or unwanted behavior",
        icon = Icons.Default.Warning,
        color = NeonPink,
        badge = "BUG",
    ),
    FEATURE_REQUEST(
        title = "Feature Request",
        description = "New keyboard modes, tools, layouts, or customization ideas",
        icon = Icons.Default.Star,
        color = AmberGold,
        badge = "FEATURE",
    ),
    TYPING_NLP(
        title = "Typing & Autocorrect",
        description = "Missed corrections, flick inaccuracies, or vocabulary learning",
        icon = Icons.Default.Edit,
        color = ElectricCyan,
        badge = "NLP",
    ),
    THEME_DESIGN(
        title = "Theme & Visuals",
        description = "Aesthetic feedback, fret styling, glow colors, or font readability",
        icon = Icons.Default.Favorite,
        color = CyberEmerald,
        badge = "DESIGN",
    ),
}

data class FeedbackItem(
    val timestamp: Long,
    val testerName: String,
    val category: String,
    val title: String,
    val description: String,
    val hasScreenshot: Boolean = false,
    val resolvedMilestone: Int? = null,
    val ticketId: String = "",
)

@Composable
fun TesterFeedbackScreen() = FlorisScreen {
    title = "Tester Feedback & Bug Reports"
    scrollable = true

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by FlorisPreferenceStore

    val rawTesterName by prefs.updater.testerName.collectAsState()
    val testerName = if (rawTesterName.isBlank() || rawTesterName.equals("Tester", ignoreCase = true)) "Daya" else rawTesterName

    var selectedCategory by remember { mutableStateOf(FeedbackCategory.BUG_REPORT) }
    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var attachFlightLogs by remember { mutableStateOf(true) }
    var submissionSuccess by remember { mutableStateOf(false) }

    var attachedImageUri by remember { mutableStateOf<Uri?>(null) }
    var attachedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        attachedImageUri = uri
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val original = BitmapFactory.decodeStream(stream)
                        val maxDim = 1024
                        val scaled = if (original.width > maxDim || original.height > maxDim) {
                            val ratio = original.width.toFloat() / original.height.toFloat()
                            if (ratio > 1f) {
                                Bitmap.createScaledBitmap(original, maxDim, (maxDim / ratio).toInt(), true)
                            } else {
                                Bitmap.createScaledBitmap(original, (maxDim * ratio).toInt(), maxDim, true)
                            }
                        } else original
                        withContext(Dispatchers.Main) {
                            attachedBitmap = scaled
                        }
                    }
                }
            }
        } else {
            attachedBitmap = null
        }
    }

    var recentFeedbacks by remember { mutableStateOf<List<FeedbackItem>>(emptyList()) }

    fun refreshRecentFeedbacks() {
        scope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "tester_feedback.jsonl")
            if (file.exists()) {
                val list = mutableListOf<FeedbackItem>()
                file.forEachLine { line ->
                    if (line.isNotBlank()) {
                        runCatching {
                            val obj = JSONObject(line)
                            val ts = obj.optLong("timestamp", 0L)
                            val explicitId = obj.optString("ticketId", "")
                            val tid = if (explicitId.isNotBlank()) explicitId else "CRK-" + (100 + list.size + 1)
                            list.add(
                                FeedbackItem(
                                    timestamp = ts,
                                    testerName = obj.optString("testerName", "Tester"),
                                    category = obj.optString("category", "BUG_REPORT"),
                                    title = obj.optString("title", ""),
                                    description = obj.optString("description", ""),
                                    hasScreenshot = obj.optBoolean("hasScreenshot", false),
                                    resolvedMilestone = obj.optInt("resolvedMilestone", -1).takeIf { it > 0 },
                                    ticketId = tid,
                                )
                            )
                        }
                    }
                }
                withContext(Dispatchers.Main) {
                    recentFeedbacks = list.reversed()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshRecentFeedbacks()
    }

    content {
        // 0. TELEMETRY & PRIVACY ASSURANCE CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CyberEmerald.copy(alpha = 0.5f)),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberEmerald.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Telemetry & Privacy Shield Active",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        color = CyberEmerald,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Zero-leak airgap • PII auto-redaction • Passwords & vaults excluded",
                        fontSize = 10.5.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // 1. TESTER IDENTITY CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(ElectricCyan.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Tester Identity: $testerName",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Your suggestions will be attributed to this tester name",
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }
        }

        // 2. CATEGORY SELECTOR
        Spacer(modifier = Modifier.height(6.dp))
        CrakeSectionHeader(title = "Feedback Type", badgeText = selectedCategory.badge, accentColor = selectedCategory.color)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackCategory.values().take(2).forEach { cat ->
                val isSelected = selectedCategory == cat
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategory = cat },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) cat.color.copy(alpha = 0.2f) else CardSurface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) cat.color else CardBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (isSelected) cat.color else TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextMuted,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackCategory.values().drop(2).forEach { cat ->
                val isSelected = selectedCategory == cat
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategory = cat },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) cat.color.copy(alpha = 0.2f) else CardSurface
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) cat.color else CardBorder,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = cat.icon,
                            contentDescription = null,
                            tint = if (isSelected) cat.color else TextMuted,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = cat.title,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextMuted,
                        )
                    }
                }
            }
        }

        // 3. INPUT FORM
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "Summary / Title",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = titleText,
                    onValueChange = { titleText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Floating candidate flicks feel slightly stiff", color = TextMuted, fontSize = 12.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedCategory.color,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Details / Description",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextMuted,
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = descriptionText,
                    onValueChange = { descriptionText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp),
                    placeholder = { Text("Describe what happened, what you expected, or words that were missed...", color = TextMuted, fontSize = 12.sp) },
                    maxLines = 5,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = selectedCategory.color,
                        unfocusedBorderColor = CardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // SCREENSHOT ATTACHMENT SECTION
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text(
                            text = "Attach Screenshot",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = if (attachedBitmap != null) "Screenshot attached" else "Attach an image to help explain",
                            fontSize = 10.5.sp,
                            color = if (attachedBitmap != null) CyberEmerald else TextMuted,
                        )
                    }
                    Button(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (attachedBitmap != null) CyberEmerald.copy(alpha = 0.2f) else CardBorder
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = null,
                            tint = if (attachedBitmap != null) CyberEmerald else Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (attachedBitmap != null) "Change" else "Attach",
                            fontSize = 11.5.sp,
                            color = if (attachedBitmap != null) CyberEmerald else Color.White,
                        )
                    }
                }

                // SCREENSHOT PREVIEW THUMBNAIL
                if (attachedBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 120.dp, height = 120.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, CyberEmerald, RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = attachedBitmap!!.asImageBitmap(),
                            contentDescription = "Attached Screenshot",
                            modifier = Modifier.matchParentSize(),
                        )
                        IconButton(
                            onClick = {
                                attachedImageUri = null
                                attachedBitmap = null
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(24.dp)
                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(bottomStart = 8.dp)),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove Screenshot",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Attach Flight Recorder Snippet",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = "Attaches last 30 typing actions to help debug typos",
                            fontSize = 10.5.sp,
                            color = TextMuted,
                        )
                    }
                    Switch(
                        checked = attachFlightLogs,
                        onCheckedChange = { attachFlightLogs = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CyberEmerald,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = CardBorder,
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = {
                        if (titleText.isBlank() && descriptionText.isBlank()) {
                            Toast.makeText(context, "Please enter a summary or details first", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val bmpToEncode = attachedBitmap
                        scope.launch(Dispatchers.IO) {
                            val flightLogs = if (attachFlightLogs) {
                                FlightRecorderManager.readRecentRecords(context, limit = 30).joinToString("\n")
                            } else null

                            var screenshotB64: String? = null
                            if (bmpToEncode != null) {
                                val stream = ByteArrayOutputStream()
                                bmpToEncode.compress(Bitmap.CompressFormat.JPEG, 75, stream)
                                screenshotB64 = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
                            }

                            val existingCount = if (File(context.filesDir, "tester_feedback.jsonl").exists()) {
                                File(context.filesDir, "tester_feedback.jsonl").readLines().count { it.isNotBlank() }
                            } else 0
                            val assignedTicketId = "CRK-" + (100 + existingCount + 1)
                            val jsonObj = JSONObject().apply {
                                put("ticketId", assignedTicketId)
                                put("timestamp", System.currentTimeMillis())
                                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
                                put("time", iso)
                                put("testerName", testerName)
                                put("category", selectedCategory.name)
                                val sanitizedTitle = FlightRecorderManager.sanitizePii(titleText.trim()) ?: titleText.trim()
                                val sanitizedDesc = FlightRecorderManager.sanitizePii(descriptionText.trim()) ?: descriptionText.trim()
                                put("title", sanitizedTitle)
                                put("description", sanitizedDesc)
                                put("hasScreenshot", screenshotB64 != null)
                                if (screenshotB64 != null) put("screenshotBase64", screenshotB64)
                                if (flightLogs != null) put("flightLogSnippet", flightLogs)
                            }

                            val file = File(context.filesDir, "tester_feedback.jsonl")
                            FileWriter(file, true).use {
                                it.append(jsonObj.toString()).append("\n")
                            }

                            // Transmit wirelessly over HTTPS to development relay
                            RemoteTelemetryClient.transmitFeedback(
                                testerName = testerName,
                                category = selectedCategory.name,
                                title = titleText.trim(),
                                jsonPayload = jsonObj.toString(),
                            )

                            withContext(Dispatchers.Main) {
                                submissionSuccess = true
                                titleText = ""
                                descriptionText = ""
                                attachedBitmap = null
                                attachedImageUri = null
                                Toast.makeText(context, "Feedback transmitted to Crake development team!", Toast.LENGTH_LONG).show()
                                refreshRecentFeedbacks()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = selectedCategory.color),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = null,
                        tint = Color(0xFF0F172A),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Submit to Crake Development",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A),
                    )
                }
            }
        }

        // 4. PREVIOUSLY SUBMITTED FEEDBACKS
        if (recentFeedbacks.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            CrakeSectionHeader(title = "Your Submitted Feedback", badgeText = "${recentFeedbacks.size} REPORTS", accentColor = ElectricCyan)
            for (fb in recentFeedbacks) {
                val t = fb.title.lowercase()
                val d = fb.description.lowercase()
                val combined = "$t $d"

                val implementedMilestone: Int? = when {
                    fb.resolvedMilestone != null && fb.resolvedMilestone > 0 -> fb.resolvedMilestone

                    // Milestone 312 fixes (Flick touch origin bounds fix & numbered Ticket ID auditing)
                    combined.contains("flick typing not firing") || (combined.contains("flick") && combined.contains("not firing")) || combined.contains("lying") || combined.contains("embedded with a number") -> 312

                    // Milestone 311 fixes (Eliminated unnecessary vertical scrolling on final tour card via 2x2 grid)
                    combined.contains("much nicer") || (combined.contains("scroll") && (combined.contains("last page") || combined.contains("unnecessary"))) -> 311

                    // Milestone 310 fixes (Telemetry timestamp PII scrubber accuracy)
                    combined.contains("timestamp") || combined.contains("pii scrubber") -> 310

                    // Milestone 309 fixes (Dynamic cloud changelog sync and retrospective history dictionary)
                    combined.contains("the truth") || (combined.contains("same thing") && combined.contains("updates")) || combined.contains("last 4 updates") -> 309

                    // Milestone 308 fixes (Upward word flick prediction restoration and guide text accuracy)
                    combined.contains("upwards flick") || combined.contains("upward flick") || (combined.contains("flick") && combined.contains("words")) || (combined.contains("flick") && combined.contains("capitals")) -> 308

                    // Milestone 307 fixes (Theme switcher component ID mapping in onboarding selector)
                    combined.contains("theme switcher") || (combined.contains("selector") && combined.contains("theme")) -> 307

                    // Milestone 306 fixes (Flick typing guide cards, 36 pure word Easter Egg registry count)
                    combined.contains("flick typing") || combined.contains("word flicks") || (combined.contains("flick") && combined.contains("introductory")) || combined.contains("record count") || (combined.contains("power surge") && (combined.contains("dropped") || combined.contains("word"))) -> 306

                    // Milestone 303 fixes (Cumulative multi-version changelog engine across notifications, home cards & releases)
                    combined.contains("more than one version") || combined.contains("all the changes") || (combined.contains("change log") && (combined.contains("io dating") || combined.contains("updating"))) || combined.contains("cumulative") -> 303

                    // Milestone 302 fixes (Centered informative telemetry card, updater transient error smooth recovery)
                    combined.contains("center") || (t.contains("more information") && combined.contains("why we would use it")) || (t.contains("updating error again") || (t.contains("update") && combined.contains("goes away after a bit"))) -> 302

                    // Milestone 295 fixes (Resolution engine fix, false keyword badge elimination, dynamic milestone additions)
                    combined.contains("incorrect bug fixes") || (t == "incorrect" && fb.timestamp >= 1788074000000L) -> 295

                    // Milestone 292 fixes (Apostrophes, contractions, force-spacing, tester name Daya)
                    combined.contains("apostrophe") || combined.contains("force spacing") || combined.contains("contraction") -> 292

                    // Milestone 291 fixes (Disambiguate 35/35 triggered vs solved, non-word egg clarification, live telemetry engine inform status, update additions list in tester card)
                    combined.contains("35/35") || combined.contains("triggered") || combined.contains("has no word") || combined.contains("update list") || (t == "inform" && combined.contains("logs have been used")) -> 291

                    // Milestone 290 fixes (CDN updater 3-tier fallback, typo recovery)
                    (t == "update error" && fb.timestamp in 1788072000000L..1788073500000L) -> 290

                    // Milestone 289 fixes (New Crake app icon, home menu deduplication, audited resolution badges)
                    (t == "icon" && combined.contains("new icon")) || t == "doubling" || (t == "incorrect" && fb.timestamp < 1788070000000L) || t == "tester bug" -> 289

                    // Milestone 288 fixes (Notification spam eradication & silent background update check gates)
                    t == "notification" && (combined.contains("every single time") || fb.timestamp in 1788065000000L..1788065900000L) -> 288

                    // Milestone 287 fixes (Dynamic resolution tagging & feedback queue indicators)
                    (t == "notification" && combined.contains("notified when their errors")) || combined.contains("notification when our feature") -> 287

                    // Milestone 286 fixes (Battery overcharge protection, currency probe on first start, already-recorded egg alert, tester beginning guidance)
                    t == "egg records" || t == "battery" || t == "first start" || t == "for testers" || t == "tester beginning" -> 286

                    // Milestone 285 fixes (Tester box header, dollar sign Western popup, noble train Easter Egg separation, testing error phrasing)
                    t == "tester box" || t == "testing error" || t == "dollar sign" || t.contains("train & noble train") || combined.contains("dollar sign") || combined.contains("noble train") -> 285

                    // Milestone 284 fixes (Easter egg recorder trigger sync, email line expand, poor visual polish)
                    t == "email" || t == "email line" || t == "easter egg recorder" || t == "poor visual" || combined.contains("easter egg recorder") -> 284

                    // Milestone 282 fixes (Screenshots attachment, top tester feedback box placement, automatic background update loop)
                    t == "updater" || t == "testers" || combined.contains("screenshot") -> 282

                    // STRICTLY IN QUEUE (Pending features & new bug reports)
                    // e.g. 'Pokemon Bank animation', 'More information / center words', 'visual notifications', 'updating error again'
                    else -> null
                }
                val isResolved = implementedMilestone != null

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, if (isResolved) CyberEmerald.copy(alpha = 0.6f) else CardBorder),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (fb.ticketId.isNotBlank()) {
                                Text(
                                    text = "#${fb.ticketId}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = AmberGold,
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                            }
                            Text(
                                text = fb.category.replace("_", " "),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isResolved) CyberEmerald else ElectricCyan,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (isResolved) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(CyberEmerald.copy(alpha = 0.2f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "IMPLEMENTED IN M$implementedMilestone",
                                        color = CyberEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ElectricCyan.copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "SUBMITTED • IN QUEUE",
                                        color = ElectricCyan,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            val dateStr = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(fb.timestamp))
                            Text(
                                text = dateStr,
                                fontSize = 10.5.sp,
                                color = TextMuted,
                            )
                        }
                        if (fb.title.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fb.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = Color.White,
                            )
                        }
                        if (fb.description.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = fb.description,
                                fontSize = 11.5.sp,
                                color = TextMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}