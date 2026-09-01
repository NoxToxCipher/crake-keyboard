//! The hidden note vault: PIN-addressed encrypted pages behind the keyboard
//! homepage. There is deliberately no notion of a "correct" PIN. Every PIN
//! opens *a* page - the one sealed under that PIN, or a blank one if none
//! exists yet - so the screen can never answer "is this the right PIN?" to
//! someone who took the phone. You give a throwaway PIN under coercion and a
//! blank (or innocuous) page appears, with no way to prove another page
//! exists. This is the same deniable-by-design stance as the tox client's
//! multi-PIN unlock.
//!
//! HONEST LIMITS (this crate never claims protection it does not give):
//!   - A PIN is a handful of digits. Against someone who images the device
//!     and brute-forces the vault offline, a short PIN is weak no matter the
//!     KDF; the stretch below raises the cost but does not make it safe from
//!     forensic capture. What this DOES defeat is the realistic threat: a
//!     person using or handed your unlocked phone. Content is never readable
//!     without the PIN.
//!   - The file reveals how many distinct PINs have saved content (one entry
//!     each), but not which is "real" and not any content. All pages are
//!     cryptographically equal; claim any of them.
//!
//! File layout:  "CNV1" | salt(16) | repeated( len:u32_le | nonce+ct )

use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use rand::RngCore;
use sha2::{Digest, Sha256};
use zeroize::Zeroize;

const MAGIC: &[u8; 4] = b"CNV1";
const SALT_LEN: usize = 16;
const NONCE_LEN: usize = 12;
const KDF_ROUNDS: u32 = 120_000;

/// Derives a 32-byte page key from a PIN and the vault salt. Iterated
/// SHA-256; not a memory-hard KDF (none is vendored), so see the module
/// note on limits. The PIN bytes are wiped from the working buffer.
fn derive_key(pin: &str, salt: &[u8; SALT_LEN]) -> [u8; 32] {
    let mut buf = Vec::with_capacity(SALT_LEN + pin.len() + 8);
    buf.extend_from_slice(b"crake-note\x01");
    buf.extend_from_slice(salt);
    buf.extend_from_slice(pin.as_bytes());
    let mut acc = Sha256::digest(&buf);
    buf.zeroize();
    for _ in 0..KDF_ROUNDS {
        acc = Sha256::digest(acc.as_slice());
    }
    let mut key = [0u8; 32];
    key.copy_from_slice(acc.as_slice());
    key
}

fn seal(key: &[u8; 32], plaintext: &[u8]) -> Vec<u8> {
    let cipher = ChaCha20Poly1305::new(Key::from_slice(key));
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher
        .encrypt(Nonce::from_slice(&nonce), Payload { msg: plaintext, aad: MAGIC })
        .expect("note seal failure");
    let mut out = Vec::with_capacity(NONCE_LEN + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    out
}

fn unseal(key: &[u8; 32], blob: &[u8]) -> Option<Vec<u8>> {
    if blob.len() < NONCE_LEN + 16 {
        return None;
    }
    let cipher = ChaCha20Poly1305::new(Key::from_slice(key));
    cipher
        .decrypt(
            Nonce::from_slice(&blob[..NONCE_LEN]),
            Payload { msg: &blob[NONCE_LEN..], aad: MAGIC },
        )
        .ok()
}

/// Splits a vault file into (salt, entries). Returns None for a malformed or
/// empty vault, so callers treat "no vault yet" as "no page yet".
fn parse(vault: &[u8]) -> Option<([u8; SALT_LEN], Vec<Vec<u8>>)> {
    if vault.len() < MAGIC.len() + SALT_LEN || &vault[..MAGIC.len()] != MAGIC {
        return None;
    }
    let mut salt = [0u8; SALT_LEN];
    salt.copy_from_slice(&vault[MAGIC.len()..MAGIC.len() + SALT_LEN]);
    let mut entries = Vec::new();
    let mut i = MAGIC.len() + SALT_LEN;
    while i + 4 <= vault.len() {
        let len = u32::from_le_bytes([vault[i], vault[i + 1], vault[i + 2], vault[i + 3]]) as usize;
        i += 4;
        if len == 0 || i + len > vault.len() {
            break;
        }
        entries.push(vault[i..i + len].to_vec());
        i += len;
    }
    Some((salt, entries))
}

fn serialize(salt: &[u8; SALT_LEN], entries: &[Vec<u8>]) -> Vec<u8> {
    let mut out = Vec::new();
    out.extend_from_slice(MAGIC);
    out.extend_from_slice(salt);
    for e in entries {
        out.extend_from_slice(&(e.len() as u32).to_le_bytes());
        out.extend_from_slice(e);
    }
    out
}

/// Opens the page sealed under `pin`. Returns the page text, or an empty
/// string when no page exists for this PIN (a fresh, blank page) - never an
/// error, never a signal about whether the PIN is "known".
pub fn note_vault_open(vault: &[u8], pin: &str) -> String {
    let (salt, entries) = match parse(vault) {
        Some(v) => v,
        None => return String::new(),
    };
    let key = derive_key(pin, &salt);
    for entry in &entries {
        if let Some(pt) = unseal(&key, entry) {
            return String::from_utf8_lossy(&pt).into_owned();
        }
    }
    String::new()
}

/// Saves `content` as the page for `pin`, returning the new vault bytes. Any
/// previous page for this PIN is replaced. Saving empty content removes the
/// PIN's page entirely (clearing a secret page leaves no trace of it).
pub fn note_vault_save(vault: &[u8], pin: &str, content: &str) -> Vec<u8> {
    let (salt, entries) = match parse(vault) {
        Some(v) => v,
        None => {
            let mut salt = [0u8; SALT_LEN];
            rand::thread_rng().fill_bytes(&mut salt);
            (salt, Vec::new())
        }
    };
    let key = derive_key(pin, &salt);
    // Keep every entry this key cannot open (other PINs' pages).
    let mut kept: Vec<Vec<u8>> = entries
        .into_iter()
        .filter(|e| unseal(&key, e).is_none())
        .collect();
    if !content.is_empty() {
        kept.push(seal(&key, content.as_bytes()));
    }
    serialize(&salt, &kept)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_pin_reads_back_its_own_page() {
        let v = note_vault_save(&[], "1234", "the raven flies at dusk");
        assert_eq!(note_vault_open(&v, "1234"), "the raven flies at dusk");
    }

    #[test]
    fn an_unknown_pin_opens_a_blank_page_not_an_error() {
        let v = note_vault_save(&[], "1234", "secret");
        assert_eq!(note_vault_open(&v, "9999"), "");
        // And an empty vault yields blank for any PIN.
        assert_eq!(note_vault_open(&[], "0000"), "");
    }

    #[test]
    fn two_pins_hold_independent_pages() {
        let mut v = note_vault_save(&[], "1111", "page one");
        v = note_vault_save(&v, "2222", "page two");
        assert_eq!(note_vault_open(&v, "1111"), "page one");
        assert_eq!(note_vault_open(&v, "2222"), "page two");
    }

    #[test]
    fn resaving_replaces_not_appends() {
        let mut v = note_vault_save(&[], "1234", "first");
        v = note_vault_save(&v, "1234", "second");
        assert_eq!(note_vault_open(&v, "1234"), "second");
        let (_, entries) = parse(&v).unwrap();
        assert_eq!(entries.len(), 1, "one page per PIN");
    }

    #[test]
    fn clearing_removes_the_page() {
        let mut v = note_vault_save(&[], "1234", "gone soon");
        v = note_vault_save(&v, "1234", "");
        assert_eq!(note_vault_open(&v, "1234"), "");
        let (_, entries) = parse(&v).unwrap();
        assert_eq!(entries.len(), 0);
    }

    #[test]
    fn ciphertext_does_not_contain_plaintext() {
        let v = note_vault_save(&[], "1234", "PLAINTEXTMARKER");
        assert!(!v.windows(15).any(|w| w == b"PLAINTEXTMARKER"));
    }

    #[test]
    fn wrong_pin_cannot_read_another_pages_content() {
        let mut v = note_vault_save(&[], "1111", "alpha");
        v = note_vault_save(&v, "2222", "beta");
        // 3333 is unused: blank, and never leaks alpha or beta.
        assert_eq!(note_vault_open(&v, "3333"), "");
    }
}
