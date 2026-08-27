/*
 * Copyright (C) 2025 The FlorisBoard Contributors
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

package dev.patrickgold.florisboard.ime.text.gestures

import android.content.Context
import android.util.Log
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.keyboard.KeyData
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.text.keyboard.TextKey
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.florisboard.nlpManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.libnative.FlorisNative

/**
 * Links [GlideTypingGesture.Detector] with the native Safe Rust DTW
 * trajectory-matching engine, which is the single glide classifier.
 */
class GlideTypingManager(context: Context) : GlideTypingGesture.Listener {
    companion object {
        private const val MAX_SUGGESTION_COUNT = 8

        // Bounds the accumulated gesture path (roughly 40+ seconds of touch
        // events); points beyond this are dropped, like the retired
        // classifier's own buffer cap.
        private const val MAX_GESTURE_POINTS = 4096
    }

    private val prefs by FlorisPreferenceStore
    private val editorInstance by context.editorInstance()
    private val keyboardManager by context.keyboardManager()
    private val nlpManager by context.nlpManager()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val gesturePoints = mutableListOf<FlorisNative.GlidePoint>()
    private var lastTime = System.currentTimeMillis()

    // In-flight preview computation; cancelled whenever a newer preview or
    // the final commit supersedes it, so a slow preview can never overwrite
    // the suggestion bar after the gesture has been committed.
    private var previewJob: Job? = null

    // Geometry last uploaded to the native engine, for skipping identical
    // re-uploads (the layout call site runs on every recomposition).
    private var lastCodes: IntArray? = null
    private var lastChars: String? = null
    private var lastXs: FloatArray? = null
    private var lastYs: FloatArray? = null
    private var lastWidths: FloatArray? = null
    private var lastHeights: FloatArray? = null

    override fun onGlideComplete(data: GlideTypingGesture.Detector.PointerData) {
        previewJob?.cancel()
        // Snapshot and clear synchronously so a next glide starting before
        // the async match completes can never mix its points into this one.
        val ptsCopy = synchronized(gesturePoints) {
            val copy = gesturePoints.toList()
            gesturePoints.clear()
            copy
        }
        updateSuggestionsAsync(MAX_SUGGESTION_COUNT, true, ptsCopy)
    }

    override fun onGlideCancelled() {
        previewJob?.cancel()
        synchronized(gesturePoints) { gesturePoints.clear() }
    }

    fun cancelGlide() {
        previewJob?.cancel()
        synchronized(gesturePoints) { gesturePoints.clear() }
    }

    override fun onGlideAddPoint(point: GlideTypingGesture.Detector.Position) {
        val time = System.currentTimeMillis()
        synchronized(gesturePoints) {
            if (gesturePoints.size < MAX_GESTURE_POINTS) {
                gesturePoints.add(FlorisNative.GlidePoint(point.x, point.y, time))
            }
        }

        if (prefs.glide.showPreview.get() && time - lastTime > prefs.glide.previewRefreshDelay.get()) {
            previewJob?.cancel()
            previewJob = updateSuggestionsAsync(1, false)
            lastTime = time
        }
    }

    /**
     * Change the key layout of the native Rust DTW engine
     */
    fun setLayout(keys: List<TextKey>) {
        if (keys.isNotEmpty()) {
            // Populate Native Safe Rust DTW key geometry
            // ASCII letters only: isLetter() also admits fullwidth Unicode
            // glyphs (device evidence 2026-08-27: bottom-row keys labelled
            // Ｖ and Ｌ entered the glide layout as letters, polluting the
            // key set and the pitch the slip radius derives from).
            val letterKeys = keys.filter {
                val code = (it.data as? KeyData)?.code ?: 0
                (code >= 'a'.code && code <= 'z'.code) || (code >= 'A'.code && code <= 'Z'.code)
            }
            if (letterKeys.isNotEmpty()) {
                val codes = IntArray(letterKeys.size) { (letterKeys[it].data as KeyData).code }
                val chars = buildString { letterKeys.forEach { append((it.data as KeyData).code.toChar()) } }
                val xs = FloatArray(letterKeys.size) { letterKeys[it].visibleBounds.center.x }
                val ys = FloatArray(letterKeys.size) { letterKeys[it].visibleBounds.center.y }
                val widths = FloatArray(letterKeys.size) { letterKeys[it].visibleBounds.width }
                val heights = FloatArray(letterKeys.size) { letterKeys[it].visibleBounds.height }
                // Identical geometry means the engine already has this exact
                // layout: skip the JNI upload and the NLP write-lock it takes.
                val geometryUnchanged = codes.contentEquals(lastCodes) &&
                    chars == lastChars &&
                    xs.contentEquals(lastXs) &&
                    ys.contentEquals(lastYs) &&
                    widths.contentEquals(lastWidths) &&
                    heights.contentEquals(lastHeights)
                if (geometryUnchanged) {
                    return
                }
                lastCodes = codes
                lastChars = chars
                lastXs = xs
                lastYs = ys
                lastWidths = widths
                lastHeights = heights
                FlorisNative.glideSetLayout(codes, chars, xs, ys, widths, heights)
                // Debug builds narrate the layout so a captured trace can be
                // replayed against the exact geometry it was drawn on.
                if (BuildConfig.DEBUG) {
                    val desc = buildString {
                        for (i in letterKeys.indices) {
                            if (i > 0) append(',')
                            append(chars[i]).append(':')
                            append(xs[i].toInt()).append(':').append(ys[i].toInt()).append(':')
                            append(widths[i].toInt()).append(':').append(heights[i].toInt())
                        }
                    }
                    Log.i("CrakeGlideTrace", "layout $desc")
                }
            }
        }
    }

    /**
     * Asks native Rust DTW engine for suggestions and then passes that on to the smartbar.
     * Also commits the most confident suggestion if [commit] is set. All happens on an async executor.
     * NB: only fetches [MAX_SUGGESTION_COUNT] suggestions.
     *
     * @param callback Called when this function completes. Takes a boolean, which indicates if suggestions
     * were successfully set.
     */
    private fun updateSuggestionsAsync(
        maxSuggestionsToShow: Int,
        commit: Boolean,
        points: List<FlorisNative.GlidePoint>? = null,
        callback: (Boolean) -> Unit = {},
    ): Job {
        return scope.launch(Dispatchers.Default) {
            val pts = points ?: synchronized(gesturePoints) { gesturePoints.toList() }
            var prevWordForTrace = ""
            val nativeSuggestions = if (FlorisNative.isAvailable() && pts.size >= 2) {
                // Previous committed word, so the native matcher can blend
                // sentence context (bigram LM) into gesture scoring.
                val prevWord = editorInstance.activeContent.textBeforeSelection
                    .trimEnd()
                    .takeLastWhile { it.isLetter() || it == '\'' }
                    .toString()
                prevWordForTrace = prevWord
                FlorisNative.glideMatch(pts, MAX_SUGGESTION_COUNT, prevWord)
            } else {
                FlorisNative.GlideResult(emptyList(), false)
            }

            // A native display-only set (no solid word anywhere - the
            // stray-flick guard) shows suggestions but commits nothing.
            val commitSafe = nativeSuggestions.commitSafe
            val suggestions = nativeSuggestions.words

            withContext(Dispatchers.Main) {
                // The top candidate is hidden from the bar only when it is
                // actually being committed; a commit blocked by the
                // stray-flick guard keeps all candidates visible.
                val firstShownIndex = if (commit && commitSafe && suggestions.isNotEmpty()) 1 else 0
                val suggestionList = buildList {
                    suggestions.subList(
                        firstShownIndex,
                        maxSuggestionsToShow.coerceAtMost(suggestions.size).coerceAtLeast(firstShownIndex)
                    ).map { keyboardManager.fixCase(it) }.forEach {
                        add(WordSuggestionCandidate(it, confidence = 1.0))
                    }
                }

                nlpManager.suggestDirectly(suggestionList)
                if (commit && commitSafe && suggestions.isNotEmpty()) {
                    // Debug builds capture the committed stroke: the real
                    // thumb traces the synthetic eval cannot imagine, turned
                    // into replayable specimens (adb logcat -s CrakeGlideTrace).
                    // Never in incognito - typed content stays unlogged there.
                    if (BuildConfig.DEBUG && !keyboardManager.activeState.isIncognitoMode) {
                        val top = suggestions.take(3).joinToString(",")
                        val startT = pts.firstOrNull()?.timestamp ?: 0L
                        pts.chunked(150).forEachIndexed { ci, chunk ->
                            val line = chunk.joinToString(";") {
                                val relT = if (it.timestamp > 0L) (it.timestamp - startT).coerceAtLeast(0) else 0L
                                "${it.x.toInt()}:${it.y.toInt()}:$relT"
                            }
                            Log.i("CrakeGlideTrace", "pts $ci $line")
                        }
                        Log.i(
                            "CrakeGlideTrace",
                            "commit prev=\"$prevWordForTrace\" top=$top n=${pts.size}"
                        )
                    }
                    keyboardManager.commitGesture(suggestions.first())
                }
                callback.invoke(true)
            }
        }
    }
}
