use std::cmp::min;

const STACK_BUFFER_MAX: usize = 32;

/// Compute Damerau-Levenshtein distance with early threshold cutoff and flat stack memory.
/// Returns None if edit distance exceeds max_threshold.
pub fn damerau_levenshtein_threshold(a: &str, b: &str, max_threshold: usize) -> Option<usize> {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let len_a = a_chars.len();
    let len_b = b_chars.len();

    // Fast-path 1: length delta exceeds cutoff budget
    if len_a.abs_diff(len_b) > max_threshold {
        return None;
    }

    if len_a == 0 {
        return (len_b <= max_threshold).then_some(len_b);
    }
    if len_b == 0 {
        return (len_a <= max_threshold).then_some(len_a);
    }

    // Fast-path 2: identical string check
    if a == b {
        return Some(0);
    }

    let rows = len_a + 2;
    let cols = len_b + 2;
    let max_dist = len_a + len_b;

    // Stack-allocated flat matrix for strings <= 30 chars, fallback to flat heap vector for large text
    if rows <= STACK_BUFFER_MAX && cols <= STACK_BUFFER_MAX {
        let mut matrix = [0usize; STACK_BUFFER_MAX * STACK_BUFFER_MAX];
        run_dl_matrix(&a_chars, &b_chars, rows, cols, max_dist, &mut matrix, max_threshold)
    } else {
        let mut matrix = vec![0usize; rows * cols];
        run_dl_matrix(&a_chars, &b_chars, rows, cols, max_dist, &mut matrix, max_threshold)
    }
}

#[inline(always)]
fn run_dl_matrix(
    a_chars: &[char],
    b_chars: &[char],
    _rows: usize,
    cols: usize,
    max_dist: usize,
    h: &mut [usize],
    max_threshold: usize,
) -> Option<usize> {
    let len_a = a_chars.len();
    let len_b = b_chars.len();

    // Indexing helper for flat 1D matrix
    let idx = |r: usize, c: usize| -> usize { r * cols + c };

    h[idx(0, 0)] = max_dist;
    for i in 0..=len_a {
        h[idx(i + 1, 0)] = max_dist;
        h[idx(i + 1, 1)] = i;
    }
    for j in 0..=len_b {
        h[idx(0, j + 1)] = max_dist;
        h[idx(1, j + 1)] = j;
    }

    // Fast alphabet cache for ASCII (covers 95%+ of characters)
    let mut da_ascii = [0usize; 256];
    let mut da_unicode = std::collections::HashMap::new();

    let get_da = |ch: char, da_ascii: &[usize; 256], da_unicode: &std::collections::HashMap<char, usize>| -> usize {
        let u = ch as usize;
        if u < 256 {
            da_ascii[u]
        } else {
            *da_unicode.get(&ch).unwrap_or(&0)
        }
    };

    let set_da = |ch: char, val: usize, da_ascii: &mut [usize; 256], da_unicode: &mut std::collections::HashMap<char, usize>| {
        let u = ch as usize;
        if u < 256 {
            da_ascii[u] = val;
        } else {
            da_unicode.insert(ch, val);
        }
    };

    for i in 1..=len_a {
        let mut db = 0;
        let char_a = a_chars[i - 1];

        for j in 1..=len_b {
            let char_b = b_chars[j - 1];
            let i1 = get_da(char_b, &da_ascii, &da_unicode);
            let j1 = db;

            let cost = usize::from(char_a != char_b);
            if cost == 0 {
                db = j;
            }

            let deletion = h[idx(i, j + 1)] + 1;
            let insertion = h[idx(i + 1, j)] + 1;
            let substitution = h[idx(i, j)] + cost;
            let transposition = if i1 > 0 && j1 > 0 {
                h[idx(i1, j1)] + (i - i1 - 1) + 1 + (j - j1 - 1)
            } else {
                max_dist
            };

            h[idx(i + 1, j + 1)] = min(min(deletion, insertion), min(substitution, transposition));
        }

        set_da(char_a, i, &mut da_ascii, &mut da_unicode);
    }

    let dist = h[idx(len_a + 1, len_b + 1)];
    (dist <= max_threshold).then_some(dist)
}

/// Unbounded Damerau-Levenshtein distance.
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

    // Property-Based Verification & Differential Oracles
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
