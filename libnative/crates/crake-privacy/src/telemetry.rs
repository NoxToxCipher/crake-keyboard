//! Session-scope privacy telemetry: real counters behind the Security
//! Telemetry board. Every number shown to the user MUST come from here —
//! a hardcoded status string is an unearned claim (the board shipped with
//! "40+ STRIPPED" as a literal, which is exactly the reassuring-over-honest
//! pattern this suite defines itself against). Counters reset with the
//! process and the UI labels them as session-scope, which is the honest
//! framing for state we do not persist.

use std::sync::atomic::{AtomicU64, Ordering};

static CLIPS_PROCESSED: AtomicU64 = AtomicU64::new(0);
static INVISIBLE_CHARS_REMOVED: AtomicU64 = AtomicU64::new(0);
static URLS_SANITIZED: AtomicU64 = AtomicU64::new(0);
static SECRETS_CAUGHT: AtomicU64 = AtomicU64::new(0);
static BOREAL_HITS: AtomicU64 = AtomicU64::new(0);

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct PrivacyTelemetry {
    pub clips_processed: u64,
    pub invisible_chars_removed: u64,
    pub urls_sanitized: u64,
    pub secrets_caught: u64,
    pub boreal_hits: u64,
    /// Whether the Boreal scanner compiled its rules and is actually
    /// scanning. False must render as a dormant/failed state in the UI,
    /// never as "SCANNING".
    pub boreal_ready: bool,
}

pub fn record_clip(invisible_removed: u64, urls_sanitized: bool, secret: bool, boreal_hit: bool) {
    CLIPS_PROCESSED.fetch_add(1, Ordering::Relaxed);
    INVISIBLE_CHARS_REMOVED.fetch_add(invisible_removed, Ordering::Relaxed);
    if urls_sanitized {
        URLS_SANITIZED.fetch_add(1, Ordering::Relaxed);
    }
    if secret {
        SECRETS_CAUGHT.fetch_add(1, Ordering::Relaxed);
    }
    if boreal_hit {
        BOREAL_HITS.fetch_add(1, Ordering::Relaxed);
    }
}

pub fn snapshot(boreal_ready: bool) -> PrivacyTelemetry {
    PrivacyTelemetry {
        clips_processed: CLIPS_PROCESSED.load(Ordering::Relaxed),
        invisible_chars_removed: INVISIBLE_CHARS_REMOVED.load(Ordering::Relaxed),
        urls_sanitized: URLS_SANITIZED.load(Ordering::Relaxed),
        secrets_caught: SECRETS_CAUGHT.load(Ordering::Relaxed),
        boreal_hits: BOREAL_HITS.load(Ordering::Relaxed),
        boreal_ready,
    }
}

#[cfg(test)]
pub fn reset_for_test() {
    CLIPS_PROCESSED.store(0, Ordering::Relaxed);
    INVISIBLE_CHARS_REMOVED.store(0, Ordering::Relaxed);
    URLS_SANITIZED.store(0, Ordering::Relaxed);
    SECRETS_CAUGHT.store(0, Ordering::Relaxed);
    BOREAL_HITS.store(0, Ordering::Relaxed);
}
