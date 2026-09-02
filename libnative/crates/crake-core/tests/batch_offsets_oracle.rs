//! Oracle for the 5a batch touch-offset lookup (perf item 5, sub-part 5a).
//!
//! `HitTester::offsets_for(&chars)` backs the new `nativeGetTouchOffsets` JNI
//! symbol, which replaces ~30 per-char `nativeGetTouchOffset` crossings per tap
//! with one. The batch is only safe if it is *bit-for-bit* identical to calling
//! the existing single-char `offset_for` for each input char (same lowercasing,
//! same full precision, same `(0.0, 0.0)` default for unlearned keys). This test
//! proves that equality on a learned tester over the exact char classes the
//! adaptive hit test feeds it: learned keys, unlearned keys, uppercase, digits,
//! punctuation, the NUL sentinel, and long randomized code streams.

use crake_core::HitTester;

/// A QWERTY-ish layout: 3 rows, 100x100 keys, so learned offsets are non-trivial.
fn qwerty_tester() -> HitTester {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    let mut flat = Vec::new();
    let mut labels = Vec::new();
    for (r, row) in rows.iter().enumerate() {
        for (c, ch) in row.chars().enumerate() {
            let left = c as f32 * 100.0;
            let top = r as f32 * 100.0;
            flat.extend_from_slice(&[left, top, left + 100.0, top + 100.0]);
            labels.push(ch);
        }
    }
    let mut t = HitTester::new();
    assert!(t.set_keys(&flat, &labels).is_some());
    t
}

/// Deterministic LCG so the fuzz is reproducible without a rand dependency.
struct Lcg(u64);
impl Lcg {
    fn next_u32(&mut self) -> u32 {
        self.0 = self.0.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        (self.0 >> 33) as u32
    }
}

/// The invariant: batch == N individual calls, for any char slice.
fn assert_batch_matches(t: &HitTester, chars: &[char]) {
    let batch = t.offsets_for(chars);
    assert_eq!(batch.len(), chars.len());
    for (i, &ch) in chars.iter().enumerate() {
        assert_eq!(
            batch[i],
            t.offset_for(ch),
            "batch offset for char {:?} (index {}) differs from per-char offset_for",
            ch,
            i
        );
    }
}

#[test]
fn batch_offsets_equal_per_char_after_learning() {
    let mut t = qwerty_tester();
    // Learn a few keys with off-centre hits so offsets are non-zero and clamped.
    // Key 'q' is index 0 (0..100, 0..100); 'a' is index 10 (0..100, 100..200).
    for _ in 0..50 {
        t.record_hit(0, 62.0, 71.0); // q, down-right bias
        t.record_hit(10, 41.0, 138.0); // a, up-left bias
        t.record_hit(20, 55.0, 205.0); // z
    }

    // Every char class the adaptive hit test can present.
    let cases: Vec<char> = vec![
        'q', 'a', 'z', // learned
        'w', 'e', 'p', 'm', // unlearned but in-layout
        'Q', 'A', 'Z', // uppercase of learned -> offset_for lowercases
        'W', 'M', // uppercase unlearned
        '1', '9', ' ', '.', ',', '!', '\0', // non-letters + NUL sentinel
        'é', 'ß', '中', // non-ASCII (lowercasing is ASCII-only; still must match)
    ];
    assert_batch_matches(&t, &cases);

    // Empty slice -> empty result (JNI length-0 path).
    assert!(t.offsets_for(&[]).is_empty());
}

#[test]
fn batch_offsets_equal_per_char_fuzz() {
    let mut t = qwerty_tester();
    let mut rng = Lcg(0x5eed_1234_abcd_0001);
    // Randomly learn some keys first.
    for _ in 0..300 {
        let idx = (rng.next_u32() % 26) as usize;
        let x = (rng.next_u32() % 400) as f32;
        let y = (rng.next_u32() % 300) as f32;
        t.record_hit(idx, x, y);
    }
    // Many randomized code streams of varied length, mixing valid + junk codes.
    for _ in 0..2000 {
        let n = (rng.next_u32() % 40) as usize;
        let chars: Vec<char> = (0..n)
            .map(|_| {
                // Mostly ASCII letters/digits, occasionally arbitrary scalar values.
                if rng.next_u32() % 4 == 0 {
                    char::from_u32(rng.next_u32() % 0x110000).unwrap_or('\0')
                } else {
                    char::from_u32(0x20 + rng.next_u32() % 0x5f).unwrap_or('\0')
                }
            })
            .collect();
        assert_batch_matches(&t, &chars);
    }
}
