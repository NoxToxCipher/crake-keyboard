//! Sealed intrusion and session logging for device-adversary defense.
//! Records are encrypted to a public key on write and can only be opened with the private key.

use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{Key, XChaCha20Poly1305, XNonce};
use rand::RngCore;
use sha2::{Digest, Sha512};
use x25519_dalek::{PublicKey, StaticSecret};
use zeroize::Zeroizing;

const EPH_LEN: usize = 32;
const NONCE_LEN: usize = 24;
const TAG_LEN: usize = 16;

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct IntrusionRecord {
    pub timestamp_sec: u64,
    pub event_code: u32,
    pub details: Vec<u8>,
}

impl IntrusionRecord {
    pub fn new(timestamp_sec: u64, event_code: u32, details: &[u8]) -> Self {
        Self {
            timestamp_sec,
            event_code,
            details: details.to_vec(),
        }
    }

    pub fn to_bytes(&self) -> Vec<u8> {
        let mut out = Vec::with_capacity(12 + self.details.len());
        out.extend_from_slice(&self.timestamp_sec.to_le_bytes());
        out.extend_from_slice(&self.event_code.to_le_bytes());
        out.extend_from_slice(&self.details);
        out
    }

    pub fn from_bytes(bytes: &[u8]) -> Option<Self> {
        if bytes.len() < 12 {
            return None;
        }
        let timestamp_sec = u64::from_le_bytes(bytes[0..8].try_into().ok()?);
        let event_code = u32::from_le_bytes(bytes[8..12].try_into().ok()?);
        let details = bytes[12..].to_vec();
        Some(Self {
            timestamp_sec,
            event_code,
            details,
        })
    }
}

fn kdf(shared: &[u8], eph_pub: &[u8; 32], recipient_pub: &[u8; 32]) -> Zeroizing<[u8; 32]> {
    let mut h = Sha512::new();
    h.update(shared);
    h.update(eph_pub);
    h.update(recipient_pub);
    let digest = h.finalize();
    let mut key = Zeroizing::new([0u8; 32]);
    key.copy_from_slice(&digest[..32]);
    key
}

/// Seal a message to recipient public key.
pub fn seal(recipient_pub: &[u8; 32], msg: &[u8]) -> Vec<u8> {
    let eph_secret = StaticSecret::random_from_rng(rand::thread_rng());
    let eph_pub = PublicKey::from(&eph_secret).to_bytes();
    let shared = eph_secret.diffie_hellman(&PublicKey::from(*recipient_pub));
    let key = kdf(shared.as_bytes(), &eph_pub, recipient_pub);

    let cipher = XChaCha20Poly1305::new(Key::from_slice(key.as_ref()));
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);
    let ct = cipher
        .encrypt(XNonce::from_slice(&nonce), Payload { msg, aad: &[] })
        .expect("encryption failure");

    let mut out = Vec::with_capacity(EPH_LEN + NONCE_LEN + ct.len());
    out.extend_from_slice(&eph_pub);
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    out
}

/// Open a sealed message with recipient private key.
pub fn open(secret: &[u8; 32], sealed: &[u8]) -> Option<Vec<u8>> {
    if sealed.len() < EPH_LEN + NONCE_LEN + TAG_LEN {
        return None;
    }
    let eph_pub: [u8; 32] = sealed[..EPH_LEN].try_into().ok()?;
    let nonce = &sealed[EPH_LEN..EPH_LEN + NONCE_LEN];
    let ct = &sealed[EPH_LEN + NONCE_LEN..];

    let sk = StaticSecret::from(*secret);
    let recipient_pub = PublicKey::from(&sk).to_bytes();
    let shared = sk.diffie_hellman(&PublicKey::from(eph_pub));
    let key = kdf(shared.as_bytes(), &eph_pub, &recipient_pub);

    let cipher = XChaCha20Poly1305::new(Key::from_slice(key.as_ref()));
    cipher.decrypt(XNonce::from_slice(nonce), Payload { msg: ct, aad: &[] }).ok()
}

/// Unpack and parse sealed log frames from raw byte buffer with checked arithmetic.
pub fn parse_sealed_records(secret: &[u8; 32], data: &[u8]) -> Vec<IntrusionRecord> {
    let mut out = Vec::new();
    let mut i = 0usize;

    while let Some(hdr_end) = i.checked_add(4) {
        if hdr_end > data.len() {
            break;
        }
        let len = u32::from_le_bytes(data[i..hdr_end].try_into().unwrap_or([0; 4])) as usize;
        let Some(rec_end) = hdr_end.checked_add(len) else { break };
        if rec_end > data.len() {
            break;
        }
        if let Some(pt) = open(secret, &data[hdr_end..rec_end]) {
            if let Some(record) = IntrusionRecord::from_bytes(&pt) {
                out.push(record);
            }
        }
        i = rec_end;
    }

    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_seal_open_roundtrip() {
        let sk = StaticSecret::random_from_rng(rand::thread_rng());
        let pk = PublicKey::from(&sk).to_bytes();
        let sk_bytes = sk.to_bytes();

        let original = b"unauthorized_keyboard_access_attempt";
        let sealed = seal(&pk, original);

        assert_ne!(sealed, original);
        let decrypted = open(&sk_bytes, &sealed).unwrap();
        assert_eq!(decrypted, original);
    }

    #[test]
    fn test_wrong_key_fails() {
        let sk1 = StaticSecret::random_from_rng(rand::thread_rng());
        let pk1 = PublicKey::from(&sk1).to_bytes();

        let sk2 = StaticSecret::random_from_rng(rand::thread_rng());
        let sk2_bytes = sk2.to_bytes();

        let sealed = seal(&pk1, b"secret_record");
        assert!(open(&sk2_bytes, &sealed).is_none());
    }

    #[test]
    fn test_intrusion_records_framing() {
        let sk = StaticSecret::random_from_rng(rand::thread_rng());
        let pk = PublicKey::from(&sk).to_bytes();
        let sk_bytes = sk.to_bytes();

        let rec1 = IntrusionRecord::new(1700000000, 101, b"invalid_pin_entry");
        let rec2 = IntrusionRecord::new(1700000010, 102, b"duress_pin_activated");

        let s1 = seal(&pk, &rec1.to_bytes());
        let s2 = seal(&pk, &rec2.to_bytes());

        let mut stream = Vec::new();
        stream.extend_from_slice(&(s1.len() as u32).to_le_bytes());
        stream.extend_from_slice(&s1);
        stream.extend_from_slice(&(s2.len() as u32).to_le_bytes());
        stream.extend_from_slice(&s2);

        let parsed = parse_sealed_records(&sk_bytes, &stream);
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0], rec1);
        assert_eq!(parsed[1], rec2);
    }
}
