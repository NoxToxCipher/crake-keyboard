//! Learned state survives an engine restart: what the user taught one
//! process must come back in the next. This is personalization rung 1 —
//! without it, every learned word died with the process.

use floris_core::NlpEngine;

/// Personal bigrams: the user's own phrasing outranks web statistics in
/// every context decision, and survives restarts with the rest of the
/// learned state.
#[test]
fn personal_bigrams_layer_over_the_shipped_table_and_persist() {
    let mut e = NlpEngine::new();
    // Unknown to any web corpus, typed by this user twice:
    e.record_personal_bigram("glossy", "cockatoo");
    e.record_personal_bigram("glossy", "cockatoo");
    let score = e.bigram_pair_score("glossy", "cockatoo");
    assert!(score >= 140, "personal pair must score strongly, got {score}");
    assert_eq!(e.bigram_pair_score("glossy", "unrelated"), 0);

    // Personal pairs count as attestation: the user's own legitimate pair
    // must never be merge-welded, even when the joined word is strong.
    e.trie.insert("ta", 200);
    e.trie.insert("gs", 200);
    e.trie.insert("tags", 250);
    assert!(e.merge_repair("ta", "gs").is_some(), "premise: unattested pair merges");
    e.record_personal_bigram("ta", "gs");
    assert_eq!(e.merge_repair("ta", "gs"), None, "personally attested pair must not merge");

    // Round trip.
    let blob = e.export_learned();
    let mut fresh = NlpEngine::new();
    fresh.import_learned(&blob).unwrap();
    assert!(fresh.bigram_pair_score("glossy", "cockatoo") >= 140);
}

/// The personal frequency layer: learning NEVER demotes a word (accepting
/// "the" used to overwrite 254 -> 100), repeated use nudges a word upward,
/// and the personal boost is bounded to corpus base + 30 so the frequency
/// table can never flatten into uniform 255s over months of typing.
#[test]
fn learning_never_demotes_and_personal_boost_is_bounded() {
    let mut e = NlpEngine::new();
    e.corpus_insert("the", 254);
    e.trie.insert("the", 254);
    e.learn_word("the", 100); // the acceptance path's flat 100
    assert!(
        e.trie.get_frequency("the").unwrap() >= 254,
        "acceptance must never demote a common word"
    );

    // A personal out-of-vocabulary word rises with use, within its headroom.
    e.learn_word("roratus", 100);
    let start = e.trie.get_frequency("roratus").unwrap();
    for _ in 0..50 {
        e.learn_word("roratus", 100);
    }
    let end = e.trie.get_frequency("roratus").unwrap();
    assert!(end > start, "repeated use must build preference: {start} -> {end}");
    assert!(end <= 130, "personal boost is bounded: {end}");
}

#[test]
fn learned_words_and_habits_survive_a_restart() {
    let mut first = NlpEngine::new();
    first.learn_word("roratus", 150);
    first.learn_word("meshtastic", 120);
    first.record_personal_correction("thay", "that");
    first.record_personal_correction("thay", "that");
    let blob = first.export_learned();

    // "Restart": a brand-new engine that has never seen these words.
    let mut second = NlpEngine::new();
    assert!(second.suggest("roratus", 3).candidates.iter().all(|c| c.word != "roratus"));

    let restored = second.import_learned(&blob).unwrap();
    assert!(restored >= 2, "both learned words restore, got {restored}");
    assert!(
        second
            .suggest("roratus", 3)
            .candidates
            .iter()
            .any(|c| c.word == "roratus"),
        "restored word must suggest again"
    );
    assert_eq!(
        second.get_personal_correction("thay").as_deref(),
        Some("that"),
        "correction habit must survive"
    );

    // A second import is idempotent, and garbage restores nothing.
    assert!(second.import_learned(&blob).is_ok());
    assert!(second.import_learned(b"garbage").is_err());
}

/// The learned stores never outgrow their persistence caps: at capacity a
/// new entry evicts the least-used one, so serialize() never truncates —
/// without this, export sorted alphabetically and every restart silently
/// forgot the user's w-z words first.
#[test]
fn learned_words_evict_weakest_at_capacity_not_the_alphabet_tail() {
    let mut e = NlpEngine::new();
    for i in 0..floris_core::persist::MAX_LEARNED_WORDS {
        // learn each filler once (freq path gives them all the same base)
        e.learn_word(&format!("filler{i:04}"), 100);
    }
    // one word the user actually uses a lot
    for _ in 0..10 {
        e.learn_word("zzzfavourite", 100);
    }
    // capacity holds, and the well-used z-word survives an export
    let blob = e.export_learned();
    let mut fresh = NlpEngine::new();
    let restored = fresh.import_learned(&blob).expect("import");
    assert!(restored <= floris_core::persist::MAX_LEARNED_WORDS as usize);
    assert!(
        fresh.trie.get_frequency("zzzfavourite").is_some(),
        "the used word must survive persistence even at capacity"
    );
}
