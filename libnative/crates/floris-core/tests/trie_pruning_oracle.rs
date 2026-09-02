//! Oracle for the collect_top max_subtree_freq pruning (trie.rs).
//! Proves the pruned prefix_search / prefix_search_filtered produce byte-identical
//! results to an independent brute-force reference (collect every terminal under the
//! prefix, sort freq-desc then lex-asc, truncate), on the shipped dictionary and on
//! random tries built through both insert() and boost_or_insert(), and that the
//! max_subtree_freq invariant (>= the true max terminal freq of every subtree) holds.

use floris_core::trie::{RadixTrie, TrieNode};

fn shipped_trie() -> RadixTrie {
    let mut t = RadixTrie::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    floris_core::parse_dict_blob(&dict, |w, f| t.insert(w, f)).expect("dict parse");
    t
}

/// Independent reference: all terminals under `prefix`, sorted freq desc then lex asc,
/// optionally filtered, truncated to `limit`.
fn reference(trie: &RadixTrie, prefix: &str, limit: usize, keep: Option<&dyn Fn(&str) -> bool>) -> Vec<(String, u32)> {
    // descend to the prefix node
    let mut node = &trie.root;
    for ch in prefix.chars() {
        match node.children.get(&ch) {
            Some(n) => node = n,
            None => return Vec::new(),
        }
    }
    let mut all: Vec<(String, u32)> = Vec::new();
    collect_all(node, &mut all);
    if let Some(k) = keep {
        all.retain(|(w, _)| k(w));
    }
    // freq desc, then lex asc
    all.sort_by(|a, b| b.1.cmp(&a.1).then_with(|| a.0.cmp(&b.0)));
    all.truncate(limit);
    all
}

fn collect_all(node: &TrieNode, out: &mut Vec<(String, u32)>) {
    if node.is_terminal {
        if let Some(w) = &node.word {
            out.push((w.clone(), node.frequency));
        }
    }
    for child in node.children.values() {
        collect_all(child, out);
    }
}

/// Returns the true max terminal frequency in the subtree, and asserts the stored
/// max_subtree_freq is never below it (the pruning-correctness invariant).
fn check_invariant(node: &TrieNode) -> u32 {
    let mut m = if node.is_terminal { node.frequency } else { 0 };
    for child in node.children.values() {
        m = m.max(check_invariant(child));
    }
    assert!(
        node.max_subtree_freq >= m,
        "max_subtree_freq {} below true subtree max {}",
        node.max_subtree_freq, m
    );
    m
}

#[test]
fn pruning_matches_reference_on_shipped_dict() {
    let trie = shipped_trie();
    check_invariant(&trie.root);

    let prefixes = [
        "", "a", "b", "c", "s", "t", "th", "the", "wor", "q", "z", "re", "un", "ing",
        "e", "i", "o", "st", "ch", "pre", "over", "x", "kz", // kz = likely-empty
    ];
    for p in prefixes {
        for limit in [1usize, 2, 5, 8, 12, 40, 300] {
            let got = trie.prefix_search(p, limit);
            let want = reference(&trie, p, limit, None);
            assert_eq!(got, want, "prefix_search mismatch prefix={p:?} limit={limit}");
        }
    }
    // filtered: keep only words ending in a chosen letter (the glide pool pattern)
    for end in ['e', 's', 'n', 'y'] {
        let keep = move |w: &str| w.ends_with(end);
        for p in ["s", "t", "th", "a", ""] {
            for limit in [1usize, 5, 8, 300] {
                let got = trie.prefix_search_filtered(p, limit, &keep);
                let want = reference(&trie, p, limit, Some(&keep));
                assert_eq!(got, want, "filtered mismatch prefix={p:?} end={end} limit={limit}");
            }
        }
    }
}

#[test]
fn pruning_matches_reference_on_random_tries() {
    // Deterministic LCG — no external rng dep.
    let mut state: u64 = 0x9E3779B97F4A7C15;
    let mut next = || {
        state = state.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        (state >> 33) as u32
    };
    let letters = ['a', 'b', 'c', 'd']; // small alphabet -> many shared prefixes & freq ties

    for trial in 0..400 {
        let mut trie = RadixTrie::new();
        let n_words = 1 + (next() % 60) as usize;
        for _ in 0..n_words {
            let len = 1 + (next() % 6) as usize;
            let word: String = (0..len).map(|_| letters[(next() % 4) as usize]).collect();
            let freq = next() % 20; // small range -> frequent ties, exercises lex tiebreak
            if next() % 3 == 0 {
                trie.boost_or_insert(&word, freq);
            } else {
                trie.insert(&word, freq); // may re-insert same word with a different freq
            }
        }
        check_invariant(&trie.root);

        for plen in 0..3usize {
            let prefix: String = (0..plen).map(|i| letters[(trial + i) % 4]).collect();
            for limit in [1usize, 2, 3, 5, 8] {
                let got = trie.prefix_search(&prefix, limit);
                let want = reference(&trie, &prefix, limit, None);
                assert_eq!(got, want, "random mismatch trial={trial} prefix={prefix:?} limit={limit}");
                let keep = |w: &str| w.len() % 2 == 0;
                let gotf = trie.prefix_search_filtered(&prefix, limit, keep);
                let wantf = reference(&trie, &prefix, limit, Some(&keep));
                assert_eq!(gotf, wantf, "random filtered mismatch trial={trial} prefix={prefix:?} limit={limit}");
            }
        }
    }
}
