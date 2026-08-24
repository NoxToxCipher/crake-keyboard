//! floris-core: High-performance, pure-Rust Radix Trie and NLP engine for FlorisBoard.

#![forbid(unsafe_code)]
#![deny(
    clippy::all,
    clippy::correctness,
    clippy::suspicious,
    clippy::complexity,
    clippy::perf,
    clippy::style
)]
#![warn(missing_docs)]

pub mod distance;
pub mod nlp;
pub mod trie;

pub use distance::{damerau_levenshtein, damerau_levenshtein_threshold};
pub use nlp::{NlpEngine, SuggestionResult};
pub use trie::{FuzzyCandidate, RadixTrie, TrieNode};

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
