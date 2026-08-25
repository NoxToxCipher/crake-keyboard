//! Ephemeral Clipboard Auto-Destruct & Zeroization Sentry.
//! Manages in-memory clipboard clips with strict TTLs and cryptographic zeroization on expiry.

use zeroize::{Zeroize, ZeroizeOnDrop};

/// A single clipboard record with cryptographic memory zeroization on drop.
#[derive(Debug, Clone, Zeroize, ZeroizeOnDrop)]
pub struct EphemeralClip {
    pub id: u64,
    pub content: String,
    pub created_at_ms: u64,
    pub ttl_ms: u64,
    pub is_sensitive: bool,
}

impl EphemeralClip {
    pub fn new(id: u64, content: String, created_at_ms: u64, ttl_ms: u64, is_sensitive: bool) -> Self {
        Self {
            id,
            content,
            created_at_ms,
            ttl_ms,
            is_sensitive,
        }
    }

    #[inline]
    pub fn is_expired(&self, current_time_ms: u64) -> bool {
        if self.ttl_ms == 0 {
            return false; // 0 = persistent / no auto-destruct
        }
        current_time_ms.saturating_sub(self.created_at_ms) >= self.ttl_ms
    }
}

/// Sentry that tracks active clips, checks TTL expirations, and securely purges sensitive items.
#[derive(Debug, Default)]
pub struct EphemeralClipboardSentry {
    clips: Vec<EphemeralClip>,
}

impl EphemeralClipboardSentry {
    pub fn new() -> Self {
        Self { clips: Vec::new() }
    }

    pub fn insert_clip(&mut self, clip: EphemeralClip) {
        // Remove existing clip with same ID or content
        self.clips.retain(|c| c.id != clip.id && c.content != clip.content);
        self.clips.push(clip);
    }

    /// Purges all expired clips and returns the IDs of the purged clips.
    /// The memory of purged clips is automatically overwritten with zeroes.
    pub fn purge_expired(&mut self, current_time_ms: u64) -> Vec<u64> {
        let mut purged_ids = Vec::new();
        let mut remaining = Vec::with_capacity(self.clips.len());

        for mut clip in self.clips.drain(..) {
            if clip.is_expired(current_time_ms) {
                purged_ids.push(clip.id);
                clip.zeroize();
            } else {
                remaining.push(clip);
            }
        }

        self.clips = remaining;
        purged_ids
    }

    /// Instant panic/duress nuke: immediately zeroizes and purges all clips.
    pub fn nuke_all(&mut self) -> Vec<u64> {
        let mut ids = Vec::new();
        for mut clip in self.clips.drain(..) {
            ids.push(clip.id);
            clip.zeroize();
        }
        ids
    }

    pub fn active_count(&self) -> usize {
        self.clips.len()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_clip_ttl_expiry() {
        let clip_sensitive = EphemeralClip::new(1, "secret_seed_phrase".to_string(), 1000, 30_000, true);
        assert!(!clip_sensitive.is_expired(1000));
        assert!(!clip_sensitive.is_expired(30_999));
        assert!(clip_sensitive.is_expired(31_000));
        assert!(clip_sensitive.is_expired(50_000));

        let clip_persistent = EphemeralClip::new(2, "public_link".to_string(), 1000, 0, false);
        assert!(!clip_persistent.is_expired(999_999));
    }

    #[test]
    fn test_sentry_purge_and_zeroize() {
        let mut sentry = EphemeralClipboardSentry::new();
        sentry.insert_clip(EphemeralClip::new(101, "otp_code_123456".to_string(), 1000, 30_000, true));
        sentry.insert_clip(EphemeralClip::new(102, "regular_text".to_string(), 1000, 60_000, false));

        // At T = 15s, nothing expired
        let purged_15s = sentry.purge_expired(16_000);
        assert!(purged_15s.is_empty());
        assert_eq!(sentry.active_count(), 2);

        // At T = 35s, OTP clip expired
        let purged_35s = sentry.purge_expired(36_000);
        assert_eq!(purged_35s, vec![101]);
        assert_eq!(sentry.active_count(), 1);

        // At T = 65s, regular clip expired
        let purged_65s = sentry.purge_expired(66_000);
        assert_eq!(purged_65s, vec![102]);
        assert_eq!(sentry.active_count(), 0);
    }

    #[test]
    fn test_sentry_nuke_all() {
        let mut sentry = EphemeralClipboardSentry::new();
        sentry.insert_clip(EphemeralClip::new(1, "secret_1".to_string(), 1000, 60_000, true));
        sentry.insert_clip(EphemeralClip::new(2, "secret_2".to_string(), 1000, 60_000, true));

        let nuked_ids = sentry.nuke_all();
        assert_eq!(nuked_ids, vec![1, 2]);
        assert_eq!(sentry.active_count(), 0);
    }
}
