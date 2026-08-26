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

package dev.patrickgold.florisboard.lib.compose

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.patrickgold.jetpref.datastore.model.PreferenceData
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ListPreferenceEntry
import kotlinx.coroutines.launch

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)
private val RadioRingUnselected = Color(0xFF64748B)

@Composable
fun CrakeRadioPreference(
    pref: PreferenceData<Boolean>,
    title: String,
    summary: String? = null,
    icon: ImageVector? = null,
    accentColor: Color = CyberEmerald,
    enabledIf: () -> Boolean = { true },
    visibleIf: () -> Boolean = { true },
) {
    if (!visibleIf()) return

    val isChecked by pref.observeAsState()
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable {
                if (enabledIf()) {
                    scope.launch {
                        pref.set(!isChecked)
                    }
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, if (isChecked) accentColor.copy(alpha = 0.4f) else CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isChecked) accentColor.copy(alpha = 0.15f) else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isChecked) accentColor else TextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary,
                        fontSize = 11.5.sp,
                        color = TextMuted,
                        lineHeight = 14.sp,
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Cyber Radio Indicator
            CrakeRadioIndicator(
                selected = isChecked,
                enabled = enabledIf(),
                accentColor = accentColor,
            )
        }
    }
}

@Composable
fun <V : Any> CrakeListPreference(
    pref: PreferenceData<V>? = null,
    listPref: PreferenceData<V>? = null,
    title: String,
    entries: List<ListPreferenceEntry<V>>,
    icon: ImageVector? = null,
    summary: String? = null,
    accentColor: Color = ElectricCyan,
    enabledIf: () -> Boolean = { true },
    visibleIf: () -> Boolean = { true },
) {
    if (!visibleIf()) return
    val targetPref = pref ?: listPref ?: return

    val currentValue by targetPref.observeAsState()
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    val currentLabel = remember(currentValue, entries) {
        entries.find { it.key == currentValue }?.label ?: currentValue.toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { if (enabledIf()) showDialog = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = BorderStroke(1.dp, CardBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = currentLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = CyberEmerald,
                )
                if (summary != null) {
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = summary,
                        fontSize = 11.sp,
                        color = TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E293B))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    // Obsidian Glass Selection Modal
    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                border = BorderStroke(1.dp, CardBorder),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "SELECT AN OPTION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricCyan,
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = CardBorder, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(10.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                    ) {
                        items(entries) { entry ->
                            val isSelected = entry.key == currentValue
                            val optionLabel = entry.label

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        scope.launch {
                                            targetPref.set(entry.key)
                                        }
                                        showDialog = false
                                    },
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFF1A263D) else Color(0xFF0F1624),
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) CyberEmerald.copy(alpha = 0.5f) else Color(0xFF1E283A),
                                ),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    CrakeRadioIndicator(
                                        selected = isSelected,
                                        enabled = true,
                                        accentColor = CyberEmerald,
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = optionLabel,
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFFCBD5E1),
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                        onClick = { showDialog = false },
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold, color = TextMuted, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CrakeRadioIndicator(
    selected: Boolean,
    enabled: Boolean,
    accentColor: Color = CyberEmerald,
    modifier: Modifier = Modifier,
) {
    val haloAlpha by animateFloatAsState(
        targetValue = if (selected) 0.25f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "haloAlpha",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) accentColor else RadioRingUnselected,
        animationSpec = tween(durationMillis = 200),
        label = "ringColor",
    )
    val dotScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "dotScale",
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(accentColor.copy(alpha = haloAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        // Outer 2dp Border Ring
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(
                    width = 2.dp,
                    color = ringColor,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Inner Active Solid Dot
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(dotScale)
                        .background(
                            color = accentColor,
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}

@Composable
fun CrakeSectionHeader(
    title: String,
    badgeText: String? = null,
    accentColor: Color = ElectricCyan,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp,
            color = accentColor,
        )
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = accentColor,
                )
            }
        }
    }
}
