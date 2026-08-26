//! Glide recognition evaluation: synthetic finger traces over a QWERTY
//! geometry, scored for whether the intended word comes back at all.
//!
//! A trace is the word's ideal key-center path, interpolated to touch-event
//! density and perturbed with deterministic jitter plus start/end offsets —
//! the sloppy-but-honest gesture of a real fast glide. The metric is top-1 /
//! top-3 recall over words spanning the frequency range, because the field
//! complaint this encodes ("it struggles to recognise what I'm gliding for",
//! 2026-08-26) is a recall failure, not a ranking nicety.

use floris_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};

const KEY_W: f32 = 100.0;
const KEY_H: f32 = 140.0;

fn qwerty_engine() -> GlideEngine {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    let offsets = [0.0, 0.5, 1.5];
    let mut keys = Vec::new();
    for (r, row) in rows.iter().enumerate() {
        for (i, ch) in row.chars().enumerate() {
            keys.push(KeyInfo {
                code: ch as i32,
                character: ch,
                center: Point2D::new(
                    (i as f32 + offsets[r] + 0.5) * KEY_W,
                    (r as f32 + 0.5) * KEY_H,
                ),
                width: KEY_W,
                height: KEY_H,
            });
        }
    }
    let mut engine = GlideEngine::new();
    engine.set_layout(keys);
    engine
}

/// Deterministic pseudo-random stream so the eval never flakes.
struct Lcg(u64);
impl Lcg {
    fn next_f32(&mut self) -> f32 {
        self.0 = self.0.wrapping_mul(6364136223846793005).wrapping_add(1442695040888963407);
        ((self.0 >> 33) as f32 / u32::MAX as f32) * 2.0 - 1.0
    }
}

/// Ideal path -> interpolated, jittered touch trace.
fn synth_trace(engine: &GlideEngine, word: &str, rng: &mut Lcg) -> Option<Vec<Point2D>> {
    let ideal = engine.build_ideal_keypath(word)?;
    let mut trace = Vec::new();
    for pair in ideal.windows(2) {
        let (a, b) = (pair[0], pair[1]);
        let steps = 6;
        for s in 0..steps {
            let t = s as f32 / steps as f32;
            trace.push(Point2D::new(
                a.x + (b.x - a.x) * t + rng.next_f32() * 18.0,
                a.y + (b.y - a.y) * t + rng.next_f32() * 18.0,
            ));
        }
    }
    let last = *ideal.last()?;
    // Fingers lift a little off the final key center.
    trace.push(Point2D::new(last.x + rng.next_f32() * 30.0, last.y + rng.next_f32() * 30.0));
    Some(trace)
}

fn eval(words: &[(&str, u32)]) -> (usize, usize, Vec<String>) {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for &(w, f) in words {
        nlp.trie.insert(w, f);
    }
    let mut rng = Lcg(0xC0FFEE);
    let (mut top1, mut top3) = (0, 0);
    let mut misses = Vec::new();
    for &(word, _) in words {
        let trace = synth_trace(&glide, word, &mut rng).expect("layout covers a-z");
        let results = glide.match_gesture(&trace, &nlp.trie, 8);
        let rank = results.iter().position(|m| m.word == word);
        match rank {
            Some(0) => {
                top1 += 1;
                top3 += 1;
            }
            Some(1..=2) => top3 += 1,
            _ => misses.push(format!(
                "'{}' -> {:?}",
                word,
                results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
            )),
        }
    }
    (top1, top3, misses)
}

/// Frequency-diverse vocabulary. The mid/low-frequency half is the point:
/// glide must find the words people actually glide, not only the top of the
/// frequency table.
const EVAL_WORDS: &[(&str, u32)] = &[
    ("the", 255),
    ("hello", 240),
    ("world", 230),
    ("keyboard", 200),
    ("something", 210),
    ("question", 190),
    ("understand", 185),
    ("private", 180),
    ("gliding", 140),
    ("privacy", 150),
    ("meshtastic", 40),
    ("cockatoo", 35),
    ("crake", 30),
    ("fretwork", 25),
    ("bowerbird", 20),
    ("quokka", 15),
];

#[test]
fn glide_recall_on_synthetic_traces() {
    let (top1, top3, misses) = eval(EVAL_WORDS);
    let n = EVAL_WORDS.len();
    // Print the scoreboard either way; assert the floor the engine must hold.
    eprintln!("glide eval: top1 {top1}/{n}, top3 {top3}/{n}");
    for m in &misses {
        eprintln!("  miss: {m}");
    }
    assert!(
        top3 * 100 >= n * 90,
        "top-3 recall {top3}/{n} below 90% floor; misses:\n{}",
        misses.join("\n")
    );
}

/// Sloppy variant: heavier jitter, an offset landing on the first key, and
/// corner-cutting (interior via-points pulled 35% toward the straight line
/// between their neighbours — what fast thumbs actually draw). The floor is
/// lower than the clean pass but must hold: this is the gesture quality the
/// field complaint was about.
fn synth_sloppy_trace(engine: &GlideEngine, word: &str, rng: &mut Lcg) -> Option<Vec<Point2D>> {
    let ideal = engine.build_ideal_keypath(word)?;
    // Corner-cut interior points toward their neighbours' midpoint.
    let mut cut = ideal.clone();
    for i in 1..cut.len().saturating_sub(1) {
        let mid_x = (ideal[i - 1].x + ideal[i + 1].x) * 0.5;
        let mid_y = (ideal[i - 1].y + ideal[i + 1].y) * 0.5;
        cut[i] = Point2D::new(
            ideal[i].x + (mid_x - ideal[i].x) * 0.35,
            ideal[i].y + (mid_y - ideal[i].y) * 0.35,
        );
    }
    let mut trace = Vec::new();
    for pair in cut.windows(2) {
        let (a, b) = (pair[0], pair[1]);
        for s in 0..5 {
            let t = s as f32 / 5.0;
            trace.push(Point2D::new(
                a.x + (b.x - a.x) * t + rng.next_f32() * 30.0,
                a.y + (b.y - a.y) * t + rng.next_f32() * 30.0,
            ));
        }
    }
    let first = cut[0];
    let last = *cut.last()?;
    trace.insert(0, Point2D::new(first.x + rng.next_f32() * 40.0, first.y + rng.next_f32() * 40.0));
    trace.push(Point2D::new(last.x + rng.next_f32() * 40.0, last.y + rng.next_f32() * 40.0));
    Some(trace)
}

/// Regression armor for the "israeli for testing" field report (2026-08-26):
/// a clean glide can never surface a word whose first letter is nowhere near
/// where the finger landed — the start-anchor filter and penalty must hold
/// regardless of any frequency skew in the dictionary.
#[test]
fn wrong_start_letter_words_never_win_a_clean_trace() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for (w, f) in [("testing", 227), ("israeli", 255), ("test", 240), ("resting", 200)] {
        nlp.trie.insert(w, f);
    }
    let mut rng = Lcg(0xBEEF);
    for _ in 0..5 {
        let trace = synth_trace(&glide, "testing", &mut rng).unwrap();
        let results = glide.match_gesture(&trace, &nlp.trie, 3);
        assert!(
            !results.iter().any(|m| m.word == "israeli"),
            "'israeli' (start i, far from t) must never match a 'testing' trace: {:?}",
            results.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
        );
        assert_eq!(results.first().map(|m| m.word.as_str()), Some("testing"));
    }
}

#[test]
fn glide_recall_on_sloppy_traces() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for &(w, f) in EVAL_WORDS {
        nlp.trie.insert(w, f);
    }
    let mut rng = Lcg(0xDECAF);
    let (mut top1, mut top3) = (0, 0);
    let mut misses = Vec::new();
    for &(word, _) in EVAL_WORDS {
        let trace = synth_sloppy_trace(&glide, word, &mut rng).expect("layout covers a-z");
        let results = glide.match_gesture(&trace, &nlp.trie, 8);
        match results.iter().position(|m| m.word == word) {
            Some(0) => {
                top1 += 1;
                top3 += 1;
            }
            Some(1..=2) => top3 += 1,
            _ => misses.push(format!(
                "'{}' -> {:?}",
                word,
                results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
            )),
        }
    }
    let n = EVAL_WORDS.len();
    eprintln!("sloppy glide eval: top1 {top1}/{n}, top3 {top3}/{n}");
    for m in &misses {
        eprintln!("  miss: {m}");
    }
    assert!(
        top3 * 100 >= n * 80,
        "sloppy top-3 recall {top3}/{n} below 80% floor; misses:\n{}",
        misses.join("\n")
    );
}
