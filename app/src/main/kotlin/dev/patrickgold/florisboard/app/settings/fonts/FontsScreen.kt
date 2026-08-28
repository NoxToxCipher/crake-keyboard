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

package dev.patrickgold.florisboard.app.settings.fonts

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.theme.CrakeFont
import dev.patrickgold.florisboard.ime.theme.CrakeFonts
import dev.patrickgold.florisboard.lib.compose.CrakeListPreference
import dev.patrickgold.florisboard.lib.compose.CrakeSectionHeader
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

@Composable
fun FontsScreen() = FlorisScreen {
    title = "Fonts"
    previewFieldVisible = true

    val prefs by FlorisPreferenceStore

    content {
        CrakeSectionHeader(title = "Choose")
        CrakeListPreference(
            prefs.fonts.keyboardFont,
            title = "Keyboard font",
            entries = enumDisplayEntriesOf(CrakeFont::class),
        )
        CrakeListPreference(
            prefs.fonts.appFont,
            title = "App font",
            entries = enumDisplayEntriesOf(CrakeFont::class),
        )

        CrakeSectionHeader(title = "The evidence")
        FontEvidenceCard(
            font = CrakeFont.ATKINSON_HYPERLEGIBLE,
            name = "Atkinson Hyperlegible",
            oneLiner = "Designed by the Braille Institute for unambiguous letter shapes.",
            evidence = "Designed by the Braille Institute (2019, expanded 2025) " +
                "around character disambiguation for low vision: mirrored " +
                "letters like b, d, p and q get distinct shapes, and lookalikes " +
                "such as capital I, lowercase l and the digit 1 cannot be " +
                "confused. That is exactly the job a key label does, one glyph " +
                "read at a glance. Institutionally designed and tested with low " +
                "vision readers; held in the Smithsonian design collection. " +
                "Strength of evidence: strong for letter recognition, the core " +
                "task on a keyboard.",
            license = "SIL Open Font License. Bundled offline.",
        )
        FontEvidenceCard(
            font = CrakeFont.B612,
            name = "B612",
            oneLiner = "The Airbus cockpit font, validated for glanced reading.",
            evidence = "Commissioned by Airbus and developed with ENAC and the " +
                "University of Toulouse (2010 to 2012) as a screen font for " +
                "cockpit displays, then validated in that research programme " +
                "for legibility of small text read in short glances under " +
                "pressure, where a misread has real cost. A keyboard key is " +
                "the same problem at a smaller stake. Airbus open sourced it " +
                "in 2017. Strength of evidence: strong, and the only option " +
                "here with a formal validation programme behind it.",
            license = "Open font, distributed via Google Fonts. Bundled offline.",
        )
        FontEvidenceCard(
            font = CrakeFont.LEXEND,
            name = "Lexend",
            oneLiner = "Spacing-first design with classroom reading data.",
            evidence = "Built on the finding that letter spacing, not letter " +
                "shape, is what measurably helps reading. The strongest result " +
                "in this whole field is Zorzi and colleagues (PNAS 2012): wider " +
                "letter spacing improved reading speed and halved errors for " +
                "dyslexic children. A 2019 study of Lexend across 2,684 " +
                "students found proficiency gains concentrated in struggling " +
                "readers. Honest caveat: reviews of special fonts overall find " +
                "the evidence thin, and the famous dyslexia fonts show no " +
                "benefit in controlled studies. Lexend earns its place by " +
                "building on the spacing result, which is real. Best suited to " +
                "the suggestion bar and app text, where whole words are read.",
            license = "SIL Open Font License. Bundled offline.",
        )
    }
}

@Composable
private fun FontEvidenceCard(
    font: CrakeFont,
    name: String,
    oneLiner: String,
    evidence: String,
    license: String,
) {
    val context = LocalContext.current
    val family = remember(font) { CrakeFonts.familyOf(context, font) }
    var expanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { expanded = !expanded }
            .animateContentSize(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = name,
                fontFamily = family,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            // The card IS the specimen: sample rendered in the font itself,
            // leading with the confusable characters keyboards live on.
            Text(
                text = "Il1 O0 bdpq • the quick brown fox",
                fontFamily = family,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = oneLiner,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = evidence,
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = license,
                    style = MaterialTheme.typography.labelSmall,
                )
            } else {
                Text(
                    text = "Tap for the evidence",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
