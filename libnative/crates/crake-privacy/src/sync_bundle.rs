//! Encrypted Sync Bundle for Optical Air-Gap (Animated QR) and NFC back-to-back transfer.

use chacha20poly1305::aead::{Aead, KeyInit, Payload};
use chacha20poly1305::{ChaCha20Poly1305, Key, Nonce};
use rand::RngCore;

const NONCE_LEN: usize = 12;

/// A frame chunk formatted for optical animated QR transmission.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct QrFrame {
    pub index: u16,
    pub total: u16,
    pub payload: Vec<u8>,
}

impl QrFrame {
    pub fn to_string_repr(&self) -> String {
        format!("CRAKE:{}/{}:{}", self.index + 1, self.total, hex_encode(&self.payload))
    }

    pub fn parse_string_repr(s: &str) -> Option<Self> {
        let parts: Vec<&str> = s.split(':').collect();
        if parts.len() != 3 || parts[0] != "CRAKE" {
            return None;
        }
        let index_parts: Vec<&str> = parts[1].split('/').collect();
        if index_parts.len() != 2 {
            return None;
        }
        let current: u16 = index_parts[0].parse().ok()?;
        let total: u16 = index_parts[1].parse().ok()?;
        if current == 0 || current > total {
            return None;
        }
        let payload = hex_decode(parts[2])?;
        Some(Self {
            index: current - 1,
            total,
            payload,
        })
    }
}

/// Encrypts raw synchronization data using ChaCha20-Poly1305.
pub fn create_encrypted_sync_bundle(key: &[u8; 32], plaintext: &[u8]) -> Vec<u8> {
    let cipher = ChaCha20Poly1305::new(Key::from_slice(key));
    let mut nonce = [0u8; NONCE_LEN];
    rand::thread_rng().fill_bytes(&mut nonce);

    let ct = cipher
        .encrypt(Nonce::from_slice(&nonce), Payload { msg: plaintext, aad: &[] })
        .expect("sync bundle encryption failure");

    let mut out = Vec::with_capacity(NONCE_LEN + ct.len());
    out.extend_from_slice(&nonce);
    out.extend_from_slice(&ct);
    out
}

/// Decrypts and verifies an encrypted sync bundle using ChaCha20-Poly1305.
pub fn open_encrypted_sync_bundle(key: &[u8; 32], bundle: &[u8]) -> Option<Vec<u8>> {
    if bundle.len() < NONCE_LEN + 16 {
        return None;
    }
    let nonce = &bundle[..NONCE_LEN];
    let ct = &bundle[NONCE_LEN..];

    let cipher = ChaCha20Poly1305::new(Key::from_slice(key));
    cipher.decrypt(Nonce::from_slice(nonce), Payload { msg: ct, aad: &[] }).ok()
}

/// Chunks an encrypted sync bundle into optical QR frames of maximum byte size `chunk_size`.
pub fn chunk_for_optical_qr(bundle: &[u8], chunk_size: usize) -> Vec<QrFrame> {
    if bundle.is_empty() || chunk_size == 0 {
        return Vec::new();
    }

    let chunks: Vec<&[u8]> = bundle.chunks(chunk_size).collect();
    let total = chunks.len() as u16;

    chunks
        .into_iter()
        .enumerate()
        .map(|(i, c)| QrFrame {
            index: i as u16,
            total,
            payload: c.to_vec(),
        })
        .collect()
}

/// Reassembles received QR frames into the full encrypted sync bundle once all frames are present.
pub fn reassemble_qr_frames(frames: &[QrFrame]) -> Option<Vec<u8>> {
    if frames.is_empty() {
        return None;
    }
    let total = frames[0].total as usize;
    if frames.len() < total {
        return None;
    }

    let mut slots: Vec<Option<Vec<u8>>> = vec![None; total];
    for f in frames {
        let idx = f.index as usize;
        if idx < total {
            slots[idx] = Some(f.payload.clone());
        }
    }

    let mut out = Vec::new();
    for slot in slots {
        out.extend(slot?);
    }
    Some(out)
}

/// Generates a binary string representation of a QR code for given text.
/// Format: `"<width>:<binary_string>"` where '1' is a dark module and '0' is light.
pub fn generate_qr_matrix(data: &str) -> Option<String> {
    use qrcode::{EcLevel, QrCode};
    let code = QrCode::with_error_correction_level(data.as_bytes(), EcLevel::M).ok()?;
    let width = code.width();
    let colors = code.to_colors();
    let mut bits = String::with_capacity(width * width);
    for c in colors {
        if c == qrcode::Color::Dark {
            bits.push('1');
        } else {
            bits.push('0');
        }
    }
    Some(format!("{}:{}", width, bits))
}

fn hex_encode(bytes: &[u8]) -> String {
    let mut s = String::with_capacity(bytes.len() * 2);
    for b in bytes {
        s.push_str(&format!("{:02x}", b));
    }
    s
}

fn hex_decode(s: &str) -> Option<Vec<u8>> {
    if !s.len().is_multiple_of(2) {
        return None;
    }
    let mut bytes = Vec::with_capacity(s.len() / 2);
    for i in (0..s.len()).step_by(2) {
        let byte = u8::from_str_radix(&s[i..i + 2], 16).ok()?;
        bytes.push(byte);
    }
    Some(bytes)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_sync_bundle_encryption_roundtrip() {
        let key = [42u8; 32];
        let original_data = b"crake_dictionary_snapshot: [secret1, secret2, secret3]";

        let encrypted = create_encrypted_sync_bundle(&key, original_data);
        assert_ne!(encrypted, original_data);

        let decrypted = open_encrypted_sync_bundle(&key, &encrypted).unwrap();
        assert_eq!(decrypted, original_data);
    }

    #[test]
    fn test_optical_qr_chunking_and_reassembly() {
        let raw_bundle = vec![0xAB; 250];
        let frames = chunk_for_optical_qr(&raw_bundle, 64);
        assert_eq!(frames.len(), 4);

        for f in &frames {
            let s = f.to_string_repr();
            let parsed = QrFrame::parse_string_repr(&s).unwrap();
            assert_eq!(*f, parsed);
        }

        let reassembled = reassemble_qr_frames(&frames).unwrap();
        assert_eq!(reassembled, raw_bundle);
    }

    #[test]
    fn test_generate_qr_matrix() {
        let text = "CRAKE:1/1:deadbeef";
        let matrix = generate_qr_matrix(text).unwrap();
        let parts: Vec<&str> = matrix.split(':').collect();
        assert_eq!(parts.len(), 2);
        let width: usize = parts[0].parse().unwrap();
        assert!(width >= 21);
        assert_eq!(parts[1].len(), width * width);
    }
}
