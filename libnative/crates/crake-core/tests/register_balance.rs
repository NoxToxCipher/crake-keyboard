//! Frequency-register balance guard.
//!
//! The shipped frequency table was derived from a news-heavy corpus, which
//! rated institutional-politics and conflict-news vocabulary as high as
//! everyday words ("government" 248 vs "thanks" 244, "israeli" 220 vs
//! "testing" 227). In a personal-typing keyboard every frequency-mixing
//! ranker (autocorrect order, glide bonus, flick predictions) inherits that
//! bias — a real user got a news-register word substituted for "testing"
//! (field report, 2026-08-26). These words stay fully typeable and
//! suggestable; they just must not outrank everyday typing. The demotion cap
//! lives in the data (data.json + CORE_DICTIONARY); this test keeps it from
//! regressing when either table is regenerated.

use crake_core::CORE_DICTIONARY;

const CAP: u32 = 140;

const NEWS_REGISTER: &[&str] = &[
    "israeli", "palestinian", "iraqi", "iranian", "syrian", "lebanese",
    "afghan", "kurdish", "serbian", "bosnian", "chechen", "taliban",
    "pentagon", "senate", "senator", "senators", "congressman",
    "congressional", "parliament", "parliamentary", "minister", "ministers",
    "ministry", "chancellor", "legislation", "legislature", "legislative",
    "diplomat", "diplomats", "diplomatic", "embassy", "treaty", "tribunal",
    "militia", "militants", "insurgents", "insurgency", "ceasefire",
    "referendum", "sanctions",
];

/// Two-letter tokens the dictionary may legitimately contain. The shipped
/// table originally held 599 two-letter entries — nearly every letter pair,
/// n-gram corpus noise like "hj" and "kd" at word-level frequencies. Junk
/// entries block autocorrect entirely (an "exact match" is never corrected),
/// which is how split fragments like "kd" survived on screen. Purged
/// 2026-08-27; this guard keeps regenerated tables clean.
const TWO_LETTER_WHITELIST: &[&str] = &[
    "am", "an", "as", "at", "ax", "be", "by", "do", "go", "he", "hi", "id",
    "if", "in", "is", "it", "me", "my", "no", "of", "oh", "ok", "on", "or",
    "ox", "so", "to", "up", "us", "we", "ah", "aw", "eh", "em", "er", "ha",
    "hm", "ho", "la", "lo", "ma", "pa", "uh", "um", "ya", "yo", "ye", "im",
    "ur", "tv", "pc", "uk", "eu", "un", "ai", "io", "os", "ip", "cd", "dj",
    "dr", "mr", "ms", "st", "pm", "km", "kg", "cm", "mm", "ml", "mg", "gb",
    "mb", "kb", "hz", "hp", "ac", "dc", "ft", "mt", "pt", "oz", "lb", "ie",
    "eg",
];

#[test]
fn two_letter_entries_are_words_not_ngram_noise() {
    let violations: Vec<&str> = CORE_DICTIONARY
        .iter()
        .filter(|(w, _)| w.chars().count() == 2 && !TWO_LETTER_WHITELIST.contains(w))
        .map(|(w, _)| *w)
        .collect();
    assert!(
        violations.is_empty(),
        "two-letter n-gram noise in CORE_DICTIONARY: {violations:?}"
    );
}

#[test]
fn news_register_words_do_not_outrank_everyday_typing() {
    let mut violations = Vec::new();
    for &(word, freq) in CORE_DICTIONARY {
        if NEWS_REGISTER.contains(&word) && freq > CAP {
            violations.push(format!("{word} at {freq} (cap {CAP})"));
        }
    }
    assert!(
        violations.is_empty(),
        "news-register words above the frequency cap:\n{}",
        violations.join("\n")
    );
}
