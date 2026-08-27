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

package dev.patrickgold.florisboard.ime.clipboard

import dev.patrickgold.florisboard.ime.clipboard.provider.ClipboardItem
import org.florisboard.libnative.FlorisNative

data class ClipboardHistory(val all: List<ClipboardItem>) {
    companion object {
        private const val GROUP_PINNED = 0
        private const val GROUP_RECENT = 1
        private const val GROUP_OTHER = 2

        val EMPTY = ClipboardHistory(emptyList())
    }

    // Grouping (pinned / recent / other, with the recency window) is decided
    // by the native clipboard policy engine.
    private val groups: ByteArray = FlorisNative.clipboardClassifyHistory(
        flags = IntArray(all.size) { if (all[it].isPinned) 1 else 0 },
        createdMs = LongArray(all.size) { all[it].creationTimestampMs },
        nowMs = System.currentTimeMillis(),
    )

    private fun groupOf(index: Int): Int {
        // Degraded mode without the native library: pinned grouping still
        // works, everything unpinned counts as recent.
        if (index >= groups.size) return if (all[index].isPinned) GROUP_PINNED else GROUP_RECENT
        return groups[index].toInt()
    }

    val pinned = all.filterIndexed { index, _ -> groupOf(index) == GROUP_PINNED }
    val unpinned = all.filterIndexed { index, _ -> groupOf(index) != GROUP_PINNED }
    val recent = all.filterIndexed { index, _ -> groupOf(index) == GROUP_RECENT }
    val other = all.filterIndexed { index, _ -> groupOf(index) == GROUP_OTHER }
}
