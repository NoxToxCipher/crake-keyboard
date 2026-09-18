//! Sentinel: a common word typed correctly is never auto-replaced. Field
//! report 2026-09-18: "in" -> "that", "that" -> "in", "with" -> "that" —
//! poisoned personal corrections recorded by the pre-2026-09-13 revert path
//! and made live when the map started applying. This test covers every
//! shipped word at corpus >= 236, alone and after common previous words,
//! and the one documented exception class (bare contractions such as
//! "im" -> "I'm", "dont" -> "don't", which are not everyday words).

use crake_core::NlpEngine;

fn engine() -> (NlpEngine, Vec<(String, u32)>) {
    let mut e = NlpEngine::new();
    let mut words = Vec::new();
    let dict = std::fs::read(concat!(env!("CARGO_MANIFEST_DIR"), "/../../../app/src/main/assets/ime/dict/data.crkd")).expect("dict");
    crake_core::parse_dict_blob(&dict, |w, f| { e.trie.insert(w, f); e.corpus_insert(w, f); words.push((w.to_string(), f)); }).expect("parse");
    let big = std::fs::read(concat!(env!("CARGO_MANIFEST_DIR"), "/../../../app/src/main/assets/ime/dict/bigrams.crkb")).expect("bigrams");
    e.load_bigrams(&big).expect("bigram parse");
    (e, words)
}

/// The documented context flips: homophone arbitration with strong bigram
/// evidence ("more then" -> than, "and than" -> then) and the curated
/// impossible-English slips ("i an" -> am). Everything else is a hijack.
const ALLOWED: &[(&str, &str, &str)] = &[
    ("more", "then", "than"),
    ("and", "than", "then"),
    ("not", "effect", "affect"),
    ("to", "except", "accept"),
    ("i", "an", "am"),
];

fn flips(e: &NlpEngine, words: &[(String, u32)], prevs: &[&str]) -> Vec<String> {
    let mut out = Vec::new();
    for (w, f) in words {
        if *f < 236 || !w.chars().all(|c| c.is_ascii_lowercase()) || w.len() < 2 {
            continue;
        }
        for prev in prevs {
            let r = e.suggest_with_context(w, prev, 3);
            if let Some(c) = r.candidates.iter().find(|c| c.is_autocorrect && !c.word.eq_ignore_ascii_case(w)) {
                let allowed = ALLOWED.iter().any(|&(p, t, f)| p == *prev && t == w && f.eq_ignore_ascii_case(&c.word));
                if !allowed {
                    out.push(format!("[{prev}] {w} -> {}", c.word));
                }
            }
        }
    }
    out
}

#[test]
fn common_words_are_never_replaced_when_typed_correctly() {
    let (e, words) = engine();
    let bad = flips(&e, &words, &["", "i", "the", "and", "to", "in", "that", "with", "you", "it", "is", "for", "on", "was", "my", "so", "but", "not", "have", "be", "more", "of", "a", "we", "he", "she", "they", "this", "are", "at"]);
    assert!(bad.is_empty(), "{} hijacks:\n{}", bad.len(), bad.join("\n"));
}

#[test]
fn poisoned_personal_pairs_cannot_replace_common_words() {
    let (mut e, words) = engine();
    for (typo, intended) in [("in", "that"), ("that", "in"), ("with", "that"), ("the", "then"), ("you", "your"), ("and", "an")] {
        for _ in 0..6 {
            e.record_personal_correction(typo, intended);
        }
    }
    let bad = flips(&e, &words, &[""]);
    assert!(bad.is_empty(), "{} hijacks:\n{}", bad.len(), bad.join("\n"));
}

#[test]
fn a_pre_fix_learned_blob_loses_its_corrections_but_keeps_its_words() {
    // Build a v3-shaped blob by hand: the layout is identical, only the
    // version byte differs, so rewrite it on a fresh export.
    let (mut e, _) = engine();
    e.learn_word("roratus", 150);
    e.record_personal_correction("beither", "brother");
    let mut blob = e.export_learned();
    assert_eq!(blob[4], crake_core::persist::LEARNED_VERSION);
    blob[4] = 3;
    let (mut e2, _) = engine();
    e2.import_learned(&blob).expect("v3 blob still imports");
    assert!(e2.trie.get_frequency("roratus").is_some(), "learned words survive");
    assert!(e2.personal_correction_with_count("beither").is_none(), "old corrections are dropped");
    let (mut e3, _) = engine();
    let fresh = e.export_learned();
    e3.import_learned(&fresh).expect("v4 import");
    assert!(e3.personal_correction_with_count("beither").is_some(), "new corrections are kept");
}
