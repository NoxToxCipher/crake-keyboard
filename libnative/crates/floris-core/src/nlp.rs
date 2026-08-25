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

    /// Dynamic Suffix / Possessive Engine.
    /// Checks if stripping common contraction suffixes yields a valid root word in the Trie.
    pub fn derive_dynamic_possessive_or_contraction(&self, query: &str) -> Option<String> {
        let trimmed_lower = query.trim().to_lowercase();
        if trimmed_lower.len() < 3 {
            return None;
        }

        // Possessive 's (e.g. bitcoin -> bitcoin's, satoshi -> satoshi's)
        if trimmed_lower.ends_with('s') && !trimmed_lower.ends_with("ss") {
            let root = &trimmed_lower[..trimmed_lower.len() - 1];
            if root.len() >= 2 && self.trie.contains(root) {
                return Some(format!("{}'s", root));
            }
        }

        // Modal 've (e.g. could -> could've)
        if trimmed_lower.ends_with("ve") {
            let root = &trimmed_lower[..trimmed_lower.len() - 2];
            if root.len() >= 3 && self.trie.contains(root) {
                return Some(format!("{}'ve", root));
            }
        }

        // Modal 'll (e.g. that -> that'll)
        if trimmed_lower.ends_with("ll") {
            let root = &trimmed_lower[..trimmed_lower.len() - 2];
            if root.len() >= 3 && self.trie.contains(root) {
                return Some(format!("{}'ll", root));
            }
        }

        // Modal 'd (e.g. how -> how'd, why -> why'd)
        if trimmed_lower.ends_with('d') {
            let root = &trimmed_lower[..trimmed_lower.len() - 1];
            if root.len() >= 3 && self.trie.contains(root) {
                return Some(format!("{}'d", root));
            }
        }

        // Modal n't (e.g. should -> shouldn't)
        if trimmed_lower.ends_with("nt") {
            let root = &trimmed_lower[..trimmed_lower.len() - 2];
            if root.len() >= 3 && self.trie.contains(root) {
                return Some(format!("{}n't", root));
            }
        }

        None
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
        } else if let Some(typo_fix) = lookup_common_typo(&trimmed_lower) {
            // 2. Wikipedia 4,500+ Misspelling Corpus instant O(log N) lookup
            let formatted = Self::apply_casing(trimmed, typo_fix);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 3. Known contraction auto-fix (e.g. dont -> don't, aint -> ain't, yall -> y'all)
            let formatted = Self::apply_casing(trimmed, contraction);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if let Some(dynamic_contraction) = self.derive_dynamic_possessive_or_contraction(trimmed) {
            // 4. Algorithmic Suffix / Possessive derivation (e.g. bitcoin's)
            let formatted = Self::apply_casing(trimmed, &dynamic_contraction);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
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
        if candidates.len() < max_candidates {
            let max_dist = if trimmed_lower.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed_lower, max_dist, max_candidates + 4);
            for fc in fuzzy {
                let formatted = Self::apply_casing(trimmed, &fc.word);
                if !contains_word(&candidates, &formatted) {
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
    fn test_dynamic_possessives_and_contractions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("bitcoin", 1000), ("computer", 900), ("friend", 800)]);

        let res_btc = engine.suggest("bitcoins", 3);
        assert_eq!(res_btc.candidates[0].word, "bitcoin's");
        assert!(res_btc.candidates[0].is_autocorrect);

        let res_comp = engine.suggest("computers", 3);
        assert_eq!(res_comp.candidates[0].word, "computer's");
        assert!(res_comp.candidates[0].is_autocorrect);
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
