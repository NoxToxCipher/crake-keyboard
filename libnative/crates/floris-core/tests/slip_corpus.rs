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
        ("this", 250),
        ("what", 250),
        ("hello", 240),
        ("keyboard", 200),
        ("privacy", 150),
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

/// Round 3: spurious mid-word spaces, verbatim from live typing. The merge
/// repair must recover the intended word from the two fragments — and must
/// refuse to merge legitimate word pairs.
#[test]
fn repairs_spurious_space_splits() {
    let mut e = engine();
    for (w, f) in [("should", 250), ("start", 240), ("double", 200), ("word", 240), ("often", 220), ("use", 250), ("things", 230), ("improve", 200), ("todo", 60)] {
        e.trie.insert(w, f);
    }
    // The shipped dictionary is polluted with corpus-noise fragments at high
    // frequency ("ni" 201, "lt" 209, "kd" 180 — measured). Mirror that here:
    // repair must fire even though the fragments look like "words".
    for (w, f) in [("ni", 201), ("lt", 209), ("kd", 180), ("shou", 142), ("st", 241), ("att", 168), ("rn", 190), ("eo", 160), ("oft", 167), ("doi", 168), ("ble", 129)] {
        e.trie.insert(w, f);
    }
    let mut failures = Vec::new();
    for (prev, cur, expected) in [
        ("shou", "kd", Some("should")),
        ("st", "att", Some("start")),
        ("ni", "stakes", Some("mistakes")),
        ("deliberate", "lt", Some("deliberately")),
        ("doi", "ble", Some("double")),
        ("eo", "rd", Some("word")),
        ("oft", "rn", Some("often")),
        // Legitimate pairs must never merge.
        ("to", "do", None),
        ("in", "the", None),
        ("can", "for", None),
    ] {
        let got = e.merge_repair(prev, cur);
        let ok = match expected {
            Some(w) => got.as_deref() == Some(w),
            None => got.is_none(),
        };
        if !ok {
            failures.push(format!("('{prev}' + '{cur}') expected {expected:?}, got {got:?}"));
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

/// Round 2: the fat-finger classes beyond adjacent substitution — swapped
/// neighbouring letters (transposition), a key registering twice, and a key
/// not registering at all. Same bar: the intended word must appear top-3.
#[test]
fn recovers_transpositions_doubles_and_drops() {
    let e = engine();
    let mut failures = Vec::new();
    for (typed, expected) in [
        // Transpositions
        ("yuo", "you"),
        ("thsi", "this"),
        ("waht", "what"),
        ("keybaord", "keyboard"),
        // Doubled letters
        ("helllo", "hello"),
        ("cann", "can"),
        ("mistakess", "mistakes"),
        // Dropped letters
        ("keybord", "keyboard"),
        ("privcy", "privacy"),
        ("glidng", "gliding"),
        ("curently", "currently"),
    ] {
        let got = top3(&e, typed);
        if !got.iter().any(|w| w.eq_ignore_ascii_case(expected)) {
            failures.push(format!("'{typed}' should offer '{expected}', got {got:?}"));
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}
