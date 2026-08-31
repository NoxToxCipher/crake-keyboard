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

package dev.patrickgold.florisboard.lib.compose

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource

private val vectorResourceCache = java.util.concurrent.ConcurrentHashMap<Long, ImageVector>()

fun Context.vectorResource(@DrawableRes id: Int): ImageVector? {
    val theme = this.theme
    val key = (id.toLong() shl 32) or (theme?.hashCode()?.toLong()?.and(0xFFFFFFFFL) ?: 0L)
    val cached = vectorResourceCache[key]
    if (cached != null) {
        return cached
    }
    val loaded = try {
        ImageVector.vectorResource(theme = theme, resId = id, res = this.resources)
    } catch (_: Exception) {
        null
    }
    if (loaded != null) {
        vectorResourceCache[key] = loaded
    }
    return loaded
}

