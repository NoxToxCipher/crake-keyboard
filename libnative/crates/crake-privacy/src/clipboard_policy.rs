//! Clipboard history policy engine.
//!
//! Owns every decision the clipboard history makes — dedup matching,
//! retention (size limit, age expiry, sensitive TTL), sensitivity
//! classification of incoming text, recency grouping, and mime type
//! matching. The Kotlin side is a shim: it feeds clip metadata in, applies
//! the returned verdicts to the Room database and the system clipboard,
//! and draws the result.
//!
//! All timestamps are unix epoch milliseconds supplied by the caller —
//! this module never reads the clock itself.

use crate::boreal_guard::shared_scanner;
use crate::metascrub::metascrub_text;
use crate::secret_shield::inspect_text;
use crate::telemetry;

/// Item kinds, matching the Kotlin `ItemType` enum values.
pub const KIND_TEXT: u8 = 1;
pub const KIND_IMAGE: u8 = 2;
pub const KIND_VIDEO: u8 = 3;

/// Recency window for the "recent" history group (5 minutes).
pub const RECENT_TIMESPAN_MS: i64 = 300_000;

/// History group assignments returned by [`classify_history`].
pub const GROUP_PINNED: u8 = 0;
pub const GROUP_RECENT: u8 = 1;
pub const GROUP_OTHER: u8 = 2;

/// Metadata for one history clip, as needed by retention decisions.
#[derive(Debug, Clone, Copy)]
pub struct ClipMeta {
    pub id: i64,
    pub created_at_ms: i64,
    pub is_pinned: bool,
    pub is_sensitive: bool,
}

/// Which retention rules to apply during a [`retention_sweep`].
#[derive(Debug, Clone, Copy, Default)]
pub struct RetentionRules {
    /// Cap the number of unpinned items at `max_unpinned`.
    pub limit_enabled: bool,
    pub max_unpinned: usize,
    /// Remove unpinned items older than `expiry_after_ms`.
    pub expiry_enabled: bool,
    pub expiry_after_ms: i64,
    /// Remove sensitive items (pinned included) older than
    /// `sensitive_expiry_after_ms`.
    pub sensitive_expiry_enabled: bool,
    pub sensitive_expiry_after_ms: i64,
}

/// The scrub-and-classify result for text arriving on the clipboard.
#[derive(Debug, Clone)]
pub struct IncomingClip {
    pub cleaned_text: String,
    pub is_sensitive: bool,
}

/// Scrubs incoming clipboard text (invisible characters, URL trackers),
/// scans it with the Boreal YARA engine, and classifies its sensitivity in
/// one pass — the single entry point for the copy path. Every clip that
/// passes through here is counted in the session telemetry the Security
/// Telemetry board renders, so the board's numbers are measured, not copy.
pub fn process_incoming_text(raw: &str) -> IncomingClip {
    let scrubbed = metascrub_text(raw);
    let shield_hit = classify_clip_text(&scrubbed.cleaned_text);
    let boreal_hit = shared_scanner()
        .map(|s| !s.scan_text(&scrubbed.cleaned_text).is_empty())
        .unwrap_or(false);
    telemetry::record_clip(
        scrubbed.invisible_chars_removed as u64,
        scrubbed.urls_sanitized,
        shield_hit,
        boreal_hit,
    );
    IncomingClip {
        cleaned_text: scrubbed.cleaned_text,
        is_sensitive: shield_hit || boreal_hit,
    }
}

/// True when the text should be treated as sensitive clipboard content:
/// either the Secret Shield detects a secret, or it looks like an OTP /
/// verification code.
pub fn classify_clip_text(text: &str) -> bool {
    is_likely_otp_or_sensitive(text) || inspect_text(text).is_secret_detected
}

/// Heuristic for OTPs and verification codes: 4-8 digit numeric codes,
/// `XXX-XXXX` style alphanumeric codes, or accompanying wording.
pub fn is_likely_otp_or_sensitive(text: &str) -> bool {
    let clean = text.trim();
    let len = clean.chars().count();
    if (4..=8).contains(&len) && clean.chars().all(|c| c.is_ascii_digit()) {
        return true;
    }
    if let Some((head, tail)) = clean.split_once('-') {
        let group_ok = |s: &str| {
            (3..=4).contains(&s.chars().count()) && s.chars().all(|c| c.is_ascii_alphanumeric())
        };
        if group_ok(head) && group_ok(tail) {
            return true;
        }
    }
    let lower = clean.to_lowercase();
    lower.contains("otp") || lower.contains("passcode") || lower.contains("verification code")
}

/// Returns the ids of clips the given retention rules say must be removed.
///
/// Rule semantics (each independent, results unioned):
/// - limit: unpinned clips, newest first; everything beyond `max_unpinned`
///   is removed (i.e. the oldest overflow).
/// - expiry: unpinned clips strictly older than `expiry_after_ms`.
/// - sensitive expiry: ALL sensitive clips — pinning does not protect
///   sensitive content — strictly older than `sensitive_expiry_after_ms`.
pub fn retention_sweep(clips: &[ClipMeta], rules: &RetentionRules, now_ms: i64) -> Vec<i64> {
    let mut remove: Vec<i64> = Vec::new();
    let push_unique = |id: i64, out: &mut Vec<i64>| {
        if !out.contains(&id) {
            out.push(id);
        }
    };

    if rules.limit_enabled {
        let mut unpinned: Vec<&ClipMeta> = clips.iter().filter(|c| !c.is_pinned).collect();
        unpinned.sort_by_key(|b| std::cmp::Reverse(b.created_at_ms));
        for clip in unpinned.iter().skip(rules.max_unpinned) {
            push_unique(clip.id, &mut remove);
        }
    }

    if rules.expiry_enabled {
        let cutoff = now_ms - rules.expiry_after_ms;
        for clip in clips.iter().filter(|c| !c.is_pinned) {
            if clip.created_at_ms < cutoff {
                push_unique(clip.id, &mut remove);
            }
        }
    }

    if rules.sensitive_expiry_enabled {
        let cutoff = now_ms - rules.sensitive_expiry_after_ms;
        for clip in clips.iter().filter(|c| c.is_sensitive) {
            if clip.created_at_ms < cutoff {
                push_unique(clip.id, &mut remove);
            }
        }
    }

    remove
}

/// Finds the first history clip that duplicates the incoming one, returning
/// its index. Text clips compare by trimmed content; media clips compare by
/// URI string. `contents` carries the text for text clips and the URI string
/// for media clips (empty string for absent values).
pub fn find_duplicate(
    kinds: &[u8],
    contents: &[&str],
    new_kind: u8,
    new_content: &str,
) -> Option<usize> {
    debug_assert_eq!(kinds.len(), contents.len());
    kinds
        .iter()
        .zip(contents.iter())
        .position(|(&kind, &content)| {
            if kind != new_kind {
                return false;
            }
            if kind == KIND_TEXT {
                content.trim() == new_content.trim()
            } else {
                content == new_content
            }
        })
}

/// Assigns each clip to a display group: pinned, recent (fresher than
/// [`RECENT_TIMESPAN_MS`]), or other.
pub fn classify_history(pinned: &[bool], created_at_ms: &[i64], now_ms: i64) -> Vec<u8> {
    debug_assert_eq!(pinned.len(), created_at_ms.len());
    pinned
        .iter()
        .zip(created_at_ms.iter())
        .map(|(&is_pinned, &created)| {
            if is_pinned {
                GROUP_PINNED
            } else if now_ms.saturating_sub(created) < RECENT_TIMESPAN_MS {
                GROUP_RECENT
            } else {
                GROUP_OTHER
            }
        })
        .collect()
}

/// Compares a concrete MIME type against a desired type that may be a
/// pattern (`*/*` or `type/*`). Ported from AOSP's
/// `ClipDescription.compareMimeTypes`.
pub fn compare_mime_types(concrete: &str, desired: &str) -> bool {
    if desired == "*/*" {
        return true;
    }
    let desired_bytes = desired.as_bytes();
    let concrete_bytes = concrete.as_bytes();
    if let Some(slashpos) = desired_bytes.iter().position(|&b| b == b'/') {
        if slashpos > 0 {
            if desired_bytes.len() == slashpos + 2 && desired_bytes[slashpos + 1] == b'*' {
                if concrete_bytes.len() > slashpos
                    && desired_bytes[..=slashpos] == concrete_bytes[..=slashpos]
                {
                    return true;
                }
            } else if desired == concrete {
                return true;
            }
        }
    }
    false
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clip(id: i64, created: i64, pinned: bool, sensitive: bool) -> ClipMeta {
        ClipMeta {
            id,
            created_at_ms: created,
            is_pinned: pinned,
            is_sensitive: sensitive,
        }
    }

    #[test]
    fn test_otp_heuristic() {
        assert!(is_likely_otp_or_sensitive("123456"));
        assert!(is_likely_otp_or_sensitive(" 4821 "));
        assert!(is_likely_otp_or_sensitive("12345678"));
        assert!(!is_likely_otp_or_sensitive("123"));
        assert!(!is_likely_otp_or_sensitive("123456789"));
        assert!(is_likely_otp_or_sensitive("AB3-9XYZ"));
        assert!(is_likely_otp_or_sensitive("abc-def"));
        assert!(!is_likely_otp_or_sensitive("ab-cdef"));
        assert!(!is_likely_otp_or_sensitive("well-known"));
        assert!(is_likely_otp_or_sensitive("Your OTP is ready"));
        assert!(is_likely_otp_or_sensitive("enter this passcode"));
        assert!(is_likely_otp_or_sensitive("Verification Code: see above"));
        assert!(!is_likely_otp_or_sensitive("hello world"));
        assert!(!is_likely_otp_or_sensitive("meet at 10am"));
    }

    #[test]
    fn test_process_incoming_text_scrubs_and_classifies() {
        let plain = process_incoming_text("just a note");
        assert_eq!(plain.cleaned_text, "just a note");
        assert!(!plain.is_sensitive);

        let otp = process_incoming_text("948302");
        assert!(otp.is_sensitive);

        // Pipe characters must survive the scrub untouched.
        let piped = process_incoming_text("a | b | c");
        assert_eq!(piped.cleaned_text, "a | b | c");
    }

    #[test]
    fn boreal_fires_on_a_card_number_through_the_live_path() {
        // The Visa test number is 16 contiguous digits: too long for the
        // OTP heuristic, so if this clip comes back sensitive it is Boreal
        // (or the shield) that fired — the same path a real paste takes.
        let clip = process_incoming_text("card: 4111111111111111");
        assert!(clip.is_sensitive, "Credit_Card_Pattern must mark the clip sensitive");
        let direct = shared_scanner()
            .expect("Boreal rules must compile")
            .scan_text("4111111111111111");
        assert!(
            direct.iter().any(|m| m.rule_name == "Credit_Card_Pattern"),
            "expected Credit_Card_Pattern, got {direct:?}"
        );
    }

    #[test]
    fn boreal_fires_on_ssn_and_confidential_stamp() {
        let scanner = shared_scanner().expect("Boreal rules must compile");
        assert!(scanner
            .scan_text("ssn is 123-45-6789 ok")
            .iter()
            .any(|m| m.rule_name == "US_SSN_Pattern"));
        assert!(scanner
            .scan_text("marked PROPRIETARY AND CONFIDENTIAL do not fwd")
            .iter()
            .any(|m| m.rule_name == "Confidential_Document_Stamp"));
        assert!(process_incoming_text("ssn is 123-45-6789 ok").is_sensitive);
    }

    #[test]
    fn boreal_stays_quiet_on_ordinary_text() {
        // Negative control: everyday clips must pass clean, or sensitivity
        // becomes noise and the sensitive-TTL sweep starts eating history.
        let scanner = shared_scanner().expect("Boreal rules must compile");
        for text in [
            "picking up milk at 5, want anything?",
            "https://example.com/article?id=42",
            "meeting moved to Thursday arvo",
            "the build is green, shipping now",
        ] {
            assert!(scanner.scan_text(text).is_empty(), "false positive on: {text}");
            assert!(!process_incoming_text(text).is_sensitive, "sensitive on: {text}");
        }
    }

    #[test]
    fn telemetry_counts_what_the_pipeline_actually_saw() {
        // Counters are process-global and other tests also feed the
        // pipeline, so assert deltas as at-least rather than exactly.
        let before = crate::telemetry::snapshot(shared_scanner().is_some());
        process_incoming_text("card: 4111111111111111");
        process_incoming_text("harmless words");
        let after = crate::telemetry::snapshot(shared_scanner().is_some());
        assert!(after.clips_processed >= before.clips_processed + 2);
        assert!(after.boreal_hits >= before.boreal_hits + 1);
        assert!(after.secrets_caught >= before.secrets_caught);
        assert!(after.boreal_ready, "rules compiled, engine must report ready");
    }

    #[test]
    fn test_retention_limit_removes_oldest_overflow() {
        let clips = vec![
            clip(1, 5000, false, false),
            clip(2, 4000, false, false),
            clip(3, 3000, true, false), // pinned: never counted
            clip(4, 2000, false, false),
            clip(5, 1000, false, false),
        ];
        let rules = RetentionRules {
            limit_enabled: true,
            max_unpinned: 2,
            ..Default::default()
        };
        let removed = retention_sweep(&clips, &rules, 10_000);
        assert_eq!(removed, vec![4, 5]);
    }

    #[test]
    fn test_retention_age_expiry_skips_pinned() {
        let clips = vec![
            clip(1, 9000, false, false),
            clip(2, 1000, false, false),
            clip(3, 1000, true, false),
        ];
        let rules = RetentionRules {
            expiry_enabled: true,
            expiry_after_ms: 5000,
            ..Default::default()
        };
        let removed = retention_sweep(&clips, &rules, 10_000);
        assert_eq!(removed, vec![2]);
    }

    #[test]
    fn test_retention_sensitive_expiry_ignores_pinning() {
        let clips = vec![
            clip(1, 1000, true, true),
            clip(2, 1000, false, true),
            clip(3, 1000, true, false),
            clip(4, 9500, false, true), // too fresh
        ];
        let rules = RetentionRules {
            sensitive_expiry_enabled: true,
            sensitive_expiry_after_ms: 1000,
            ..Default::default()
        };
        let removed = retention_sweep(&clips, &rules, 10_000);
        assert_eq!(removed, vec![1, 2]);
    }

    #[test]
    fn test_retention_rules_union_without_duplicates() {
        let clips = vec![
            clip(1, 8000, false, false),
            clip(2, 500, false, true), // hit by both expiry and sensitive TTL
        ];
        let rules = RetentionRules {
            limit_enabled: true,
            max_unpinned: 1,
            expiry_enabled: true,
            expiry_after_ms: 5000,
            sensitive_expiry_enabled: true,
            sensitive_expiry_after_ms: 2000,
            ..Default::default()
        };
        let removed = retention_sweep(&clips, &rules, 10_000);
        assert_eq!(removed, vec![2]);
    }

    #[test]
    fn test_boundary_is_strictly_older_than_cutoff() {
        // created == cutoff must survive (Kotlin used strict <).
        let clips = vec![clip(1, 5000, false, false)];
        let rules = RetentionRules {
            expiry_enabled: true,
            expiry_after_ms: 5000,
            ..Default::default()
        };
        assert!(retention_sweep(&clips, &rules, 10_000).is_empty());
        assert_eq!(retention_sweep(&clips, &rules, 10_001), vec![1]);
    }

    #[test]
    fn test_find_duplicate_text_trims() {
        let kinds = [KIND_TEXT, KIND_TEXT, KIND_IMAGE];
        let contents = ["hello", "  spaced  ", "content://media/1"];
        assert_eq!(find_duplicate(&kinds, &contents, KIND_TEXT, "spaced"), Some(1));
        assert_eq!(find_duplicate(&kinds, &contents, KIND_TEXT, "hello "), Some(0));
        assert_eq!(find_duplicate(&kinds, &contents, KIND_TEXT, "absent"), None);
    }

    #[test]
    fn test_find_duplicate_media_exact_and_kind_gated() {
        let kinds = [KIND_TEXT, KIND_IMAGE];
        let contents = ["content://media/1", "content://media/1"];
        // Same URI but different kind must not match a VIDEO query.
        assert_eq!(
            find_duplicate(&kinds, &contents, KIND_VIDEO, "content://media/1"),
            None
        );
        assert_eq!(
            find_duplicate(&kinds, &contents, KIND_IMAGE, "content://media/1"),
            Some(1)
        );
    }

    #[test]
    fn test_classify_history_groups() {
        let pinned = [true, false, false];
        let created = [0, 9_800_000, 9_000_000];
        let groups = classify_history(&pinned, &created, 10_000_000);
        assert_eq!(groups, vec![GROUP_PINNED, GROUP_RECENT, GROUP_OTHER]);
    }

    #[test]
    fn test_compare_mime_types() {
        assert!(compare_mime_types("image/png", "*/*"));
        assert!(compare_mime_types("image/png", "image/*"));
        assert!(compare_mime_types("image/png", "image/png"));
        assert!(!compare_mime_types("image/png", "video/*"));
        assert!(!compare_mime_types("image/png", "image/jpeg"));
        assert!(!compare_mime_types("image", "image/*"));
        assert!(!compare_mime_types("text/plain", "/plain"));
        assert!(!compare_mime_types("", "image/*"));
    }
}
