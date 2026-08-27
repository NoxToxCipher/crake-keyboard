//! Native Safe Rust Glide / Gesture Typing Engine.
//! Powered by Dynamic Time Warping (DTW) and Ramer-Douglas-Peucker (RDP) trajectory simplification.

use crate::trie::RadixTrie;
use std::collections::HashMap;

/// 2D coordinate point for touch inputs and key positions.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct Point2D {
    pub x: f32,
    pub y: f32,
}

impl Point2D {
    #[inline]
    pub fn new(x: f32, y: f32) -> Self {
        Self { x, y }
    }

    #[inline]
    pub fn distance_squared(&self, other: &Point2D) -> f32 {
        let dx = self.x - other.x;
        let dy = self.y - other.y;
        dx * dx + dy * dy
    }

    #[inline]
    pub fn distance(&self, other: &Point2D) -> f32 {
        self.distance_squared(other).sqrt()
    }
}


/// Anisotropic Ergonomic Thumb-Arc Metric (Idea 4 / Loops 10-12):
/// Models the biomechanical reach envelope of the human thumb.
/// The major axis is aligned with natural diagonal thumb sweep (~28.5 degrees).
#[inline]
pub fn anisotropic_thumb_distance_sq(touch: &Point2D, target: &Point2D, _key_radius: f32) -> f32 {
    let dx = touch.x - target.x;
    let dy = touch.y - target.y;

    // Pre-computed quadratic form coefficients for rotated ellipse:
    // theta = 28.5 deg, a = 1.25 (major reach), b = 0.85 (minor perpendicular)
    // Runs in 3 multiplications + 2 additions with zero trigonometry or division.
    const A: f32 = 0.8094;
    const B: f32 = -0.6241;
    const C: f32 = 1.2146;

    A * dx * dx + B * dx * dy + C * dy * dy
}

#[inline]
pub fn anisotropic_thumb_distance(touch: &Point2D, target: &Point2D, key_radius: f32) -> f32 {
    anisotropic_thumb_distance_sq(touch, target, key_radius).sqrt()
}

/// Metadata about a single keyboard key's geometric layout.
#[derive(Debug, Clone)]
pub struct KeyInfo {
    pub code: i32,
    pub character: char,
    pub center: Point2D,
    pub width: f32,
    pub height: f32,
}

/// Words below this unigram floor never auto-commit from a glide; a result
/// set with no word at or above it is display-only (the stray-flick guard).
pub const GLIDE_COMMIT_MIN_FREQ: u32 = 150;

/// Folds common Latin diacritics to their ASCII base letter, mirroring the
/// NFD normalization the retired Kotlin classifier applied. Keyboard layouts
/// carry ASCII letter keys only, so without folding an accented interior or
/// final letter makes a word unglideable ("café" would have no key path).
fn fold_to_ascii(ch: char) -> char {
    match ch {
        'à' | 'á' | 'â' | 'ã' | 'ä' | 'å' | 'ā' | 'ă' | 'ą' => 'a',
        'ç' | 'ć' | 'č' => 'c',
        'ď' => 'd',
        'è' | 'é' | 'ê' | 'ë' | 'ē' | 'ĕ' | 'ė' | 'ę' | 'ě' => 'e',
        'ğ' | 'ģ' => 'g',
        'ì' | 'í' | 'î' | 'ï' | 'ī' | 'į' | 'ı' => 'i',
        'ķ' => 'k',
        'ĺ' | 'ļ' | 'ľ' | 'ł' => 'l',
        'ñ' | 'ń' | 'ņ' | 'ň' => 'n',
        'ò' | 'ó' | 'ô' | 'õ' | 'ö' | 'ø' | 'ō' | 'ő' => 'o',
        'ŕ' | 'ř' => 'r',
        'ś' | 'ş' | 'š' | 'ș' => 's',
        'ť' | 'ţ' | 'ț' => 't',
        'ù' | 'ú' | 'û' | 'ü' | 'ū' | 'ŭ' | 'ů' | 'ű' | 'ų' => 'u',
        'ý' | 'ÿ' => 'y',
        'ź' | 'ż' | 'ž' => 'z',
        other => other,
    }
}

/// Lowercases (full Unicode, so 'É' -> 'é') and then folds diacritics.
fn fold_key_char(ch: char) -> char {
    let lower = ch.to_lowercase().next().unwrap_or(ch);
    fold_to_ascii(lower)
}

/// A classified gesture match candidate.
#[derive(Debug, Clone, PartialEq)]
pub struct GlideMatch {
    pub word: String,
    pub score: f32,
    pub dtw_distance: f32,
    /// Unigram frequency of the matched word, so commit policy can tell a
    /// junk-band shape-fit from a real word.
    pub frequency: u32,
}

/// Detected inflection point (corner turn or dwell) along a touch trajectory.
#[derive(Debug, Clone, Copy, PartialEq)]
pub struct InflectionPoint {
    pub point: Point2D,
    /// Importance weight of this inflection (1.0 = normal, >1.5 = sharp corner or dwell)
    pub weight: f32,
    pub is_dwell: bool,
}

/// Extracts kinematic inflection points (corners and speed dips) from raw touch samples.
pub fn extract_inflections(points: &[Point2D], key_radius: f32) -> Vec<InflectionPoint> {
    if points.len() <= 2 {
        return points
            .iter()
            .map(|&p| InflectionPoint {
                point: p,
                weight: 1.0,
                is_dwell: false,
            })
            .collect();
    }

    let mut inflections = Vec::new();
    // Always anchor start point
    inflections.push(InflectionPoint {
        point: points[0],
        weight: 1.5,
        is_dwell: true,
    });

    // Compute average step distance
    let mut total_dist = 0.0;
    for w in points.windows(2) {
        total_dist += w[0].distance(&w[1]);
    }
    let avg_step = total_dist / (points.len() - 1).max(1) as f32;

    for i in 1..points.len() - 1 {
        let p_prev = points[i - 1];
        let p_curr = points[i];
        let p_next = points[i + 1];

        let d_prev = p_prev.distance(&p_curr);
        let d_next = p_curr.distance(&p_next);

        let dx1 = p_curr.x - p_prev.x;
        let dy1 = p_curr.y - p_prev.y;
        let dx2 = p_next.x - p_curr.x;
        let dy2 = p_next.y - p_curr.y;

        let mag1 = d_prev;
        let mag2 = d_next;

        let mut is_corner = false;
        let mut is_dwell = false;
        let mut weight = 1.0;

        if mag1 > 1e-4 && mag2 > 1e-4 {
            let dot = (dx1 * dx2 + dy1 * dy2) / (mag1 * mag2);
            let angle_rad = dot.clamp(-1.0, 1.0).acos(); // 0 = straight line, PI = complete reversal
            let angle_deg = angle_rad * (180.0 / std::f32::consts::PI);

            if angle_deg > 35.0 {
                is_corner = true;
                weight += (angle_deg / 60.0).clamp(0.5, 2.0);
            }
        }

        // Dwell / slow-down detection
        if d_prev < avg_step * 0.45 && d_next < avg_step * 0.45 {
            is_dwell = true;
            weight += 1.0;
        }

        if is_corner || is_dwell {
            // Avoid duplicate inflections too close to each other
            if let Some(last) = inflections.last() {
                if last.point.distance(&p_curr) < key_radius * 0.45 {
                    continue;
                }
            }
            inflections.push(InflectionPoint {
                point: p_curr,
                weight,
                is_dwell,
            });
        }
    }

    // Always anchor end point
    if let Some(&last_pt) = points.last() {
        if inflections.last().map(|p| p.point.distance(&last_pt)).unwrap_or(100.0) > key_radius * 0.4 {
            inflections.push(InflectionPoint {
                point: last_pt,
                weight: 1.5,
                is_dwell: true,
            });
        }
    }

    inflections
}


/// Detects micro-loops (closed mini-circles) and hesitation stutters in a gesture trace.
/// Used to recognize intentional double-letter inputs (e.g. "look", "good", "coffee", "sleep").
#[allow(clippy::needless_range_loop)]
pub fn detect_double_letter_loops(points: &[Point2D], key_radius: f32) -> Vec<Point2D> {
    if points.len() < 5 {
        return Vec::new();
    }

    let mut loop_centers = Vec::with_capacity(4);
    let min_loop_path = key_radius * 0.75;
    let max_radius_sq = (key_radius * 0.75).powi(2);
    let max_closure_dist_sq = (key_radius * 0.55).powi(2);

    for i in 0..points.len().saturating_sub(3) {
        for j in (i + 3)..points.len().min(i + 14) {
            let mut path_len = 0.0;
            for k in i..j {
                path_len += points[k].distance(&points[k + 1]);
            }

            if path_len >= min_loop_path {
                let closure_dist_sq = points[i].distance_squared(&points[j]);
                if closure_dist_sq <= max_closure_dist_sq {
                    // Compute centroid of the loop
                    let mut cx = 0.0;
                    let mut cy = 0.0;
                    for k in i..=j {
                        cx += points[k].x;
                        cy += points[k].y;
                    }
                    let count = (j - i + 1) as f32;
                    let center = Point2D::new(cx / count, cy / count);

                    // Ensure all points in loop stay within keycap radius
                    let mut confined = true;
                    for k in i..=j {
                        if points[k].distance_squared(&center) > max_radius_sq {
                            confined = false;
                            break;
                        }
                    }

                    if confined
                        && loop_centers.last().map(|c: &Point2D| c.distance_squared(&center)).unwrap_or(1e6) > max_radius_sq {
                        loop_centers.push(center);
                    }
                }
            }
        }
    }

    loop_centers
}

/// Checks if a word contains consecutive doubled characters (e.g. "oo", "ee", "ll", "tt").
pub fn get_double_letter_chars(word: &str) -> Vec<char> {
    let mut doubles = Vec::new();
    let chars: Vec<char> = word.chars().map(|c| c.to_ascii_lowercase()).collect();
    for i in 0..chars.len().saturating_sub(1) {
        if chars[i] == chars[i + 1] && chars[i].is_alphabetic() {
            doubles.push(chars[i]);
        }
    }
    doubles
}


/// Trims accidental touchdown and liftoff hardware touch hooks (Idea 5 / Loops 13-15).
/// Touch hardware frequently introduces 1-2 sample acute backward/lateral flick artifacts
/// as the finger lands and rolls off the screen. Trims these in O(1) before RDP simplification.
#[inline]
pub fn trim_takeoff_and_landing_hooks(points: &[Point2D], key_radius: f32) -> &[Point2D] {
    if points.len() < 6 {
        return points;
    }

    let mut start_idx = 0;
    let mut end_idx = points.len();

    let max_hook_dist = key_radius * 0.45;
    let max_hook_dist_sq = max_hook_dist * max_hook_dist;

    // 1. Takeoff Hook Cleanup:
    let p0 = points[0];
    let p1 = points[1];
    let p3 = points[3];
    let d01_sq = p0.distance_squared(&p1);

    if d01_sq <= max_hook_dist_sq {
        let v_init = (p1.x - p0.x, p1.y - p0.y);
        let v_body = (p3.x - p1.x, p3.y - p1.y);
        let mag_init_sq = v_init.0 * v_init.0 + v_init.1 * v_init.1;
        let mag_body_sq = v_body.0 * v_body.0 + v_body.1 * v_body.1;

        if mag_init_sq > 1.0 && mag_body_sq > 1.0 {
            let dot = v_init.0 * v_body.0 + v_init.1 * v_body.1;
            let mag_prod = (mag_init_sq * mag_body_sq).sqrt();
            let cos_theta = dot / mag_prod;
            // Sharp acute reversal (>98 degrees) over tiny distance
            if cos_theta < -0.15 {
                start_idx = 1;
            }
        }
    }

    // 2. Liftoff Hook Cleanup:
    let pn = points[end_idx - 1];
    let pn_1 = points[end_idx - 2];
    let pn_3 = points[end_idx - 4];
    let d_end_sq = pn.distance_squared(&pn_1);

    if d_end_sq <= max_hook_dist_sq {
        let v_tail = (pn.x - pn_1.x, pn.y - pn_1.y);
        let v_prev = (pn_1.x - pn_3.x, pn_1.y - pn_3.y);
        let mag_tail_sq = v_tail.0 * v_tail.0 + v_tail.1 * v_tail.1;
        let mag_prev_sq = v_prev.0 * v_prev.0 + v_prev.1 * v_prev.1;

        if mag_tail_sq > 1.0 && mag_prev_sq > 1.0 {
            let dot = v_tail.0 * v_prev.0 + v_tail.1 * v_prev.1;
            let mag_prod = (mag_tail_sq * mag_prev_sq).sqrt();
            let cos_theta = dot / mag_prod;
            // Sharp acute flick upon finger release
            if cos_theta < -0.15 {
                end_idx -= 1;
            }
        }
    }

    if end_idx > start_idx + 1 {
        &points[start_idx..end_idx]
    } else {
        points
    }
}

#[allow(clippy::needless_range_loop)]
pub fn simplify_rdp(points: &[Point2D], epsilon: f32) -> Vec<Point2D> {
    if points.len() <= 2 {
        return points.to_vec();
    }

    let epsilon_sq = epsilon * epsilon;
    let mut dmax_sq = 0.0f32;
    let mut index = 0;
    let start = points[0];
    let end = points[points.len() - 1];

    let line_len_sq = start.distance_squared(&end);

    for i in 1..points.len() - 1 {
        let pt = points[i];
        let dist_sq = if line_len_sq == 0.0 {
            pt.distance_squared(&start)
        } else {
            // Perpendicular distance squared from point to line segment
            let t = (((pt.x - start.x) * (end.x - start.x) + (pt.y - start.y) * (end.y - start.y)) / line_len_sq)
                .clamp(0.0, 1.0);
            let proj = Point2D::new(start.x + t * (end.x - start.x), start.y + t * (end.y - start.y));
            pt.distance_squared(&proj)
        };

        if dist_sq > dmax_sq {
            index = i;
            dmax_sq = dist_sq;
        }
    }

    if dmax_sq > epsilon_sq {
        let left = simplify_rdp(&points[..=index], epsilon);
        let right = simplify_rdp(&points[index..], epsilon);

        let mut result = left;
        result.pop(); // remove duplicate middle point
        result.extend(right);
        result
    } else {
        vec![start, end]
    }
}

/// Computes Dynamic Time Warping (DTW) distance between two trajectories.
/// Time complexity: O(N * M) with dynamic programming.
pub fn compute_dtw(path_a: &[Point2D], path_b: &[Point2D]) -> f32 {
    let n = path_a.len();
    let m = path_b.len();
    if n == 0 || m == 0 {
        return f32::INFINITY;
    }

    if m <= 31 {
        let mut prev_row = [f32::INFINITY; 32];
        let mut curr_row = [f32::INFINITY; 32];
        prev_row[0] = 0.0;

        for i in 1..=n {
            curr_row[0] = f32::INFINITY;
            let pt_a = path_a[i - 1];

            for j in 1..=m {
                let pt_b = path_b[j - 1];
                let cost = pt_a.distance(&pt_b);

                let min_prev = prev_row[j].min(curr_row[j - 1]).min(prev_row[j - 1]);
                curr_row[j] = cost + min_prev;
            }

            prev_row[..=m].copy_from_slice(&curr_row[..=m]);
            curr_row[..=m].fill(f32::INFINITY);
        }

        prev_row[m]
    } else {
        // Fallback for unusually long traces
        let mut prev_row = vec![f32::INFINITY; m + 1];
        let mut curr_row = vec![f32::INFINITY; m + 1];

        prev_row[0] = 0.0;

        for i in 1..=n {
            curr_row[0] = f32::INFINITY;
            let pt_a = path_a[i - 1];

            for j in 1..=m {
                let pt_b = path_b[j - 1];
                let cost = pt_a.distance(&pt_b);

                let min_prev = prev_row[j].min(curr_row[j - 1]).min(prev_row[j - 1]);
                curr_row[j] = cost + min_prev;
            }

            std::mem::swap(&mut prev_row, &mut curr_row);
            curr_row.fill(f32::INFINITY);
        }

        prev_row[m]
    }
}

/// Native Glide Typing Engine managing key geometry and trajectory matching.
#[derive(Debug, Default, Clone)]
pub struct GlideEngine {
    key_centers: HashMap<char, Point2D>,
    adaptive_centroids: HashMap<char, Point2D>,
    key_bounds: Vec<KeyInfo>,
    average_key_radius: f32,
}

impl GlideEngine {
    pub fn new() -> Self {
        Self {
            key_centers: HashMap::new(),
            adaptive_centroids: HashMap::new(),
            key_bounds: Vec::new(),
            average_key_radius: 50.0,
        }
    }

    /// Sets or updates the active keyboard key layout geometry.
    pub fn set_layout(&mut self, keys: Vec<KeyInfo>) {
        self.key_centers.clear();
        let mut total_radius = 0.0;

        for key in &keys {
            let char_lower = key.character.to_ascii_lowercase();
            self.key_centers.insert(char_lower, key.center);
            total_radius += (key.width + key.height) * 0.25;
        }

        if !keys.is_empty() {
            self.average_key_radius = total_radius / keys.len() as f32;
        }
        self.key_bounds = keys;
    }

    /// Adaptively updates the learned centroid of a key based on user touch observations (Idea 6 / Loops 16-18).
    /// Uses exponential moving average bounded within 0.35x key radius of nominal key center.
    #[inline]
    pub fn adapt_key_centroid(&mut self, ch: char, observed: Point2D) {
        let ch_lower = ch.to_ascii_lowercase();
        if let Some(&nominal) = self.key_centers.get(&ch_lower) {
            let current = self.adaptive_centroids.get(&ch_lower).copied().unwrap_or(nominal);
            let alpha = 0.05f32;
            let mut updated = Point2D::new(
                current.x * (1.0 - alpha) + observed.x * alpha,
                current.y * (1.0 - alpha) + observed.y * alpha,
            );

            // Bounded clamp: ensure adaptive center stays within 0.35x key radius of nominal
            let max_offset = (self.average_key_radius * 0.35).max(5.0);
            let dx = updated.x - nominal.x;
            let dy = updated.y - nominal.y;
            let dist_sq = dx * dx + dy * dy;
            if dist_sq > max_offset * max_offset {
                let dist = dist_sq.sqrt();
                updated.x = nominal.x + (dx / dist) * max_offset;
                updated.y = nominal.y + (dy / dist) * max_offset;
            }

            self.adaptive_centroids.insert(ch_lower, updated);
        }
    }

    /// Returns the effective key center (learned adaptive center if available, otherwise nominal).
    #[inline]
    pub fn get_adaptive_key_center(&self, ch: char) -> Option<Point2D> {
        let ch_lower = ch.to_ascii_lowercase();
        self.adaptive_centroids.get(&ch_lower).copied().or_else(|| self.key_centers.get(&ch_lower).copied())
    }

    /// Clears learned adaptive centroids and restores nominal key geometry.
    #[inline]
    pub fn reset_adaptive_centroids(&mut self) {
        self.adaptive_centroids.clear();
    }

pub fn key_center(&self, ch: char) -> Option<Point2D> {
        self.key_centers.get(&ch.to_ascii_lowercase()).copied()
    }

    /// Builds the ideal ideal keypath trajectory for a given candidate word.
    pub fn build_ideal_keypath(&self, word: &str) -> Option<Vec<Point2D>> {
        let mut path = Vec::with_capacity(word.len());
        for ch in word.chars() {
            let ch_lower = fold_key_char(ch);
            if ch_lower == '\'' || ch_lower == '’' || ch_lower == '‘' || ch_lower == '-' {
                continue;
            }
            let pt = self.get_adaptive_key_center(ch_lower)?;
            // Deduplicate consecutive identical keys (e.g. 'll' or 'ee')
            if path.last() != Some(&pt) {
                path.push(pt);
            }
        }
        if path.len() >= 2 {
            Some(path)
        } else {
            None
        }
    }

    /// Pulls a template's interior via-points toward the straight line
    /// between their neighbours. Real glides cut corners — thumbs never fully
    /// visit interior keys — so comparing the gesture against an equally
    /// corner-cut template removes a deformation DTW would otherwise charge
    /// the honest gesture for. Endpoints stay exact (they anchor the score).
    fn soften_corners(path: Vec<Point2D>) -> Vec<Point2D> {
        if path.len() < 3 {
            return path;
        }
        let mut softened = path.clone();
        for i in 1..path.len() - 1 {
            let mid_x = (path[i - 1].x + path[i + 1].x) * 0.5;
            let mid_y = (path[i - 1].y + path[i + 1].y) * 0.5;
            softened[i] = Point2D::new(
                path[i].x + (mid_x - path[i].x) * 0.25,
                path[i].y + (mid_y - path[i].y) * 0.25,
            );
        }
        softened
    }

    /// Matches a raw touch gesture against candidate words in the Radix Trie.
    pub fn match_gesture(
        &self,
        raw_path: &[Point2D],
        trie: &RadixTrie,
        max_results: usize,
    ) -> Vec<GlideMatch> {
        self.match_gesture_with_context(raw_path, trie, max_results, None)
    }

    /// Context-aware gesture match: identical geometry scoring, plus a bonus
    /// for words the bigram language model expects after `prev_word`. The
    /// bonus is deliberately modest — strong context (score ~200) is worth
    /// about one key-width of anchor error, so geometry still dominates and
    /// context breaks ties between near-identical traces ("hello"/"jello").
    /// With `context: None` the behaviour is bit-identical to
    /// [`Self::match_gesture`].
    pub fn match_gesture_with_context(
        &self,
        raw_path: &[Point2D],
        trie: &RadixTrie,
        max_results: usize,
        context: Option<(&crate::NlpEngine, &str)>,
    ) -> Vec<GlideMatch> {
        self.match_gesture_timed(raw_path, &[], trie, max_results, context)
    }

    /// Timing-aware matching: `timestamps` (ms since stroke start, one per
    /// raw point) enable the DWELL CONSISTENCY term — a candidate that
    /// leaves heavily-dwelt keys unexplained pays for them. Empty slice =
    /// identical to the untimed path (every existing caller and eval).
    /// First real evidence 2026-08-28: a stroke committed "horatio" dwelt
    /// w:89 o:86 l:63 e:57 ms — horatio explains almost none of that.
    pub fn match_gesture_timed(
        &self,
        raw_path: &[Point2D],
        timestamps: &[u32],
        trie: &RadixTrie,
        max_results: usize,
        context: Option<(&crate::NlpEngine, &str)>,
    ) -> Vec<GlideMatch> {
        if raw_path.len() < 2 || self.key_centers.is_empty() {
            return Vec::new();
        }

        // 0. Dynamic Takeoff & Landing Hook Trimming (Idea 5 / Loops 13-15)
        let cleaned_path = trim_takeoff_and_landing_hooks(raw_path, self.average_key_radius);

        // 1. Simplify touch curve using RDP (epsilon proportional to key radius)
        let rdp_epsilon = (self.average_key_radius * 0.35).max(10.0);
        let simplified_gesture = simplify_rdp(cleaned_path, rdp_epsilon);
        if simplified_gesture.len() < 2 {
            return Vec::new();
        }

        let start_pt = simplified_gesture[0];
        let end_pt = simplified_gesture[simplified_gesture.len() - 1];

        // 2. Spatial bounding box filter for start & end keys (within 1.45x key radius)
        let search_radius_sq = (self.average_key_radius * 1.45).powi(2);
        let mut start_chars = Vec::new();
        let mut end_chars = Vec::new();

        for (&ch, &center) in &self.key_centers {
            if center.distance_squared(&start_pt) <= search_radius_sq {
                start_chars.push(ch);
            }
            if center.distance_squared(&end_pt) <= search_radius_sq {
                end_chars.push(ch);
            }
        }

        if start_chars.is_empty() || end_chars.is_empty() {
            return Vec::new();
        }

        // 2b. Extract kinematics (corners and dwell points) and micro-loops from the raw trace
        let inflections = extract_inflections(cleaned_path, self.average_key_radius);
        let double_loops = detect_double_letter_loops(cleaned_path, self.average_key_radius);
        let radius_match_sq = (self.average_key_radius * 1.35).powi(2);

        // Per-key dwell map from raw timing (ms spent within 0.6 key-widths
        // of each key). Empty when no timing data — the dwell term then
        // contributes nothing anywhere.
        let mut key_dwell: Vec<(char, u32)> = Vec::new();
        if timestamps.len() == raw_path.len() && raw_path.len() >= 2 {
            for k in &self.key_bounds {
                let radius = k.width * 0.6;
                let radius_sq = radius * radius;
                let mut d = 0u32;
                for i in 0..raw_path.len() - 1 {
                    if raw_path[i].distance_squared(&k.center) < radius_sq {
                        d += timestamps[i + 1].saturating_sub(timestamps[i]);
                    }
                }
                // Only meaningful holds count: sweep-through takes ~20-40ms
                // per key at normal glide speed.
                if d >= 50 {
                    key_dwell.push((k.character.to_ascii_lowercase(), d));
                }
            }
        }

        let mut matches = Vec::new();
        let mut char_buf = [0u8; 4];
        for &start_ch in &start_chars {
            let prefix = start_ch.encode_utf8(&mut char_buf);
            // Top 300 VIABLE words (end letter reachable from where the
            // finger lifted), filtered during the trie walk. The old shape
            // pulled 1500 frequency-sorted clones and filtered after —
            // 1.27ms per start letter on the mid-gesture preview path, and
            // any viable word below the frequency cut was unglidable.
            let viable = trie.prefix_search_filtered(prefix, 300, |word| {
                let clean_len = word.chars().filter(|c| *c != '\'' && *c != '’' && *c != '‘' && *c != '-').count();
                clean_len >= 2
                    && word
                        .chars()
                        .rfind(|c| *c != '\'' && *c != '’' && *c != '‘' && *c != '-')
                        .is_some_and(|c| end_chars.contains(&fold_key_char(c)))
            });
            let viable = viable.into_iter();

            for (word, freq) in viable {
                // Build ideal keypath for the word
                if let Some(ideal_path) = self.build_ideal_keypath(&word).map(Self::soften_corners) {
                    let dtw_dist = compute_dtw(&simplified_gesture, &ideal_path);

                    // Normalize distance by gesture length
                    let normalized_dist = dtw_dist / (simplified_gesture.len() + ideal_path.len()) as f32;

                    // Anchor accuracy: glides start deliberately (the user is
                    // looking at the first key), evaluated via anisotropic ergonomic thumb reach.
                    let anchor_penalty = match (ideal_path.first(), ideal_path.last()) {
                        (Some(first), Some(last)) => {
                            (anisotropic_thumb_distance(&start_pt, first, self.average_key_radius)
                                + anisotropic_thumb_distance(&end_pt, last, self.average_key_radius))
                                / self.average_key_radius.max(1.0)
                                * 6.0
                        }
                        _ => 0.0,
                    };

                    // Kinematics Inflection Alignment & Key Coverage:
                    // Reward candidates whose interior keys align with detected turn corners/dwells,
                    // and penalize templates that bypass prominent interior gesture via-points.
                    let mut kinematics_bonus = 0.0f32;
                    for key_pt in &ideal_path {
                        if inflections.iter().any(|inf| inf.point.distance_squared(key_pt) <= radius_match_sq) {
                            kinematics_bonus += 1.5;
                        }
                    }
                    // Reward templates that match the total turn complexity (number of via-points)
                    if ideal_path.len() >= 3 && simplified_gesture.len() >= 3 {
                        let len_diff = (ideal_path.len() as f32 - simplified_gesture.len() as f32).abs();
                        if len_diff <= 1.0 {
                            kinematics_bonus += 1.0;
                        }
                    }

                    let mut interior_alignment_penalty = 0.0f32;
                    if simplified_gesture.len() >= 3 {
                        for pt in &simplified_gesture[1..simplified_gesture.len() - 1] {
                            let min_dist = ideal_path.iter().map(|k| k.distance(pt)).fold(f32::INFINITY, f32::min);
                            if min_dist > self.average_key_radius * 1.8 {
                                interior_alignment_penalty += (min_dist - self.average_key_radius * 1.8) / self.average_key_radius * 3.0;
                            }
                        }
                    }

                    // Double-letter loop / stutter bonus:
                    // If candidate word has double letters (e.g. "good", "look", "coffee", "sleep")
                    // and a micro-loop was detected over that keycap, apply a decisive double-letter reward.
                    let mut double_letter_bonus = 0.0f32;
                    let double_chars = get_double_letter_chars(&word);
                    if !double_loops.is_empty() && !double_chars.is_empty() {
                        for d_char in &double_chars {
                            if let Some(&center) = self.key_centers.get(d_char) {
                                if double_loops.iter().any(|lp| lp.distance_squared(&center) <= radius_match_sq) {
                                    double_letter_bonus += 22.0;
                                }
                            }
                        }
                    }

                    // Combine DTW geometric closeness with word frequency bonus & kinematics
                    let freq_bonus = (freq as f32 / 255.0).clamp(0.1, 1.0) * 15.0;
                    // Multi-Word N-Gram Context & Score Fusion (Idea 3 / Loops 7-9):
                    let context_bonus = match context {
                        Some((nlp, prev)) if !prev.is_empty() => {
                            nlp.multi_word_context_score(prev, &word)
                        }
                        _ => 0.0,
                    };
                    // Dwell consistency: every meaningfully-held key the
                    // candidate does not contain is unexplained evidence
                    // against it, priced per millisecond of hold.
                    let mut dwell_penalty = 0.0f32;
                    for &(ch, d) in &key_dwell {
                        if !word.contains(ch) {
                            dwell_penalty += d as f32 * 0.08;
                        }
                    }
                    let total_score = normalized_dist + anchor_penalty + interior_alignment_penalty + dwell_penalty - freq_bonus - context_bonus - kinematics_bonus - double_letter_bonus;

                    matches.push(GlideMatch {
                        word,
                        score: total_score,
                        dtw_distance: dtw_dist,
                        frequency: freq,
                    });
                }
            }
        }

        // Sort by lowest score (lowest DTW distance + frequency boost)
        matches.sort_by(|a, b| a.score.partial_cmp(&b.score).unwrap_or(std::cmp::Ordering::Equal));

        // Junk never wins a coin flip: glide auto-commits its top-1, and
        // real device traces (2026-08-27) had 60-band junk beating real
        // words by whisker margins ("upi" over "uni" by 2.8, "opi" over
        // "ohio" by 10.8). If the winner is below the solid-word floor and
        // a real word sits within the margin, the real word leads. A rare
        // word that wins by a LANDSLIDE (distinct shape, no real word
        // nearby) still wins — that is a genuine rare-word glide.
        const JUNK_RESCUE_MARGIN: f32 = 12.0;
        if matches.first().is_some_and(|m| m.frequency < GLIDE_COMMIT_MIN_FREQ) {
            // The user's own learned words are NEVER demoted by the junk
            // coin-flip: "crake" (corpus freq 30, but learned on the
            // owner's device) was losing its clean glide to "cake" under
            // this rule (audit 2026-08-27). Frequency cannot tell a rare
            // legitimate word from junk; personal vocabulary can.
            let winner_is_learned = context
                .map(|(nlp, _)| {
                    matches
                        .first()
                        .is_some_and(|m| nlp.is_learned(&m.word))
                })
                .unwrap_or(false);
            if !winner_is_learned {
                if let Some(pos) = matches.iter().position(|m| m.frequency >= GLIDE_COMMIT_MIN_FREQ) {
                    if matches[pos].score - matches[0].score <= JUNK_RESCUE_MARGIN {
                        let rescued = matches.remove(pos);
                        matches.insert(0, rescued);
                    }
                }
            }
        }
        // Contractions are unglideable (no apostrophe key), so their bare
        // non-word forms match instead. Surface the apostrophized form the
        // user actually means: "dont" -> "don't". Real-word bares ("were",
        // "wont") are untouched by canonicalize_contraction. Mapping happens
        // BEFORE the result cap, and duplicates are dropped wherever they
        // sit in the ranking (not just adjacent pairs), so a duplicate can
        // never waste a result slot.
        let mut deduped: Vec<GlideMatch> = Vec::with_capacity(max_results);
        for mut m in matches {
            if deduped.len() == max_results {
                break;
            }
            if let Some(display) = crate::nlp::canonicalize_contraction(&m.word) {
                m.word = display.to_string();
            }
            if deduped.iter().any(|d| d.word == m.word) {
                continue;
            }
            deduped.push(m);
        }
        deduped
    }
}

#[cfg(test)]
mod tests {
    #[test]
    fn test_fold_key_char() {
        use super::fold_key_char;
        assert_eq!(fold_key_char('é'), 'e');
        assert_eq!(fold_key_char('É'), 'e');
        assert_eq!(fold_key_char('ü'), 'u');
        assert_eq!(fold_key_char('ñ'), 'n');
        assert_eq!(fold_key_char('ç'), 'c');
        assert_eq!(fold_key_char('A'), 'a');
        assert_eq!(fold_key_char('z'), 'z');
        // Unmapped characters pass through unchanged.
        assert_eq!(fold_key_char('ß'), 'ß');
        assert_eq!(fold_key_char('\''), '\'');
    }

    #[test]
    fn test_accented_word_builds_keypath_and_matches() {
        let engine = create_mock_qwerty_engine();

        // "café" folds to the c-a-f-e key path.
        let ideal = engine.build_ideal_keypath("café").expect("café must fold to a key path");
        assert_eq!(ideal.len(), 4);
        assert_eq!(ideal[3], engine.key_center('e').unwrap());

        let mut trie = RadixTrie::new();
        trie.insert("café", 200);
        trie.insert("cage", 180);

        // Trace along c-a-f-e.
        let waypoints = ['c', 'a', 'f', 'e'].map(|ch| engine.key_center(ch).unwrap());
        let mut swipe = Vec::new();
        for pair in waypoints.windows(2) {
            for s in 0..8 {
                let t = s as f32 / 8.0;
                swipe.push(Point2D::new(
                    pair[0].x + (pair[1].x - pair[0].x) * t,
                    pair[0].y + (pair[1].y - pair[0].y) * t,
                ));
            }
        }
        swipe.push(waypoints[3]);

        // The folding contract: an accented word is a first-class citizen,
        // scored EXACTLY like its ASCII twin. (Whether this particular
        // synthetic trace ranks it above "cage" is the scoring lane's
        // concern, not folding's.)
        let matches = engine.match_gesture(&swipe, &trie, 3);
        let accented = matches
            .iter()
            .find(|m| m.word == "café")
            .expect("accented word must be matchable");

        let mut ascii_trie = RadixTrie::new();
        ascii_trie.insert("cafe", 200);
        ascii_trie.insert("cage", 180);
        let ascii_matches = engine.match_gesture(&swipe, &ascii_trie, 3);
        let ascii_twin = ascii_matches
            .iter()
            .find(|m| m.word == "cafe")
            .expect("ascii twin must be matchable");

        assert_eq!(accented.score, ascii_twin.score, "folding must be score-transparent");
        assert_eq!(accented.dtw_distance, ascii_twin.dtw_distance);
        assert_eq!(
            matches.iter().position(|m| m.word == "café"),
            ascii_matches.iter().position(|m| m.word == "cafe"),
            "accented word must rank exactly where its ascii twin ranks"
        );
    }

    #[test]
    fn test_dedup_is_not_positional_and_fills_slots() {
        // "dont" maps to "don't" via canonicalize_contraction; duplicates
        // must vanish no matter where they rank, and freed slots go to the
        // next candidate.
        let engine = create_mock_qwerty_engine();
        let mut trie = RadixTrie::new();
        trie.insert("dont", 200);
        trie.insert("done", 190);
        trie.insert("dine", 150);

        let pt_d = engine.key_center('d').unwrap();
        let pt_o = engine.key_center('o').unwrap();
        let pt_n = engine.key_center('n').unwrap();
        let pt_t = engine.key_center('t').unwrap();
        let swipe = vec![pt_d, pt_o, pt_n, pt_t];

        let matches = engine.match_gesture(&swipe, &trie, 3);
        let words: Vec<&str> = matches.iter().map(|m| m.word.as_str()).collect();
        let unique: std::collections::HashSet<&&str> = words.iter().collect();
        assert_eq!(words.len(), unique.len(), "no duplicate words in results: {words:?}");
        assert_eq!(matches[0].word, "don't", "bare contraction displays apostrophized: {words:?}");
    }

    use super::*;

    fn create_mock_qwerty_engine() -> GlideEngine {
        let mut engine = GlideEngine::new();
        let mut keys = Vec::new();

        // Standard QWERTY layout grid approximation
        let row1 = "qwertyuiop";
        for (i, c) in row1.chars().enumerate() {
            keys.push(KeyInfo {
                code: c as i32,
                character: c,
                center: Point2D::new(50.0 + i as f32 * 100.0, 100.0),
                width: 90.0,
                height: 120.0,
            });
        }

        let row2 = "asdfghjkl";
        for (i, c) in row2.chars().enumerate() {
            keys.push(KeyInfo {
                code: c as i32,
                character: c,
                center: Point2D::new(100.0 + i as f32 * 100.0, 250.0),
                width: 90.0,
                height: 120.0,
            });
        }

        let row3 = "zxcvbnm";
        for (i, c) in row3.chars().enumerate() {
            keys.push(KeyInfo {
                code: c as i32,
                character: c,
                center: Point2D::new(200.0 + i as f32 * 100.0, 400.0),
                width: 90.0,
                height: 120.0,
            });
        }

        engine.set_layout(keys);
        engine
    }

    #[test]
    fn test_rdp_path_simplification() {
        let noisy_line = vec![
            Point2D::new(0.0, 0.0),
            Point2D::new(1.0, 0.2),
            Point2D::new(2.0, -0.1),
            Point2D::new(5.0, 10.0), // Inflection peak
            Point2D::new(8.0, 9.8),
            Point2D::new(10.0, 10.0),
        ];

        let simplified = simplify_rdp(&noisy_line, 1.0);
        assert!(simplified.len() < noisy_line.len());
        assert_eq!(simplified[0], Point2D::new(0.0, 0.0));
        assert_eq!(simplified[simplified.len() - 1], Point2D::new(10.0, 10.0));
    }

    #[test]
    fn test_dtw_distance_identical_and_warped() {
        let path1 = vec![
            Point2D::new(0.0, 0.0),
            Point2D::new(50.0, 50.0),
            Point2D::new(100.0, 100.0),
        ];

        // Identical path has zero DTW distance
        assert_eq!(compute_dtw(&path1, &path1), 0.0);

        // Stretched / Warped time path (slow swipe)
        let path_stretched = vec![
            Point2D::new(0.0, 0.0),
            Point2D::new(25.0, 25.0),
            Point2D::new(50.0, 50.0),
            Point2D::new(75.0, 75.0),
            Point2D::new(100.0, 100.0),
        ];

        let dist = compute_dtw(&path1, &path_stretched);
        assert!(dist <= 75.0, "DTW distance should be small for elastic aligned path: {}", dist);
    }

    #[test]
    fn test_glide_word_matching() {
        let engine = create_mock_qwerty_engine();
        let mut trie = RadixTrie::new();
        trie.insert("quick", 200);
        trie.insert("quiet", 150);
        trie.insert("quit", 180);
        trie.insert("hello", 250);
        trie.insert("bitcoin", 220);

        // Generate synthetic swipe path for "quick" (Q -> U -> I -> C -> K)
        let pt_q = engine.key_centers[&'q'];
        let pt_u = engine.key_centers[&'u'];
        let pt_i = engine.key_centers[&'i'];
        let pt_c = engine.key_centers[&'c'];
        let pt_k = engine.key_centers[&'k'];

        let swipe_quick = vec![
            pt_q,
            Point2D::new((pt_q.x + pt_u.x) / 2.0, (pt_q.y + pt_u.y) / 2.0),
            pt_u,
            pt_i,
            pt_c,
            pt_k,
        ];

        let matches = engine.match_gesture(&swipe_quick, &trie, 3);
        assert!(!matches.is_empty());
        assert_eq!(matches[0].word, "quick", "Top gesture match should be 'quick'");
    }
}
