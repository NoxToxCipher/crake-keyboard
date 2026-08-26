//! Shadow hit-testing: touch-point → key resolution in Rust.
//!
//! Mirrors `TextKeyboard.getKeyForPos` exactly: walk the keys in row-major
//! order and return the FIRST whose touch bounds contain the point, where
//! containment is half-open on both axes (`x >= left && x < right`,
//! `y >= top && y < bottom`) — the `FlorisRect.contains` contract.
//!
//! This runs in shadow mode first: the Kotlin implementation stays
//! authoritative while every touch is resolved by both sides and compared.
//! Divergence must be zero across real usage before Rust takes over, so this
//! module's job is to be *identical*, not clever. NaN coordinates match no
//! key on either side (all comparisons are false), preserving parity there
//! too.

/// One key's touch bounds, in keyboard-local pixels.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct KeyRect {
    pub left: f32,
    pub top: f32,
    pub right: f32,
    pub bottom: f32,
}

impl KeyRect {
    fn contains(&self, x: f32, y: f32) -> bool {
        x >= self.left && x < self.right && y >= self.top && y < self.bottom
    }
}

/// Holds the most recently uploaded keyboard layout. Uploads are stamped with
/// a generation so a comparison against a stale layout (another keyboard page
/// was laid out since) can be detected and skipped rather than miscounted.
#[derive(Debug, Default)]
pub struct HitTester {
    keys: Vec<KeyRect>,
    generation: u32,
}

impl HitTester {
    pub fn new() -> Self {
        Self::default()
    }

    /// Replaces the layout from a flat `[l, t, r, b] * n` array and returns
    /// the new generation. A ragged array (length not a multiple of 4) is
    /// refused, keeping whatever layout was valid before.
    pub fn set_keys(&mut self, flat: &[f32]) -> Option<u32> {
        if !flat.len().is_multiple_of(4) {
            return None;
        }
        self.keys.clear();
        for quad in flat.chunks_exact(4) {
            self.keys.push(KeyRect {
                left: quad[0],
                top: quad[1],
                right: quad[2],
                bottom: quad[3],
            });
        }
        self.generation = self.generation.wrapping_add(1);
        Some(self.generation)
    }

    pub fn generation(&self) -> u32 {
        self.generation
    }

    /// First key containing the point, in upload order — `None` mirrors
    /// Kotlin's null (no key hit).
    pub fn hit(&self, x: f32, y: f32) -> Option<usize> {
        self.keys.iter().position(|k| k.contains(x, y))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tester(rects: &[[f32; 4]]) -> HitTester {
        let mut t = HitTester::new();
        let flat: Vec<f32> = rects.iter().flatten().copied().collect();
        t.set_keys(&flat).unwrap();
        t
    }

    #[test]
    fn finds_the_containing_key() {
        let t = tester(&[[0.0, 0.0, 10.0, 10.0], [10.0, 0.0, 20.0, 10.0]]);
        assert_eq!(t.hit(5.0, 5.0), Some(0));
        assert_eq!(t.hit(15.0, 5.0), Some(1));
        assert_eq!(t.hit(25.0, 5.0), None);
    }

    #[test]
    fn boundaries_are_half_open_like_florisrect() {
        let t = tester(&[[0.0, 0.0, 10.0, 10.0], [10.0, 0.0, 20.0, 10.0]]);
        // x == right of key 0 == left of key 1: belongs to key 1.
        assert_eq!(t.hit(10.0, 5.0), Some(1));
        assert_eq!(t.hit(0.0, 0.0), Some(0));
        assert_eq!(t.hit(5.0, 10.0), None); // y == bottom: outside
        assert_eq!(t.hit(20.0, 5.0), None); // x == last right: outside
    }

    #[test]
    fn overlapping_keys_resolve_to_the_first_in_order() {
        let t = tester(&[[0.0, 0.0, 10.0, 10.0], [5.0, 0.0, 15.0, 10.0]]);
        assert_eq!(t.hit(7.0, 5.0), Some(0));
    }

    #[test]
    fn nan_matches_nothing() {
        let t = tester(&[[0.0, 0.0, 10.0, 10.0]]);
        assert_eq!(t.hit(f32::NAN, 5.0), None);
        assert_eq!(t.hit(5.0, f32::NAN), None);
    }

    #[test]
    fn empty_layout_hits_nothing_and_ragged_upload_is_refused() {
        let mut t = HitTester::new();
        assert_eq!(t.hit(5.0, 5.0), None);
        assert_eq!(t.set_keys(&[1.0, 2.0, 3.0]), None);
        let g1 = t.set_keys(&[0.0, 0.0, 10.0, 10.0]).unwrap();
        // Refused upload must not have consumed a generation or the layout.
        assert_eq!(t.set_keys(&[1.0]), None);
        assert_eq!(t.generation(), g1);
        assert_eq!(t.hit(5.0, 5.0), Some(0));
    }

    #[test]
    fn generation_increments_per_upload() {
        let mut t = HitTester::new();
        let g1 = t.set_keys(&[0.0, 0.0, 10.0, 10.0]).unwrap();
        let g2 = t.set_keys(&[0.0, 0.0, 20.0, 20.0]).unwrap();
        assert_ne!(g1, g2);
        assert_eq!(t.generation(), g2);
    }
}
