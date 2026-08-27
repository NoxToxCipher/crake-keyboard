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

package dev.patrickgold.florisboard.ime.theme

import android.content.Context
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * The bundled legibility fonts. Every file ships in assets/fonts — no
 * downloadable-font provider, no network, ever. SYSTEM means "whatever the
 * device default is", which keeps the classic look as the default choice.
 */
enum class CrakeFont {
    SYSTEM,
    ATKINSON_HYPERLEGIBLE,
    B612,
    LEXEND;
}

object CrakeFonts {
    private val cache = mutableMapOf<CrakeFont, FontFamily>()

    /**
     * Resolves a choice to a loaded [FontFamily], or null for SYSTEM so
     * callers fall through to their existing default. Families are loaded
     * once and cached for the process lifetime.
     */
    fun familyOf(context: Context, choice: CrakeFont): FontFamily? {
        if (choice == CrakeFont.SYSTEM) return null
        return cache.getOrPut(choice) {
            val assets = context.assets
            when (choice) {
                CrakeFont.ATKINSON_HYPERLEGIBLE -> FontFamily(
                    Font("fonts/AtkinsonHyperlegible-Regular.ttf", assets, FontWeight.Normal),
                    Font("fonts/AtkinsonHyperlegible-Bold.ttf", assets, FontWeight.Bold),
                )
                CrakeFont.B612 -> FontFamily(
                    Font("fonts/B612-Regular.ttf", assets, FontWeight.Normal),
                    Font("fonts/B612-Bold.ttf", assets, FontWeight.Bold),
                )
                CrakeFont.LEXEND -> FontFamily(
                    Font("fonts/Lexend-Variable.ttf", assets, FontWeight.Normal),
                    Font("fonts/Lexend-Variable.ttf", assets, FontWeight.Bold),
                )
                CrakeFont.SYSTEM -> error("unreachable")
            }
        }
    }
}
