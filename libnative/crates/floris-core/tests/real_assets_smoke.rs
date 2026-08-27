//! Sentinel battery against the SHIPPED assets (data.crkd + bigrams.crkb,
//! in-repo). Every behavior here was a field fix; unit harnesses prove the
//! mechanisms, this proves them against the data actually on phones — so
//! an asset regen, a dictionary edit, or an ingestion can't silently undo
//! a shipped fix. Field specimens are all Lochran's own typing (2026-08).

use floris_core::NlpEngine;

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    floris_core::parse_dict_blob(&dict, |w, f| {
        e.trie.insert(w, f);
        e.corpus_insert(w, f);
    })
    .expect("dict parse");
    let big = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/bigrams.crkb"
    ))
    .expect("bigram blob");
    e.load_bigrams(&big).expect("bigram parse");
    e
}

#[test]
fn shipped_fixes_hold_on_shipped_assets() {
    let e = engine();
    // (prev, typed, expected auto-commit word or "" for must-not-flip)
    let cases: &[(&str, &str, &str)] = &[
        ("", "ti", "to"),
        ("i", "an", "am"),
        ("", "abunch", "a bunch"),
        ("", "thoriufhky", "thoroughly"),
        ("", "xurrenrky", "currently"),
        ("", "aer", "are"),
        ("always", "hse", "use"),
        ("", "tou", "you"),
        ("", "tomorow", "tomorrow"),
        ("", "helllo", "hello"),
        ("", "inmy", "in my"),
        // must never flip: AU vocab, his project names, abbreviations
        ("", "arvo", ""),
        ("", "doona", ""),
        ("", "smoko", ""),
        ("", "uni", ""),
        ("", "nbn", ""),
        ("", "tor", ""),
        ("", "gst", ""),
        ("", "Crake", ""),
        ("", "Fieldmark", ""),
        ("", "Antigravity", ""),
        ("are", "your", ""),
        ("you", "to", ""),
        ("in", "their", ""),
        ("want", "an", ""),
        ("must", "he", ""),
        ("a", "cot", ""),
        ("the", "hen", ""),
        // form/from are cross-hand adjacent transpositions of each other
        // and BOTH valid: timing-based transposition features must never
        // flip a typed valid word (review of loop 7/21, 2026-08-28).
        ("", "form", ""),
        ("the", "form", ""),
        ("", "from", ""),
    ];
    let mut failures = Vec::new();
    for &(prev, typed, expected) in cases {
        let r = e.suggest_with_context(typed, prev, 5);
        let flip = r
            .candidates
            .iter()
            .find(|c| c.is_autocorrect && !c.word.eq_ignore_ascii_case(typed));
        match (expected, flip) {
            ("", None) => {}
            ("", Some(c)) => failures.push(format!("[{prev}] {typed} must not flip, got {}", c.word)),
            (want, Some(c)) if c.word == want => {}
            (want, got) => failures.push(format!(
                "[{prev}] {typed} -> expected {want}, got {:?}",
                got.map(|c| c.word.clone())
            )),
        }
    }
    // merge repairs on real data
    if e.merge_repair("oft", "rn").as_deref() != Some("often") {
        failures.push("oft+rn -> often".into());
    }
    if e.merge_repair("ni", "stakes").as_deref() != Some("mistakes") {
        failures.push("ni+stakes -> mistakes".into());
    }
    // the homophone survivors still fire
    let r = e.suggest_with_context("then", "more", 5);
    if !r.candidates.first().is_some_and(|c| c.word == "than" && c.is_autocorrect) {
        failures.push("more then -> than".into());
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

/// Latency floor-guard on the real assets: the suggest pipeline has grown
/// many gated stages (rescues, punch-throughs, homophone arbitration) and
/// nothing was watching the clock. The bound is deliberately generous —
/// debug builds, CI jitter — it exists to catch a CATASTROPHIC regression
/// (an ungated trie scan), not to tune against. Baseline printed for eyes.
#[test]
fn suggest_latency_stays_sane() {
    let e = engine();
    // Mix of shapes: exact words with context (rescue path), typos with
    // completions (punch-through), merges, short tokens, long chains.
    let cases: &[(&str, &str)] = &[
        ("i", "an"),
        ("", "pleade"),
        ("", "thoriufhky"),
        ("the", "keyboard"),
        ("", "ti"),
        ("more", "then"),
        ("", "worls"),
        ("in", "their"),
        ("", "glidinf"),
        ("want", "to"),
    ];
    // warm-up
    for &(p, q) in cases {
        let _ = e.suggest_with_context(q, p, 5);
    }
    let start = std::time::Instant::now();
    let rounds = 200;
    for _ in 0..rounds {
        for &(p, q) in cases {
            let _ = e.suggest_with_context(q, p, 5);
        }
    }
    let per_call = start.elapsed() / (rounds * cases.len() as u32);
    eprintln!("suggest latency: {per_call:?} per call (debug build, real assets)");
    assert!(
        per_call < std::time::Duration::from_millis(20),
        "suggest took {per_call:?} per call — a stage lost its gate"
    );
}
