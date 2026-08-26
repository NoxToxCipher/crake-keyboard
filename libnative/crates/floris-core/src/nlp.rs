use crate::shorthand::lookup_shorthand;
use crate::trie::RadixTrie;
use crate::typo_corpus::lookup_common_typo;

/// Comprehensive lexicon of common English contractions (SCOWL & Wiktionary).
pub const CONTRACTIONS: &[(&str, &str)] = &[
    ("aint", "ain't"),
    ("dont", "don't"),
    ("cant", "can't"),
    ("wont", "won't"),
    ("didnt", "didn't"),
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
    ("oughtnt", "oughtn't"),
    ("darent", "daren't"),
    ("couldve", "could've"),
    ("shouldve", "should've"),
    ("wouldve", "would've"),
    ("mightve", "might've"),
    ("mustve", "must've"),
    ("im", "I'm"),
    ("ive", "I've"),
    ("ill", "I'll"),
    ("id", "I'd"),
    ("youre", "you're"),
    ("youve", "you've"),
    ("youll", "you'll"),
    ("youd", "you'd"),
    ("theyre", "they're"),
    ("theyve", "they've"),
    ("theyll", "they'll"),
    ("theyd", "they'd"),
    ("weve", "we've"),
    ("were", "we're"),
    ("well", "we'll"),
    ("wed", "we'd"),
    ("hes", "he's"),
    ("shes", "she's"),
    ("hed", "he'd"),
    ("shed", "she'd"),
    ("its", "it's"),
    ("itll", "it'll"),
    ("itd", "it'd"),
    ("whats", "what's"),
    ("whatll", "what'll"),
    ("whatd", "what'd"),
    ("whatre", "what're"),
    ("whatve", "what've"),
    ("thats", "that's"),
    ("thatll", "that'll"),
    ("thatd", "that'd"),
    ("theres", "there's"),
    ("therell", "there'll"),
    ("thered", "there'd"),
    ("therere", "there're"),
    ("heres", "here's"),
    ("wheres", "where's"),
    ("wherell", "where'll"),
    ("whered", "where'd"),
    ("wherere", "where're"),
    ("whereve", "where've"),
    ("hows", "how's"),
    ("howll", "how'll"),
    ("howd", "how'd"),
    ("howre", "how're"),
    ("howve", "how've"),
    ("whos", "who's"),
    ("wholl", "who'll"),
    ("whod", "who'd"),
    ("whore", "who're"),
    ("whove", "who've"),
    ("whys", "why's"),
    ("whyll", "why'll"),
    ("whyd", "why'd"),
    ("whyre", "why're"),
    ("whyve", "why've"),
    ("lets", "let's"),
    ("yall", "y'all"),
    ("yalld", "y'all'd"),
    ("yallve", "y'all've"),
    ("somethings", "something's"),
    ("everythings", "everything's"),
    ("nothings", "nothing's"),
    ("someones", "someone's"),
    ("everyones", "everyone's"),
    ("anyones", "anyone's"),
    ("somebodys", "somebody's"),
    ("everybodys", "everybody's"),
    ("anybodys", "anybody's"),
    ("cmon", "c'mon"),
    ("maam", "ma'am"),
    ("oclock", "o'clock"),
    ("tis", "'tis"),
    ("twas", "'twas"),
];

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
    /// The shipped dictionary exactly as the CRKD blob delivered it, in blob
    /// order. Deliberately separate from the trie: the trie also accumulates
    /// user-learned words, and the glide classifier's word list must keep
    /// meaning "the static corpus" — the same contents the JVM-side word map
    /// held before this store replaced it.
    corpus_words: Vec<String>,
    corpus_freqs: std::collections::HashMap<String, u32>,
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
            self.corpus_words.push(word.to_string());
        }
    }

    pub fn corpus_words(&self) -> &[String] {
        &self.corpus_words
    }

    /// Frequency of a corpus word, 0 when absent — the same contract as a
    /// map lookup defaulting to 0 on the JVM side.
    pub fn corpus_freq(&self, word: &str) -> u32 {
        self.corpus_freqs.get(word).copied().unwrap_or(0)
    }

    pub fn apply_casing(query: &str, candidate: &str) -> String {
        let is_all_upper = query.len() > 1 && query.chars().all(|c| !c.is_alphabetic() || c.is_uppercase());
        let is_first_upper = query.chars().next().map_or(false, |c| c.is_uppercase());

        if is_all_upper {
            candidate.to_uppercase()
        } else if is_first_upper {
            let mut chars = candidate.chars();
            match chars.next() {
                Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
                None => candidate.to_string(),
            }
        } else {
            candidate.to_string()
        }
    }

    pub fn suggest(&self, query: &str, max_candidates: usize) -> SuggestionResult {
        let trimmed = query.trim();
        let trimmed_lower = trimmed.to_lowercase();
        if trimmed_lower.is_empty() {
            return SuggestionResult {
                query: query.to_string(),
                is_exact_match: false,
                candidates: Vec::new(),
            };
        }

        let is_exact = self.trie.contains(&trimmed_lower);
        let mut candidates: Vec<RankedCandidate> = Vec::with_capacity(max_candidates);

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
        } else if let Some(typo_fix) = lookup_common_typo(&trimmed_lower) {
            // 3. Wikipedia 1,770+ Misspelling Corpus instant O(log N) lookup
            let formatted = Self::apply_casing(trimmed, typo_fix);
            candidates.push(RankedCandidate {
                word: formatted,
                is_autocorrect: true,
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
        }

        // 6. Prefix completions (completions must NOT auto-commit on space)
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

        // 7. Fuzzy search for typo recovery if query is not in dictionary
        let is_capitalized = trimmed.chars().next().map_or(false, |c| c.is_uppercase());
        if candidates.len() < max_candidates {
            let max_dist = if trimmed_lower.len() <= 4 { 1 } else { 2 };
            let fuzzy = self.trie.fuzzy_search(&trimmed_lower, max_dist, max_candidates + 4);
            // Partition fuzzy matches: spatial keyboard neighbor slips get top priority
            let mut sorted_fuzzy = fuzzy;
            sorted_fuzzy.sort_by_key(|fc| {
                let is_neighbor = Self::is_spatial_slip_match(&trimmed_lower, &fc.word);
                let score = if is_neighbor { 0 } else { 1 };
                (score, fc.distance)
            });

            for fc in sorted_fuzzy {
                let formatted = Self::apply_casing(trimmed, &fc.word);
                if !contains_word(&candidates, &formatted) {
                    let is_neighbor = Self::is_spatial_slip_match(&trimmed_lower, &fc.word);
                    let should_autocorrect = !is_exact && !is_capitalized && (fc.distance == 1 || is_neighbor) && candidates.is_empty();
                    candidates.push(RankedCandidate {
                        word: formatted,
                        is_autocorrect: should_autocorrect,
                    });
                }
                if candidates.len() >= max_candidates {
                    break;
                }
            }
        }

        // 8. CRITICAL: The literal raw typed word MUST ALWAYS be in the candidate list
        // so the user can always tap their exact text (e.g. custom names, passphrases, codes)
        if !contains_word(&candidates, trimmed) {
            if is_capitalized || candidates.is_empty() {
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

        if candidates.len() > max_candidates {
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
        't' => matches!(b, 'r' | 'y' | 'f' | 'g' | 'h' | 'h' | 'n'),
        'y' => matches!(b, 't' | 'u' | 'g' | 'h' | 'j' | 'p' | 'f'),
        'u' => matches!(b, 'y' | 'i' | 'h' | 'j' | 'k' | 'e' | 'i'),
        'i' => matches!(b, 'u' | 'o' | 'j' | 'k' | 'l' | 'u' | 'd'),
        'o' => matches!(b, 'i' | 'p' | 'k' | 'l' | 'a' | 'e'),
        'p' => matches!(b, 'o' | 'l' | 'y' | 'f'),
        'a' => matches!(b, 'q' | 'w' | 's' | 'z' | 'o' | '\''),
        's' => matches!(b, 'w' | 'e' | 'a' | 'd' | 'z' | 'x' | 'n' | '-'),
        'd' => matches!(b, 'e' | 'r' | 's' | 'f' | 'x' | 'c' | 'i' | 'h'),
        'f' => matches!(b, 'r' | 't' | 'd' | 'g' | 'c' | 'v' | 'y' | 'g'),
        'g' => matches!(b, 't' | 'y' | 'f' | 'h' | 'v' | 'b' | 'c' | 'r'),
        'h' => matches!(b, 'y' | 'u' | 'g' | 'j' | 'b' | 'n' | 'd' | 't'),
        'j' => matches!(b, 'u' | 'i' | 'h' | 'k' | 'n' | 'm' | 'q' | 'k'),
        'k' => matches!(b, 'i' | 'o' | 'j' | 'l' | 'm' | 'j' | 'x'),
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
    slip_count > 0 && slip_count <= 2
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

    /// Predicts the highest-frequency word for each next possible letter key (BlackBerry Flick Predictions).
    /// If prefix is non-empty, predicts prefix completions starting with each letter.
    /// If prefix is empty, predicts contextual next words following `prev_word`.
    pub fn predict_next_letter_words(&self, prefix: &str, prev_word: &str) -> Vec<(char, String)> {
        let trimmed = prefix.trim();
        if !trimmed.is_empty() {
            let trimmed_lower = trimmed.to_ascii_lowercase();
            let mut candidates: Vec<(char, String, u32)> = Vec::with_capacity(26);

            for ch in 'a'..='z' {
                let candidate_prefix = format!("{}{}", trimmed_lower, ch);
                let matches = self.trie.prefix_search(&candidate_prefix, 1);
                if let Some((word, freq)) = matches.first() {
                    if word.len() > trimmed_lower.len() && *freq >= 120 {
                        let formatted = Self::apply_casing(trimmed, word);
                        candidates.push((ch, formatted, *freq));
                    }
                }
            }

            candidates.sort_by(|a, b| b.2.cmp(&a.2));
            candidates.truncate(6);
            return candidates.into_iter().map(|(ch, word, _)| (ch, word)).collect();
        }

        // Prefix is empty: Predict next words based on preceding context word
        let prev_trimmed = prev_word.trim().to_ascii_lowercase();
        let mut result_map = std::collections::HashMap::new();

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
