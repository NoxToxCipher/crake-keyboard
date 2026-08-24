//! Fast Damerau-Levenshtein distance calculation with threshold cutoff.

use std::cmp::min;

/// Computes the Damerau-Levenshtein distance between two strings `a` and `b`.
/// If the distance exceeds `max_threshold`, computation aborts early and returns `None`.
pub fn damerau_levenshtein_threshold(a: &str, b: &str, max_threshold: usize) -> Option<usize> {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let len_a = a_chars.len();
    let len_b = b_chars.len();

    // Quick length check using abs_diff
    if len_a.abs_diff(len_b) > max_threshold {
        return None;
    }

    if len_a == 0 {
        return if len_b <= max_threshold { Some(len_b) } else { None };
    }
    if len_b == 0 {
        return if len_a <= max_threshold { Some(len_a) } else { None };
    }

    // Dynamic programming matrix with transposition support
    let max_dist = len_a + len_b;
    let mut h = vec![vec![0usize; len_b + 2]; len_a + 2];

    h[0][0] = max_dist;
    for i in 0..=len_a {
        h[i + 1][0] = max_dist;
        h[i + 1][1] = i;
    }
    for j in 0..=len_b {
        h[0][j + 1] = max_dist;
        h[1][j + 1] = j;
    }

    let mut da = std::collections::HashMap::new();

    for i in 1..=len_a {
        let mut db = 0;
        let char_a = a_chars[i - 1];

        for j in 1..=len_b {
            let char_b = b_chars[j - 1];
            let i1 = *da.get(&char_b).unwrap_or(&0);
            let j1 = db;

            let cost = if char_a == char_b {
                db = j;
                0
            } else {
                1
            };

            let deletion = h[i][j + 1] + 1;
            let insertion = h[i + 1][j] + 1;
            let substitution = h[i][j] + cost;
            let transposition = if i1 > 0 && j1 > 0 {
                h[i1][j1] + (i - i1 - 1) + 1 + (j - j1 - 1)
            } else {
                max_dist
            };

            h[i + 1][j + 1] = min(min(deletion, insertion), min(substitution, transposition));
        }

        da.insert(char_a, i);
    }

    let dist = h[len_a + 1][len_b + 1];
    if dist <= max_threshold {
        Some(dist)
    } else {
        None
    }
}

/// Computes the exact Damerau-Levenshtein distance without threshold bounds.
pub fn damerau_levenshtein(a: &str, b: &str) -> usize {
    let max_len = a.chars().count() + b.chars().count();
    damerau_levenshtein_threshold(a, b, max_len).unwrap_or(max_len)
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    #[test]
    fn test_identical_strings() {
        assert_eq!(damerau_levenshtein("hello", "hello"), 0);
        assert_eq!(damerau_levenshtein("", ""), 0);
    }

    #[test]
    fn test_single_operations() {
        assert_eq!(damerau_levenshtein("hell", "hello"), 1);
        assert_eq!(damerau_levenshtein("hello", "hell"), 1);
        assert_eq!(damerau_levenshtein("hello", "jello"), 1);
        assert_eq!(damerau_levenshtein("hlelo", "hello"), 1);
        assert_eq!(damerau_levenshtein("teh", "the"), 1);
    }

    #[test]
    fn test_threshold_cutoff() {
        assert_eq!(damerau_levenshtein_threshold("apple", "aple", 1), Some(1));
        assert_eq!(damerau_levenshtein_threshold("apple", "orange", 2), None);
    }

    #[test]
    fn test_unicode_handling() {
        assert_eq!(damerau_levenshtein("café", "cafe"), 1);
        assert_eq!(damerau_levenshtein("über", "uber"), 1);
        assert_eq!(damerau_levenshtein("日本語", "日本語"), 0);
    }

    // Property-Based Verification & Mathematical Oracles
    proptest! {
        #[test]
        fn prop_distance_identity(s in "\\PC{0,30}") {
            prop_assert_eq!(damerau_levenshtein(&s, &s), 0);
        }

        #[test]
        fn prop_distance_symmetry(a in "\\PC{0,20}", b in "\\PC{0,20}") {
            prop_assert_eq!(damerau_levenshtein(&a, &b), damerau_levenshtein(&b, &a));
        }

        #[test]
        fn prop_triangle_inequality(a in "[a-z]{0,10}", b in "[a-z]{0,10}", c in "[a-z]{0,10}") {
            let d_ac = damerau_levenshtein(&a, &c);
            let d_ab = damerau_levenshtein(&a, &b);
            let d_bc = damerau_levenshtein(&b, &c);
            prop_assert!(d_ac <= d_ab + d_bc);
        }

        #[test]
        fn prop_threshold_matches_unbounded(a in "[a-z]{0,15}", b in "[a-z]{0,15}", t in 0usize..5) {
            let exact = damerau_levenshtein(&a, &b);
            let threshold_res = damerau_levenshtein_threshold(&a, &b, t);

            if exact <= t {
                prop_assert_eq!(threshold_res, Some(exact));
            } else {
                prop_assert_eq!(threshold_res, None);
            }
        }
    }
}
