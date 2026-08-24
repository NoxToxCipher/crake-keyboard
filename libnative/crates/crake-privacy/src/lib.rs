//! crake-privacy: Hardened privacy engine for Crake Keyboard.
//! Enforces strict 100% Safe Rust across the entire crate.

#![forbid(unsafe_code)]
#![deny(
    clippy::all,
    clippy::correctness,
    clippy::suspicious,
    clippy::complexity,
    clippy::perf,
    clippy::style
)]
#![warn(missing_docs)]

pub mod ffi_guard;
pub mod sanitizer;
pub mod zeroize_buffer;

pub use ffi_guard::{catch_ffi_panic, checked_slice, FfiError};
pub use sanitizer::{sanitize_text, sanitize_url, TRACKING_PARAMS};
pub use zeroize_buffer::EphemeralBuffer;

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
