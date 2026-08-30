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

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
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
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val CyberAmber = Color(0xFFF59E0B)
private val TextMuted = Color(0xFF94A3B8)

enum class FeedbackCategory(val label: String, val badge: String, val color: Color) {
    FEATURE_REQUEST("Feature Request", "IDEA", Color(0xFF00E5A3)),
    BUG_REPORT("Bug Report", "BUG", Color(0xFFFF5252)),
    NLP_TYPING("Typing & Autocorrect", "NLP", Color(0xFF00D2FF)),
    DESIGN_THEME("Theme & Aesthetics", "UI", Color(0xFFF59E0B)),
}

data class SavedFeedback(
    val timestamp: Long,
    val testerName: String,
    val category: String,
    val title: String,
    val description: String,
    val flightLogSnippet: String? = null,
)

@Composable
fun TesterFeedbackScreen() = FlorisScreen {
    title = "Tester Feedback & Bug Reporter"
    previewFieldVisible = false

    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val testerName by prefs.updater.testerName.collectAsState()
    var selectedCategory by remember { mutableStateOf(FeedbackCategory.FEATURE_REQUEST) }
    var titleText by remember { mutableStateOf("") }
    var descriptionText by remember { mutableStateOf("") }
    var attachFlightLogs by remember { mutableStateOf(true) }
    var submissionSuccess by remember { mutableStateOf(false) }
    var recentFeedbacks by remember { mutableStateOf<List<SavedFeedback>>(emptyList()) }

    fun refreshRecentFeedbacks() {
        scope.launch(Dispatchers.IO) {
            val file = File(context.filesDir, "tester_feedback.jsonl")
            if (file.exists()) {
                val list = file.readLines().mapNotNull { line ->
                    runCatching {
                        val obj = JSONObject(line)
                        SavedFeedback(
                            timestamp = obj.optLong("timestamp", 0L),
                            testerName = obj.optString("testerName", "Tester"),
                            category = obj.optString("category", "Feedback"),
                            title = obj.optString("title", ""),
                            description = obj.optString("description", ""),
                            flightLogSnippet = obj.optString("flightLogSnippet", "").ifEmpty { null },
                        )
                    }.getOrNull()
                }.reversed().take(10)
                withContext(Dispatchers.Main) {
                    recentFeedbacks = list
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshRecentFeedbacks()
    }

    content {
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
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (cat in FeedbackCategory.entries) {
                val isSelected = cat == selectedCategory
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedCategory = cat },
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) cat.color.copy(alpha = 0.15f) else CardSurface
                    ),
                    border = BorderStroke(1.dp, if (isSelected) cat.color else CardBorder),
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val icon = when (cat) {
                            FeedbackCategory.FEATURE_REQUEST -> Icons.Default.AutoAwesome
                            FeedbackCategory.BUG_REPORT -> Icons.Default.Warning
                            FeedbackCategory.NLP_TYPING -> Icons.Default.Edit
                            FeedbackCategory.DESIGN_THEME -> Icons.Default.Favorite
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) cat.color else TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cat.label,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else TextMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // 3. SUBMISSION FORM
        Spacer(modifier = Modifier.height(8.dp))
        CrakeSectionHeader(title = "Suggestion & Report Details", badgeText = "FORM", accentColor = ElectricCyan)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
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
                        .height(110.dp),
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

                Spacer(modifier = Modifier.height(12.dp))
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
                        scope.launch(Dispatchers.IO) {
                            val flightLogs = if (attachFlightLogs) {
                                FlightRecorderManager.readRecentRecords(context, limit = 30).joinToString("\n")
                            } else null

                            val jsonObj = JSONObject().apply {
                                put("timestamp", System.currentTimeMillis())
                                val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US).format(Date())
                                put("time", iso)
                                put("testerName", testerName)
                                put("category", selectedCategory.name)
                                put("title", titleText.trim())
                                put("description", descriptionText.trim())
                                if (flightLogs != null) put("flightLogSnippet", flightLogs)
                            }

                            val file = File(context.filesDir, "tester_feedback.jsonl")
                            FileWriter(file, true).use {
                                it.append(jsonObj.toString()).append("\n")
                            }

                            // Transmit wirelessly to development relay
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = BorderStroke(1.dp, CardBorder),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = fb.category.replace("_", " "),
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = ElectricCyan,
                            )
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