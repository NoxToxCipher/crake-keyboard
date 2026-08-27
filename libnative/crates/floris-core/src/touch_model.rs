//! Gaussian touch model: which key slips are *plausible* on the layout the
//! user is actually typing on.
//!
//! Fat-finger contact is well modelled as an isotropic Gaussian centred near
//! the aimed key, with a spread proportional to key pitch. Under that model
//! the likelihood of registering key `b` while aiming at `a` is
//! `exp(-d² / 2σ²)` for centre distance `d`. With σ = 0.55 · pitch, demanding
//! likelihood ≥ e⁻² gives d ≤ 2σ = 1.1 · pitch; we allow a little extra for
//! row stagger and use d ≤ NEAR_FACTOR · pitch. Direct neighbours
//! (d ≈ 1.0 · pitch, staggered verticals ≈ 1.1) qualify; keys two positions
//! away (d ≥ 2 · pitch) never do.
//!
//! This replaces a hardcoded adjacency table that unioned QWERTY *and*
//! Dvorak neighbourhoods — nearly doubling every key's neighbour set and
//! producing false repairs ("can" + "for" fuzzing into "cancer"). Built from
//! the live layout, the model is exactly as permissive as the user's actual
//! keyboard: a Dvorak typist gets Dvorak neighbours, not the union.
//!
//! The model is fed from the same geometry upload the glide engine receives;
//! until a layout arrives (or in host-side tests) callers fall back to the
//! static table.

use std::collections::HashMap;

/// Slips within this multiple of the layout's key pitch count as plausible
/// (cost 1 half-unit in the weighted fuzzy search); everything further costs
/// a full edit. See the module docs for the Gaussian derivation.
pub const NEAR_FACTOR: f32 = 1.25;

/// A physical capacitive contact patch reported by hardware touch digitizer (Idea 2 / Loops 4-6).
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct ContactPatch {
    pub x: f32,
    pub y: f32,
    pub major: f32,
    pub minor: f32,
    pub orientation: f32, // angle in radians (-PI/2 to PI/2)
}

impl ContactPatch {
    pub fn new(x: f32, y: f32, major: f32, minor: f32, orientation: f32) -> Self {
        Self { x, y, major, minor, orientation }
    }

    /// Calculates the true physical fingertip / bone apex coordinates by correcting for
    /// the capacitive smear and thumb-roll tilt vector.
    #[inline]
    pub fn corrected_apex(&self) -> (f32, f32) {
        if !self.x.is_finite() || !self.y.is_finite() || !self.major.is_finite() || !self.minor.is_finite() || !self.orientation.is_finite() {
            return (self.x, self.y);
        }
        let eccentricity = (self.major - self.minor).max(0.0);
        if eccentricity <= 0.001 {
            return (self.x, self.y);
        }

        // Thumb pad apex scale factor kappa: shifts contact center toward physical bone apex
        const KAPPA: f32 = 0.38;
        let shift_mag = (eccentricity * 0.5) * KAPPA;

        let dx = -shift_mag * self.orientation.sin();
        let dy = -shift_mag * self.orientation.cos();

        (self.x + dx, self.y + dy)
    }
}

#[derive(Debug, Clone, Default)]
pub struct TouchModel {
    centers: HashMap<char, (f32, f32)>,
    near_dist_sq: f32,
}

impl TouchModel {
    /// Builds a model from per-key characters and centre coordinates.
    /// Returns `None` for degenerate input (fewer than two distinct keys, or
    /// a collapsed layout with zero pitch).
    pub fn from_layout(keys: &[(char, f32, f32)]) -> Option<Self> {
        let mut centers: HashMap<char, (f32, f32)> = HashMap::new();
        for &(ch, x, y) in keys {
            centers.insert(ch.to_ascii_lowercase(), (x, y));
        }
        if centers.len() < 2 {
            return None;
        }

        // Pitch = median nearest-neighbour distance. Robust against outlier
        // keys (a distant wide spacebar cannot stretch it) and against a few
        // overlapping entries (which contribute zero and sink to the front).
        let pts: Vec<(f32, f32)> = centers.values().copied().collect();
        let mut nearest: Vec<f32> = pts
            .iter()
            .enumerate()
            .map(|(i, a)| {
                pts.iter()
                    .enumerate()
                    .filter(|&(j, _)| j != i)
                    .map(|(_, b)| {
                        let (dx, dy) = (a.0 - b.0, a.1 - b.1);
                        dx * dx + dy * dy
                    })
                    .fold(f32::INFINITY, f32::min)
            })
            .collect();
        nearest.sort_by(|a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        let median_sq = nearest[nearest.len() / 2];
        if !(median_sq.is_finite() && median_sq > 0.0) {
            return None;
        }
        let pitch = median_sq.sqrt();
        let near = pitch * NEAR_FACTOR;
        Some(Self {
            centers,
            near_dist_sq: near * near,
        })
    }

    /// Whether registering `b` while aiming at `a` is a plausible slip on
    /// this layout. Unknown characters (not on the layout) are never near —
    /// the caller's fallback table does not apply once a model is loaded,
    /// because mixing the two would reintroduce the union-graph problem.
    pub fn is_near(&self, a: char, b: char) -> bool {
        let a = a.to_ascii_lowercase();
        let b = b.to_ascii_lowercase();
        if a == b {
            return true;
        }
        match (self.centers.get(&a), self.centers.get(&b)) {
            (Some(&(ax, ay)), Some(&(bx, by))) => {
                let (dx, dy) = (ax - bx, ay - by);
                dx * dx + dy * dy <= self.near_dist_sq
            }
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn grid(rows: &[&str], offsets: &[f32], key_w: f32, key_h: f32) -> TouchModel {
        let mut keys = Vec::new();
        for (r, row) in rows.iter().enumerate() {
            for (i, ch) in row.chars().enumerate() {
                keys.push((ch, (i as f32 + offsets[r] + 0.5) * key_w, (r as f32 + 0.5) * key_h));
            }
        }
        TouchModel::from_layout(&keys).expect("valid grid")
    }

    fn qwerty() -> TouchModel {
        grid(&["qwertyuiop", "asdfghjkl", "zxcvbnm"], &[0.0, 0.5, 1.5], 100.0, 140.0)
    }

    /// Simplified Dvorak letter rows (punctuation keys omitted).
    fn dvorak() -> TouchModel {
        grid(&["pyfgcrl", "aoeuidhtns", "qjkxbmwvz"], &[3.0, 0.0, 1.5], 100.0, 140.0)
    }

    #[test]
    fn horizontal_neighbours_are_near() {
        let m = qwerty();
        assert!(m.is_near('q', 'w'));
        assert!(m.is_near('f', 'g'));
        assert!(m.is_near('n', 'm'));
    }

    #[test]
    fn two_keys_apart_is_never_near() {
        let m = qwerty();
        assert!(!m.is_near('u', 'o')); // i sits between them
        assert!(!m.is_near('a', 'd'));
        assert!(!m.is_near('q', 'e'));
    }

    #[test]
    fn dvorak_gets_dvorak_neighbours_not_the_union() {
        let q = qwerty();
        let d = dvorak();
        // e/o are home-row neighbours on Dvorak but two keys apart on QWERTY.
        assert!(d.is_near('e', 'o'));
        assert!(!q.is_near('e', 'o'));
        // t/y are QWERTY neighbours but far apart on Dvorak.
        assert!(q.is_near('t', 'y'));
        assert!(!d.is_near('t', 'y'));
    }

    #[test]
    fn same_char_is_near_and_unknown_chars_are_not() {
        let m = qwerty();
        assert!(m.is_near('a', 'a'));
        assert!(!m.is_near('a', 'é'));
        assert!(!m.is_near('1', '2'));
    }

    #[test]
    fn degenerate_layouts_are_rejected() {
        assert!(TouchModel::from_layout(&[]).is_none());
        assert!(TouchModel::from_layout(&[('a', 0.0, 0.0)]).is_none());
        // All keys stacked on one point: zero pitch.
        assert!(TouchModel::from_layout(&[('a', 5.0, 5.0), ('b', 5.0, 5.0)]).is_none());
    }
}
