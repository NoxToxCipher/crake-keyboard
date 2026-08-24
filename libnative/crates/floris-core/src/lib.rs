//! floris-core: High-performance, pure-Rust Radix Trie and NLP engine for FlorisBoard.

pub mod distance;
pub mod nlp;
pub mod trie;

pub use distance::{damerau_levenshtein, damerau_levenshtein_threshold};
pub use nlp::{NlpEngine, SuggestionResult};
pub use trie::{FuzzyCandidate, RadixTrie, TrieNode};

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
