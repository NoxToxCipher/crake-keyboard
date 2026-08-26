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
    let and_pos = tail.iter().position(|&w| w == "and").expect("'and' offered");
    assert!(
        any_pos < and_pos,
        "'hardly any' (220) must outrank unseen 'hardly and', got {tail:?}"
    );
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
