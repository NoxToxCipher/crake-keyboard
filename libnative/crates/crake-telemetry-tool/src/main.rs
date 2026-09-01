//! Developer-side tool for the encrypted tester-telemetry sprint.
//!
//!   crake-telemetry keygen                 -> prints a fresh keypair (hex)
//!   crake-telemetry pubkey-array <pubhex>  -> Rust byte-array literal for the app
//!   crake-telemetry decrypt <privkey.hex>  -> reads base64 sealed blobs on
//!                                             stdin (one per line) and prints
//!                                             each decrypted bundle
//!
//! The private key stays on your machine. The app only ever carries the
//! PUBLIC key, so a phone (or anyone watching the transport) can seal a
//! bundle but cannot open one. Fetch the sealed blobs however you like, e.g.
//!   curl -s "https://ntfy.sh/<topic>/json?poll=1" | jq -r 'select(.event=="message").message' | crake-telemetry decrypt priv.hex

use std::io::{BufRead, Write};

use rand::rngs::OsRng;
use x25519_dalek::{PublicKey, StaticSecret};

fn to_hex(b: &[u8]) -> String {
    b.iter().map(|x| format!("{x:02x}")).collect()
}

fn from_hex(s: &str) -> Option<Vec<u8>> {
    let s = s.trim();
    if s.len() % 2 != 0 {
        return None;
    }
    (0..s.len() / 2)
        .map(|i| u8::from_str_radix(&s[i * 2..i * 2 + 2], 16).ok())
        .collect()
}

// Minimal standard-base64 decoder (no dependency).
fn b64_decode(s: &str) -> Option<Vec<u8>> {
    fn val(c: u8) -> Option<u8> {
        match c {
            b'A'..=b'Z' => Some(c - b'A'),
            b'a'..=b'z' => Some(c - b'a' + 26),
            b'0'..=b'9' => Some(c - b'0' + 52),
            b'+' => Some(62),
            b'/' => Some(63),
            _ => None,
        }
    }
    let clean: Vec<u8> = s.bytes().filter(|&c| c != b'=' && !c.is_ascii_whitespace()).collect();
    let mut out = Vec::with_capacity(clean.len() * 3 / 4);
    for chunk in clean.chunks(4) {
        let mut acc = 0u32;
        let mut bits = 0;
        for &c in chunk {
            acc = (acc << 6) | val(c)? as u32;
            bits += 6;
        }
        acc <<= 32 - bits;
        let nbytes = bits / 8;
        for i in 0..nbytes {
            out.push((acc >> (24 - i * 8)) as u8);
        }
    }
    Some(out)
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let cmd = args.get(1).map(String::as_str).unwrap_or("");
    match cmd {
        "keygen" => {
            let sk = StaticSecret::random_from_rng(OsRng);
            let pk = PublicKey::from(&sk);
            eprintln!("Keep the PRIVATE key secret and off the phone and out of the repo.");
            println!("private {}", to_hex(&sk.to_bytes()));
            println!("public  {}", to_hex(pk.as_bytes()));
        }
        "derive-pub" => {
            let Some(sk_bytes) = args.get(2).and_then(|p| std::fs::read_to_string(p).ok()).and_then(|s| from_hex(&s)) else {
                eprintln!("usage: crake-telemetry derive-pub <privkey.hex-file>");
                std::process::exit(2);
            };
            let arr: [u8; 32] = sk_bytes.try_into().expect("private key must be 32 bytes");
            let pk = PublicKey::from(&StaticSecret::from(arr));
            println!("{}", to_hex(pk.as_bytes()));
        }
        "pubkey-array" => {
            let Some(pk) = args.get(2).and_then(|s| from_hex(s)) else {
                eprintln!("usage: crake-telemetry pubkey-array <pubhex>");
                std::process::exit(2);
            };
            let body: Vec<String> = pk.iter().map(|b| format!("0x{b:02x}")).collect();
            println!("[{}]", body.join(", "));
        }
        "decrypt" => {
            let Some(priv_bytes) = args.get(2).and_then(|p| std::fs::read_to_string(p).ok()).and_then(|s| from_hex(&s)) else {
                eprintln!("usage: crake-telemetry decrypt <privkey.hex-file>");
                std::process::exit(2);
            };
            let mut secret = [0u8; 32];
            if priv_bytes.len() != 32 {
                eprintln!("private key must be 32 bytes (64 hex chars)");
                std::process::exit(2);
            }
            secret.copy_from_slice(&priv_bytes);
            let stdin = std::io::stdin();
            let stdout = std::io::stdout();
            let mut out = stdout.lock();
            let mut n_ok = 0u32;
            let mut n_bad = 0u32;
            for line in stdin.lock().lines().map_while(Result::ok) {
                if line.trim().is_empty() {
                    continue;
                }
                let sealed = match b64_decode(&line) {
                    Some(s) => s,
                    None => {
                        n_bad += 1;
                        continue;
                    }
                };
                match crake_privacy::intrusion::open(&secret, &sealed) {
                    Some(pt) => {
                        n_ok += 1;
                        let _ = out.write_all(&pt);
                        let _ = out.write_all(b"\n");
                    }
                    None => n_bad += 1,
                }
            }
            eprintln!("decrypted {n_ok} bundle(s), {n_bad} unreadable");
        }
        _ => {
            eprintln!("commands: keygen | pubkey-array <pubhex> | decrypt <privkey.hex-file>");
            std::process::exit(2);
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // NOTE: the developer's PRIVATE key is deliberately NOT in this repo.
    // The "does the embedded app pubkey match your saved private key" check
    // is run locally against the key file, never committed. These tests use
    // ephemeral keys so they prove the crypto without holding a secret.

    #[test]
    fn a_bundle_sealed_to_a_key_opens_with_its_private_key_through_base64() {
        let sk = StaticSecret::random_from_rng(OsRng);
        let pk = PublicKey::from(&sk).to_bytes();
        let sk_bytes = sk.to_bytes();
        let bundle = br#"{"testerName":"Charlton","records":[]}"#;
        let sealed = crake_privacy::intrusion::seal(&pk, bundle);
        // The transport carries base64; exercise the tool's own decode path.
        let b64 = base64_encode(&sealed);
        let decoded = b64_decode(&b64).unwrap();
        let opened = crake_privacy::intrusion::open(&sk_bytes, &decoded).unwrap();
        assert_eq!(opened, bundle);
    }

    #[test]
    fn a_different_private_key_cannot_open_the_bundle() {
        let recipient = StaticSecret::random_from_rng(OsRng);
        let pk = PublicKey::from(&recipient).to_bytes();
        let attacker = StaticSecret::random_from_rng(OsRng).to_bytes();
        let sealed = crake_privacy::intrusion::seal(&pk, b"secret");
        assert!(crake_privacy::intrusion::open(&attacker, &sealed).is_none());
    }

    fn base64_encode(data: &[u8]) -> String {
        const T: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
        let mut out = String::new();
        for chunk in data.chunks(3) {
            let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
            let n = ((b[0] as u32) << 16) | ((b[1] as u32) << 8) | b[2] as u32;
            out.push(T[(n >> 18 & 63) as usize] as char);
            out.push(T[(n >> 12 & 63) as usize] as char);
            out.push(if chunk.len() > 1 { T[(n >> 6 & 63) as usize] as char } else { '=' });
            out.push(if chunk.len() > 2 { T[(n & 63) as usize] as char } else { '=' });
        }
        out
    }
}
