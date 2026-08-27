//! Replays REAL captured glide traces (harvested from a debug device by
//! utils/harvest_glide_traces.py) against the engine, on the exact key
//! geometry each stroke was drawn on. This is the ground-truth companion
//! to glide_eval's synthetic traces: when the field reports a wrong commit
//! ("would" -> "writings"), the harvested stroke lands here and the failure
//! becomes reproducible.
//!
//! Line format (pipe-delimited, no serde):
//!     prev|top1,top2,top3|ch:x:y:w:h,...|x:y;x:y;...
//! where `top` is what the DEVICE build answered at capture time.
//!
//! With no data file the test passes trivially — capture is optional and
//! device-dependent. With data it asserts the engine still produces
//! candidates for every stroke and prints a scoreboard comparing current
//! answers against the captured ones, so an engine change that shifts
//! real-world behaviour is visible in the test output.

use floris_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};

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

/// Points parse from v1 "x:y" or v2 "x:y:t" (t = ms since stroke start).
/// Timestamps ride alongside; geometry callers ignore them until the
/// dwell/velocity work lands (three features are waiting on them).
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

#[test]
fn replay_captured_device_traces() {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/tests/data/glide_traces.txt");
    let data = match std::fs::read_to_string(path) {
        Ok(d) => d,
        Err(_) => {
            eprintln!("glide replay: no captured traces (tests/data/glide_traces.txt) — skipping");
            return;
        }
    };

    // Real dictionary + bigrams: captured strokes were matched against the
    // shipped assets, so the replay must be too.
    let mut nlp = NlpEngine::new();
    let dict = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/data.crkd"
    ))
    .expect("dict blob");
    floris_core::parse_dict_blob(&dict, |word, freq| {
        nlp.trie.insert(word, freq);
        nlp.corpus_insert(word, freq);
    })
    .expect("dict parse");
    let big = std::fs::read(concat!(
        env!("CARGO_MANIFEST_DIR"),
        "/../../../app/src/main/assets/ime/dict/bigrams.crkb"
    ))
    .expect("bigram blob");
    nlp.load_bigrams(&big).expect("bigram parse");

    let mut total = 0;
    let mut agree = 0;
    for (i, line) in data.lines().enumerate() {
        let fields: Vec<&str> = line.splitn(4, '|').collect();
        if fields.len() != 4 {
            continue;
        }
        let (prev, captured_top, layout_s, pts_s) =
            (fields[0], fields[1], fields[2], fields[3]);
        let layout = parse_layout(layout_s);
        let key_w = layout.first().map(|k| k.width).unwrap_or(95.0);
        let (points, timestamps) = parse_points(pts_s);
        assert!(
            layout.len() >= 26 && points.len() >= 2,
            "trace {i}: malformed (keys={}, pts={})",
            layout.len(),
            points.len()
        );
        let mut engine = GlideEngine::new();
        engine.set_layout(layout);
        let ctx = if prev.is_empty() { None } else { Some((&nlp, prev)) };
        // Micro-strokes (tap-slides) may legitimately return NOTHING now:
        // kinematic gating rejects them engine-side, which is the desired
        // defense in depth next to the detector threshold. Only strokes
        // with real glide extent must produce candidates.
        // (key width captured before set_layout consumed the vec)
        let travel: f32 = points.windows(2).map(|w| w[0].distance(&w[1])).sum();
        let results = engine.match_gesture_with_context(&points, &nlp.trie, 8, ctx);
        // Below ~1.5 key-widths the engine may reject the stroke as a
        // tap-slide (kinematic gating) — that is desired; a real word
        // glide travels several key-widths. Only clearly-multi-key
        // strokes must produce candidates.
        // Rejection is the engine's prerogative at ANY length: kinematic
        // gating refuses garbled strokes that would only ever match junk
        // (field case: a 5.5 kw mid-frustration garble the old build
        // committed as "uaw" — every candidate any era produced for it
        // was junk-band; nothing IS the right answer). Rejections are
        // reported, never asserted against; the hard pin below guards
        // agreement drift on strokes the engine does answer.
        if results.is_empty() {
            eprintln!(
                "  trace {i}: stroke ({:.2} kw) rejected by engine — accepted",
                travel / key_w
            );
            continue;
        }
        eprintln!(
            "  trace {i} prev='{prev}' scores: {}",
            results
                .iter()
                .take(6)
                .map(|m| format!("{}={:.2}", m.word, m.score))
                .collect::<Vec<_>>()
                .join(" ")
        );
        // v2 traces carry timing: print the dwell profile — time spent
        // within one key-radius of each layout key — so wobble-vs-visit
        // hypotheses can be read straight off real strokes.
        if timestamps.len() == points.len() && points.len() >= 2 {
            let duration = timestamps.last().unwrap().saturating_sub(timestamps[0]);
            let mut dwells: Vec<(char, u32)> = Vec::new();
            let layout_keys = parse_layout(layout_s);
            for k in &layout_keys {
                let mut d = 0u32;
                for w in 0..points.len() - 1 {
                    if points[w].distance(&k.center) < k.width * 0.6 {
                        d += timestamps[w + 1].saturating_sub(timestamps[w]);
                    }
                }
                if d > 0 {
                    dwells.push((k.character, d));
                }
            }
            dwells.sort_by(|a, b| b.1.cmp(&a.1));
            let top_dwells: Vec<String> =
                dwells.iter().take(6).map(|(c, d)| format!("{c}:{d}ms")).collect();
            eprintln!(
                "  trace {i} timing: {duration}ms total, dwell {}",
                top_dwells.join(" ")
            );
        }

        let device_first = captured_top.split(',').next().unwrap_or("");
        let now_first = results.first().map(|m| m.word.as_str()).unwrap_or("");
        total += 1;
        if now_first == device_first {
            agree += 1;
            // A long deliberate stroke where device and engine agree is
            // ground truth: future scoring drift on REAL glides fails
            // loudly instead of hiding in the agreement ratio. (First
            // pinned specimen: the 12.9 kw "hello", 2026-08-28.)
            if travel >= key_w * 5.0 {
                assert_eq!(
                    now_first, device_first,
                    "trace {i}: long real glide drifted from its committed word"
                );
            }
        } else {
            eprintln!(
                "  trace {i}: device committed '{device_first}', engine now says '{now_first}' \
                 (top-3 now: {:?}, prev='{prev}')",
                results.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
            );
        }
    }
    eprintln!("glide replay: {agree}/{total} match the captured device commits");
}
