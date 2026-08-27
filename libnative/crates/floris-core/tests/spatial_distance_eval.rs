use floris_core::{spatial_levenshtein_distance, spatial_substitution_cost, TouchModel};

#[test]
fn test_identity_and_symmetry() {
    assert_eq!(spatial_levenshtein_distance("crake", "crake", None), 0.0);
    let d1 = spatial_levenshtein_distance("crake", "crate", None);
    let d2 = spatial_levenshtein_distance("crate", "crake", None);
    assert!((d1 - d2).abs() < 1e-5, "Spatial distance must be symmetric");
}

#[test]
fn test_adjacent_substitution_costs_significantly_less_than_distant_keys() {
    // 'o' and 'p' are adjacent keys on QWERTY
    let cost_adjacent = spatial_substitution_cost('o', 'p', None);
    // 'o' and 'z' are opposite-corner keys
    let cost_distant = spatial_substitution_cost('o', 'z', None);

    assert_eq!(cost_adjacent, 0.50);
    assert_eq!(cost_distant, 1.50);
    assert!(cost_adjacent < cost_distant, "Adjacent substitution must cost less than distant substitution");
}

#[test]
fn test_spatial_levenshtein_ranks_physically_close_typos_higher() {
    // Query "helo"
    // 'o' and 'p' are adjacent keys (cost 0.50) -> dist("helo", "help") = 0.50
    // 'o' and 'd' are distant keys (cost 1.50) -> dist("helo", "held") = 1.50
    let dist_help = spatial_levenshtein_distance("helo", "help", None);
    let dist_held = spatial_levenshtein_distance("helo", "held", None);

    assert!(dist_help < dist_held, "Spatial distance to physically adjacent 'help' ({dist_help}) must be lower than distant 'held' ({dist_held})");
    assert!((dist_help - 0.50).abs() < 1e-3);
    assert!((dist_held - 1.50).abs() < 1e-3);
}

#[test]
fn test_spatial_levenshtein_respects_custom_touch_model_layout() {
    // Custom touch layout where 'x' and 'p' are placed side by side (dx=50, dy=0)
    let keys = vec![
        ('x', 50.0, 50.0),
        ('p', 100.0, 50.0),
        ('a', 300.0, 300.0),
    ];
    let model = TouchModel::from_layout(&keys).expect("Valid touch layout");

    let cost_with_model = spatial_substitution_cost('x', 'p', Some(&model));
    let cost_without_model = spatial_substitution_cost('x', 'p', None);

    assert!(cost_with_model < 1.0, "With custom layout placing 'x' next to 'p', cost ({cost_with_model}) must be < 1.0");
    assert_eq!(cost_without_model, 1.50, "Without layout model, QWERTY non-adjacent fallback cost is 1.50");
}
