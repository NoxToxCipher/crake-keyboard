use crate::shorthand::lookup_shorthand;
use crate::trie::RadixTrie;
use crate::typo_corpus::lookup_common_typo;

/// Comprehensive lexicon of common English contractions (SCOWL & Wiktionary).
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
        } else if let Some(shorthand) = lookup_shorthand(&trimmed_lower) {
            // 2. SMS & internet slang shorthand quick expansion (e.g. idk -> I don't know, u -> you, r -> are)
            let formatted = Self::apply_casing(trimmed, shorthand.expansion);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: shorthand.is_autocorrect,
            });
        } else if let Some(typo_fix) = lookup_common_typo(&trimmed_lower) {
            // 3. Wikipedia 1,770+ Misspelling Corpus instant O(log N) lookup
            let formatted = Self::apply_casing(trimmed, typo_fix);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 4. Known contraction handling:
            // High-confidence unambiguous contractions (dont, cant, aint, wont, yall, etc.) auto-correct.
            // Ambiguous words (well, were, shed, wed, hell, its) do NOT auto-correct over exact word.
            let is_ambiguous = matches!(trimmed_lower.as_str(), "well" | "were" | "shed" | "wed" | "hell" | "its" | "ill" | "id");
            let formatted = Self::apply_casing(trimmed, contraction);
            if is_ambiguous && is_exact {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: false,
                });
            } else {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: true,
                });
            }
        } else if is_exact {
            // 5. Exact valid word -> NEVER auto-hijack
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        }

        // 6. Prefix completions (completions must NOT auto-commit on space)
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

        // 7. Fuzzy search for typo recovery if query is not in dictionary
        let is_capitalized = trimmed.chars().next().map_or(false, |c| c.is_uppercase());
        if candidates.len() < max_candidates {
            let max_dist = if trimmed_lower.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed_lower, max_dist, max_candidates + 4);
            for fc in fuzzy {
                let formatted = Self::apply_casing(trimmed, &fc.word);
                if !contains_word(&candidates, &formatted) {
                    // Capitalized words (names/places) and multi-word inputs must NEVER be auto-hijacked by fuzzy matching
                    let should_autocorrect = !is_exact && !is_capitalized && fc.distance == 1 && candidates.is_empty();
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

        // 8. CRITICAL: The literal raw typed word MUST ALWAYS be in the candidate list
        // so the user can always tap their exact text (e.g. custom names, passphrases, codes)
        if !contains_word(&candidates, trimmed) {
            if is_capitalized || candidates.is_empty() {
                // For capitalized names/proper nouns, prioritize the literal typed word in slot 0
                candidates.insert(0, RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            } else {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            }
        }

        if candidates.len() > max_candidates {
            candidates.truncate(max_candidates);
        }

        SuggestionResult {
            query: query.to_string(),
            is_exact_match: is_exact,
            candidates,
        }
    }

    /// Predicts the highest-frequency word for each next possible letter key (BlackBerry Flick Predictions).
    /// Returns a list of (next_char, predicted_word).
    pub fn predict_next_letter_words(&self, prefix: &str) -> Vec<(char, String)> {
        let trimmed = prefix.trim();
        if trimmed.is_empty() {
            return Vec::new();
        }

        let trimmed_lower = trimmed.to_ascii_lowercase();
        let mut results = Vec::with_capacity(26);

        for ch in 'a'..='z' {
            let candidate_prefix = format!("{}{}", trimmed_lower, ch);
            let matches = self.trie.prefix_search(&candidate_prefix, 1);
            if let Some((word, _)) = matches.first() {
                // Ensure the predicted word is longer than the prefix itself
                if word.len() > trimmed_lower.len() {
                    let formatted = Self::apply_casing(trimmed, word);
                    results.push((ch, formatted));
                }
            }
        }

        results
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_predict_next_letter_words() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("the", 1000),
            ("this", 900),
            ("that", 850),
            ("those", 800),
            ("three", 750),
            ("crypto", 950),
            ("can", 900),
            ("could", 850),
        ]);

        let th_preds = engine.predict_next_letter_words("th");
        let pred_map: std::collections::HashMap<char, String> = th_preds.into_iter().collect();

        assert_eq!(pred_map.get(&'e').unwrap(), "the");
        assert_eq!(pred_map.get(&'i').unwrap(), "this");
        assert_eq!(pred_map.get(&'a').unwrap(), "that");
        assert_eq!(pred_map.get(&'o').unwrap(), "those");
        assert_eq!(pred_map.get(&'r').unwrap(), "three");

        // Test with capitalization preservation
        let c_preds = engine.predict_next_letter_words("C");
        let c_map: std::collections::HashMap<char, String> = c_preds.into_iter().collect();
        assert_eq!(c_map.get(&'r').unwrap(), "Crypto");
        assert_eq!(c_map.get(&'a').unwrap(), "Can");
    }

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
    fn test_shorthand_slang_expansions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("you", 1000), ("are", 1000), ("know", 500), ("honest", 400)]);

        // Single letter abbreviations (suggest 'you'/'are' without space hijacking)
        let res_u = engine.suggest("u", 3);
        assert_eq!(res_u.candidates[0].word, "you");
        assert!(!res_u.candidates[0].is_autocorrect);

        let res_r = engine.suggest("r", 3);
        assert_eq!(res_r.candidates[0].word, "are");
        assert!(!res_r.candidates[0].is_autocorrect);

        // 2+ char acronyms (auto-commit enabled)
        let res_idk = engine.suggest("idk", 3);
        assert_eq!(res_idk.candidates[0].word, "I don't know");
        assert!(res_idk.candidates[0].is_autocorrect);

        let res_tbh = engine.suggest("tbh", 3);
        assert_eq!(res_tbh.candidates[0].word, "to be honest");
        assert!(res_tbh.candidates[0].is_autocorrect);

        let res_omw = engine.suggest("omw", 3);
        assert_eq!(res_omw.candidates[0].word, "on my way");
        assert!(res_omw.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_wikipedia_corpus_integration() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("definitely", 1000), ("government", 900), ("accommodation", 800)]);

        let res_def = engine.suggest("definately", 3);
        assert_eq!(res_def.candidates[0].word, "definitely");
        assert!(res_def.candidates[0].is_autocorrect);

        let res_gov = engine.suggest("goverment", 3);
        assert_eq!(res_gov.candidates[0].word, "government");
        assert!(res_gov.candidates[0].is_autocorrect);

        let res_acc = engine.suggest("accomodation", 3);
        assert_eq!(res_acc.candidates[0].word, "accommodation");
        assert!(res_acc.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_custom_names_and_literal_candidate_presence() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("lock", 1000),
            ("loom", 900),
            ("look", 800),
            ("alice", 700),
        ]);

        // Custom name "Lochran" (not in dictionary)
        let res_name = engine.suggest("Lochran", 3);
        // "Lochran" must be candidate 0 and NOT auto-corrected
        assert_eq!(res_name.candidates[0].word, "Lochran");
        assert!(!res_name.candidates[0].is_autocorrect);

        // Rare custom lowercase word "qwertyuiop"
        let res_custom = engine.suggest("qwertyuiop", 3);
        assert!(res_custom.candidates.iter().any(|c| c.word == "qwertyuiop"));
        assert!(!res_custom.candidates.iter().find(|c| c.word == "qwertyuiop").unwrap().is_autocorrect);
    }

    #[test]
    fn test_exact_words_never_mangled_to_contractions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("word", 1000),
            ("words", 900),
            ("desired", 800),
            ("desire", 700),
            ("well", 1000),
            ("were", 1000),
            ("shed", 500),
        ]);

        // "word", "words", "desired" must NEVER become "wor'd", "word's", "desire'd"
        let res_word = engine.suggest("word", 3);
        assert_eq!(res_word.candidates[0].word, "word");
        assert!(!res_word.candidates[0].is_autocorrect);

        let res_words = engine.suggest("words", 3);
        assert_eq!(res_words.candidates[0].word, "words");
        assert!(!res_words.candidates[0].is_autocorrect);

        let res_desired = engine.suggest("desired", 3);
        assert_eq!(res_desired.candidates[0].word, "desired");
        assert!(!res_desired.candidates[0].is_autocorrect);

        // Ambiguous words like "well" and "were" must NOT auto-hijack to "we'll" / "we're"
        let res_well = engine.suggest("well", 3);
        assert_eq!(res_well.candidates[0].word, "well");
        assert!(!res_well.candidates[0].is_autocorrect);

        let res_were = engine.suggest("were", 3);
        assert_eq!(res_were.candidates[0].word, "were");
        assert!(!res_were.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_contractions_vast_coverage() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("dont", 50),
            ("don't", 1000),
            ("aint", 50),
            ("ain't", 1000),
            ("cant", 50),
            ("can't", 1000),
            ("wont", 50),
            ("won't", 1000),
            ("yall", 50),
            ("y'all", 1000),
        ]);

        let test_cases = &[
            ("aint", "ain't", true),
            ("Aint", "Ain't", true),
            ("dont", "don't", true),
            ("cant", "can't", true),
            ("wont", "won't", true),
            ("yall", "y'all", true),
        ];

        for &(input, expected, should_auto) in test_cases {
            let res = engine.suggest(input, 3);
            assert_eq!(res.candidates[0].word, expected, "Failed for input: {}", input);
            assert_eq!(res.candidates[0].is_autocorrect, should_auto, "Auto-flag wrong for: {}", input);
        }
    }
}
