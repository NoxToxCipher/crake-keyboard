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

/// Bigram attestation is the legitimacy oracle: a pair the corpus has seen
/// is real language and must never be welded, even when the joined form is
/// a strong dictionary word ("can not" -> cannot, "are a" -> area). Fragments
/// from spurious spaces are exactly the pairs no corpus ever saw, so they
/// keep merging.
#[test]
fn attested_pairs_never_merge_even_when_the_joined_word_is_strong() {
    let mut e = engine();
    for (w, f) in [("cannot", 238), ("area", 246), ("can", 250), ("not", 250), ("are", 250), ("a", 250), ("should", 250)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    // Without attestation these WOULD merge (strong joined words):
    assert_eq!(e.merge_repair("can", "not").as_deref(), Some("cannot"));
    assert_eq!(e.merge_repair("are", "a").as_deref(), Some("area"));

    // Attest the pairs in a tiny bigram table -> merges must stop.
    let id = |eng: &NlpEngine, w: &str| eng.corpus_words().iter().position(|c| c == w).unwrap() as u32;
    let mut entries = vec![
        (id(&e, "can"), id(&e, "not"), 219u8),
        (id(&e, "are"), id(&e, "a"), 204u8),
    ];
    entries.sort();
    let mut blob = Vec::new();
    blob.extend_from_slice(b"CRKB");
    blob.push(2);
    blob.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    blob.extend_from_slice(&(e.corpus_words().len() as u32).to_le_bytes());
    for (a, b, s) in entries {
        blob.extend_from_slice(&a.to_le_bytes());
        blob.extend_from_slice(&b.to_le_bytes());
        blob.push(s);
    }
    e.load_bigrams(&blob).unwrap();

    assert_eq!(e.merge_repair("can", "not"), None, "attested pair must not merge");
    assert_eq!(e.merge_repair("are", "a"), None, "attested pair must not merge");
    // Unattested spurious fragments still repair.
    assert_eq!(e.merge_repair("shou", "kd").as_deref(), Some("should"));
}

/// The Gaussian touch model makes slip costs layout-true: the same fragments
/// merge on the layout where the slip is physically plausible and refuse on
/// one where it is not. "kd" for "ld" is a k/l neighbour slip on QWERTY;
/// on Dvorak k and l live on opposite corners.
#[test]
fn merge_repair_follows_the_active_layout_model() {
    use floris_core::TouchModel;
    fn grid(rows: &[&str], offsets: &[f32]) -> TouchModel {
        let mut keys = Vec::new();
        for (r, row) in rows.iter().enumerate() {
            for (i, ch) in row.chars().enumerate() {
                keys.push((ch, (i as f32 + offsets[r] + 0.5) * 100.0, (r as f32 + 0.5) * 140.0));
            }
        }
        TouchModel::from_layout(&keys).expect("valid grid")
    }
    let mut e = engine();
    e.trie.insert("should", 250);

    e.set_touch_model(Some(grid(&["qwertyuiop", "asdfghjkl", "zxcvbnm"], &[0.0, 0.5, 1.5])));
    assert_eq!(e.merge_repair("shou", "kd").as_deref(), Some("should"));
    assert_eq!(e.merge_repair("can", "for"), None);

    e.set_touch_model(Some(grid(&["pyfgcrl", "aoeuidhtns", "qjkxbmwvz"], &[3.0, 0.0, 1.5])));
    assert_eq!(e.merge_repair("shou", "kd"), None, "k/l are not neighbours on Dvorak");
}

/// Round 4: transposition COMBINED with another slip. A swap must cost one
/// edit (2 units), not two substitutions (4) — otherwise swap+slip chains
/// ("gildinf" for gliding) overflow the budget and become unfindable.
#[test]
fn recovers_transposition_plus_slip_combos() {
    let mut e = engine();
    for (w, f) in [("thanks", 244), ("testing", 227)] {
        e.trie.insert(w, f);
    }
    let mut failures = Vec::new();
    for (typed, expected) in [
        ("tesitnf", "testing"),  // it-swap + f/g slip
        ("gildinf", "gliding"),  // il-swap + f/g slip
        ("keybaodr", "keyboard"), // two swaps (ao, rd)
        ("thnaks", "thanks"),    // single swap, must now rank as one edit
    ] {
        let got = top3(&e, typed);
        if !got.iter().any(|w| w.eq_ignore_ascii_case(expected)) {
            failures.push(format!("'{typed}' should offer '{expected}', got {got:?}"));
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
