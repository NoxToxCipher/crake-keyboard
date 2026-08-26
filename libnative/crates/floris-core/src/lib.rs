//! floris-core: Radix Trie and NLP candidate engine for FlorisBoard.

#![forbid(unsafe_code)]
#![deny(
    clippy::all,
    clippy::correctness,
    clippy::suspicious,
    clippy::complexity,
    clippy::perf
)]

pub mod bigram;
pub mod blob;
pub mod core_dict;
pub mod hit_test;
pub mod touch_model;
pub mod distance;
pub mod glide;
pub mod nlp;
pub mod shorthand;
pub mod trie;
pub mod typo_corpus;

pub use bigram::{BigramError, BigramModel};
pub use blob::{parse_dict_blob, BlobError};
pub use core_dict::CORE_DICTIONARY;
pub use distance::{damerau_levenshtein, damerau_levenshtein_threshold};
pub use glide::{compute_dtw, simplify_rdp, GlideEngine, GlideMatch, KeyInfo, Point2D};
pub use hit_test::{HitTester, KeyRect};
pub use touch_model::TouchModel;
pub use nlp::{NlpEngine, SuggestionResult};
pub use shorthand::{lookup_shorthand, ShorthandEntry, SHORTHAND_LEXICON};
pub use trie::{FuzzyCandidate, RadixTrie, TrieNode};
pub use typo_corpus::{lookup_common_typo, WIKIPEDIA_COMMON_TYPOS};

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
