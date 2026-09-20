//! Real strokes, with what the person was actually drawing.
//!
//! glide_replay compares today's answer against what the DEVICE answered
//! when the stroke was captured, which only catches drift. This file says
//! what each stroke MEANT. The labels were recovered by walking each path
//! and reading off the keys it dwells on: line 16 traces
//! h-g-t-r-e-r-t-h-j-k-l-o, which is "hello" written as three strokes of
//! the thumb, and lines 13, 15, 20 and 31 trace the same shape.
//!
//! That is the whole point of this file: the person glided "hello" six
//! times and got hello three times, jericho, horatio and heavily. A
//! keyboard that answers a common word correctly half the time is the
//! "essentially unusable" in the field report of 2026-09-21.
//!
//! The corpus is private typing data and stays gitignored, so with no data
//! file this test skips, exactly as glide_replay does.

use crake_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};

/// (line number in the corpus, what the person was drawing)
const GOLD: &[(usize, &str)] = &[
    (13, "hello"),
    (15, "hello"),
    (16, "hello"),
    (17, "hollow"),
    (20, "hello"),
    (21, "glee"),
    (22, "how"),
    (25, "you"),
    (26, "going"),
    (28, "going"),
    (30, "well"),
    (31, "hello"),
];

fn parse_layout(s: &str) -> Vec<KeyInfo> {
    s.split(',')
        .filter_map(|item| {
            let p: Vec<&str> = item.split(':').collect();
            if p.len() != 5 {
                return None;
            }
            let ch = p[0].chars().next()?;
            Some(KeyInfo {
                code: ch as i32,
                character: ch,
                center: Point2D::new(p[1].parse().ok()?, p[2].parse().ok()?),
                width: p[3].parse().ok()?,
                height: p[4].parse().ok()?,
            })
        })
        .collect()
}

fn parse_points(s: &str) -> (Vec<Point2D>, Vec<u32>) {
    let mut pts = Vec::new();
    let mut ts = Vec::new();
    for triple in s.split(';') {
        let mut it = triple.split(':');
        let (Some(x), Some(y)) = (it.next(), it.next()) else { continue };
        let (Ok(x), Ok(y)) = (x.parse(), y.parse()) else { continue };
        pts.push(Point2D::new(x, y));
        if let Some(t) = it.next().and_then(|t| t.parse::<f32>().ok()) {
            ts.push(t as u32);
        }
    }
    if ts.len() != pts.len() {
        ts.clear();
    }
    (pts, ts)
}

fn shipped_trie() -> NlpEngine {
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
    e
}

#[test]
fn real_strokes_produce_the_word_that_was_drawn() {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/data/glide_traces.txt");
    let Ok(data) = std::fs::read_to_string(path) else {
        eprintln!("glide gold: no captured traces — skipping");
        return;
    };
    let lines: Vec<&str> = data.lines().collect();
    let nlp = shipped_trie();

    let (mut top1, mut top3, mut total) = (0usize, 0usize, 0usize);
    let mut report = Vec::new();
    for &(idx, want) in GOLD {
        let Some(line) = lines.get(idx) else { continue };
        let f: Vec<&str> = line.split('|').collect();
        if f.len() < 4 {
            continue;
        }
        let keys = parse_layout(f[2]);
        let (pts, ts) = parse_points(f[3]);
        if keys.is_empty() || pts.len() < 20 {
            continue;
        }
        let mut glide = GlideEngine::new();
        glide.set_layout(keys);
        let prev = f[0].trim();
        let ctx = if prev.is_empty() { None } else { Some((&nlp, prev)) };
        let results = glide.match_gesture_timed(&pts, &ts, &nlp.trie, 8, ctx);
        total += 1;
        let rank = results.iter().position(|m| m.word.eq_ignore_ascii_case(want));
        match rank {
            Some(0) => {
                top1 += 1;
                top3 += 1;
            }
            Some(1..=2) => {
                top3 += 1;
                report.push(format!(
                    "  line {idx:>2} '{want}' at slot {}: {:?}",
                    rank.unwrap() + 1,
                    results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
                ));
            }
            _ => report.push(format!(
                "  line {idx:>2} '{want}' MISSING: {:?}",
                results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
            )),
        }
    }

    eprintln!("\nGLIDE GOLD (real captured strokes, shipped dictionary)");
    eprintln!("  top-1 {top1}/{total}   top-3 {top3}/{total}");
    for r in &report {
        eprintln!("{r}");
    }
    assert!(total >= 10, "gold set did not load: {total} strokes");
    // Where it stood when this file was written. Raise these as the
    // decoder improves; never lower them.
    assert!(top1 >= 7, "real-stroke top-1 regressed: {top1}/{total}");
    assert!(top3 >= 9, "real-stroke top-3 regressed: {top3}/{total}");
}
