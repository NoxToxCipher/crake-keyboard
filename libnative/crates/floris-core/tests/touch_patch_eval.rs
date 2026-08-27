use floris_core::{ContactPatch, HitTester};

fn setup_three_row_keyboard() -> HitTester {
    let mut tester = HitTester::new();
    // 3 rows:
    // Row 1: q, w, e (0..100 y)
    // Row 2: a, s, d (100..200 y)
    // Row 3: z, x, c (200..300 y)
    // Spacebar: (300..400 y)
    let flat_keys = vec![
        0.0, 0.0, 100.0, 100.0,    // 0: q
        100.0, 0.0, 200.0, 100.0,  // 1: w
        200.0, 0.0, 300.0, 100.0,  // 2: e
        0.0, 100.0, 100.0, 200.0,  // 3: a
        100.0, 100.0, 200.0, 200.0,// 4: s
        200.0, 100.0, 300.0, 200.0,// 5: d
        0.0, 200.0, 100.0, 300.0,  // 6: z
        100.0, 200.0, 200.0, 300.0,// 7: x
        200.0, 200.0, 300.0, 300.0,// 8: c
        0.0, 300.0, 300.0, 400.0,  // 9: space
    ];
    let chars = vec!['q', 'w', 'e', 'a', 's', 'd', 'z', 'x', 'c', ' '];
    tester.set_keys(&flat_keys, &chars).unwrap();
    tester
}

#[test]
fn test_vertical_row_slip_elimination_with_thumb_tilt() {
    let tester = setup_three_row_keyboard();

    // User aims for 's' (nominal center is 150, 150), but thumb strike capacitive smear
    // centers at (155, 208) - physically inside row 3 key 'x' (100..200 x, 200..300 y).
    let raw_x = 155.0f32;
    let raw_y = 208.0f32;

    // 1. Raw geometric hit falls in 'x' (index 7)
    assert_eq!(tester.hit(raw_x, raw_y), Some(7), "Raw hit without contact patch falls into 'x'");

    // 2. Contact patch reports thumb orientation theta = 30 degrees (0.5236 rad), major=90px, minor=40px
    let theta = 30.0f32.to_radians();
    let patch = ContactPatch::new(raw_x, raw_y, 90.0, 40.0, theta);
    let (apex_x, apex_y) = patch.corrected_apex();

    // Apex should shift upward (dy < 0) and leftward (dx < 0)
    assert!(apex_y < 200.0, "Apex y must shift upward into 's' row (y < 200), got {apex_y}");
    assert!(apex_x < raw_x, "Apex x must shift leftward toward key center, got {apex_x}");

    // 3. Hit with patch captures intended key 's' (index 4)
    let patch_hit = tester.hit_with_patch(&patch);
    assert_eq!(patch_hit, Some(4), "Contact patch apex correction must recover intended key 's'");
}

#[test]
fn test_spacebar_bottom_row_boundary_correction() {
    let tester = setup_three_row_keyboard();

    // Left-thumb tap aimed at key 'c' (nominal center is 250, 250), but capacitive smear
    // lands at (245, 305) - physically inside the spacebar (y >= 300).
    let raw_x = 245.0f32;
    let raw_y = 305.0f32;

    assert_eq!(tester.hit(raw_x, raw_y), Some(9), "Raw hit falls on spacebar");

    // Left thumb orientation theta = -28 degrees (-0.4887 rad), major=80px, minor=36px
    let theta = (-28.0f32).to_radians();
    let patch = ContactPatch::new(raw_x, raw_y, 80.0, 36.0, theta);
    let (_apex_x, apex_y) = patch.corrected_apex();

    assert!(apex_y < 300.0, "Apex must shift up out of spacebar into key 'c', got {apex_y}");
    assert_eq!(tester.hit_with_patch(&patch), Some(8), "Contact patch must rescue key 'c' from spacebar");
}

#[test]
fn test_circular_stylus_zero_eccentricity_identity() {
    let raw_x = 120.0f32;
    let raw_y = 150.0f32;

    // Stylus: circular contact patch (major = minor = 15px)
    let patch = ContactPatch::new(raw_x, raw_y, 15.0, 15.0, 0.785);
    let (apex_x, apex_y) = patch.corrected_apex();

    assert_eq!(apex_x, raw_x, "Circular contact must not shift x");
    assert_eq!(apex_y, raw_y, "Circular contact must not shift y");
}

#[test]
fn test_nan_and_infinite_touch_posture() {
    let patch_nan = ContactPatch::new(f32::NAN, 100.0, 50.0, 20.0, 0.5);
    let (ax, ay) = patch_nan.corrected_apex();
    assert!(ax.is_nan());
    assert_eq!(ay, 100.0);

    let patch_inf = ContactPatch::new(100.0, 100.0, f32::INFINITY, 20.0, 0.5);
    let (ax, ay) = patch_inf.corrected_apex();
    assert_eq!(ax, 100.0);
    assert_eq!(ay, 100.0);
}
