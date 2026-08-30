use floris_core::glide::{GlideEngine, KeyInfo, Point2D, NeuralGlideDecoder, MAX_NEURAL_FRAMES, NEURAL_IN_CHANNELS, NEURAL_OUT_CHANNELS};
use floris_core::trie::RadixTrie;
use std::collections::HashMap;

fn build_test_glide_engine() -> (GlideEngine, RadixTrie) {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    let offsets = [0.0f32, 0.5f32, 1.5f32];
    let key_w = 100.0f32;
    let key_h = 140.0f32;

    let mut keys = Vec::new();
    for (r, row) in rows.iter().enumerate() {
        for (i, ch) in row.chars().enumerate() {
            keys.push(KeyInfo {
                code: ch as i32,
                character: ch,
                center: Point2D::new((i as f32 + offsets[r] + 0.5) * key_w, (r as f32 + 0.5) * key_h),
                width: key_w,
                height: key_h,
            });
        }
    }

    let mut engine = GlideEngine::new();
    engine.set_layout(keys);

    let mut trie = RadixTrie::new();
    trie.insert("hello", 240);
    trie.insert("help", 180);
    trie.insert("hero", 150);
    trie.insert("world", 220);
    trie.insert("word", 200);
    trie.insert("coffee", 230);
    trie.insert("code", 210);
    trie.insert("come", 200);
    trie.insert("quick", 210);
    trie.insert("keyboard", 250);

    (engine, trie)
}

#[test]
fn oracle_clean_glide_shape_preservation() {
    let (engine, trie) = build_test_glide_engine();
    
    // Ideal trajectory for "hello": h -> e -> l -> o
    let p_h = engine.key_center('h').unwrap();
    let p_e = engine.key_center('e').unwrap();
    let p_l = engine.key_center('l').unwrap();
    let p_o = engine.key_center('o').unwrap();

    let stroke = vec![p_h, p_e, p_l, p_o];
    let matches = engine.match_gesture(&stroke, &trie, 5);

    assert!(!matches.is_empty(), "Matches must not be empty for clean glide");
    assert_eq!(matches[0].word, "hello", "Clean trace for 'hello' must rank #1");
}

#[test]
fn oracle_corner_cutting_trajectory_rescue() {
    let (engine, trie) = build_test_glide_engine();

    // Fast, sloppy corner-cutting stroke for "coffee": c -> [curved cut past o] -> f -> e
    let p_c = engine.key_center('c').unwrap();
    let p_o = engine.key_center('o').unwrap();
    let p_f = engine.key_center('f').unwrap();
    let p_e = engine.key_center('e').unwrap();

    // User cuts the corner between 'c' and 'f', only pulling 60% toward 'o'
    let cut_o = Point2D::new(
        p_c.x + (p_o.x - p_c.x) * 0.65,
        p_c.y + (p_o.y - p_c.y) * 0.65,
    );

    let sloppy_stroke = vec![p_c, cut_o, p_f, p_e];
    let matches = engine.match_gesture(&sloppy_stroke, &trie, 5);

    assert!(!matches.is_empty());
    let words: Vec<&str> = matches.iter().map(|m| m.word.as_str()).collect();
    assert!(words.contains(&"coffee"), "Corner-cut gesture must contain 'coffee' in top candidates, got {:?}", words);
}

#[test]
fn oracle_zero_allocation_and_nan_invariants() {
    let mut key_centers = HashMap::new();
    key_centers.insert('a', Point2D::new(100.0, 100.0));
    key_centers.insert('b', Point2D::new(200.0, 100.0));

    let mut features = [[0.0f32; NEURAL_IN_CHANNELS]; MAX_NEURAL_FRAMES];
    let mut logits = [[0.0f32; NEURAL_OUT_CHANNELS]; MAX_NEURAL_FRAMES];

    // 1. Hostile non-finite stroke
    let hostile_stroke = vec![Point2D::new(f32::NAN, 0.0), Point2D::new(100.0, f32::INFINITY)];
    let n = NeuralGlideDecoder::resample_stroke(&hostile_stroke, 16, &mut features);
    assert_eq!(n, 0, "Non-finite stroke must return 0 frames cleanly");

    // 2. Degenerate zero-length stroke
    let zero_stroke = vec![Point2D::new(50.0, 50.0), Point2D::new(50.0, 50.0)];
    let n_zero = NeuralGlideDecoder::resample_stroke(&zero_stroke, 16, &mut features);
    assert_eq!(n_zero, 0, "Zero-length stroke must return 0 frames cleanly");

    // 3. Score alignment with valid inputs must stay bounded in [0.0, 1.0]
    logits[0][0] = 0.9;
    logits[1][1] = 0.8;
    let score = NeuralGlideDecoder::score_word_alignment("ab", &logits, 2);
    assert!((0.0..=1.0).contains(&score), "Neural score must be bounded in [0, 1], got {}", score);
}

#[test]
fn oracle_neural_inference_sub_15_microsecond_speed() {
    let (engine, _) = build_test_glide_engine();
    let p1 = Point2D::new(100.0, 100.0);
    let p2 = Point2D::new(300.0, 200.0);
    let p3 = Point2D::new(600.0, 150.0);
    let p4 = Point2D::new(800.0, 300.0);
    let stroke = vec![p1, p2, p3, p4];

    let mut features = [[0.0f32; NEURAL_IN_CHANNELS]; MAX_NEURAL_FRAMES];
    let mut logits = [[0.0f32; NEURAL_OUT_CHANNELS]; MAX_NEURAL_FRAMES];
    let key_centers: HashMap<char, Point2D> = ('a'..='z')
        .filter_map(|c| engine.key_center(c).map(|pt| (c, pt)))
        .collect();

    let start = std::time::Instant::now();
    let iterations = 10_000;

    for _ in 0..iterations {
        let n_frames = NeuralGlideDecoder::resample_stroke(&stroke, 32, &mut features);
        if n_frames > 0 {
            NeuralGlideDecoder::forward_tcn(&features, n_frames, &mut logits, &key_centers, p1, 700.0);
            let _score = NeuralGlideDecoder::score_word_alignment("word", &logits, n_frames);
        }
    }

    let elapsed = start.elapsed();
    let micros_per_op = (elapsed.as_nanos() as f64 / iterations as f64) / 1000.0;

    let max_micros = if cfg!(debug_assertions) { 150.0 } else { 15.0 };
    assert!(micros_per_op < max_micros, "Neural TCN inference must be < {:.1} µs per op, measured: {:.2} µs", max_micros, micros_per_op);
}
