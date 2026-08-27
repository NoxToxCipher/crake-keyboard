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

/// EMA smoothing per tap: slow enough that one wild tap cannot move a key,
/// fast enough that a real bias shows within a few hundred taps.
const OFFSET_EMA_ALPHA: f32 = 0.02;
/// Learned offsets are clamped to this fraction of the key's smaller side —
/// personalization may nudge a key, never relocate it.
const OFFSET_CLAMP_FRACTION: f32 = 0.4;

pub const OFFSETS_MAGIC: [u8; 4] = *b"CRKT";
pub const OFFSETS_VERSION: u8 = 1;
pub const MAX_OFFSET_KEYS: u32 = 256;

/// Holds the most recently uploaded keyboard layout. Uploads are stamped with
/// a generation so a comparison against a stale layout (another keyboard page
/// was laid out since) can be detected and skipped rather than miscounted.
///
/// A candidate key hit scored with spatial Gaussian proximity and LM probability priors (Idea 1 / Loops 1-3).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ProbabilisticHit {
    pub index: usize,
    pub character: char,
    pub probability: f32,
    pub score: f32,
}

/// Holds the most recently uploaded keyboard layout. Uploads are stamped with
/// a generation so a comparison against a stale layout (another keyboard page
/// was laid out since) can be detected and skipped rather than miscounted.
///
/// Also the accumulation point for PER-KEY TOUCH OFFSETS: every in-bounds
/// hit updates an exponential moving average of where the user's finger
/// lands relative to that key's centre. Keyed by the key's character (not
/// its index), so the bias survives layout rebuilds and pages.
#[derive(Debug, Default)]
pub struct HitTester {
    keys: Vec<KeyRect>,
    chars: Vec<char>,
    generation: u32,
    offsets: std::collections::HashMap<char, (f32, f32)>,
}

impl HitTester {
    pub fn new() -> Self {
        Self::default()
    }

    /// Replaces the layout from a flat `[l, t, r, b] * n` array and returns
    /// the new generation. A ragged array (length not a multiple of 4) is
    /// refused, keeping whatever layout was valid before. `chars` labels
    /// each key for offset learning; when its length disagrees with the
    /// rect count the labels are dropped (learning pauses, hit-testing
    /// continues).
    pub fn set_keys(&mut self, flat: &[f32], chars: &[char]) -> Option<u32> {
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
        self.chars = if chars.len() == self.keys.len() {
            chars.iter().map(|c| c.to_ascii_lowercase()).collect()
        } else {
            Vec::new()
        };
        self.generation = self.generation.wrapping_add(1);
        Some(self.generation)
    }

    /// Records an in-bounds hit for offset learning: EMA of the touch's
    /// delta from the key centre, clamped so a bias can nudge but never
    /// relocate a key.
    pub fn record_hit(&mut self, index: usize, x: f32, y: f32) {
        let (Some(rect), Some(&ch)) = (self.keys.get(index), self.chars.get(index)) else {
            return;
        };
        if !ch.is_alphabetic() {
            return;
        }
        let cx = (rect.left + rect.right) * 0.5;
        let cy = (rect.top + rect.bottom) * 0.5;
        let max_dx = (rect.right - rect.left) * OFFSET_CLAMP_FRACTION;
        let max_dy = (rect.bottom - rect.top) * OFFSET_CLAMP_FRACTION;
        let entry = self.offsets.entry(ch).or_insert((0.0, 0.0));
        entry.0 = (entry.0 * (1.0 - OFFSET_EMA_ALPHA) + (x - cx) * OFFSET_EMA_ALPHA)
            .clamp(-max_dx, max_dx);
        entry.1 = (entry.1 * (1.0 - OFFSET_EMA_ALPHA) + (y - cy) * OFFSET_EMA_ALPHA)
            .clamp(-max_dy, max_dy);
    }

    /// The learned per-key bias for a character, (0, 0) when unknown.
    pub fn offset_for(&self, ch: char) -> (f32, f32) {
        self.offsets
            .get(&ch.to_ascii_lowercase())
            .copied()
            .unwrap_or((0.0, 0.0))
    }

    /// Serializes learned offsets (CRKT v1: char u32, dx f32, dy f32 each).
    pub fn export_offsets(&self) -> Vec<u8> {
        let mut entries: Vec<(char, (f32, f32))> =
            self.offsets.iter().map(|(&c, &o)| (c, o)).collect();
        entries.sort_by_key(|&(c, _)| c);
        entries.truncate(MAX_OFFSET_KEYS as usize);
        let mut out = Vec::new();
        out.extend_from_slice(&OFFSETS_MAGIC);
        out.push(OFFSETS_VERSION);
        out.extend_from_slice(&(entries.len() as u32).to_le_bytes());
        for (c, (dx, dy)) in entries {
            out.extend_from_slice(&(c as u32).to_le_bytes());
            out.extend_from_slice(&dx.to_le_bytes());
            out.extend_from_slice(&dy.to_le_bytes());
        }
        out
    }

    /// Restores offsets from a CRKT blob; a corrupt blob restores nothing.
    /// Non-finite values are rejected entry-wise (hostile-input posture).
    pub fn import_offsets(&mut self, data: &[u8]) -> Result<usize, ()> {
        if data.len() < 9 || data[0..4] != OFFSETS_MAGIC || data[4] != OFFSETS_VERSION {
            return Err(());
        }
        let count = u32::from_le_bytes([data[5], data[6], data[7], data[8]]);
        if count > MAX_OFFSET_KEYS {
            return Err(());
        }
        let needed = 9 + count as usize * 12;
        if data.len() < needed {
            return Err(());
        }
        let mut restored = 0;
        for chunk in data[9..needed].chunks_exact(12) {
            let code = u32::from_le_bytes([chunk[0], chunk[1], chunk[2], chunk[3]]);
            let dx = f32::from_le_bytes([chunk[4], chunk[5], chunk[6], chunk[7]]);
            let dy = f32::from_le_bytes([chunk[8], chunk[9], chunk[10], chunk[11]]);
            let (Some(ch), true) = (char::from_u32(code), dx.is_finite() && dy.is_finite())
            else {
                continue;
            };
            self.offsets.insert(ch.to_ascii_lowercase(), (dx, dy));
            restored += 1;
        }
        Ok(restored)
    }

    pub fn generation(&self) -> u32 {
        self.generation
    }

    /// First key containing the point, in upload order — `None` mirrors
    /// Kotlin's null (no key hit).

    /// Evaluates a touch position with dynamic LM character priors, expanding the effective capture
    /// area of high-probability keys and shrinking unlikely keys.
    pub fn probabilistic_hit(
        &self,
        x: f32,
        y: f32,
        priors: &[(char, f32)],
    ) -> Option<usize> {
        self.rank_hits(x, y, priors, 1).first().map(|h| h.index)
    }

    /// Returns ranked candidate key hits based on spatial Gaussian proximity, learned touch offsets,
    /// and dynamic LM priors.
    pub fn rank_hits(
        &self,
        x: f32,
        y: f32,
        priors: &[(char, f32)],
        max_results: usize,
    ) -> Vec<ProbabilisticHit> {
        if self.keys.is_empty() || !x.is_finite() || !y.is_finite() {
            return Vec::new();
        }

        let geometric_hit = self.hit(x, y);
        let mut candidates = Vec::with_capacity(self.keys.len().min(8));

        for (idx, rect) in self.keys.iter().enumerate() {
            let ch = self.chars.get(idx).copied().unwrap_or(' ');
            let (off_x, off_y) = if ch != ' ' {
                self.offset_for(ch)
            } else {
                (0.0, 0.0)
            };

            let cx = (rect.left + rect.right) * 0.5 + off_x;
            let cy = (rect.top + rect.bottom) * 0.5 + off_y;
            let width = (rect.right - rect.left).max(1.0);
            let height = (rect.bottom - rect.top).max(1.0);
            let key_radius = (width + height) * 0.25;

            let dx = x - cx;
            let dy = y - cy;
            let dist_sq = dx * dx + dy * dy;

            // Only consider keys within 1.85x key radius of the touch
            let max_reach_sq = (key_radius * 2.0).powi(2);
            if dist_sq <= max_reach_sq {
                let sigma = key_radius * 0.65;
                let spatial_score = (-dist_sq / (2.0 * sigma * sigma)).exp();

                // Look up LM prior (default 0.02 if not in top predictions)
                let prior = priors
                    .iter()
                    .find(|(c, _)| c.to_ascii_lowercase() == ch.to_ascii_lowercase())
                    .map(|(_, p)| *p)
                    .unwrap_or(0.02);

                // Probabilistic hit score: spatial Gaussian * (1.0 + gamma * LM prior)
                // Geometric containment provides an anchor boost
                let containment_bonus = if rect.contains(x, y) { 1.15 } else { 1.0 };
                let final_score = spatial_score * (1.0 + 2.2 * prior) * containment_bonus;

                candidates.push(ProbabilisticHit {
                    index: idx,
                    character: ch,
                    probability: prior,
                    score: final_score,
                });
            }
        }

        // Fallback to geometric hit if spatial Gaussian found no candidate within reach
        if candidates.is_empty() {
            if let Some(idx) = geometric_hit {
                let ch = self.chars.get(idx).copied().unwrap_or(' ');
                return vec![ProbabilisticHit {
                    index: idx,
                    character: ch,
                    probability: 1.0,
                    score: 1.0,
                }];
            }
            return Vec::new();
        }

        // Sort descending by final score
        candidates.sort_by(|a, b| b.score.partial_cmp(&a.score).unwrap_or(std::cmp::Ordering::Equal));
        candidates.truncate(max_results);
        candidates
    }

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
        t.set_keys(&flat, &[]).unwrap();
        t
    }

    #[test]
    fn offsets_learn_a_bias_clamp_and_round_trip() {
        let mut t = HitTester::new();
        t.set_keys(&[0.0, 0.0, 100.0, 140.0], &['e']).unwrap();
        // Consistent low-right taps: EMA converges toward (+20, +30)...
        for _ in 0..600 {
            t.record_hit(0, 70.0, 100.0); // centre is (50, 70)
        }
        let (dx, dy) = t.offset_for('e');
        assert!(dx > 15.0 && dx <= 40.0, "dx converges within clamp: {dx}");
        assert!(dy > 22.0 && dy <= 56.0, "dy converges within clamp: {dy}");
        // ...and an extreme tap barrage cannot exceed the clamp.
        for _ in 0..600 {
            t.record_hit(0, 99.9, 139.9);
        }
        let (dx, dy) = t.offset_for('e');
        assert!(dx <= 40.0 + 1e-3 && dy <= 56.0 + 1e-3, "clamped: {dx},{dy}");

        let blob = t.export_offsets();
        let mut fresh = HitTester::new();
        assert!(fresh.import_offsets(&blob).unwrap() >= 1);
        assert_eq!(fresh.offset_for('e'), t.offset_for('e'));
        assert!(fresh.import_offsets(b"junk").is_err());
    }

    #[test]
    fn unlabeled_layouts_learn_nothing() {
        let mut t = tester(&[[0.0, 0.0, 10.0, 10.0]]);
        t.record_hit(0, 9.0, 9.0);
        assert_eq!(t.offset_for('a'), (0.0, 0.0));
        assert!(t.export_offsets().len() == 9, "no entries serialized");
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
        assert_eq!(t.set_keys(&[1.0, 2.0, 3.0], &[]), None);
        let g1 = t.set_keys(&[0.0, 0.0, 10.0, 10.0], &[]).unwrap();
        // Refused upload must not have consumed a generation or the layout.
        assert_eq!(t.set_keys(&[1.0], &[]), None);
        assert_eq!(t.generation(), g1);
        assert_eq!(t.hit(5.0, 5.0), Some(0));
    }

    #[test]
    fn generation_increments_per_upload() {
        let mut t = HitTester::new();
        let g1 = t.set_keys(&[0.0, 0.0, 10.0, 10.0], &[]).unwrap();
        let g2 = t.set_keys(&[0.0, 0.0, 20.0, 20.0], &[]).unwrap();
        assert_ne!(g1, g2);
        assert_eq!(t.generation(), g2);
    }
}
