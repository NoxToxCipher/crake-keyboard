//! Learned state survives an engine restart: what the user taught one
//! process must come back in the next. This is personalization rung 1 —
//! without it, every learned word died with the process.

use floris_core::NlpEngine;

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
