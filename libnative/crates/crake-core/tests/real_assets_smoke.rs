//! Sentinel battery against the SHIPPED assets (data.crkd + bigrams.crkb,
//! in-repo). Every behavior here was a field fix; unit harnesses prove the
//! mechanisms, this proves them against the data actually on phones — so
//! an asset regen, a dictionary edit, or an ingestion can't silently undo
//! a shipped fix. Field specimens are all Lochran's own typing (2026-08).

use crake_core::NlpEngine;

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

#[test]
fn shipped_fixes_hold_on_shipped_assets() {
    let e = engine();
    // (prev, typed, expected auto-commit word or "" for must-not-flip)
    let cases: &[(&str, &str, &str)] = &[
        ("", "ti", "to"),
        ("i", "an", "am"),
        ("", "abunch", "a bunch"),
        ("", "thoriufhky", "thoroughly"),
        ("", "xurrenrky", "currently"),
        ("", "aer", "are"),
        ("always", "hse", "use"),
        ("", "tou", "you"),
        ("", "tomorow", "tomorrow"),
        ("", "helllo", "hello"),
        ("", "inmy", "in my"),
        // "ill" is a dropped-apostrophe "I'll" unless the word before it
        // says adjective; "lile" is "like" (l for k), not the city
        // (field report 2026-09-13).
        ("", "ill", "I'll"),
        ("", "Ill", "I'll"),
        ("tomorrow", "ill", "I'll"),
        ("and", "Ill", "I'll"),
        ("ok,", "ill", "I'll"),
        ("", "lile", "like"),
        ("i", "lile", "like"),
        ("feel", "ill", ""),
        ("feeling", "ill", ""),
        ("very", "ill", ""),
        ("is", "ill", ""),
        ("the", "ill", ""),
        ("I'm", "ill", ""),
        // key-bounce triples take the real word, not the junk single form;
        // a dropped last letter completes to the top-tier word; an exact
        // word never completes (2026-09-13).
        ("", "willl", "will"),
        ("", "goood", "good"),
        ("", "beeen", "been"),
        ("", "peopl", "people"),
        ("", "becaus", "because"),
        ("", "kno", "know"),
        ("", "the", ""),
        ("", "thin", ""),
        // a token that is also one adjacent key from another common word
        // is not completed over it ("whe" is who/she as much as when);
        // where the neighbour is the clear reading it still wins
        ("", "whe", ""),
        ("", "healt", "health"),
        // house (249) over hours (244): the commoner one-letter fix wins;
        // "healt" is health (dropped h), heart needs l~r which is Dvorak
        ("", "hous", "house"),
        // a capitalised token is a name ("Gav" stays); an equally close,
        // commoner insertion rival wins the token ("thre" is there)
        ("", "Gav", ""),
        ("hi", "Gav", ""),
        // "thre"/"thir" are three-way ambiguous (the/there/three,
        // this/their/third): no commit, or the commonest reading; since
        // 2026-09-18 a cost tie prefers the longer word (a dropped letter
        // is the commoner slip), so "thre" is there
        ("", "thre", "there"),
        ("", "thir", "their"),
        // the pronoun/article-drop class the sweep cannot see (a lone "i"
        // half is shown as "I", 2026-09-18)
        ("", "iwas", "I was"),
        ("", "ihave", "I have"),
        ("", "ithink", "I think"),
        ("", "isee", "I see"),
        ("", "afew", "a few"),
        ("", "cani", "can I"),
        ("", "heis", "he is"),
        // one adjacent slip of a top-tier word punches through the typo's
        // own completions ("front", "james")
        ("", "fron", "from"),
        ("", "jame", "name"),
        ("", "hig", ""),
        // a typing slip of one common word is never a phrase, however
        // strong the pair; a stray letter after a common word is that word
        ("", "amking", "making"),
        ("", "toher", "other"),
        ("", "maybbe", "maybe"),
        ("", "innto", "into"),
        // splitter: the halves never testify against their own phrase, a
        // strongly attested pair is never blocked, a doubled lead letter is
        // a bounce, and a plain typo still is not a phrase (review
        // 2026-09-13)
        ("", "abit", "a bit"),
        ("", "tobe", "to be"),
        ("", "imean", "I mean"),
        ("", "onmy", "on my"),
        ("", "aand", "and"),
        ("", "abut", "about"),
        ("", "agout", "about"),
        // fuzzy ranking: one adjacent slip beats a two-slip top-tier word;
        // an ineligible far word cannot starve the dropped-letter fix
        ("", "sdll", "sell"),
        // hunt 2026-09-18
        ("the", "shell", ""),
        ("she", "lets", ""),
        ("is", "you're", ""),
        ("in", "they're", ""),
        ("", "canr", "can't"),
        ("", "wonf", "won't"),
        ("", "dontt", "don't"),
        ("", "illdo", "I'll do"),
        ("", "imsure", "I'm sure"),
        ("", "iforgot", "I forgot"),
        ("", "iwasnt", "I wasn't"),
        ("", "iam", "I am"),
        ("", "idont", "I don't"),
        ("", "acount", "account"),
        ("", "bein", ""),
        ("", "sleepin", ""),
        ("", "vs", ""),
        ("", "wk", ""),
        ("", "ghosted", ""),
        ("hey", "y'all", ""),
        ("", "kids'", ""),
        ("", "notme", "not me"),
        ("", "can’t", ""),
        ("", "IM", ""),
        ("", "ILL", ""),
        ("bit", "ill", ""),
        ("still", "ill", ""),
        // the typo corpus never rewrites a real word (British spellings,
        // "owed", "alright", "mt"); a genuine misspelling still fixes
        ("my", "favourite", ""),
        ("", "Favourite", ""),
        ("the", "neighbour", ""),
        ("", "alright", ""),
        ("he", "owed", ""),
        ("", "Mt", ""),
        ("", "teh", "the"),
        ("", "thier", "their"),
        // a shorthand code after a number is a unit
        ("500", "gm", ""),
        ("300", "bc", ""),
        // one bounce on a word with a double letter
        ("", "wwill", "will"),
        ("", "goodd", "good"),
        ("", "tooo", "too"),
        ("", "offf", "off"),
        ("", "aall", "all"),
        ("", "needd", "need"),
        ("", "bbeen", "been"),
        // a swap that lands on a double is still the swap
        ("", "theer", "there"),
        ("", "perss", "press"),
        ("", "sveen", "seven"),
        ("", "emmber", "member"),
        // the doubled guess yields to the everyday insertion reading
        ("", "abot", "about"),
        ("", "oter", "other"),
        ("", "parot", "parrot"),
        // a rare swap never steals a token with a commoner deletion reading
        ("", "sohe", "so he"),
        ("", "notme", "not me"),
        ("", "ishe", "is he"),
        // the space beam still owns the far-letter compounds
        ("", "somebone", "someone"),
        ("", "tobday", "today"),
        // three-word run-togethers never commit a rare word
        ("", "alotof", ""),
        ("", "inabit", ""),
        // QWERTY-only adjacency: Dvorak pairs are not slips
        ("", "vould", "could"),
        ("", "canh", "can"),
        ("", "theb", "then"),
        // the relaxed chat split never beats another top-tier reading
        ("", "ime", ""),
        ("", "ino", ""),
        ("", "istory", "history"),
        ("", "durin", ""),
        // a junk swap/collapse is never shown as filler, so the real fix
        // is not vetoed; the longer word wins a cost tie; a rare swap or
        // collapse yields to the everyday word one letter longer
        ("", "lve", "love"),
        ("", "stll", "still"),
        ("", "flm", "film"),
        ("", "pge", "page"),
        ("", "aain", "again"),
        ("", "tme", "time"),
        ("", "mke", "make"),
        ("", "rund", "round"),
        ("", "leat", "least"),
        ("", "maye", "maybe"),
        ("", "acces", "access"),
        ("", "meber", "member"),
        ("", "moent", "moment"),
        ("", "taes", "takes"),
        ("", "ters", "terms"),
        ("", "yasss", ""),
        ("", "ewww", ""),
        ("", "finna", ""),
        ("", "tmr", ""),
        // review 2026-09-18: a real low-band word is never split; a swap
        // typo of a contraction keeps the contraction; the right half of
        // a split is never re-apostrophised; a bounce still yields to the
        // top-tier far reading; g-drop yields to a strong pair; codes
        // after a number still expand; a far slip never beats an adjacent
        // one; the three-word shadow needs three words
        ("", "irate", ""),
        ("was", "irate", ""),
        ("", "imparts", ""),
        ("", "wonton", ""),
        ("", "catn", "can't"),
        ("", "wsant", "wasn't"),
        ("", "wnot", "won't"),
        ("", "youer", "you're"),
        ("", "thast", "that's"),
        ("", "onits", "on its"),
        ("", "inits", "in its"),
        ("", "ttis", "this"),
        ("", "hhat", "that"),
        ("", "hhey", "hey"),
        ("", "notin", "not in"),
        ("", "backin", "back in"),
        ("", "signin", "sign in"),
        ("3", "ppl", "people"),
        ("5", "pls", "please"),
        ("8", "tmrw", "tomorrow"),
        ("", "kuck", "luck"),
        ("", "jeen", "keen"),
        ("", "orane", "orange"),
        ("", "toally", "totally"),
        ("", "beauy", "beauty"),
        ("", "nite", ""),
        ("", "aight", ""),
        ("", "planing", ""),
        ("", "ment", "meant"),
        ("or", "ill", "I'll"),
        ("again", "ill", "I'll"),
        // shorthand (hunt 2026-09-18): a typed initialism never expands, a
        // code one slip from a common word is suggestion-only, the kept
        // codes still expand
        ("500", "BC", ""),
        ("london", "NW", ""),
        ("or", "np", ""),
        ("to", "bf", ""),
        ("lets", "gl", ""),
        ("oh", "ffs", ""),
        ("", "idk", "I don't know"),
        ("", "tbh", "to be honest"),
        // adjacency is symmetric and one plausible slip is one bucket
        // (2026-09-18): a common word beats a rare one inside it
        ("", "vfry", "very"),
        ("", "frm", "from"),
        ("", "belng", "being"),
        ("", "cdan", "can"),
        ("", "rfally", "really"),
        ("", "golng", "going"),
        ("", "thbe", "the"),
        ("", "frday", "friday"),
        ("", "htel", "hotel"),
        // must never flip: AU vocab, his project names, abbreviations
        ("", "arvo", ""),
        ("", "doona", ""),
        ("", "smoko", ""),
        ("", "uni", ""),
        ("", "nbn", ""),
        ("", "tor", ""),
        ("", "gst", ""),
        ("", "Crake", ""),
        ("", "Fieldmark", ""),
        ("", "Antigravity", ""),
        ("are", "your", ""),
        ("you", "to", ""),
        ("in", "their", ""),
        ("want", "an", ""),
        ("must", "he", ""),
        ("a", "cot", ""),
        ("the", "hen", ""),
        // form/from are cross-hand adjacent transpositions of each other
        // and BOTH valid: timing-based transposition features must never
        // flip a typed valid word (review of loop 7/21, 2026-08-28).
        ("", "form", ""),
        ("the", "form", ""),
        ("", "from", ""),
    ];
    let mut failures = Vec::new();
    for &(prev, typed, expected) in cases {
        let r = e.suggest_with_context(typed, prev, 5);
        let flip = r
            .candidates
            .iter()
            .find(|c| c.is_autocorrect && !c.word.eq_ignore_ascii_case(typed));
        match (expected, flip) {
            ("", None) => {}
            ("", Some(c)) => failures.push(format!("[{prev}] {typed} must not flip, got {}", c.word)),
            (want, Some(c)) if c.word == want => {}
            (want, got) => failures.push(format!(
                "[{prev}] {typed} -> expected {want}, got {:?}",
                got.map(|c| c.word.clone())
            )),
        }
    }
    // merge repairs on real data
    if e.merge_repair("oft", "rn").as_deref() != Some("often") {
        failures.push("oft+rn -> often".into());
    }
    if e.merge_repair("ni", "stakes").as_deref() != Some("mistakes") {
        failures.push("ni+stakes -> mistakes".into());
    }
    // the homophone survivors still fire
    let r = e.suggest_with_context("then", "more", 5);
    if !r.candidates.first().is_some_and(|c| c.word == "than" && c.is_autocorrect) {
        failures.push("more then -> than".into());
    }
    assert!(failures.is_empty(), "\n{}", failures.join("\n"));
}

/// The engine's own wrong auto-commit can be learned back as a personal
/// "correction" (+15 per record). On a real phone six of those lifted
/// "lille" to 255, above "like", and the doubled-letter guess won again.
/// The guess must yield on shipped-corpus evidence, not on learned boosts
/// (field report 2026-09-13).
#[test]
fn learned_boost_cannot_resurrect_doubled_guess() {
    let mut e = engine();
    // The boost alone (what the old loop produced), not a recorded personal
    // mapping: eight EXPLICIT user corrections "lile" -> "lille" would now
    // rightly win through the personal-correction stage.
    for _ in 0..8 {
        e.learn_and_boost_word("lille");
    }
    // The boost is now capped at corpus + 30 (170 -> 200); before the cap
    // eight boosts reached 255. Either way the guess must not win.
    assert!(e.trie.get_frequency("lille").unwrap_or(0) > e.corpus_freq("lille"), "boost path lifts the learned freq");
    let r = e.suggest_with_context("lile", "", 4);
    let first = r.candidates.first().expect("candidates");
    assert!(
        first.word == "like" && first.is_autocorrect,
        "lile must still auto-commit like, got {:?}",
        r.candidates.iter().map(|c| format!("{}{}", c.word, if c.is_autocorrect { "*" } else { "" })).collect::<Vec<_>>()
    );
}

/// Latency floor-guard on the real assets: the suggest pipeline has grown
/// many gated stages (rescues, punch-throughs, homophone arbitration) and
/// nothing was watching the clock. The bound is deliberately generous —
/// debug builds, CI jitter — it exists to catch a CATASTROPHIC regression
/// (an ungated trie scan), not to tune against. Baseline printed for eyes.
#[test]
fn suggest_latency_stays_sane() {
    let e = engine();
    // Mix of shapes: exact words with context (rescue path), typos with
    // completions (punch-through), merges, short tokens, long chains.
    let cases: &[(&str, &str)] = &[
        ("i", "an"),
        ("", "pleade"),
        ("", "thoriufhky"),
        ("the", "keyboard"),
        ("", "ti"),
        ("more", "then"),
        ("", "worls"),
        ("in", "their"),
        ("", "glidinf"),
        ("want", "to"),
    ];
    // warm-up
    for &(p, q) in cases {
        let _ = e.suggest_with_context(q, p, 5);
    }
    let start = std::time::Instant::now();
    let rounds = 200;
    for _ in 0..rounds {
        for &(p, q) in cases {
            let _ = e.suggest_with_context(q, p, 5);
        }
    }
    let per_call = start.elapsed() / (rounds * cases.len() as u32);
    eprintln!("suggest latency: {per_call:?} per call (debug build, real assets)");
    assert!(
        per_call < std::time::Duration::from_millis(20),
        "suggest took {per_call:?} per call — a stage lost its gate"
    );
}
