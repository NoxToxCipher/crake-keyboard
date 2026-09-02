use crake_core::NlpEngine;

#[test]
fn test_space_split_beam_repairs_run_together_phrases() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("in", 255);
    nlp.trie.insert("order", 220);
    nlp.trie.insert("as", 250);
    nlp.trie.insert("well", 230);
    nlp.trie.insert("a", 255);
    nlp.trie.insert("lot", 210);

    // 1. "inorder" -> "in order"
    let split_inorder = nlp.evaluate_split_beam("inorder");
    assert!(split_inorder.is_some(), "Must detect split for 'inorder'");
    assert_eq!(split_inorder.unwrap().text, "in order");

    // 2. "aswell" -> "as well"
    let split_aswell = nlp.evaluate_split_beam("aswell");
    assert!(split_aswell.is_some(), "Must detect split for 'aswell'");
    assert_eq!(split_aswell.unwrap().text, "as well");

    // 3. "alot" -> "a lot"
    let split_alot = nlp.evaluate_split_beam("alot");
    assert!(split_alot.is_some(), "Must detect split for 'alot'");
    assert_eq!(split_alot.unwrap().text, "a lot");
}

#[test]
fn test_bottom_row_spacebar_substitution_repairs_accidental_letters() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("got", 240);
    nlp.trie.insert("to", 255);
    nlp.trie.insert("in", 255);
    nlp.trie.insert("order", 220);
    nlp.trie.insert("have", 250);
    nlp.trie.insert("a", 255);

    // 1. "gotnto" (where 'n' was an accidental bottom-row hit instead of spacebar) -> "got to"
    let split_gotnto = nlp.evaluate_split_beam("gotnto");
    assert!(split_gotnto.is_some(), "Must detect spacebar slip in 'gotnto'");
    assert_eq!(split_gotnto.unwrap().text, "got to");

    // 2. "inmorder" (where 'm' was accidental spacebar hit) -> "in order"
    let split_inmorder = nlp.evaluate_split_beam("inmorder");
    assert!(split_inmorder.is_some(), "Must detect spacebar slip in 'inmorder'");
    assert_eq!(split_inmorder.unwrap().text, "in order");

    // 3. "haveva" (where 'v' was accidental spacebar hit) -> "have a"
    let split_haveva = nlp.evaluate_split_beam("haveva");
    assert!(split_haveva.is_some(), "Must detect spacebar slip in 'haveva'");
    assert_eq!(split_haveva.unwrap().text, "have a");
}

#[test]
fn test_token_merge_beam_repairs_accidental_space_splits() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("already", 240);
    nlp.trie.insert("tomorrow", 230);
    nlp.trie.insert("everything", 235);
    nlp.trie.insert("al", 20);
    nlp.trie.insert("ready", 180);
    nlp.trie.insert("to", 255);
    nlp.trie.insert("morrow", 10);
    nlp.trie.insert("every", 200);
    nlp.trie.insert("thing", 190);

    // 1. "al" + "ready" -> "already"
    let merge_already = nlp.evaluate_merge_beam("al", "ready");
    assert!(merge_already.is_some(), "Must merge 'al ready' -> 'already'");
    assert_eq!(merge_already.unwrap().text, "already");

    // 2. "to" + "morrow" -> "tomorrow"
    let merge_tomorrow = nlp.evaluate_merge_beam("to", "morrow");
    assert!(merge_tomorrow.is_some(), "Must merge 'to morrow' -> 'tomorrow'");
    assert_eq!(merge_tomorrow.unwrap().text, "tomorrow");

    // 3. "every" + "thing" -> "everything"
    let merge_everything = nlp.evaluate_merge_beam("every", "thing");
    assert!(merge_everything.is_some(), "Must merge 'every thing' -> 'everything'");
    assert_eq!(merge_everything.unwrap().text, "everything");
}

#[test]
fn test_legitimate_unbroken_words_never_mangled() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("together", 240);
    nlp.trie.insert("to", 255);
    nlp.trie.insert("get", 245);
    nlp.trie.insert("her", 240);

    // "together" is a strong word (freq 240 >= 150) -> should not split into fragments
    let split_together = nlp.evaluate_split_beam("together");
    assert!(split_together.is_none(), "High frequency word 'together' must never be split");
}
