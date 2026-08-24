//! Fast Damerau-Levenshtein distance calculation with threshold cutoff.

use std::cmp::min;

/// Computes the Damerau-Levenshtein distance between two strings `a` and `b`.
/// If the distance exceeds `max_threshold`, computation aborts early and returns `None`.
pub fn damerau_levenshtein_threshold(a: &str, b: &str, max_threshold: usize) -> Option<usize> {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let len_a = a_chars.len();
    let len_b = b_chars.len();

    // Quick length check
    let len_diff = if len_a > len_b { len_a - len_b } else { len_b - len_a };
    if len_diff > max_threshold {
        return None;
    }

    if len_a == 0 {
        return if len_b <= max_threshold { Some(len_b) } else { None };
    }
    if len_b == 0 {
        return if len_a <= max_threshold { Some(len_a) } else { None };
    }

    // Dynamic programming matrix with transposition support
    // (len_a + 2) x (len_b + 2)
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

    // Alphabet position cache
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

    #[test]
    fn test_identical_strings() {
        assert_eq!(damerau_levenshtein("hello", "hello"), 0);
        assert_eq!(damerau_levenshtein("", ""), 0);
    }

    #[test]
    fn test_single_operations() {
        // Insertion
        assert_eq!(damerau_levenshtein("hell", "hello"), 1);
        // Deletion
        assert_eq!(damerau_levenshtein("hello", "hell"), 1);
        // Substitution
        assert_eq!(damerau_levenshtein("hello", "jello"), 1);
        // Transposition (adjacent swap)
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
}
