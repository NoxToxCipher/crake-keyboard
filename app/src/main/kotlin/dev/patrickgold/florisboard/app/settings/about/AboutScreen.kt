/*
 * Copyright (C) 2021-2026 The Crake Contributors
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

package dev.patrickgold.florisboard.app.settings.about

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.R
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.florisboard.lib.util.launchUrl
import dev.patrickgold.jetpref.datastore.ui.Preference
import org.florisboard.lib.android.stringRes
import org.florisboard.lib.compose.stringRes

private val CardSurface = Color(0xFF131A29)
private val CardBorder = Color(0xFF222D42)
private val CyberEmerald = Color(0xFF00E5A3)
private val ElectricCyan = Color(0xFF00D2FF)
private val TextMuted = Color(0xFF94A3B8)

@Composable
fun AboutScreen() = FlorisScreen {
    title = stringRes(R.string.about__title)

    val context = LocalContext.current
    val clipboardManager by context.clipboardManager()
    val appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

    content {
        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF0F172A))
                    .padding(8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_app_icon_foreground),
                    contentDescription = "Crake Logo",
                    modifier = Modifier.size(64.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "CRAKE KEYBOARD",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.5.sp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "SAFE RUST CORE • AIR-GAPPED • LOCAL VAULT",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyberEmerald,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.sp,
            )
        }

        // CRAKE ARCHITECTURE & PRIVACY CARD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = BorderStroke(1.dp, CardBorder),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = CyberEmerald,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Cryptographic Sovereignty",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = Color.White,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Crake Keyboard is an independent, security-hardened air-gapped fork engineered for absolute privacy, 5.3M word/s native Safe Rust NLP predictive intelligence, and encrypted local storage.",
                    fontSize = 11.5.sp,
                    color = TextMuted,
                    lineHeight = 16.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Preference(
            icon = Icons.Outlined.Info,
            title = stringRes(R.string.about__version__title),
            summary = appVersion,
            onClick = {
                try {
                    clipboardManager.addNewPlaintext(appVersion)
                    Toast.makeText(context, R.string.about__version_copied__title, Toast.LENGTH_SHORT).show()
                } catch (e: Throwable) {
                    Toast.makeText(
                        context,
                        context.stringRes(R.string.about__version_copied__error, "error_message" to e.message),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            },
        )

        Preference(
            icon = Icons.Default.Memory,
            title = "Native Engine Core",
            summary = "Safe Rust 5.3M word/s Radix Trie + Damerau-Levenshtein Engine",
            onClick = {},
        )

        Preference(
            icon = Icons.Default.Lock,
            title = "Zero-Network Guarantee",
            summary = "100% Air-Gapped • No Internet Permission • Zero Telemetry",
            onClick = {},
        )

        Preference(
            icon = Icons.Default.Code,
            title = stringRes(R.string.about__repository__title),
            summary = "https://github.com/NoxToxCipher/crake-keyboard",
            onClick = { context.launchUrl(R.string.florisboard__repo_url) },
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
