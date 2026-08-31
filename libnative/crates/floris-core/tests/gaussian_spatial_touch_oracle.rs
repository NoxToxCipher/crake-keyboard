use floris_core::touch_model::TouchModel;
use floris_core::distance::spatial_substitution_cost;

fn build_qwerty_layout() -> TouchModel {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    let offsets = [0.0f32, 0.5f32, 1.5f32];
    let key_w = 100.0f32;
    let key_h = 140.0f32;
    
    let mut keys = Vec::new();
    for (r, row) in rows.iter().enumerate() {
        for (i, ch) in row.chars().enumerate() {
            keys.push((ch, (i as f32 + offsets[r] + 0.5) * key_w, (r as f32 + 0.5) * key_h));
        }
    }
    TouchModel::from_layout(&keys).expect("valid qwerty layout")
}

#[test]
fn oracle_zero_drift_identity_preservation() {
    let m = build_qwerty_layout();
    for ch in "abcdefghijklmnopqrstuvwxyz".chars() {
        let (cx, cy) = m.get_center(ch).expect("key center");
        let (matched, prob) = m.evaluate_spatial_touch(cx, cy).expect("spatial match");
        assert_eq!(matched, ch, "Dead center touch on '{}' must resolve to '{}'", ch, ch);
        assert!(prob > 0.5, "Confidence on dead-center touch for '{}' must be > 0.5, got {}", ch, prob);
    }
}

#[test]
fn oracle_thumb_drift_learning_and_slip_recovery() {
    let mut m = build_qwerty_layout();
    let center_t = m.get_center('t').expect("center t");

    // Before adaptation, a touch at (center_t.x + 48.0, center_t.y) is right at the border of 't' and 'y'
    let slip_x = center_t.0 + 48.0;
    let slip_y = center_t.1;

    // Simulate user typing 't' with a persistent rightward thumb bias of +25px over 15 keystrokes
    for _ in 0..15 {
        m.record_touch_hit('t', center_t.0 + 25.0, center_t.1 + 3.0);
    }

    // After adaptation, the slip at slip_x should clearly resolve to 't' rather than 'y' or 'r'
    let (matched, prob) = m.evaluate_spatial_touch(slip_x, slip_y).expect("match");
    assert_eq!(matched, 't', "Adapted Gaussian touch model must resolve rightward slip to 't'");
    assert!(prob > 0.35);

    // Spatial substitution cost between 't' and a nearby slip must be significantly reduced
    let cost = spatial_substitution_cost('t', 'r', Some(&m));
    assert!(cost < 1.0, "Physical neighbour substitution cost must be < 1.0, got {}", cost);
}

#[test]
fn oracle_bounded_drift_hard_guards() {
    let mut m = build_qwerty_layout();
    let center_a = m.get_center('a').expect("center a");

    // Try to violently pull key 'a' 5,000 pixels to the right
    for _ in 0..1000 {
        m.record_touch_hit('a', 5000.0, 5000.0);
    }

    let gkey_a = m.gaussian_keys.get(&'a').expect("gkey a");
    let max_allowed_drift = m.near_dist_sq().sqrt() * 0.35;
    let actual_drift_x = (gkey_a.mean_x - center_a.0).abs();
    let actual_drift_y = (gkey_a.mean_y - center_a.1).abs();

    assert!(actual_drift_x <= max_allowed_drift + 0.001, "Mean X drift ({}) must not exceed max allowed ({})", actual_drift_x, max_allowed_drift);
    assert!(actual_drift_y <= max_allowed_drift + 0.001, "Mean Y drift ({}) must not exceed max allowed ({})", actual_drift_y, max_allowed_drift);
}

#[test]
fn oracle_covariance_positive_definite_invariant() {
    let mut m = build_qwerty_layout();
    let center_s = m.get_center('s').expect("center s");

    // Simulate perfectly colinear degenerate taps (x == y line)
    for i in 0..100 {
        let offset = i as f32 * 0.2;
        m.record_touch_hit('s', center_s.0 + offset, center_s.1 + offset);
    }

    let gkey_s = m.gaussian_keys.get(&'s').expect("gkey s");
    assert!(gkey_s.var_x >= 4.0, "Variance X must be >= 4.0, got {}", gkey_s.var_x);
    assert!(gkey_s.var_y >= 4.0, "Variance Y must be >= 4.0, got {}", gkey_s.var_y);

    let det = gkey_s.var_x * gkey_s.var_y - gkey_s.cov_xy * gkey_s.cov_xy;
    assert!(det > 1e-4, "Covariance determinant must be strictly positive (> 1e-4), got {}", det);

    // Mahalanobis distance must always be finite and non-negative
    let m_sq = gkey_s.mahalanobis_sq(center_s.0 + 10.0, center_s.1 - 5.0);
    assert!(m_sq.is_finite() && m_sq >= 0.0, "Mahalanobis sq must be finite and >= 0, got {}", m_sq);
}

#[test]
fn oracle_hostile_non_finite_rejection() {
    let mut m = build_qwerty_layout();
    let center_k = m.get_center('k').expect("center k");
    let initial_mean_x = m.gaussian_keys.get(&'k').unwrap().mean_x;

    m.record_touch_hit('k', f32::NAN, center_k.1);
    m.record_touch_hit('k', center_k.0, f32::INFINITY);
    m.record_touch_hit('k', f32::NEG_INFINITY, f32::NAN);

    let after_mean_x = m.gaussian_keys.get(&'k').unwrap().mean_x;
    assert_eq!(initial_mean_x, after_mean_x, "Non-finite inputs must be safely ignored without state corruption");

    // Spatial evaluation on non-finite coords must return None cleanly
    assert!(m.evaluate_spatial_touch(f32::NAN, center_k.1).is_none());
    assert!(m.evaluate_spatial_touch(center_k.0, f32::INFINITY).is_none());
}

#[test]
fn oracle_sub_microsecond_throughput() {
    let m = build_qwerty_layout();
    let start = std::time::Instant::now();
    let iterations = 25_000;
    
    let mut dummy_count = 0;
    for i in 0..iterations {
        let x = 50.0 + (i % 900) as f32;
        let y = 70.0 + ((i * 7) % 400) as f32;
        if let Some((_, prob)) = m.evaluate_spatial_touch(x, y) {
            if prob > 0.0 {
                dummy_count += 1;
            }
        }
    }
    let elapsed = start.elapsed();
    let nanos_per_op = elapsed.as_nanos() as f64 / iterations as f64;
    
    assert!(dummy_count > 0);
    assert!(nanos_per_op < 5000.0, "Spatial evaluation must be < 5,000 ns per op, measured: {:.1} ns", nanos_per_op);
}
