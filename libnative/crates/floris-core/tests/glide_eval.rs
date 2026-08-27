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
    // near-misses are printed so a shrinking margin is visible before it
    // becomes a miss
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
            Some(p @ 1..=2) => {
                top3 += 1;
                eprintln!(
                    "  near-miss (clean, pos {p}): '{}' behind {:?}",
                    word,
                    results.iter().take(p).map(|m| m.word.as_str()).collect::<Vec<_>>()
                );
            }
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
    // Second tranche (2026-08-27): everyday message vocabulary plus
    // double-letter and short words, the shapes fast thumbs actually glide.
    ("tomorrow", 235),
    ("morning", 240),
    ("message", 230),
    ("because", 245),
    ("thanks", 240),
    ("weekend", 220),
    ("people", 245),
    ("little", 235),
    ("coffee", 225),
    ("better", 235),
    ("really", 240),
    ("working", 230),
    ("already", 225),
    ("tonight", 220),
    ("dinner", 215),
    ("about", 250),
    ("would", 250),
    ("there", 250),
    ("think", 245),
    ("phone", 230),
    ("battery", 200),
    ("charging", 180),
    ("security", 190),
    ("parrot", 90),
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

/// Contractions are unglideable (no apostrophe key), so the bare form wins
/// the trace — but the DISPLAYED word must be the apostrophized one, and
/// real-word bares like "were" must come through untouched.
#[test]
fn glided_bare_contractions_display_apostrophized() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for (w, f) in [("dont", 228), ("were", 254)] {
        nlp.trie.insert(w, f);
    }
    let mut rng = Lcg(0xD0C5);
    let trace = synth_trace(&glide, "dont", &mut rng).unwrap();
    let results = glide.match_gesture(&trace, &nlp.trie, 3);
    assert_eq!(
        results.first().map(|m| m.word.as_str()),
        Some("don't"),
        "glided dont must display don't, got {:?}",
        results.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
    );

    let trace_were = synth_trace(&glide, "were", &mut rng).unwrap();
    let results_were = glide.match_gesture(&trace_were, &nlp.trie, 3);
    assert_eq!(
        results_were.first().map(|m| m.word.as_str()),
        Some("were"),
        "glided were is a real word and stays bare, got {:?}",
        results_were.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
}

/// Context-aware glide: "hello" and "jello" trace nearly identical paths
/// (h/j are adjacent), so geometry cannot separate them. With "jello" given
/// the higher frequency, only sentence context can rescue "hello" — and it
/// must, after "say". Without context the frequency favourite must still
/// win, proving the None path is unchanged.
#[test]
fn context_separates_near_identical_traces() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for (w, f) in [("hello", 200), ("jello", 240), ("say", 220)] {
        nlp.corpus_insert(w, f);
        nlp.trie.insert(w, f);
    }
    let say_id = nlp.corpus_words().iter().position(|w| w == "say").unwrap() as u32;
    let hello_id = nlp.corpus_words().iter().position(|w| w == "hello").unwrap() as u32;
    let mut blob = Vec::new();
    blob.extend_from_slice(b"CRKB");
    blob.push(2);
    blob.extend_from_slice(&1u32.to_le_bytes());
    blob.extend_from_slice(&(nlp.corpus_words().len() as u32).to_le_bytes());
    blob.extend_from_slice(&say_id.to_le_bytes());
    blob.extend_from_slice(&hello_id.to_le_bytes());
    blob.push(220);
    nlp.load_bigrams(&blob).unwrap();

    let mut rng = Lcg(0xFACADE);
    // Start the gesture midway between h and j so the anchor cannot decide:
    // shift the whole trace half a key toward j. Geometry is now genuinely
    // ambiguous between hello and jello; frequency (or context) must decide.
    let trace: Vec<Point2D> = synth_trace(&glide, "hello", &mut rng)
        .unwrap()
        .into_iter()
        .map(|p| Point2D::new(p.x + 50.0, p.y))
        .collect();

    let score_of = |ms: &[floris_core::GlideMatch], w: &str| {
        ms.iter().find(|m| m.word == w).map(|m| m.score).unwrap()
    };
    let no_ctx = glide.match_gesture(&trace, &nlp.trie, 3);
    for w in ["hello", "jello"] {
        assert!(
            no_ctx.iter().any(|m| m.word == w),
            "ambiguous trace must offer '{w}', got {:?}",
            no_ctx.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
        );
    }
    let gap_no_ctx = score_of(&no_ctx, "jello") - score_of(&no_ctx, "hello");

    let with_ctx = glide.match_gesture_with_context(&trace, &nlp.trie, 3, Some((&nlp, "say")));
    assert_eq!(
        with_ctx.first().map(|m| m.word.as_str()),
        Some("hello"),
        "'say hello' (220) must lead with context, got {:?}",
        with_ctx.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
    // The bonus is exactly bigram * 0.04 and applies only to "hello", so the
    // hello-vs-jello margin must widen by 220 * 0.04 = 8.8 (float tolerance).
    let gap_ctx = score_of(&with_ctx, "jello") - score_of(&with_ctx, "hello");
    assert!(
        (gap_ctx - gap_no_ctx - 8.8).abs() < 0.05,
        "context must widen the margin by 8.8: {gap_no_ctx} -> {gap_ctx}"
    );
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
            Some(p @ 1..=2) => {
                top3 += 1;
                eprintln!(
                    "  near-miss (sloppy, pos {p}): '{}' behind {:?}",
                    word,
                    results.iter().take(p).map(|m| m.word.as_str()).collect::<Vec<_>>()
                );
            }
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

/// The sloppy near-misses are the w-o-r-d corridor (world behind worked,
/// would behind word) and the standing claim is that CONTEXT finishes the
/// job in real use. This pins the claim: the same sloppy traces, given
/// their natural prev word, must put the right word back on top.
#[test]
fn context_rescues_the_word_corridor_on_sloppy_traces() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for &(w, f) in EVAL_WORDS {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    for (w, f) in [("worked", 200), ("word", 220), ("i", 250)] {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    let id = |e: &NlpEngine, w: &str| e.corpus_words().iter().position(|c| c == w).unwrap() as u32;
    let pairs = [("the", "world", 210u8), ("i", "would", 200)];
    let mut entries: Vec<(u32, u32, u8)> =
        pairs.iter().map(|&(a, b, s)| (id(&nlp, a), id(&nlp, b), s)).collect();
    entries.sort();
    let mut blob = Vec::new();
    blob.extend_from_slice(b"CRKB");
    blob.push(2);
    blob.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    blob.extend_from_slice(&(nlp.corpus_words().len() as u32).to_le_bytes());
    for (a, b, sc) in entries {
        blob.extend_from_slice(&a.to_le_bytes());
        blob.extend_from_slice(&b.to_le_bytes());
        blob.push(sc);
    }
    nlp.load_bigrams(&blob).unwrap();

    // Walk the same seeded rng through EVAL order so the corridor words get
    // exactly the traces the sloppy eval near-missed on.
    let mut rng = Lcg(0xDECAF);
    for &(word, _) in EVAL_WORDS {
        let trace = synth_sloppy_trace(&glide, word, &mut rng).expect("trace");
        let prev = match word {
            "world" => "the",
            "would" => "i",
            _ => continue,
        };
        let with_ctx = glide.match_gesture_with_context(&trace, &nlp.trie, 8, Some((&nlp, prev)));
        assert_eq!(
            with_ctx.first().map(|m| m.word.as_str()),
            Some(word),
            "context '{prev}' must rescue '{word}': {:?}",
            with_ctx.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
        );
    }
}

/// The inverse contract — the israeli-class property: context REFINES an
/// ambiguous trace, it never overrides a clean one. A clean "word" glide
/// with prev "the" (and "the world" strongly attested) must stay "word".
#[test]
fn context_never_hijacks_a_clean_trace() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for &(w, f) in EVAL_WORDS {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    for (w, f) in [("worked", 200), ("word", 220)] {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    let id = |e: &NlpEngine, w: &str| e.corpus_words().iter().position(|c| c == w).unwrap() as u32;
    let entries = {
        let mut v = vec![(id(&nlp, "the"), id(&nlp, "world"), 210u8)];
        v.sort();
        v
    };
    let mut blob = Vec::new();
    blob.extend_from_slice(b"CRKB");
    blob.push(2);
    blob.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    blob.extend_from_slice(&(nlp.corpus_words().len() as u32).to_le_bytes());
    for (a, b, sc) in entries {
        blob.extend_from_slice(&a.to_le_bytes());
        blob.extend_from_slice(&b.to_le_bytes());
        blob.push(sc);
    }
    nlp.load_bigrams(&blob).unwrap();

    let mut rng = Lcg(0xBEEF);
    let trace = synth_trace(&glide, "word", &mut rng).expect("trace");
    let with_ctx = glide.match_gesture_with_context(&trace, &nlp.trie, 8, Some((&nlp, "the")));
    assert_eq!(
        with_ctx.first().map(|m| m.word.as_str()),
        Some("word"),
        "a clean 'word' trace must never be hijacked to 'world' by context: {:?}",
        with_ctx.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
}

/// The coffee/code corridor: doubled letters vanish from a glide (c-o-f-e),
/// leaving a shape one adjacent via-point from c-o-d-e, and frequency
/// legitimately favors "code" (238 vs 225). Like the w-o-r-d corridor,
/// context is the designed disambiguator — pin that it works.
#[test]
fn context_rescues_the_coffee_code_corridor() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for &(w, f) in EVAL_WORDS {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    for (w, f) in [("code", 238), ("morning", 240)] {
        nlp.trie.insert(w, f);
        nlp.corpus_insert(w, f);
    }
    let id = |e: &NlpEngine, w: &str| e.corpus_words().iter().position(|c| c == w).unwrap() as u32;
    let entries = {
        let mut v = vec![(id(&nlp, "morning"), id(&nlp, "coffee"), 190u8)];
        v.sort();
        v
    };
    let mut blob = Vec::new();
    blob.extend_from_slice(b"CRKB");
    blob.push(2);
    blob.extend_from_slice(&(entries.len() as u32).to_le_bytes());
    blob.extend_from_slice(&(nlp.corpus_words().len() as u32).to_le_bytes());
    for (a, b, sc) in entries {
        blob.extend_from_slice(&a.to_le_bytes());
        blob.extend_from_slice(&b.to_le_bytes());
        blob.push(sc);
    }
    nlp.load_bigrams(&blob).unwrap();

    // Same seeded rng walk as the clean eval so "coffee" gets the exact
    // trace that near-missed.
    let mut rng = Lcg(0xC0FFEE);
    let trace = synth_trace(&glide, "coffee", &mut rng).expect("trace");
    let no_ctx = glide.match_gesture(&trace, &nlp.trie, 8);
    let with_ctx = glide.match_gesture_with_context(&trace, &nlp.trie, 8, Some((&nlp, "morning")));
    // premise guard: the corridor is real — code must be a top-2 contender
    assert!(
        no_ctx.iter().take(2).any(|m| m.word == "code"),
        "premise: code contends without context: {:?}",
        no_ctx.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
    assert_eq!(
        with_ctx.first().map(|m| m.word.as_str()),
        Some("coffee"),
        "'morning' must rescue coffee: {:?}",
        with_ctx.iter().take(3).map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
}


/// Comprehensive test battery for glide contraction expansion across all major English contractions:
/// bare swipe traces must automatically surface their apostrophized canonical forms.
#[test]
fn comprehensive_glided_contractions_battery() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    
    let contraction_pairs = [
        ("dont", "don't"),
        ("cant", "can't"),
        ("wont", "won't"),
        ("didnt", "didn't"),
        ("doesnt", "doesn't"),
        ("isnt", "isn't"),
        ("arent", "aren't"),
        ("wasnt", "wasn't"),
        ("werent", "weren't"),
        ("hasnt", "hasn't"),
        ("havent", "haven't"),
        ("hadnt", "hadn't"),
        ("couldnt", "couldn't"),
        ("shouldnt", "shouldn't"),
        ("wouldnt", "wouldn't"),
        ("mustnt", "mustn't"),
        ("neednt", "needn't"),
        ("mightnt", "mightn't"),
        ("couldve", "could've"),
        ("shouldve", "should've"),
        ("wouldve", "would've"),
        ("mightve", "might've"),
        ("mustve", "must've"),
        ("im", "I'm"),
        ("ive", "I've"),
        ("youre", "you're"),
        ("youve", "you've"),
        ("youll", "you'll"),
        ("youd", "you'd"),
        ("theyre", "they're"),
        ("theyve", "they've"),
        ("theyll", "they'll"),
        ("theyd", "they'd"),
        ("weve", "we've"),
        ("itll", "it'll"),
        ("itd", "it'd"),
        ("whats", "what's"),
        ("thats", "that's"),
        ("theres", "there's"),
        ("heres", "here's"),
        ("wheres", "where's"),
        ("hows", "how's"),
        ("whos", "who's"),
        ("whys", "why's"),
        ("lets", "let's"),
        ("yall", "y'all"),
        ("cmon", "c'mon"),
        ("maam", "ma'am"),
        ("oclock", "o'clock"),
        ("somethings", "something's"),
        ("everythings", "everything's"),
        ("nothings", "nothing's"),
        ("someones", "someone's"),
        ("everyones", "everyone's"),
        ("aint", "ain't"),
    ];

    for &(bare, _) in &contraction_pairs {
        nlp.trie.insert(bare, 255);
    }

    let mut rng = Lcg(0x5EED_C0DE);
    let mut failures = Vec::new();

    for &(bare, expected_apostrophized) in &contraction_pairs {
        if let Some(trace) = synth_trace(&glide, bare, &mut rng) {
            let results = glide.match_gesture(&trace, &nlp.trie, 3);
            let in_top = results.iter().take(3).any(|m| m.word == expected_apostrophized);
            if !in_top {
                failures.push(format!("Bare '{bare}' expected '{expected_apostrophized}' in top-3, got {:?}", results.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()));
            }
        } else {
            failures.push(format!("Failed to synthesize keypath for '{bare}'"));
        }
    }

    assert!(failures.is_empty(), "Contraction glide failures:\n{}", failures.join("\n"));
}


/// Kinematic inflection testing (Loop 2/18):
/// A straight-line fast transit from c -> a -> t must output "cat", NOT "cart" (which requires
/// an intentional detour or dwell at 'r'). When the user deliberately visits 'r', "cart" wins.
#[test]
fn kinematics_inflections_separate_swipe_through_accidental_keys() {
    let glide = qwerty_engine();
    let mut nlp = NlpEngine::new();
    for (w, f) in [("cat", 245), ("cart", 240), ("pat", 240), ("part", 245)] {
        nlp.trie.insert(w, f);
    }
    let mut rng = Lcg(0xCA7_C0DE);

    // 1. Direct swipe for "cat" (c -> a -> t)
    let trace_cat = synth_trace(&glide, "cat", &mut rng).expect("trace cat");
    let res_cat = glide.match_gesture(&trace_cat, &nlp.trie, 3);
    assert_eq!(
        res_cat.first().map(|m| m.word.as_str()),
        Some("cat"),
        "direct swipe for 'cat' must yield 'cat', got {:?}",
        res_cat.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
    );

    // 2. Deliberate detour swipe for "cart" (c -> a -> r -> t)
    let trace_cart = synth_trace(&glide, "cart", &mut rng).expect("trace cart");
    let res_cart = glide.match_gesture(&trace_cart, &nlp.trie, 3);
    assert_eq!(
        res_cart.first().map(|m| m.word.as_str()),
        Some("cart"),
        "deliberate detour swipe for 'cart' must yield 'cart', got {:?}",
        res_cart.iter().map(|m| m.word.as_str()).collect::<Vec<_>>()
    );
}
