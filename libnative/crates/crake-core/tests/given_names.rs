//! Irish given names. The shipped frequency table was built from news
//! English, which never saw them: typing "aoife" was corrected to "alice",
//! and no prefix of one was ever offered as a suggestion (field report
//! 2026-09-19: "it is actually impossible to write that name"). They are
//! ordinary dictionary words now, so they are never rewritten, they
//! complete from a prefix, and typing one in lower case offers the capital.

use crake_core::NlpEngine;

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    crake_core::parse_dict_blob(&dict, |w, f| {
        e.trie.insert(w, f);
        e.corpus_insert(w, f);
    })
    .expect("dict parse");
    let big = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/bigrams.crkb"
    ))
    .expect("bigram blob");
    e.load_bigrams(&big).expect("bigram parse");
    e
}

/// (typed, what the keyboard offers). Where a name carries a fada, the
/// accented spelling is what it offers: a phone keyboard cannot type one
/// without a long press, and the plain capital is still one tap away in
/// the strip (field report 2026-09-19).
const NAMES: &[(&str, &str)] = &[
    ("aoife", "Aoife"),
    ("niamh", "Niamh"),
    ("saoirse", "Saoirse"),
    ("caoimhe", "Caoimhe"),
    ("cillian", "Cillian"),
    ("tadhg", "Tadhg"),
    ("aisling", "Aisling"),
    ("diarmuid", "Diarmuid"),
    ("roisin", "Róisín"),
    ("oisin", "Oisín"),
    ("padraig", "Pádraig"),
    ("ciaran", "Ciarán"),
    ("sinead", "Sinéad"),
    ("grainne", "Gráinne"),
    ("siobhan", "Siobhán"),
    ("seamus", "Séamus"),
];

#[test]
fn a_name_typed_in_lower_case_offers_its_capital() {
    let e = engine();
    let mut failures = Vec::new();
    for &(typed, capital) in NAMES {
        let r = e.suggest_with_context(typed, "", 4);
        match r.candidates.first() {
            Some(c) if c.word == capital && c.is_autocorrect => {}
            other => failures.push(format!("{typed} -> {:?}, wanted {capital}", other.map(|c| c.word.clone()))),
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

#[test]
fn a_name_is_never_turned_into_another_word() {
    let e = engine();
    let mut failures = Vec::new();
    for &(typed, capital) in NAMES {
        // written with its capital, as a person writes a name, nothing happens
        let r = e.suggest_with_context(capital, "", 4);
        if let Some(c) = r.candidates.iter().find(|c| c.is_autocorrect && !c.word.eq_ignore_ascii_case(capital)) {
            failures.push(format!("{capital} was rewritten to {}", c.word));
        }
        // and after a previous word too
        let r = e.suggest_with_context(capital, "with", 4);
        if let Some(c) = r.candidates.iter().find(|c| c.is_autocorrect && !c.word.eq_ignore_ascii_case(capital)) {
            failures.push(format!("[with] {capital} was rewritten to {}", c.word));
        }
        let _ = typed;
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

/// A name that carries a fada offers BOTH spellings: the accented one
/// leads, the plain capital sits beside it, and neither is forced on
/// anyone.
#[test]
fn a_fada_name_offers_the_plain_spelling_too() {
    let e = engine();
    let mut failures = Vec::new();
    for &(typed, accented) in NAMES {
        if accented.is_ascii() {
            continue;
        }
        let mut c = typed.chars();
        let plain = c
            .next()
            .map(|f| f.to_uppercase().collect::<String>() + c.as_str())
            .unwrap_or_default();
        let r = e.suggest_with_context(typed, "", 4);
        let words: Vec<String> = r.candidates.iter().map(|c| c.word.clone()).collect();
        if !words.iter().any(|w| w == accented) {
            failures.push(format!("{typed} never offered {accented}: {words:?}"));
        }
        if !words.iter().any(|w| *w == plain) {
            failures.push(format!("{typed} never offered {plain}: {words:?}"));
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

#[test]
fn a_name_completes_from_its_first_letters() {
    let e = engine();
    let mut failures = Vec::new();
    for &(typed, _) in NAMES {
        let prefix: String = typed.chars().take(4).collect();
        if prefix.len() < 4 {
            continue;
        }
        // Width 10: a name whose opening letters are also a common English
        // stem ("seam" -> seamless, seams, seaman) sits below them, which is
        // right. What matters is that it is offered at all.
        let r = e.suggest_with_context(&prefix, "", 10);
        if !r.candidates.iter().any(|c| c.word.eq_ignore_ascii_case(typed)) {
            failures.push(format!(
                "{prefix} did not offer {typed}: {:?}",
                r.candidates.iter().map(|c| c.word.clone()).collect::<Vec<_>>()
            ));
        }
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}
