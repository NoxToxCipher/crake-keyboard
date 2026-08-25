//! crake-privacy: Hardened privacy engine for Crake Keyboard.
//! Enforces strict 100% Safe Rust across the entire crate.

#![forbid(unsafe_code)]
#![deny(
    clippy::all,
    clippy::correctness,
    clippy::suspicious,
    clippy::complexity,
    clippy::perf
)]

pub mod boreal_guard;
pub mod ephemeral_clipboard;
pub mod ffi_guard;
pub mod intrusion;
pub mod metascrub;
pub mod profile;
pub mod sanitizer;
pub mod secret_shield;
pub mod sync_bundle;
pub mod zeroize_buffer;

pub use boreal_guard::{BorealScanner, ThreatMatch, DEFAULT_YARA_RULES};
pub use ephemeral_clipboard::{EphemeralClip, EphemeralClipboardSentry};
pub use ffi_guard::{catch_ffi_panic, checked_slice, FfiError};
pub use intrusion::{open, parse_sealed_records, seal, IntrusionRecord};
pub use metascrub::{is_invisible_char, metascrub_text, strip_invisible_characters, MetaScrubResult};
pub use profile::{ProfileKind, ProfileManager};
pub use sanitizer::{sanitize_text, sanitize_url, TRACKING_PARAMS};
pub use secret_shield::{inspect_text, verify_bip39_phrase, SecretKind, ShieldResult, BIP39_WORDLIST};
pub use sync_bundle::{
    chunk_for_optical_qr, create_encrypted_sync_bundle, generate_qr_matrix,
    open_encrypted_sync_bundle, reassemble_qr_frames, QrFrame,
};
pub use zeroize_buffer::EphemeralBuffer;

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
