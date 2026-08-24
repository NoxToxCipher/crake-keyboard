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

pub mod ffi_guard;
pub mod intrusion;
pub mod profile;
pub mod sanitizer;
pub mod sync_bundle;
pub mod zeroize_buffer;

pub use ffi_guard::{catch_ffi_panic, checked_slice, FfiError};
pub use intrusion::{open, parse_sealed_records, seal, IntrusionRecord};
pub use profile::{ProfileKind, ProfileManager};
pub use sanitizer::{sanitize_text, sanitize_url, TRACKING_PARAMS};
pub use sync_bundle::{
    chunk_for_optical_qr, create_encrypted_sync_bundle, open_encrypted_sync_bundle,
    reassemble_qr_frames, QrFrame,
};
pub use zeroize_buffer::EphemeralBuffer;

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
