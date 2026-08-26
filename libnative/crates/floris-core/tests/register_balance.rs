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

use floris_core::CORE_DICTIONARY;

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
