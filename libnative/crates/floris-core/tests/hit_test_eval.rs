use floris_core::{HitTester, NlpEngine};

fn setup_sample_keyboard() -> HitTester {
    let mut tester = HitTester::new();
    // Simplified top two rows around QWERTY:
    // Row 1: q(0..100), w(100..200), e(200..300), r(300..400), t(400..500)
    // Row 2: a(0..100), s(100..200), d(200..300), f(300..400), g(400..500)
    // y is 0..100 for row 1, 100..200 for row 2
    let flat_keys = vec![
        0.0, 0.0, 100.0, 100.0,    // 0: q
        100.0, 0.0, 200.0, 100.0,  // 1: w
        200.0, 0.0, 300.0, 100.0,  // 2: e
        300.0, 0.0, 400.0, 100.0,  // 3: r
        400.0, 0.0, 500.0, 100.0,  // 4: t
        0.0, 100.0, 100.0, 200.0,  // 5: a
        100.0, 100.0, 200.0, 200.0,// 6: s
        200.0, 100.0, 300.0, 200.0,// 7: d
        300.0, 100.0, 400.0, 200.0,// 8: f
        400.0, 100.0, 500.0, 200.0,// 9: g
    ];
    let chars = vec!['q', 'w', 'e', 'r', 't', 'a', 's', 'd', 'f', 'g'];
    tester.set_keys(&flat_keys, &chars).unwrap();
    tester
}

#[test]
fn test_probabilistic_hit_expands_expected_next_letter_boundary() {
    let tester = setup_sample_keyboard();

    // Touch is at (192, 50) -> physically inside 'w' (100..200), but only 8px from the border of 'e' (200..300)
    let touch_x = 192.0f32;
    let touch_y = 50.0f32;

    // 1. Without priors (uniform/empty), geometric hit wins ('w', index 1)
    let raw_hit = tester.hit(touch_x, touch_y);
    assert_eq!(raw_hit, Some(1), "Raw geometric hit should be 'w'");

    let unguided_hit = tester.probabilistic_hit(touch_x, touch_y, &[]);
    assert_eq!(unguided_hit, Some(1), "Without strong priors, geometric hit 'w' wins");

    // 2. With strong prior for 'e' (e.g. following prefix "th" where 'e' is 85% probable)
    let priors = vec![('e', 0.85), ('a', 0.10), ('w', 0.02)];
    let guided_hit = tester.probabilistic_hit(touch_x, touch_y, &priors);
    assert_eq!(guided_hit, Some(2), "High LM prior must expand 'e' hit target to capture border touch");
}

#[test]
fn test_learned_touch_offsets_blend_with_probabilistic_hit() {
    let mut tester = setup_sample_keyboard();

    // User systematically taps 15px to the right of 'e'
    for _ in 0..300 {
        tester.record_hit(2, 265.0, 50.0); // nominal center is (250, 50)
    }

    let (dx, _) = tester.offset_for('e');
    assert!(dx > 10.0, "Learned offset for 'e' must shift rightward");

    // Touch at (280, 50) is near 'r' boundary (300..400)
    let priors = vec![('e', 0.70), ('r', 0.15)];
    let hit = tester.probabilistic_hit(280.0, 50.0, &priors);
    assert_eq!(hit, Some(2), "Learned offset + prior must capture shifted touch for 'e'");
}

#[test]
fn test_predict_next_char_probabilities_speed_and_normalization() {
    let mut nlp = NlpEngine::new();
    nlp.trie.insert("the", 255);
    nlp.trie.insert("there", 240);
    nlp.trie.insert("this", 220);
    nlp.trie.insert("that", 210);

    let priors = nlp.predict_next_char_probabilities("th");
    assert!(!priors.is_empty(), "Prefix 'th' must yield character completions");

    let sum: f32 = priors.iter().map(|(_, p)| *p).sum();
    assert!(sum > 0.95 && sum <= 1.01, "Probabilities must be normalized to ~1.0: sum={sum}");

    // Top prediction for "th" should be 'e' or 'i'/'a'
    let top_char = priors[0].0;
    assert!(top_char == 'e' || top_char == 'i' || top_char == 'a');
}

#[test]
fn test_nan_and_out_of_bounds_posture() {
    let tester = setup_sample_keyboard();

    assert_eq!(tester.probabilistic_hit(f32::NAN, 50.0, &[]), None);
    assert_eq!(tester.probabilistic_hit(50.0, f32::NAN, &[]), None);
    assert_eq!(tester.probabilistic_hit(-500.0, -500.0, &[]), None);
    assert_eq!(tester.rank_hits(f32::NAN, 50.0, &[], 5).len(), 0);
}
