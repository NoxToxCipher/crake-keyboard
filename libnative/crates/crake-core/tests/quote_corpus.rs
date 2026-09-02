//! Regression corpus for single-quoted words, from a live field report
//! (2026-08-27): typing 'word' used to delete the opening quote (autocorrect
//! "repaired" 'word -> word) and turn the closing quote into a possessive
//! (word' ranked word's first). Edge apostrophes are deliberate punctuation:
//! quotes live behind long-press and are not fat-fingered.

use crake_core::NlpEngine;

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    for (w, f) in [("word", 240), ("words", 200), ("word's", 120), ("quote", 180)] {
        e.trie.insert(w, f);
    }
    e
}

#[test]
fn edge_apostrophe_tokens_never_autocorrect() {
    let e = engine();
    for typed in ["'word", "word'", "'word'", "'quote", "quote'", "'quote'"] {
        let r = e.suggest(typed, 4);
        for c in &r.candidates {
            assert!(
                !c.is_autocorrect,
                "'{typed}' produced auto-commit candidate '{}' — quotes must survive",
                c.word
            );
        }
    }
}

#[test]
fn the_literal_quoted_token_leads_the_list() {
    let e = engine();
    for typed in ["'word'", "word'", "'word"] {
        let r = e.suggest(typed, 4);
        assert_eq!(
            r.candidates.first().map(|c| c.word.as_str()),
            Some(typed),
            "the literal '{typed}' must lead, got {:?}",
            r.candidates.iter().map(|c| c.word.as_str()).collect::<Vec<_>>()
        );
    }
}

#[test]
fn unquoted_words_still_autocorrect_and_interior_apostrophes_are_untouched() {
    let e = engine();
    // Control: plain slip still auto-corrects (regression guard for the fix).
    let slip = e.suggest("eord", 3);
    assert!(
        slip.candidates.first().is_some_and(|c| c.word == "word" && c.is_autocorrect),
        "plain slips must keep auto-correcting, got {:?}",
        slip.candidates.iter().map(|c| c.word.as_str()).collect::<Vec<_>>()
    );
    // Interior apostrophes (contraction typing) are not edge quotes.
    let dont = e.suggest("don't", 3);
    assert!(dont.is_exact_match || dont.candidates.iter().any(|c| c.word == "don't"));
}
