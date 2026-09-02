use crake_core::NlpEngine;

#[test]
fn test_accidental_typo_decays_without_reinforcement() {
    let mut nlp = NlpEngine::new();

    // Accidental typo typed once at epoch 100
    nlp.learn_word_with_decay("wrok", 120, 100);

    // At epoch 120 (20 epochs elapsed, half-life = 50) -> should still be present
    nlp.decay_learned_entries(120, 50);
    // Export state to verify presence
    let exported = nlp.export_learned();
    assert!(!exported.is_empty());

    // At epoch 160 (60 epochs elapsed >= 50) -> must be decayed and evicted
    nlp.decay_learned_entries(160, 50);
    let exported_after = nlp.export_learned();
    let mut nlp_clean = NlpEngine::new();
    let restored = nlp_clean.import_learned(&exported_after).unwrap_or(0);
    assert_eq!(restored, 0, "Transient typo must be pruned after half-life expiration");
}

#[test]
fn test_reinforced_frequent_words_persist_across_epochs() {
    let mut nlp = NlpEngine::new();

    // Strongly reinforced word (e.g. user name or technical term with high frequency)
    nlp.learn_word_with_decay("cryptography", 200, 100);

    // At epoch 400 (300 epochs elapsed) -> should persist because freq >= 150
    nlp.decay_learned_entries(400, 50);
    let exported = nlp.export_learned();
    let mut nlp_clean = NlpEngine::new();
    let restored = nlp_clean.import_learned(&exported).unwrap_or(0);
    assert_eq!(restored, 1, "Strongly learned word must survive decay epochs");
}

#[test]
fn test_repeatedly_rejected_autocorrects_are_suppressed() {
    let mut nlp = NlpEngine::new();

    // Typo "idk" -> unwanted suggestion "ink"
    assert!(!nlp.is_rejected_correction("idk", "ink"), "Initially not rejected");

    // Rejected once
    nlp.record_rejected_correction("idk", "ink");
    assert!(!nlp.is_rejected_correction("idk", "ink"), "Single rejection does not suppress yet");

    // Rejected twice -> becomes suppressed
    nlp.record_rejected_correction("idk", "ink");
    assert!(nlp.is_rejected_correction("idk", "ink"), "Two rejections must activate anti-sticky suppression");

    // Other pairs remain unsuppressed
    assert!(!nlp.is_rejected_correction("idk", "idea"));
}
