use floris_core::{get_key_hand, is_bimanual_transposition, Hand, NlpEngine};

#[test]
fn test_hand_assignment_completeness() {
    for ch in 'a'..='z' {
        let hand = get_key_hand(ch);
        assert_ne!(hand, Hand::Unknown, "Character '{ch}' must have an assigned hand");
    }
}

#[test]
fn test_bimanual_transposition_rescues_opposite_hand_flips() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("the", 255);
    nlp.trie.insert("of", 250);
    nlp.trie.insert("with", 240);
    nlp.trie.insert("can", 230);

    // 1. "teh" -> "the" (t=Left, e=Left, h=Right). The transposed pair is 'e' (L) and 'h' (R).
    // Timestamps: t=0ms, e=120ms, h=148ms -> delta between 'e' and 'h' is 28ms (<= 55ms)
    let timestamps_teh = vec![0, 120, 148];
    assert!(is_bimanual_transposition("teh", "the", &timestamps_teh));

    let res = nlp.suggest_with_timing("teh", &timestamps_teh, 3);
    assert!(!res.candidates.is_empty());
    assert_eq!(res.candidates[0].word, "the", "Bimanual timing must promote 'the' to top slot");
    assert!(res.candidates[0].is_autocorrect);

    // 2. "fo" -> "of" (f=Left, o=Right). Timestamps: f=0ms, o=32ms -> delta 32ms
    let timestamps_fo = vec![0, 32];
    assert!(is_bimanual_transposition("fo", "of", &timestamps_fo));
    let res_fo = nlp.suggest_with_timing("fo", &timestamps_fo, 3);
    assert_eq!(res_fo.candidates[0].word, "of", "Bimanual timing must promote 'of' to top slot");

    // 3. "wiht" -> "with" (w=L, i=R, h=R, t=L). Transposed pair is 'h' (R) and 't' (L).
    // Timestamps: w=0ms, i=110ms, h=220ms, t=255ms -> delta between 'h' and 't' is 35ms
    let timestamps_wiht = vec![0, 110, 220, 255];
    assert!(is_bimanual_transposition("wiht", "with", &timestamps_wiht));
    let res_wiht = nlp.suggest_with_timing("wiht", &timestamps_wiht, 3);
    assert_eq!(res_wiht.candidates[0].word, "with", "Bimanual timing must promote 'with' to top slot");
}

#[test]
fn test_slow_or_same_hand_transpositions_never_override() {
    // 1. Slow typing (delta = 180ms > 55ms threshold): deliberate spelling or slow entry
    let timestamps_slow = vec![0, 120, 300]; // delta between e and h is 180ms
    assert!(!is_bimanual_transposition("teh", "the", &timestamps_slow));

    // 2. Same-hand transposition (e.g. 'f' and 'r' are both Left Hand)
    // "fr" vs "rf": both keys are left hand
    let timestamps_same_hand = vec![0, 30];
    assert!(!is_bimanual_transposition("fr", "rf", &timestamps_same_hand));
}

/// Absent timing data is absent evidence: with no timestamps the bimanual
/// detector must refuse, otherwise every cross-hand transposition would
/// qualify unconditionally and valid words like "form" would flip to
/// "from" the moment this feature is wired (coord review 2026-08-28).
#[test]
fn no_timestamps_means_no_transposition_claim() {
    use floris_core::nlp::is_bimanual_transposition;
    assert!(!is_bimanual_transposition("form", "from", &[]));
    assert!(!is_bimanual_transposition("teh", "the", &[]));
    // mismatched length is equally not evidence
    assert!(!is_bimanual_transposition("teh", "the", &[0, 100]));
}
