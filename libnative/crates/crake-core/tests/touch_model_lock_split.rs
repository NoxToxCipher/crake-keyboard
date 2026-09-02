//! Guard for perf item 6: the touch model moved off NLP_ENGINE's big RwLock into
//! its own lock inside the engine (`Arc<RwLock<Option<TouchModel>>>`).
//!
//! Two properties must hold after the split:
//!  1. NO SUGGEST-CONSISTENCY REGRESSION — with a fixed (frozen) touch model, a
//!     suggest is deterministic, because each suggest still takes ONE read guard
//!     for the whole call. This mirrors the old behaviour where the engine write
//!     lock made the model immutable for the duration of any suggest read.
//!  2. NO DEADLOCK — in the real deployment shape (engine behind an outer RwLock,
//!     exactly like the JNI `NLP_ENGINE`), the per-key-down record path taking
//!     `engine.read()` + `touch_model.write()`, concurrent suggest readers taking
//!     `engine.read()` + `touch_model.read()`, and an occasional layout upload
//!     taking `engine.write()`, all interleave without deadlocking. Lock order is
//!     always engine-before-touch_model, so no cycle exists; this test drives the
//!     three paths hard from many threads and must terminate.

use crake_core::{NlpEngine, TouchModel};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::{Arc, RwLock};

/// A QWERTY touch model, matching the layout tests elsewhere in the suite.
fn qwerty_model() -> Option<TouchModel> {
    let mut keys = Vec::new();
    for (r, row) in ["qwertyuiop", "asdfghjkl", "zxcvbnm"].iter().enumerate() {
        for (i, ch) in row.chars().enumerate() {
            keys.push((
                ch,
                (i as f32 + [0.0, 0.5, 1.5][r] + 0.5) * 100.0,
                (r as f32 + 0.5) * 140.0,
            ));
        }
    }
    TouchModel::from_layout(&keys)
}

fn loaded_engine() -> NlpEngine {
    let mut e = NlpEngine::new();
    // A few extra words so the fuzzy/typo paths have something to find.
    for w in ["hello", "world", "keyboard", "suggest", "there", "their"] {
        e.trie.insert(w, 500);
    }
    e.set_touch_model(qwerty_model());
    e
}

#[test]
fn frozen_touch_model_snapshot_is_deterministic() {
    let e = loaded_engine();
    // Repeated suggests over a frozen model must be byte-identical: the per-suggest
    // read-guard snapshot means the slip oracle can never change mid-call.
    let baseline = e.suggest_with_context("thier", "", 8);
    for _ in 0..1000 {
        assert_eq!(
            e.suggest_with_context("thier", "", 8),
            baseline,
            "suggest must be deterministic while the touch model is frozen"
        );
    }
    // Sanity: the model is actually consulted — swapping to the static-table
    // fallback (None) is allowed to change results, proving the lock path is live.
    let mut e2 = loaded_engine();
    e2.set_touch_model(None);
    // Not asserting inequality (they may coincide), just that both paths run and
    // stay self-consistent.
    let fallback = e2.suggest_with_context("thier", "", 8);
    assert_eq!(e2.suggest_with_context("thier", "", 8), fallback);
}

#[test]
fn concurrent_record_suggest_and_upload_do_not_deadlock() {
    // Engine behind an outer RwLock == the JNI NLP_ENGINE deployment shape.
    let engine = Arc::new(RwLock::new(loaded_engine()));
    let done = Arc::new(AtomicU64::new(0));

    let mut handles = Vec::new();

    // Suggest readers: engine.read() + touch_model.read() (via slip oracle).
    for t in 0..4 {
        let engine = Arc::clone(&engine);
        let done = Arc::clone(&done);
        handles.push(std::thread::spawn(move || {
            let queries = ["thier", "helo", "wrold", "sugest", "keybard"];
            for i in 0..3000 {
                let e = engine.read().unwrap();
                let q = queries[(i + t) % queries.len()];
                std::hint::black_box(e.suggest_with_context(q, "there", 8));
                std::hint::black_box(e.keys_near('e', 'r'));
            }
            done.fetch_add(1, Ordering::Relaxed);
        }));
    }

    // Key-down recorders: engine.read() + touch_model.write().
    for t in 0..2 {
        let engine = Arc::clone(&engine);
        let done = Arc::clone(&done);
        handles.push(std::thread::spawn(move || {
            let chars = ['e', 'r', 't', 'a', 's'];
            for i in 0..8000 {
                let e = engine.read().unwrap();
                let ch = chars[(i + t) % chars.len()];
                let x = 200.0 + (i % 40) as f32;
                let y = 100.0 + (i % 30) as f32;
                e.record_touch_hit_biometrics(ch, x, y, 8.0, 4.0, 0.1);
            }
            done.fetch_add(1, Ordering::Relaxed);
        }));
    }

    // Layout uploads: engine.write() + set_touch_model (engine-then-touch_model).
    {
        let engine = Arc::clone(&engine);
        let done = Arc::clone(&done);
        handles.push(std::thread::spawn(move || {
            for i in 0..200 {
                let mut e = engine.write().unwrap();
                e.set_touch_model(if i % 2 == 0 { qwerty_model() } else { None });
                drop(e);
                std::thread::yield_now();
            }
            done.fetch_add(1, Ordering::Relaxed);
        }));
    }

    // Join all — a deadlock would hang here and trip the test-runner timeout.
    for h in handles {
        h.join().expect("worker thread panicked");
    }
    assert_eq!(done.load(Ordering::Relaxed), 7, "every worker must complete");

    // Engine is still usable and self-consistent afterwards.
    let e = engine.read().unwrap();
    let a = e.suggest_with_context("thier", "there", 8);
    let b = e.suggest_with_context("thier", "there", 8);
    assert_eq!(a, b, "post-run suggest must be deterministic under a frozen read");
}
