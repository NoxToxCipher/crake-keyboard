//! High-performance Radix / Prefix Trie for dictionary storage and candidate lookup.

use std::collections::BTreeMap;

#[derive(Debug, Clone, Default)]
pub struct TrieNode {
    pub is_terminal: bool,
    pub frequency: u32,
    pub word: Option<String>,
    pub children: BTreeMap<char, TrieNode>,
}

#[derive(Debug, Clone, Default)]
pub struct RadixTrie {
    pub root: TrieNode,
    pub size: usize,
}

impl RadixTrie {
    pub fn new() -> Self {
        Self {
            root: TrieNode::default(),
            size: 0,
        }
    }

    /// Inserts a word with an associated frequency into the Trie.
    pub fn insert(&mut self, word: &str, frequency: u32) {
        if word.is_empty() {
            return;
        }

        let mut current = &mut self.root;
        for ch in word.chars() {
            current = current.children.entry(ch).or_default();
        }

        if !current.is_terminal {
            self.size += 1;
        }
        current.is_terminal = true;
        current.frequency = frequency;
        current.word = Some(word.to_string());
    }

    /// Checks if an exact word exists in the Trie.
    pub fn contains(&self, word: &str) -> bool {
        self.get_terminal_node(word)
            .map(|n| n.is_terminal)
            .unwrap_or(false)
    }

    /// Returns the frequency of a word if it exists.
    pub fn get_frequency(&self, word: &str) -> Option<u32> {
        self.get_terminal_node(word).and_then(|n| {
            if n.is_terminal {
                Some(n.frequency)
            } else {
                None
            }
        })
    }

    fn get_terminal_node(&self, word: &str) -> Option<&TrieNode> {
        let mut current = &self.root;
        for ch in word.chars() {
            match current.children.get(&ch) {
                Some(next) => current = next,
                None => return None,
            }
        }
        Some(current)
    }

    /// Searches for words starting with `prefix`, ordered by frequency descending.
    pub fn prefix_search(&self, prefix: &str, limit: usize) -> Vec<(String, u32)> {
        let mut results = Vec::new();
        if let Some(node) = self.get_terminal_node(prefix) {
            self.collect_words(node, &mut results);
            results.sort_by(|a, b| b.1.cmp(&a.1));
            results.truncate(limit);
        }
        results
    }

    fn collect_words(&self, node: &TrieNode, out: &mut Vec<(String, u32)>) {
        if node.is_terminal {
            if let Some(ref w) = node.word {
                out.push((w.clone(), node.frequency));
            }
        }
        for child in node.children.values() {
            self.collect_words(child, out);
        }
    }

    /// Fuzzy search matching candidate words within `max_distance` edit distance.
    /// Traverses the trie directly using branch-and-bound on the DP row to avoid brute force.
    pub fn fuzzy_search(
        &self,
        query: &str,
        max_distance: usize,
        limit: usize,
    ) -> Vec<FuzzyCandidate> {
        let query_chars: Vec<char> = query.chars().collect();
        let query_len = query_chars.len();
        let mut results = Vec::new();

        // Initial row: 0, 1, 2, 3, ...
        let initial_row: Vec<usize> = (0..=query_len).collect();

        for (&ch, child) in &self.root.children {
            self.fuzzy_search_recursive(
                child,
                ch,
                &query_chars,
                &initial_row,
                max_distance,
                &mut results,
            );
        }

        // Rank by distance ascending, then frequency descending
        results.sort_by(|a, b| {
            a.distance
                .cmp(&b.distance)
                .then_with(|| b.frequency.cmp(&a.frequency))
        });
        results.truncate(limit);
        results
    }

    fn fuzzy_search_recursive(
        &self,
        node: &TrieNode,
        ch: char,
        query: &[char],
        prev_row: &[usize],
        max_distance: usize,
        out: &mut Vec<FuzzyCandidate>,
    ) {
        let cols = query.len() + 1;
        let mut current_row = vec![0usize; cols];
        current_row[0] = prev_row[0] + 1;

        for j in 1..cols {
            let cost = if query[j - 1] == ch { 0 } else { 1 };
            let deletion = prev_row[j] + 1;
            let insertion = current_row[j - 1] + 1;
            let substitution = prev_row[j - 1] + cost;

            current_row[j] = std::cmp::min(std::cmp::min(deletion, insertion), substitution);
        }

        // If this node is a complete word and the final cell is within max_distance
        if node.is_terminal && current_row[query.len()] <= max_distance {
            if let Some(ref word) = node.word {
                out.push(FuzzyCandidate {
                    word: word.clone(),
                    distance: current_row[query.len()],
                    frequency: node.frequency,
                });
            }
        }

        // Branch and bound: continue if any entry in current_row <= max_distance
        let min_row_val = *current_row.iter().min().unwrap_or(&usize::MAX);
        if min_row_val <= max_distance {
            for (&next_ch, child) in &node.children {
                self.fuzzy_search_recursive(
                    child,
                    next_ch,
                    query,
                    &current_row,
                    max_distance,
                    out,
                );
            }
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct FuzzyCandidate {
    pub word: String,
    pub distance: usize,
    pub frequency: u32,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_insert_and_contains() {
        let mut trie = RadixTrie::new();
        trie.insert("privacy", 100);
        trie.insert("private", 80);
        trie.insert("protect", 90);

        assert!(trie.contains("privacy"));
        assert!(trie.contains("private"));
        assert!(trie.contains("protect"));
        assert!(!trie.contains("priv"));
        assert!(!trie.contains("public"));
    }

    #[test]
    fn test_prefix_search() {
        let mut trie = RadixTrie::new();
        trie.insert("cat", 50);
        trie.insert("caterpillar", 10);
        trie.insert("category", 30);
        trie.insert("dog", 100);

        let matches = trie.prefix_search("cat", 5);
        assert_eq!(matches.len(), 3);
        assert_eq!(matches[0].0, "cat");
        assert_eq!(matches[1].0, "category");
        assert_eq!(matches[2].0, "caterpillar");
    }

    #[test]
    fn test_fuzzy_search() {
        let mut trie = RadixTrie::new();
        trie.insert("hello", 1000);
        trie.insert("help", 500);
        trie.insert("hell", 200);
        trie.insert("yellow", 100);
        trie.insert("world", 800);

        let candidates = trie.fuzzy_search("helo", 1, 3);
        assert!(!candidates.is_empty());
        // "hell" or "hello" are distance 1
        let words: Vec<String> = candidates.into_iter().map(|c| c.word).collect();
        assert!(words.contains(&"hello".to_string()) || words.contains(&"hell".to_string()));
    }
}
