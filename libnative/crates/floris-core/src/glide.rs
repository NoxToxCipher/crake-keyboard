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

/// Simplifies a touch trajectory using the Ramer-Douglas-Peucker (RDP) algorithm.
/// Reduces hundreds of noisy touch samples down to essential inflection points.

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

        // Angle between incoming and outgoing vectors
        let dx1 = p_curr.x - p_prev.x;
        let dy1 = p_curr.y - p_prev.y;
        let dx2 = p_next.x - p_curr.x;
        let dy2 = p_next.y - p_curr.y;

        let mag1 = (dx1 * dx1 + dy1 * dy1).sqrt();
        let mag2 = (dx2 * dx2 + dy2 * dy2).sqrt();

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
pub fn detect_double_letter_loops(points: &[Point2D], key_radius: f32) -> Vec<Point2D> {
    if points.len() < 5 {
        return Vec::new();
    }

    let mut loop_centers = Vec::new();
    let max_radius_sq = (key_radius * 1.15).powi(2);

    // Sliding window checking for closed trajectory loops:
    // A loop occurs when points wrap around (total angle change >= 240 deg)
    // while remaining confined within a single keycap boundary.
    let window_size = 8;
    for i in 0..points.len().saturating_sub(window_size) {
        let window = &points[i..i + window_size];
        let p_start = window[0];
        let p_mid = window[window_size / 2];

        // Must stay confined within single key radius
        let mut confined = true;
        for p in window {
            if p.distance_squared(&p_mid) > max_radius_sq {
                confined = false;
                break;
            }
        }

        if !confined {
            continue;
        }

        // Sum angular turns in the window
        let mut total_angle_deg = 0.0f32;
        for j in 1..window.len() - 1 {
            let dx1 = window[j].x - window[j - 1].x;
            let dy1 = window[j].y - window[j - 1].y;
            let dx2 = window[j + 1].x - window[j].x;
            let dy2 = window[j + 1].y - window[j].y;

            let mag1 = (dx1 * dx1 + dy1 * dy1).sqrt();
            let mag2 = (dx2 * dx2 + dy2 * dy2).sqrt();

            if mag1 > 1e-3 && mag2 > 1e-3 {
                let dot = (dx1 * dx2 + dy1 * dy2) / (mag1 * mag2);
                let cross = dx1 * dy2 - dy1 * dx2;
                let angle = dot.clamp(-1.0, 1.0).acos();
                // Signed angle turn
                let signed_deg = angle * (180.0 / std::f32::consts::PI) * cross.signum();
                total_angle_deg += signed_deg;
            }
        }

        // Loop threshold: >= 220 degrees of consistent rotation or end-to-start close loop
        if total_angle_deg.abs() >= 220.0 || window.last().unwrap().distance_squared(&p_start) < (key_radius * 0.4).powi(2) {
            if loop_centers.last().map(|c: &Point2D| c.distance_squared(&p_mid)).unwrap_or(1e6) > max_radius_sq {
                loop_centers.push(p_mid);
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

    // Two-row DP buffer for optimal memory locality and zero heap churn
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

/// Native Glide Typing Engine managing key geometry and trajectory matching.
#[derive(Debug, Default)]
pub struct GlideEngine {
    key_centers: HashMap<char, Point2D>,
    key_bounds: Vec<KeyInfo>,
    average_key_radius: f32,
}

impl GlideEngine {
    pub fn new() -> Self {
        Self {
            key_centers: HashMap::new(),
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

    /// Builds the ideal ideal keypath trajectory for a given candidate word.
    pub fn build_ideal_keypath(&self, word: &str) -> Option<Vec<Point2D>> {
        let mut path = Vec::with_capacity(word.len());
        for ch in word.chars() {
            let ch_lower = ch.to_ascii_lowercase();
            if ch_lower == '\'' || ch_lower == '’' || ch_lower == '‘' || ch_lower == '-' {
                continue;
            }
            match self.key_centers.get(&ch_lower) {
                Some(&pt) => {
                    // Deduplicate consecutive identical keys (e.g. 'll' or 'ee')
                    if path.last() != Some(&pt) {
                        path.push(pt);
                    }
                }
                None => return None, // Unknown character in layout
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
        if raw_path.len() < 2 || self.key_centers.is_empty() {
            return Vec::new();
        }

        // 1. Simplify touch curve using RDP (epsilon proportional to key radius)
        let rdp_epsilon = (self.average_key_radius * 0.35).max(10.0);
        let simplified_gesture = simplify_rdp(raw_path, rdp_epsilon);
        if simplified_gesture.len() < 2 {
            return Vec::new();
        }

        let start_pt = simplified_gesture[0];
        let end_pt = simplified_gesture[simplified_gesture.len() - 1];

        // 2. Spatial bounding box filter for start & end keys (within 1.75x key radius)
        let search_radius_sq = (self.average_key_radius * 1.75).powi(2);
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
        let inflections = extract_inflections(raw_path, self.average_key_radius);
        let double_loops = detect_double_letter_loops(raw_path, self.average_key_radius);
        let radius_match_sq = (self.average_key_radius * 1.35).powi(2);

        // 3. Collect candidate words from Radix Trie matching start characters
        let mut matches = Vec::new();

        for &start_ch in &start_chars {
            let prefix = start_ch.to_string();
            // Top 300 VIABLE words (end letter reachable from where the
            // finger lifted), filtered during the trie walk. The old shape
            // pulled 1500 frequency-sorted clones and filtered after —
            // 1.27ms per start letter on the mid-gesture preview path, and
            // any viable word below the frequency cut was unglidable.
            let viable = trie.prefix_search_filtered(&prefix, 300, |word| {
                let clean_len = word.chars().filter(|c| *c != '\'' && *c != '’' && *c != '‘' && *c != '-').count();
                clean_len >= 2
                    && word
                        .chars()
                        .filter(|c| *c != '\'' && *c != '’' && *c != '‘' && *c != '-')
                        .last()
                        .is_some_and(|c| end_chars.contains(&c.to_ascii_lowercase()))
            });
            let viable = viable.into_iter();

            for (word, freq) in viable {
                // Build ideal keypath for the word
                if let Some(ideal_path) = self.build_ideal_keypath(&word).map(Self::soften_corners) {
                    let dtw_dist = compute_dtw(&simplified_gesture, &ideal_path);

                    // Normalize distance by gesture length
                    let normalized_dist = dtw_dist / (simplified_gesture.len() + ideal_path.len()) as f32;

                    // Anchor accuracy: glides start deliberately (the user is
                    // looking at the first key), so distance from the trace's
                    // endpoints to the word's first/last key centers carries
                    // real signal that plain DTW dilutes across the path.
                    let anchor_penalty = match (ideal_path.first(), ideal_path.last()) {
                        (Some(first), Some(last)) => {
                            (start_pt.distance(first) + end_pt.distance(last))
                                / self.average_key_radius.max(1.0)
                                * 6.0
                        }
                        _ => 0.0,
                    };

                    // Kinematics Inflection Alignment:
                    // Reward candidates whose interior keys align with detected turn corners/dwells.
                    let mut kinematics_bonus = 0.0f32;
                    let radius_match_sq = (self.average_key_radius * 1.35).powi(2);
                    for key_pt in &ideal_path {
                        if inflections.iter().any(|inf| inf.point.distance_squared(key_pt) <= radius_match_sq) {
                            kinematics_bonus += 0.8;
                        }
                    }

                    // Double-letter loop / stutter bonus:
                    // If candidate word has double letters (e.g. "good", "look", "coffee", "sleep")
                    // and a micro-loop was detected over that keycap, apply a strong double-letter reward.
                    let mut double_letter_bonus = 0.0f32;
                    let double_chars = get_double_letter_chars(&word);
                    for d_char in double_chars {
                        if let Some(&center) = self.key_centers.get(&d_char) {
                            if double_loops.iter().any(|lp| lp.distance_squared(&center) <= radius_match_sq) {
                                double_letter_bonus += 4.0;
                            }
                        }
                    }

                    // Combine DTW geometric closeness with word frequency bonus & kinematics
                    let freq_bonus = (freq as f32 / 255.0).clamp(0.1, 1.0) * 15.0;
                    // Sentence-context bonus from the bigram LM (0 without
                    // context or when the pair is unseen).
                    let context_bonus = match context {
                        Some((nlp, prev)) if !prev.is_empty() => {
                            nlp.bigram_pair_score(prev, &word) as f32 * 0.04
                        }
                        _ => 0.0,
                    };
                    let total_score = normalized_dist + anchor_penalty - freq_bonus - context_bonus - kinematics_bonus - double_letter_bonus;

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
            if let Some(pos) = matches.iter().position(|m| m.frequency >= GLIDE_COMMIT_MIN_FREQ) {
                if matches[pos].score - matches[0].score <= JUNK_RESCUE_MARGIN {
                    let rescued = matches.remove(pos);
                    matches.insert(0, rescued);
                }
            }
        }
        matches.truncate(max_results);

        // Contractions are unglideable (no apostrophe key), so their bare
        // non-word forms match instead. Surface the apostrophized form the
        // user actually means: "dont" -> "don't". Real-word bares ("were",
        // "wont") are untouched by contraction_display.
        for m in &mut matches {
            if let Some(display) = crate::nlp::canonicalize_contraction(&m.word) {
                m.word = display.to_string();
            }
        }
        matches.dedup_by(|a, b| a.word == b.word);
        matches
    }
}

#[cfg(test)]
mod tests {
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
