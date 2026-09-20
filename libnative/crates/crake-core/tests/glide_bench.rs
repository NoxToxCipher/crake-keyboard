//! What gliding actually scores, against the dictionary that ships.
//!
//! glide_eval decodes against a hand-written word list of a few dozen
//! entries, so it passes while real gliding fails: on a phone the stroke
//! competes with every one of the ~50,000 words in data.crkd. This bench
//! puts the same decoder in front of the real trie, on the key geometry of
//! a real device (the rects captured in tests/data/glide_traces.txt), over
//! the commonest words a person actually glides, and reports top-1 and
//! top-3.
//!
//! It is a BENCH, not a gate: the assertion at the end only catches a
//! collapse, because the number is meant to be read and improved, not
//! defended. Run it with --nocapture to see the breakdown by word length
//! and the worst failures.
//!
//! The noise model is deliberately closer to a thumb than glide_eval's:
//! a thumb cuts corners rather than visiting key centres, accelerates
//! along straights and slows into turns, wobbles more when it is moving
//! fast, and both takes off and lands with a hook.

use crake_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};

/// The real key rects a device reported, from the captured trace corpus:
/// three rows at y = 80/240/400, keys about 95x131, rows 2 and 3 indented.
fn device_layout() -> Vec<KeyInfo> {
    const ROWS: [&str; 3] = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    const X0: [f32; 3] = [59.0, 107.0, 223.0];
    const STEP: [f32; 3] = [106.6, 108.0, 105.7];
    const Y: [f32; 3] = [80.0, 240.0, 400.0];
    let mut keys = Vec::new();
    for (r, row) in ROWS.iter().enumerate() {
        for (i, ch) in row.chars().enumerate() {
            keys.push(KeyInfo {
                code: ch as i32,
                character: ch,
                center: Point2D::new(X0[r] + i as f32 * STEP[r], Y[r]),
                width: 95.0,
                height: 131.0,
            });
        }
    }
    keys
}

/// Deterministic noise so the bench never flakes.
struct Lcg(u64);
impl Lcg {
    fn next(&mut self) -> f32 {
        self.0 = self.0.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        ((self.0 >> 33) as f32 / u32::MAX as f32) * 2.0 - 1.0
    }
}

/// A thumb's path through a word: corners rounded, speed varying, wobble
/// proportional to speed, hooks at both ends. Returns the points and a
/// timestamp per point in milliseconds.
fn thumb_trace(engine: &GlideEngine, word: &str, rng: &mut Lcg) -> Option<(Vec<Point2D>, Vec<u32>)> {
    let ideal = engine.build_ideal_keypath(word)?;
    if ideal.len() < 2 {
        return None;
    }
    // Round the corners: a thumb passes INSIDE each turn rather than
    // reaching the key centre, by up to a third of the way to the
    // neighbouring keys.
    let mut waypoints: Vec<Point2D> = Vec::with_capacity(ideal.len());
    waypoints.push(ideal[0]);
    for i in 1..ideal.len() - 1 {
        let (prev, here, next) = (ideal[i - 1], ideal[i], ideal[i + 1]);
        let cut = 0.28;
        waypoints.push(Point2D::new(
            here.x + ((prev.x + next.x) * 0.5 - here.x) * cut,
            here.y + ((prev.y + next.y) * 0.5 - here.y) * cut,
        ));
    }
    waypoints.push(*ideal.last()?);

    let mut pts = Vec::new();
    let mut ts = Vec::new();
    let mut t_ms = 0.0f32;
    // take-off hook: the thumb starts a little short of the first key and
    // pulls onto it
    let first = waypoints[0];
    let toward = waypoints[1];
    let hook = Point2D::new(
        first.x - (toward.x - first.x) * 0.10 + rng.next() * 8.0,
        first.y - (toward.y - first.y) * 0.10 + rng.next() * 8.0,
    );
    pts.push(hook);
    ts.push(0);

    for pair in waypoints.windows(2) {
        let (a, b) = (pair[0], pair[1]);
        let len = a.distance(&b).max(1.0);
        // roughly 1400 px/s along a straight, slowing to 700 into a turn
        let steps = ((len / 26.0).ceil() as usize).clamp(3, 24);
        for s in 1..=steps {
            let u = s as f32 / steps as f32;
            // ease in and out of every waypoint: fast in the middle
            let speed = 0.55 + 0.45 * (std::f32::consts::PI * u).sin();
            let wobble = 7.0 + 9.0 * speed;
            pts.push(Point2D::new(
                a.x + (b.x - a.x) * u + rng.next() * wobble,
                a.y + (b.y - a.y) * u + rng.next() * wobble,
            ));
            t_ms += (len / steps as f32) / (0.7 + 0.7 * speed);
            ts.push(t_ms as u32);
        }
    }
    // landing hook: the thumb overshoots the last key slightly before lift
    let last = *waypoints.last()?;
    let from = waypoints[waypoints.len() - 2];
    pts.push(Point2D::new(
        last.x + (last.x - from.x) * 0.08 + rng.next() * 10.0,
        last.y + (last.y - from.y) * 0.08 + rng.next() * 10.0,
    ));
    t_ms += 18.0;
    ts.push(t_ms as u32);
    Some((pts, ts))
}

fn shipped_engine() -> (NlpEngine, Vec<(String, u32)>) {
    let mut e = NlpEngine::new();
    let mut words = Vec::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    crake_core::parse_dict_blob(&dict, |w, f| {
        e.trie.insert(w, f);
        e.corpus_insert(w, f);
        words.push((w.to_string(), f));
    })
    .expect("dict parse");
    (e, words)
}

/// The words worth being right about: common, all letters, 3-9 long.
fn bench_words(words: &[(String, u32)], limit: usize) -> Vec<String> {
    let mut v: Vec<(String, u32)> = words
        .iter()
        .filter(|(w, f)| {
            *f >= 200
                && w.chars().count() >= 3
                && w.chars().count() <= 9
                && w.chars().all(|c| c.is_ascii_lowercase())
        })
        .cloned()
        .collect();
    v.sort_by(|a, b| b.1.cmp(&a.1).then(a.0.cmp(&b.0)));
    v.truncate(limit);
    v.into_iter().map(|(w, _)| w).collect()
}

#[test]
fn glide_accuracy_against_the_shipped_dictionary() {
    let (nlp, all_words) = shipped_engine();
    let mut glide = GlideEngine::new();
    glide.set_layout(device_layout());
    let words = bench_words(&all_words, 600);
    assert!(words.len() > 400, "bench needs a real word list, got {}", words.len());

    let mut rng = Lcg(0x91DE_BEEF);
    let (mut top1, mut top3, mut nothing) = (0usize, 0usize, 0usize);
    let mut by_len: std::collections::BTreeMap<usize, (usize, usize)> = Default::default();
    let mut failures: Vec<String> = Vec::new();

    for word in &words {
        let Some((pts, ts)) = thumb_trace(&glide, word, &mut rng) else { continue };
        let results = glide.match_gesture_timed(&pts, &ts, &nlp.trie, 8, None);
        let rank = results.iter().position(|m| m.word == *word);
        let entry = by_len.entry(word.chars().count()).or_insert((0, 0));
        entry.1 += 1;
        match rank {
            Some(0) => {
                top1 += 1;
                top3 += 1;
                entry.0 += 1;
            }
            Some(1..=2) => top3 += 1,
            _ => {
                if results.is_empty() {
                    nothing += 1;
                }
                if failures.len() < 40 {
                    failures.push(format!(
                        "{word} -> {:?}",
                        results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
                    ));
                }
            }
        }
    }

    let n = words.len();
    eprintln!("\nGLIDE BENCH  ({n} words, shipped dictionary, device geometry)");
    eprintln!("  top-1 {:>5.1}%   top-3 {:>5.1}%   no candidates: {nothing}",
        100.0 * top1 as f32 / n as f32,
        100.0 * top3 as f32 / n as f32);
    eprintln!("  by length:");
    for (len, (hit, total)) in &by_len {
        eprintln!("    {len} letters: {:>5.1}%  ({hit}/{total})", 100.0 * *hit as f32 / *total as f32);
    }
    eprintln!("  first failures:");
    for f in failures.iter().take(25) {
        eprintln!("    {f}");
    }

    // A collapse guard only. The number is here to be improved.
    assert!(top1 * 4 >= n, "glide top-1 collapsed below 25%: {top1}/{n}");
}
