//! "its" and "it's" cannot be told apart when they are typed: the shipped
//! language model has no apostrophe tokens and both forms sit at corpus
//! 253. The word that follows settles it, and that word only arrives a
//! keystroke later, so the fix is applied retroactively (field report
//! 2026-09-19).

use crake_core::retro_word_fix;

#[test]
fn the_following_word_decides_it() {
    // possessive is impossible after a determiner, pronoun, preposition or
    // verb form, so a typed "its" was "it is"
    for next in ["a", "the", "my", "not", "been", "going", "just about", "because", "me", "so"] {
        let n = next.split(' ').next().unwrap();
        if n == "just" { continue; }
        assert_eq!(retro_word_fix("its", n).as_deref(), Some("it's"), "its {n}");
    }
    // ... and "it is own" is not English, so a typed "it's" was possessive
    assert_eq!(retro_word_fix("it's", "own").as_deref(), Some("its"));
    assert_eq!(retro_word_fix("it's", "respective").as_deref(), Some("its"));
}

#[test]
fn a_form_that_is_already_right_is_left_alone() {
    assert_eq!(retro_word_fix("it's", "a"), None);
    assert_eq!(retro_word_fix("it's", "not"), None);
    assert_eq!(retro_word_fix("its", "own"), None);
}

#[test]
fn anything_the_next_word_cannot_settle_is_left_alone() {
    // adjectives and degree adverbs are ambiguous: "its very nature",
    // "its only hope", "its pretty face" are all correct possessives
    for next in ["very", "only", "pretty", "good", "hard", "best", "little"] {
        assert_eq!(retro_word_fix("its", next), None, "its {next}");
        assert_eq!(retro_word_fix("it's", next), None, "it's {next}");
    }
    // nouns that can also be a predicate: "it is time", "it is worth it"
    for next in ["time", "worth", "way", "all", "one", "magic", "money"] {
        assert_eq!(retro_word_fix("its", next), None, "its {next}");
        assert_eq!(retro_word_fix("it's", next), None, "it's {next}");
    }
    // and any other word at all
    assert_eq!(retro_word_fix("its", "tail"), None);
    assert_eq!(retro_word_fix("its", "zebra"), None);
}

#[test]
fn only_this_word_is_touched() {
    assert_eq!(retro_word_fix("is", "a"), None);
    assert_eq!(retro_word_fix("it", "a"), None);
    assert_eq!(retro_word_fix("bits", "a"), None);
    assert_eq!(retro_word_fix("", "a"), None);
    assert_eq!(retro_word_fix("its", ""), None);
}

#[test]
fn capitalisation_and_punctuation_carry_across() {
    assert_eq!(retro_word_fix("Its", "a").as_deref(), Some("It's"));
    assert_eq!(retro_word_fix("ITS", "a").as_deref(), Some("It's"));
    assert_eq!(retro_word_fix("It's", "own").as_deref(), Some("Its"));
    // the follower's trailing punctuation must not hide it
    assert_eq!(retro_word_fix("its", "me,").as_deref(), Some("it's"));
    assert_eq!(retro_word_fix("its", "over.").as_deref(), Some("it's"));
    // a typographic apostrophe counts as an apostrophe
    assert_eq!(retro_word_fix("it\u{2019}s", "a"), None);
    assert_eq!(retro_word_fix("it\u{2019}s", "own").as_deref(), Some("its"));
}
