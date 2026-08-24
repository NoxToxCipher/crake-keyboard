//! NLP Engine for suggestion scoring, spell checking, and candidate ranking.

use crate::trie::RadixTrie;

/// Represents the ranked suggestion output for a user's input query.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SuggestionResult {
    /// The original input query string.
    pub query: String,
    /// Whether the query exactly matched an existing word in the dictionary.
    pub is_exact_match: bool,
    /// List of ranked candidate word suggestions.
    pub candidates: Vec<String>,
}

/// Natural Language Processing engine wrapping the dictionary trie.
#[derive(Debug, Clone, Default)]
pub struct NlpEngine {
    /// Internal radix trie holding the word dictionary.
    pub trie: RadixTrie,
}

impl NlpEngine {
    /// Creates a new `NlpEngine` with an empty trie.
    #[must_use]
    pub fn new() -> Self {
        Self {
            trie: RadixTrie::new(),
        }
    }

    /// Loads a dictionary slice of (word, frequency) pairs.
    pub fn load_dictionary(&mut self, words: &[(&str, u32)]) {
        for &(word, freq) in words {
            self.trie.insert(word, freq);
        }
    }

    /// Generates ranked word suggestions for a given input query.
    #[must_use]
    pub fn suggest(&self, query: &str, max_candidates: usize) -> SuggestionResult {
        let trimmed = query.trim().to_lowercase();
        if trimmed.is_empty() {
            return SuggestionResult {
                query: query.to_string(),
                is_exact_match: false,
                candidates: Vec::new(),
            };
        }

        let is_exact = self.trie.contains(&trimmed);
        let mut candidates = Vec::new();

        // 1. Prefix completions
        let prefix_matches = self.trie.prefix_search(&trimmed, max_candidates);
        for (w, _) in prefix_matches {
            if !candidates.contains(&w) {
                candidates.push(w);
            }
        }

        // 2. If candidates are sparse, perform fuzzy search
        if candidates.len() < max_candidates {
            let max_dist = if trimmed.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed, max_dist, max_candidates);
            for fc in fuzzy {
                if !candidates.contains(&fc.word) {
                    candidates.push(fc.word);
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
