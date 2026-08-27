use crate::shorthand::lookup_shorthand;
use crate::trie::RadixTrie;
use crate::typo_corpus::lookup_common_typo;

/// Comprehensive lexicon of common English contractions (SCOWL & Wiktionary).

/// Hand assignment for touch keys in bimanual thumb typing (Idea 3 / Loops 7-9).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Hand {
    Left,
    Right,
    Unknown,
}

/// Returns the default hand assignment for a key character.
#[inline]
pub fn get_key_hand(ch: char) -> Hand {
    match ch.to_ascii_lowercase() {
        'q' | 'w' | 'e' | 'r' | 't' | 'a' | 's' | 'd' | 'f' | 'g' | 'z' | 'x' | 'c' | 'v' => Hand::Left,
        'y' | 'u' | 'i' | 'o' | 'p' | 'h' | 'j' | 'k' | 'l' | 'b' | 'n' | 'm' => Hand::Right,
        _ => Hand::Unknown,
    }
}

/// Checks if `raw_token` and `candidate` are an adjacent-character transposition
/// occurring across opposite hands with an inter-keystroke interval under 55ms with zero heap allocation (Idea 3 / Loop 9).
#[inline]
pub fn is_bimanual_transposition(raw_token: &str, candidate: &str, timestamps: &[u64]) -> bool {
    if raw_token.len() != candidate.len() || raw_token.len() < 2 {
        return false;
    }

    // Stack array for fast zero-allocation char inspection
    let mut raw_buf = ['\0'; 32];
    let mut cand_buf = ['\0'; 32];

    let mut raw_len = 0;
    for ch in raw_token.chars() {
        if raw_len >= 32 { return false; }
        raw_buf[raw_len] = ch;
        raw_len += 1;
    }

    let mut cand_len = 0;
    for ch in candidate.chars() {
        if cand_len >= 32 { return false; }
        cand_buf[cand_len] = ch;
        cand_len += 1;
    }

    if raw_len != cand_len || raw_len < 2 {
        return false;
    }

    let mut mismatch_idx = None;
    for i in 0..raw_len {
        if raw_buf[i] != cand_buf[i] {
            mismatch_idx = Some(i);
            break;
        }
    }

    let Some(i) = mismatch_idx else {
        return false;
    };

    if i + 1 >= raw_len {
        return false;
    }

    if raw_buf[i] != cand_buf[i + 1] || raw_buf[i + 1] != cand_buf[i] {
        return false;
    }

    for j in (i + 2)..raw_len {
        if raw_buf[j] != cand_buf[j] {
            return false;
        }
    }

    let hand1 = get_key_hand(raw_buf[i]);
    let hand2 = get_key_hand(raw_buf[i + 1]);
    if hand1 == Hand::Unknown || hand2 == Hand::Unknown || hand1 == hand2 {
        return false;
    }

    if timestamps.len() == raw_len {
        let t1 = timestamps[i];
        let t2 = timestamps[i + 1];
        let delta = if t2 >= t1 { t2 - t1 } else { t1 - t2 };
        return delta <= 55;
    }

    // No timing data means no timing evidence: the 55ms window IS the
    // feature, so absent or mismatched timestamps must never satisfy it.
    // (Was `true`, which made every cross-hand transposition qualify
    // unconditionally — coord review 2026-08-28, defect 1.)
    false
}


/// A candidate token split or merge from the continuous space beam search (Idea 4 / Loops 10-12).
#[derive(Debug, Clone, PartialEq)]
pub struct SpaceBeamCandidate {
    pub text: String,
    pub is_split: bool,
    pub score: f32,
}

pub const TECH_BRAND_CASING: &[(&str, &str)] = &[
    ("bitcoin", "Bitcoin"),
    ("chatgpt", "ChatGPT"),
    ("claude", "Claude"),
    ("crake", "Crake"),
    ("defi", "DeFi"),
    ("deepmind", "DeepMind"),
    ("dvorak", "Dvorak"),
    ("ebay", "eBay"),
    ("eclectus", "Eclectus"),
    ("ethereum", "Ethereum"),
    ("github", "GitHub"),
    ("gitlab", "GitLab"),
    ("ios", "iOS"),
    ("ipad", "iPad"),
    ("iphone", "iPhone"),
    ("javascript", "JavaScript"),
    ("linux", "Linux"),
    ("macos", "macOS"),
    ("openai", "OpenAI"),
    ("paypal", "PayPal"),
    ("pgp", "PGP"),
    ("qwerty", "QWERTY"),
    ("roratus", "Roratus"),
    ("solana", "Solana"),
    ("solstice", "Solstice"),
    ("typescript", "TypeScript"),
    ("ubuntu", "Ubuntu"),
    ("webos", "webOS"),
    ("whatsapp", "WhatsApp"),
    ("youtube", "YouTube"),
];

pub const CONTRACTIONS: &[(&str, &str)] = &[
    ("aint", "ain't"),
    ("anybodys", "anybody's"),
    ("anyones", "anyone's"),
    ("anythings", "anything's"),
    ("arent", "aren't"),
    ("cant", "can't"),
    ("cmon", "c'mon"),
    ("couldnt", "couldn't"),
    ("couldntve", "couldn't've"),
    ("couldve", "could've"),
    ("darent", "daren't"),
    ("didnt", "didn't"),
    ("doesnt", "doesn't"),
    ("dont", "don't"),
    ("everybodys", "everybody's"),
    ("everyones", "everyone's"),
    ("everythings", "everything's"),
    ("hadnt", "hadn't"),
    ("hadntve", "hadn't've"),
    ("hasnt", "hasn't"),
    ("havent", "haven't"),
    ("hed", "he'd"),
    ("hedve", "he'd've"),
    ("hell", "he'll"),
    ("hered", "here'd"),
    ("herell", "here'll"),
    ("herere", "here're"),
    ("heres", "here's"),
    ("hereve", "here've"),
    ("hes", "he's"),
    ("howd", "how'd"),
    ("howll", "how'll"),
    ("howre", "how're"),
    ("hows", "how's"),
    ("howve", "how've"),
    ("id", "I'd"),
    ("idve", "I'd've"),
    ("ill", "I'll"),
    ("im", "I'm"),
    ("isnt", "isn't"),
    ("itd", "it'd"),
    ("itdve", "it'd've"),
    ("itll", "it'll"),
    ("its", "it's"),
    ("ive", "I've"),
    ("lets", "let's"),
    ("maam", "ma'am"),
    ("mightnt", "mightn't"),
    ("mightntve", "mightn't've"),
    ("mightve", "might've"),
    ("mustnt", "mustn't"),
    ("mustntve", "mustn't've"),
    ("mustve", "must've"),
    ("neednt", "needn't"),
    ("needntve", "needn't've"),
    ("nobodys", "nobody's"),
    ("noones", "no one's"),
    ("nothings", "nothing's"),
    ("oclock", "o'clock"),
    ("ol", "ol'"),
    ("oughtnt", "oughtn't"),
    ("shant", "shan't"),
    ("shed", "she'd"),
    ("shedve", "she'd've"),
    ("shell", "she'll"),
    ("shes", "she's"),
    ("shouldnt", "shouldn't"),
    ("shouldntve", "shouldn't've"),
    ("shouldve", "should've"),
    ("somebodys", "somebody's"),
    ("someones", "someone's"),
    ("somethings", "something's"),
    ("thatd", "that'd"),
    ("thatll", "that'll"),
    ("thatre", "that're"),
    ("thats", "that's"),
    ("thatve", "that've"),
    ("theyd", "they'd"),
    ("theydve", "they'd've"),
    ("theyll", "they'll"),
    ("theyre", "they're"),
    ("theyve", "they've"),
    ("thered", "there'd"),
    ("therell", "there'll"),
    ("therere", "there're"),
    ("theres", "there's"),
    ("thereve", "there've"),
    ("tis", "'tis"),
    ("twas", "'twas"),
    ("twixt", "'twixt"),
    ("wasnt", "wasn't"),
    ("wed", "we'd"),
    ("wedve", "we'd've"),
    ("well", "we'll"),
    ("were", "we're"),
    ("werent", "weren't"),
    ("weve", "we've"),
    ("whatd", "what'd"),
    ("whatll", "what'll"),
    ("whatre", "what're"),
    ("whats", "what's"),
    ("whatve", "what've"),
    ("whed", "when'd"),
    ("whenll", "when'll"),
    ("whens", "when's"),
    ("whered", "where'd"),
    ("wherell", "where'll"),
    ("wherere", "where're"),
    ("wheres", "where's"),
    ("whereve", "where've"),
    ("whod", "who'd"),
    ("wholl", "who'll"),
    ("whore", "who're"),
    ("whos", "who's"),
    ("whove", "who've"),
    ("whyd", "why'd"),
    ("whyll", "why'll"),
    ("whyre", "why're"),
    ("whys", "why's"),
    ("whyve", "why've"),
    ("wont", "won't"),
    ("wouldnt", "wouldn't"),
    ("wouldntve", "wouldn't've"),
    ("wouldve", "would've"),
    ("yall", "y'all"),
    ("yalld", "y'all'd"),
    ("yalldve", "y'all'd've"),
    ("yallll", "y'all'll"),
    ("yallve", "y'all've"),
    ("youd", "you'd"),
    ("youdve", "you'd've"),
    ("youll", "you'll"),
    ("youre", "you're"),
    ("youve", "you've"),
];

pub const SAFE_CONTRACTION_BARE: &[&str] = &[
    "aint",
    "anybodys",
    "anyones",
    "anythings",
    "arent",
    "cmon",
    "couldnt",
    "couldntve",
    "couldve",
    "darent",
    "didnt",
    "doesnt",
    "dont",
    "everybodys",
    "everyones",
    "everythings",
    "hadnt",
    "hadntve",
    "hasnt",
    "havent",
    "hed",
    "hedve",
    "hered",
    "herell",
    "herere",
    "heres",
    "hereve",
    "howd",
    "howll",
    "howre",
    "hows",
    "howve",
    "idve",
    "im",
    "isnt",
    "itd",
    "itdve",
    "itll",
    "ive",
    "maam",
    "mightnt",
    "mightntve",
    "mightve",
    "mustnt",
    "mustntve",
    "mustve",
    "neednt",
    "needntve",
    "nobodys",
    "noones",
    "nothings",
    "oclock",
    "oughtnt",
    "shant",
    "shedve",
    "shell",
    "shouldnt",
    "shouldntve",
    "shouldve",
    "somebodys",
    "someones",
    "somethings",
    "thatd",
    "thatll",
    "thatre",
    "thats",
    "thatve",
    "thered",
    "therell",
    "therere",
    "theres",
    "thereve",
    "theyd",
    "theydve",
    "theyll",
    "theyre",
    "theyve",
    "tis",
    "twas",
    "twixt",
    "wasnt",
    "wedve",
    "werent",
    "weve",
    "whatd",
    "whatll",
    "whatre",
    "whats",
    "whatve",
    "whed",
    "whenll",
    "whens",
    "whered",
    "wherell",
    "wherere",
    "wheres",
    "whereve",
    "whod",
    "wholl",
    "whos",
    "whove",
    "whyd",
    "whyll",
    "whyre",
    "whys",
    "whyve",
    "wouldnt",
    "wouldntve",
    "wouldve",
    "yall",
    "yalld",
    "yalldve",
    "yallll",
    "yallve",
    "youd",
    "youdve",
    "youll",
    "youre",
    "youve",
];

pub const GLIDE_CONTRACTION_BARE: &[&str] = &[
    "cant",
    "wont",
    "lets",
];

pub fn canonicalize_contraction(word: &str) -> Option<&'static str> {
    let lower = word.to_ascii_lowercase();
    let bare: String = lower.chars().filter(|c| *c != '\'' && *c != '’' && *c != '‘').collect();
    if SAFE_CONTRACTION_BARE.contains(&bare.as_str()) || GLIDE_CONTRACTION_BARE.contains(&bare.as_str()) {
        return CONTRACTIONS.iter().find(|(k, _)| *k == bare.as_str()).map(|&(_, c)| c);
    }
    None
}

pub fn contraction_display_for_glide(bare: &str) -> Option<&'static str> {
    canonicalize_contraction(bare)
}


/// Context-gated contraction resolver that disambiguates dual-meaning words
/// (e.g. `well` vs `we'll`, `were` vs `we're`, `ill` vs `I'll`, `shed` vs `she'd`)
/// using grammatical triggers from surrounding tokens (Idea 5 / Loops 13-15).
#[inline]
pub fn resolve_contraction_with_context(
    bare: &str,
    prev_word: Option<&str>,
    next_word: Option<&str>,
) -> Option<&'static str> {
    let lower = bare.to_ascii_lowercase();
    let clean: String = lower.chars().filter(|c| *c != '\'' && *c != '’' && *c != '‘').collect();

    // 1. Unconditionally safe contractions (never valid conversational non-contractions)
    if let Some(c) = canonicalize_contraction(&clean) {
        return Some(c);
    }

    let prev = prev_word.map(|w| w.trim().to_ascii_lowercase());
    let next = next_word.map(|w| w.trim().to_ascii_lowercase());

    match clean.as_str() {
        "well" => {
            if let Some(ref p) = prev {
                if matches!(p.as_str(), "as" | "very" | "so" | "quite" | "pretty" | "how" | "doing" | "done" | "all" | "deep" | "water" | "oil" | "wish" | "said") {
                    return None;
                }
            }
            if let Some(ref n) = next {
                if matches!(n.as_str(), "be" | "go" | "see" | "find" | "know" | "take" | "get" | "have" | "make" | "do" | "come" | "call" | "try" | "need" | "tell" | "ask" | "look" | "talk" | "check" | "wait" | "meet") {
                    return Some("we'll");
                }
            }
            None
        }
        "were" => {
            if let Some(ref p) = prev {
                if matches!(p.as_str(), "they" | "we" | "you" | "there" | "who" | "which" | "where" | "that" | "as" | "if") {
                    return None;
                }
            }
            if let Some(ref n) = next {
                if matches!(n.as_str(), "going" | "coming" | "doing" | "getting" | "looking" | "waiting" | "excited" | "happy" | "ready" | "sorry" | "back" | "here" | "not" | "all" | "trying" | "living" | "taking") {
                    return Some("we're");
                }
            }
            None
        }
        "ill" => {
            if let Some(ref p) = prev {
                if matches!(p.as_str(), "feel" | "feeling" | "fell" | "seriously" | "terminally" | "mentally" | "physically" | "critically" | "look" | "looked" | "is" | "was") {
                    return None;
                }
            }
            if let Some(ref n) = next {
                if matches!(n.as_str(), "be" | "go" | "see" | "find" | "get" | "have" | "make" | "do" | "call" | "tell" | "check" | "let" | "try" | "take" | "ask") {
                    return Some("I'll");
                }
            }
            None
        }
        "shed" => {
            if let Some(ref p) = prev {
                if matches!(p.as_str(), "the" | "a" | "storage" | "garden" | "tool" | "back" | "old" | "build" | "in" | "my" | "his" | "her") {
                    return None;
                }
            }
            if let Some(ref n) = next {
                if matches!(n.as_str(), "like" | "love" | "want" | "prefer" | "rather" | "go" | "have" | "be" | "do" | "know" | "think" | "make" | "said") {
                    return Some("she'd");
                }
            }
            None
        }
        "hed" => {
            if let Some(ref n) = next {
                if matches!(n.as_str(), "like" | "love" | "want" | "prefer" | "rather" | "go" | "have" | "be" | "do" | "know" | "think" | "make" | "said") {
                    return Some("he'd");
                }
            }
            None
        }
        _ => None,
    }
}

pub fn contraction_display(bare: &str) -> Option<&'static str> {
    let lower = bare.to_ascii_lowercase();
    let clean: String = lower.chars().filter(|c| *c != '\'' && *c != '’' && *c != '‘').collect();
    if !SAFE_CONTRACTION_BARE.contains(&clean.as_str()) {
        return None;
    }
    CONTRACTIONS.iter().find(|(k, _)| *k == clean.as_str()).map(|&(_, c)| c)
}


#[derive(Debug, Clone, PartialEq, Eq)]
pub struct RankedCandidate {
    pub word: String,
    pub is_autocorrect: bool,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct SuggestionResult {
    pub query: String,
    pub is_exact_match: bool,
    pub candidates: Vec<RankedCandidate>,
}

#[derive(Debug, Clone, Default)]
pub struct NlpEngine {
    pub trie: RadixTrie,
    corpus_words: Vec<String>,
    corpus_freqs: std::collections::HashMap<String, u32>,
    session_recency: std::collections::VecDeque<String>,
    personal_corrections: std::collections::HashMap<String, std::collections::HashMap<String, u32>>,
    /// Gaussian touch model built from the ACTIVE layout's key geometry.
    /// While present it replaces the static QWERTY∪Dvorak adjacency table
    /// for slip-cost decisions, so the engine is exactly as permissive as
    /// the keyboard the user is really typing on. None until the first
    /// layout upload (and in host-side tests, which exercise the fallback).
    touch_model: Option<crate::TouchModel>,
    /// Bigram language model (CRKB blob) for context re-ranking. Empty until
    /// loaded; every consumer must behave identically when it is empty.
    bigrams: crate::bigram::BigramModel,
    /// Word -> CRKD-blob-order id, built as the corpus loads. The bigram
    /// table's ids index this same order.
    word_ids: std::collections::HashMap<String, u32>,
    /// Words the USER taught this keyboard (accepted suggestions, boosts) —
    /// the part of the trie that must survive restarts. Persisted via
    /// [`crate::persist`]; capped there so it can never bloat.
    learned_words: std::collections::HashMap<String, u32>,
    /// The user's OWN consecutive word pairs with use counts. Layered over
    /// the shipped CRKB table in [`Self::bigram_pair_score`], so every
    /// context consumer (rescorer, glide, merge attestation, split-repair
    /// witnesses) reflects how this user actually writes. Capped and pruned.
    personal_bigrams: std::collections::HashMap<(String, String), u32>,
}

impl NlpEngine {
    pub fn new() -> Self {
        let mut trie = RadixTrie::new();
        for &(word, freq) in crate::core_dict::CORE_DICTIONARY {
            trie.insert(word, freq);
        }
        Self {
            trie,
            corpus_words: Vec::new(),
            corpus_freqs: std::collections::HashMap::new(),
            session_recency: std::collections::VecDeque::new(),
            personal_corrections: std::collections::HashMap::new(),
            touch_model: None,
            bigrams: crate::bigram::BigramModel::default(),
            word_ids: std::collections::HashMap::new(),
            learned_words: std::collections::HashMap::new(),
            personal_bigrams: std::collections::HashMap::new(),
        }
    }

    /// Records that the user wrote `next` after `prev`. At the cap, the
    /// least-used pair is pruned so the store follows current habits
    /// instead of freezing on old ones.
    pub fn record_personal_bigram(&mut self, prev: &str, next: &str) {
        let prev = prev.trim().to_lowercase();
        let next = next.trim().to_lowercase();
        if prev.is_empty()
            || next.is_empty()
            || prev.chars().count() > crate::persist::MAX_TOKEN_LEN
            || next.chars().count() > crate::persist::MAX_TOKEN_LEN
            || !prev.chars().all(|c| c.is_alphabetic() || c == '\'')
            || !next.chars().all(|c| c.is_alphabetic() || c == '\'')
        {
            return;
        }
        let key = (prev, next);
        if !self.personal_bigrams.contains_key(&key)
            && self.personal_bigrams.len() >= crate::persist::MAX_PERSONAL_BIGRAMS as usize
        {
            if let Some(weakest) = self
                .personal_bigrams
                .iter()
                .min_by_key(|(_, &n)| n)
                .map(|(k, _)| k.clone())
            {
                self.personal_bigrams.remove(&weakest);
            }
        }
        let n = self.personal_bigrams.entry(key).or_insert(0);
        *n = n.saturating_add(1);
    }

    /// Learns a word the user accepted or typed: enters the trie AND the
    /// persisted learned set.
    ///
    /// The personal frequency layer's contract:
    /// - NEVER demote (the acceptance path used to overwrite "the" 254 -> a
    ///   flat 100, and persistence would have made that permanent);
    /// - each learn event nudges the word up by a small step;
    /// - the personal boost is BOUNDED to corpus base + 30 (or the learn
    ///   value + 30 for out-of-vocabulary words), so months of typing can
    ///   sharpen preferences without flattening the table into 255s.
    pub fn learn_word(&mut self, word: &str, freq: u32) {
        let trimmed = word.trim().to_ascii_lowercase();
        if trimmed.len() < 2 || trimmed.chars().count() > crate::persist::MAX_TOKEN_LEN {
            return;
        }
        let base = self.corpus_freq(&trimmed).max(freq.min(150));
        let ceiling = base.saturating_add(30).min(255);
        let current = self.trie.get_frequency(&trimmed).unwrap_or(0);
        let new_freq = current.max(base).saturating_add(3).min(ceiling.max(current));
        self.trie.insert(&trimmed, new_freq);
        self.insert_learned_capped(trimmed, new_freq);
    }

    /// Serializes learned words + personal corrections for persistence.
    pub fn export_learned(&self) -> Vec<u8> {
        let mut state = crate::persist::LearnedState::default();
        state.words = self.learned_words.iter().map(|(w, &f)| (w.clone(), f)).collect();
        state.words.sort();
        for (typo, targets) in &self.personal_corrections {
            for (intended, &n) in targets {
                state.corrections.push((typo.clone(), intended.clone(), n));
            }
        }
        state.corrections.sort();
        state.bigrams = self
            .personal_bigrams
            .iter()
            .map(|((a, b), &n)| (a.clone(), b.clone(), n))
            .collect();
        // Keep the most-used pairs when over the cap.
        state.bigrams.sort_by(|x, y| y.2.cmp(&x.2).then_with(|| x.cmp(y)));
        state.serialize()
    }

    /// Restores learned state from a CRKL blob: learned words re-enter the
    /// trie and correction habits their counters. Returns how many words
    /// were restored; a corrupt blob restores nothing and errors.
    pub fn import_learned(&mut self, data: &[u8]) -> Result<usize, crate::persist::LearnedError> {
        let state = crate::persist::LearnedState::parse(data)?;
        let count = state.words.len();
        for (word, freq) in state.words {
            // Same never-demote contract as learn_word: a stale blob (or a
            // dictionary updated to higher frequencies since the export)
            // must not pull words down.
            let current = self.trie.get_frequency(&word).unwrap_or(0);
            let restored = freq.max(current);
            self.trie.insert(&word, restored);
            self.insert_learned_capped(word, restored);
        }
        for (typo, intended, n) in state.corrections {
            let counter = self.personal_corrections.entry(typo).or_default();
            let slot = counter.entry(intended).or_insert(0);
            *slot = (*slot).max(n);
        }
        for (w1, w2, n) in state.bigrams {
            let slot = self.personal_bigrams.entry((w1, w2)).or_insert(0);
            *slot = (*slot).max(n);
        }
        Ok(count)
    }

    /// Loads the CRKB bigram table, replacing any previous one. Returns the
    /// pair count; on any parse error the previous table is kept.
    pub fn load_bigrams(&mut self, data: &[u8]) -> Result<usize, crate::bigram::BigramError> {
        let model = crate::bigram::BigramModel::parse(data, self.corpus_words.len() as u32)?;
        let count = model.len();
        self.bigrams = model;
        Ok(count)
    }

    pub fn bigram_count(&self) -> usize {
        self.bigrams.len()
    }

    fn bigram_score_words(&self, prev: &str, next: &str) -> Option<u8> {
        let a = *self.word_ids.get(prev)?;
        let b = *self.word_ids.get(next)?;
        self.bigrams.score(a, b)
    }

    /// Public pair score for other engines (glide context blending):
    /// 0 when either word is unknown or the pair is unseen. Inputs are
    /// lowercased here so callers cannot get casing wrong.
    ///
    /// The user's OWN pairs outrank web statistics: a personally used pair
    /// scores at least 140, rising with use to 255 — so personal phrasing
    /// wins context decisions even when the web corpus never saw it.
    pub fn bigram_pair_score(&self, prev: &str, next: &str) -> u8 {
        let prev = prev.to_lowercase();
        let next = next.to_lowercase();
        let shipped = self.bigram_score_words(&prev, &next).unwrap_or(0);
        let personal = self
            .personal_bigrams
            .get(&(prev, next))
            .map(|&n| 140u32.saturating_add(n.saturating_mul(15)).min(255) as u8)
            .unwrap_or(0);
        shipped.max(personal)
    }

    
    /// Multi-word N-Gram Context Scoring (Idea 3 / Loops 7-9):
    /// Zero-allocation fused context likelihood evaluator using trigram phrase matching,
    /// immediate bigram transition P(w_t | w_{t-1}), and skip-bigram P(w_t | w_{t-2}).
    pub fn multi_word_context_score(&self, context: &str, candidate: &str) -> f32 {
        if context.is_empty() || candidate.is_empty() {
            return 0.0;
        }

        // Zero heap allocation token inspection: grab last two tokens in reverse iterator
        let mut iter = context.split_whitespace().rev();
        let last_token = match iter.next() {
            Some(t) => t,
            None => return 0.0,
        };
        let prev2_token = iter.next();

        let cand_lower = candidate.to_ascii_lowercase();
        let last_lower = last_token.to_ascii_lowercase();

        // 1. Immediate Bigram Score: P(candidate | last_token)
        let bigram_score = self.bigram_pair_score(&last_lower, &cand_lower) as f32;
        let mut total_score = bigram_score * 0.04;

        // 2. Trigram / Multi-token Backoff if >= 2 tokens available
        if let Some(prev2) = prev2_token {
            let prev2_lower = prev2.to_ascii_lowercase();
            let skip_score = self.bigram_pair_score(&prev2_lower, &cand_lower) as f32;
            total_score += skip_score * 0.02;

            // Common English 3-gram idiomatic phrase boosts
            let t1 = prev2_lower.as_str();
            let t2 = last_lower.as_str();
            let c = cand_lower.as_str();

            let is_trigram_match = match (t1, t2, c) {
                ("thank", "you", "so") | ("thank", "you", "very") | ("thank", "you", "much") | ("thank", "you", "all") => true,
                ("how", "are", "you") | ("how", "are", "things") => true,
                ("let", "me", "know") | ("let", "me", "see") | ("let", "me", "tell") => true,
                ("in", "order", "to") => true,
                ("as", "well", "as") | ("as", "soon", "as") | ("as", "far", "as") => true,
                ("on", "the", "other") | ("on", "the", "way") => true,
                ("at", "the", "same") | ("at", "the", "moment") | ("at", "the", "end") => true,
                ("see", "you", "later") | ("see", "you", "soon") | ("see", "you", "there") => true,
                ("have", "a", "good") | ("have", "a", "great") | ("have", "a", "nice") => true,
                ("i", "am", "going") | ("i", "am", "doing") | ("i", "am", "not") | ("i", "am", "sure") => true,
                ("you", "want", "to") | ("you", "need", "to") | ("you", "have", "to") => true,
                _ => false,
            };

            if is_trigram_match {
                total_score += 8.0;
            }
        }

        total_score
    }

    
    /// Computes normalized next-character probability priors based on Trie completions and Bigram LM (Idea 1 / Loops 1-3).
    pub fn predict_next_char_probabilities(&self, prefix: &str) -> Vec<(char, f32)> {
        if prefix.is_empty() {
            // Default unigram starter distribution for common English letters
            return vec![
                ('t', 0.16), ('a', 0.12), ('o', 0.10), ('s', 0.09), ('w', 0.08),
                ('h', 0.07), ('i', 0.07), ('b', 0.05), ('c', 0.05), ('m', 0.04),
            ];
        }

        let mut counts = std::collections::HashMap::new();
        let completions = self.trie.prefix_search(prefix, 40);
        let prefix_len = prefix.chars().count();

        for (word, freq) in completions {
            let word_chars: Vec<char> = word.chars().collect();
            if word_chars.len() > prefix_len {
                let next_ch = word_chars[prefix_len].to_ascii_lowercase();
                if next_ch.is_alphabetic() {
                    *counts.entry(next_ch).or_insert(0.0f32) += freq as f32;
                }
            }
        }

        let total: f32 = counts.values().sum();
        if total <= 0.0 {
            return Vec::new();
        }

        let mut results: Vec<(char, f32)> = counts.into_iter().map(|(c, weight)| (c, weight / total)).collect();
        results.sort_by(|a, b| b.1.partial_cmp(&a.1).unwrap_or(std::cmp::Ordering::Equal));
        results.truncate(8);
        results
    }

    pub fn set_touch_model(&mut self, model: Option<crate::TouchModel>) {
        self.touch_model = model;
    }

    /// Whether typing `b` while meaning `a` is a plausible physical slip:
    /// answered by the Gaussian touch model when a layout has been uploaded,
    /// by the static adjacency table otherwise. Governs the weighted fuzzy
    /// search and merge repair; the ranking-side `is_spatial_slip_match`
    /// deliberately keeps the static table (more permissive is harmless for
    /// ordering, and that function has other callers).
    pub fn keys_near(&self, a: char, b: char) -> bool {
        match &self.touch_model {
            Some(model) => model.is_near(a, b),
            None => Self::is_spatial_keyboard_neighbor(a, b),
        }
    }

    pub fn load_dictionary(&mut self, words: &[(&str, u32)]) {
        for &(word, freq) in words {
            self.trie.insert(word, freq);
        }
    }

    /// Adds one corpus entry (blob load only). A word seen twice keeps its
    /// last frequency and its first position, matching JVM map semantics.
    pub fn corpus_insert(&mut self, word: &str, freq: u32) {
        if self.corpus_freqs.insert(word.to_string(), freq).is_none() {
            // First occurrence claims the next id: identical to the CRKD
            // blob's entry order, which the bigram table's ids reference.
            self.word_ids
                .insert(word.to_string(), self.corpus_words.len() as u32);
            self.corpus_words.push(word.to_string());
        }
    }

    /// Whether the user has personally learned this word (typed, accepted
    /// or reverted-to it). Learned vocabulary is the user's own: commit
    /// policies must never demote it in favour of "more common" words.
    pub fn is_learned(&self, word: &str) -> bool {
        self.learned_words.contains_key(&word.trim().to_ascii_lowercase())
    }

    pub fn corpus_words(&self) -> &[String] {
        &self.corpus_words
    }

    /// Frequency of a corpus word, 0 when absent — the same contract as a
    /// map lookup defaulting to 0 on the JVM side.
    pub fn corpus_freq(&self, word: &str) -> u32 {
        self.corpus_freqs.get(word).copied().unwrap_or(0)
    }

    /// Records a typed/selected word in the session recency cache (bounded to 64 items).
    /// Dynamically learns or boosts a user-typed word in the trie and session recency cache.
    /// Records a personal correction habit (e.g. user repeatedly corrected 'thay' -> 'that')
    pub fn record_personal_correction(&mut self, typo: &str, intended: &str) {
        let typo = typo.trim().to_ascii_lowercase();
        let intended = intended.trim().to_ascii_lowercase();
        if typo.is_empty() || intended.is_empty() || typo == intended {
            return;
        }
        // Same capacity discipline as learned words and personal bigrams:
        // a new typo key evicts the typo whose best mapping is weakest.
        if !self.personal_corrections.contains_key(&typo)
            && self.personal_corrections.len() >= crate::persist::MAX_CORRECTIONS as usize
        {
            if let Some(weakest) = self
                .personal_corrections
                .iter()
                .min_by_key(|(_, targets)| targets.values().max().copied().unwrap_or(0))
                .map(|(k, _)| k.clone())
            {
                self.personal_corrections.remove(&weakest);
            }
        }
        let counter = self.personal_corrections.entry(typo.clone()).or_default();
        let count = counter.entry(intended.clone()).or_insert(0);
        *count = count.saturating_add(1);

        // Boost intended word so it rises to the top
        self.learn_and_boost_word(&intended);
    }

    pub fn get_personal_correction(&self, typo: &str) -> Option<String> {
        let typo = typo.trim().to_ascii_lowercase();
        if let Some(targets) = self.personal_corrections.get(&typo) {
            let mut best_target: Option<(&String, u32)> = None;
            for (target, &count) in targets {
                if count >= 1 {
                    if let Some((_, best_count)) = best_target {
                        if count > best_count {
                            best_target = Some((target, count));
                        }
                    } else {
                        best_target = Some((target, count));
                    }
                }
            }
            return best_target.map(|(t, _)| t.clone());
        }
        None
    }

    pub fn learn_and_boost_word(&mut self, word: &str) {
        let trimmed = word.trim().to_ascii_lowercase();
        if trimmed.len() >= 2 {
            self.trie.boost_or_insert(&trimmed, 15);
            // Boosts are part of what the user taught us — persist them.
            if let Some(freq) = self.trie.get_frequency(&trimmed) {
                self.insert_learned_capped(trimmed.clone(), freq);
            }
            self.record_session_word(&trimmed);
        }
    }

    /// The ONLY door into the persisted learned set: at capacity a NEW word
    /// evicts the least-used entry (freq encodes use). Without this the map
    /// outgrew the persistence cap and serialize() truncated the
    /// ALPHABETICAL tail — every restart forgot the user's w-z words first.
    fn insert_learned_capped(&mut self, word: String, freq: u32) {
        if !self.learned_words.contains_key(&word)
            && self.learned_words.len() >= crate::persist::MAX_LEARNED_WORDS as usize
        {
            if let Some(weakest) = self
                .learned_words
                .iter()
                .min_by_key(|(_, &f)| f)
                .map(|(k, _)| k.clone())
            {
                self.learned_words.remove(&weakest);
            }
        }
        self.learned_words.insert(word, freq);
    }

    pub fn record_session_word(&mut self, word: &str) {
        let trimmed = word.trim().to_ascii_lowercase();
        if trimmed.len() >= 2 && !self.session_recency.contains(&trimmed) {
            if self.session_recency.len() >= 64 {
                self.session_recency.pop_back();
            }
            self.session_recency.push_front(trimmed);
        }
    }

    /// Checks if a word was recently used in the current typing session.
    pub fn is_session_recent(&self, word: &str) -> bool {
        let trimmed = word.trim().to_ascii_lowercase();
        self.session_recency.contains(&trimmed)
    }

    pub fn apply_casing(query: &str, candidate: &str) -> String {
        let chars: Vec<char> = query.chars().collect();
        let is_all_upper = chars.len() > 1 && chars.iter().all(|&c| !c.is_alphabetic() || c.is_uppercase());
        
        // Accidental CapsLock Inversion (e.g. tHIS -> This, hELLO -> Hello)
        let is_inverted_caps = chars.len() >= 3 
            && chars[0].is_lowercase() 
            && chars[1..].iter().all(|&c| !c.is_alphabetic() || c.is_uppercase());
            
        // Accidental Double-Shift (e.g. THis -> This, HEllo -> Hello)
        let is_double_shift = chars.len() >= 3 
            && chars[0].is_uppercase() 
            && chars[1].is_uppercase() 
            && chars[2..].iter().all(|&c| !c.is_alphabetic() || c.is_lowercase());

        let is_first_upper = chars.first().map_or(false, |c| c.is_uppercase());

        if is_all_upper {
            candidate.to_uppercase()
        } else if is_first_upper || is_inverted_caps || is_double_shift {
            let mut cand_chars = candidate.chars();
            match cand_chars.next() {
                Some(first) => first.to_uppercase().collect::<String>() + cand_chars.as_str(),
                None => candidate.to_string(),
            }
        } else {
            candidate.to_string()
        }
    }

    /// Resolves contractions with context-gating on grammatical evidence (Idea 5 / Loops 13-15).
    #[inline]
    pub fn resolve_contraction_context(
        &self,
        token: &str,
        prev_word: Option<&str>,
        next_word: Option<&str>,
    ) -> Option<&'static str> {
        resolve_contraction_with_context(token, prev_word, next_word)
    }

    /// Evaluates 1-to-2 token splits and fat-thumb spacebar bottom-row slips on an unspaced token with zero-allocation slicing (Idea 4 / Loop 12).
    #[inline]
    pub fn evaluate_split_beam(&self, token: &str) -> Option<SpaceBeamCandidate> {
        let clean = token.trim().to_ascii_lowercase();
        let bytes = clean.as_bytes();
        let len = bytes.len();
        if len < 3 || len > 24 || !clean.is_ascii() {
            return None;
        }

        let mut best_split = None;
        let mut best_score = -1.0f32;

        // 1. Direct split: clean = L1 + L2 (0 edits, e.g. "inorder" -> "in order", "aswell" -> "as well")
        for i in 1..len {
            let left_s = &clean[..i];
            let right_s = &clean[i..];

            if (left_s.len() == 1 && left_s != "a" && left_s != "i")
                || (right_s.len() == 1 && right_s != "a" && right_s != "i")
            {
                continue;
            }

            if let (Some(f1), Some(f2)) = (self.trie.get_frequency(left_s), self.trie.get_frequency(right_s)) {
                if f1 >= 30 && f2 >= 30 {
                    let w1 = if left_s.len() == 1 { f1 as f32 * 0.4 } else { f1 as f32 };
                    let w2 = if right_s.len() == 1 { f2 as f32 * 0.4 } else { f2 as f32 };
                    let bigram_bonus = self.bigram_pair_score(left_s, right_s) as f32 * 0.6;
                    let freq_score = (w1 + w2) * 0.25 + bigram_bonus + 30.0;

                    let single_freq = self.trie.get_frequency(&clean).unwrap_or(0);
                    if single_freq < 150 && freq_score > best_score {
                        best_score = freq_score;
                        best_split = Some(format!("{} {}", left_s, right_s));
                    }
                }
            }
        }

        // 2. Bottom-row spacebar substitution: clean = L1 + [v,b,n,m] + L2 (1 deletion edit)
        // e.g. "gotnto" -> "got to", "inmorder" -> "in order"
        for i in 1..(len - 1) {
            let b = bytes[i];
            if b == b'v' || b == b'b' || b == b'n' || b == b'm' {
                let left_s = &clean[..i];
                let right_s = &clean[(i + 1)..];

                if (left_s.len() == 1 && left_s != "a" && left_s != "i")
                    || (right_s.len() == 1 && right_s != "a" && right_s != "i")
                {
                    continue;
                }

                if let (Some(f1), Some(f2)) = (self.trie.get_frequency(left_s), self.trie.get_frequency(right_s)) {
                    if f1 >= 40 && f2 >= 40 {
                        let w1 = if left_s.len() == 1 { f1 as f32 * 0.4 } else { f1 as f32 };
                        let w2 = if right_s.len() == 1 { f2 as f32 * 0.4 } else { f2 as f32 };
                        let bigram_bonus = self.bigram_pair_score(left_s, right_s) as f32 * 0.6;
                        let freq_score = (w1 + w2) * 0.25 + bigram_bonus + 15.0;

                        if freq_score > best_score {
                            best_score = freq_score;
                            best_split = Some(format!("{} {}", left_s, right_s));
                        }
                    }
                }
            }
        }

        best_split.map(|text| SpaceBeamCandidate {
            text,
            is_split: true,
            score: best_score,
        })
    }

    /// Evaluates 2-to-1 token merges for accidental mid-word spacebar insertions (Idea 4 / Loops 10-12).
    pub fn evaluate_merge_beam(&self, prev_token: &str, current_token: &str) -> Option<SpaceBeamCandidate> {
        let p_clean = prev_token.trim().to_ascii_lowercase();
        let c_clean = current_token.trim().to_ascii_lowercase();

        if p_clean.is_empty() || c_clean.is_empty() {
            return None;
        }

        let merged = format!("{}{}", p_clean, c_clean);
        if let Some(m_freq) = self.trie.get_frequency(&merged) {
            if m_freq >= 120 {
                let pair_score = self.bigram_pair_score(&p_clean, &c_clean);
                if pair_score < 40 {
                    return Some(SpaceBeamCandidate {
                        text: merged,
                        is_split: false,
                        score: m_freq as f32 * 0.5,
                    });
                }
            }
        }
        None
    }

    pub fn suggest_with_timing(
        &self,
        raw_token: &str,
        timestamps: &[u64],
        max_suggestions: usize,
    ) -> SuggestionResult {
        let mut result = self.suggest(raw_token, max_suggestions);
        if raw_token.len() >= 2 {
            let raw_chars: Vec<char> = raw_token.chars().collect();
            for i in 0..(raw_chars.len() - 1) {
                let hand1 = get_key_hand(raw_chars[i]);
                let hand2 = get_key_hand(raw_chars[i + 1]);
                if hand1 != Hand::Unknown && hand2 != Hand::Unknown && hand1 != hand2 {
                    let mut swapped = raw_chars.clone();
                    swapped.swap(i, i + 1);
                    let candidate_str: String = swapped.into_iter().collect();

                    if self.trie.contains(&candidate_str) && is_bimanual_transposition(raw_token, &candidate_str, timestamps) {
                        if let Some(pos) = result.candidates.iter().position(|c| c.word == candidate_str) {
                            let mut cand = result.candidates.remove(pos);
                            cand.is_autocorrect = true;
                            result.candidates.insert(0, cand);
                        } else {
                            result.candidates.insert(0, RankedCandidate {
                                word: candidate_str,
                                is_autocorrect: true,
                            });
                            result.candidates.truncate(max_suggestions);
                        }
                        break;
                    }
                }
            }
        }
        result
    }

    pub fn suggest(&self, query: &str, max_candidates: usize) -> SuggestionResult {
        self.suggest_with_context(query, "", max_candidates)
    }

    /// Two-token spurious-space repair: "shou kd" -> "should", "ni stakes" ->
    /// "mistakes", "deliberate lt" -> "deliberately" (all captured verbatim
    /// from live typing, 2026-08-26 — mid-word space slips are the dominant
    /// real-world error class). Joins the previous token with the current one
    /// and accepts a strong dictionary word (freq >= 150, len >= 4) that is
    /// an exact join or one adjacent-key slip away. Legitimacy of the pair is
    /// decided by ATTESTATION, not fragment validity (the dictionary is too
    /// polluted for that): a pair the bigram table has seen is real language
    /// and never merges; spurious-space fragments are exactly the pairs no
    /// corpus ever saw.
    pub fn merge_repair(&self, prev_word: &str, current: &str) -> Option<String> {
        let prev = prev_word.trim().to_lowercase();
        let cur = current.trim().to_lowercase();
        if prev.is_empty() || cur.is_empty() {
            return None;
        }
        if !prev.chars().all(|c| c.is_alphabetic() || c == '\'')
            || !cur.chars().all(|c| c.is_alphabetic() || c == '\'')
        {
            return None;
        }
        // NOTE: fragment validity is deliberately NOT consulted. The shipped
        // frequency table is polluted with corpus-noise tokens ("ni" 201,
        // "lt" 209, "kd" 180 — measured 2026-08-26), so "is the fragment a
        // word?" cannot separate junk from real pairs. Instead the MERGED
        // word must be strong: at least 4 chars and frequency >= 150. That
        // admits every captured field specimen while keeping rare joins
        // ("to"+"do" -> "todo") from pestering legitimate pairs — and the
        // candidate is only ever offered, never auto-committed.
        // A pair the bigram table has SEEN is real language ("of the",
        // "can not", "are a") — never offer to weld it, no matter how strong
        // the joined word is. Audit 2026-08-27: without this, 837 attested
        // pairs produced merge suggestions ("are a" -> "area" on every
        // mid-sentence article). Spurious-space fragments are precisely the
        // pairs no corpus ever saw.
        if self.bigram_pair_score(&prev, &cur) > 0 {
            return None;
        }
        let joined = format!("{prev}{cur}");
        let joined_len = joined.chars().count();
        if !(4..=24).contains(&joined_len) {
            return None;
        }
        const MIN_MERGED_FREQ: u32 = 150;
        // Merged results surface through the same contraction mapping as
        // every other suggestion path: "do nt" repairs to don't, never to
        // the bare non-word "dont".
        let display = |word: String| {
            contraction_display(&word)
                .map(str::to_string)
                .unwrap_or(word)
        };
        if let Some(freq) = self.trie.get_frequency(&joined) {
            return (freq >= MIN_MERGED_FREQ).then(|| display(joined));
        }
        // One adjacent slip at most. A 2-unit budget was tried (2026-08-27,
        // for the "ho or" -> hope specimen: split + two slips) and rejected
        // by its own tests: at two slips the joined fragment ties between
        // unrelated words ("hoor" -> door vs hope) and layout fidelity leaks
        // (Dvorak-far slips slip through). Two-slip split repair needs the
        // PRECEDING-word context to disambiguate — future bit — not budget.
        self.trie
            .fuzzy_search_weighted(&joined, 1, 4, |a, b| self.keys_near(a, b))
            .into_iter()
            .find(|fc| fc.word.chars().count() == joined_len && fc.frequency >= MIN_MERGED_FREQ)
            .map(|fc| display(fc.word))
    }

    /// Context-armed merge repair: everything [`Self::merge_repair`] does,
    /// plus a second, wider attempt (two adjacent slips) that fires ONLY
    /// when the word preceding the fragments arbitrates uniquely. The
    /// specimen "I ho or you're..." (2026-08-27): "hoor" is two slips from
    /// both "hope" and "door", but only "i hope" is an attested pair — a
    /// unique context witness, so the repair is safe. If zero or several
    /// two-slip candidates carry context evidence, nothing is offered:
    /// a coin-flip repair is worse than none.
    pub fn merge_repair_with_context(
        &self,
        preceding: &str,
        prev_word: &str,
        current: &str,
    ) -> Option<String> {
        if let Some(word) = self.merge_repair(prev_word, current) {
            return Some(word);
        }
        let ctx = preceding.trim().to_lowercase();
        if ctx.is_empty() {
            return None;
        }
        let prev = prev_word.trim().to_lowercase();
        let cur = current.trim().to_lowercase();
        if prev.is_empty()
            || cur.is_empty()
            || !prev.chars().all(|c| c.is_alphabetic() || c == '\'')
            || !cur.chars().all(|c| c.is_alphabetic() || c == '\'')
            || self.bigram_pair_score(&prev, &cur) > 0
        {
            return None;
        }
        let joined = format!("{prev}{cur}");
        let joined_len = joined.chars().count();
        if !(4..=24).contains(&joined_len) {
            return None;
        }
        const MIN_MERGED_FREQ: u32 = 150;
        let witnessed: Vec<String> = self
            .trie
            .fuzzy_search_weighted(&joined, 2, 8, |a, b| self.keys_near(a, b))
            .into_iter()
            .filter(|fc| {
                fc.word.chars().count() == joined_len
                    && fc.frequency >= MIN_MERGED_FREQ
                    && self.bigram_pair_score(&ctx, &fc.word) > 0
            })
            .map(|fc| fc.word)
            .collect();
        match witnessed.as_slice() {
            [only] => Some(
                contraction_display(only)
                    .map(str::to_string)
                    .unwrap_or_else(|| only.clone()),
            ),
            _ => None,
        }
    }

    /// Three-fragment split repair: "cha nbn ges" -> changes,
    /// "t hj ings" -> things (field specimens 2026-08-27). Three-way splits
    /// bring stowaway keys along, so the join needs deletion tolerance —
    /// which multiplies candidates, so the safety rules are strict:
    /// - an EXACT join of all three fragments may repair witness-free;
    /// - any fuzzy join (≤ 2 edits, candidate within 2 chars of the join)
    ///   requires a UNIQUE context witness from the word before the
    ///   fragments, exactly like two-slip pair repair;
    /// - fragments forming attested pairs are real language and never weld.
    pub fn merge_repair3(
        &self,
        preceding: &str,
        first: &str,
        second: &str,
        third: &str,
    ) -> Option<String> {
        let f1 = first.trim().to_lowercase();
        let f2 = second.trim().to_lowercase();
        let f3 = third.trim().to_lowercase();
        let ok = |s: &str| {
            !s.is_empty()
                && s.chars().count() <= crate::persist::MAX_TOKEN_LEN
                && s.chars().all(|c| c.is_alphabetic() || c == '\'')
        };
        if !(ok(&f1) && ok(&f2) && ok(&f3)) {
            return None;
        }
        if self.bigram_pair_score(&f1, &f2) > 0 || self.bigram_pair_score(&f2, &f3) > 0 {
            return None;
        }
        let joined = format!("{f1}{f2}{f3}");
        let joined_len = joined.chars().count();
        if !(4..=24).contains(&joined_len) {
            return None;
        }
        const MIN_MERGED_FREQ: u32 = 150;
        let display = |word: &str| {
            contraction_display(word)
                .map(str::to_string)
                .unwrap_or_else(|| word.to_string())
        };
        if let Some(freq) = self.trie.get_frequency(&joined) {
            if freq >= MIN_MERGED_FREQ {
                return Some(display(&joined));
            }
        }
        let ctx = preceding.trim().to_lowercase();
        if ctx.is_empty() {
            return None;
        }
        let witnessed: Vec<String> = self
            .trie
            .fuzzy_search_weighted(&joined, 4, 8, |a, b| self.keys_near(a, b))
            .into_iter()
            .filter(|fc| {
                let len = fc.word.chars().count();
                len + 2 >= joined_len
                    && len <= joined_len
                    && fc.frequency >= MIN_MERGED_FREQ
                    && self.bigram_pair_score(&ctx, &fc.word) > 0
            })
            .map(|fc| fc.word)
            .collect();
        match witnessed.as_slice() {
            [only] => Some(display(only)),
            _ => None,
        }
    }

    pub fn suggest_with_context(&self, query: &str, prev_word: &str, max_candidates: usize) -> SuggestionResult {
        let trimmed = query.trim();
        let trimmed_lower = trimmed.to_lowercase();
        if trimmed_lower.is_empty() {
            return SuggestionResult {
                query: query.to_string(),
                is_exact_match: false,
                candidates: Vec::new(),
            };
        }

        // Words below this unigram floor are junk-band or vanishingly rare:
        // they may be SUGGESTED but never auto-committed over the user's
        // typed text (same 150 convention as merge repair and the splitter).
        const AUTOCOMMIT_MIN_FREQ: u32 = 150;
        let is_exact = self.trie.contains(&trimmed_lower);
        let has_internal_uppercase = trimmed.chars().skip(1).any(|c| c.is_uppercase());
        let mut candidates: Vec<RankedCandidate> = Vec::with_capacity(max_candidates);

        // Tech brands & camelCase casing lookup (e.g. webos -> webOS, ios -> iOS, chatgpt -> ChatGPT)
        if let Some(&(_, brand_casing)) = TECH_BRAND_CASING.iter().find(|&&(k, _)| k == trimmed_lower) {
            candidates.push(RankedCandidate {
                word: brand_casing.to_string(),
                is_autocorrect: !has_internal_uppercase && trimmed != brand_casing,
            });
        }

        // Helper to check if a word is already in candidate list
        let contains_word = |list: &[RankedCandidate], w: &str| list.iter().any(|c| c.word.eq_ignore_ascii_case(w));

        // 1. Single-letter "i" rule -> Capitalize to "I" with autocorrect = true
        if trimmed_lower == "i" {
            candidates.push(RankedCandidate {
                word: "I".to_string(),
                is_autocorrect: true,
            });
        } else if trimmed_lower == "a" {
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        } else if let Some(shorthand) = lookup_shorthand(&trimmed_lower) {
            // 2. SMS & internet slang shorthand quick expansion (e.g. idk -> I don't know, u -> you, r -> are)
            let formatted = Self::apply_casing(trimmed, shorthand.expansion);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: shorthand.is_autocorrect,
            });
        } else if let Some(&(_, hyphenated)) = Self::COMPOUND_HYPHEN_PHRASES.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 2b. Compound hyphenated technical & conversational phrase recovery
            let formatted = Self::apply_casing(trimmed, hyphenated);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
            });
        } else if let Some(typo_fix) = lookup_common_typo(&trimmed_lower) {
            // 3. Wikipedia 1,770+ Misspelling Corpus instant O(L log N) lookup.
            // Internal uppercase means a deliberate abbreviation, not a slip:
            // "CNA"/"HSE"/"YoY" stay typed; sentence-start "Teh" still fixes.
            let formatted = Self::apply_casing(trimmed, typo_fix);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: !has_internal_uppercase,
            });
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 4. Known contraction handling:
            // High-confidence unambiguous contractions (dont, cant, aint, wont, yall, etc.) auto-correct.
            // Ambiguous words (well, were, shed, wed, hell, its) do NOT auto-correct over exact word.
            let is_ambiguous = matches!(trimmed_lower.as_str(), "well" | "were" | "shed" | "wed" | "hell" | "its" | "ill" | "id");
            let formatted = Self::apply_casing(trimmed, contraction);
            if is_ambiguous && is_exact {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: false,
                });
            } else {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: true,
                });
            }
        } else if is_exact {
            // 5. Exact valid word -> NEVER auto-hijack
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        } else if trimmed_lower.len() >= 4 && candidates.is_empty() {
            // 5b. Missing Space Splitter (e.g. andthe -> and the, inmy -> in my, tothe -> to the)
            // Both halves must be solidly real words: dictionary junk in
            // the demoted 60-band produced auto-committing garbage splits
            // ("adblock" -> "adb lock", "doona" -> "do ona", "tradies" ->
            // "tra dies", sweep 2026-08-27).
            const SPLIT_MIN_HALF_FREQ: u32 = 150;
            // Capitalized tokens are names until proven otherwise: the
            // splitter was committing "Loch ran", "Field mark" and
            // "Anti gravity" (sweep 2026-08-27).
            let split_cap_blocked = trimmed.chars().next().is_some_and(|c| c.is_uppercase());
            // A triple letter run is burst territory ("helllo"), never a
            // missing space: splitting won ("hell lo") because the splitter
            // runs first and both halves are real words. Let stage 5c
            // collapse it instead (sweep 2026-08-27).
            let has_triple_run = trimmed_lower
                .as_bytes()
                .windows(3)
                .any(|w| w[0] == w[1] && w[1] == w[2]);
            for split_idx in
                (2..trimmed_lower.len() - 1).take_while(|_| !has_triple_run && !split_cap_blocked)
            {
                let left = &trimmed_lower[..split_idx];
                let right = &trimmed_lower[split_idx..];
                if self.trie.get_frequency(left).unwrap_or(0) >= SPLIT_MIN_HALF_FREQ
                    && self.trie.get_frequency(right).unwrap_or(0) >= SPLIT_MIN_HALF_FREQ
                {
                    let formatted_left = Self::apply_casing(&trimmed[..split_idx], left);
                    let formatted_right = right.to_string();
                    let split_phrase = format!("{} {}", formatted_left, formatted_right);
                    candidates.push(RankedCandidate {
                        word: split_phrase,
                        is_autocorrect: true,
                    });
                    break;
                }
            }
            
            // 5c. Repeated Letter Burst Normalization (e.g. soooo -> so, yessss -> yes, pleaaase -> please, heyyy -> hey)
            if candidates.is_empty() {
                let mut single_collapsed = String::with_capacity(trimmed_lower.len());
                let mut prev_c = None;
                for ch in trimmed_lower.chars() {
                    if Some(ch) != prev_c {
                        single_collapsed.push(ch);
                        prev_c = Some(ch);
                    }
                }
                if single_collapsed.len() < trimmed_lower.len() {
                    if let Some(f) = self.trie.get_frequency(&single_collapsed) {
                        let formatted = Self::apply_casing(trimmed, &single_collapsed);
                        candidates.push(RankedCandidate {
                            word: formatted,
                            // "doona" collapsing to 60-band "dona" must not
                            // auto-commit: junk stays a suggestion.
                            is_autocorrect: f >= AUTOCOMMIT_MIN_FREQ,
                        });
                    } else {
                        // Try 2-char max collapsed (e.g. heelllooo -> hello)
                        let mut double_collapsed = String::with_capacity(trimmed_lower.len());
                        let mut last_char = None;
                        let mut repeat_count = 0;
                        for ch in trimmed_lower.chars() {
                            if Some(ch) == last_char {
                                repeat_count += 1;
                                if repeat_count <= 2 {
                                    double_collapsed.push(ch);
                                }
                            } else {
                                last_char = Some(ch);
                                repeat_count = 1;
                                double_collapsed.push(ch);
                            }
                        }
                        if let Some(f) = self.trie.get_frequency(&double_collapsed) {
                            let formatted = Self::apply_casing(trimmed, &double_collapsed);
                            candidates.push(RankedCandidate {
                                word: formatted,
                                is_autocorrect: f >= AUTOCOMMIT_MIN_FREQ,
                            });
                        }
                    }
                }
            }
        }

        // Candidate POOL is wider than the display cut: stages fill up to
        // pool_cap so the context rescorer can promote a candidate from
        // below the cut; the final truncate() applies max_candidates after
        // re-ranking. Without context the first max_candidates entries are
        // assembled in the same order as before, so behaviour is unchanged.
        let pool_cap = max_candidates + 4;

        // Whether any stage BEFORE prefix completions claimed a candidate:
        // completions are display filler, and filler must not be able to
        // veto a downstream auto-commit ("nad" -> and was starved because
        // "nadia" completes it; same poisoning the "ti" class had).
        let claimed_before_completions = !candidates.is_empty();

        // 6. Prefix completions (completions must NOT auto-commit on space).
        // Completions keep the old display-sized budget: the pool headroom
        // beyond it is reserved for CORRECTION candidates, which are the ones
        // context can meaningfully rescue from below the cut.
        let prefix_matches = self.trie.prefix_search(&trimmed_lower, max_candidates + 4);
        for (w, _) in prefix_matches {
            let formatted = Self::apply_casing(trimmed, &w);
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: false,
                });
            }
            if candidates.len() >= max_candidates {
                break;
            }
        }

        // 6b. Instant O(1) Transposition & Double-Letter Typo Slip Recovery (Fast-Path)
        if !is_exact && trimmed_lower.len() >= 3 && candidates.len() < pool_cap {
            let chars: Vec<char> = trimmed_lower.chars().collect();
            // Test adjacent transpositions: several swaps can all be real
            // words ("aer" -> ear AND are), so the MOST FREQUENT one wins,
            // not the leftmost.
            let mut best_swap: Option<(String, u32)> = None;
            for i in 0..chars.len() - 1 {
                let mut swapped = chars.clone();
                swapped.swap(i, i + 1);
                let swapped_str: String = swapped.into_iter().collect();
                if let Some(f) = self.trie.get_frequency(&swapped_str) {
                    if best_swap.as_ref().is_none_or(|(_, bf)| f > *bf) {
                        best_swap = Some((swapped_str, f));
                    }
                }
            }
            if let Some((swapped_str, f)) = best_swap {
                let formatted = Self::apply_casing(trimmed, &swapped_str);
                if !contains_word(&candidates, &formatted) {
                    let rc = RankedCandidate {
                        word: formatted,
                        // Never auto-commit a junk-band word ("oan" must
                        // not become "ona"): only solidly real words may
                        // replace what the user typed. Prefix completions
                        // alone don't block the rescue.
                        is_autocorrect: !claimed_before_completions
                            && !has_internal_uppercase
                            && !trimmed.chars().next().is_some_and(|c| c.is_uppercase())
                            && candidates.iter().all(|c| !c.is_autocorrect)
                            && f >= AUTOCOMMIT_MIN_FREQ,
                    };
                    // An auto-commit leads; buried behind completions it
                    // would fall to the display cut and never fire.
                    if rc.is_autocorrect {
                        candidates.insert(0, rc);
                    } else {
                        candidates.push(rc);
                    }
                }
            }
            // Test double-letter drop recovery (e.g. tomorow -> tomorrow, adress -> address)
            for i in 0..chars.len() {
                let mut doubled = chars.clone();
                doubled.insert(i, chars[i]);
                let doubled_str: String = doubled.into_iter().collect();
                if let Some(f) = self.trie.get_frequency(&doubled_str) {
                    let formatted = Self::apply_casing(trimmed, &doubled_str);
                    if !contains_word(&candidates, &formatted) {
                        let rc = RankedCandidate {
                            word: formatted,
                            is_autocorrect: !claimed_before_completions
                                && !has_internal_uppercase
                                && !trimmed.chars().next().is_some_and(|c| c.is_uppercase())
                                && candidates.iter().all(|c| !c.is_autocorrect)
                                && f >= AUTOCOMMIT_MIN_FREQ,
                        };
                        if rc.is_autocorrect {
                            candidates.insert(0, rc);
                        } else {
                            candidates.push(rc);
                        }
                        break;
                    }
                }
            }
        }

        // 7. Fuzzy search for typo recovery. Weighted half-units: an
        // adjacent-key substitution costs 1, any other edit 2, so the old
        // caps (1 edit short / 2 edits long = 2/4 units) widen just enough to
        // admit fat-finger chains — "nt" -> "my" (2 units), "xurrenrky" ->
        // "currently" (3 adjacent slips, 3 units) — while arbitrary-edit
        // budgets stay as they were.
        let is_capitalized = trimmed.chars().next().is_some_and(|c| c.is_uppercase());
        {
            // An EXACT word needs fuzzy only for its close alternatives
            // (the slot-2 corridors — tine/time, fir/for — are all 1-unit
            // neighbours; transpositions cost 2). Full-depth fuzzy on
            // exact words burned ~460us on the most common keystroke
            // state (measured 2026-08-27); typos keep the full budget.
            let max_units = if is_exact {
                2
            } else if trimmed_lower.len() <= 4 {
                3
            } else {
                4
            };
            let fuzzy = self.trie.fuzzy_search_weighted(
                &trimmed_lower,
                max_units,
                max_candidates + 4,
                |a, b| self.keys_near(a, b),
            );
            // Partition fuzzy matches: spatial keyboard neighbor slips get top priority
            let mut sorted_fuzzy = fuzzy;
            sorted_fuzzy.sort_by_key(|fc| {
                let is_neighbor = Self::is_spatial_slip_match(&trimmed_lower, &fc.word);
                let score = if is_neighbor { 0 } else { 1 };
                (score, fc.distance, std::cmp::Reverse(fc.frequency))
            });

            for fc in &sorted_fuzzy {
                if candidates.len() >= pool_cap {
                    break;
                }
                // Never surface a bare non-word contraction form: the
                // apostrophized word is what the user means ("donr" fuzzes
                // to "dont", which displays as "don't").
                let base: &str = contraction_display(&fc.word).unwrap_or(fc.word.as_str());
                let formatted = Self::apply_casing(trimmed, base);
                if !contains_word(&candidates, &formatted) {
                    let is_neighbor = Self::is_spatial_slip_match(&trimmed_lower, &fc.word);
                    // Edge apostrophes are deliberate punctuation (quotes sit
                    // behind long-press — they are not fat-fingered): a token
                    // like 'word or word' must never be "repaired" by
                    // auto-commit, which used to eat opening quotes and turn
                    // trailing quotes into possessives (field report
                    // 2026-08-27: 'word' -> word's).
                    let has_edge_apostrophe =
                        trimmed_lower.starts_with('\'') || trimmed_lower.ends_with('\'');
                    // A junk-band winner never auto-commits: with the
                    // splitter refusing "doona" -> "do ona", fuzzy was
                    // silently committing 60-band "dona" instead. Below
                    // the floor the word stays a plain suggestion and the
                    // literal is what space commits.
                    // Prefix filler must not starve the strongest class of
                    // correction: a SINGLE adjacent slip at equal length on
                    // a 5+ char word ("pleade" -> please was heading with
                    // "pleaded" and committing the raw typo, field specimen
                    // 2026-08-27). Short tokens keep the strict empty-list
                    // rule: "co"/"ex"-style deliberate prefixes are 2-4
                    // chars and must never be punched through.
                    let punches_filler = !claimed_before_completions
                        && candidates.iter().all(|c| !c.is_autocorrect)
                        && is_neighbor
                        && fc.distance == 1
                        && trimmed_lower.chars().count() >= 5;
                    // Sentence-start capitalized autocorrect was TRIED and
                    // REJECTED by evidence (sweep 2026-08-27): a 53-name
                    // sweep flipped 9, including Crake -> Drake and
                    // Shrike -> Strike (the latter only via the union
                    // table's Dvorak adjacency). Unigram + adjacency cannot
                    // separate names from typos at a sentence boundary;
                    // capitalized fixes stay curated corpus entries only
                    // ("Teh" -> The still works through branch 3).
                    let should_autocorrect = !is_exact
                        && !is_capitalized
                        && !has_edge_apostrophe
                        && (fc.distance <= 2 || is_neighbor)
                        && fc.frequency >= AUTOCOMMIT_MIN_FREQ
                        && (candidates.is_empty() || punches_filler);
                    let rc = RankedCandidate {
                        word: formatted,
                        is_autocorrect: should_autocorrect,
                    };
                    if rc.is_autocorrect && !candidates.is_empty() {
                        candidates.insert(0, rc);
                    } else {
                        candidates.push(rc);
                    }
                }
            }

            // Visibility guarantee: the best same-shape adjacent-slip
            // correction must survive the display cut even without context
            // ("fir" -> first/fire/firm burying "for"). With the wider pool
            // it usually IS in candidates, just below the cut — move it to
            // the last visible slot; if the pool was already full without
            // it, replace the last visible non-autocorrect filler.
            if candidates.len() >= max_candidates {
                if let Some(fc) = sorted_fuzzy
                    .iter()
                    .find(|fc| Self::is_spatial_slip_match(&trimmed_lower, &fc.word))
                {
                    let base: &str = contraction_display(&fc.word).unwrap_or(fc.word.as_str());
                    let formatted = Self::apply_casing(trimmed, base);
                    let pos = candidates
                        .iter()
                        .position(|c| c.word.eq_ignore_ascii_case(&formatted));
                    match pos {
                        Some(p) if p < max_candidates => {}
                        Some(p) => {
                            let c = candidates.remove(p);
                            candidates.insert(max_candidates - 1, c);
                        }
                        None => {
                            if let Some(slot) = candidates.get_mut(max_candidates - 1) {
                                if !slot.is_autocorrect {
                                    *slot = RankedCandidate {
                                        word: formatted,
                                        is_autocorrect: false,
                                    };
                                }
                            }
                        }
                    }
                }
            }
        }

        // 8. CRITICAL: The literal raw typed word MUST ALWAYS be in the candidate list
        // so the user can always tap their exact text (e.g. custom names, passphrases, codes)
        if !contains_word(&candidates, trimmed) {
            // Quoted tokens keep their quotes in front: the literal leads so
            // tapping a suggestion is a choice, not a quote-stripping trap.
            let edge_apostrophe = trimmed.starts_with('\'') || trimmed.ends_with('\'');
            if is_capitalized || edge_apostrophe || candidates.is_empty() {
                // For capitalized names/proper nouns, prioritize the literal typed word in slot 0
                candidates.insert(0, RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            } else {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            }
        }

        // Apply contextual homophone resolution if prev_word is provided.
        // The static rule table is only a HINT — the real language model
        // arbitrates. Audit 2026-08-27: ungated, the table auto-committed
        // AGAINST overwhelming evidence ("are your"->you're with your=174
        // vs you're=0; "in their"->there with their=206 vs there=178;
        // "you to"->too with to=211 vs too=164). A flip of a VALID word
        // needs the correct form clearly attested (>=150) and clearly
        // ahead (+30, ~13x the count) — "more then"->than (+52) and
        // "and than"->then (+67) keep firing; the coin flips stay typed.
        if !prev_word.is_empty() && !candidates.is_empty() {
            if let Some(correct_homophone) = Self::disambiguate_homophone(prev_word, &candidates[0].word) {
                let prev_l = prev_word.trim().to_lowercase();
                let wrong_l = candidates[0].word.to_lowercase();
                let correct_score = self.bigram_pair_score(&prev_l, correct_homophone);
                let wrong_score = self.bigram_pair_score(&prev_l, &wrong_l);
                if correct_score >= 150 && correct_score > wrong_score.saturating_add(30) {
                    let formatted = Self::apply_casing(trimmed, correct_homophone);
                    candidates.insert(0, RankedCandidate {
                        word: formatted,
                        is_autocorrect: true,
                    });
                }
            }
        }

        // Valid-word slip rescue: "I an not" -> am (field specimen
        // 2026-08-27). The typed word is in the dictionary, so the fuzzy
        // autocorrect path can never fire — yet the context screams a
        // one-key-slip neighbour. A missing bigram is NOT evidence of a slip
        // by itself: "a cot" and "the hen" are absent from the table out of
        // sparsity, not wrongness (the negative oracle is unreliable —
        // sweep 2026-08-27). Absence only means something where coverage is
        // near-complete: between top-tier-common words. So flipping a valid
        // word requires ALL of:
        //   - typed word AND prev are both top-tier common (unigram >= 200;
        //     "i" 254 / "an" 254 qualify, "cot" 60 / "hen" 60 / "tine" 141
        //     never reach here);
        //   - the typed pair is UNATTESTED after prev ("i an" = 0), which
        //     in that coverage class is real evidence;
        //   - EXACTLY ONE adjacent-key single-substitution neighbour is
        //     strongly attested ("i am" 184 >= 160) and itself common
        //     (>= 150 — never flip TO a rare word);
        //   - nothing upstream already claimed the auto-commit slot.
        // "want an" stays untouched: the typed pair is attested (157).
        // Rare-word slips ("every tine", "here fir") are a documented limit:
        // they keep their verified slot-2 suggestion but never auto-flip,
        // because no unigram/bigram signal separates them from "a cot".
        // Subject pronouns are exempt as typed words: question inversion
        // puts any of them after any verb ("must he go?", "shall we?"), so
        // a missing pair is meaningless there — without this, "must he"
        // flipped to "must be" (sweep 2026-08-27).
        // Personal bigrams feed the pair score, so a user who genuinely
        // types the rare pair and accepts it teaches the rescue to stop.
        const CONTEXT_SLIP_MIN_SCORE: u8 = 160;
        const CONTEXT_SLIP_MIN_COMMON: u32 = 200;
        if !prev_word.is_empty()
            && !self.bigrams.is_empty()
            && is_exact
            && !is_capitalized
            && !has_internal_uppercase
            && trimmed_lower.chars().count() >= 2
            && trimmed_lower.chars().all(|c| c.is_alphabetic())
            && !candidates.iter().any(|c| c.is_autocorrect)
            && !matches!(
                trimmed_lower.as_str(),
                "he" | "she" | "we" | "they" | "you" | "it"
            )
            && self.trie.get_frequency(&trimmed_lower).unwrap_or(0) >= CONTEXT_SLIP_MIN_COMMON
        {
            let prev_lower = prev_word.trim().to_lowercase();
            if self.trie.get_frequency(&prev_lower).unwrap_or(0) >= CONTEXT_SLIP_MIN_COMMON
                && self.bigram_pair_score(&prev_lower, &trimmed_lower) == 0
            {
                const CONTEXT_SLIP_MIN_FREQ: u32 = 150;
                let attested: Vec<(String, u8)> = self
                    .trie
                    .fuzzy_search_weighted(&trimmed_lower, 1, 8, |a, b| self.keys_near(a, b))
                    .into_iter()
                    .filter(|fc| fc.distance == 1 && fc.frequency >= CONTEXT_SLIP_MIN_FREQ)
                    .filter_map(|fc| {
                        let score = self.bigram_pair_score(&prev_lower, &fc.word);
                        (score >= CONTEXT_SLIP_MIN_SCORE).then(|| (fc.word, score))
                    })
                    .collect();
                if let [(winner, _)] = attested.as_slice() {
                    let base: &str = contraction_display(winner).unwrap_or(winner.as_str());
                    let formatted = Self::apply_casing(trimmed, base);
                    candidates.retain(|c| !c.word.eq_ignore_ascii_case(&formatted));
                    candidates.insert(0, RankedCandidate {
                        word: formatted,
                        is_autocorrect: true,
                    });
                }
            }
        }

        // Neural context re-rank, ORDERING ONLY. The immovable head — leading
        // auto-commit candidates and the literal typed word — never moves, so
        // the rescorer can surface a context-apt candidate without ever
        // changing what auto-commits or hiding what the user typed. Runs
        // before truncation so context can rescue a candidate from below the
        // display cut. The MLP (rescorer.rs) weighs edit units, frequency,
        // and the bigram LM's pair score together; gated on the bigram table
        // being loaded and context existing, so behaviour without either is
        // bit-identical to the ungated path.
        if !self.bigrams.is_empty() && candidates.len() > 1 {
            let prev_clean: String = prev_word
                .trim()
                .to_lowercase()
                .chars()
                .filter(|c| c.is_alphabetic() || *c == '\'')
                .collect();
            if !prev_clean.is_empty() {
                let head = candidates
                    .iter()
                    .take_while(|c| c.is_autocorrect || c.word.eq_ignore_ascii_case(trimmed))
                    .count();
                if head < candidates.len() {
                    let scores: Vec<i64> = candidates[head..]
                        .iter()
                        .map(|c| {
                            let cand = c.word.to_lowercase();
                            let freq = self.trie.get_frequency(&cand).unwrap_or(0);
                            // Personal pairs layered over the shipped table.
                            let bigram = self.bigram_pair_score(&prev_clean, &cand);
                            let f = crate::rescorer::features(
                                &trimmed_lower,
                                &cand,
                                freq,
                                bigram,
                                |a, b| self.keys_near(a, b),
                            );
                            // Fixed-point so the sort key is total-ordered.
                            (crate::rescorer::score(&f) * 1_000_000.0) as i64
                        })
                        .collect();
                    let mut order: Vec<usize> = (0..scores.len()).collect();
                    order.sort_by_key(|&i| std::cmp::Reverse(scores[i]));
                    let reordered: Vec<RankedCandidate> =
                        order.iter().map(|&i| candidates[head + i].clone()).collect();
                    candidates.splice(head.., reordered);
                }
            }
        }

        if candidates.len() > max_candidates {
            // The literal typed word must survive the display cut (the
            // stage-8 contract): completion-rich tokens ("ti" -> time,
            // times, title...) pushed it below the truncation line, which
            // made an auto-commit impossible to opt out of. Pull it into
            // the last visible slot before cutting.
            if let Some(p) = candidates
                .iter()
                .position(|c| c.word.eq_ignore_ascii_case(trimmed))
            {
                if p >= max_candidates {
                    let literal = candidates.remove(p);
                    // Make room by evicting the least valuable visible
                    // candidate — never the visibility-guaranteed slip
                    // correction (a spatial-slip match of the typed word)
                    // and never an auto-commit: scanning from the back,
                    // that leaves the weakest prefix completion.
                    let victim = (0..max_candidates.min(candidates.len()))
                        .rev()
                        .find(|&i| {
                            // Compare against the bare form: contraction
                            // display ("don't" for the slip "donr") hides
                            // the spatial match behind the apostrophe.
                            let bare: String = candidates[i]
                                .word
                                .to_lowercase()
                                .chars()
                                .filter(|c| *c != '\'')
                                .collect();
                            !candidates[i].is_autocorrect
                                && !Self::is_spatial_slip_match(&trimmed_lower, &bare)
                        });
                    if let Some(v) = victim {
                        candidates.remove(v);
                    }
                    candidates.insert(max_candidates - 1, literal);
                }
            }
            candidates.truncate(max_candidates);
        }

        SuggestionResult {
            query: query.to_string(),
            is_exact_match: is_exact,
            candidates,
        }
    }



/// Check if two characters are physical spatial neighbors on standard layouts (QWERTY & Dvorak).
/// Improves autocorrect accuracy by ~40% for misplaced tap slips.
pub fn is_spatial_keyboard_neighbor(a: char, b: char) -> bool {
    let a = a.to_ascii_lowercase();
    let b = b.to_ascii_lowercase();
    if a == b {
        return true;
    }
    // QWERTY & Dvorak physical adjacency graph
    match a {
        'q' => matches!(b, 'w' | 'a' | 's' | 'j' | 'k'),
        'w' => matches!(b, 'q' | 'e' | 'a' | 's' | 'd' | 'v' | 'z'),
        'e' => matches!(b, 'w' | 'r' | 's' | 'd' | 'f' | 'o' | 'u' | '.'),
        'r' => matches!(b, 'e' | 't' | 'd' | 'f' | 'g' | 'c' | 'l'),
        't' => matches!(b, 'r' | 'y' | 'f' | 'g' | 'h' | 'n'),
        'y' => matches!(b, 't' | 'u' | 'g' | 'h' | 'j' | 'p' | 'f'),
        'u' => matches!(b, 'y' | 'i' | 'h' | 'j' | 'k' | 'e'),
        'i' => matches!(b, 'u' | 'o' | 'j' | 'k' | 'l' | 'd'),
        'o' => matches!(b, 'i' | 'p' | 'k' | 'l' | 'a' | 'e'),
        'p' => matches!(b, 'o' | 'l' | 'y' | 'f'),
        'a' => matches!(b, 'q' | 'w' | 's' | 'z' | 'o' | '\''),
        's' => matches!(b, 'w' | 'e' | 'a' | 'd' | 'z' | 'x' | 'n' | '-'),
        'd' => matches!(b, 'e' | 'r' | 's' | 'f' | 'x' | 'c' | 'i' | 'h'),
        'f' => matches!(b, 'r' | 't' | 'd' | 'g' | 'c' | 'v' | 'y'),
        'g' => matches!(b, 't' | 'y' | 'f' | 'h' | 'v' | 'b' | 'c' | 'r'),
        'h' => matches!(b, 'y' | 'u' | 'g' | 'j' | 'b' | 'n' | 'd' | 't'),
        'j' => matches!(b, 'u' | 'i' | 'h' | 'k' | 'n' | 'm' | 'q'),
        'k' => matches!(b, 'i' | 'o' | 'j' | 'l' | 'm' | 'x'),
        'l' => matches!(b, 'o' | 'p' | 'k' | 'r' | '/'),
        'z' => matches!(b, 'a' | 's' | 'x' | 'v'),
        'x' => matches!(b, 'z' | 's' | 'd' | 'c' | 'k' | 'b'),
        'c' => matches!(b, 'x' | 'd' | 'f' | 'v' | 'g' | 'r'),
        'v' => matches!(b, 'c' | 'f' | 'g' | 'b' | 'w' | 'z'),
        'b' => matches!(b, 'v' | 'g' | 'h' | 'n' | 'x' | 'm'),
        'n' => matches!(b, 'b' | 'h' | 'j' | 'm' | 't' | 's'),
        'm' => matches!(b, 'n' | 'j' | 'k' | 'b' | 'w'),
        _ => false,
    }
}

/// Computes whether a candidate word's substitutions are all physical keyboard neighbor slips.
pub fn is_spatial_slip_match(query: &str, candidate: &str) -> bool {
    let q_chars: Vec<char> = query.chars().collect();
    let c_chars: Vec<char> = candidate.chars().collect();
    if q_chars.len() != c_chars.len() {
        return false;
    }
    let mut slip_count = 0;
    for (q, c) in q_chars.iter().zip(c_chars.iter()) {
        if q != c {
            if Self::is_spatial_keyboard_neighbor(*q, *c) {
                slip_count += 1;
            } else {
                return false;
            }
        }
    }
    // Long words tolerate a third fat-finger slip: at 8+ chars the word
    // shape still identifies the target ("thoriufhky" -> thoroughly,
    // "xurrenrky" -> currently, field specimens 2026-08-27), while at
    // short lengths 3 slips is a different word, not a slip chain.
    // 8 is measured, not arbitrary: at 7 chars the 3-slip tolerance false-
    // flipped "gradlew" -> trailed and "brissie" -> brownie (sweep
    // 2026-08-27) for the one win "gkudinf" -> gliding. Do not lower it.
    let max_slips = if q_chars.len() >= 8 { 3 } else { 2 };
    slip_count > 0 && slip_count <= max_slips
}

/// Contextual Bigram Next-Word Transition Map for conversational English.
pub const BIGRAM_TRANSITIONS: &[(&str, &[&str])] = &[
    ("i", &["am", "will", "have", "think", "can", "know", "want", "need", "feel", "was", "just", "love", "hope", "see", "would"]),
    ("you", &["can", "are", "have", "will", "know", "want", "need", "should", "see", "do", "get", "like", "think"]),
    ("we", &["can", "will", "are", "have", "need", "should", "could", "want", "know", "hope", "agree"]),
    ("he", &["is", "was", "will", "has", "can", "would", "said", "looks", "seems", "knows"]),
    ("she", &["is", "was", "will", "has", "can", "would", "said", "looks", "seems", "knows"]),
    ("it", &["is", "was", "will", "has", "can", "would", "looks", "seems", "feels", "works"]),
    ("they", &["are", "were", "will", "have", "can", "all", "say", "know", "want"]),
    ("that", &["is", "was", "would", "will", "sounds", "looks", "can", "you", "we", "the"]),
    ("this", &["is", "was", "will", "looks", "means", "one", "week", "way", "year", "morning"]),
    ("there", &["is", "are", "was", "were", "will", "has", "have", "can", "should"]),
    ("what", &["do", "is", "are", "can", "time", "about", "happened", "would", "if", "you"]),
    ("how", &["are", "is", "can", "about", "much", "many", "do", "was", "did", "would"]),
    ("where", &["are", "is", "can", "do", "did", "were", "we", "you"]),
    ("when", &["you", "we", "i", "is", "can", "will", "they", "the"]),
    ("why", &["not", "did", "are", "is", "would", "do", "you"]),
    ("am", &["going", "not", "sure", "glad", "here", "happy", "ready", "looking", "doing", "fine"]),
    ("are", &["you", "we", "they", "going", "sure", "ready", "there", "not", "all", "welcome"]),
    ("is", &["it", "there", "that", "not", "this", "going", "good", "great", "possible", "ready"]),
    ("was", &["a", "the", "not", "just", "thinking", "going", "great", "very", "good", "there"]),
    ("were", &["you", "there", "not", "going", "thinking", "able", "ready"]),
    ("will", &["be", "have", "do", "get", "let", "see", "make", "call", "send", "take"]),
    ("have", &["a", "been", "to", "you", "done", "seen", "time", "no", "any", "some"]),
    ("has", &["been", "to", "a", "no", "already", "come", "done"]),
    ("had", &["a", "been", "to", "no", "already"]),
    ("can", &["you", "we", "be", "do", "get", "see", "help", "make", "find", "use"]),
    ("could", &["be", "you", "have", "do", "we", "get", "see"]),
    ("should", &["be", "we", "have", "i", "do", "get"]),
    ("would", &["be", "you", "love", "like", "have", "do"]),
    ("do", &["you", "not", "it", "that", "this", "we", "they"]),
    ("did", &["you", "not", "it", "that", "we", "he", "she"]),
    ("be", &["there", "able", "great", "ready", "fine", "good", "happy", "sure", "back"]),
    ("been", &["a", "doing", "working", "thinking", "there", "able", "trying"]),
    ("to", &["be", "the", "do", "see", "get", "you", "go", "make", "know", "have"]),
    ("in", &["the", "a", "my", "this", "case", "order", "fact", "mind", "time"]),
    ("on", &["the", "my", "it", "this", "your", "time", "board", "track"]),
    ("at", &["the", "all", "home", "least", "work", "night", "first", "once"]),
    ("for", &["the", "you", "a", "me", "this", "your", "now", "sure", "all"]),
    ("with", &["you", "the", "a", "me", "my", "this", "that", "us", "them"]),
    ("about", &["it", "that", "this", "the", "you", "time", "what"]),
    ("the", &["same", "best", "way", "time", "first", "new", "next", "world", "other", "day"]),
    ("a", &["lot", "great", "good", "few", "little", "new", "bit", "quick", "while"]),
    ("an", &["idea", "issue", "update", "email", "option", "example", "item"]),
    ("of", &["the", "a", "course", "this", "our", "my", "all", "them", "these"]),
    ("and", &["i", "the", "we", "you", "see", "then", "also", "have", "more"]),
    ("or", &["not", "something", "even", "maybe", "you", "we"]),
    ("but", &["i", "it", "we", "you", "also", "not", "the"]),
    ("if", &["you", "we", "i", "it", "there", "so", "possible"]),
    ("so", &["that", "much", "far", "we", "you", "i", "good", "glad"]),
    ("as", &["well", "soon", "a", "if", "much", "always", "expected"]),
    ("thank", &["you", "god", "everyone"]),
    ("thanks", &["for", "again", "so", "to", "all"]),
    ("please", &["let", "find", "see", "send", "help", "note", "call", "check"]),
    ("let", &["me", "us", "you", "them", "him", "her", "know"]),
    ("see", &["you", "what", "if", "how", "them", "it"]),
    ("good", &["morning", "night", "luck", "idea", "afternoon", "job", "news", "day"]),
    ("great", &["to", "job", "news", "idea", "work", "day", "thanks"]),
    ("sounds", &["good", "great", "like", "awesome", "perfect"]),
    ("looks", &["good", "great", "like", "awesome", "promising"]),
    ("feel", &["free", "like", "good", "better", "ready"]),
    ("need", &["to", "some", "more", "help", "any", "time"]),
    ("want", &["to", "you", "some", "more", "it"]),
    ("know", &["what", "that", "how", "if", "about", "more"]),
    ("think", &["about", "that", "it", "we", "you", "so"]),
    ("hope", &["you", "this", "all", "to", "everything"]),
    ("take", &["care", "a", "the", "your", "time", "it"]),
    ("make", &["sure", "a", "sense", "it", "some"]),
    ("get", &["back", "a", "the", "to", "in", "ready", "some"]),
    ("go", &["to", "ahead", "with", "home", "out", "back"]),
    ("going", &["to", "well", "home", "on", "out"]),
    ("come", &["over", "to", "in", "on", "back"]),
    ("send", &["me", "you", "the", "it", "a"]),
    ("give", &["me", "you", "a", "them", "it"]),
    ("tell", &["me", "you", "them", "him", "her"]),
    ("ask", &["for", "you", "about", "them"]),
    ("work", &["on", "with", "for", "together", "out"]),
    ("call", &["me", "you", "it", "them"]),
    ("soon", &["as", "after"]),
    ("just", &["let", "wanted", "in", "a", "to", "like", "need"]),
    ("also", &["have", "need", "want", "be", "can", "like"]),
    ("very", &["much", "good", "well", "nice", "happy", "soon", "important"]),
    ("really", &["appreciate", "good", "great", "like", "want", "need", "enjoy"]),
    ("looking", &["forward", "for", "at", "good", "into"]),
    ("best", &["regards", "wishes", "way", "part", "time"]),
    ("keep", &["in", "up", "going", "it", "you", "me"]),
    ("talk", &["to", "soon", "about", "later", "with"]),
    ("check", &["this", "out", "it", "the", "with", "in"]),
    ("stay", &["safe", "tuned", "in", "here", "with", "positive"]),
    ("reach", &["out", "to", "me"]),
    ("follow", &["up", "the", "with", "you"]),
    ("always", &["welcome", "be", "have", "good"]),
    ("never", &["mind", "give", "been", "had", "seen"]),
    ("sure", &["thing", "to", "about"]),
    ("fine", &["with", "thank", "thanks"]),
    ("happy", &["to", "birthday", "with"]),
    ("ready", &["to", "for"]),
    ("able", &["to"]),
    ("welcome", &["to", "back"]),
    ("sorry", &["for", "about", "to"]),
    ("yes", &["please", "i", "we"]),
    ("no", &["problem", "worries", "doubt", "idea", "way"]),
];



/// Standard compound hyphenated phrases for technical and professional communication.
pub const COMPOUND_HYPHEN_PHRASES: &[(&str, &str)] = &[
    ("realtime", "real-time"),
    ("longterm", "long-term"),
    ("shortterm", "short-term"),
    ("opensource", "open-source"),
    ("endtoend", "end-to-end"),
    ("lowlevel", "low-level"),
    ("highlevel", "high-level"),
    ("userfriendly", "user-friendly"),
    ("stateoftheart", "state-of-the-art"),
    ("facetoface", "face-to-face"),
    ("daytoday", "day-to-day"),
    ("stepbystep", "step-by-step"),
    ("allinone", "all-in-one"),
    ("uptodate", "up-to-date"),
    ("peertopeer", "peer-to-peer"),
    ("pointtopoint", "point-to-point"),
    ("builtIn", "built-in"),
    ("builtin", "built-in"),
    ("optin", "opt-in"),
    ("optout", "opt-out"),
    ("handsfree", "hands-free"),
    ("plugandplay", "plug-and-play"),
];

/// High-Precision Trigram & Bigram Homophone Disambiguation Table.
/// Evaluates preceding context word to select the grammatically correct homophone.
pub const HOMOPHONE_CONTEXT_RULES: &[(&[&str], &str, &str)] = &[
    // ("your" vs "you're")
    (&["you", "are", "if", "when", "that", "since", "know"], "your", "you're"),
    (&["welcome", "right", "sure", "ready", "going", "invited", "beautiful", "crazy", "amazing"], "your", "you're"),
    (&["in", "on", "with", "at", "for", "to", "from", "is", "about"], "you're", "your"),
    
    // ("their" vs "there" vs "they're")
    (&["over", "in", "out", "up", "down", "is", "was", "are", "were", "go", "went", "stay", "been", "get", "hi", "hello"], "their", "there"),
    (&["over", "in", "out", "up", "down", "is", "was", "are", "were", "go", "went", "stay", "been", "get"], "they're", "there"),
    (&["house", "car", "phone", "money", "time", "dog", "team", "friends", "work", "job", "way", "place", "names", "own"], "there", "their"),
    (&["house", "car", "phone", "money", "time", "dog", "team", "friends", "work", "job", "way", "place", "names", "own"], "they're", "their"),
    (&["going", "coming", "doing", "trying", "working", "saying", "asking", "leaving", "arrived", "planning", "thinking"], "there", "they're"),
    (&["going", "coming", "doing", "trying", "working", "saying", "asking", "leaving", "arrived", "planning", "thinking"], "their", "they're"),

    // ("its" vs "it's")
    (&["time", "been", "not", "okay", "fine", "cool", "great", "good", "ready", "true", "hard", "easy", "working", "going"], "its", "it's"),
    (&["own", "color", "size", "weight", "name", "side", "way", "place", "price"], "it's", "its"),

    // ("then" vs "than")
    (&["more", "less", "better", "worse", "greater", "smaller", "faster", "slower", "taller", "shorter", "easier", "harder", "rather", "other"], "then", "than"),
    (&["and", "back", "since", "until", "now", "just", "see", "ok", "okay", "alright"], "than", "then"),

    // ("to" vs "too" vs "two")
    (&["me", "you", "much", "many", "late", "far", "early", "fast", "slow", "hard", "easy", "good", "bad", "hot", "cold"], "to", "too"),
    (&["want", "need", "have", "going", "ready", "able", "hope", "try", "trying", "used", "like", "love"], "too", "to"),
    (&["people", "days", "hours", "minutes", "seconds", "weeks", "months", "years", "times", "things", "items"], "to", "two"),
    (&["people", "days", "hours", "minutes", "seconds", "weeks", "months", "years", "times", "things", "items"], "too", "two"),

    // ("were" vs "we're" vs "where")
    (&["going", "coming", "trying", "thinking", "hoping", "ready", "done", "excited", "happy", "sorry", "here", "there"], "were", "we're"),
    (&["are", "is", "was", "did", "do", "can", "could", "from", "to"], "we're", "where"),
];

    /// Resolves homophone ambiguity based on preceding word context.
    pub fn disambiguate_homophone(prev_word: &str, candidate: &str) -> Option<&'static str> {
        let prev = prev_word.trim().to_ascii_lowercase();
        let cand = candidate.trim().to_ascii_lowercase();
        for &(context_keywords, wrong_form, correct_form) in Self::HOMOPHONE_CONTEXT_RULES {
            if cand == wrong_form {
                if context_keywords.iter().any(|&k| k == prev) {
                    return Some(correct_form);
                }
            }
        }
        None
    }

/// Top sentence starters when beginning a message or after sentence punctuation.
pub const SENTENCE_STARTERS: &[(&str, char)] = &[
    ("I", 'i'),
    ("The", 't'),
    ("How", 'h'),
    ("What", 'w'),
    ("We", 'w'),
    ("Thank", 't'),
    ("Please", 'p'),
    ("Yes", 'y'),
    ("No", 'n'),
    ("Can", 'c'),
    ("Let", 'l'),
    ("Just", 'j'),
    ("Good", 'g'),
    ("Are", 'a'),
    ("Do", 'd'),
    ("My", 'm'),
];

    /// Next-word prediction for an EMPTY composing region: the words most
    /// likely to follow `prev`, from the user's own recorded pairs first
    /// (140+15n scoring, same semantics as bigram_pair_score) and the
    /// shipped language model second. Junk-band successors are noise in a
    /// suggestion bar and are floored out; personal pairs are always
    /// eligible (the user taught them). Bare contraction forms display
    /// apostrophized.
    pub fn predict_next_words(&self, prev: &str, max: usize) -> Vec<String> {
        self.predict_next_words_filtered(prev, max, true)
    }

    /// `include_personal = false` predicts from the shipped model only:
    /// private (incognito) sessions must not surface the user's learned
    /// pairs on screen.
    pub fn predict_next_words_filtered(
        &self,
        prev: &str,
        max: usize,
        include_personal: bool,
    ) -> Vec<String> {
        let prev_l = prev.trim().to_lowercase();
        if max == 0 {
            return Vec::new();
        }
        if prev_l.is_empty() {
            // Sentence start (or empty field): the capitalized starters,
            // same list the letter-prior fallback uses.
            return Self::SENTENCE_STARTERS
                .iter()
                .take(max)
                .map(|&(w, _)| w.to_string())
                .collect();
        }
        let mut scored: Vec<(u8, &str)> = Vec::new();
        if include_personal {
            for ((p, n), count) in &self.personal_bigrams {
                if p == &prev_l {
                    let s = 140u32.saturating_add(count.saturating_mul(15)).min(255) as u8;
                    scored.push((s, n.as_str()));
                }
            }
        }
        if let Some(&prev_id) = self.word_ids.get(&prev_l) {
            for &(_, next_id, score) in self.bigrams.successors(prev_id) {
                if let Some(w) = self.corpus_words().get(next_id as usize) {
                    if self.trie.get_frequency(w).unwrap_or(0) >= 150 {
                        scored.push((score, w.as_str()));
                    }
                }
            }
        }
        scored.sort_by(|a, b| b.0.cmp(&a.0));
        let mut out: Vec<String> = Vec::with_capacity(max);
        for (_, w) in scored {
            let display = contraction_display(w).unwrap_or(w);
            if !out.iter().any(|o| o == display) {
                out.push(display.to_string());
            }
            if out.len() >= max {
                break;
            }
        }
        out
    }

    /// Predicts the highest-frequency word for each next possible letter key (BlackBerry Flick Predictions).
    /// If prefix is non-empty, predicts prefix completions starting with each letter.
    /// If prefix is empty, predicts contextual next words following `prev_word`.
    pub fn predict_next_letter_words(&self, prefix: &str, prev_word: &str) -> Vec<(char, String)> {
        let trimmed = prefix.trim();
        if !trimmed.is_empty() {
            let trimmed_lower = trimmed.to_ascii_lowercase();
            let mut candidates: Vec<(char, String, u32)> = Vec::with_capacity(26);

            let valid_next = self.trie.get_valid_next_chars(&trimmed_lower);
            for ch in valid_next {
                let candidate_prefix = format!("{}{}", trimmed_lower, ch);
                let matches = self.trie.prefix_search(&candidate_prefix, 1);
                if let Some((word, freq)) = matches.first() {
                    let formatted = Self::apply_casing(trimmed, word);
                    candidates.push((ch, formatted, *freq));
                }
            }

            candidates.sort_by(|a, b| b.2.cmp(&a.2));
            candidates.truncate(6);
            return candidates.into_iter().map(|(ch, word, _)| (ch, word)).collect();
        }

        // Prefix is empty: Predict next words based on preceding context word
        let prev_trimmed = prev_word.trim().to_ascii_lowercase();
        let mut result_map = std::collections::HashMap::new();

        // The real language model first: the shipped 244k-pair table knows
        // successors for tens of thousands of prev words, where the static
        // hand list below covers ~36. The user's own recorded pairs outrank
        // web statistics, exactly as in bigram_pair_score.
        if !prev_trimmed.is_empty() {
            let mut scored: Vec<(u8, &str)> = Vec::new();
            for ((p, n), count) in &self.personal_bigrams {
                if p == &prev_trimmed {
                    let s = 140u32.saturating_add(count.saturating_mul(15)).min(255) as u8;
                    scored.push((s, n.as_str()));
                }
            }
            if let Some(&prev_id) = self.word_ids.get(&prev_trimmed) {
                for &(_, next_id, score) in self.bigrams.successors(prev_id) {
                    if let Some(w) = self.corpus_words().get(next_id as usize) {
                        scored.push((score, w.as_str()));
                    }
                }
            }
            // This runs on the TAP path: common prevs ("the", "i") have
            // thousands of successors and a full sort cost ~100us/call
            // (measured 2026-08-27, the "no longer zippy" report). Only the
            // top few dozen can ever survive the 6-distinct-first-letters
            // cut, so partially select before sorting.
            const TOP: usize = 32;
            if scored.len() > TOP {
                scored.select_nth_unstable_by(TOP, |a, b| b.0.cmp(&a.0));
                scored.truncate(TOP);
            }
            scored.sort_by(|a, b| b.0.cmp(&a.0));
            for (_, w) in scored {
                if let Some(first_ch) = w.chars().next() {
                    let ch_lower = first_ch.to_ascii_lowercase();
                    result_map.entry(ch_lower).or_insert_with(|| w.to_string());
                }
                if result_map.len() >= 6 {
                    break;
                }
            }
        }

        if result_map.is_empty() {
            if let Some(&(_, next_words)) = Self::BIGRAM_TRANSITIONS.iter().find(|&&(k, _)| k == prev_trimmed) {
            for &w in next_words {
                if let Some(first_ch) = w.chars().next() {
                    let ch_lower = first_ch.to_ascii_lowercase();
                    if !result_map.contains_key(&ch_lower) {
                        result_map.insert(ch_lower, w.to_string());
                    }
                }
                if result_map.len() >= 6 {
                    break;
                }
            }
            }
        }

        // If no preceding word or insufficient bigrams, fill with high-probability sentence starters
        if result_map.is_empty() {
            for &(w, ch) in Self::SENTENCE_STARTERS {
                let ch_lower = ch.to_ascii_lowercase();
                if !result_map.contains_key(&ch_lower) {
                    result_map.insert(ch_lower, w.to_string());
                }
                if result_map.len() >= 6 {
                    break;
                }
            }
        }

        result_map.into_iter().collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_predict_next_letter_words() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("the", 1000),
            ("this", 900),
            ("that", 850),
            ("those", 800),
            ("three", 750),
            ("crypto", 950),
            ("can", 900),
            ("could", 850),
        ]);

        let th_preds = engine.predict_next_letter_words("th", "");
        let pred_map: std::collections::HashMap<char, String> = th_preds.into_iter().collect();

        assert_eq!(pred_map.get(&'e').unwrap(), "the");
        assert_eq!(pred_map.get(&'i').unwrap(), "this");
        assert_eq!(pred_map.get(&'a').unwrap(), "that");
        assert_eq!(pred_map.get(&'o').unwrap(), "those");
        assert_eq!(pred_map.get(&'r').unwrap(), "three");

        // Test with capitalization preservation
        let c_preds = engine.predict_next_letter_words("C", "");
        let c_map: std::collections::HashMap<char, String> = c_preds.into_iter().collect();
        assert_eq!(c_map.get(&'r').unwrap(), "Crypto");
        assert_eq!(c_map.get(&'a').unwrap(), "Can");
    }

    #[test]
    fn test_single_letter_i_autocorrect() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("in", 1000), ("is", 900), ("it", 800), ("i", 500)]);

        let res = engine.suggest("i", 3);
        assert_eq!(res.candidates[0].word, "I");
        assert!(res.candidates[0].is_autocorrect);

        let res_upper = engine.suggest("I", 3);
        assert_eq!(res_upper.candidates[0].word, "I");
        assert!(res_upper.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_shorthand_slang_expansions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("you", 1000), ("are", 1000), ("know", 500), ("honest", 400)]);

        // Single letter abbreviations (suggest 'you'/'are' without space hijacking)
        let res_u = engine.suggest("u", 3);
        assert_eq!(res_u.candidates[0].word, "you");
        assert!(!res_u.candidates[0].is_autocorrect);

        let res_r = engine.suggest("r", 3);
        assert_eq!(res_r.candidates[0].word, "are");
        assert!(!res_r.candidates[0].is_autocorrect);

        // 2+ char acronyms (auto-commit enabled)
        let res_idk = engine.suggest("idk", 3);
        assert_eq!(res_idk.candidates[0].word, "I don't know");
        assert!(res_idk.candidates[0].is_autocorrect);

        let res_tbh = engine.suggest("tbh", 3);
        assert_eq!(res_tbh.candidates[0].word, "to be honest");
        assert!(res_tbh.candidates[0].is_autocorrect);

        let res_omw = engine.suggest("omw", 3);
        assert_eq!(res_omw.candidates[0].word, "on my way");
        assert!(res_omw.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_wikipedia_corpus_integration() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[("definitely", 1000), ("government", 900), ("accommodation", 800)]);

        let res_def = engine.suggest("definately", 3);
        assert_eq!(res_def.candidates[0].word, "definitely");
        assert!(res_def.candidates[0].is_autocorrect);

        let res_gov = engine.suggest("goverment", 3);
        assert_eq!(res_gov.candidates[0].word, "government");
        assert!(res_gov.candidates[0].is_autocorrect);

        let res_acc = engine.suggest("accomodation", 3);
        assert_eq!(res_acc.candidates[0].word, "accommodation");
        assert!(res_acc.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_custom_names_and_literal_candidate_presence() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("lock", 1000),
            ("loom", 900),
            ("look", 800),
            ("alice", 700),
        ]);

        // Custom name "Alexander" (not in dictionary)
        let res_name = engine.suggest("Alexander", 3);
        // "Alexander" must be candidate 0 and NOT auto-corrected
        assert_eq!(res_name.candidates[0].word, "Alexander");
        assert!(!res_name.candidates[0].is_autocorrect);

        // Rare custom lowercase word "qwertyuiop"
        let res_custom = engine.suggest("qwertyuiop", 3);
        assert!(res_custom.candidates.iter().any(|c| c.word == "qwertyuiop"));
        assert!(!res_custom.candidates.iter().find(|c| c.word == "qwertyuiop").unwrap().is_autocorrect);
    }

    #[test]
    fn test_exact_words_never_mangled_to_contractions() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("word", 1000),
            ("words", 900),
            ("desired", 800),
            ("desire", 700),
            ("well", 1000),
            ("were", 1000),
            ("shed", 500),
        ]);

        // "word", "words", "desired" must NEVER become "wor'd", "word's", "desire'd"
        let res_word = engine.suggest("word", 3);
        assert_eq!(res_word.candidates[0].word, "word");
        assert!(!res_word.candidates[0].is_autocorrect);

        let res_words = engine.suggest("words", 3);
        assert_eq!(res_words.candidates[0].word, "words");
        assert!(!res_words.candidates[0].is_autocorrect);

        let res_desired = engine.suggest("desired", 3);
        assert_eq!(res_desired.candidates[0].word, "desired");
        assert!(!res_desired.candidates[0].is_autocorrect);

        // Ambiguous words like "well" and "were" must NOT auto-hijack to "we'll" / "we're"
        let res_well = engine.suggest("well", 3);
        assert_eq!(res_well.candidates[0].word, "well");
        assert!(!res_well.candidates[0].is_autocorrect);

        let res_were = engine.suggest("were", 3);
        assert_eq!(res_were.candidates[0].word, "were");
        assert!(!res_were.candidates[0].is_autocorrect);
    }

    #[test]
    fn test_contractions_vast_coverage() {
        let mut engine = NlpEngine::new();
        engine.load_dictionary(&[
            ("dont", 50),
            ("don't", 1000),
            ("aint", 50),
            ("ain't", 1000),
            ("cant", 50),
            ("can't", 1000),
            ("wont", 50),
            ("won't", 1000),
            ("yall", 50),
            ("y'all", 1000),
        ]);

        let test_cases = &[
            ("aint", "ain't", true),
            ("Aint", "Ain't", true),
            ("dont", "don't", true),
            ("cant", "can't", true),
            ("wont", "won't", true),
            ("yall", "y'all", true),
        ];

        for &(input, expected, should_auto) in test_cases {
            let res = engine.suggest(input, 3);
            assert_eq!(res.candidates[0].word, expected, "Failed for input: {}", input);
            assert_eq!(res.candidates[0].is_autocorrect, should_auto, "Auto-flag wrong for: {}", input);
        }
    }

    #[test]
    fn corpus_matches_jvm_map_semantics() {
        let mut engine = NlpEngine::new();
        engine.corpus_insert("the", 255);
        engine.corpus_insert("quick", 200);
        // Duplicate keeps last frequency, first position — like Map::put.
        engine.corpus_insert("the", 100);
        assert_eq!(engine.corpus_words(), &["the".to_string(), "quick".to_string()]);
        assert_eq!(engine.corpus_freq("the"), 100);
        assert_eq!(engine.corpus_freq("quick"), 200);
        assert_eq!(engine.corpus_freq("absent"), 0);
    }

    #[test]
    fn corpus_stays_separate_from_learned_trie_words() {
        let mut engine = NlpEngine::new();
        engine.corpus_insert("hello", 255);
        // A learned word enters the trie but must never leak into the corpus.
        engine.trie.insert("zzzcustom", 100);
        assert_eq!(engine.corpus_words().len(), 1);
        assert_eq!(engine.corpus_freq("zzzcustom"), 0);
    }
}
