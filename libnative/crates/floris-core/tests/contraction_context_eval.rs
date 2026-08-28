use floris_core::resolve_contraction_with_context;

#[test]
fn test_unconditional_contractions_always_normalize() {
    assert_eq!(resolve_contraction_with_context("cant", None, None), Some("can't"));
    assert_eq!(resolve_contraction_with_context("dont", None, None), Some("don't"));
    assert_eq!(resolve_contraction_with_context("wont", None, None), Some("won't"));
    assert_eq!(resolve_contraction_with_context("didnt", None, None), Some("didn't"));
    assert_eq!(resolve_contraction_with_context("couldnt", None, None), Some("couldn't"));
    assert_eq!(resolve_contraction_with_context("wouldnt", None, None), Some("wouldn't"));
}

#[test]
fn test_well_disambiguation_with_context() {
    // Following verb trigger -> "we'll"
    assert_eq!(resolve_contraction_with_context("well", None, Some("see")), Some("we'll"));
    assert_eq!(resolve_contraction_with_context("well", None, Some("be")), Some("we'll"));
    assert_eq!(resolve_contraction_with_context("well", None, Some("meet")), Some("we'll"));
    assert_eq!(resolve_contraction_with_context("well", None, Some("go")), Some("we'll"));

    // Preceding adverb / modifier trigger -> "well" (None)
    assert_eq!(resolve_contraction_with_context("well", Some("as"), None), None);
    assert_eq!(resolve_contraction_with_context("well", Some("very"), None), None);
    assert_eq!(resolve_contraction_with_context("well", Some("doing"), None), None);
    assert_eq!(resolve_contraction_with_context("well", Some("oil"), None), None);
    assert_eq!(resolve_contraction_with_context("well", Some("water"), None), None);
}

#[test]
fn test_were_disambiguation_with_context() {
    // Following participle trigger -> "we're"
    assert_eq!(resolve_contraction_with_context("were", None, Some("going")), Some("we're"));
    assert_eq!(resolve_contraction_with_context("were", None, Some("coming")), Some("we're"));
    assert_eq!(resolve_contraction_with_context("were", None, Some("excited")), Some("we're"));
    assert_eq!(resolve_contraction_with_context("were", None, Some("ready")), Some("we're"));

    // Preceding subject / adverb trigger -> "were" (None)
    assert_eq!(resolve_contraction_with_context("were", Some("they"), None), None);
    assert_eq!(resolve_contraction_with_context("were", Some("we"), None), None);
    assert_eq!(resolve_contraction_with_context("were", Some("you"), None), None);
    assert_eq!(resolve_contraction_with_context("were", Some("there"), None), None);
}

#[test]
fn test_ill_disambiguation_with_context() {
    // Following verb trigger -> "I'll"
    assert_eq!(resolve_contraction_with_context("ill", None, Some("be")), Some("I'll"));
    assert_eq!(resolve_contraction_with_context("ill", None, Some("call")), Some("I'll"));
    assert_eq!(resolve_contraction_with_context("ill", None, Some("take")), Some("I'll"));

    // Preceding sickness trigger -> "ill" (None)
    assert_eq!(resolve_contraction_with_context("ill", Some("feeling"), None), None);
    assert_eq!(resolve_contraction_with_context("ill", Some("feel"), None), None);
    assert_eq!(resolve_contraction_with_context("ill", Some("critically"), None), None);
    assert_eq!(resolve_contraction_with_context("ill", Some("terminally"), None), None);
}

#[test]
fn test_shed_and_hed_disambiguation_with_context() {
    // Following verb trigger -> "she'd" / "he'd"
    assert_eq!(resolve_contraction_with_context("shed", None, Some("love")), Some("she'd"));
    assert_eq!(resolve_contraction_with_context("shed", None, Some("prefer")), Some("she'd"));
    assert_eq!(resolve_contraction_with_context("hed", None, Some("like")), Some("he'd"));
    assert_eq!(resolve_contraction_with_context("hed", None, Some("rather")), Some("he'd"));

    // Preceding noun modifier -> "shed" (None)
    assert_eq!(resolve_contraction_with_context("shed", Some("tool"), None), None);
    assert_eq!(resolve_contraction_with_context("shed", Some("storage"), None), None);
    assert_eq!(resolve_contraction_with_context("shed", Some("garden"), None), None);
}

/// Modal + "well" + verb is the adverb, never the contraction: "it may
/// well be" must not become "may we'll be". Pinned before anything wires
/// this resolver into a revision feature (audit 2026-08-28).
#[test]
fn modal_well_verb_is_adverbial() {
    use floris_core::nlp::resolve_contraction_with_context;
    for modal in ["may", "might", "could", "should", "would", "will", "can", "must"] {
        assert_eq!(
            resolve_contraction_with_context("well", Some(modal), Some("be")),
            None,
            "'{modal} well be' must stay adverbial"
        );
    }
    // the genuine contraction context still resolves
    assert_eq!(
        resolve_contraction_with_context("well", Some("tomorrow"), Some("see")),
        Some("we'll"),
    );
}

#[test]
fn test_id_disambiguation_with_context() {
    // Following modal/preference verb -> "I'd"
    assert_eq!(resolve_contraction_with_context("id", None, Some("like")), Some("I'd"));
    assert_eq!(resolve_contraction_with_context("id", None, Some("love")), Some("I'd"));
    assert_eq!(resolve_contraction_with_context("id", None, Some("prefer")), Some("I'd"));
    assert_eq!(resolve_contraction_with_context("id", None, Some("rather")), Some("I'd"));
    assert_eq!(resolve_contraction_with_context("id", None, Some("suggest")), Some("I'd"));

    // Preceding noun/identifier trigger -> "id" (None)
    assert_eq!(resolve_contraction_with_context("id", Some("user"), None), None);
    assert_eq!(resolve_contraction_with_context("id", Some("photo"), None), None);
    assert_eq!(resolve_contraction_with_context("id", Some("valid"), None), None);
    assert_eq!(resolve_contraction_with_context("id", Some("card"), None), None);
}
