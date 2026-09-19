use crate::shorthand::lookup_shorthand;
use crate::trie::RadixTrie;
use crate::touch_model::SLIP_NEAR_FACTOR;
use crate::typo_corpus::lookup_common_typo;
use std::sync::{Arc, RwLock};

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
    if raw_token.len() != candidate.len() || raw_token.len() < 2 || raw_token.len() > 32 {
        return false;
    }

    if raw_token.is_ascii() && candidate.is_ascii() {
        let raw_bytes = raw_token.as_bytes();
        let cand_bytes = candidate.as_bytes();
        let len = raw_bytes.len();

        let mut mismatch_idx = None;
        for i in 0..len {
            if raw_bytes[i] != cand_bytes[i] {
                mismatch_idx = Some(i);
                break;
            }
        }

        let Some(i) = mismatch_idx else {
            return false;
        };

        if i + 1 >= len {
            return false;
        }

        if raw_bytes[i] != cand_bytes[i + 1] || raw_bytes[i + 1] != cand_bytes[i] {
            return false;
        }

        for j in (i + 2)..len {
            if raw_bytes[j] != cand_bytes[j] {
                return false;
            }
        }

        let hand1 = get_key_hand(raw_bytes[i] as char);
        let hand2 = get_key_hand(raw_bytes[i + 1] as char);
        if hand1 == Hand::Unknown || hand2 == Hand::Unknown || hand1 == hand2 {
            return false;
        }

        if timestamps.len() == len {
            let t1 = timestamps[i];
            let t2 = timestamps[i + 1];
            let delta = t2.abs_diff(t1);
            return delta <= 55;
        }

        return false;
    }

    // Stack array for fast zero-allocation char inspection (Unicode fallback)
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
        let delta = t2.abs_diff(t1);
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

/// Given names that are always written with a capital. Typing one in
/// lower case offers it capitalised, exactly as the brand table does
/// for "crake" -> "Crake" (field report 2026-09-19). Add a name here
/// only when it is also in the dictionary, or the suggestion would be
/// the only place it exists.
pub const GIVEN_NAME_CASING: &[(&str, &str)] = &[
    ("aidan", "Aidan"),
    ("aine", "Aine"),
    ("aisling", "Aisling"),
    ("aoibhe", "Aoibhe"),
    ("aoibheann", "Aoibheann"),
    ("aoibhinn", "Aoibhinn"),
    ("aoife", "Aoife"),
    ("blathnaid", "Blathnaid"),
    ("bronagh", "Bronagh"),
    ("caoilfhinn", "Caoilfhinn"),
    ("caoimhe", "Caoimhe"),
    ("cathal", "Cathal"),
    ("cian", "Cian"),
    ("ciara", "Ciara"),
    ("ciaran", "Ciaran"),
    ("cillian", "Cillian"),
    ("clodagh", "Clodagh"),
    ("colm", "Colm"),
    ("cormac", "Cormac"),
    ("daithi", "Daithi"),
    ("darragh", "Darragh"),
    ("declan", "Declan"),
    ("deirdre", "Deirdre"),
    ("diarmuid", "Diarmuid"),
    ("doireann", "Doireann"),
    ("donal", "Donal"),
    ("eabha", "Eabha"),
    ("eamon", "Eamon"),
    ("eimear", "Eimear"),
    ("eithne", "Eithne"),
    ("eoin", "Eoin"),
    ("fergal", "Fergal"),
    ("fiachra", "Fiachra"),
    ("fiadh", "Fiadh"),
    ("finbar", "Finbar"),
    ("fintan", "Fintan"),
    ("fionnuala", "Fionnuala"),
    ("gearoid", "Gearoid"),
    ("grainne", "Grainne"),
    ("keeva", "Keeva"),
    ("kieran", "Kieran"),
    ("killian", "Killian"),
    ("laoise", "Laoise"),
    ("lorcan", "Lorcan"),
    ("maeve", "Maeve"),
    ("mairead", "Mairead"),
    ("meabh", "Meabh"),
    ("moira", "Moira"),
    ("muireann", "Muireann"),
    ("neasa", "Neasa"),
    ("niall", "Niall"),
    ("niamh", "Niamh"),
    ("nuala", "Nuala"),
    ("odhran", "Odhran"),
    ("oisin", "Oisin"),
    ("oonagh", "Oonagh"),
    ("orlaith", "Orlaith"),
    ("padraig", "Padraig"),
    ("roise", "Roise"),
    ("roisin", "Roisin"),
    ("ronan", "Ronan"),
    ("ruairi", "Ruairi"),
    ("sadhbh", "Sadhbh"),
    ("saoirse", "Saoirse"),
    ("seamus", "Seamus"),
    ("senan", "Senan"),
    ("sinead", "Sinead"),
    ("siobhan", "Siobhan"),
    ("siofra", "Siofra"),
    ("sorcha", "Sorcha"),
    ("tadhg", "Tadhg"),
    ("tiernan", "Tiernan"),
    ("ultan", "Ultan"),
];

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
    ("gday", "g'day"),
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
    ("thered", "there'd"),
    ("therell", "there'll"),
    ("therere", "there're"),
    ("theres", "there's"),
    ("thereve", "there've"),
    ("theyd", "they'd"),
    ("theydve", "they'd've"),
    ("theyll", "they'll"),
    ("theyre", "they're"),
    ("theyve", "they've"),
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
    "gday",
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

/// A typo-corpus key at or above this corpus frequency is a real word the
/// person meant, not a misspelling ("favourite" 222, "alright" 219).
const TYPO_CORPUS_REAL_WORD_FLOOR: u32 = 160;

/// Typo-corpus keys that sit above the floor only as n-gram noise, never
/// as words a person means ("ment" 163): they always fix. The floor is a
/// frequency proxy; real words under it must be removed from the table
/// by hand (see typo_corpus.rs).
const TYPO_CORPUS_KNOWN_JUNK: &[&str] = &["ment", "gunna", "thr"];

/// Tokens that fix even though they begin a longer everyday word, because
/// nobody ever means them: "thr" is not a word, and a person typing it
/// wants "the" far more often than they are three letters into "through"
/// (asked for by name, field report 2026-09-19). Everything else that is
/// still a live prefix keeps what was typed -- see the guard at the end of
/// `suggest_with_context_opts`. Add to this list only on evidence.
const ALWAYS_FIX_LIVE_PREFIXES: &[&str] = &["thr"];

/// Words after which the possessive "its" is impossible, so a typed "its"
/// was meant as "it is"/"it has". A possessive determiner MUST be followed
/// by a noun phrase, so a determiner, a pronoun, a preposition or a verb
/// form after it settles the question by itself.
///
/// Deliberately NOT here: adjectives and degree adverbs, because "its very
/// nature", "its only hope" and "its pretty face" are all correct
/// possessives; and nouns that can also be a predicate ("it is time",
/// "it is worth it"). One wrong entry is worse than fifty missing ones.
const ITS_FOLLOWED_BY_APOSTROPHE: &[&str] = &[
    // determiners: nothing may follow a possessive determiner but a noun
    "a", "an", "the", "my", "your", "his", "her", "their", "our", "this",
    "these", "those",
    // pronouns
    "me", "you", "him", "us", "them", "everyone", "everybody", "someone",
    "somebody", "something", "nothing", "anything", "everything", "mine",
    "yours", "ours", "theirs",
    // negation and sentence adverbs
    "not", "never", "always", "still", "already", "probably", "definitely",
    "actually", "basically", "literally", "apparently", "honestly",
    "obviously", "clearly", "so", "too", "gonna",
    // verb forms that follow "it is" / "it has"
    "been", "going", "getting", "coming", "become", "becoming", "happening",
    "working", "raining", "snowing", "starting", "supposed", "meant", "gone",
    "done", "over",
    // prepositions and conjunctions
    "for", "to", "from", "about", "because", "like", "at", "in", "on",
];

/// Words after which "it is"/"it has" is impossible, so a typed "it's" was
/// meant as the possessive. Only words that cannot be a predicate belong
/// here: "it is own" is not English, while "it is time" and "it is worth
/// it" both are, so "time" and "worth" must never be listed.
const ITS_FOLLOWED_BY_POSSESSIVE: &[&str] = &["own", "respective", "sake", "entirety"];

/// The word a token should have been, judged by the word typed AFTER it.
///
/// "its" and "it's" cannot be told apart when they are typed: the shipped
/// language model holds no apostrophe tokens at all, and both forms sit at
/// corpus 253. What settles it is the next word, which only arrives a
/// keystroke later -- so this is applied retroactively, once that word is
/// committed (field report 2026-09-19: "what about its and it's ... is it
/// context dependent on a few words later?").
///
/// Returns the replacement for `prev`, carrying its capitalisation across,
/// or `None` when the following word decides nothing -- which is most of
/// the time, and is the safe answer.
pub fn retro_word_fix(prev: &str, next: &str) -> Option<String> {
    let prev_trim = prev.trim();
    let next_key: String = next
        .trim()
        .trim_end_matches(|c: char| c.is_ascii_punctuation() && c != '\'')
        .to_ascii_lowercase();
    if next_key.is_empty() {
        return None;
    }
    let bare: String = prev_trim
        .to_ascii_lowercase()
        .chars()
        .filter(|c| c.is_ascii_alphabetic())
        .collect();
    if bare != "its" {
        return None;
    }
    let typed_apostrophe = prev_trim.contains(APOSTROPHE_CHARS[0])
        || prev_trim.contains(APOSTROPHE_CHARS[1])
        || prev_trim.contains(APOSTROPHE_CHARS[2]);
    let want_apostrophe = if ITS_FOLLOWED_BY_APOSTROPHE.contains(&next_key.as_str()) {
        true
    } else if ITS_FOLLOWED_BY_POSSESSIVE.contains(&next_key.as_str()) {
        false
    } else {
        return None;
    };
    if want_apostrophe == typed_apostrophe {
        return None;
    }
    let replacement = if want_apostrophe { "it's" } else { "its" };
    // carry the capitalisation of what was typed
    Some(if prev_trim.chars().next().is_some_and(|c| c.is_uppercase()) {
        let mut c = replacement.chars();
        match c.next() {
            Some(f) => f.to_uppercase().collect::<String>() + c.as_str(),
            None => replacement.to_string(),
        }
    } else {
        replacement.to_string()
    })
}

const APOSTROPHE_CHARS: [char; 3] = ['\'', '\u{2019}', '\u{2018}'];

/// Chat prefixes that get run into the next word on a phone. The splitter
/// accepts `prefix + top-tier word` (or `prefix + contraction`) on any
/// attestation at all, because the pair table is news-flavoured and rates
/// "i forgot" below "a council".
pub const CHAT_PREFIXES: &[&str] = &[
    "i", "u", "ur", "ya", "im", "id", "ill", "ive", "its", "dont", "cant", "wont", "thats", "whats",
];

/// `token` = chat prefix + rest, longest prefix first, rest at least two
/// letters.
pub fn chat_prefix_of(token: &str) -> Option<(&'static str, &str)> {
    let mut best: Option<(&'static str, &str)> = None;
    for &p in CHAT_PREFIXES {
        if let Some(rest) = token.strip_prefix(p) {
            if rest.chars().count() >= 2
                && rest.chars().all(|c| c.is_ascii_alphabetic())
                && best.is_none_or(|(b, _)| p.len() > b.len())
            {
                best = Some((p, rest));
            }
        }
    }
    best
}

/// The only valid-word slips corrected by context: pairs that are not
/// English at all, each a single adjacent key from the word meant. Added by
/// hand from field specimens; never generated from the bigram table.
pub const CURATED_CONTEXT_SLIPS: &[(&str, &str, &str)] = &[
    ("i", "an", "am"),
    ("i", "ma", "am"),
];

pub const GLIDE_CONTRACTION_BARE: &[&str] = &[
    "cant",
    "wont",
];

pub fn canonicalize_contraction(word: &str) -> Option<&'static str> {
    if word.is_empty() || word.len() > 32 {
        return None;
    }
    let mut bare_buf = [0u8; 32];
    let mut bare_len = 0;
    for ch in word.chars() {
        if ch != '\'' && ch != '’' && ch != '‘' {
            let lower = ch.to_ascii_lowercase();
            if lower.is_ascii_alphabetic() {
                if bare_len < 32 {
                    bare_buf[bare_len] = lower as u8;
                    bare_len += 1;
                }
            } else {
                return None;
            }
        }
    }
    let bare = std::str::from_utf8(&bare_buf[..bare_len]).ok()?;
    if SAFE_CONTRACTION_BARE.binary_search(&bare).is_ok() || GLIDE_CONTRACTION_BARE.binary_search(&bare).is_ok() {
        if let Ok(idx) = CONTRACTIONS.binary_search_by_key(&bare, |&(k, _)| k) {
            return Some(CONTRACTIONS[idx].1);
        }
    }
    None
}

pub fn contraction_display_for_glide(bare: &str) -> Option<&'static str> {
    canonicalize_contraction(bare)
}


/// Previous-word triggers under which a typed "ill" is the adjective ("feel
/// ill", "very ill", "the ill") and not a dropped-apostrophe "I'll". The
/// bigram table cannot arbitrate this pair: its corpus was tokenised without
/// apostrophes, so "and I'll" is counted under "and ill" (probe 2026-09-13:
/// and=154, so=137, i'll=0 for every prev). Only words that never precede
/// "I'll" in conversation belong here — a miss keeps what was typed, a false
/// trigger silently eats an "I'll". Trailing punctuation on the prev token
/// ("feel,") is ignored.
pub fn ill_reads_as_adjective(prev: Option<&str>) -> bool {
    let Some(prev) = prev else { return false };
    let p: String = prev
        .trim()
        .chars()
        .filter(|c| c.is_ascii_alphabetic() || *c == '\'' || *c == '’')
        .map(|c| if c == '’' { '\'' } else { c.to_ascii_lowercase() })
        .collect();
    matches!(
        p.as_str(),
        "feel" | "feels" | "feeling" | "felt" | "fell" | "fall" | "falls" | "falling" | "fallen"
            | "look" | "looks" | "looked" | "looking" | "seem" | "seems" | "seemed"
            | "am" | "is" | "was" | "are" | "were" | "be" | "been" | "being"
            | "get" | "gets" | "got" | "getting" | "gotten"
            | "become" | "becomes" | "became" | "becoming"
            | "very" | "really" | "too" | "quite" | "pretty" | "extremely"
            | "seriously" | "terminally" | "mentally" | "physically" | "critically"
            | "chronically" | "gravely" | "violently"
            | "the" | "an" | "of"
            | "im" | "i'm" | "hes" | "he's" | "shes" | "she's" | "youre" | "you're"
            | "theyre" | "they're" | "we're"
            | "isnt" | "isn't" | "wasnt" | "wasn't" | "arent" | "aren't"
            | "bit" | "little" | "still" | "super" | "currently"
            | "sick" | "mum" | "mom" | "dad" | "baby" | "kids" | "kid" | "dog" | "cat"
    )
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
                if matches!(
                    p.as_str(),
                    "as" | "very" | "so" | "quite" | "pretty" | "how" | "doing" | "done"
                        | "all" | "deep" | "water" | "oil" | "wish" | "said"
                        // modal + "well" + verb is adverbial, never "we'll":
                        // "it may well be", "could well go" (audit 2026-08-28)
                        | "may" | "might" | "could" | "should" | "would" | "will" | "can" | "must"
                ) {
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
            if ill_reads_as_adjective(prev.as_deref()) {
                return None;
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
        "id" => {
            if let Some(ref p) = prev {
                if matches!(p.as_str(), "user" | "the" | "an" | "my" | "your" | "their" | "his" | "her" | "valid" | "photo" | "national" | "card" | "badge" | "ego") {
                    return None;
                }
            }
            if let Some(ref n) = next {
                if matches!(n.as_str(), "like" | "love" | "want" | "prefer" | "rather" | "say" | "think" | "suggest" | "be" | "have" | "do" | "go" | "see" | "know" | "make" | "tell" | "ask" | "never" | "always" | "hope") {
                    return Some("I'd");
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
    if !SAFE_CONTRACTION_BARE.contains(&clean.as_str()) && !GLIDE_CONTRACTION_BARE.contains(&clean.as_str()) {
        return None;
    }
    CONTRACTIONS.iter().find(|(k, _)| *k == clean.as_str()).map(|&(_, c)| c)
}

/// `query` is `word` with exactly one letter inserted. True when that
/// letter looks like a finger slip — a bounce of the letter beside it
/// ("innto", "maybbe") or a key adjacent to one of its neighbours
/// ("inbto": b sits next to n). A letter from elsewhere on the keyboard
/// ("heis": e is nowhere near h or i) is not a slip of "his"; it is the
/// phrase "he is" missing its space.
pub fn inserted_letter_is_a_slip(query: &str, word: &str) -> bool {
    let q: Vec<char> = query.chars().collect();
    let w: Vec<char> = word.chars().collect();
    if q.len() != w.len() + 1 {
        return false;
    }
    let i = (0..w.len()).find(|&i| q[i] != w[i]).unwrap_or(w.len());
    let ch = q[i];
    let before = if i > 0 { Some(q[i - 1]) } else { None };
    let after = q.get(i + 1).copied();
    [before, after].into_iter().flatten().any(|n| n == ch || NlpEngine::is_spatial_keyboard_neighbor(ch, n))
}

/// True when `a` and `b` differ only by one pair of adjacent letters
/// swapped ("amking" / "making").
pub fn is_adjacent_transposition(a: &str, b: &str) -> bool {
    let a: Vec<char> = a.chars().collect();
    let b: Vec<char> = b.chars().collect();
    if a.len() != b.len() || a.len() < 2 {
        return false;
    }
    let Some(i) = (0..a.len()).find(|&i| a[i] != b[i]) else {
        return false;
    };
    i + 1 < a.len()
        && a[i] == b[i + 1]
        && a[i + 1] == b[i]
        && a[i + 2..] == b[i + 2..]
}

/// Plain Levenshtein edit count between two short tokens (insert, delete,
/// substitute all cost one). The fuzzy stage ranks on this before its
/// weighted units, because units cannot separate one dropped letter from
/// two adjacent slips. Tokens here are at most a few dozen chars.
pub fn edit_count(a: &str, b: &str) -> usize {
    let a: Vec<char> = a.chars().collect();
    let b: Vec<char> = b.chars().collect();
    if a.is_empty() {
        return b.len();
    }
    if b.is_empty() {
        return a.len();
    }
    let mut prev: Vec<usize> = (0..=b.len()).collect();
    let mut cur = vec![0usize; b.len() + 1];
    for (i, &ca) in a.iter().enumerate() {
        cur[0] = i + 1;
        for (j, &cb) in b.iter().enumerate() {
            let sub = prev[j] + usize::from(ca != cb);
            cur[j + 1] = sub.min(prev[j + 1] + 1).min(cur[j] + 1);
        }
        std::mem::swap(&mut prev, &mut cur);
    }
    prev[b.len()]
}

/// True when `word` is `typed_lower` with apostrophes added ("I'll" for
/// "ill", "it's" for "its"), case-insensitively.
pub fn is_apostrophe_variant(word: &str, typed_lower: &str) -> bool {
    if !word.contains('\'') && !word.contains('’') {
        return false;
    }
    let mut typed = typed_lower.chars();
    for ch in word.chars() {
        if ch == '\'' || ch == '’' {
            continue;
        }
        match typed.next() {
            Some(t) if t == ch.to_ascii_lowercase() => {}
            _ => return false,
        }
    }
    typed.next().is_none()
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
    /// (perf item 6) Behind its OWN lock: written per key-down by
    /// [`Self::record_touch_hit_biometrics`], read during suggest by
    /// [`Self::keys_near`]/[`Self::slip_oracle`]. The `Arc<RwLock<..>>` lets the
    /// record path hold only NLP_ENGINE.read() (plus this small write lock)
    /// instead of NLP_ENGINE.write(), so a background key-down no longer stalls
    /// in-flight suggest reads on the whole engine lock. Lock order is always
    /// engine-before-touch_model, so no path can deadlock.
    touch_model: Arc<RwLock<Option<crate::TouchModel>>>,
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
    /// Suppressed false-positive corrections (typo, wrong_word) -> rejection count (Idea 6 / Loops 16-18)
    rejected_corrections: std::collections::HashMap<(String, String), u32>,
    /// Last-used interaction epoch for learned words
    word_epochs: std::collections::HashMap<String, u64>,
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
            touch_model: Arc::new(RwLock::new(None)),
            bigrams: crate::bigram::BigramModel::default(),
            word_ids: std::collections::HashMap::new(),
            learned_words: std::collections::HashMap::new(),
            personal_bigrams: std::collections::HashMap::new(),
            rejected_corrections: std::collections::HashMap::new(),
            word_epochs: std::collections::HashMap::new(),
        }
    }

    /// Records an explicitly rejected / backspaced autocorrect to suppress sticky typos (Idea 6 / Loops 16-18).
    #[inline]
    pub fn record_rejected_correction(&mut self, typo: &str, wrong_suggestion: &str) {
        let t = typo.trim().to_ascii_lowercase();
        let w = wrong_suggestion.trim().to_ascii_lowercase();
        if !t.is_empty() && !w.is_empty() && t.len() <= 64 && w.len() <= 64 {
            // Capacity guard: keep map bounded to prevent heap exhaustion
            if self.rejected_corrections.len() >= 512 {
                self.rejected_corrections.retain(|_, &mut v| v >= 2);
            }
            let entry = self.rejected_corrections.entry((t, w)).or_insert(0);
            *entry = entry.saturating_add(1);
        }
    }

    /// Checks whether a suggestion has been repeatedly rejected by the user with zero-allocation fast path.
    #[inline]
    pub fn is_rejected_correction(&self, typo: &str, candidate: &str) -> bool {
        if self.rejected_corrections.is_empty() {
            return false;
        }
        let t = typo.trim().to_ascii_lowercase();
        let c = candidate.trim().to_ascii_lowercase();
        if let Some(&rejections) = self.rejected_corrections.get(&(t, c)) {
            return rejections >= 2;
        }
        false
    }

    /// Learns a word with recency epoch tracking (Idea 6 / Loops 16-18).
    #[inline]
    pub fn learn_word_with_decay(&mut self, word: &str, freq: u32, current_epoch: u64) {
        self.learn_word(word, freq);
        let trimmed = word.trim().to_ascii_lowercase();
        if !trimmed.is_empty() {
            self.word_epochs.insert(trimmed, current_epoch);
        }
    }

    /// Decays transient learned words that have not been reinforced within `half_life` epochs in a single retain pass (Idea 6 / Loop 18).
    #[inline]
    pub fn decay_learned_entries(&mut self, current_epoch: u64, half_life: u64) {
        if self.word_epochs.is_empty() {
            return;
        }
        let learned_ref = &mut self.learned_words;
        self.word_epochs.retain(|word, &mut epoch| {
            if current_epoch > epoch && (current_epoch - epoch) >= half_life {
                if let Some(&freq) = learned_ref.get(word) {
                    if freq <= 140 {
                        learned_ref.remove(word);
                        return false;
                    }
                }
            }
            true
        });
    }

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
        let mut words: Vec<(String, u32)> = self.learned_words.iter().map(|(w, &f)| (w.clone(), f)).collect();
        words.sort();
        let mut corrections = Vec::new();
        for (typo, targets) in &self.personal_corrections {
            for (intended, &n) in targets {
                corrections.push((typo.clone(), intended.clone(), n));
            }
        }
        corrections.sort();
        let mut bigrams: Vec<(String, String, u32)> = self
            .personal_bigrams
            .iter()
            .map(|((a, b), &n)| (a.clone(), b.clone(), n))
            .collect();
        // Keep the most-used pairs when over the cap.
        bigrams.sort_by(|x, y| y.2.cmp(&x.2).then_with(|| x.cmp(y)));
        
        let mut rejected: Vec<(String, String, u32)> = self
            .rejected_corrections
            .iter()
            .map(|((t, w), &n)| (t.clone(), w.clone(), n))
            .collect();
        rejected.sort_by(|x, y| y.2.cmp(&x.2).then_with(|| x.cmp(y)));

        let mut word_epochs: Vec<(String, u64)> = self
            .word_epochs
            .iter()
            .map(|(w, &ep)| (w.clone(), ep))
            .collect();
        word_epochs.sort_by(|x, y| y.1.cmp(&x.1).then_with(|| x.cmp(y)));

        let state = crate::persist::LearnedState {
            version: crate::persist::LEARNED_VERSION,
            words,
            corrections,
            bigrams,
            rejected,
            word_epochs,
        };
        state.serialize()
    }

    /// Restores learned state from a CRKL blob: learned words re-enter the
    /// trie, correction habits, personal bigrams, rejected corrections, and
    /// decay epochs. Returns how many words were restored; a corrupt blob
    /// restores nothing and errors.
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
        // Corrections from a pre-v4 blob were keyed on the ENGINE's own
        // substitutions (the recorder bug fixed 2026-09-13): "in" -> "that",
        // "that" -> "in", "with" -> "that" on Lochran's phone by 2026-09-18.
        // None of them can be trusted; the map starts again from clean
        // observations. Words, bigrams, rejections and epochs are kept.
        let trusted_corrections = if state.version >= 4 { state.corrections } else { Vec::new() };
        for (typo, intended, n) in trusted_corrections {
            // Purge pairs keyed on an everyday word: never a real habit.
            if self.corpus_freq(&typo) >= 236 {
                continue;
            }
            let counter = self.personal_corrections.entry(typo).or_default();
            let slot = counter.entry(intended).or_insert(0);
            *slot = (*slot).max(n);
        }
        for (w1, w2, n) in state.bigrams {
            let slot = self.personal_bigrams.entry((w1, w2)).or_insert(0);
            *slot = (*slot).max(n);
        }
        for (typo, wrong, n) in state.rejected {
            let slot = self.rejected_corrections.entry((typo, wrong)).or_insert(0);
            *slot = (*slot).max(n);
        }
        for (word, epoch) in state.word_epochs {
            let slot = self.word_epochs.entry(word).or_insert(0);
            *slot = (*slot).max(epoch);
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

            let is_trigram_match = matches!(
                (t1, t2, c),
                ("thank", "you", "so")
                    | ("thank", "you", "very")
                    | ("thank", "you", "much")
                    | ("thank", "you", "all")
                    | ("how", "are", "you")
                    | ("how", "are", "things")
                    | ("let", "me", "know")
                    | ("let", "me", "see")
                    | ("let", "me", "tell")
                    | ("in", "order", "to")
                    | ("as", "well", "as")
                    | ("as", "soon", "as")
                    | ("as", "far", "as")
                    | ("on", "the", "other")
                    | ("on", "the", "way")
                    | ("at", "the", "same")
                    | ("at", "the", "moment")
                    | ("at", "the", "end")
                    | ("see", "you", "later")
                    | ("see", "you", "soon")
                    | ("see", "you", "there")
                    | ("have", "a", "good")
                    | ("have", "a", "great")
                    | ("have", "a", "nice")
                    | ("i", "am", "going")
                    | ("i", "am", "doing")
                    | ("i", "am", "not")
                    | ("i", "am", "sure")
                    | ("you", "want", "to")
                    | ("you", "need", "to")
                    | ("you", "have", "to")
            );

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
        *self.touch_model.write().unwrap() = model;
    }

    /// Applies one biometric touch hit to the touch model in place. Takes only a
    /// SHARED (`&self`) borrow of the engine — the model has its own lock — so the
    /// per-key-down record path holds `NLP_ENGINE.read()` plus this small write
    /// lock instead of `NLP_ENGINE.write()`, and no longer blocks in-flight
    /// suggest reads on the engine lock (perf item 6). No-op when no layout has
    /// been uploaded, exactly as the old `touch_model_mut()`-guarded call was.
    pub fn record_touch_hit_biometrics(
        &self,
        ch: char,
        x: f32,
        y: f32,
        major: f32,
        minor: f32,
        orientation: f32,
    ) {
        if let Some(model) = self.touch_model.write().unwrap().as_mut() {
            model.record_touch_hit_with_biometrics(ch, x, y, major, minor, orientation);
        }
    }

    /// Whether typing `b` while meaning `a` is a plausible physical slip:
    /// answered by the Gaussian touch model when a layout has been uploaded,
    /// by the static adjacency table otherwise. Governs the weighted fuzzy
    /// search and merge repair; the ranking-side `is_spatial_slip_match`
    /// deliberately keeps the static table (more permissive is harmless for
    /// ordering, and that function has other callers).
    ///
    /// This convenience form locks the touch model per call; the suggest hot
    /// paths instead take one read guard for the whole call and feed
    /// [`Self::slip_oracle`], so their view is frozen (no mid-suggest change) and
    /// each char-pair comparison is a plain match rather than a lock acquisition.
    pub fn keys_near(&self, a: char, b: char) -> bool {
        Self::slip_oracle(&self.touch_model.read().unwrap(), a, b)
    }

    /// The slip oracle against an already-borrowed touch-model snapshot.
    #[inline]
    fn slip_oracle(model: &Option<crate::TouchModel>, a: char, b: char) -> bool {
        match model {
            Some(m) => m.is_near(a, b),
            None => Self::is_spatial_keyboard_neighbor(a, b),
        }
    }

    /// Shipped-corpus frequency, falling back to the learned frequency for a
    /// word the corpus never shipped (a taught name still competes). Used
    /// wherever two dictionary words are weighed against each other for an
    /// auto-commit: personal boosts must not decide that, because the
    /// engine's own wrong commits get learned back as "corrections" (+15
    /// each) — six of them lifted "lille" to 255 on a real phone, above
    /// "like" (2026-09-13).
    fn corpus_or_learned(&self, word: &str, learned: u32) -> u32 {
        let c = self.corpus_freq(word);
        if c > 0 { c } else { learned }
    }

    /// True when a same-length adjacent-key substitution of `query_lower`
    /// is a CLEARLY commoner word than `word` (corpus frequencies). The
    /// guess stages (swap, doubled letter) run before the fuzzy stage and
    /// used to claim the auto-commit unconditionally; this is how they
    /// yield to the correction the fuzzy stage will make. The margin
    /// matters: a swap of one common word is often also a slip of another
    /// ("srory" is sorry or story, "wno" is won or who, "ahd" is had or
    /// and), and there the swap — the user's own letters, reordered — is
    /// the safer reading. Only a rare swap target loses ("heh" 188 vs
    /// "the" 255, "nod" 191 vs "and" 254, "lille" 170 vs "like" 254).
    fn outranked_by_adjacent_slip(&self, query_lower: &str, word: &str, learned_freq: u32) -> bool {
        const SLIP_YIELD_MARGIN: u32 = 20;
        let base = self.corpus_or_learned(word, learned_freq).saturating_add(SLIP_YIELD_MARGIN);
        let touch = self.touch_model.read().unwrap();
        if self
            .trie
            .fuzzy_search_weighted(query_lower, 1, 4, |a, b| Self::slip_oracle(&touch, a, b))
            .iter()
            .any(|fc| fc.distance == 1 && self.corpus_or_learned(&fc.word, fc.frequency) >= base)
        {
            return true;
        }
        drop(touch);
        // A top-tier word one FAR substitution away also outranks a guess:
        // "ttis" is this, "hhat" is that, whatever the key geometry says
        // about t and h (review 2026-09-18; the fuzzy stage ranks the same
        // reading first).
        if !query_lower.is_ascii() {
            return false;
        }
        let b = query_lower.as_bytes();
        let mut buf = String::with_capacity(b.len());
        for i in 0..b.len() {
            for c in b'a'..=b'z' {
                if c == b[i] {
                    continue;
                }
                buf.clear();
                buf.push_str(&query_lower[..i]);
                buf.push(c as char);
                buf.push_str(&query_lower[i + 1..]);
                let cf = self.corpus_freq(&buf);
                if cf >= 236 && cf >= base {
                    return true;
                }
            }
        }
        false
    }

    /// Whether `token` is still the beginning of a longer everyday word,
    /// i.e. the person may not have finished typing it. Used to refuse a
    /// mid-word replacement (2026-09-19).
    fn is_live_prefix(&self, token: &str) -> bool {
        // 200, not the usual 236: "fini" continues into finished 233 and
        // finish 232, and those are exactly the words being typed when the
        // space bar is bumped.
        const LIVE_PREFIX_MIN_FREQ: u32 = 200;
        self.trie
            .prefix_search(token, 8)
            .iter()
            .any(|(w, _)| w.chars().count() > token.chars().count() && self.corpus_freq(w) >= LIVE_PREFIX_MIN_FREQ)
    }

    /// How many everyday words (corpus >= 236) `token` could still become.
    /// Two or more and the word in progress is anyone's guess: "unde" is
    /// under AND understand, so neither may be committed over the other.
    fn top_tier_continuations(&self, token: &str) -> usize {
        self.trie
            .prefix_search(token, 8)
            .iter()
            .filter(|(w, _)| w.chars().count() > token.chars().count() && self.corpus_freq(w) >= 236)
            .count()
    }

    /// Every everyday word (corpus >= 236) that is `token` with a single
    /// adjacent-key substitution. Used to tell a token with one obvious
    /// reading from one that could be either of two words.
    fn single_slip_rivals(&self, token: &str) -> Vec<(String, u32)> {
        let mut out = Vec::new();
        if !token.is_ascii() {
            return out;
        }
        let b = token.as_bytes();
        for i in 0..b.len() {
            for c in b'a'..=b'z' {
                if c == b[i] || !Self::is_spatial_keyboard_neighbor(b[i] as char, c as char) {
                    continue;
                }
                let w = format!("{}{}{}", &token[..i], c as char, &token[i + 1..]);
                let f = self.corpus_freq(&w);
                if f >= 236 {
                    out.push((w, f));
                }
            }
        }
        out
    }

    /// Whether some top-tier word (corpus >= 236), other than the two
    /// halves, is one edit (insert, delete, substitute, adjacent swap) from
    /// `token`. Enumerated directly: the fuzzy search's short result list
    /// can miss a deletion rival behind a crowd of substitutions.
    fn top_tier_one_edit_rival(&self, token: &str, left: &str, right: &str) -> bool {
        if !token.is_ascii() {
            return false;
        }
        let top = |w: &str| w != left && w != right && w != token && self.corpus_freq(w) >= 236;
        let b = token.as_bytes();
        let mut buf = String::with_capacity(token.len() + 1);
        for i in 0..=b.len() {
            // insertion
            for c in b'a'..=b'z' {
                buf.clear();
                buf.push_str(&token[..i]);
                buf.push(c as char);
                buf.push_str(&token[i..]);
                if top(&buf) {
                    return true;
                }
            }
            if i < b.len() {
                // deletion
                buf.clear();
                buf.push_str(&token[..i]);
                buf.push_str(&token[i + 1..]);
                if top(&buf) {
                    return true;
                }
                // substitution
                for c in b'a'..=b'z' {
                    if c == b[i] {
                        continue;
                    }
                    buf.clear();
                    buf.push_str(&token[..i]);
                    buf.push(c as char);
                    buf.push_str(&token[i + 1..]);
                    if top(&buf) {
                        return true;
                    }
                }
                // adjacent swap
                if i + 1 < b.len() && b[i] != b[i + 1] {
                    buf.clear();
                    buf.push_str(&token[..i]);
                    buf.push(b[i + 1] as char);
                    buf.push(b[i] as char);
                    buf.push_str(&token[i + 2..]);
                    if top(&buf) {
                        return true;
                    }
                }
            }
        }
        false
    }

    /// Whether a top-tier word (corpus >= 236 and >= `at_least`) is `token`
    /// with one letter removed.
    fn one_deletion_rival(&self, token: &str, at_least: u32) -> bool {
        if !token.is_ascii() {
            return false;
        }
        (0..token.len()).any(|i| {
            let w = format!("{}{}", &token[..i], &token[i + 1..]);
            let cf = self.corpus_freq(&w);
            cf >= 236 && cf >= at_least
        })
    }

    /// Whether a top-tier word (corpus >= 236 and >= `at_least`) is `token`
    /// with one letter added anywhere.
    fn one_insertion_rival(&self, token: &str, at_least: u32) -> bool {
        if !token.is_ascii() {
            return false;
        }
        let mut buf = String::with_capacity(token.len() + 1);
        for i in 0..=token.len() {
            for c in b'a'..=b'z' {
                buf.clear();
                buf.push_str(&token[..i]);
                buf.push(c as char);
                buf.push_str(&token[i..]);
                let cf = self.corpus_freq(&buf);
                if cf >= 236 && cf >= at_least {
                    return true;
                }
            }
        }
        false
    }

    /// The corpus frequency of the best adjacent transposition of `token`
    /// that is a top-tier word (>= 236) and not itself outranked by an
    /// adjacent-key slip.
    fn top_tier_adjacent_swap(&self, token: &str) -> Option<u32> {
        if !token.is_ascii() {
            return None;
        }
        let b = token.as_bytes();
        let mut best: Option<u32> = None;
        for i in 0..b.len().saturating_sub(1) {
            if b[i] == b[i + 1] {
                continue;
            }
            let w = format!("{}{}{}{}", &token[..i], b[i + 1] as char, b[i] as char, &token[i + 2..]);
            let cf = self.corpus_freq(&w);
            if cf >= 236
                && best.is_none_or(|bf| cf > bf)
                && !self.outranked_by_adjacent_slip(token, &w, self.trie.get_frequency(&w).unwrap_or(cf))
            {
                best = Some(cf);
            }
        }
        best
    }

    /// Whether `token` opens with a strongly attested two-word pair
    /// ("alotof" opens with "a lot", "inabit" with "in a"): the sign of a
    /// three-word run-together the two-way splitter cannot produce.
    fn has_strong_prefix_pair(&self, token: &str) -> bool {
        const STRONG_PAIR: u8 = 200;
        // the same "solidly real" floor the commit stages use
        const AUTOCOMMIT_MIN_FREQ: u32 = 150;
        if !token.is_ascii() || token.len() < 5 || self.bigrams.is_empty() {
            return false;
        }
        // The remainder must be a word too: "orane" opens with "or a" but
        // "ne" is nothing, so it is a slip of orange, not a phrase (review
        // 2026-09-18).
        let solid = |w: &str| self.trie.get_frequency(w).unwrap_or(0) >= AUTOCOMMIT_MIN_FREQ;
        for i in 1..token.len() - 1 {
            let left = &token[..i];
            if !solid(left) {
                continue;
            }
            for j in i + 1..token.len() {
                let mid = &token[i..j];
                let rest = &token[j..];
                // single letters ("y", "t") are dictionary entries but not
                // words a phrase ends in: "toally" is totally
                let word_like = |w: &str| w.len() >= 2 || w == "a" || w == "i";
                if word_like(mid)
                    && rest.len() >= 2
                    && solid(mid)
                    && solid(rest)
                    && self.bigram_pair_score(left, mid) >= STRONG_PAIR
                {
                    return true;
                }
            }
        }
        false
    }

    /// Whether a proposed missing-space split `left right` of `query_lower`
    /// should yield to a single-word reading. A blocker is a top-tier word
    /// (corpus >= `min_freq`) that is NOT one of the halves (dropping the
    /// space of "a bit" is by construction one edit from "bit", so the
    /// halves can never testify against their own split — review
    /// 2026-09-13, which found "abit" -> "bait", "imean" -> "imran") and is
    /// either one adjacent-key substitution away ("agout" -> about) or one
    /// insertion/deletion away ("abut" -> about, "ared" -> are). A same-
    /// length two-unit reading ("onmy" -> only, "tobe" -> time) is too weak
    /// to block a phrase. A strongly attested pair (>= `override_pair`)
    /// is never blocked at all: "to be" at 231 is what "tobe" means.
    fn split_blocked_by_single_word(
        &self,
        query_lower: &str,
        left: &str,
        right: &str,
        pair_score: u8,
        min_freq: u32,
        override_pair: u8,
        direct: bool,
    ) -> bool {
        const SPLIT_PAIR_BEATS_ADJACENT_SLIP: u8 = 220;
        // Structural slips are judged against top-tier words ("almost",
        // "maybe", "often" sit at 244).
        const STRUCTURAL_BLOCKER_MIN_FREQ: u32 = 236;
        // "a"/"i" + word is the commonest missed space there is: only a
        // top-50 word one slip away ("what" for "ahat", "another" for
        // "amother") outweighs it, and only when the pair is not itself
        // strong ("a bit" 200, "a few" 211 stand against "shit"/"area").
        const ARTICLE_SPLIT_BLOCKER_MIN_FREQ: u32 = 250;
        const ARTICLE_SPLIT_UNBLOCKABLE_PAIR: u8 = 210;
        let query_len = query_lower.chars().count();
        let touch = self.touch_model.read().unwrap();
        self.trie
            .fuzzy_search_weighted(query_lower, 2, 8, |a, b| Self::slip_oracle(&touch, a, b))
            .iter()
            .any(|fc| {
                if fc.word == left || fc.word == right {
                    return false;
                }
                let cf = self.corpus_freq(&fc.word);
                if cf < min_freq {
                    return false;
                }
                let word_len = fc.word.chars().count();
                // A typing slip of the single word that is never a phrase:
                // two letters swapped ("amking" is making, "toher" is
                // other, "menas" is means) or one letter bounced in
                // ("maybbe" is maybe, "innto" is into). These block even a
                // strongly attested pair.
                let structural_slip = (word_len + 1 == query_len
                    && edit_count(query_lower, &fc.word) == 1
                    && inserted_letter_is_a_slip(query_lower, &fc.word))
                    || is_adjacent_transposition(query_lower, &fc.word);
                if structural_slip {
                    return cf >= STRUCTURAL_BLOCKER_MIN_FREQ;
                }
                if left.chars().count() == 1 {
                    let one_slip = fc.distance == 1 || (fc.distance == 2 && word_len != query_len);
                    return one_slip
                        && cf >= ARTICLE_SPLIT_BLOCKER_MIN_FREQ
                        && pair_score < ARTICLE_SPLIT_UNBLOCKABLE_PAIR;
                }
                if fc.distance == 1 {
                    // An adjacent-key slip of a common word ("tosay" is
                    // today, "toan" is town) outweighs all but the very
                    // strongest pairs ("to be" 231).
                    return pair_score < SPLIT_PAIR_BEATS_ADJACENT_SLIP;
                }
                // One letter dropped or added ("abut" is about, "ared" is
                // are) yields to a well attested pair ("he is" 199). An
                // added letter only counts when it looks like a slip; a
                // letter from across the keyboard is the missing space
                // ("notme" is "not me", not "note").
                // The space beam is the exception: b/n/m/v ARE the keys a
                // thumb hits instead of space ("somebone", "tobday"), so
                // there the far letter is evidence for the single word.
                let plausible = !direct || word_len >= query_len || inserted_letter_is_a_slip(query_lower, &fc.word);
                fc.distance == 2 && word_len != query_len && plausible && pair_score < override_pair
            })
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

    /// Pre-sizes the corpus structures for a known word count (the CRKD
    /// header carries it), avoiding rehash/regrow churn during the
    /// startup bulk load.
    pub fn reserve_corpus(&mut self, additional: usize) {
        self.corpus_words.reserve(additional);
        self.word_ids.reserve(additional);
        self.learned_words.reserve(64);
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
        // The CRKL reader rejects tokens over MAX_TOKEN_LEN bytes: one
        // oversized pair made the whole learned-state blob unreadable at the
        // next launch, and everything learned was lost (review 2026-09-13).
        if typo.len() > crate::persist::MAX_TOKEN_LEN || intended.len() > crate::persist::MAX_TOKEN_LEN {
            return;
        }
        // An everyday word is never a "typo" to remember a correction for
        // (see suggest stage 0): refuse the pair at the door.
        if self.corpus_freq(&typo) >= 236 {
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

    /// The user's own strongest mapping for a typed token, with how many
    /// times it was observed. Observations come from the Kotlin rewind
    /// tracker (erase a word, retype it as another) and from revert-then-
    /// accept; the engine's own auto-commits never count (2026-09-13).
    pub fn personal_correction_with_count(&self, typo: &str) -> Option<(&str, u32)> {
        let typo = typo.trim().to_ascii_lowercase();
        let targets = self.personal_corrections.get(&typo)?;
        targets
            .iter()
            .max_by(|a, b| a.1.cmp(b.1).then_with(|| b.0.cmp(a.0)))
            .map(|(t, &n)| (t.as_str(), n))
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
        if trimmed.len() >= 2 && trimmed.len() <= crate::persist::MAX_TOKEN_LEN {
            self.trie.boost_or_insert(&trimmed, 15);
            // Same ceiling as learn_word: a personal boost lifts a shipped
            // word at most 30 above its corpus frequency. Uncapped, six
            // boosts took "lille" to 255 (review 2026-09-13); the personal
            // map now applies corrections directly, so the boost only has
            // to keep a taught word visible, not win frequency contests.
            let base = self.corpus_freq(&trimmed).max(150);
            let ceiling = base.saturating_add(30).min(255);
            if let Some(freq) = self.trie.get_frequency(&trimmed) {
                let capped = freq.min(ceiling.max(self.corpus_freq(&trimmed)));
                if capped != freq {
                    self.trie.insert(&trimmed, capped);
                }
                self.insert_learned_capped(trimmed.clone(), capped);
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
        if query.is_empty() || candidate.is_empty() {
            return candidate.to_string();
        }
        let mut chars = ['\0'; 32];
        let mut len = 0;
        for ch in query.chars() {
            if len < 32 {
                chars[len] = ch;
            }
            len += 1;
        }
        let slice = &chars[..len.min(32)];
        let is_all_upper = len > 1 && slice.iter().all(|&c| !c.is_alphabetic() || c.is_uppercase());
        
        // Accidental CapsLock Inversion (e.g. tHIS -> This, hELLO -> Hello)
        let is_inverted_caps = len >= 3 
            && slice[0].is_lowercase() 
            && slice[1..].iter().all(|&c| !c.is_alphabetic() || c.is_uppercase());
            
        // Accidental Double-Shift (e.g. THis -> This, HEllo -> Hello)
        let is_double_shift = len >= 3 
            && slice[0].is_uppercase() 
            && slice[1].is_uppercase() 
            && slice[2..].iter().all(|&c| !c.is_alphabetic() || c.is_lowercase());

        let is_first_upper = slice.first().is_some_and(|c| c.is_uppercase());

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
        if !(3..=24).contains(&len) || !clean.is_ascii() {
            return None;
        }

        let mut best_split = None;
        let mut best_score = -1.0f32;
        let single_freq = self.trie.get_frequency(&clean).unwrap_or(0);

        // 1. Direct split: clean = L1 + L2 (0 edits, e.g. "inorder" -> "in order", "aswell" -> "as well")
        if single_freq < 150 {
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

                        if freq_score > best_score {
                            best_score = freq_score;
                            best_split = Some(format!("{} {}", left_s, right_s));
                        }
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

        if p_clean.is_empty() || c_clean.is_empty() || p_clean.len() + c_clean.len() > 32 {
            return None;
        }

        let mut merged_buf = [0u8; 32];
        let p_bytes = p_clean.as_bytes();
        let c_bytes = c_clean.as_bytes();
        let m_len = p_bytes.len() + c_bytes.len();
        merged_buf[..p_bytes.len()].copy_from_slice(p_bytes);
        merged_buf[p_bytes.len()..m_len].copy_from_slice(c_bytes);
        let merged_str = std::str::from_utf8(&merged_buf[..m_len]).ok()?;

        if let Some(m_freq) = self.trie.get_frequency(merged_str) {
            if m_freq >= 120 {
                let pair_score = self.bigram_pair_score(&p_clean, &c_clean);
                if pair_score < 40 {
                    return Some(SpaceBeamCandidate {
                        text: merged_str.to_string(),
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
        if raw_token.len() >= 2 && raw_token.len() <= 32 {
            let mut raw_buf = ['\0'; 32];
            let mut raw_len = 0;
            for ch in raw_token.chars() {
                if raw_len < 32 {
                    raw_buf[raw_len] = ch;
                    raw_len += 1;
                }
            }
            if raw_len >= 2 {
                for i in 0..(raw_len - 1) {
                    let hand1 = get_key_hand(raw_buf[i]);
                    let hand2 = get_key_hand(raw_buf[i + 1]);
                    if hand1 != Hand::Unknown && hand2 != Hand::Unknown && hand1 != hand2 {
                        raw_buf.swap(i, i + 1);
                        let candidate_str: String = raw_buf[..raw_len].iter().collect();
                        raw_buf.swap(i, i + 1);

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
        let touch = self.touch_model.read().unwrap();
        self.trie
            .fuzzy_search_weighted(&joined, 1, 4, |a, b| Self::slip_oracle(&touch, a, b))
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
        let touch = self.touch_model.read().unwrap();
        let witnessed: Vec<String> = self
            .trie
            .fuzzy_search_weighted(&joined, 2, 8, |a, b| Self::slip_oracle(&touch, a, b))
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
        let touch = self.touch_model.read().unwrap();
        let witnessed: Vec<String> = self
            .trie
            .fuzzy_search_weighted(&joined, 4, 8, |a, b| Self::slip_oracle(&touch, a, b))
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
        self.suggest_with_context_opts(query, prev_word, max_candidates, true)
    }

    /// `include_personal = false` is the private-session contract: nothing
    /// this keyboard has learned about its user — the personal-correction
    /// map — reaches the screen the user marked private (review
    /// 2026-09-13; next-word prediction already honoured it).
    pub fn suggest_with_context_opts(
        &self,
        query: &str,
        prev_word: &str,
        max_candidates: usize,
        include_personal: bool,
    ) -> SuggestionResult {
        // Typographic apostrophes are the same key on a phone: normalise so
        // "can’t" is the word it is and not a far slip of "cant" (hunt
        // 2026-09-18).
        let normalized: String = query
            .trim()
            .chars()
            .map(|c| match c {
                '’' | '‘' | '´' | '`' => '\'',
                o => o,
            })
            .collect();
        let trimmed = normalized.as_str();
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
        if let Some(&(_, brand_casing)) = TECH_BRAND_CASING
            .iter()
            .chain(GIVEN_NAME_CASING.iter())
            .find(|&&(k, _)| k == trimmed_lower)
        {
            // Nothing to offer when it is already written that way, and
            // pushing it anyway put the word in the strip twice.
            if trimmed != brand_casing {
                candidates.push(RankedCandidate {
                    word: brand_casing.to_string(),
                    is_autocorrect: !has_internal_uppercase,
                });
            }
        }

        // Helper to check if a word is already in candidate list
        let contains_word = |list: &[RankedCandidate], w: &str| list.iter().any(|c| c.word.eq_ignore_ascii_case(w));

        // 0. The user's own corrections come first. A token they have erased
        // and retyped as the same other word at least twice is theirs to
        // define ("beither" -> "brother"), valid word or not; a single
        // observation only earns a visible suggestion. Observations are per
        // user and never come from the engine's own auto-commits; two
        // reverts still retire the flip (rejected_corrections, below).
        // Until 2026-09-13 this map was recorded and persisted but never
        // read — its only effect was a frequency boost.
        const PERSONAL_AUTOCOMMIT_MIN_OBSERVATIONS: u32 = 2;
        const PERSONAL_KEY_MAX_CORPUS_FREQ: u32 = 236;
        // A word everyone types constantly ("in", "that", "the") is never
        // the user's to redefine through this map: the pre-2026-09-13
        // recording path keyed corrections on the ENGINE's substituted word,
        // so phones carry pairs like "in" -> "that" that fired on every
        // "in" the moment the map went live (field report, same day).
        let key_is_everyday_word = self.corpus_freq(&trimmed_lower) >= PERSONAL_KEY_MAX_CORPUS_FREQ;
        let personal: Option<(String, u32)> = if include_personal && !key_is_everyday_word {
            self.personal_correction_with_count(&trimmed_lower)
                .filter(|(intended, _)| !intended.eq_ignore_ascii_case(&trimmed_lower))
                // The apostrophised twin of the typed token ("ill" ->
                // "i'll", recorded from every hand-retyped I'll before the
                // engine learned to do it) is the contraction stage's
                // business, with its casing and context gate; the map does
                // not override that (review 2026-09-13).
                .filter(|(intended, _)| !is_apostrophe_variant(intended, &trimmed_lower))
                .map(|(intended, n)| {
                    // Targets are stored lowercased; a known contraction
                    // gets its display form back ("i'm" -> "I'm").
                    let display = contraction_display(intended).unwrap_or(intended);
                    (Self::apply_casing(trimmed, display), n)
                })
        } else {
            None
        };
        let personal_pin: Option<String> = personal.as_ref().map(|(w, _)| w.clone());
        if let Some((formatted, n)) = &personal {
            if *n >= PERSONAL_AUTOCOMMIT_MIN_OBSERVATIONS {
                candidates.push(RankedCandidate {
                    word: formatted.clone(),
                    is_autocorrect: true,
                });
            }
        }

        // A junk-band dictionary entry that is really a chat prefix run into
        // a common word ("iam" 60, "imnot") must not shield itself behind
        // the exact-word rule: the splitter gets it (hunt 2026-09-18).
        // The pair must be attested: the 130-149 band holds real words
        // ("irate", "imparts", "wonton") that a bare prefix test would
        // split into "I rate", "I'm parts", "won't on" (review 2026-09-18).
        let junk_exact_chat_split = is_exact
            && self.corpus_freq(&trimmed_lower) < AUTOCOMMIT_MIN_FREQ
            && chat_prefix_of(&trimmed_lower).is_some_and(|(p, rest)| {
                (self.corpus_freq(rest) >= 236 || CONTRACTIONS.binary_search_by_key(&rest, |&(k, _)| k).is_ok())
                    && (self.bigrams.is_empty() || self.bigram_pair_score(p, rest) > 0)
            });

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
            // A code typed in capitals ("BC", "NW", "RN", "FAQ") is an
            // initialism the person meant; expanding it shouted "500
            // BECAUSE" and "london NO WORRIES" (hunt 2026-09-18). Codes that
            // map to themselves (lol -> lol) are only casing fixes.
            let typed_caps = trimmed.chars().count() > 1
                && trimmed.chars().all(|c| !c.is_alphabetic() || c.is_uppercase());
            let self_map = shorthand.expansion.eq_ignore_ascii_case(&trimmed_lower);
            // After a number the code is a unit or an era ("500 gm", "300
            // bc"), never the phrase (hunt 2026-09-18).
            const UNIT_OR_ERA_CODES: &[&str] = &["bc", "dm", "gm", "nm"];
            let prev_t = prev_word.trim();
            let after_number = UNIT_OR_ERA_CODES.contains(&trimmed_lower.as_str())
                && prev_t.chars().any(|c| c.is_ascii_digit())
                && prev_t.chars().all(|c| c.is_ascii_digit() || matches!(c, ',' | '.'));
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: shorthand.is_autocorrect
                        && !after_number
                        && (self_map || (!typed_caps && !has_internal_uppercase)),
                });
            }
        } else if let Some(&(_, hyphenated)) = Self::COMPOUND_HYPHEN_PHRASES.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 2b. Compound hyphenated technical & conversational phrase recovery
            let formatted = Self::apply_casing(trimmed, hyphenated);
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: true,
                });
            }
        } else if let Some(typo_fix) = lookup_common_typo(&trimmed_lower).filter(|fix| {
            // A dictionary word the person typed is not a "common typo":
            // the Wikipedia table carried British spellings and plain
            // words as keys ("favourite" -> favorite, "owed" -> word,
            // "alright" -> all right, "mt" -> my; hunt 2026-09-18). Genuine
            // misspellings that ship in the dictionary sit under the floor
            // ("teh" 60, "thier" 152) and still fix.
            TYPO_CORPUS_KNOWN_JUNK.contains(&trimmed_lower.as_str())
                || !(is_exact && self.corpus_freq(&trimmed_lower) >= TYPO_CORPUS_REAL_WORD_FLOOR && !fix.contains('\''))
        }) {
            // 3. Wikipedia 1,770+ Misspelling Corpus instant O(L log N) lookup.
            // Internal uppercase means a deliberate abbreviation, not a slip:
            // "CNA"/"HSE"/"YoY" stay typed; sentence-start "Teh" still fixes.
            let formatted = Self::apply_casing(trimmed, typo_fix);
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: !has_internal_uppercase,
                });
            }
        } else if let Some(&(_, contraction)) = CONTRACTIONS.iter().find(|&&(k, _)| k == trimmed_lower) {
            // 4. Known contraction handling:
            // High-confidence unambiguous contractions (dont, cant, aint, wont, yall, etc.) auto-correct.
            // Ambiguous words (well, were, shed, wed, hell, its) do NOT auto-correct over exact word.
            // "shell" and "lets" are everyday words ("the shell", "she lets
            // me"): kept as typed, contraction offered (hunt 2026-09-18).
            let is_ambiguous = matches!(trimmed_lower.as_str(), "well" | "were" | "shed" | "wed" | "hell" | "its" | "ill" | "id" | "shell" | "lets");
            // A 2-3 letter token typed in capitals is an initialism (IM, ID,
            // ILL), not a shouted contraction.
            let caps_initialism = trimmed.chars().count() <= 3 && trimmed.chars().all(|c| c.is_uppercase());
            let formatted = Self::apply_casing(trimmed, contraction);
            // "ill" is ambiguous on paper, not in a chat: the dropped
            // apostrophe "I'll" is what gets typed, and the adjective is
            // announced by the word before it ("feel ill", "very ill").
            // Field report 2026-09-13: "Ill" committed at every sentence
            // start. The gate is the resolver's own table; two reverts
            // still retire the flip like any other correction.
            let ill_flips = trimmed_lower == "ill" && !ill_reads_as_adjective(Some(prev_word));
            if ill_flips {
                if !contains_word(&candidates, &formatted) {
                    candidates.push(RankedCandidate {
                        word: formatted,
                        is_autocorrect: !caps_initialism,
                    });
                }
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            } else if is_ambiguous && is_exact {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
                if !contains_word(&candidates, &formatted) {
                    candidates.push(RankedCandidate {
                        word: formatted,
                        is_autocorrect: false,
                    });
                }
            } else if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: !caps_initialism,
                });
            }
        } else if is_exact && !junk_exact_chat_split {
            // 5. Exact valid word -> NEVER auto-hijack
            candidates.push(RankedCandidate {
                word: trimmed.to_string(),
                is_autocorrect: false,
            });
        } else if (trimmed_lower.len() >= 4 || junk_exact_chat_split) && candidates.is_empty() {
            // 5b. Missing Space Splitter (e.g. andthe -> and the, inmy -> in my, tothe -> to the)
            // Both halves must be solidly real words: dictionary junk in
            // the demoted 60-band produced auto-committing garbage splits
            // ("adblock" -> "adb lock", "doona" -> "do ona", "tradies" ->
            // "tra dies", sweep 2026-08-27).
            const SPLIT_MIN_HALF_FREQ: u32 = 150;
            // A split is a GUESS too, and it used to claim the auto-commit
            // ahead of the fuzzy stage on nothing but "both halves are
            // words": sweep 2026-09-13 found 4,704 of 60,907 slip probes
            // turned into two-word phrases ("abut" -> "a but", "agout" ->
            // "a gout", "habe" -> "ha be", "weer" -> "we er"). Two gates:
            // the pair must be attested in the language model, and no
            // top-tier single word may sit within one edit of the typed
            // token — that word is the correction, and the fuzzy stage
            // commits it. Shipped fixtures stay well inside ("a bunch" 180,
            // "in my" 204, "and the" 234, "got to" 187).
            const SPLIT_MIN_PAIR_SCORE: u8 = 150;
            const SPLIT_YIELD_TO_WORD_FREQ: u32 = 236;
            // A pair attested this well is the phrase over a one-letter
            // add/drop reading ("he is" 199 over "his"; "be in" 201).
            // Adjacent-key slips need a stronger pair still (see
            // split_blocked_by_single_word).
            const SPLIT_UNBLOCKABLE_PAIR_SCORE: u8 = 185;
            // Capitalized tokens are names until proven otherwise: the
            // splitter was committing "Loch ran", "Field mark" and
            // "Anti gravity" (sweep 2026-08-27).
            let split_cap_blocked = trimmed.chars().next().is_some_and(|c| c.is_uppercase());
            // "bein", "sleepin", "wantin": a dropped g is a register, not a
            // missing space ("be in", "sleep in"). The typed form leads and
            // nothing auto-commits (hunt 2026-09-18).
            let g_dropped = trimmed_lower.len() >= 4 && trimmed_lower.ends_with("in") && {
                let ing = self.trie.get_frequency(&format!("{trimmed_lower}g")).unwrap_or(0);
                let stem = &trimmed_lower[..trimmed_lower.len() - 2];
                let stem_is_verb = self.trie.get_frequency(stem).unwrap_or(0) >= AUTOCOMMIT_MIN_FREQ
                    || self.trie.get_frequency(&format!("{stem}e")).unwrap_or(0) >= AUTOCOMMIT_MIN_FREQ;
                // top-tier -ing words ("nothin", "mornin") or a real verb
                // stem ("sleepin", "happenin", "textin"); an accidental
                // drop inside "-tion" ("statin") has neither
                // ... unless the two words are a strong pair of their own
                // ("not in" 194, "back in" 190, "sign in" 206), which is the
                // phrase over a one-letter-drop reading (review 2026-09-18)
                ing >= 236
                    || (ing >= AUTOCOMMIT_MIN_FREQ
                        && stem_is_verb
                        && self.bigram_pair_score(stem, "in") < SPLIT_UNBLOCKABLE_PAIR_SCORE)
            };
            if g_dropped {
                candidates.push(RankedCandidate {
                    word: trimmed.to_string(),
                    is_autocorrect: false,
                });
            }
            // A triple letter run is burst territory ("helllo"), never a
            // missing space: splitting won ("hell lo") because the splitter
            // runs first and both halves are real words. Let stage 5c
            // collapse it instead (sweep 2026-08-27).
            let mut char_iter = trimmed_lower.chars();
            let mut c1 = char_iter.next();
            let mut c2 = char_iter.next();
            let mut has_triple_run = false;
            for c3 in char_iter {
                if c1 == c2 && c2 == Some(c3) {
                    has_triple_run = true;
                    break;
                }
                c1 = c2;
                c2 = Some(c3);
            }
            if !has_triple_run && !split_cap_blocked && trimmed_lower.is_ascii() {
                // Every split point is weighed and the best attested pair
                // wins: "ishe" is "is he" (168), not the chat-prefix
                // reading "i she" (0) that happens to come first (sweep
                // 2026-09-18).
                let mut best_split: Option<(String, u8)> = None;
                for (split_idx, _) in trimmed_lower.char_indices().skip(1) {
                    if split_idx >= trimmed_lower.len() || !trimmed.is_char_boundary(split_idx) {
                        continue;
                    }
                    let left = &trimmed_lower[..split_idx];
                    let right = &trimmed_lower[split_idx..];
                    if (left.len() == 1 && left != "a" && left != "i")
                        || (right.len() == 1 && right != "a" && right != "i")
                    {
                        continue;
                    }
                    // A single letter doubled onto the word it starts
                    // ("aand", "iill") is a key bounce, not a missing
                    // space; the collapse stage owns it.
                    let doubled_lead = left.chars().count() == 1 && right.starts_with(left);
                    let pair = self.bigram_pair_score(left, right);
                    // A chat prefix ("i", "im", "dont", "u", "ur"...) run
                    // into a top-tier word or a contraction is the phrase
                    // even when the news-flavoured pair table barely knows
                    // it: "iforgot", "imsure", "illdo", "iwasnt" (hunt
                    // 2026-09-18: 928 of 1,029 "i"+verb tokens lost the
                    // pronoun; "illdo" became "dildo").
                    let chat_prefix = CHAT_PREFIXES.contains(&left);
                    // Only a pronoun prefix splits on NO attestation at all
                    // ("illdo", "imsure": the pair table has no apostrophe
                    // tokens); "dont"/"cant"/"wont" need a witnessed pair,
                    // or "wonton" is "won't on" (review 2026-09-18).
                    let pronoun_prefix = matches!(left, "i" | "im" | "id" | "ill" | "ive" | "its" | "u" | "ur" | "ya");
                    let right_is_contraction = CONTRACTIONS.binary_search_by_key(&right, |&(k, _)| k).is_ok();
                    // The relaxed gate is only for tokens that have no
                    // other top-tier reading: "ime" is time, "ino" is into,
                    // "istory" is history (sweep 2026-09-18), and those
                    // rivals can sit outside the blocker's short fuzzy list.
                    let pair_ok = if self.bigrams.is_empty() {
                        true
                    } else if pair >= SPLIT_MIN_PAIR_SCORE {
                        true
                    } else if chat_prefix {
                        (pair > 0 || (pronoun_prefix && (self.corpus_freq(right) >= 236 || right_is_contraction)))
                            && !self.top_tier_one_edit_rival(&trimmed_lower, left, right)
                    } else {
                        false
                    };
                    if !doubled_lead
                        && self.trie.get_frequency(left).unwrap_or(0) >= SPLIT_MIN_HALF_FREQ
                        && (self.trie.get_frequency(right).unwrap_or(0) >= SPLIT_MIN_HALF_FREQ || right_is_contraction)
                        // Without a language model there is nothing to
                        // attest against; the halves-only rule stands.
                        && pair_ok
                        && !self.split_blocked_by_single_word(
                            &trimmed_lower,
                            left,
                            right,
                            pair,
                            SPLIT_YIELD_TO_WORD_FREQ,
                            SPLIT_UNBLOCKABLE_PAIR_SCORE,
                            true,
                        )
                    {
                        // Halves are shown the way a typed word would be:
                        // a lone "i" is "I", a bare contraction gets its
                        // apostrophe ("idont" -> "I don't").
                        let show_right = |h: &str| -> String {
                            if h == "i" {
                                "I".to_string()
                            } else if let Some(c) = contraction_display(h) {
                                c.to_string()
                            } else {
                                h.to_string()
                            }
                        };
                        // Only the LEFT half is a run-in chat prefix
                        // ("illdo", "idlike", "itsok"); the right half is
                        // the word it is ("on its way" stays possessive;
                        // review 2026-09-18).
                        let show_left = |h: &str| -> String {
                            if CHAT_PREFIXES.contains(&h) {
                                if let Ok(i) = CONTRACTIONS.binary_search_by_key(&h, |&(k, _)| k) {
                                    return CONTRACTIONS[i].1.to_string();
                                }
                            }
                            show_right(h)
                        };
                        let formatted_left = Self::apply_casing(&trimmed[..split_idx], &show_left(left));
                        let formatted_right = show_right(right);
                        let split_phrase = format!("{} {}", formatted_left, formatted_right);
                        if best_split.as_ref().is_none_or(|(_, bp)| pair > *bp) {
                            best_split = Some((split_phrase, pair));
                        }
                    }
                }
                if let Some((split_phrase, _)) = best_split {
                    candidates.push(RankedCandidate {
                        word: split_phrase,
                        // a g-dropped form ("bein") keeps the typed
                        // word; the split stays one tap away
                        is_autocorrect: !g_dropped,
                    });
                }
            }
            
            // 5b-2. Space-beam fallback (Antigravity Idea 4, wired here):
            // catches what the direct splitter cannot — a fat thumb
            // hitting a bottom-row letter INSTEAD of space ("gotnto" ->
            // "got to"). Only when the splitter found nothing, and under
            // this file's commit discipline: never for capitalized input,
            // and the winning halves must each be solidly real (>= 150)
            // or the pair attested in the language model.
            let beam_triple_run = has_triple_run;
            if candidates.is_empty()
                && !trimmed.chars().next().is_some_and(|c| c.is_uppercase())
                && trimmed_lower.len() >= 5
                // burst territory ("helllo") collapses, never splits — the
                // same rule the direct splitter learned (iteration ~42).
                && !beam_triple_run
            {
                if let Some(beam) = self.evaluate_split_beam(&trimmed_lower) {
                    let mut parts = beam.text.splitn(2, ' ');
                    if let (Some(l), Some(r)) = (parts.next(), parts.next()) {
                        // Same two gates as the direct splitter ("cdan" was
                        // committing "cd an" over "can" on solid halves
                        // alone, sweep 2026-09-13).
                        let solid = |w: &str| self.trie.get_frequency(w).unwrap_or(0) >= 150;
                        let pair = self.bigram_pair_score(l, r);
                        let attested = if self.bigrams.is_empty() {
                            solid(l) && solid(r)
                        } else {
                            pair >= SPLIT_MIN_PAIR_SCORE
                        };
                        if attested
                            && !self.split_blocked_by_single_word(
                                &trimmed_lower,
                                l,
                                r,
                                pair,
                                SPLIT_YIELD_TO_WORD_FREQ,
                                SPLIT_UNBLOCKABLE_PAIR_SCORE,
                                false,
                            )
                        {
                            candidates.push(RankedCandidate {
                                word: beam.text,
                                is_autocorrect: true,
                            });
                        }
                    }
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
                    // A collapse is a guess like the swap and the doubled
                    // letter: "ttis" is "this" (t for h) far more often than
                    // "tis", "hhat" is "that", not "hat" (sweep 2026-09-13).
                    // A clearly commoner adjacent-key neighbour wins; a close
                    // call ("hhey": hey 238 vs they 254) keeps the collapse.
                    // Both collapses are weighed, not tried in order: a key
                    // bounce on a real double letter ("willl", "goood",
                    // "beeen") used to take the single form ("wil", "god",
                    // "ben") because it was checked first and happened to be
                    // a word. The commoner of the two is the word meant.
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
                    let hit = |w: &str| {
                        self.trie
                            .get_frequency(w)
                            .filter(|&f| !self.outranked_by_adjacent_slip(&trimmed_lower, w, f))
                            .map(|f| (w.to_string(), f, self.corpus_or_learned(w, f)))
                    };
                    // One bounce on a word that already has a double letter
                    // ("wwill", "goodd", "tooo", "needd") is one run one
                    // letter too long, not every run flattened: "wil",
                    // "god", "to", "needs" were committed (hunt 2026-09-18).
                    // The commonest one-run-shortened reading that is a
                    // real word wins; only when none is do the full
                    // collapses get weighed.
                    let mut one_run: Option<(String, u32, u32)> = None;
                    {
                        let cs: Vec<char> = trimmed_lower.chars().collect();
                        let mut i = 0;
                        while i < cs.len() {
                            let mut j = i;
                            while j + 1 < cs.len() && cs[j + 1] == cs[i] {
                                j += 1;
                            }
                            if j > i {
                                let mut v = cs.clone();
                                v.remove(i);
                                let v: String = v.into_iter().collect();
                                if let Some(h) = hit(&v) {
                                    if h.1 >= AUTOCOMMIT_MIN_FREQ
                                        && one_run.as_ref().is_none_or(|b| h.2 > b.2)
                                    {
                                        one_run = Some(h);
                                    }
                                }
                            }
                            i = j + 1;
                        }
                    }
                    let single_hit = hit(&single_collapsed);
                    let double_hit = if double_collapsed.len() < trimmed_lower.len()
                        && double_collapsed != single_collapsed
                    {
                        hit(&double_collapsed)
                    } else {
                        None
                    };
                    let chosen = one_run.or(match (single_hit, double_hit) {
                        (Some(s), Some(d)) => Some(if d.2 > s.2 { d } else { s }),
                        (s, d) => s.or(d),
                    });
                    // A swap that lands on a double ("theer", "perss",
                    // "sveen") looks like a burst; when two swapped letters
                    // read as a top-tier word clearly ahead of the collapse
                    // ("there" 253 over "ther" 160), the swap stage owns it.
                    let chosen = chosen.filter(|(_, _, cf)| {
                        !self
                            .top_tier_adjacent_swap(&trimmed_lower)
                            .is_some_and(|sf| sf >= cf.saturating_add(20))
                    });
                    // A collapse below the top tier yields to the everyday
                    // word one letter LONGER than the token: "acces" is
                    // access, not aces; "meber" is member, not ember (sweep
                    // 2026-09-18).
                    let chosen = chosen.filter(|(_, _, cf)| {
                        *cf >= 236 || !self.one_insertion_rival(&trimmed_lower, cf.saturating_add(60))
                    });
                    // A junk-band collapse ("stl", "ain", "fina") is not
                    // shown at all: as display filler it counted as a claim
                    // and switched off every later fix ("stll" never became
                    // still). Stretched slang ("yasss", "ewww") keeps the
                    // filler, which is what protects it from a fuzzy
                    // neighbour ("gases", "www") (hunt 2026-09-18).
                    let chosen = chosen.filter(|(_, f, _)| *f >= AUTOCOMMIT_MIN_FREQ || has_triple_run);
                    if let Some((word, f, _)) = chosen {
                        // "dontt" collapses to "dont": show it as "don't".
                        let base: &str = contraction_display(&word).unwrap_or(&word);
                        let formatted = Self::apply_casing(trimmed, base);
                        candidates.push(RankedCandidate {
                            word: formatted,
                            // "doona" collapsing to 60-band "dona" must not
                            // auto-commit: junk stays a suggestion.
                            is_autocorrect: f >= AUTOCOMMIT_MIN_FREQ,
                        });
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
        // 6a. Dropped last letter — the one completion that DOES commit.
        // "peopl", "becaus", "kno": the typed token is not a word, nothing
        // upstream claimed it, and the strongest completion is exactly one
        // letter longer and top-tier. That is the word meant, and it used
        // to sit in the strip uncommitted (397 of the 2,199 misses in the
        // 2026-09-13 sweep). Deliberate prefixes are protected by the
        // exact-word rule (typing "the" never completes to "then"), by the
        // one-letter rule ("peo" stays), by the three-letter floor ("co",
        // "ex" stay), and — like stages 6b and 7 — by capitalisation: a
        // capitalised token is a name until proven otherwise ("Gav" must
        // not become "Gave"; review 2026-09-13).
        const COMPLETION_AUTOCOMMIT_MIN_FREQ: u32 = 236;
        // Rival readings of the token: a same-length adjacent-key
        // neighbour ("healt" is "heart" as much as "health", "fron" is
        // "from") and any other single insertion ("thre" is "there" as
        // much as "three"). The completion only claims when it is clearly
        // the commoner word; otherwise the fuzzy stage keeps the decision.
        const COMPLETION_LEAD_OVER_RIVAL: u32 = 20;
        let typed_len = trimmed_lower.chars().count();
        let typed_capitalized = trimmed.chars().next().is_some_and(|c| c.is_uppercase());
        let completion_claim: Option<String> = if !is_exact
            && candidates.is_empty()
            && typed_len >= 3
            && !typed_capitalized
            && !has_internal_uppercase
            && trimmed_lower.chars().all(|c| c.is_alphabetic())
        {
            // The commonest shipped one-letter-longer completion, not the
            // trie's first entry: a learned boost must not switch the fix
            // off ("known" taught to 255 would otherwise hide "know").
            prefix_matches
                .iter()
                .filter(|(w, _)| w.chars().count() == typed_len + 1)
                .map(|(w, _)| (w, self.corpus_freq(w)))
                .filter(|&(_, cf)| cf >= COMPLETION_AUTOCOMMIT_MIN_FREQ)
                .max_by_key(|&(_, cf)| cf)
                .and_then(|(w, cf)| {
                    let best_rival = {
                        let touch = self.touch_model.read().unwrap();
                        self.trie
                            .fuzzy_search_weighted(&trimmed_lower, 2, 8, |a, b| Self::slip_oracle(&touch, a, b))
                            .iter()
                            .filter(|fc| {
                                fc.word != *w
                                    && (fc.distance == 1
                                        || (fc.distance == 2 && fc.word.chars().count() == typed_len + 1))
                            })
                            .map(|fc| self.corpus_or_learned(&fc.word, fc.frequency))
                            .max()
                            .unwrap_or(0)
                    };
                    (cf >= best_rival.saturating_add(COMPLETION_LEAD_OVER_RIVAL)).then(|| w.clone())
                })
        } else {
            None
        };
        for (w, _) in prefix_matches {
            let formatted = Self::apply_casing(trimmed, &w);
            if !contains_word(&candidates, &formatted) {
                candidates.push(RankedCandidate {
                    word: formatted,
                    is_autocorrect: completion_claim.as_deref() == Some(w.as_str()),
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
            // A swap is one GUESS at the slip, and a same-length adjacent-key
            // substitution to a commoner word beats it: "hhe" is "the", not
            // "heh"; "ond" is "and", not "nod"; "fot" is "for", not "oft"
            // (sweep 2026-09-13: 299 such flips across the top 1,000 words,
            // because this stage ran first and claimed the auto-commit).
            let best_swap = best_swap.filter(|(w, f)| !self.outranked_by_adjacent_slip(&trimmed_lower, w, *f));
            // A rare swap must not take a token whose commoner reading is
            // one dropped letter: "wasit" is "wait"/"was it", never "waist"
            // (199); "sohe" is not "shoe" (hunt 2026-09-18).
            // ... and not one whose commoner reading is one dropped letter
            // of a longer word: "moent" is moment, not monet; "taes" is
            // takes, not teas; "ters" is terms, not tres (sweep 2026-09-18).
            let best_swap = best_swap.filter(|(w, f)| {
                // a bare contraction key is as common as its apostrophe
                // form ("catn" is can't, not can), same as the fuzzy stage
                let shown: &str = contraction_display(w).unwrap_or(w.as_str());
                let cf = self.corpus_or_learned(w, *f).max(self.corpus_freq(shown));
                cf >= 236
                    || (!self.one_deletion_rival(&trimmed_lower, cf.saturating_add(30))
                        && !self.one_insertion_rival(&trimmed_lower, cf.saturating_add(60)))
            });
            // A junk-band guess ("lev" for lve, "peg" for pge) is not shown:
            // as filler it vetoed the real fix (hunt 2026-09-18, finding
            // 32). Stretched slang keeps it (see the collapse stage).
            let stretched = {
                let cs: Vec<char> = trimmed_lower.chars().collect();
                cs.windows(3).any(|w| w[0] == w[1] && w[1] == w[2])
            };
            let best_swap = best_swap.filter(|(_, f)| *f >= AUTOCOMMIT_MIN_FREQ || stretched);
            if let Some((swapped_str, f)) = best_swap {
                // "catn" is shown as "can't", the way the fuzzy stage shows it
                let base: &str = contraction_display(&swapped_str).unwrap_or(&swapped_str);
                let formatted = Self::apply_casing(trimmed, base);
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
                            // a typed apostrophe is deliberate: "kids'" must not
                            // become "kid's" (hunt 2026-09-18)
                            && !trimmed_lower.contains('\'')
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
                    // A doubled letter is one GUESS at the slip, and a weak
                    // one next to a same-length adjacent-key substitution:
                    // "lile" is "like" with l for k far more often than the
                    // city "lille" (254 vs 170), yet this stage ran first
                    // and claimed the auto-commit. When a 1-unit neighbour
                    // is the more common word, leave the slot to stage 7,
                    // which ranks that neighbour first and still surfaces
                    // the doubled word behind it (field report 2026-09-13).
                    if self.outranked_by_adjacent_slip(&trimmed_lower, &doubled_str, f) {
                        continue;
                    }
                    // Both readings are one dropped letter; the everyday
                    // word wins by a clear margin: "abot" is about, not
                    // abbot (180); "oter" is other, not otter (hunt
                    // 2026-09-18). Only an INSERTION rival counts: a
                    // deletion rival would let "part" steal "parot".
                    let dcf = self.corpus_or_learned(&doubled_str, f);
                    if dcf < 236 && self.one_insertion_rival(&trimmed_lower, dcf.saturating_add(60)) {
                        continue;
                    }
                    if f < AUTOCOMMIT_MIN_FREQ && !stretched {
                        continue;
                    }
                    let base: &str = contraction_display(&doubled_str).unwrap_or(&doubled_str);
                    let formatted = Self::apply_casing(trimmed, base);
                    if !contains_word(&candidates, &formatted) {
                        let rc = RankedCandidate {
                            word: formatted,
                            is_autocorrect: !claimed_before_completions
                                && !has_internal_uppercase
                                && !trimmed.chars().next().is_some_and(|c| c.is_uppercase())
                                // a typed apostrophe is deliberate: "kids'" must not
                                // become "kid's" (hunt 2026-09-18)
                                && !trimmed_lower.contains('\'')
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
            } else if trimmed_lower.len() < 3 {
                // 1-2 char inputs are the widest queries the engine serves
                // (every backspace step lands here) and their only pinned
                // deep correction is the 2-unit class ("nt" -> my).
                // Budget 3 at this length bought nothing but latency
                // (platform handoff, 2026-08-28).
                2
            } else if trimmed_lower.len() <= 4 {
                3
            } else {
                4
            };
            // Frozen touch-model snapshot for just this fuzzy pass: the read guard
            // is scoped to the block so it drops before any later snapshot in this
            // method (no same-thread recursive read lock).
            let fuzzy = {
                let touch = self.touch_model.read().unwrap();
                self.trie.fuzzy_search_weighted(
                    &trimmed_lower,
                    max_units,
                    max_candidates + 4,
                    |a, b| Self::slip_oracle(&touch, a, b),
                )
            };
            // Frequency-aware distance: a top-tier word earns back a unit
            // (two above 250). Raw units with neighbour-first ordering let
            // a same-length adjacent-key match to a rare word beat the
            // obvious dropped-letter fix — "abut" -> "shut" over "about",
            // "hve" -> "dvd" over "have", "tfhe" -> "true" over "the"
            // (sweep 2026-09-13, ~4,000 such flips across the top 1,000
            // words). Neighbour matches keep their edge among equals only.
            // Shipped-corpus frequency, so a learned boost cannot buy units.
            // Rank by how many things went wrong, then by how common the
            // word is, then by how cheap the slips were. The weighted units
            // alone cannot tell one dropped letter (2 units) from two
            // adjacent slips (2 units), which is how "tfhe" became "true"
            // instead of "the" and "abut" became "shut" instead of "about";
            // and a unit bonus for common words let "will" (two slips) steal
            // "sdll" from "sell" (one slip) — review 2026-09-13. Edit count
            // first settles both: one slip beats two; among one-slip
            // readings a top-tier word beats a rare one ("cdan" is "can",
            // not "chan"; "lke" is "like", not "lie"); ties fall through to
            // units, so an adjacent-key slip still beats a far one
            // ("hous" is house before hours), then to the neighbour
            // shape, then to raw frequency. Shipped-corpus frequency for
            // the tier, so a learned boost cannot buy a place.
            // Cost in half-units: an adjacent-key substitution 2, a far
            // substitution 4, an added or dropped letter 3 — cheaper than
            // a far substitution, dearer than an adjacent one. That keeps
            // "sdll" -> sell (2) over will (4), "tfhe" -> the (3) over true
            // (4), "nt" -> my (4) level with at (4) so the neighbour shape
            // decides, and "healt" -> health (3) over heart (4: l~r is not
            // a QWERTY neighbour).
            let typed_chars = trimmed_lower.chars().count();
            let cost = |fc: &crate::trie::FuzzyCandidate| {
                let len_diff = typed_chars.abs_diff(fc.word.chars().count());
                fc.distance * 2 - len_diff.min(fc.distance)
            };
            // One plausible slip — an adjacent key (2) or a letter added or
            // dropped (3) — is one bucket, and commonness decides inside it
            // ("frm" is from, not fem; "cdan" is can, not chan). A far
            // substitution (4) or anything heavier sits behind. Raw cost
            // still breaks ties inside a bucket ("healt": heart before
            // health).
            // A single far substitution to a top-tier word is also one
            // plausible slip: with the real QWERTY geometry (2026-09-18)
            // "rfally" is really and "golng" is going, not the rarer
            // dropped-letter readings "rally" and "gong". Two adjacent
            // slips (also 4) stay behind: "sdll" is sell, not will.
            // Rank, most plausible first: an adjacent-key slip of a
            // top-tier word; a letter added or dropped from one ("frm" is
            // from); an adjacent slip of a rarer word ("fem" behind from,
            // but "luck" ahead of a far slip); one far substitution to a
            // top-tier word ("rfally" really, "golng" going: ahead of the
            // rarer dropped-letter readings rally/gong, behind any
            // adjacent slip so "kuck" stays luck, not fuck — review
            // 2026-09-18); an added/dropped letter of a rarer word; then
            // everything heavier by cost.
            let rank = |c: usize, tier: u8, one_far_sub_to_top_tier: bool| match (c, tier) {
                (0..=2, 0) => 0,
                (3, 0) => 1,
                (0..=2, 1) => 2,
                (4, 0) if one_far_sub_to_top_tier => 3,
                // "ttis" is this, not the rare adjacent reading "tris"
                (0..=2, _) => 4,
                (3, 1) => 5,
                (3, _) => 6,
                (4, _) => 7,
                (c, t) => 8 + c * 2 + t as usize,
            };
            let mut sorted_fuzzy = fuzzy;
            sorted_fuzzy.sort_by_key(|fc| {
                let is_neighbor = Self::is_spatial_slip_match(&trimmed_lower, &fc.word);
                // A bare contraction key is as common as its apostrophe
                // form: "canr" is "can't", not "can" (hunt 2026-09-18).
                let shown: &str = contraction_display(&fc.word).unwrap_or(fc.word.as_str());
                let commonness = self
                    .corpus_or_learned(&fc.word, fc.frequency)
                    .max(self.corpus_freq(shown));
                // top tier, everyday (200-235: luck, keen), rare (< 200: tris)
                let tier: u8 = if commonness >= 236 { 0 } else if commonness >= 200 { 1 } else { 2 };
                let c = cost(fc);
                let one_far_sub_to_top_tier = tier == 0
                    && fc.distance == 2
                    && fc.word.chars().count() == typed_chars
                    && edit_count(&trimmed_lower, &fc.word) == 1;
                // Among equals the longer word wins: dropping a letter is
                // a far commoner slip than typing a stray one, so "tme" is
                // time, not me; "rund" is round, not run (sweep 2026-09-18).
                let shorter = if fc.word.chars().count() < typed_chars { 1 } else { 0 };
                (
                    rank(c, tier, one_far_sub_to_top_tier),
                    c,
                    shorter,
                    if is_neighbor { 0 } else { 1 },
                    std::cmp::Reverse(fc.frequency),
                )
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
                // The word may already sit in the pool as a plain prefix
                // completion ("house" for "hous"): the correction verdict
                // below must be able to promote it, not skip it.
                let existing_at = candidates.iter().position(|c| c.word.eq_ignore_ascii_case(&formatted));
                if existing_at.is_none_or(|p| !candidates[p].is_autocorrect) {
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
                    // A second way through the filler (review 2026-09-13):
                    // a single-edit fix to a top-tier word on a 4+ letter
                    // token — "thre" is "there", "hous" is "house", "fron"
                    // is "from" — must not be starved by the completions of
                    // the typo ("three", "hours", "front") sitting in the
                    // pool as plain suggestions.
                    // A third way through (field report 2026-09-19: "Pne"
                    // never became "One"). A three-letter token that is not
                    // a word, one adjacent key from an everyday word, whose
                    // own completions are all far rarer than that word:
                    // "pne" completes to pneumonia 193 and pneumatic 169
                    // while one adjacent slip away sits "one" at 254. The
                    // margin is what keeps deliberate short forms safe --
                    // a completion anywhere near the rival keeps the slot.
                    let three_letter_slip = trimmed_lower.chars().count() == 3
                        && is_neighbor
                        && fc.distance == 1
                        && self.corpus_or_learned(&fc.word, fc.frequency) >= 236
                        && self
                            .trie
                            .prefix_search(&trimmed_lower, 4)
                            .iter()
                            .filter(|(w, _)| w.chars().count() > 3)
                            .all(|(w, _)| self.corpus_freq(w) + 40 < self.corpus_or_learned(&fc.word, fc.frequency))
                        // ... and the reading has to be the only one. Three
                        // letters is short enough that a token often sits one
                        // key from TWO everyday words ("aho" is a slip of both
                        // "who" and "ago"), and picking the commoner one is a
                        // coin toss that writes a real word in the wrong
                        // place. A clear gap is still decisive ("tge" is the).
                        && self
                            .single_slip_rivals(&trimmed_lower)
                            .into_iter()
                            .filter(|(w, _)| w != &fc.word)
                            .all(|(_, f)| f + 60 <= self.corpus_or_learned(&fc.word, fc.frequency))
                        // A dropped letter is at least as likely as a
                        // mis-hit key at this length: "kow" is "know" and
                        // "haf" is "half", so a word one letter longer
                        // keeps the slot.
                        && !self.one_insertion_rival(&trimmed_lower, 236);
                    let punches_filler = !claimed_before_completions
                        && candidates.iter().all(|c| !c.is_autocorrect)
                        && (three_letter_slip
                            || (is_neighbor && fc.distance == 1 && trimmed_lower.chars().count() >= 5)
                            || (trimmed_lower.chars().count() >= 4
                                && edit_count(&trimmed_lower, &fc.word) == 1
                                && self.corpus_or_learned(&fc.word, fc.frequency) >= 236));
                    // Sentence-start capitalized autocorrect was TRIED and
                    // REJECTED by evidence (sweep 2026-08-27): a 53-name
                    // sweep flipped 9, including Crake -> Drake and
                    // Shrike -> Strike (the latter only via the union
                    // table's Dvorak adjacency). Unigram + adjacency cannot
                    // separate names from typos at a sentence boundary;
                    // capitalized fixes stay curated corpus entries only
                    // ("Teh" -> The still works through branch 3).
                    // A two-letter token is never turned into one letter
                    // ("vs" -> "s", "wk" -> "w"; hunt 2026-09-18).
                    let one_letter_for_two = trimmed_lower.chars().count() == 2 && fc.word.chars().count() == 1;
                    // "alotof" is not aloof (152), "inabit" is not inhabit
                    // (172): a token that opens with a strongly attested
                    // pair is a run-together the splitter cannot reach
                    // (three words); a rare word stays a suggestion (hunt
                    // 2026-09-18).
                    let shadowed_by_phrase = self.corpus_or_learned(&fc.word, fc.frequency) < 236
                        && self.has_strong_prefix_pair(&trimmed_lower);
                    let should_autocorrect = !is_exact
                        && !is_capitalized
                        && !has_edge_apostrophe
                        && !one_letter_for_two
                        && !shadowed_by_phrase
                        && (fc.distance <= 2 || is_neighbor)
                        && fc.frequency >= AUTOCOMMIT_MIN_FREQ
                        && (candidates.is_empty() || punches_filler);
                    let rc = RankedCandidate {
                        word: formatted,
                        is_autocorrect: should_autocorrect,
                    };
                    if let Some(p) = existing_at {
                        // Already listed as filler: only a promotion changes it.
                        if should_autocorrect {
                            candidates.remove(p);
                            candidates.insert(0, rc);
                        }
                    } else if rc.is_autocorrect && !candidates.is_empty() {
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
                // The closest slip match by RAW distance, not the first in
                // effective order — that could be the two-slip thief the
                // guarantee exists to protect against (review 2026-09-13).
                if let Some(fc) = sorted_fuzzy
                    .iter()
                    .filter(|fc| Self::is_spatial_slip_match(&trimmed_lower, &fc.word))
                    .min_by_key(|fc| fc.distance)
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

        // A once-observed personal correction is a visible suggestion in
        // slot 2 (never an auto-commit): the user sees what the keyboard
        // has learned before it is allowed to act on it.
        if let Some((formatted, n)) = &personal {
            if *n < PERSONAL_AUTOCOMMIT_MIN_OBSERVATIONS {
                // Usually the word is already somewhere in the pool (a
                // correction is mostly one edit away): move it up rather
                // than skipping, or it never gets seen before it starts
                // auto-committing (review 2026-09-13).
                let existing = candidates
                    .iter()
                    .position(|c| c.word.eq_ignore_ascii_case(formatted))
                    .map(|p| candidates.remove(p))
                    .unwrap_or_else(|| RankedCandidate {
                        word: formatted.clone(),
                        is_autocorrect: false,
                    });
                let at = 1.min(candidates.len());
                candidates.insert(at, existing);
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
        // A token typed WITH its apostrophe ("you're", "they're") is the
        // word meant; the pair table has no apostrophe tokens, so the gate
        // below would read "is you're" as unattested and flip it to "your"
        // (hunt 2026-09-18).
        if !prev_word.is_empty() && !candidates.is_empty() && !trimmed_lower.contains('\'') {
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

        // Valid-word slips are NOT rescued from the bigram table. The rescue
        // that lived here ("I an not" -> am) fired on any common word whose
        // pair with the previous word happened to be missing from the table
        // and had one attested neighbour: a sentinel over every shipped word
        // >= 236 after twenty common previous words found 438 hijacks
        // ("[in] be" -> me, "[i] so" -> do, "[to] than" -> that, "[was] is"
        // -> in, "[my] had" -> dad). Coverage of a 2 MB pair table is not
        // evidence of wrongness (field report 2026-09-18: "many common words
        // correcting to random things"). The few slips that are genuinely
        // impossible English are curated below; nothing statistical touches
        // a correctly typed word.
        if !prev_word.is_empty()
            && is_exact
            && !is_capitalized
            && !has_internal_uppercase
            && !candidates.iter().any(|c| c.is_autocorrect)
        {
            let prev_lower = prev_word.trim().to_lowercase();
            // The user's own accepted pair outranks the table: once they
            // have typed and kept "i an", it is theirs.
            let user_keeps_it = self
                .personal_bigrams
                .contains_key(&(prev_lower.clone(), trimmed_lower.clone()));
            if let Some(&(_, _, fix)) = CURATED_CONTEXT_SLIPS
                .iter()
                .filter(|_| !user_keeps_it)
                .find(|&&(p, t, _)| p == prev_lower && t == trimmed_lower)
            {
                let formatted = Self::apply_casing(trimmed, fix);
                candidates.retain(|c| !c.word.eq_ignore_ascii_case(&formatted));
                candidates.insert(0, RankedCandidate {
                    word: formatted,
                    is_autocorrect: true,
                });
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
                    .take_while(|c| {
                        c.is_autocorrect
                            || c.word.eq_ignore_ascii_case(trimmed)
                            // The apostrophised twin of the typed word
                            // ("I'll" behind a kept "ill") is a choice, not
                            // filler: it stays in slot 2 instead of being
                            // re-ranked below "dll" and "ilk" (probe
                            // 2026-09-13).
                            || is_apostrophe_variant(&c.word, &trimmed_lower)
                            // The user's own once-seen correction stays
                            // where it was pinned.
                            || personal_pin.as_deref().is_some_and(|p| p.eq_ignore_ascii_case(&c.word))
                    })
                    .count();
                if head < candidates.len() {
                    // One frozen snapshot for the whole rescoring pass so every
                    // candidate sees the same touch model (matches the old
                    // engine-read-frozen view) with no per-comparison locking.
                    let touch = self.touch_model.read().unwrap();
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
                                |a, b| Self::slip_oracle(&touch, a, b),
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

        // The user's spoken: a correction they have rejected twice (via backspace revert)
        // never auto-commits for that typed token again.
        if !self.rejected_corrections.is_empty() {
            for c in candidates.iter_mut() {
                let c_word = c.word.to_ascii_lowercase();
                if let Some(&rejections) = self.rejected_corrections.get(&(trimmed_lower.clone(), c_word)) {
                    if rejections >= 2 {
                        c.is_autocorrect = false;
                    }
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

        // A half-typed long word must never be replaced by a DIFFERENT
        // word. A stray space mid-word (the space bar sits directly under
        // c/v/b/n) used to commit "worl" as "work", "thr" as "the", "hou"
        // as "you", "somet" as "some" -- 730 such rewrites across the
        // interior prefixes of the 1,500 commonest words -- and the rest of
        // the word was then typed after it: "finishing" became
        // "find shing" (field report 2026-09-19). When what was typed is
        // still a live prefix of a common word, only a candidate that
        // CONTINUES it may auto-commit ("peopl" -> people stays, all 611
        // continuations stay); anything else is a suggestion. A typo is not
        // a prefix of anything, so ordinary autocorrect is untouched.
        // Three letters and up: a two-letter token is where the curated
        // context slips live ("i an" -> "i am") and there is no room for a
        // word to be "in progress" in two keystrokes.
        if trimmed_lower.chars().count() >= 3 && self.is_live_prefix(&trimmed_lower) {
            // What the user taught this keyboard themselves always stands.
            let taught = if include_personal {
                self.personal_correction_with_count(&trimmed_lower).map(|(w, _)| w.to_ascii_lowercase())
            } else {
                None
            };
            let always_fixes = ALWAYS_FIX_LIVE_PREFIXES.contains(&trimmed_lower.as_str());
            for c in candidates.iter_mut() {
                if !c.is_autocorrect || always_fixes {
                    continue;
                }
                if taught.as_deref() == Some(c.word.to_ascii_lowercase().as_str()) {
                    continue;
                }
                let bare = c.word.to_ascii_lowercase();
                // A candidate that keeps every letter typed, and only adds
                // a space or an apostrophe, cannot destroy the word:
                // "notin" -> "not in", "whos" -> "who's" are always fine.
                let letters: String = bare.chars().filter(|c| c.is_alphanumeric()).collect();
                let keeps_letters = letters.starts_with(trimmed_lower.as_str());
                if !keeps_letters {
                    c.is_autocorrect = false;
                }
            }
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
    // The hand-written table below is not symmetric (e lists f, f does not
    // list e): "vfry" was read as a FAR slip of "very" and lost to "fry"
    // (2026-09-18). Adjacency is a property of the keyboard, not of the
    // direction of the lookup.
    Self::adjacency_table(a, b) || Self::adjacency_table(b, a)
}

/// Key centre of a letter on the staggered QWERTY phone layout, in key
/// units: row offsets 0, 0.5 and 1.5, row pitch 1.
fn qwerty_key_pos(c: char) -> Option<(f32, f32)> {
    const ROWS: [&str; 3] = ["qwertyuiop", "asdfghjkl", "zxcvbnm"];
    const OFFSETS: [f32; 3] = [0.0, 0.5, 1.5];
    ROWS.iter().enumerate().find_map(|(r, row)| {
        row.find(c).map(|i| (i as f32 + OFFSETS[r], r as f32))
    })
}

fn adjacency_table(a: char, b: char) -> bool {
    let a = a.to_ascii_lowercase();
    let b = b.to_ascii_lowercase();
    if a == b {
        return true;
    }
    // The staggered QWERTY phone layout, judged by the same elliptical
    // rule the on-device touch model uses (see `SLIP_NEAR_FACTOR`): the
    // next key across, the two keys under a top-row key, the key under a
    // home-row key; not the bottom-row corners. This used to be a
    // hand-written UNION of QWERTY and Dvorak, so "vould" read as "would"
    // (v~w), "theb" as "then" (b~n) and "canh" as "can't" (h~t) on a
    // QWERTY phone: 251 of 1,207 wrong commits in a slip census came from
    // pairs adjacent only on a Dvorak keyboard (hunt 2026-09-18). On
    // device the geometric touch model decides; this is the glide-off
    // fallback and the sweep's reference.
    if !a.is_ascii_lowercase() || !b.is_ascii_lowercase() {
        return false;
    }
    static NEAR: std::sync::OnceLock<[[bool; 26]; 26]> = std::sync::OnceLock::new();
    let table = NEAR.get_or_init(|| {
        let mut t = [[false; 26]; 26];
        for (i, row) in t.iter_mut().enumerate() {
            for (j, cell) in row.iter_mut().enumerate() {
                let (a, b) = ((b'a' + i as u8) as char, (b'a' + j as u8) as char);
                *cell = match (Self::qwerty_key_pos(a), Self::qwerty_key_pos(b)) {
                    (Some((ax, ay)), Some((bx, by))) => {
                        let (dx, dy) = (ax - bx, ay - by);
                        dx * dx + dy * dy <= SLIP_NEAR_FACTOR * SLIP_NEAR_FACTOR
                    }
                    _ => false,
                };
            }
        }
        t
    });
    table[(a as u8 - b'a') as usize][(b as u8 - b'a') as usize]
}

/// Computes whether a candidate word's substitutions are all physical keyboard neighbor slips.
pub fn is_spatial_slip_match(query: &str, candidate: &str) -> bool {
    let mut slip_count = 0;
    let mut char_count = 0;
    let mut q_iter = query.chars();
    let mut c_iter = candidate.chars();
    loop {
        match (q_iter.next(), c_iter.next()) {
            (Some(q), Some(c)) => {
                char_count += 1;
                if q != c {
                    if Self::is_spatial_keyboard_neighbor(q, c) {
                        slip_count += 1;
                    } else {
                        return false;
                    }
                }
            }
            (None, None) => break,
            _ => return false,
        }
    }
    // Long words tolerate a third fat-finger slip: at 8+ chars the word
    // shape still identifies the target ("thoriufhky" -> thoroughly,
    // "xurrenrky" -> currently, field specimens 2026-08-27), while at
    // short lengths 3 slips is a different word, not a slip chain.
    let max_slips = if char_count >= 8 { 3 } else { 2 };
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
    // "too" -> "to" after want/need/have/like... was removed 2026-09-18:
    // "I have too many", "I like too much" are everyday English and the
    // bigram gate let "have too" through. A typed "too" is kept.
    (&["people", "days", "hours", "minutes", "seconds", "weeks", "months", "years", "times", "things", "items"], "to", "two"),
    (&["people", "days", "hours", "minutes", "seconds", "weeks", "months", "years", "times", "things", "items"], "too", "two"),

    // ("were" vs "we're" vs "where")
    (&["going", "coming", "trying", "thinking", "hoping", "ready", "done", "excited", "happy", "sorry", "here", "there"], "were", "we're"),
    (&["are", "is", "was", "did", "do", "can", "could", "from", "to"], "we're", "where"),

    // ("loose" vs "lose")
    (&["will", "to", "not", "dont", "don't", "might", "could", "never", "cannot", "cant", "can't", "gonna"], "loose", "lose"),
    (&["too", "very", "fitting", "skin", "pants", "change", "threads", "knot"], "lose", "loose"),

    // ("affect" vs "effect")
    (&["will", "would", "could", "might", "can", "to", "not", "greatly", "directly"], "effect", "affect"),
    (&["the", "a", "an", "this", "that", "side", "cause", "positive", "negative", "overall"], "affect", "effect"),

    // ("accept" vs "except")
    (&["to", "will", "would", "please", "can", "cannot", "cant", "can't"], "except", "accept"),
    (&["all", "everything", "everyone", "nothing", "nobody", "anywhere"], "accept", "except"),
];

    /// Resolves homophone ambiguity based on preceding word context.
    pub fn disambiguate_homophone(prev_word: &str, candidate: &str) -> Option<&'static str> {
        let prev = prev_word.trim().to_ascii_lowercase();
        let cand = candidate.trim().to_ascii_lowercase();
        for &(context_keywords, wrong_form, correct_form) in Self::HOMOPHONE_CONTEXT_RULES {
            if cand == wrong_form && context_keywords.iter().any(|&k| k == prev) {
                return Some(correct_form);
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
        scored.sort_by_key(|b| std::cmp::Reverse(b.0));
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
            let mut prefix_buf = trimmed_lower.clone();
            for ch in valid_next {
                prefix_buf.push(ch);
                let matches = self.trie.prefix_search(&prefix_buf, 1);
                if let Some((word, freq)) = matches.first() {
                    let formatted = Self::apply_casing(trimmed, word);
                    candidates.push((ch, formatted, *freq));
                }
                prefix_buf.pop();
            }

            candidates.sort_by_key(|b| std::cmp::Reverse(b.2));
            candidates.truncate(6);
            return candidates.into_iter().map(|(ch, word, _)| (ch, word)).collect();
        }

        // Prefix is empty: Predict next words based on preceding context word
        let prev_trimmed = prev_word.trim().to_ascii_lowercase();
        let mut results: Vec<(char, String)> = Vec::with_capacity(6);

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
            scored.sort_by_key(|b| std::cmp::Reverse(b.0));
            for (_, w) in scored {
                if let Some(first_ch) = w.chars().next() {
                    let ch_lower = first_ch.to_ascii_lowercase();
                    if !results.iter().any(|(c, _)| *c == ch_lower) {
                        results.push((ch_lower, w.to_string()));
                    }
                }
                if results.len() >= 6 {
                    break;
                }
            }
        }

        if results.is_empty() {
            if let Some(&(_, next_words)) = Self::BIGRAM_TRANSITIONS.iter().find(|&&(k, _)| k == prev_trimmed) {
                for &w in next_words {
                    if let Some(first_ch) = w.chars().next() {
                        let ch_lower = first_ch.to_ascii_lowercase();
                        if !results.iter().any(|(c, _)| *c == ch_lower) {
                            results.push((ch_lower, w.to_string()));
                        }
                    }
                    if results.len() >= 6 {
                        break;
                    }
                }
            }
        }

        // If no preceding word or insufficient bigrams, fill with high-probability sentence starters
        if results.is_empty() {
            for &(w, ch) in Self::SENTENCE_STARTERS {
                let ch_lower = ch.to_ascii_lowercase();
                if !results.iter().any(|(c, _)| *c == ch_lower) {
                    results.push((ch_lower, w.to_string()));
                }
                if results.len() >= 6 {
                    break;
                }
            }
        }

        results
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
    fn test_contractions_tables_are_sorted() {
        for window in CONTRACTIONS.windows(2) {
            assert!(
                window[0].0 <= window[1].0,
                "CONTRACTIONS must be strictly sorted for binary search: {} > {}",
                window[0].0,
                window[1].0
            );
        }
        for window in SAFE_CONTRACTION_BARE.windows(2) {
            assert!(
                window[0] <= window[1],
                "SAFE_CONTRACTION_BARE must be sorted for binary search: {} > {}",
                window[0],
                window[1]
            );
        }
        for window in GLIDE_CONTRACTION_BARE.windows(2) {
            assert!(
                window[0] <= window[1],
                "GLIDE_CONTRACTION_BARE must be sorted for binary search: {} > {}",
                window[0],
                window[1]
            );
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
