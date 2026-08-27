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
pub mod british_spelling;
pub mod core_dict;
pub mod hit_test;
pub mod persist;
pub mod rescorer;
pub mod rescorer_weights;
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
pub use glide::{
    anisotropic_thumb_distance, anisotropic_thumb_distance_sq, compute_dtw,
    detect_double_letter_loops, simplify_rdp, trim_takeoff_and_landing_hooks, GlideEngine,
    GlideMatch, KeyInfo, Point2D,
};
pub use hit_test::{HitTester, KeyRect};
pub use touch_model::TouchModel;
pub use nlp::{NlpEngine, SuggestionResult};
pub use shorthand::{lookup_shorthand, ShorthandEntry, SHORTHAND_LEXICON};
pub use trie::{FuzzyCandidate, RadixTrie, TrieNode};
pub use typo_corpus::{lookup_common_typo, WIKIPEDIA_COMMON_TYPOS};

#[cfg(feature = "uniffi-bindings")]
uniffi::setup_scaffolding!();
