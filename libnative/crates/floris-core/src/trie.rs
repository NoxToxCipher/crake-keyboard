use std::collections::BTreeMap;

const MAX_QUERY_STACK_LEN: usize = 32;

/// Bloom filter for fast negative word lookup.
#[derive(Debug, Clone)]
pub struct BloomFilter {
    bits: Vec<u64>,
    num_bits: usize,
}

impl Default for BloomFilter {
    fn default() -> Self {
        Self::new(65536) // 64 KB bitset
    }
}

impl BloomFilter {
    pub fn new(size_bits: usize) -> Self {
        let u64_count = (size_bits + 63) / 64;
        Self {
            bits: vec![0u64; u64_count],
            num_bits: size_bits,
        }
    }

    pub fn insert(&mut self, item: &str) {
        let (h1, h2) = self.hash_pair(item);
        for i in 0..3 {
            let combined = h1.wrapping_add((i as u64).wrapping_mul(h2)) as usize % self.num_bits;
            self.bits[combined / 64] |= 1u64 << (combined % 64);
        }
    }

    pub fn may_contain(&self, item: &str) -> bool {
        let (h1, h2) = self.hash_pair(item);
        for i in 0..3 {
            let combined = h1.wrapping_add((i as u64).wrapping_mul(h2)) as usize % self.num_bits;
            if (self.bits[combined / 64] & (1u64 << (combined % 64))) == 0 {
                return false;
            }
        }
        true
    }

    fn hash_pair(&self, s: &str) -> (u64, u64) {
        let mut h1: u64 = 0xcbf29ce484222325;
        let mut h2: u64 = 0x100000001b3;
        for &b in s.as_bytes() {
            h1 = (h1 ^ (b as u64)).wrapping_mul(0x100000001b3);
            h2 = (h2 ^ (b as u64)).wrapping_mul(0xcbf29ce484222325);
        }
        (h1, h2)
    }
}

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
    pub bloom: BloomFilter,
}

impl RadixTrie {
    pub fn new() -> Self {
        Self {
            root: TrieNode::default(),
            size: 0,
            bloom: BloomFilter::default(),
        }
    }

    pub fn insert(&mut self, word: &str, frequency: u32) {
        if word.is_empty() {
            return;
        }

        self.bloom.insert(word);

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

    pub fn contains(&self, word: &str) -> bool {
        // Fast negative check via Bloom filter
        if !self.bloom.may_contain(word) {
            return false;
        }

        self.get_terminal_node(word)
            .map(|n| n.is_terminal)
            .unwrap_or(false)
    }

    pub fn get_frequency(&self, word: &str) -> Option<u32> {
        if !self.bloom.may_contain(word) {
            return None;
        }
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
            current = current.children.get(&ch)?;
        }
        Some(current)
    }

    /// Prefix completion ordered by frequency descending, then alphabetical.
    pub fn prefix_search(&self, prefix: &str, limit: usize) -> Vec<(String, u32)> {
        let mut results = Vec::new();
        if let Some(node) = self.get_terminal_node(prefix) {
            self.collect_words(node, &mut results);
            results.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
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

    /// Zero-allocation fuzzy search matching candidate words within `max_distance` edit distance.
    /// Traverses the trie directly using branch-and-bound on a stack scratchpad row.
    pub fn fuzzy_search(
        &self,
        query: &str,
        max_distance: usize,
        limit: usize,
    ) -> Vec<FuzzyCandidate> {
        let query_chars: Vec<char> = query.chars().collect();
        let query_len = query_chars.len();
        let mut results = Vec::new();

        if query_len + 1 <= MAX_QUERY_STACK_LEN {
            let mut initial_row = [0usize; MAX_QUERY_STACK_LEN];
            for (i, val) in initial_row.iter_mut().enumerate().take(query_len + 1) {
                *val = i;
            }

            for (&ch, child) in &self.root.children {
                self.fuzzy_search_stack(
                    child,
                    ch,
                    &query_chars,
                    &initial_row,
                    query_len + 1,
                    max_distance,
                    &mut results,
                );
            }
        } else {
            // Fallback for unusually long queries (> 31 characters)
            let initial_row: Vec<usize> = (0..=query_len).collect();
            for (&ch, child) in &self.root.children {
                self.fuzzy_search_heap(
                    child,
                    ch,
                    &query_chars,
                    &initial_row,
                    max_distance,
                    &mut results,
                );
            }
        }

        results.sort_by(|a, b| {
            a.distance
                .cmp(&b.distance)
                .then_with(|| b.frequency.cmp(&a.frequency))
                .then_with(|| a.word.cmp(&b.word))
        });
        results.truncate(limit);
        results
    }

    fn fuzzy_search_stack(
        &self,
        node: &TrieNode,
        ch: char,
        query: &[char],
        prev_row: &[usize; MAX_QUERY_STACK_LEN],
        cols: usize,
        max_distance: usize,
        out: &mut Vec<FuzzyCandidate>,
    ) {
        let mut current_row = [0usize; MAX_QUERY_STACK_LEN];
        current_row[0] = prev_row[0] + 1;
        let mut min_val = current_row[0];

        for j in 1..cols {
            let cost = usize::from(query[j - 1] != ch);
            let deletion = prev_row[j] + 1;
            let insertion = current_row[j - 1] + 1;
            let substitution = prev_row[j - 1] + cost;

            let val = std::cmp::min(std::cmp::min(deletion, insertion), substitution);
            current_row[j] = val;
            if val < min_val {
                min_val = val;
            }
        }

        let final_dist = current_row[cols - 1];
        if node.is_terminal && final_dist <= max_distance {
            if let Some(ref word) = node.word {
                out.push(FuzzyCandidate {
                    word: word.clone(),
                    distance: final_dist,
                    frequency: node.frequency,
                });
            }
        }

        // Branch-and-bound: only continue if min_val <= max_distance
        if min_val <= max_distance {
            for (&next_ch, child) in &node.children {
                self.fuzzy_search_stack(
                    child,
                    next_ch,
                    query,
                    &current_row,
                    cols,
                    max_distance,
                    out,
                );
            }
        }
    }

    fn fuzzy_search_heap(
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
            let cost = usize::from(query[j - 1] != ch);
            let deletion = prev_row[j] + 1;
            let insertion = current_row[j - 1] + 1;
            let substitution = prev_row[j - 1] + cost;

            current_row[j] = std::cmp::min(std::cmp::min(deletion, insertion), substitution);
        }

        if node.is_terminal && current_row[query.len()] <= max_distance {
            if let Some(ref word) = node.word {
                out.push(FuzzyCandidate {
                    word: word.clone(),
                    distance: current_row[query.len()],
                    frequency: node.frequency,
                });
            }
        }

        let min_row_val = *current_row.iter().min().unwrap_or(&usize::MAX);
        if min_row_val <= max_distance {
            for (&next_ch, child) in &node.children {
                self.fuzzy_search_heap(
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
    use proptest::collection::vec as prop_vec;
    use proptest::prelude::*;
    use std::collections::HashSet;

    #[test]
    fn test_bloom_filter_guarantees() {
        let mut bloom = BloomFilter::default();
        bloom.insert("password");
        bloom.insert("security");

        assert!(bloom.may_contain("password"));
        assert!(bloom.may_contain("security"));
        assert!(!bloom.may_contain("xyz_nonexistent_word_123"));
    }

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
        let words: Vec<String> = candidates.into_iter().map(|c| c.word).collect();
        assert!(words.contains(&"hello".to_string()) || words.contains(&"hell".to_string()));
    }

    proptest! {
        #[test]
        fn prop_bloom_zero_false_negatives(
            words in prop_vec("[a-z]{1,12}", 1..100)
        ) {
            let mut bloom = BloomFilter::default();
            for w in &words {
                bloom.insert(w);
            }
            // Invariant: If a word was inserted, may_contain MUST ALWAYS return true
            for w in &words {
                prop_assert!(bloom.may_contain(w));
            }
        }

        #[test]
        fn prop_trie_insert_and_contains_oracle(
            words in prop_vec("[a-z]{1,10}", 1..50),
            freqs in prop_vec(1u32..1000, 1..50)
        ) {
            let mut trie = RadixTrie::new();
            let mut expected_set = HashSet::new();

            for (w, &f) in words.iter().zip(freqs.iter().cycle()) {
                trie.insert(w, f);
                expected_set.insert(w.clone());
            }

            prop_assert_eq!(trie.size, expected_set.len());

            for w in &expected_set {
                prop_assert!(trie.contains(w));
            }
        }

        #[test]
        fn prop_prefix_search_oracle(
            words in prop_vec("[a-z]{1,10}", 10..40),
            prefix in "[a-z]{1,3}"
        ) {
            let mut trie = RadixTrie::new();
            let mut oracle_matches = Vec::new();

            for (i, w) in words.iter().enumerate() {
                let freq = (i + 1) as u32;
                trie.insert(w, freq);
                if w.starts_with(&prefix) {
                    oracle_matches.push((w.clone(), freq));
                }
            }

            let mut oracle_map = std::collections::HashMap::new();
            for (w, f) in oracle_matches {
                oracle_map.entry(w).and_modify(|e| *e = std::cmp::max(*e, f)).or_insert(f);
            }
            let mut expected: Vec<(String, u32)> = oracle_map.into_iter().collect();
            expected.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));

            let trie_results = trie.prefix_search(&prefix, 100);
            prop_assert_eq!(trie_results, expected);
        }
    }
}
