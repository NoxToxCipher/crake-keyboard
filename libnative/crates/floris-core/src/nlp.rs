use crate::trie::RadixTrie;

const CONTRACTIONS: &[(&str, &str)] = &[
    ("dont", "don't"),
    ("cant", "can't"),
    ("wont", "won't"),
    ("didnt", "didn't"),
    ("isnt", "isn't"),
    ("arent", "aren't"),
    ("hasnt", "hasn't"),
    ("havent", "haven't"),
    ("hadnt", "hadn't"),
    ("couldnt", "couldn't"),
    ("shouldnt", "shouldn't"),
    ("wouldnt", "wouldn't"),
    ("im", "I'm"),
    ("ive", "I've"),
    ("ill", "I'll"),
    ("youre", "you're"),
    ("youve", "you've"),
    ("youll", "you'll"),
    ("theyre", "they're"),
    ("theyve", "they've"),
    ("theyll", "they'll"),
    ("weve", "we've"),
    ("were", "we're"),
    ("hes", "he's"),
    ("shes", "she's"),
    ("its", "it's"),
    ("whats", "what's"),
    ("thats", "that's"),
    ("theres", "there's"),
    ("heres", "here's"),
    ("wheres", "where's"),
    ("hows", "how's"),
    ("lets", "let's"),
];

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SuggestionResult {
    pub query: String,
    pub is_exact_match: bool,
    pub candidates: Vec<String>,
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

    fn apply_casing(query: &str, candidate: &str) -> String {
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
        let mut candidates = Vec::with_capacity(max_candidates);

        // 1. Single-letter "I" / "A" or exact match priority
        if trimmed_lower == "i" {
            candidates.push("I".to_string());
        } else if trimmed_lower == "a" {
            candidates.push(trimmed.to_string());
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            let formatted = Self::apply_casing(trimmed, contraction);
            candidates.push(formatted);
        } else if is_exact {
            candidates.push(trimmed.to_string());
        }

        // 2. Direct prefix completions (sorted by frequency descending in Trie)
        let prefix_matches = self.trie.prefix_search(&trimmed_lower, max_candidates + 2);
        for (w, _) in prefix_matches {
            let formatted = Self::apply_casing(trimmed, &w);
            if !candidates.contains(&formatted) {
                candidates.push(formatted);
            }
            if candidates.len() >= max_candidates {
                break;
            }
        }

        // 3. Fuzzy fallback for typo recovery
        if candidates.len() < max_candidates {
            let max_dist = if trimmed_lower.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed_lower, max_dist, max_candidates + 2);
            for fc in fuzzy {
                let formatted = Self::apply_casing(trimmed, &fc.word);
                if !candidates.contains(&formatted) {
                    candidates.push(formatted);
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
    fn test_nlp_single_letter_i() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("in", 1000), ("is", 900), ("it", 800), ("i", 500)]);

        let res = engine.suggest("i", 3);
        assert_eq!(res.candidates[0], "I");

        let res_upper = engine.suggest("I", 3);
        assert_eq!(res_upper.candidates[0], "I");
    }

    #[test]
    fn test_nlp_contractions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("dont", 100), ("don't", 1000), ("done", 800)]);

        let res = engine.suggest("dont", 3);
        assert_eq!(res.candidates[0], "don't");

        let res_cap = engine.suggest("Dont", 3);
        assert_eq!(res_cap.candidates[0], "Don't");

        let res_im = engine.suggest("im", 3);
        assert_eq!(res_im.candidates[0], "I'm");
    }

    #[test]
    fn test_nlp_casing_preservation() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("security", 1000), ("secure", 800)]);

        let res_lower = engine.suggest("sec", 2);
        assert_eq!(res_lower.candidates[0], "security");

        let res_title = engine.suggest("Sec", 2);
        assert_eq!(res_title.candidates[0], "Security");

        let res_upper = engine.suggest("SEC", 2);
        assert_eq!(res_upper.candidates[0], "SECURITY");
    }

    #[test]
    fn test_nlp_suggestions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("security", 1000),
            ("secure", 800),
            ("secrecy", 500),
            ("privacy", 1200),
            ("protect", 900),
        ]);

        let res = engine.suggest("sec", 3);
        assert!(!res.is_exact_match);
        assert_eq!(res.candidates, vec!["security", "secure", "secrecy"]);

        let res_exact = engine.suggest("privacy", 3);
        assert!(res_exact.is_exact_match);
        assert_eq!(res_exact.candidates[0], "privacy");
    }

    #[test]
    fn test_nlp_typo_recovery() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("password", 1000),
            ("passkey", 800),
            ("pattern", 500),
        ]);

        let res = engine.suggest("pasword", 3);
        assert!(!res.is_exact_match);
        assert!(res.candidates.contains(&"password".to_string()));
    }
}
