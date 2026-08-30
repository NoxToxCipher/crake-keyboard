/*
 * Copyright (C) 2024-2026 The Crake Contributors
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

package dev.patrickgold.florisboard.ime.smartbar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.ime.dictionary.DictionaryManager
import dev.patrickgold.florisboard.ime.dictionary.UserDictionaryEntry
import dev.patrickgold.florisboard.ime.nlp.ClipboardSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.SuggestionCandidate
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import dev.patrickgold.florisboard.subtypeManager
import dev.patrickgold.jetpref.datastore.model.collectAsState
import org.florisboard.lib.compose.conditional
import org.florisboard.lib.compose.florisHorizontalScroll
import org.florisboard.lib.snygg.SnyggSelector
import org.florisboard.lib.snygg.ui.SnyggBox
import org.florisboard.lib.snygg.ui.SnyggColumn
import org.florisboard.lib.snygg.ui.SnyggIcon
import org.florisboard.lib.snygg.ui.SnyggRow
import org.florisboard.lib.snygg.ui.SnyggSpacer
import org.florisboard.lib.snygg.ui.SnyggText
import kotlinx.coroutines.withTimeout

val CandidatesRowScrollbarHeight = 2.dp

@Composable
fun CandidatesRow(modifier: Modifier = Modifier) {
    val prefs by FlorisPreferenceStore
    val context = LocalContext.current
    val keyboardManager by context.keyboardManager()
    val nlpManager by context.nlpManager()
    val subtypeManager by context.subtypeManager()

    val displayMode by prefs.suggestion.displayMode.collectAsState()
    val candidates by nlpManager.activeCandidatesFlow.collectAsState()
    var selectedCandidateForMenu by remember { mutableStateOf<SuggestionCandidate?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        SnyggRow(
            elementName = FlorisImeUi.SmartbarCandidatesRow.elementName,
            modifier = Modifier
                .fillMaxSize()
                .conditional(displayMode == CandidatesDisplayMode.DYNAMIC_SCROLLABLE && candidates.size > 1) {
                    florisHorizontalScroll(scrollbarHeight = CandidatesRowScrollbarHeight)
                },
            horizontalArrangement = if (candidates.size > 1) {
                Arrangement.Start
            } else {
                Arrangement.Center
            },
        ) {
            if (candidates.isNotEmpty()) {
                val candidateModifier = if (candidates.size == 1) {
                    Modifier
                        .fillMaxHeight()
                        .weight(1f, fill = false)
                } else {
                    Modifier
                        .fillMaxHeight()
                        .conditional(displayMode == CandidatesDisplayMode.CLASSIC) {
                            weight(1f)
                        }
                        .conditional(displayMode != CandidatesDisplayMode.CLASSIC) {
                            wrapContentWidth().widthIn(min = 40.dp, max = 320.dp)
                        }
                }
                val list = when (displayMode) {
                    CandidatesDisplayMode.CLASSIC -> candidates.subList(0, 3.coerceAtMost(candidates.size))
                    else -> candidates
                }
                for ((n, candidate) in list.withIndex()) {
                    if (n > 0) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight(0.45f)
                                .align(Alignment.CenterVertically)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color(0x3500E5FF),
                                            Color(0x60CBD5E1),
                                            Color(0x3500E5FF),
                                            Color.Transparent,
                                        )
                                    )
                                )
                        )
                    }
                    CandidateItem(
                        modifier = candidateModifier,
                        candidate = candidate,
                        displayMode = displayMode,
                        onClick = {
                            keyboardManager.commitCandidate(candidates[n], withSpace = true)
                        },
                        onLongPress = {
                            val candidateItem = candidates[n]
                            selectedCandidateForMenu = candidateItem
                            true
                        },
                        longPressDelay = prefs.keyboard.longPressDelay.get().toLong(),
                    )
                }
            }
        }

        // Crake Signature Glassmorphic Suggestion & Dictionary Menu Box
        selectedCandidateForMenu?.let { item ->
            val wordText = item.text.toString()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xCC05070B))
                    .clickable { selectedCandidateForMenu = null },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight(0.92f)
                        .padding(horizontal = 6.dp)
                        .background(
                            color = Color(0xF412151E),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .border(
                            width = 1.2.dp,
                            brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF00E5FF),
                                    Color(0xFF38BDF8),
                                    Color(0xFF00E5FF),
                                )
                            ),
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Word Label Badge
                    Row(
                        modifier = Modifier
                            .background(Color(0x3000E5FF), RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Spellcheck,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.padding(end = 4.dp).width(14.dp).height(14.dp),
                        )
                        Text(
                            text = wordText,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    // Add to Dictionary Action Pill
                    Row(
                        modifier = Modifier
                            .background(Color(0x2510B981), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x6010B981), RoundedCornerShape(6.dp))
                            .clickable {
                                try {
                                    val dictManager = DictionaryManager.default()
                                    dictManager.florisUserDictionaryDao()?.insert(
                                        UserDictionaryEntry(
                                            id = 0L,
                                            word = wordText,
                                            freq = 250,
                                            locale = subtypeManager.activeSubtype.primaryLocale.languageTag(),
                                            shortcut = null,
                                        )
                                    )
                                } catch (_: Throwable) {}
                                selectedCandidateForMenu = null
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = Color(0xFF34D399),
                            modifier = Modifier.padding(end = 2.dp).width(13.dp).height(13.dp),
                        )
                        Text(
                            text = "Add Word",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Delete / Forget Action Pill
                    Row(
                        modifier = Modifier
                            .background(Color(0x25F43F5E), RoundedCornerShape(6.dp))
                            .border(1.dp, Color(0x60F43F5E), RoundedCornerShape(6.dp))
                            .clickable {
                                nlpManager.removeSuggestion(subtypeManager.activeSubtype, item)
                                selectedCandidateForMenu = null
                            }
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFB7185),
                            modifier = Modifier.padding(end = 2.dp).width(13.dp).height(13.dp),
                        )
                        Text(
                            text = "Delete",
                            color = Color(0xFFFB7185),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    // Close Button
                    IconButton(
                        onClick = { selectedCandidateForMenu = null },
                        modifier = Modifier.width(22.dp).height(22.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.width(14.dp).height(14.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CandidateItem(
    candidate: SuggestionCandidate,
    displayMode: CandidatesDisplayMode,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = { },
    onLongPress: () -> Boolean = { false },
    longPressDelay: Long = 0,
) = with(LocalDensity.current) {
    var isPressed by remember { mutableStateOf(false) }

    val elementName = if (candidate is ClipboardSuggestionCandidate) {
        FlorisImeUi.SmartbarCandidateClip
    } else {
        FlorisImeUi.SmartbarCandidateWord
    }.elementName
    val attributes = mapOf("auto-commit" to if (candidate.isEligibleForAutoCommit) 1 else 0)
    val selector = if (isPressed) SnyggSelector.PRESSED else SnyggSelector.NONE

    SnyggRow(
        elementName = elementName,
        attributes = attributes,
        selector = selector,
        modifier = modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    isPressed = true
                    if (down.pressed != down.previousPressed) down.consume()
                    var upOrCancel: PointerInputChange? = null
                    try {
                        upOrCancel = withTimeout(longPressDelay) {
                            waitForUpOrCancellation()
                        }
                        upOrCancel?.let { if (it.pressed != it.previousPressed) it.consume() }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (onLongPress()) {
                            upOrCancel = null
                            isPressed = false
                        }
                        waitForUpOrCancellation()?.let { if (it.pressed != it.previousPressed) it.consume() }
                    }
                    if (upOrCancel != null) {
                        onClick()
                    }
                    isPressed = false
                }
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (candidate.isEligibleForAutoCommit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .background(
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0x2400E5FF),
                                Color(0x1000E5FF),
                            )
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
                    .border(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(
                                Color(0x8000E5FF),
                                Color(0x2500E5FF),
                            )
                        ),
                        shape = RoundedCornerShape(8.dp),
                    )
            )
        }
        if (isPressed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 4.dp)
                    .background(
                        color = Color(0x4000E5FF),
                        shape = RoundedCornerShape(8.dp),
                    )
            )
        }
        if (candidate.icon != null) {
            SnyggBox(
                elementName = "$elementName-icon",
                attributes = attributes,
                selector = selector,
            ) {
                SnyggIcon(imageVector = candidate.icon!!)
            }
        }
        SnyggColumn(
            modifier = if (displayMode == CandidatesDisplayMode.CLASSIC) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SnyggText(
                elementName = "$elementName-text",
                attributes = attributes,
                selector = selector,
                text = candidate.text.toString(),
            )
            if (candidate.secondaryText != null) {
                SnyggText(
                    elementName = "$elementName-secondary-text",
                    attributes = attributes,
                    selector = selector,
                    text = candidate.secondaryText!!.toString(),
                )
            }
        }
    }
}
