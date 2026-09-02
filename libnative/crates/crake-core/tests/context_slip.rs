//! Behavioural contract of the valid-word slip rescue: a dictionary word is
//! only ever auto-flipped when the context evidence is overwhelming,
//! unambiguous AND meaningful — both words top-tier common (coverage class
//! where a missing bigram is real evidence), typed pair unattested, exactly
//! one adjacent-key neighbour strongly attested. Field specimen 2026-08-27:
//! "I an not" -> am. Rare-word slips ("every tine", "here fir") stay
//! suggestion-only: no unigram/bigram signal separates them from legitimate
//! rare words in open contexts ("a cot", "the hen").

use crake_core::NlpEngine;

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
    for (w, f) in [
        ("i", 250),
        ("am", 240),
        ("an", 245),
        ("and", 250),
        ("ah", 160),
        ("not", 250),
        ("every", 230),
        ("time", 250),
        ("tine", 60),
        ("tune", 200),
        ("here", 240),
        ("for", 255),
        ("fir", 60),
        ("want", 230),
        ("apple", 180),
        ("a", 254),
        ("the", 255),
        ("cot", 60),
        ("cat", 230),
        ("hen", 60),
        ("ten", 237),
    ] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    e
}

fn top(e: &NlpEngine, prev: &str, q: &str) -> Vec<(String, bool)> {
    e.suggest_with_context(q, prev, 5)
        .candidates
        .iter()
        .map(|c| (c.word.clone(), c.is_autocorrect))
        .collect()
}

/// Real-table shape: "i an" unattested, "i am" overwhelming -> rescue fires,
/// literal stays reachable.
#[test]
fn unattested_valid_word_flips_to_the_one_attested_neighbour() {
    let mut e = engine();
    let b = blob(&e, &[("i", "am", 184), ("i", "and", 153)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "i", "an");
    assert_eq!(
        got.first(),
        Some(&("am".to_string(), true)),
        "'i an' must rescue to am: {got:?}"
    );
    assert!(
        got.iter().any(|(w, ac)| w == "an" && !ac),
        "literal 'an' must stay tappable: {got:?}"
    );
}

/// Rare typed words NEVER auto-flip — "every tine" and "here fir" are real
/// slips, but the same signal shape describes "a cot" and "the hen", which
/// are legitimate. Documented limit: the context-apt neighbour must still
/// sit directly after the literal so one tap fixes it.
#[test]
fn rare_word_slips_stay_suggestion_only_at_slot_two() {
    let mut e = engine();
    let b = blob(&e, &[("every", "time", 182), ("here", "for", 205)]);
    e.load_bigrams(&b).unwrap();
    for (prev, q, want) in [("every", "tine", "time"), ("here", "fir", "for")] {
        let got = top(&e, prev, q);
        assert!(!got.iter().any(|(_, ac)| *ac), "{prev} {q} must not flip: {got:?}");
        assert_eq!(got.first().map(|(w, _)| w.as_str()), Some(q), "literal leads");
        assert_eq!(
            got.get(1).map(|(w, _)| w.as_str()),
            Some(want),
            "{want} must sit at slot 2 for a one-tap fix: {got:?}"
        );
    }
}

/// The false positives the frequency-class gate exists for: "a cot" and
/// "the hen" are absent from the bigram table out of SPARSITY, and must
/// never be flipped to "a cat" / "the ten" (sweep 2026-08-27).
#[test]
fn legitimate_rare_words_in_open_contexts_never_flip() {
    let mut e = engine();
    let b = blob(&e, &[("a", "cat", 200), ("the", "ten", 190)]);
    e.load_bigrams(&b).unwrap();
    for (prev, q) in [("a", "cot"), ("the", "hen")] {
        let got = top(&e, prev, q);
        assert!(
            !got.iter().any(|(_, ac)| *ac),
            "{prev} {q} is legitimate English and must never flip: {got:?}"
        );
        assert_eq!(got.first().map(|(w, _)| w.as_str()), Some(q));
    }
}

/// Question inversion makes any "verb + subject pronoun" legitimate, so a
/// typed subject pronoun is never flipped: "must he go?" stays "must he"
/// even though "must be" is overwhelmingly attested (sweep 2026-08-27).
#[test]
fn subject_pronouns_are_never_flipped() {
    let mut e = engine();
    for (w, f) in [("must", 230), ("be", 250), ("him", 240)] {
        e.corpus_insert(w, f);
        e.trie.insert(w, f);
    }
    let b = blob(&e, &[("must", "be", 220)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "must", "he");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "'must he' is question inversion, never a slip to flip: {got:?}"
    );
    assert_eq!(got.first().map(|(w, _)| w.as_str()), Some("he"));
}

/// "want an" is attested (157 in the real table): an attested typed pair
/// must NEVER be flipped, no matter how strong a neighbour looks.
#[test]
fn attested_typed_pair_is_never_flipped() {
    let mut e = engine();
    let b = blob(&e, &[("want", "an", 157), ("want", "am", 200)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "want", "an");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "nothing may autocorrect after an attested pair: {got:?}"
    );
    assert_eq!(got.first().map(|(w, _)| w.as_str()), Some("an"));
}

/// Two strongly attested neighbours = ambiguity, and ambiguity never
/// auto-commits ("tine" with both time AND tune attested).
#[test]
fn ambiguous_evidence_never_flips() {
    let mut e = engine();
    let b = blob(&e, &[("every", "time", 182), ("every", "tune", 170)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "every", "tine");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "two attested neighbours must stay a suggestion, not a commit: {got:?}"
    );
}

/// Attestation below the overwhelming threshold (160) is not evidence
/// enough to override what the user typed.
#[test]
fn weak_attestation_never_flips() {
    let mut e = engine();
    let b = blob(&e, &[("i", "am", 150)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "i", "an");
    assert!(!got.iter().any(|(_, ac)| *ac), "150 < 160 must not flip: {got:?}");
}

/// A rare word never replaces a typed word ("for" -> "fir" is impossible
/// even under fabricated attestation), and capitalized input (names) is
/// never touched.
#[test]
fn rare_neighbour_and_capitalized_input_are_safe() {
    let mut e = engine();
    // Fabricate overwhelming attestation for the RARE neighbour: the
    // candidate unigram floor (150) must still refuse to flip "for" (255)
    // to "fir" (60).
    let b = blob(&e, &[("want", "fir", 250), ("i", "am", 184)]);
    e.load_bigrams(&b).unwrap();
    let got = top(&e, "want", "for");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "a rare word must never replace a common one: {got:?}"
    );
    // capitalized: "An" after "i" stays literal-first even with the blob.
    let got = top(&e, "i", "An");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "capitalized input must never be flipped: {got:?}"
    );
}

/// No bigram table, or no context: behaviour is bit-identical to before —
/// the literal valid word leads and nothing auto-commits.
#[test]
fn no_table_or_no_context_is_unchanged() {
    let e = engine();
    let got = top(&e, "i", "an");
    assert_eq!(got.first(), Some(&("an".to_string(), false)));
    assert!(!got.iter().any(|(_, ac)| *ac));

    let mut e2 = engine();
    let b = blob(&e2, &[("i", "am", 184)]);
    e2.load_bigrams(&b).unwrap();
    let got2 = top(&e2, "", "an");
    assert_eq!(got2.first(), Some(&("an".to_string(), false)));
    assert!(!got2.iter().any(|(_, ac)| *ac));
}

/// Self-healing: once the user's own accepted pair attests "i an" through a
/// personal bigram, the rescue stands down.
#[test]
fn personal_bigram_teaches_the_rescue_to_stop() {
    let mut e = engine();
    let b = blob(&e, &[("i", "am", 184)]);
    e.load_bigrams(&b).unwrap();
    assert_eq!(
        top(&e, "i", "an").first(),
        Some(&("am".to_string(), true)),
        "premise: rescue fires before learning"
    );
    e.record_personal_bigram("i", "an");
    let got = top(&e, "i", "an");
    assert!(
        !got.iter().any(|(_, ac)| *ac),
        "after the user teaches 'i an', it must stay theirs: {got:?}"
    );
}
