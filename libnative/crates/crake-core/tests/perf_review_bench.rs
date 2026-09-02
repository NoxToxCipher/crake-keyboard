//! Performance-review benchmark against the SHIPPED assets (data.crkd +
//! bigrams.crkb). Unlike tests/benchmark.rs, which uses a synthetic 10k
//! "wordNNNNN" corpus whose 6-7 char prefixes only ever touch tiny
//! subtrees, this measures the paths the keyboard actually takes per
//! keystroke and per glide, on the real 49k-word dictionary.
//!
//! Run: cargo test -p crake-core --release --test perf_review_bench -- --nocapture

use crake_core::{detect_double_letter_loops, GlideEngine, KeyInfo, NlpEngine, Point2D};
use std::time::Instant;

fn engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    crake_core::parse_dict_blob(&dict, |w, f| {
        e.trie.insert(w, f);
        e.corpus_insert(w, f);
    })
    .expect("dict parse");
    let big = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/bigrams.crkb"
    ))
    .expect("bigram blob");
    e.load_bigrams(&big).expect("bigram parse");
    e
}

/// Standard QWERTY geometry, 95px key pitch (mirrors harvested traces).
fn qwerty_layout() -> Vec<KeyInfo> {
    let rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    let mut keys = Vec::new();
    for (r, row) in rows.iter().enumerate() {
        let x0 = match r {
            1 => 47.5 + 47.5,
            2 => 47.5 + 142.5,
            _ => 47.5,
        };
        for (c, ch) in row.chars().enumerate() {
            keys.push(KeyInfo {
                code: ch as i32,
                character: ch,
                center: Point2D::new(x0 + c as f32 * 95.0, 60.0 + r as f32 * 120.0),
                width: 95.0,
                height: 120.0,
            });
        }
    }
    keys
}

fn key_center(keys: &[KeyInfo], ch: char) -> Point2D {
    keys.iter().find(|k| k.character == ch).unwrap().center
}

/// Straight-line interpolated stroke through the letters of `word`,
/// ~200 points total (a realistic 120Hz half-second glide), with jitter.
fn stroke_for(keys: &[KeyInfo], word: &str, points_total: usize) -> (Vec<Point2D>, Vec<u32>) {
    let centers: Vec<Point2D> = word.chars().map(|c| key_center(keys, c)).collect();
    let segs = centers.len() - 1;
    let per = (points_total / segs.max(1)).max(2);
    let mut pts = Vec::new();
    let mut ts = Vec::new();
    let mut t = 0u32;
    for w in centers.windows(2) {
        for i in 0..per {
            let f = i as f32 / per as f32;
            let jx = ((i * 7) % 5) as f32 - 2.0;
            let jy = ((i * 3) % 5) as f32 - 2.0;
            pts.push(Point2D::new(
                w[0].x + (w[1].x - w[0].x) * f + jx,
                w[0].y + (w[1].y - w[0].y) * f + jy,
            ));
            ts.push(t);
            t += 4; // ~250Hz sampling
        }
    }
    pts.push(*centers.last().unwrap());
    ts.push(t);
    (pts, ts)
}

#[test]
#[ignore = "perf benchmark, run on demand with -- --ignored --nocapture"]
fn perf_review_report() {
    let e = engine();
    println!("\n=== PERF REVIEW: shipped dictionary ({} bytes blob) ===", 652825);

    // ---- 1. prefix_search: full-subtree walk cost by prefix length ----
    for (prefix, limit, label) in [
        ("s", 12, "per-keystroke suggest pull, 1-char word"),
        ("t", 12, "per-keystroke suggest pull, 1-char word"),
        ("th", 12, "per-keystroke suggest pull, 2-char word"),
        ("the", 12, "per-keystroke suggest pull, 3-char word"),
        ("s", 300, "glide candidate pull (limit=300)"),
        ("t", 300, "glide candidate pull (limit=300)"),
    ] {
        let iters = 200;
        let n = e.trie.prefix_search(prefix, limit).len();
        let t0 = Instant::now();
        for _ in 0..iters {
            std::hint::black_box(e.trie.prefix_search(std::hint::black_box(prefix), limit));
        }
        let us = t0.elapsed().as_micros() as f64 / iters as f64;
        println!(
            "prefix_search({prefix:?}, {limit:>3})  -> {n:>3} results  {us:>9.1} us/call   [{label}]"
        );
    }

    // ---- 2. suggest_with_context: the per-keystroke JNI workload ----
    for (typed, prev) in [("s", ""), ("th", ""), ("the", "of"), ("thoriufhky", ""), ("helllo", "")] {
        let iters = 100;
        let t0 = Instant::now();
        for _ in 0..iters {
            std::hint::black_box(e.suggest_with_context(std::hint::black_box(typed), prev, 8));
        }
        let us = t0.elapsed().as_micros() as f64 / iters as f64;
        println!("suggest_with_context({typed:?}, prev={prev:?})  {us:>9.1} us/keystroke");
    }

    // ---- 3. predict_next_letter_words: up to 26 subtree walks ----
    for prefix in ["", "s", "th"] {
        let iters = 50;
        let t0 = Instant::now();
        for _ in 0..iters {
            std::hint::black_box(e.predict_next_letter_words(std::hint::black_box(prefix), "the"));
        }
        let us = t0.elapsed().as_micros() as f64 / iters as f64;
        println!("predict_next_letter_words({prefix:?})  {us:>9.1} us/call");
    }

    // ---- 4. glide match: preview fires every 150ms + final on lift ----
    let keys = qwerty_layout();
    let mut g = GlideEngine::new();
    g.set_layout(keys.clone());
    for word in ["hello", "keyboard", "something"] {
        let (pts, ts) = stroke_for(&keys, word, 200);
        let iters = 30;
        let t0 = Instant::now();
        for _ in 0..iters {
            std::hint::black_box(g.match_gesture_timed(&pts, &ts, &e.trie, 8, Some((&e, "the"))));
        }
        let us = t0.elapsed().as_micros() as f64 / iters as f64;
        let top = g
            .match_gesture_timed(&pts, &ts, &e.trie, 8, Some((&e, "the")))
            .first()
            .map(|m| m.word.clone())
            .unwrap_or_default();
        println!(
            "match_gesture_timed({word:?}, {} pts)  {us:>9.1} us/call (x4-5 per gesture)  top={top:?}",
            pts.len()
        );
    }

    // ---- 5. detect_double_letter_loops: O(n * 11^2) sqrt recompute ----
    let (pts, _) = stroke_for(&keys, "something", 200);
    let iters = 500;
    let t0 = Instant::now();
    for _ in 0..iters {
        std::hint::black_box(detect_double_letter_loops(std::hint::black_box(&pts), 47.5));
    }
    let us = t0.elapsed().as_micros() as f64 / iters as f64;
    println!("detect_double_letter_loops(200 pts)  {us:>9.1} us/call");

    println!("=== END PERF REVIEW ===\n");
}

/// Reproduces the lib.rs lock topology: one global RwLock<NlpEngine>.
/// A background reader runs suggest_with_context (the per-keystroke JNI
/// path) while the "UI thread" tries to take the write lock the way
/// nativeRecordTouchHit does on every letter key-down. Measures how long
/// the key-down stalls behind an in-flight read.
///
/// NOTE (measured 2026-09-02): with the reader in a tight loop (no gap
/// between reads), the writer STARVED OUTRIGHT — the write() below did
/// not return for over 4 minutes before the run was killed. std RwLock
/// makes no fairness guarantee, and on the Linux/Android futex
/// implementation a re-acquiring reader can beat the woken writer
/// indefinitely. The app's real read traffic has gaps, so the realistic
/// stall is "remainder of one in-flight read" (up to ~1ms measured here,
/// several ms on device) — the 500us reader gap below models that. But
/// overlapping readers (suggest + glide preview + flick warmer all take
/// NLP_ENGINE.read()) push toward the starvation regime, on the UI thread.
#[test]
#[ignore = "perf benchmark, run on demand with -- --ignored --nocapture"]
fn perf_review_lock_stall() {
    use std::sync::atomic::{AtomicBool, Ordering};
    use std::sync::{Arc, RwLock};

    let engine = Arc::new(RwLock::new(engine()));
    let stop = Arc::new(AtomicBool::new(false));

    let reader = {
        let engine = Arc::clone(&engine);
        let stop = Arc::clone(&stop);
        std::thread::spawn(move || {
            while !stop.load(Ordering::Relaxed) {
                {
                    let e = engine.read().unwrap();
                    // worst realistic keystroke: a typo needing fuzzy search
                    std::hint::black_box(e.suggest_with_context("thoriufhky", "", 8));
                }
                // gap between reads — without this the writer starves (see note)
                std::thread::sleep(std::time::Duration::from_micros(500));
            }
        })
    };

    std::thread::sleep(std::time::Duration::from_millis(50));
    let mut worst = std::time::Duration::ZERO;
    let mut total = std::time::Duration::ZERO;
    let samples = 200;
    for _ in 0..samples {
        let t0 = Instant::now();
        {
            // Perf item 6: the per-key-down record now takes a SHARED engine read
            // plus the touch model's own lock, instead of the engine WRITE lock —
            // so it no longer serializes behind the suggest reader above.
            let e = engine.read().unwrap();
            std::hint::black_box(e.record_touch_hit_biometrics('a', 5.0, 5.0, 0.0, 0.0, 0.0));
        }
        let dt = t0.elapsed();
        total += dt;
        if dt > worst {
            worst = dt;
        }
        std::thread::sleep(std::time::Duration::from_millis(2));
    }
    stop.store(true, Ordering::Relaxed);
    reader.join().unwrap();
    println!(
        "\nkey-down record (shared engine read + touch-model lock) latency behind per-keystroke suggest read: avg {:>7.1} us, worst {:>7.1} us over {} samples\n",
        total.as_micros() as f64 / samples as f64,
        worst.as_micros() as f64,
        samples
    );
}
