/*
 * Copyright (C) 2025-2026 The Crake Contributors
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

package dev.patrickgold.florisboard.app.settings.advanced

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import kotlinx.coroutines.delay
import org.florisboard.libnative.FlorisNative

private val ObsidianBg = Color(0xFF0A0E17)
private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun OpticalQrSyncScreen() = FlorisScreen {
    title = "Air-Gapped Optical QR Sync"
    previewFieldVisible = false

    content {
        var selectedTabIndex by remember { mutableIntStateOf(0) }
        val tabTitles = listOf("Broadcast (Transmit)", "Ingest (Receive)")

        TabRow(
            selectedTabIndex = selectedTabIndex,
            containerColor = CardSurface,
            contentColor = CyberEmerald,
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTabIndex == index) CyberEmerald else TextMuted,
                            fontSize = 13.sp,
                        )
                    },
                )
            }
        }

        if (selectedTabIndex == 0) {
            QrTransmitterSection()
        } else {
            QrReceiverSection()
        }
    }
}

@Composable
private fun QrTransmitterSection() {
    var pairingKey by remember { mutableStateOf("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef") }
    var payloadText by remember { mutableStateOf("{\"app\":\"crake\",\"version\":\"0.1.0\",\"profile\":\"default\",\"vault\":\"encrypted\"}") }
    var isBroadcasting by remember { mutableStateOf(false) }
    var currentFrameIndex by remember { mutableIntStateOf(0) }
    var frames by remember { mutableStateOf(listOf<String>()) }
    var currentMatrix by remember { mutableStateOf("") }

    LaunchedEffect(isBroadcasting, frames) {
        if (isBroadcasting && frames.isNotEmpty()) {
            while (isBroadcasting) {
                val frameData = frames[currentFrameIndex % frames.size]
                currentMatrix = FlorisNative.generateQrMatrix(frameData)
                delay(200)
                currentFrameIndex = (currentFrameIndex + 1) % frames.size
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.QrCode2,
                        contentDescription = null,
                        tint = CyberEmerald,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ANIMATED OPTICAL QR",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isBroadcasting) CyberEmerald.copy(alpha = 0.2f) else CardBorder)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (isBroadcasting) "TRANSMITTING" else "STANDBY",
                        color = if (isBroadcasting) CyberEmerald else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // QR DISPLAY BOX
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (currentMatrix.isNotEmpty()) {
                    QrCanvas(matrixString = currentMatrix, modifier = Modifier.size(216.dp))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(64.dp),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tap 'Start Broadcast' to generate optical frames",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (frames.isNotEmpty()) {
                Text(
                    text = "Frame ${currentFrameIndex + 1} of ${frames.size} • ChaCha20-Poly1305 Sealed",
                    fontSize = 12.sp,
                    color = ElectricCyan,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedTextField(
                value = pairingKey,
                onValueChange = { pairingKey = it },
                label = { Text("32-Byte Pairing Hex Key", color = TextMuted) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyberEmerald,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    if (!isBroadcasting) {
                        val generatedFrames = FlorisNative.createSyncBundle(pairingKey, payloadText, chunkSize = 64)
                        if (generatedFrames.isNotEmpty()) {
                            frames = generatedFrames
                            isBroadcasting = true
                        }
                    } else {
                        isBroadcasting = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcasting) Color(0xFFEF4444) else CyberEmerald,
                    contentColor = if (isBroadcasting) Color.White else ObsidianBg,
                ),
                shape = RoundedCornerShape(10.dp),
            ) {
                Icon(
                    imageVector = if (isBroadcasting) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isBroadcasting) "Stop Transmission" else "Start Optical Broadcast",
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun QrReceiverSection() {
    var pairingKey by remember { mutableStateOf("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef") }
    var inputFrameText by remember { mutableStateOf("") }
    val receivedFrames = remember { mutableStateListOf<String>() }
    var decryptedResult by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = null,
                    tint = ElectricCyan,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "RECEIVE & DECRYPT BUNDLE",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = CardBorder)
            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Ingested Optical Frames: ${receivedFrames.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextMuted,
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = inputFrameText,
                onValueChange = { inputFrameText = it },
                label = { Text("Paste Scanned Frame (CRAKE:X/Y:hex)", color = TextMuted) },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricCyan,
                    unfocusedBorderColor = CardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                ),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (inputFrameText.startsWith("CRAKE:") && !receivedFrames.contains(inputFrameText)) {
                        receivedFrames.add(inputFrameText.trim())
                        inputFrameText = ""
                        // Attempt decryption
                        val decrypted = FlorisNative.reassembleSyncBundle(pairingKey, receivedFrames)
                        if (decrypted.isNotEmpty()) {
                            decryptedResult = decrypted
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text(text = "Add Optical Frame", fontWeight = FontWeight.Bold)
            }

            if (decryptedResult.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CyberEmerald.copy(alpha = 0.15f))
                        .padding(12.dp),
                ) {
                    Column {
                        Text(
                            text = "✓ BUNDLE REASSEMBLED & VERIFIED",
                            color = CyberEmerald,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = decryptedResult,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrCanvas(matrixString: String, modifier: Modifier = Modifier) {
    val parts = matrixString.split(":", limit = 2)
    if (parts.size != 2) return
    val width = parts[0].toIntOrNull() ?: return
    val bits = parts[1]

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val cellSize = size.minDimension / width
        for (y in 0 until width) {
            for (x in 0 until width) {
                val index = y * width + x
                val isDark = bits.getOrNull(index) == '1'
                if (isDark) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(x * cellSize, y * cellSize),
                        size = Size(cellSize, cellSize),
                    )
                }
            }
        }
    }
}
