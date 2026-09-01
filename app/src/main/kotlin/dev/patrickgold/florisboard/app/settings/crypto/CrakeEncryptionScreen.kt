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

package dev.patrickgold.florisboard.app.settings.crypto

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.collectAsState
import kotlinx.coroutines.launch
import org.florisboard.libnative.FlorisNative

private val Emerald = Color(0xFF2DD4BF)
private val Cyan = Color(0xFF38BDF8)
private val Panel = Color(0xFF141C26)
private val PanelBorder = Color(0xFF25313F)
private val Ink = Color(0xFFE6EDF3)
private val InkFaint = Color(0xFF8A9AA9)

private fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

@Composable
fun CrakeEncryptionScreen() = FlorisScreen {
    title = "Encryption"
    previewFieldVisible = false

    content {
        val context = LocalContext.current
        val identity = remember { CrakeIdentityStore(context.filesDir) }
        var myPublicKey by remember { mutableStateOf(identity.publicKey() ?: "") }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            SectionCard(title = "How to use encryption", accent = Emerald) {
                Text(
                    "Crake features zero-trust offline encryption so you can safely send secret messages over any untrusted messaging app or SMS.",
                    color = Ink, fontSize = 12.5.sp, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🔐", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("1. In-Place Keyboard Lock", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Type a message in any app (WhatsApp, SMS, Telegram, Notes), tap the 🔒 icon in the keyboard Smartbar, and Crake seals the text in place into an encrypted block.", color = InkFaint, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🔑", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("2. Public Key Exchange (X25519)", color = Cyan, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Copy your public key below and share it with your contact. Save their public key in Contacts. Messages encrypted to a contact's key can only be read by their device.", color = InkFaint, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🛡️", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("3. Passphrase Encryption (ChaCha20-Poly1305)", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("For quick secret chats without exchanging public keys, use a shared secret passphrase. Recipient enters the same passphrase to unseal.", color = InkFaint, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Text("🔓", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("4. One-Tap Decrypt", color = Color(0xFFC084FC), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text("Copy or select an incoming ciphertext (starts with crake-msg1- or crake-p1-) and tap Decrypt on the keyboard or in the Decrypt card below to reveal the plaintext.", color = InkFaint, fontSize = 11.sp, lineHeight = 14.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionCard(title = "Your identity", accent = Emerald) {
                Text(
                    "Share this public key so others can encrypt messages only you can open. Your private key stays on this device, sealed at rest, and is never shown.",
                    color = InkFaint, fontSize = 12.5.sp,
                )
                Spacer(Modifier.height(10.dp))
                MonoBox(myPublicKey.ifEmpty { "(unavailable)" })
                Spacer(Modifier.height(8.dp))
                Row {
                    PillButton("Copy public key", Emerald) {
                        if (myPublicKey.isNotEmpty()) copyToClipboard(context, "Crake public key", myPublicKey)
                    }
                    Spacer(Modifier.width(8.dp))
                    PillButton("Regenerate", InkFaint) {
                        if (identity.generate()) myPublicKey = identity.publicKey() ?: ""
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            ContactsCard(context)

            Spacer(Modifier.height(14.dp))
            EncryptCard(context, myRecipientHint = myPublicKey)

            Spacer(Modifier.height(14.dp))
            DecryptCard(context, identity)

            Spacer(Modifier.height(14.dp))
            Text(
                "Encryption happens on this device. A passphrase protects a message with a shared secret; a public key encrypts to one person. A short passphrase can be guessed offline, so choose a strong one.",
                color = InkFaint, fontSize = 11.5.sp,
            )
        }
    }
}

@Composable
private fun ContactsCard(context: Context) {
    val prefs by dev.patrickgold.florisboard.app.FlorisPreferenceStore
    val contactsRaw by prefs.internal.crakeContacts.collectAsState()
    val activeKey by prefs.internal.crakeActiveRecipient.collectAsState()
    val activeLabel by prefs.internal.crakeActiveRecipientLabel.collectAsState()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val contacts = remember(contactsRaw) { CrakeContacts.parse(contactsRaw) }

    var newLabel by remember { mutableStateOf("") }
    var newKey by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }

    SectionCard(title = "Contacts", accent = Emerald) {
        Text(
            "Save people's public keys with a name. Pick one to set who the keyboard's in-place Encrypt key writes to - no need to open this screen each time.",
            color = InkFaint, fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(6.dp))
        if (activeKey.isNotBlank()) {
            Text(
                "Encrypting to: ${activeLabel.ifBlank { "a pasted key" }}",
                color = Cyan, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(10.dp))
        }

        contacts.forEach { c ->
            val isActive = c.key.trim() == activeKey.trim()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .background(if (isActive) Color(0xFF16303A) else Color(0xFF0B1016), RoundedCornerShape(8.dp))
                    .border(1.dp, if (isActive) Emerald else PanelBorder, RoundedCornerShape(8.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(c.label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        c.key.take(22) + "…",
                        color = InkFaint, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                    )
                }
                if (!isActive) {
                    PillButton("Message", Emerald) {
                        scope.launch {
                            prefs.internal.crakeActiveRecipient.set(c.key.trim())
                            prefs.internal.crakeActiveRecipientLabel.set(c.label)
                        }
                    }
                } else {
                    Text("Active", color = Emerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "✕",
                    color = InkFaint,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable {
                            scope.launch {
                                prefs.internal.crakeContacts.set(CrakeContacts.serialize(CrakeContacts.remove(contacts, c.key)))
                                if (c.key.trim() == activeKey.trim()) {
                                    prefs.internal.crakeActiveRecipient.set("")
                                    prefs.internal.crakeActiveRecipientLabel.set("")
                                }
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        CrakeField(newLabel, "Name / label", { newLabel = it })
        Spacer(Modifier.height(8.dp))
        CrakeField(newKey, "Their public key (crake-pk1-...)", { newKey = it })
        Spacer(Modifier.height(10.dp))
        PillButton("Add contact", Emerald) {
            val label = newLabel.trim()
            val key = newKey.trim()
            when {
                label.isEmpty() -> addError = "Give them a name"
                !FlorisNative.cryptoEncrypt("publickey", "test", key).ok -> addError = "That doesn't look like a valid public key"
                else -> {
                    scope.launch {
                        prefs.internal.crakeContacts.set(
                            CrakeContacts.serialize(CrakeContacts.upsert(contacts, CrakeContact(label, key)))
                        )
                    }
                    newLabel = ""; newKey = ""; addError = null
                }
            }
        }
        addError?.let { Spacer(Modifier.height(8.dp)); Text(it, color = Color(0xFFF08A8A), fontSize = 12.sp) }
    }
}

@Composable
private fun EncryptCard(context: Context, myRecipientHint: String) {
    var usePassphrase by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard(title = "Encrypt a message", accent = Cyan) {
        SchemeToggle(usePassphrase) { usePassphrase = it }
        Spacer(Modifier.height(10.dp))
        CrakeField(message, "Message to encrypt", { message = it }, minLines = 3)
        Spacer(Modifier.height(8.dp))
        CrakeField(
            secret,
            if (usePassphrase) "Passphrase (shared secret)" else "Recipient public key (crake-pk1-...)",
            { secret = it },
        )
        Spacer(Modifier.height(10.dp))
        PillButton("Encrypt", Cyan) {
            val scheme = if (usePassphrase) "passphrase" else "publickey"
            val r = FlorisNative.cryptoEncrypt(scheme, message, secret.trim())
            if (r.ok) { output = r.value!!; error = null } else { error = r.error; output = "" }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text("Couldn't encrypt: $it", color = Color(0xFFF08A8A), fontSize = 12.sp) }
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MonoBox(output)
            Spacer(Modifier.height(8.dp))
            PillButton("Copy encrypted message", Cyan) { copyToClipboard(context, "Crake encrypted", output) }
        }
    }
}

@Composable
private fun DecryptCard(context: Context, identity: CrakeIdentityStore) {
    var armored by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scheme = FlorisNative.cryptoScheme(armored)

    SectionCard(title = "Decrypt a message", accent = Emerald) {
        CrakeField(armored, "Paste the encrypted message", { armored = it }, minLines = 3)
        if (scheme.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                if (scheme == "passphrase") "Passphrase-protected message detected." else "Public-key message detected - opens with your identity.",
                color = InkFaint, fontSize = 11.5.sp,
            )
        }
        if (scheme == "passphrase") {
            Spacer(Modifier.height(8.dp))
            CrakeField(secret, "Passphrase", { secret = it })
        }
        Spacer(Modifier.height(10.dp))
        PillButton("Decrypt", Emerald) {
            when (scheme) {
                "passphrase" -> {
                    val r = FlorisNative.cryptoDecrypt("passphrase", armored, secret)
                    if (r.ok) { output = r.value!!; error = null } else { error = r.error; output = "" }
                }
                "publickey" -> {
                    val priv = identity.privateKeyHex()
                    if (priv == null) { error = "No identity on this device yet."; output = "" }
                    else {
                        val r = FlorisNative.cryptoDecrypt("publickey", armored, priv)
                        if (r.ok) { output = r.value!!; error = null } else { error = r.error; output = "" }
                    }
                }
                else -> { error = "That is not a Crake encrypted message."; output = "" }
            }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text("Couldn't decrypt: $it", color = Color(0xFFF08A8A), fontSize = 12.sp) }
        if (output.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            MonoBox(output)
            Spacer(Modifier.height(8.dp))
            PillButton("Copy plaintext", Emerald) { copyToClipboard(context, "Crake decrypted", output) }
        }
    }
}

@Composable
private fun SchemeToggle(usePassphrase: Boolean, onChange: (Boolean) -> Unit) {
    Row {
        TogglePill("Passphrase", usePassphrase) { onChange(true) }
        Spacer(Modifier.width(8.dp))
        TogglePill("Public key", !usePassphrase) { onChange(false) }
    }
}

@Composable
private fun TogglePill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (active) Color(0xFF0B1016) else InkFaint,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        modifier = Modifier
            .background(if (active) Cyan else Panel, RoundedCornerShape(8.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionCard(title: String, accent: Color, body: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(14.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(14.dp))
            .padding(16.dp),
    ) {
        Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(10.dp))
        body()
    }
}

@Composable
private fun MonoBox(text: String) {
    Text(
        text,
        color = Ink,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1016), RoundedCornerShape(8.dp))
            .border(1.dp, PanelBorder, RoundedCornerShape(8.dp))
            .padding(12.dp),
    )
}

@Composable
private fun CrakeField(value: String, label: String, onChange: (String) -> Unit, minLines: Int = 1) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        minLines = minLines,
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Emerald,
            unfocusedBorderColor = PanelBorder,
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedLabelColor = Emerald,
            unfocusedLabelColor = InkFaint,
        ),
        shape = RoundedCornerShape(8.dp),
    )
}

@Composable
private fun PillButton(label: String, accent: Color, onClick: () -> Unit) {
    Text(
        label,
        color = Color(0xFF0B1016),
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        modifier = Modifier
            .background(accent, RoundedCornerShape(9.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}
