//! Regression corpus of real-world fat-finger failures, captured verbatim
//! from a message typed on Crake Keyboard on 2026-08-26. Every substitution
//! in this corpus is a physically-adjacent key slip (x↔c, r↔t, k↔l, f↔g,
//! u↔i, i↔o, t↔y, n↔m) — the signature of fast thumb typing, and the case
//! the autocorrect budget must be spent on.

use floris_core::NlpEngine;

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    // The words the corpus expects, with plausible frequencies, on top of
    // CORE_DICTIONARY (which may or may not contain them).
    for (w, f) in [
        ("currently", 220),
        ("gliding", 140),
        ("can", 250),
        ("for", 255),
        ("you", 255),
        ("my", 255),
        ("mistakes", 180),
        ("deliberately", 160),
    ] {
        e.trie.insert(w, f);
    }
    e
}

fn top3(e: &NlpEngine, typed: &str) -> Vec<String> {
    e.suggest(typed, 3).candidates.into_iter().map(|c| c.word).collect()
}

/// Adjacent-slip substitutions the engine must recover, from the live corpus.
#[test]
fn recovers_adjacent_key_slips() {
    let e = engine();
    let mut failures = Vec::new();
    for (typed, expected) in [
        ("xan", "can"),         // 1 slip, wrong FIRST letter
        ("fir", "for"),         // 1 slip (real-word collision: fir is a tree)
        ("tou", "you"),         // 1 slip, wrong first letter
        ("xurrenrky", "currently"), // 3 slips
        ("gkudinf", "gliding"),     // 3 slips
        ("nt", "my"),               // 2 slips on a 2-letter word
        ("yui", "you"),             // 2 slips
    ] {
        let got = top3(&e, typed);
        if !got.iter().any(|w| w.eq_ignore_ascii_case(expected)) {
            failures.push(format!("'{typed}' should offer '{expected}', got {got:?}"));
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}
