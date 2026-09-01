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

package dev.patrickgold.florisboard.app.island

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex

@Composable
fun CrakeDynamicIslandOverlay(
    modifier: Modifier = Modifier,
    useStatusBarsPadding: Boolean = true,
) {
    val notification by DynamicIslandManager.currentNotification.collectAsState()
    val isExpanded by DynamicIslandManager.isExpanded.collectAsState()

    AnimatedVisibility(
        visible = notification != null,
        enter = slideInVertically(
            initialOffsetY = { -it },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow)
        ) + fadeIn() + scaleIn(initialScale = 0.85f),
        exit = slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ) + fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = modifier
            .then(if (useStatusBarsPadding) Modifier.statusBarsPadding() else Modifier)
            .padding(top = if (useStatusBarsPadding) 4.dp else 2.dp)
            .zIndex(9999f),
    ) {
        val notif = notification ?: return@AnimatedVisibility

        val infiniteTransition = rememberInfiniteTransition(label = "islandPulse")
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(1200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        val targetWidth by animateDpAsState(
            targetValue = if (isExpanded) 345.dp else 225.dp,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
            label = "islandWidth"
        )

        Surface(
            modifier = Modifier
                .width(targetWidth)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                )
                .shadow(
                    elevation = 12.dp,
                    shape = RoundedCornerShape(if (isExpanded) 20.dp else 24.dp),
                    spotColor = notif.accentColor.copy(alpha = 0.5f),
                    ambientColor = notif.accentColor.copy(alpha = 0.3f),
                )
                .clip(RoundedCornerShape(if (isExpanded) 20.dp else 24.dp))
                .border(
                    BorderStroke(
                        1.2.dp,
                        Brush.horizontalGradient(
                            listOf(
                                notif.accentColor.copy(alpha = pulseAlpha),
                                notif.accentColor.copy(alpha = 0.35f),
                                notif.accentColor.copy(alpha = pulseAlpha),
                            )
                        )
                    ),
                    RoundedCornerShape(if (isExpanded) 20.dp else 24.dp)
                )
                .pointerInput(Unit) {
                    var totalDragY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { totalDragY = 0f },
                        onDragEnd = {
                            if (totalDragY < -30f) {
                                DynamicIslandManager.dismiss(notif.id)
                            } else if (totalDragY > 30f) {
                                DynamicIslandManager.setExpanded(true)
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            totalDragY += dragAmount
                        }
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            if (notif.onClick != null) {
                                notif.onClick.invoke()
                            } else if (!useStatusBarsPadding && notif.onAction != null) {
                                notif.onAction.invoke()
                                DynamicIslandManager.dismiss(notif.id)
                            } else if (!useStatusBarsPadding) {
                                DynamicIslandManager.dismiss(notif.id)
                            } else {
                                DynamicIslandManager.toggleExpanded()
                            }
                        }
                    )
                },
            color = Color(0xFF090D16),
            tonalElevation = 6.dp,
        ) {
            if (isExpanded) {
                // EXPANDED ISLAND CARD VIEW
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(notif.accentColor.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (notif.emoji != null) {
                                    Text(text = notif.emoji, fontSize = 15.sp)
                                } else if (notif.icon != null) {
                                    Icon(
                                        imageVector = notif.icon,
                                        contentDescription = null,
                                        tint = notif.accentColor,
                                        modifier = Modifier.size(16.dp),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(notif.accentColor)
                                    )
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = notif.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (notif.subtitle != null) {
                                    Text(
                                        text = notif.subtitle,
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.5.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }

                        IconButton(
                            onClick = { DynamicIslandManager.dismiss(notif.id) },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    if (notif.progress != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            LinearProgressIndicator(
                                progress = { notif.progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = notif.accentColor,
                                trackColor = Color(0xFF1E293B),
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "${(notif.progress * 100).toInt()}%",
                                color = notif.accentColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }

                    if (notif.actionLabel != null && notif.onAction != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = {
                                notif.onAction.invoke()
                                DynamicIslandManager.dismiss(notif.id)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = notif.accentColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(34.dp),
                        ) {
                            Text(
                                text = notif.actionLabel,
                                color = Color(0xFF090D16),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            } else {
                // COMPACT CAPSULE VIEW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(notif.accentColor.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (notif.emoji != null) {
                                Text(text = notif.emoji, fontSize = 12.sp)
                            } else if (notif.icon != null) {
                                Icon(
                                    imageVector = notif.icon,
                                    contentDescription = null,
                                    tint = notif.accentColor,
                                    modifier = Modifier.size(13.dp),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(notif.accentColor)
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = notif.title,
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (notif.progress != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "${(notif.progress * 100).toInt()}%",
                            color = notif.accentColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    } else if (notif.actionLabel != null) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(notif.accentColor.copy(alpha = 0.2f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = notif.actionLabel,
                                color = notif.accentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
