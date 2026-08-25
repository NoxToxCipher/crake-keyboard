use crate::trie::RadixTrie;

/// Comprehensive lexicon of common English contractions.
pub const CONTRACTIONS: &[(&str, &str)] = &[
    ("aint", "ain't"),
    ("dont", "don't"),
    ("cant", "can't"),
    ("wont", "won't"),
    ("didnt", "didn't"),
    ("isnt", "isn't"),
    ("arent", "aren't"),
    ("wasnt", "wasn't"),
    ("werent", "weren't"),
    ("hasnt", "hasn't"),
    ("havent", "haven't"),
    ("hadnt", "hadn't"),
    ("couldnt", "couldn't"),
    ("shouldnt", "shouldn't"),
    ("wouldnt", "wouldn't"),
    ("mustnt", "mustn't"),
    ("neednt", "needn't"),
    ("mightnt", "mightn't"),
    ("oughtnt", "oughtn't"),
    ("darent", "daren't"),
    ("couldve", "could've"),
    ("shouldve", "should've"),
    ("wouldve", "would've"),
    ("mightve", "might've"),
    ("mustve", "must've"),
    ("im", "I'm"),
    ("ive", "I've"),
    ("ill", "I'll"),
    ("id", "I'd"),
    ("youre", "you're"),
    ("youve", "you've"),
    ("youll", "you'll"),
    ("youd", "you'd"),
    ("theyre", "they're"),
    ("theyve", "they've"),
    ("theyll", "they'll"),
    ("theyd", "they'd"),
    ("weve", "we've"),
    ("were", "we're"),
    ("well", "we'll"),
    ("wed", "we'd"),
    ("hes", "he's"),
    ("shes", "she's"),
    ("hed", "he'd"),
    ("shed", "she'd"),
    ("its", "it's"),
    ("itll", "it'll"),
    ("itd", "it'd"),
    ("whats", "what's"),
    ("whatll", "what'll"),
    ("whatd", "what'd"),
    ("whatre", "what're"),
    ("whatve", "what've"),
    ("thats", "that's"),
    ("thatll", "that'll"),
    ("thatd", "that'd"),
    ("theres", "there's"),
    ("therell", "there'll"),
    ("thered", "there'd"),
    ("therere", "there're"),
    ("heres", "here's"),
    ("wheres", "where's"),
    ("wherell", "where'll"),
    ("whered", "where'd"),
    ("wherere", "where're"),
    ("whereve", "where've"),
    ("hows", "how's"),
    ("howll", "how'll"),
    ("howd", "how'd"),
    ("howre", "how're"),
    ("howve", "how've"),
    ("whos", "who's"),
    ("wholl", "who'll"),
    ("whod", "who'd"),
    ("whore", "who're"),
    ("whove", "who've"),
    ("whys", "why's"),
    ("whyll", "why'll"),
    ("whyd", "why'd"),
    ("whyre", "why're"),
    ("whyve", "why've"),
    ("lets", "let's"),
    ("yall", "y'all"),
    ("yalld", "y'all'd"),
    ("yallve", "y'all've"),
    ("somethings", "something's"),
    ("everythings", "everything's"),
    ("nothings", "nothing's"),
    ("someones", "someone's"),
    ("everyones", "everyone's"),
    ("anyones", "anyone's"),
    ("somebodys", "somebody's"),
    ("everybodys", "everybody's"),
    ("anybodys", "anybody's"),
    ("cmon", "c'mon"),
    ("maam", "ma'am"),
    ("oclock", "o'clock"),
    ("tis", "'tis"),
    ("twas", "'twas"),
];

/// Known common English typographical misspellings mapped to canonical corrections.
pub const COMMON_TYPOS: &[(&str, &str)] = &[
    ("teh", "the"),
    ("adn", "and"),
    ("taht", "that"),
    ("thsi", "this"),
    ("wiht", "with"),
    ("waht", "what"),
    ("wath", "what"),
    ("yuo", "you"),
    ("oyu", "you"),
    ("recieve", "receive"),
    ("recieved", "received"),
    ("recieving", "receiving"),
    ("seperate", "separate"),
    ("seperated", "separated"),
    ("seperately", "separately"),
    ("definately", "definitely"),
    ("definitly", "definitely"),
    ("untill", "until"),
    ("occured", "occurred"),
    ("occuring", "occurring"),
    ("tomo", "tomorrow"),
    ("tmrw", "tomorrow"),
    ("tommorow", "tomorrow"),
    ("alot", "a lot"),
    ("becuase", "because"),
    ("beacuse", "because"),
    ("beleive", "believe"),
    ("wierd", "weird"),
    ("freind", "friend"),
    ("truely", "truly"),
    ("calender", "calendar"),
    ("neccessary", "necessary"),
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RankedCandidate {
    pub word: String,
    pub is_autocorrect: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SuggestionResult {
    pub query: String,
    pub is_exact_match: bool,
    pub candidates: Vec<RankedCandidate>,
}

#[derive(Debug, Clone, Default)]
pub struct NlpEngine {
    pub trie: RadixTrie,
}

impl NlpEngine {
    pub fn new() -> Self {
        Self {
            trie: RadixTrie::new(),
        }
    }

    pub fn load_dictionary(&mut self, words: &[(&str, u32)]) {
        for &(word, freq) in words {
            self.trie.insert(word, freq);
        }
    }

    pub fn apply_casing(query: &str, candidate: &str) -> String {
        let is_all_upper = query.len() > 1 && query.chars().all(|c| !c.is_alphabetic() || c.is_uppercase());
        let is_first_upper = query.chars().next().map_or(false, |c| c.is_uppercase());

        if is_all_upper {
            candidate.to_uppercase()
        } else if is_first_upper {
            let mut chars = candidate.chars();
            match chars.next() {
                Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
                None => candidate.to_string(),
            }
        } else {
            candidate.to_string()
        }
    }

    pub fn suggest(&self, query: &str, max_candidates: usize) -> SuggestionResult {
        let trimmed = query.trim();
        let trimmed_lower = trimmed.to_lowercase();
        if trimmed_lower.is_empty() {
            return SuggestionResult {
                query: query.to_string(),
                is_exact_match: false,
                candidates: Vec::new(),
            };
        }

        let is_exact = self.trie.contains(&trimmed_lower);
        let mut candidates: Vec<RankedCandidate> = Vec::with_capacity(max_candidates);

        // Helper to check if a word is already in candidate list
        let contains_word = |list: &[RankedCandidate], w: &str| list.iter().any(|c| c.word.eq_ignore_ascii_case(w));

        // 1. Single-letter "i" rule -> Capitalize to "I" with autocorrect = true
        if trimmed_lower == "i" {
            candidates.push(RankedCandidate {
                word: "I".to_string(),
                is_autocorrect: true,
            });
        } else if trimmed_lower == "a" {
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        } else if let Some(&(_, typo_fix)) = COMMON_TYPOS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 2. High-confidence common typo replacement
            let formatted = Self::apply_casing(trimmed, typo_fix);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 3. Known contraction auto-fix (e.g. dont -> don't, im -> I'm)
            let formatted = Self::apply_casing(trimmed, contraction);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if is_exact {
            // 4. Exact valid word -> NEVER auto-hijack
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        }

        // 5. Prefix completions (completions must NOT auto-commit on space)
        let prefix_matches = self.trie.prefix_search(&trimmed_lower, max_candidates + 4);
        for (w, _) in prefix_matches {
            let formatted = Self::apply_casing(trimmed, &w);
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: false,
                });
            }
            if candidates.len() >= max_candidates {
                break;
            }
        }

        // 6. Fuzzy search for typo recovery if query is not in dictionary
        if candidates.len() < max_candidates {
            let max_dist = if trimmed_lower.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed_lower, max_dist, max_candidates + 4);
            for fc in fuzzy {
                let formatted = Self::apply_casing(trimmed, &fc.word);
                if !contains_word(&candidates, &formatted) {
                    // If the input was NOT a valid word and fuzzy distance is 1, enable autocorrect
                    let should_autocorrect = !is_exact && fc.distance == 1 && candidates.is_empty();
                    candidates.push(RankedCandidate {
                        word: formatted,
                        is_autocorrect: should_autocorrect,
                    });
                }
                if candidates.len() >= max_candidates {
                    break;
                }
            }
        }

        SuggestionResult {
            query: query.to_string(),
            is_exact_match: is_exact,
            candidates,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_single_letter_i_autocorrect() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("in", 1000), ("is", 900), ("it", 800), ("i", 500)]);

        let res = engine.suggest("i", 3);
        assert_eq!(res.candidates[0].word, "I");
        assert!(res.candidates[0].is_autocorrect);

        let res_upper = engine.suggest("I", 3);
        assert_eq!(res_upper.candidates[0].word, "I");
        assert!(res_upper.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_contractions_vast_coverage() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("dont", 50),
            ("don't", 1000),
            ("cant", 50),
            ("can't", 1000),
            ("wont", 50),
            ("won't", 1000),
            ("didnt", 50),
            ("didn't", 1000),
            ("im", 50),
            ("youre", 50),
            ("theyre", 50),
        ]);

        let test_cases = &[
            ("aint", "ain't", true),
            ("Aint", "Ain't", true),
            ("AINT", "AIN'T", true),
            ("dont", "don't", true),
            ("Dont", "Don't", true),
            ("DONT", "DON'T", true),
            ("cant", "can't", true),
            ("wont", "won't", true),
            ("didnt", "didn't", true),
            ("im", "I'm", true),
            ("youre", "you're", true),
            ("theyre", "they're", true),
            ("yall", "y'all", true),
            ("couldve", "could've", true),
        ];

        for &(input, expected, should_auto) in test_cases {
            let res = engine.suggest(input, 3);
            assert_eq!(res.candidates[0].word, expected, "Failed for input: {}", input);
            assert_eq!(res.candidates[0].is_autocorrect, should_auto, "Auto-flag wrong for: {}", input);
        }
    }

    #[test]
    fn test_common_typos_vast_coverage() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("the", 2000),
            ("and", 1800),
            ("that", 1500),
            ("this", 1400),
            ("with", 1300),
            ("receive", 1000),
            ("separate", 900),
            ("definitely", 800),
        ]);

        let test_cases = &[
            ("teh", "the", true),
            ("adn", "and", true),
            ("taht", "that", true),
            ("thsi", "this", true),
            ("wiht", "with", true),
            ("recieve", "receive", true),
            ("seperate", "separate", true),
            ("definately", "definitely", true),
        ];

        for &(input, expected, should_auto) in test_cases {
            let res = engine.suggest(input, 3);
            assert_eq!(res.candidates[0].word, expected, "Typo failed for: {}", input);
            assert_eq!(res.candidates[0].is_autocorrect, should_auto, "Auto-flag wrong for typo: {}", input);
        }
    }

    #[test]
    fn test_valid_word_never_hijacked() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("in", 1500),
            ("inside", 800),
            ("into", 900),
            ("the", 2000),
            ("then", 800),
        ]);

        let res = engine.suggest("in", 3);
        assert_eq!(res.candidates[0].word, "in");
        assert!(!res.candidates[0].is_autocorrect, "Valid word 'in' must NOT be flagged for auto-commit!");

        let res_the = engine.suggest("the", 3);
        assert_eq!(res_the.candidates[0].word, "the");
        assert!(!res_the.candidates[0].is_autocorrect, "Valid word 'the' must NOT be flagged for auto-commit!");
    }

    #[test]
    fn test_prefix_completions_never_autocommit() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("hello", 1200),
            ("help", 1000),
            ("helicopter", 400),
        ]);

        let res = engine.suggest("hel", 3);
        assert_eq!(res.candidates[0].word, "hello");
        assert!(!res.candidates[0].is_autocorrect, "Prefix 'hel' must NOT auto-commit on space!");
    }
}
