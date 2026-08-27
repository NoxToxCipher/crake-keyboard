//! Behavioural contract of the bigram context re-ranker:
//! ordering only, immovable auto-commit/literal head, identical behaviour
//! when no table is loaded.

use floris_core::NlpEngine;

/// Builds a CRKB blob for (prev, next, score) triples using the engine's own
/// corpus ids, exactly as the generator does against the CRKD order.
fn blob(engine: &NlpEngine, pairs: &[(&str, &str, u8)]) -> Vec<u8> {
    let id = |w: &str| {
        engine
            .corpus_words()
            .iter()
            .position(|c| c == w)
            .expect("word in corpus") as u32
    };
    let mut entries: Vec<(u32, u32, u8)> = pairs
        .iter()
        .map(|&(a, b, s)| (id(a), id(b), s))
        .collect();
    entries.sort();
    let mut out = Vec::new();
    out.extend_from_slice(b"CRKB");
    out.push(2);
    out.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    out.extend_from_slice(&(engine.corpus_words().len() as u32).to_le_bytes());
    for (a, b, s) in entries {
        out.extend_from_slice(&a.to_le_bytes());
        out.extend_from_slice(&b.to_le_bytes());
        out.push(s);
    }
    out
}

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    // corpus_insert assigns ids AND fills the trie via a second insert call,
    // mirroring the JNI blob-load path.
    for (w, f) in [
        ("i", 250),
        ("am", 240),
        ("an", 240),
        ("as", 240),
        ("at", 240),
        ("morning", 200),
        ("mornings", 150),
        ("morbid", 100),
    ] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    e
}

#[test]
fn context_reorders_the_tail_by_pair_score() {
    let mut e = engine();
    for (w, f) in [("any", 245), ("and", 250)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    // "and" is the more frequent word; the context table says the pair
    // "hardly any" is strong and "hardly and" unseen, so context must beat
    // raw frequency in the movable tail. (v1 contract: the LM reorders only
    // candidates the upstream stages actually offered.)
    e.corpus_insert("hardly", 180);
    e.trie.insert("hardly", 180);
    let blob = blob(&e, &[("hardly", "any", 220)]);
    assert_eq!(e.load_bigrams(&blob).unwrap(), 1);

    let no_ctx = e.suggest("an", 6);
    let with_ctx = e.suggest_with_context("an", "hardly", 6);
    assert_eq!(
        no_ctx.candidates.first().map(|c| c.word.as_str()),
        with_ctx.candidates.first().map(|c| c.word.as_str()),
        "head candidate must not move"
    );
    let tail: Vec<&str> = with_ctx.candidates[1..].iter().map(|c| c.word.as_str()).collect();
    let any_pos = tail.iter().position(|&w| w == "any").expect("'any' offered");
    // "and" either ranks below "any" or fell out of view entirely — both
    // satisfy "context-apt outranks context-less".
    if let Some(and_pos) = tail.iter().position(|&w| w == "and") {
        assert!(
            any_pos < and_pos,
            "'hardly any' (220) must outrank unseen 'hardly and', got {tail:?}"
        );
    }
    // And without context the frequency order stands.
    let plain_tail: Vec<&str> = no_ctx.candidates[1..].iter().map(|c| c.word.as_str()).collect();
    let p_any = plain_tail.iter().position(|&w| w == "any").expect("'any' offered");
    let p_and = plain_tail.iter().position(|&w| w == "and").expect("'and' offered");
    assert!(p_and < p_any, "without context 'and' (freq 250) leads, got {plain_tail:?}");
}

/// The pool-starvation fix: a context-apt word that misses the display cut
/// on raw frequency must be rescued by the rescorer. "am" is below five
/// stronger prefix/fuzzy candidates for the typo "an" at width 4 — with
/// "i" as context it must appear in the final list anyway.
#[test]
fn context_rescues_a_candidate_from_below_the_display_cut() {
    let mut e = engine();
    let blob = blob(&e, &[("i", "am", 230)]);
    e.load_bigrams(&blob).unwrap();

    let no_ctx = e.suggest("an", 4);
    assert!(
        !no_ctx.candidates.iter().any(|c| c.word == "am"),
        "precondition: without context 'am' misses the cut, got {:?}",
        no_ctx.candidates.iter().map(|c| c.word.as_str()).collect::<Vec<_>>()
    );
    let with_ctx = e.suggest_with_context("an", "i", 4);
    assert!(
        with_ctx.candidates.iter().any(|c| c.word == "am"),
        "'i am' (230) must be rescued into view, got {:?}",
        with_ctx.candidates.iter().map(|c| c.word.as_str()).collect::<Vec<_>>()
    );
}

#[test]
fn no_table_means_no_change() {
    let e = engine();
    let plain = e.suggest("mornin", 3);
    let ctx = e.suggest_with_context("mornin", "good", 3);
    let words = |r: &floris_core::SuggestionResult| {
        r.candidates.iter().map(|c| c.word.clone()).collect::<Vec<_>>()
    };
    assert_eq!(words(&plain), words(&ctx));
}

#[test]
fn corrupt_table_is_rejected_and_previous_kept() {
    let mut e = engine();
    let good = blob(&e, &[("i", "am", 200)]);
    e.load_bigrams(&good).unwrap();
    assert_eq!(e.bigram_count(), 1);
    assert!(e.load_bigrams(b"garbage").is_err());
    assert_eq!(e.bigram_count(), 1, "failed load must keep the old table");
}

/// Word-start letter prediction (adaptive hitboxes) draws on the REAL
/// language model, not just the ~36-word static list: any prev the table
/// knows yields its top successors' first letters, and the user's own
/// recorded pairs outrank web statistics.
#[test]
fn letter_prediction_uses_the_real_model_and_personal_pairs() {
    let mut e = engine();
    for (w, f) in [("keyboard", 230), ("shortcut", 180), ("layout", 190), ("crake", 30)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    let b = blob(&e, &[("keyboard", "shortcut", 190), ("keyboard", "layout", 170)]);
    e.load_bigrams(&b).unwrap();
    let preds = e.predict_next_letter_words("", "keyboard");
    let letters: Vec<char> = preds.iter().map(|(c, _)| *c).collect();
    assert!(letters.contains(&'s') && letters.contains(&'l'), "LM successors expected: {preds:?}");
    // personal pair outranks: after the user types "keyboard crake" twice,
    // 'c' must appear and map to crake
    e.record_personal_bigram("keyboard", "crake");
    e.record_personal_bigram("keyboard", "crake");
    let preds = e.predict_next_letter_words("", "keyboard");
    assert!(
        preds.iter().any(|(c, w)| *c == 'c' && w == "crake"),
        "personal pair must surface: {preds:?}"
    );
}

/// Homophone rules are hints, not verdicts: the LM arbitrates. A flip of a
/// valid word fires only when the correct form is clearly attested (>=150)
/// and clearly ahead (+30). Real-table shape: "are your" (174 vs 0) stays;
/// "more then" -> than (163 vs 215) fires.
#[test]
fn homophone_flips_require_language_model_agreement() {
    let mut e = engine();
    for (w, f) in [("your", 250), ("more", 245), ("then", 240), ("than", 240), ("are", 250)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    let b = blob(&e, &[
        ("are", "your", 174),
        ("more", "than", 215),
        ("more", "then", 163),
    ]);
    e.load_bigrams(&b).unwrap();
    // "are your": the rule matches but the LM says the typed word is right
    let r = e.suggest_with_context("your", "are", 5);
    assert!(
        !r.candidates.iter().any(|c| c.word == "you're" && c.is_autocorrect),
        "'are your' must never flip: {:?}",
        r.candidates
    );
    // "more then": rule matches AND the LM agrees by a wide margin
    let r = e.suggest_with_context("then", "more", 5);
    assert!(
        r.candidates.first().is_some_and(|c| c.word == "than" && c.is_autocorrect),
        "'more then' must fix to than: {:?}",
        r.candidates
    );
    // No bigram table at all: no homophone flip can fire
    let e2 = engine();
    let r = e2.suggest_with_context("then", "more", 5);
    assert!(
        !r.candidates.iter().any(|c| c.word == "than" && c.is_autocorrect),
        "no LM, no flip: {:?}",
        r.candidates
    );
}

/// Next-word prediction: personal pairs outrank the shipped LM, junk-band
/// successors never clutter the row, bare contractions display properly.
#[test]
fn next_word_prediction_blends_personal_and_shipped() {
    let mut e = engine();
    for (w, f) in [("keyboard", 230), ("shortcut", 180), ("layout", 190), ("junkx", 60), ("dont", 200)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    let b = blob(&e, &[
        ("keyboard", "shortcut", 190),
        ("keyboard", "layout", 170),
        ("keyboard", "junkx", 250),
        ("keyboard", "dont", 160),
    ]);
    e.load_bigrams(&b).unwrap();
    let preds = e.predict_next_words("keyboard", 3);
    assert!(!preds.iter().any(|w| w == "junkx"), "junk never suggested: {preds:?}");
    assert_eq!(preds.first().map(String::as_str), Some("shortcut"), "{preds:?}");
    assert!(preds.iter().any(|w| w == "don't"), "contraction displays: {preds:?}");
    // personal pair takes the lead after two uses (140+30=170 < 190? no —
    // equals layout; three uses = 185, still < 190; five = 215 leads)
    for _ in 0..5 {
        e.record_personal_bigram("keyboard", "layout");
    }
    let preds = e.predict_next_words("keyboard", 3);
    assert_eq!(preds.first().map(String::as_str), Some("layout"), "personal leads: {preds:?}");
    // unknown prev: empty, never panics
    assert!(e.predict_next_words("zzzz", 3).is_empty());
}
