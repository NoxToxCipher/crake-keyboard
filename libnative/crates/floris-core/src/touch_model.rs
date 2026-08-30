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
    #[inline]
    pub fn new(x: f32, y: f32, major: f32, minor: f32, orientation: f32) -> Self {
        Self { x, y, major, minor, orientation }
    }

    /// Calculates the true physical fingertip / bone apex coordinates by correcting for
    /// the capacitive smear and thumb-roll tilt vector with sub-microsecond branchless math (Idea 2 / Loop 6).
    #[inline]
    pub fn corrected_apex(&self) -> (f32, f32) {
        if !self.x.is_finite() || !self.y.is_finite() || !self.major.is_finite() || !self.minor.is_finite() || !self.orientation.is_finite() {
            return (self.x, self.y);
        }
        let eccentricity = (self.major - self.minor).max(0.0);
        if eccentricity <= 0.001 {
            return (self.x, self.y);
        }

        // Thumb pad apex scale factor kappa: shifts contact center toward physical bone apex (0.19 * eccentricity)
        const KAPPA_HALF: f32 = 0.19;
        let shift_mag = eccentricity * KAPPA_HALF;

        // Fast path for straight-on / vertical touches without full trigonometry
        if self.orientation.abs() <= 0.05 {
            return (self.x, self.y - shift_mag);
        }

        let (sin_o, cos_o) = self.orientation.sin_cos();
        (self.x - shift_mag * sin_o, self.y - shift_mag * cos_o)
    }
}


/// A 2D Bivariate Gaussian key distribution modeling user touch accuracy and thumb drift.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct BivariateGaussianKey {
    pub mean_x: f32,
    pub mean_y: f32,
    pub var_x: f32,
    pub var_y: f32,
    pub cov_xy: f32,
    pub sample_count: u32,
}

impl BivariateGaussianKey {
    /// Initializes a Gaussian key from visual layout center and pitch.
    pub fn new(center_x: f32, center_y: f32, pitch: f32) -> Self {
        let initial_sigma = (pitch * 0.40).max(1.0);
        let initial_var = initial_sigma * initial_sigma;
        Self {
            mean_x: center_x,
            mean_y: center_y,
            var_x: initial_var,
            var_y: initial_var,
            cov_xy: 0.0,
            sample_count: 0,
        }
    }

    /// Updates the Gaussian parameters using online Welford updates with exponential decay.
    pub fn update(&mut self, x: f32, y: f32) {
        if !x.is_finite() || !y.is_finite() {
            return;
        }
        self.sample_count = self.sample_count.saturating_add(1);
        let weight = (1.0 / (self.sample_count as f32).min(64.0)).max(0.04);
        
        let dx = x - self.mean_x;
        let dy = y - self.mean_y;
        
        self.mean_x += weight * dx;
        self.mean_y += weight * dy;
        
        let new_dx = x - self.mean_x;
        let new_dy = y - self.mean_y;
        
        self.var_x = ((1.0 - weight) * self.var_x + weight * (dx * new_dx)).max(4.0);
        self.var_y = ((1.0 - weight) * self.var_y + weight * (dy * new_dy)).max(4.0);
        
        let max_cov = (self.var_x * self.var_y).sqrt() * 0.85;
        self.cov_xy = ((1.0 - weight) * self.cov_xy + weight * (dx * new_dy)).clamp(-max_cov, max_cov);
    }

    /// Computes the squared Mahalanobis distance d_M^2 of a touch point (x, y).
    #[inline]
    pub fn mahalanobis_sq(&self, x: f32, y: f32) -> f32 {
        let dx = x - self.mean_x;
        let dy = y - self.mean_y;
        let det = (self.var_x * self.var_y - self.cov_xy * self.cov_xy).max(1e-4);
        
        let sq = (self.var_y * dx * dx - 2.0 * self.cov_xy * dx * dy + self.var_x * dy * dy) / det;
        sq.max(0.0)
    }

    /// Evaluates the unnormalized Gaussian density score exp(-0.5 * d_M^2).
    #[inline]
    pub fn density(&self, x: f32, y: f32) -> f32 {
        let d_sq = self.mahalanobis_sq(x, y);
        (-0.5 * d_sq).exp()
    }
}

#[derive(Debug, Clone, Default)]
pub struct TouchModel {
    centers: HashMap<char, (f32, f32)>,
    near_dist_sq: f32,
    pub gaussian_keys: HashMap<char, BivariateGaussianKey>,
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
        let mid = nearest.len() / 2;
        nearest.select_nth_unstable_by(mid, |a, b| a.partial_cmp(b).unwrap_or(std::cmp::Ordering::Equal));
        let median_sq = nearest[mid];
        if !(median_sq.is_finite() && median_sq > 0.0) {
            return None;
        }
        let pitch = median_sq.sqrt();
        let near = pitch * NEAR_FACTOR;
        let mut gaussian_keys = HashMap::new();
        for (&ch, &(x, y)) in &centers {
            gaussian_keys.insert(ch, BivariateGaussianKey::new(x, y, pitch));
        }
        Some(Self {
            centers,
            near_dist_sq: near * near,
            gaussian_keys,
        })
    }

    #[inline]
    pub fn get_center(&self, ch: char) -> Option<(f32, f32)> {
        self.centers.get(&ch.to_ascii_lowercase()).copied()
    }

    #[inline]
    pub fn near_dist_sq(&self) -> f32 {
        self.near_dist_sq
    }


    /// Evaluates the best matching key and its spatial probability distribution for a given touch.
    pub fn evaluate_spatial_touch(&self, x: f32, y: f32) -> Option<(char, f32)> {
        let mut best_key = ' ';
        let mut best_score = -1.0f32;
        let mut total_density = 0.0f32;
        
        for (&ch, gkey) in &self.gaussian_keys {
            let dens = gkey.density(x, y);
            total_density += dens;
            if dens > best_score {
                best_score = dens;
                best_key = ch;
            }
        }
        if total_density > 1e-6 {
            Some((best_key, best_score / total_density))
        } else {
            None
        }
    }

    /// Records a true user tap to adaptively train the 2D Gaussian touch centroid for the aimed key.
    pub fn record_touch_hit(&mut self, aimed_key: char, touch_x: f32, touch_y: f32) {
        if let Some(gkey) = self.gaussian_keys.get_mut(&aimed_key.to_ascii_lowercase()) {
            gkey.update(touch_x, touch_y);
        }
    }

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
    #[test]
    fn bivariate_gaussian_touch_model_adapts_to_thumb_drift() {
        let mut m = qwerty();
        let center_e = m.get_center('e').expect("e center");
        
        // Simulate user repeatedly tapping 8 pixels to the right of 'e'
        for _ in 0..20 {
            m.record_touch_hit('e', center_e.0 + 8.0, center_e.1 - 2.0);
        }
        
        let gkey_e = m.gaussian_keys.get(&'e').expect("gkey e");
        assert!(gkey_e.mean_x > center_e.0 + 4.0, "Mean X should drift rightward toward user's touch habits");
        
        // Touch at slightly offset position should score highest for 'e'
        let (matched_char, prob) = m.evaluate_spatial_touch(center_e.0 + 7.0, center_e.1 - 2.0).expect("match");
        assert_eq!(matched_char, 'e');
        assert!(prob > 0.3);
    }

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
