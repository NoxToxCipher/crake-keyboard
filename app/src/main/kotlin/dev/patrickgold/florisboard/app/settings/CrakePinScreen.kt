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

package dev.patrickgold.florisboard.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Crake PIN pad, matching the tox client's unlock screen: a vault mark,
 * "Enter PIN", six dots that fill with the accent, and a 3-column monospace
 * keypad. It carries no "wrong PIN" state by design - the caller opens the
 * page for whatever PIN is entered, so the screen can never reveal whether a
 * PIN is "known" (the deniable multi-PIN stance from the vault).
 */
@Composable
fun CrakePinScreen(
    pinLength: Int = 6,
    onComplete: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val obsidian = Color(0xFF0B1016)
    val panel = Color(0xFF141C26)
    val line = Color(0xFF25313F)
    val ink = Color(0xFFE6EDF3)
    val inkFaint = Color(0xFF8A9AA9)
    val accent = Color(0xFF2DD4BF)

    var pin by remember { mutableStateOf("") }

    fun press(d: String) {
        if (pin.length < pinLength) {
            pin += d
            if (pin.length == pinLength) {
                val entered = pin
                pin = ""
                onComplete(entered)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(obsidian)
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 300.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(46.dp).clip(CircleShape).background(panel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("Enter PIN", color = ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "This page is sealed until it's unlocked.",
                color = inkFaint,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(pinLength) { i ->
                    val filled = i < pin.length
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (filled) accent else Color.Transparent)
                            .then(
                                if (filled) Modifier
                                else Modifier.border(1.5.dp, inkFaint, CircleShape),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (rowStart in 0 until keys.size step 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in rowStart until rowStart + 3) {
                            val label = keys[c]
                            Box(
                                modifier = Modifier
                                    .width(74.dp)
                                    .height(52.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (label.isNotEmpty()) {
                                    Surface(
                                        color = panel,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable {
                                                when (label) {
                                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                                    else -> press(label)
                                                }
                                            },
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (label == "⌫") {
                                                Icon(
                                                    Icons.AutoMirrored.Outlined.Backspace,
                                                    contentDescription = "delete",
                                                    tint = inkFaint,
                                                    modifier = Modifier.size(20.dp).align(Alignment.Center),
                                                )
                                            } else {
                                                Text(
                                                    label,
                                                    color = ink,
                                                    fontSize = 18.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    modifier = Modifier.align(Alignment.Center),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                "Close",
                color = inkFaint,
                fontSize = 13.sp,
                modifier = Modifier.clickable { onCancel() }.padding(8.dp),
            )
        }
    }
}
