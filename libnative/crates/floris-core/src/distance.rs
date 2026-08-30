use std::cmp::min;

const STACK_BUFFER_MAX: usize = 32;

/// Compute Damerau-Levenshtein distance with threshold cutoff.
/// Returns None if edit distance exceeds max_threshold.
pub fn damerau_levenshtein_threshold(a: &str, b: &str, max_threshold: usize) -> Option<usize> {
    // Identical string fast-path
    if a == b {
        return Some(0);
    }

    let mut a_buf = ['\0'; 32];
    let mut a_len = 0;
    for ch in a.chars() {
        if a_len < 32 {
            a_buf[a_len] = ch;
        }
        a_len += 1;
    }

    let mut b_buf = ['\0'; 32];
    let mut b_len = 0;
    for ch in b.chars() {
        if b_len < 32 {
            b_buf[b_len] = ch;
        }
        b_len += 1;
    }

    // Length delta exceeds threshold budget
    if a_len.abs_diff(b_len) > max_threshold {
        return None;
    }

    if a_len == 0 {
        return (b_len <= max_threshold).then_some(b_len);
    }
    if b_len == 0 {
        return (a_len <= max_threshold).then_some(a_len);
    }

    let rows = a_len + 2;
    let cols = b_len + 2;
    let max_dist = a_len + b_len;

    // Stack-allocated flat matrix for strings <= 30 chars, fallback to flat heap vector for large text
    if rows <= STACK_BUFFER_MAX && cols <= STACK_BUFFER_MAX {
        let mut matrix = [0usize; STACK_BUFFER_MAX * STACK_BUFFER_MAX];
        run_dl_matrix(&a_buf[..a_len], &b_buf[..b_len], rows, cols, max_dist, &mut matrix, max_threshold)
    } else {
        let a_chars: Vec<char> = a.chars().collect();
        let b_chars: Vec<char> = b.chars().collect();
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


/// Returns the millimeter-aware substitution cost between two characters (Idea 7 / Loops 19-21).
#[inline]
pub fn spatial_substitution_cost(a: char, b: char, touch_model: Option<&crate::TouchModel>) -> f32 {
    let a_low = a.to_ascii_lowercase();
    let b_low = b.to_ascii_lowercase();
    if a_low == b_low {
        return 0.0;
    }

    if let Some(model) = touch_model {
        // If Bivariate Gaussian model has learned key distribution for candidate 'b', evaluate Mahalanobis cost
        if let (Some(p1), Some(gkey_b)) = (model.get_center(a_low), model.gaussian_keys.get(&b_low)) {
            let m_sq = gkey_b.mahalanobis_sq(p1.0, p1.1);
            if m_sq <= 9.0 {
                // Within 3-sigma ellipse of candidate key: smooth Mahalanobis substitution cost
                let density = (-0.5 * m_sq).exp();
                return 0.25 + 0.65 * (1.0 - density);
            }
        }
        if let (Some(p1), Some(p2)) = (model.get_center(a_low), model.get_center(b_low)) {
            let dx = p1.0 - p2.0;
            let dy = p1.1 - p2.1;
            let dist_sq = dx * dx + dy * dy;
            let near_dist_sq = model.near_dist_sq();
            if near_dist_sq > 0.0 && dist_sq <= near_dist_sq {
                let ratio = (dist_sq / near_dist_sq).min(1.0);
                return 0.35 + 0.50 * ratio;
            }
        }
    }

    if is_qwerty_adjacent(a_low, b_low) {
        0.50
    } else {
        1.50
    }
}

#[inline]
fn is_qwerty_adjacent(a: char, b: char) -> bool {
    const ADJACENCIES: &[(char, &[char])] = &[
        ('q', &['w', 'a', 's', '1', '2']),
        ('w', &['q', 'e', 'a', 's', 'd', '2', '3']),
        ('e', &['w', 'r', 's', 'd', 'f', '3', '4']),
        ('r', &['e', 't', 'd', 'f', 'g', '4', '5']),
        ('t', &['r', 'y', 'f', 'g', 'h', '5', '6']),
        ('y', &['t', 'u', 'g', 'h', 'j', '6', '7']),
        ('u', &['y', 'i', 'h', 'j', 'k', '7', '8']),
        ('i', &['u', 'o', 'j', 'k', 'l', '8', '9']),
        ('o', &['i', 'p', 'k', 'l', '9', '0']),
        ('p', &['o', 'l', '0']),
        ('a', &['q', 'w', 's', 'z', 'x']),
        ('s', &['q', 'w', 'e', 'a', 'd', 'z', 'x', 'c']),
        ('d', &['w', 'e', 'r', 's', 'f', 'x', 'c', 'v']),
        ('f', &['e', 'r', 't', 'd', 'g', 'c', 'v', 'b']),
        ('g', &['r', 't', 'y', 'f', 'h', 'v', 'b', 'n']),
        ('h', &['t', 'y', 'u', 'g', 'j', 'b', 'n', 'm']),
        ('j', &['y', 'u', 'i', 'h', 'k', 'n', 'm']),
        ('k', &['u', 'i', 'o', 'j', 'l', 'm']),
        ('l', &['i', 'o', 'p', 'k']),
        ('z', &['a', 's', 'x']),
        ('x', &['z', 'a', 's', 'd', 'c']),
        ('c', &['x', 's', 'd', 'f', 'v']),
        ('v', &['c', 'd', 'f', 'g', 'b']),
        ('b', &['v', 'f', 'g', 'h', 'n']),
        ('n', &['b', 'g', 'h', 'j', 'm']),
        ('m', &['n', 'h', 'j', 'k']),
    ];
    ADJACENCIES.iter().any(|&(k, adj)| k == a && adj.contains(&b))
}

/// Calculates the millimeter-aware spatial Levenshtein distance between query and target
/// using stack-allocated rolling DP buffers with zero heap allocation (Idea 7 / Loop 21).
#[inline]
#[allow(clippy::needless_range_loop)]
pub fn spatial_levenshtein_distance(
    query: &str,
    target: &str,
    touch_model: Option<&crate::TouchModel>,
) -> f32 {
    if query == target {
        return 0.0;
    }

    let mut q_buf = ['\0'; 32];
    let mut t_buf = ['\0'; 32];

    let mut m = 0;
    for ch in query.chars() {
        if m >= 32 { return 32.0; }
        q_buf[m] = ch;
        m += 1;
    }

    let mut n = 0;
    for ch in target.chars() {
        if n >= 32 { return 32.0; }
        t_buf[n] = ch;
        n += 1;
    }

    if m == 0 { return n as f32; }
    if n == 0 { return m as f32; }

    let mut dp = [[0.0f32; 33]; 33];

    for i in 0..=m {
        dp[i][0] = i as f32;
    }
    for j in 0..=n {
        dp[0][j] = j as f32;
    }

    for i in 1..=m {
        for j in 1..=n {
            let cost = spatial_substitution_cost(q_buf[i - 1], t_buf[j - 1], touch_model);
            let deletion = dp[i - 1][j] + 1.0;
            let insertion = dp[i][j - 1] + 1.0;
            let substitution = dp[i - 1][j - 1] + cost;

            let mut best = deletion.min(insertion).min(substitution);

            if i > 1 && j > 1 && q_buf[i - 1] == t_buf[j - 2] && q_buf[i - 2] == t_buf[j - 1] {
                let trans_cost = dp[i - 2][j - 2] + 0.6;
                best = best.min(trans_cost);
            }

            dp[i][j] = best;
        }
    }

    dp[m][n]
}
