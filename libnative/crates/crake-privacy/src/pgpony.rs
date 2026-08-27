//! PGPony: Inline Hardware-Anchored Public Key Cryptography Engine.
//! Powered by Curve25519/X25519 and ChaCha20-Poly1305 AEAD.

use chacha20poly1305::aead::{Aead, KeyInit};
use chacha20poly1305::{ChaCha20Poly1305, Nonce};
use rand::rngs::OsRng;
use sha2::{Digest, Sha256};
use x25519_dalek::{EphemeralSecret, PublicKey, StaticSecret};
use zeroize::Zeroize;

pub const PGPONY_HEADER: &str = "-----BEGIN CRAKE ENCRYPTED MESSAGE-----";
pub const PGPONY_FOOTER: &str = "-----END CRAKE ENCRYPTED MESSAGE-----";
pub const PGPONY_KEY_PREFIX: &str = "crake-pk1-";

/// A generated PGPony Keypair.
#[derive(Debug)]
pub struct PgpKeypair {
    pub private_key_hex: String,
    pub public_key_bech: String,
}

/// Generates a new cryptographically secure X25519 keypair for PGPony.
pub fn generate_keypair() -> PgpKeypair {
    let secret = StaticSecret::random_from_rng(OsRng);
    let public = PublicKey::from(&secret);

    let priv_hex = hex_encode(secret.to_bytes().as_ref());
    let pub_hex = hex_encode(public.as_bytes());

    PgpKeypair {
        private_key_hex: priv_hex,
        public_key_bech: format!("{}{}", PGPONY_KEY_PREFIX, pub_hex),
    }
}

/// Derives the public key string from a 32-byte private key hex.
pub fn derive_public_key(private_key_hex: &str) -> Option<String> {
    let priv_bytes = hex_decode(private_key_hex)?;
    if priv_bytes.len() != 32 {
        return None;
    }
    let mut arr = [0u8; 32];
    arr.copy_from_slice(&priv_bytes);
    let secret = StaticSecret::from(arr);
    let public = PublicKey::from(&secret);
    Some(format!("{}{}", PGPONY_KEY_PREFIX, hex_encode(public.as_bytes())))
}

/// Checks if a string contains an armored PGPony encrypted message.
pub fn is_pgpony_message(text: &str) -> bool {
    text.contains(PGPONY_HEADER) && text.contains(PGPONY_FOOTER)
}

/// Encrypts plaintext for a recipient's public key (X25519 + ChaCha20-Poly1305).
pub fn pgpony_encrypt(plaintext: &str, recipient_pubkey: &str) -> Result<String, String> {
    let clean_pub = recipient_pubkey.trim().strip_prefix(PGPONY_KEY_PREFIX).unwrap_or(recipient_pubkey.trim());
    let pub_bytes = hex_decode(clean_pub).ok_or("Invalid recipient public key hex")?;
    if pub_bytes.len() != 32 {
        return Err("Recipient public key must be exactly 32 bytes".to_string());
    }

    let mut pub_arr = [0u8; 32];
    pub_arr.copy_from_slice(&pub_bytes);
    let peer_public = PublicKey::from(pub_arr);

    // 1. Generate ephemeral X25519 secret and derive public key
    let ephemeral_secret = EphemeralSecret::random_from_rng(OsRng);
    let ephemeral_public = PublicKey::from(&ephemeral_secret);

    // 2. Diffie-Hellman Key Agreement
    let shared_secret = ephemeral_secret.diffie_hellman(&peer_public);

    // 3. Derive 256-bit ChaCha20 key via SHA-256 HKDF-like hash
    let mut hasher = Sha256::new();
    hasher.update(b"CRAKE_PGPONY_V1_KEY_DERIVATION");
    hasher.update(shared_secret.as_bytes());
    let mut derived_key = hasher.finalize();

    // 4. Generate random 12-byte nonce
    let mut nonce_bytes = [0u8; 12];
    rand::RngCore::fill_bytes(&mut OsRng, &mut nonce_bytes);
    let nonce = Nonce::from_slice(&nonce_bytes);

    // 5. Encrypt with ChaCha20-Poly1305 AEAD
    let cipher = ChaCha20Poly1305::new_from_slice(&derived_key)
        .map_err(|e| format!("Cipher init error: {}", e))?;
    let ciphertext = cipher.encrypt(nonce, plaintext.as_bytes())
        .map_err(|e| format!("Encryption error: {}", e))?;

    // Zeroize derived key
    derived_key.as_mut_slice().zeroize();

    // 6. Bundle: [ephemeral_pub (32 bytes) || nonce (12 bytes) || ciphertext_with_tag]
    let mut payload = Vec::with_capacity(32 + 12 + ciphertext.len());
    payload.extend_from_slice(ephemeral_public.as_bytes());
    payload.extend_from_slice(&nonce_bytes);
    payload.extend_from_slice(&ciphertext);

    let base64_payload = base64_encode(&payload);

    let armored = format!(
        "{}\nVersion: Crake-PGPony-v1\n\n{}\n{}",
        PGPONY_HEADER, base64_payload, PGPONY_FOOTER
    );
    Ok(armored)
}

/// Decrypts a PGPony armored message using the recipient's 32-byte private key.
pub fn pgpony_decrypt(armored_text: &str, private_key_hex: &str) -> Result<String, String> {
    if !is_pgpony_message(armored_text) {
        return Err("Text is not an armored PGPony message".to_string());
    }

    let priv_bytes = hex_decode(private_key_hex.trim()).ok_or("Invalid private key hex")?;
    if priv_bytes.len() != 32 {
        return Err("Private key must be exactly 32 bytes".to_string());
    }

    let mut priv_arr = [0u8; 32];
    priv_arr.copy_from_slice(&priv_bytes);
    let static_secret = StaticSecret::from(priv_arr);

    // Extract payload between headers
    let start_idx = armored_text.find(PGPONY_HEADER).ok_or("Missing header")? + PGPONY_HEADER.len();
    let end_idx = armored_text.find(PGPONY_FOOTER).ok_or("Missing footer")?;
    let body = armored_text[start_idx..end_idx].trim();

    // Strip optional "Version: ..." headers
    let b64_body = if let Some(idx) = body.find("\n\n") {
        body[idx + 2..].trim()
    } else {
        body
    };

    let payload = base64_decode(b64_body).ok_or("Failed to decode base64 payload")?;
    if payload.len() < 32 + 12 + 16 {
        return Err("Payload is too short to contain valid ciphertext and MAC".to_string());
    }

    let mut eph_pub_bytes = [0u8; 32];
    eph_pub_bytes.copy_from_slice(&payload[0..32]);
    let ephemeral_pub = PublicKey::from(eph_pub_bytes);

    let nonce_bytes = &payload[32..44];
    let nonce = Nonce::from_slice(nonce_bytes);
    let ciphertext = &payload[44..];

    // Diffie-Hellman Key Agreement
    let shared_secret = static_secret.diffie_hellman(&ephemeral_pub);

    // Derive ChaCha20 key
    let mut hasher = Sha256::new();
    hasher.update(b"CRAKE_PGPONY_V1_KEY_DERIVATION");
    hasher.update(shared_secret.as_bytes());
    let mut derived_key = hasher.finalize();

    let cipher = ChaCha20Poly1305::new_from_slice(&derived_key)
        .map_err(|e| format!("Cipher init error: {}", e))?;
    let plaintext_bytes = cipher.decrypt(nonce, ciphertext)
        .map_err(|_| "Decryption or MAC verification failed (wrong key or corrupted message)".to_string())?;

    derived_key.as_mut_slice().zeroize();

    String::from_utf8(plaintext_bytes).map_err(|_| "Decrypted plaintext is not valid UTF-8".to_string())
}

fn hex_encode(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for &b in bytes {
        use std::fmt::Write;
        let _ = write!(s, "{:02x}", b);
    }
    s
}

fn hex_decode(s: &str) -> Option<Vec<u8>> {
    let clean = s.trim();
    if !clean.len().is_multiple_of(2) {
        return None;
    }
    let mut bytes = Vec::with_capacity(clean.len() / 2);
    for i in (0..clean.len()).step_by(2) {
        let b = u8::from_str_radix(&clean[i..i + 2], 16).ok()?;
        bytes.push(b);
    }
    Some(bytes)
}

const BASE64_CHARS: &[u8] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

fn base64_encode(data: &[u8]) -> String {
    let mut result = String::new();
    let mut i = 0;
    while i < data.len() {
        let b0 = data[i];
        let b1 = if i + 1 < data.len() { data[i + 1] } else { 0 };
        let b2 = if i + 2 < data.len() { data[i + 2] } else { 0 };

        result.push(BASE64_CHARS[(b0 >> 2) as usize] as char);
        result.push(BASE64_CHARS[(((b0 & 3) << 4) | (b1 >> 4)) as usize] as char);
        if i + 1 < data.len() {
            result.push(BASE64_CHARS[(((b1 & 15) << 2) | (b2 >> 6)) as usize] as char);
        } else {
            result.push('=');
        }
        if i + 2 < data.len() {
            result.push(BASE64_CHARS[(b2 & 63) as usize] as char);
        } else {
            result.push('=');
        }
        i += 3;
    }
    result
}

fn base64_decode(s: &str) -> Option<Vec<u8>> {
    let clean: Vec<u8> = s.bytes().filter(|&b| !b.is_ascii_whitespace()).collect();
    if !clean.len().is_multiple_of(4) {
        return None;
    }
    let decode_char = |b: u8| -> Option<u8> {
        match b {
            b'A'..=b'Z' => Some(b - b'A'),
            b'a'..=b'z' => Some(b - b'a' + 26),
            b'0'..=b'9' => Some(b - b'0' + 52),
            b'+' => Some(62),
            b'/' => Some(63),
            b'=' => Some(0),
            _ => None,
        }
    };

    let mut output = Vec::new();
    let mut i = 0;
    while i < clean.len() {
        let c0 = decode_char(clean[i])?;
        let c1 = decode_char(clean[i + 1])?;
        let c2 = decode_char(clean[i + 2])?;
        let c3 = decode_char(clean[i + 3])?;

        output.push((c0 << 2) | (c1 >> 4));
        if clean[i + 2] != b'=' {
            output.push(((c1 & 15) << 4) | (c2 >> 2));
        }
        if clean[i + 3] != b'=' {
            output.push(((c2 & 3) << 6) | c3);
        }
        i += 4;
    }
    Some(output)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_pgpony_roundtrip_encryption_and_decryption() {
        let alice = generate_keypair();
        let bob = generate_keypair();

        let message = "Crake Keyboard PGPony secret message: 0x71C8401344CD24C836015b67272719299478f7B7";

        // Alice encrypts for Bob
        let armored = pgpony_encrypt(message, &bob.public_key_bech).expect("Encryption failed");
        assert!(is_pgpony_message(&armored));

        // Bob decrypts with his private key
        let decrypted = pgpony_decrypt(&armored, &bob.private_key_hex).expect("Decryption failed");
        assert_eq!(message, decrypted);

        // Alice (wrong key) tries to decrypt -> fails cleanly
        let wrong_decrypt = pgpony_decrypt(&armored, &alice.private_key_hex);
        assert!(wrong_decrypt.is_err());
    }
}
