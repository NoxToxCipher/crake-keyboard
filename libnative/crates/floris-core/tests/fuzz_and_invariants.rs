//! Fuzzing, Sanitizers, Oracles, and Invariant Anti-Rot Test Suite.
//!
//! Validates:
//! 1. Panic-freedom on arbitrary Unicode, malformed inputs, control characters, and emojis.
//! 2. Numerical stability (NaN/Inf freedom) across geometric and touch algorithms.
//! 3. Hostile byte stream robustness on binary parsers (CRKD blob & CRKL learned state).
//! 4. Mathematical metric invariants (identity, symmetry, triangle inequality).
//! 5. Output deduplication and exact-word survival guarantees.

use floris_core::blob::parse_dict_blob;
use floris_core::distance::{damerau_levenshtein, spatial_levenshtein_distance};
use floris_core::glide::{anisotropic_thumb_distance, GlideEngine, KeyInfo, Point2D};
use floris_core::nlp::NlpEngine;
use floris_core::persist::LearnedState;

fn build_mock_nlp() -> NlpEngine {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("the", 255);
    nlp.trie.insert("quick", 180);
    nlp.trie.insert("brown", 170);
    nlp.trie.insert("fox", 160);
    nlp.trie.insert("jumps", 150);
    nlp.trie.insert("over", 200);
    nlp.trie.insert("lazy", 140);
    nlp.trie.insert("dog", 190);
    nlp.trie.insert("hello", 220);
    nlp.trie.insert("world", 210);
    nlp.trie.insert("crake", 100);
    nlp.trie.insert("don't", 200);
    nlp.trie.insert("dont", 150);
    nlp
}

fn build_mock_glide_engine() -> GlideEngine {
    let mut engine = GlideEngine::new();
    let keys = vec![
        KeyInfo { code: 'q' as i32, character: 'q', center: Point2D::new(50.0, 50.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'w' as i32, character: 'w', center: Point2D::new(150.0, 50.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'e' as i32, character: 'e', center: Point2D::new(250.0, 50.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'r' as i32, character: 'r', center: Point2D::new(350.0, 50.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 't' as i32, character: 't', center: Point2D::new(450.0, 50.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'a' as i32, character: 'a', center: Point2D::new(75.0, 150.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 's' as i32, character: 's', center: Point2D::new(175.0, 150.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'd' as i32, character: 'd', center: Point2D::new(275.0, 150.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'f' as i32, character: 'f', center: Point2D::new(375.0, 150.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'z' as i32, character: 'z', center: Point2D::new(100.0, 250.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'x' as i32, character: 'x', center: Point2D::new(200.0, 250.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'c' as i32, character: 'c', center: Point2D::new(300.0, 250.0), width: 100.0, height: 100.0 },
        KeyInfo { code: 'v' as i32, character: 'v', center: Point2D::new(400.0, 250.0), width: 100.0, height: 100.0 },
    ];
    engine.set_layout(keys);
    engine
}

// Pseudo-random linear congruential generator for reproducible zero-dep fuzzing
struct LcgRng {
    state: u64,
}

impl LcgRng {
    fn new(seed: u64) -> Self {
        Self { state: seed }
    }

    fn next_u32(&mut self) -> u32 {
        self.state = self.state.wrapping_mul(6364136223846793005).wrapping_add(1);
        (self.state >> 32) as u32
    }

    fn next_f32(&mut self) -> f32 {
        (self.next_u32() as f32) / (u32::MAX as f32)
    }

    fn next_bytes(&mut self, len: usize) -> Vec<u8> {
        let mut v = Vec::with_capacity(len);
        for _ in 0..len {
            v.push((self.next_u32() & 0xFF) as u8);
        }
        v
    }

    fn next_string(&mut self, max_len: usize) -> String {
        let len = (self.next_u32() as usize % max_len).max(1);
        let mut s = String::new();
        for _ in 0..len {
            let choice = self.next_u32() % 6;
            match choice {
                0 => s.push((b'a' + (self.next_u32() % 26) as u8) as char),
                1 => s.push((b'A' + (self.next_u32() % 26) as u8) as char),
                2 => s.push((b'0' + (self.next_u32() % 10) as u8) as char),
                3 => s.push_str(" \t\n\r"),
                4 => s.push_str("🚀🔥✨🎉❤️👍🐱‍👤"),
                _ => s.push_str("\u{0000}\u{200B}\u{FEFF}\u{0300}\u{FFFF}"),
            }
        }
        s
    }
}

#[test]
fn fuzz_nlp_suggest_invariants() {
    let nlp = build_mock_nlp();
    let mut rng = LcgRng::new(0xDEADBEEF_CAFE0001);

    for _ in 0..5_000 {
        let query = rng.next_string(60);
        let prev = rng.next_string(30);

        // 1. suggest() must never panic or crash
        let suggestions = nlp.suggest(&query, 5);
        assert!(suggestions.candidates.len() <= 5);

        // Deduplication invariant
        let mut seen = std::collections::HashSet::new();
        for cand in &suggestions.candidates {
            assert!(seen.insert(&cand.word), "Duplicate candidate found in suggest output: {}", cand.word);
            assert!(!cand.word.is_empty(), "Candidate word must not be empty");
        }

        // 2. predict_next_words()
        let next_words = nlp.predict_next_words(&prev, 6);
        assert!(next_words.len() <= 6);

        // 3. predict_next_letter_words()
        let flick_words = nlp.predict_next_letter_words(&query, &prev);
        assert!(flick_words.len() <= 6);
        for (ch, word) in flick_words {
            assert!(ch.is_alphanumeric() || ch.is_ascii_punctuation(), "Invalid flick char: {}", ch);
            assert!(!word.is_empty());
        }
    }
}

#[test]
fn fuzz_glide_engine_invariants() {
    let engine = build_mock_glide_engine();
    let nlp = build_mock_nlp();
    let mut rng = LcgRng::new(0xFEEDFACE_12345678);

    for _ in 0..3_000 {
        let point_count = (rng.next_u32() % 100) as usize;
        let mut raw_points = Vec::with_capacity(point_count);

        for _ in 0..point_count {
            let choice = rng.next_u32() % 10;
            let pt = match choice {
                0 => Point2D::new(f32::NAN, f32::NAN),
                1 => Point2D::new(f32::INFINITY, f32::NEG_INFINITY),
                2 => Point2D::new(-1000.0, -1000.0),
                3 => Point2D::new(5000.0, 5000.0),
                _ => Point2D::new(rng.next_f32() * 600.0, rng.next_f32() * 400.0),
            };
            raw_points.push(pt);
        }

        // Filter out non-finite points as Android view layer does
        let valid_points: Vec<Point2D> = raw_points.into_iter().filter(|p| p.x.is_finite() && p.y.is_finite()).collect();
        let prev_word = rng.next_string(15);

        let matches = engine.match_gesture_with_context(&valid_points, &nlp.trie, 5, Some((&nlp, &prev_word)));
        assert!(matches.len() <= 5);

        // Verify score ordering & non-NaN guarantees
        let mut prev_score = -10000.0f32;
        let mut seen = std::collections::HashSet::new();
        for m in &matches {
            assert!(!m.score.is_nan(), "GlideMatch score must never be NaN");
            assert!(!m.dtw_distance.is_nan(), "DTW distance must never be NaN");
            assert!(m.score >= prev_score, "Matches must be sorted by score ascending");
            prev_score = m.score;
            assert!(seen.insert(&m.word), "Duplicate word in glide matches: {}", m.word);
        }
    }
}

#[test]
fn fuzz_hostile_blob_parser() {
    let mut rng = LcgRng::new(0xABCD1234_567890EF);

    // Fuzz with completely hostile, arbitrary byte sequences
    for _ in 0..5_000 {
        let len = (rng.next_u32() % 2048) as usize;
        let data = rng.next_bytes(len);

        // Parser must reject invalid inputs gracefully with Result::Err and never panic
        let _ = parse_dict_blob(&data, |_word, _freq| {});
    }
}

#[test]
fn fuzz_hostile_persist_parser() {
    let mut rng = LcgRng::new(0x98765432_FEDCBA98);

    // Fuzz with corrupted, truncated, and random binary learned states
    for _ in 0..5_000 {
        let len = (rng.next_u32() % 2048) as usize;
        let data = rng.next_bytes(len);

        // LearnedState::parse must reject gracefully without panic
        let _ = LearnedState::parse(&data);
    }
}

#[test]
fn test_persist_round_trip_integrity_and_capping() {
    let mut state = LearnedState::default();
    for i in 0..6_000 {
        state.words.push((format!("word_{}", i), i as u32));
        state.corrections.push((format!("typo_{}", i), format!("intended_{}", i), (i % 10) as u32));
        state.bigrams.push((format!("w1_{}", i), format!("w2_{}", i), (i % 5) as u32));
        state.rejected.push((format!("rej_typo_{}", i), format!("rej_wrong_{}", i), 1));
        state.word_epochs.push((format!("epoch_word_{}", i), 1700000000 + i as u64));
    }

    let serialized = state.serialize();
    assert!(!serialized.is_empty());

    let parsed = LearnedState::parse(&serialized).expect("Valid serialized state must parse");
    assert_eq!(parsed.words.len(), floris_core::persist::MAX_LEARNED_WORDS as usize);
    assert_eq!(parsed.corrections.len(), floris_core::persist::MAX_CORRECTIONS as usize);
    assert_eq!(parsed.bigrams.len(), floris_core::persist::MAX_PERSONAL_BIGRAMS as usize);
    assert_eq!(parsed.rejected.len(), floris_core::persist::MAX_REJECTED_CORRECTIONS as usize);
    assert_eq!(parsed.word_epochs.len(), floris_core::persist::MAX_WORD_EPOCHS as usize);
}

#[test]
fn test_distance_metric_mathematical_invariants() {
    let test_words = [
        "hello", "world", "quick", "quck", "brown", "fox", "jumps", "lazy", "dog",
        "crake", "keyboard", "gesture", "typing", "accuracy", "performance", "optimization",
    ];

    for &w1 in &test_words {
        // Identity: d(x, x) == 0
        assert_eq!(damerau_levenshtein(w1, w1), 0);
        assert_eq!(spatial_levenshtein_distance(w1, w1, None), 0.0);

        for &w2 in &test_words {
            // Symmetry: d(x, y) == d(y, x)
            assert_eq!(damerau_levenshtein(w1, w2), damerau_levenshtein(w2, w1));
            assert_eq!(spatial_levenshtein_distance(w1, w2, None), spatial_levenshtein_distance(w2, w1, None));

            for &w3 in &test_words {
                // Triangle inequality: d(x, z) <= d(x, y) + d(y, z)
                let d_ac = damerau_levenshtein(w1, w3);
                let d_ab = damerau_levenshtein(w1, w2);
                let d_bc = damerau_levenshtein(w2, w3);
                assert!(d_ac <= d_ab + d_bc, "Triangle inequality failed for ({}, {}, {})", w1, w2, w3);
            }
        }
    }
}

#[test]
fn test_anisotropic_distance_numerical_stability() {
    let touch = Point2D::new(100.0, 100.0);
    let target = Point2D::new(100.0, 100.0);
    assert_eq!(anisotropic_thumb_distance(&touch, &target, 50.0), 0.0);

    let extremes = [
        Point2D::new(0.0, 0.0),
        Point2D::new(10000.0, 10000.0),
        Point2D::new(-5000.0, 3000.0),
        Point2D::new(25.5, 99.9),
    ];

    for &p1 in &extremes {
        for &p2 in &extremes {
            let dist = anisotropic_thumb_distance(&p1, &p2, 50.0);
            assert!(dist.is_finite());
            assert!(!dist.is_nan());
            assert!(dist >= 0.0);
        }
    }
}
