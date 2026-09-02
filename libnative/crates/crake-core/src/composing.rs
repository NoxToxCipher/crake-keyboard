//! Text composers: dynamic per-keystroke text transformation for scripts
//! where one-key-per-letter is impossible (Hangul syllable assembly, kana
//! dakuten/handakuten/small-kana toggling) plus a generic rule table.
//!
//! Ported 1:1 from the Kotlin `ime/text/composing` classes. The contract is
//! `get_actions(preceding_text, to_insert) -> (chars_to_delete, replacement)`
//! where `chars_to_delete` counts UTF-16 CODE UNITS (the Kotlin caller feeds
//! it to `String.dropLast`), not Unicode scalars. All "last char" / "first
//! char" reads are likewise UTF-16-code-unit reads to stay bit-exact with
//! the Kotlin behavior, including around surrogate halves.

/// Number of UTF-16 code units in `s` (Kotlin's `String.length`).
fn utf16_len(s: &str) -> usize {
    s.encode_utf16().count()
}

/// Last UTF-16 code unit of `s` mapped to a `char`. A lone surrogate half
/// (astral char split by the caller's UTF-16 `takeLast`) maps to U+FFFD,
/// which matches nothing in any composer table — the same "no action"
/// outcome the Kotlin code reaches with the raw surrogate.
fn last_unit_char(s: &str) -> Option<char> {
    let last = s.encode_utf16().last()?;
    Some(char::from_u32(last as u32).unwrap_or('\u{FFFD}'))
}

/// First UTF-16 code unit of `s` mapped to a `char`, same surrogate rule.
fn first_unit_char(s: &str) -> Option<char> {
    let first = s.encode_utf16().next()?;
    Some(char::from_u32(first as u32).unwrap_or('\u{FFFD}'))
}

// ---------------------------------------------------------------------------
// Hangul (id "hangul-unicode")
// ---------------------------------------------------------------------------

const INITIALS: [char; 19] = [
    'ㄱ', 'ㄲ', 'ㄴ', 'ㄷ', 'ㄸ', 'ㄹ', 'ㅁ', 'ㅂ', 'ㅃ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅉ', 'ㅊ', 'ㅋ',
    'ㅌ', 'ㅍ', 'ㅎ',
];
const MEDIALS: [char; 21] = [
    'ㅏ', 'ㅐ', 'ㅑ', 'ㅒ', 'ㅓ', 'ㅔ', 'ㅕ', 'ㅖ', 'ㅗ', 'ㅘ', 'ㅙ', 'ㅚ', 'ㅛ', 'ㅜ', 'ㅝ', 'ㅞ',
    'ㅟ', 'ㅠ', 'ㅡ', 'ㅢ', 'ㅣ',
];
// Index 0 is the '_' sentinel meaning "no final".
const FINALS: [char; 28] = [
    '_', 'ㄱ', 'ㄲ', 'ㄳ', 'ㄴ', 'ㄵ', 'ㄶ', 'ㄷ', 'ㄹ', 'ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ',
    'ㅁ', 'ㅂ', 'ㅄ', 'ㅅ', 'ㅆ', 'ㅇ', 'ㅈ', 'ㅊ', 'ㅋ', 'ㅌ', 'ㅍ', 'ㅎ',
];

/// (base, addable-seconds, composed-results): composed[i] = base + seconds[i]
const MEDIAL_COMP: [(char, &[char], &[char]); 3] = [
    ('ㅗ', &['ㅏ', 'ㅐ', 'ㅣ'], &['ㅘ', 'ㅙ', 'ㅚ']),
    ('ㅜ', &['ㅓ', 'ㅔ', 'ㅣ'], &['ㅝ', 'ㅞ', 'ㅟ']),
    ('ㅡ', &['ㅣ'], &['ㅢ']),
];
const FINAL_COMP: [(char, &[char], &[char]); 4] = [
    ('ㄱ', &['ㅅ'], &['ㄳ']),
    ('ㄴ', &['ㅈ', 'ㅎ'], &['ㄵ', 'ㄶ']),
    ('ㄹ', &['ㄱ', 'ㅁ', 'ㅂ', 'ㅅ', 'ㅌ', 'ㅍ', 'ㅎ'], &['ㄺ', 'ㄻ', 'ㄼ', 'ㄽ', 'ㄾ', 'ㄿ', 'ㅀ']),
    ('ㅂ', &['ㅅ'], &['ㅄ']),
];

fn index_of(arr: &[char], c: char) -> Option<usize> {
    arr.iter().position(|&x| x == c)
}

fn comp_lookup(
    table: &'static [(char, &'static [char], &'static [char])],
    base: char,
) -> Option<(&'static [char], &'static [char])> {
    for &(b, seconds, composed) in table {
        if b == base {
            return Some((seconds, composed));
        }
    }
    None
}

/// Decompose a composed jamo back into (first, second), e.g. ㄳ -> (ㄱ, ㅅ).
fn comp_reverse(table: &[(char, &[char], &[char])], composed_char: char) -> Option<(char, char)> {
    for &(b, seconds, composed) in table {
        for (i, &cc) in composed.iter().enumerate() {
            if cc == composed_char {
                return Some((b, seconds[i]));
            }
        }
    }
    None
}

fn syllable(ini: usize, med: usize, fin: usize) -> char {
    // Always within the Hangul syllable block (U+AC00..=U+D7A3).
    char::from_u32((ini * 588 + med * 28 + fin + 44032) as u32).unwrap_or('\u{FFFD}')
}

fn syllable_blocks(syll_ord: u32) -> (usize, usize, usize) {
    let initial = ((syll_ord - 44032) / 588) as usize;
    let medial = ((syll_ord - 44032) as usize - initial * 588) / 28;
    let fin = ((syll_ord - 44032) % 28) as usize;
    (initial, medial, fin)
}

/// Hangul composition action, bit-exact with Kotlin `HangulUnicode.getActions`.
pub fn hangul_unicode_actions(preceding: &str, to_insert: &str) -> (i32, String) {
    let c = match first_unit_char(to_insert) {
        Some(c) => c,
        None => return (0, to_insert.to_string()),
    };
    let last_char = match last_unit_char(preceding) {
        Some(l) => l,
        None => return (0, to_insert.to_string()),
    };
    let last_ord = last_char as u32;

    if INITIALS.contains(&last_char) && MEDIALS.contains(&c) {
        let ini = index_of(&INITIALS, last_char).unwrap_or(0);
        let med = index_of(&MEDIALS, c).unwrap_or(0);
        return (1, syllable(ini, med, 0).to_string());
    } else if (44032..=55203).contains(&last_ord) {
        let (ini, med, fin) = syllable_blocks(last_ord);

        // underscore is a sentinel in the FINALS table
        if c == '_' {
            return (0, to_insert.to_string());
        }

        // no final yet and the new char is a final: merge
        if fin == 0 && FINALS.contains(&c) {
            let f = index_of(&FINALS, c).unwrap_or(0);
            return (1, syllable(ini, med, f).to_string());
        }

        // existing final merges with the new char into a composed final
        if let Some((seconds, composed)) = comp_lookup(&FINAL_COMP, FINALS[fin]) {
            if let Some(i) = seconds.iter().position(|&s| s == c) {
                let f = index_of(&FINALS, composed[i]).unwrap_or(0);
                return (1, syllable(ini, med, f).to_string());
            }
        }

        // simple final + medial: split the final off into a new syllable
        if fin != 0 && comp_reverse(&FINAL_COMP, FINALS[fin]).is_none() && MEDIALS.contains(&c) {
            let new_ini = index_of(&INITIALS, FINALS[fin]).unwrap_or(0);
            let new_med = index_of(&MEDIALS, c).unwrap_or(0);
            let mut out = syllable(ini, med, 0).to_string();
            out.push(syllable(new_ini, new_med, 0));
            return (1, out);
        }

        // composed final + medial: split the composed final apart
        if let Some((first, second)) = comp_reverse(&FINAL_COMP, FINALS[fin]) {
            if MEDIALS.contains(&c) {
                let f = index_of(&FINALS, first).unwrap_or(0);
                let new_ini = index_of(&INITIALS, second).unwrap_or(0);
                let new_med = index_of(&MEDIALS, c).unwrap_or(0);
                let mut out = syllable(ini, med, f).to_string();
                out.push(syllable(new_ini, new_med, 0));
                return (1, out);
            }
        }

        // no final and the medial composes with the new char: merge medials
        if fin == 0 {
            if let Some((seconds, composed)) = comp_lookup(&MEDIAL_COMP, MEDIALS[med]) {
                if let Some(i) = seconds.iter().position(|&s| s == c) {
                    let new_med = index_of(&MEDIALS, composed[i]).unwrap_or(0);
                    return (1, syllable(ini, new_med, 0).to_string());
                }
            }
        }
    } else if comp_lookup(&MEDIAL_COMP, last_char)
        .is_some_and(|(seconds, _)| seconds.contains(&c))
    {
        // bare medial + medial, e.g. ㅗ then ㅏ -> ㅘ
        if let Some((seconds, composed)) = comp_lookup(&MEDIAL_COMP, last_char) {
            if let Some(i) = seconds.iter().position(|&s| s == c) {
                return (1, composed[i].to_string());
            }
        }
    } else if comp_lookup(&FINAL_COMP, last_char)
        .is_some_and(|(seconds, _)| seconds.contains(&c))
    {
        // bare final + final, e.g. ㄱ then ㅅ -> ㄳ
        if let Some((seconds, composed)) = comp_lookup(&FINAL_COMP, last_char) {
            if let Some(i) = seconds.iter().position(|&s| s == c) {
                return (1, composed[i].to_string());
            }
        }
    }

    (0, to_insert.to_string())
}

// ---------------------------------------------------------------------------
// Kana (id "kana-unicode")
// ---------------------------------------------------------------------------

const SMALL_SENTINEL: char = '〓';
// Kotlin's `sticky` is a hardcoded false; the non-sticky branches are inlined.

fn reverse_daku(c: char) -> Option<char> {
    Some(match c {
        'ゔ' => 'う',
        'が' => 'か', 'ぎ' => 'き', 'ぐ' => 'く', 'げ' => 'け', 'ご' => 'こ',
        'ざ' => 'さ', 'じ' => 'し', 'ず' => 'す', 'ぜ' => 'せ', 'ぞ' => 'そ',
        'だ' => 'た', 'ぢ' => 'ち', 'づ' => 'つ', 'で' => 'て', 'ど' => 'と',
        'ば' => 'は', 'び' => 'ひ', 'ぶ' => 'ふ', 'べ' => 'へ', 'ぼ' => 'ほ',
        'ヴ' => 'ウ',
        'ガ' => 'カ', 'ギ' => 'キ', 'グ' => 'ク', 'ゲ' => 'ケ', 'ゴ' => 'コ',
        'ザ' => 'サ', 'ジ' => 'シ', 'ズ' => 'ス', 'ゼ' => 'セ', 'ゾ' => 'ソ',
        'ダ' => 'タ', 'ヂ' => 'チ', 'ヅ' => 'ツ', 'デ' => 'テ', 'ド' => 'ト',
        'バ' => 'ハ', 'ビ' => 'ヒ', 'ブ' => 'フ', 'ベ' => 'ヘ', 'ボ' => 'ホ',
        'ヷ' => 'ワ', 'ヸ' => 'ヰ', 'ヹ' => 'ヱ', 'ヺ' => 'ヲ',
        'ゞ' => 'ゝ', 'ヾ' => 'ヽ',
        _ => return None,
    })
}

fn reverse_handaku(c: char) -> Option<char> {
    Some(match c {
        'ぱ' => 'は', 'ぴ' => 'ひ', 'ぷ' => 'ふ', 'ぺ' => 'へ', 'ぽ' => 'ほ',
        'パ' => 'ハ', 'ピ' => 'ヒ', 'プ' => 'フ', 'ペ' => 'ヘ', 'ポ' => 'ホ',
        _ => return None,
    })
}

/// Values are &str because a few small kana live outside the BMP.
fn small(c: char) -> Option<&'static str> {
    Some(match c {
        'あ' => "ぁ", 'い' => "ぃ", 'え' => "ぇ", 'う' => "ぅ", 'お' => "ぉ",
        'か' => "ゕ", 'け' => "ゖ",
        'つ' => "っ",
        'や' => "ゃ", 'ゆ' => "ゅ", 'よ' => "ょ",
        'わ' => "ゎ", 'ゐ' => "𛅐", 'ゑ' => "𛅑", 'を' => "𛅒",
        'ア' => "ァ", 'イ' => "ィ", 'エ' => "ェ", 'ウ' => "ゥ", 'オ' => "ォ",
        'カ' => "ヵ", 'ク' => "ㇰ", 'ケ' => "ヶ",
        'シ' => "ㇱ", 'ス' => "ㇲ",
        'ツ' => "ッ", 'ト' => "ㇳ",
        'ヌ' => "ㇴ",
        'ハ' => "ㇵ", 'ヒ' => "ㇶ", 'フ' => "ㇷ", 'ヘ' => "ㇸ", 'ホ' => "ㇹ",
        'ム' => "ㇺ",
        'ヤ' => "ャ", 'ユ' => "ュ", 'ヨ' => "ョ",
        'ラ' => "ㇻ", 'リ' => "ㇼ", 'ル' => "ㇽ", 'レ' => "ㇾ", 'ロ' => "ㇿ",
        'ワ' => "ヮ", 'ヰ' => "𛅤", 'ヱ' => "𛅥", 'ヲ' => "𛅦",
        'ン' => "𛅧",
        _ => return None,
    })
}

fn reverse_small(c: char) -> Option<char> {
    Some(match c {
        'ぁ' => 'あ', 'ぃ' => 'い', 'ぅ' => 'う', 'ぇ' => 'え', 'ぉ' => 'お',
        'ゕ' => 'か', 'ゖ' => 'け',
        'っ' => 'つ',
        'ゃ' => 'や', 'ゅ' => 'ゆ', 'ょ' => 'よ',
        'ゎ' => 'わ',
        'ァ' => 'ア', 'ィ' => 'イ', 'ゥ' => 'ウ', 'ェ' => 'エ', 'ォ' => 'オ',
        'ヵ' => 'カ', 'ㇰ' => 'ク', 'ヶ' => 'ケ',
        'ㇱ' => 'シ', 'ㇲ' => 'ス',
        'ッ' => 'ツ', 'ㇳ' => 'ト',
        'ㇴ' => 'ヌ',
        'ㇵ' => 'ハ', 'ㇶ' => 'ヒ', 'ㇷ' => 'フ', 'ㇸ' => 'ヘ', 'ㇹ' => 'ホ',
        'ㇺ' => 'ム',
        'ャ' => 'ヤ', 'ュ' => 'ユ', 'ョ' => 'ヨ',
        'ㇻ' => 'ラ', 'ㇼ' => 'リ', 'ㇽ' => 'ル', 'ㇾ' => 'レ', 'ㇿ' => 'ロ',
        'ヮ' => 'ワ',
        _ => return None,
    })
}

fn is_dakuten(c: char) -> bool {
    c == '\u{3099}' || c == '゛' || c == 'ﾞ'
}

fn is_handakuten(c: char) -> bool {
    c == '\u{309A}' || c == '゜' || c == 'ﾟ'
}

fn is_composing_character(c: char) -> bool {
    c == '\u{3099}' || c == '\u{309A}'
}

fn get_base_character(c: char) -> char {
    reverse_daku(c)
        .or_else(|| reverse_handaku(c))
        .or_else(|| reverse_small(c))
        .unwrap_or(c)
}

/// One transform family (daku / handaku / small). `base` maps base char to
/// transformed form, `rev` un-transforms; non-sticky: an already-transformed
/// char toggles back via `rev` first.
fn handle_transform(
    l: char,
    c: char,
    base: fn(char) -> Option<&'static str>,
    rev: fn(char) -> Option<char>,
    add_on_false: bool,
) -> (i32, String) {
    let base_char = get_base_character(l);
    let trans: Option<String> = match rev(l) {
        Some(r) => Some(r.to_string()),
        None => base(base_char).map(|s| s.to_string()),
    };
    if let Some(t) = trans {
        (1, t)
    } else if is_composing_character(l) && is_composing_character(c) {
        (1, if l == c { String::new() } else { c.to_string() })
    } else {
        (0, if add_on_false { c.to_string() } else { String::new() })
    }
}

fn daku_str(c: char) -> Option<&'static str> {
    // char-to-str adapters so all three families share handle_transform
    match c {
        'う' => Some("ゔ"),
        'か' => Some("が"), 'き' => Some("ぎ"), 'く' => Some("ぐ"), 'け' => Some("げ"), 'こ' => Some("ご"),
        'さ' => Some("ざ"), 'し' => Some("じ"), 'す' => Some("ず"), 'せ' => Some("ぜ"), 'そ' => Some("ぞ"),
        'た' => Some("だ"), 'ち' => Some("ぢ"), 'つ' => Some("づ"), 'て' => Some("で"), 'と' => Some("ど"),
        'は' => Some("ば"), 'ひ' => Some("び"), 'ふ' => Some("ぶ"), 'へ' => Some("べ"), 'ほ' => Some("ぼ"),
        'ウ' => Some("ヴ"),
        'カ' => Some("ガ"), 'キ' => Some("ギ"), 'ク' => Some("グ"), 'ケ' => Some("ゲ"), 'コ' => Some("ゴ"),
        'サ' => Some("ザ"), 'シ' => Some("ジ"), 'ス' => Some("ズ"), 'セ' => Some("ゼ"), 'ソ' => Some("ゾ"),
        'タ' => Some("ダ"), 'チ' => Some("ヂ"), 'ツ' => Some("ヅ"), 'テ' => Some("デ"), 'ト' => Some("ド"),
        'ハ' => Some("バ"), 'ヒ' => Some("ビ"), 'フ' => Some("ブ"), 'ヘ' => Some("ベ"), 'ホ' => Some("ボ"),
        'ワ' => Some("ヷ"), 'ヰ' => Some("ヸ"), 'ヱ' => Some("ヹ"), 'ヲ' => Some("ヺ"),
        'ゝ' => Some("ゞ"), 'ヽ' => Some("ヾ"),
        _ => None,
    }
}

fn handaku_str(c: char) -> Option<&'static str> {
    match c {
        'は' => Some("ぱ"), 'ひ' => Some("ぴ"), 'ふ' => Some("ぷ"), 'へ' => Some("ぺ"), 'ほ' => Some("ぽ"),
        'ハ' => Some("パ"), 'ヒ' => Some("ピ"), 'フ' => Some("プ"), 'ヘ' => Some("ペ"), 'ホ' => Some("ポ"),
        _ => None,
    }
}

/// Kana composition action, bit-exact with Kotlin `KanaUnicode.getActions`.
pub fn kana_unicode_actions(preceding: &str, to_insert: &str) -> (i32, String) {
    let c = match first_unit_char(to_insert) {
        Some(c) => c,
        None => return (0, to_insert.to_string()),
    };
    let last_char = match last_unit_char(preceding) {
        Some(l) => l,
        None => {
            // empty preceding: swallow bare sentinel / combining marks
            return if c == SMALL_SENTINEL || is_composing_character(c) {
                (0, String::new())
            } else {
                (0, to_insert.to_string())
            };
        }
    };

    if is_dakuten(c) {
        handle_transform(last_char, c, daku_str, reverse_daku, true)
    } else if is_handakuten(c) {
        handle_transform(last_char, c, handaku_str, reverse_handaku, true)
    } else if c == SMALL_SENTINEL {
        handle_transform(last_char, c, small, reverse_small, false)
    } else {
        (0, to_insert.to_string())
    }
}

// ---------------------------------------------------------------------------
// Rule-table composer (id "with-rules")
// ---------------------------------------------------------------------------

/// Generic rule-table composer (Kotlin `WithRules`). Entries keep the JSON
/// insertion order; matching tries keys longest-first with length ties in
/// REVERSE insertion order (Kotlin: `sortedBy { length }.reversed()`, a
/// stable ascending sort then a full reverse).
pub struct RuleComposer {
    entries: Vec<(String, String)>,
    order: Vec<usize>,
}

impl RuleComposer {
    pub fn new(entries: Vec<(String, String)>) -> Self {
        let mut order: Vec<usize> = (0..entries.len()).collect();
        order.sort_by_key(|&i| utf16_len(&entries[i].0));
        order.reverse();
        Self { entries, order }
    }

    pub fn get_actions(&self, preceding: &str, to_insert: &str) -> (i32, String) {
        let combined = format!("{preceding}{to_insert}");
        let lower = combined.to_lowercase();
        for &i in &self.order {
            let (key, value) = &self.entries[i];
            if lower.ends_with(key.as_str()) {
                let key_len16 = utf16_len(key);
                // Case is decided by the FIRST UTF-16 unit of the matched
                // tail in the original-case string: uppercase-or-caseless
                // uppercases the whole replacement.
                let combined16: Vec<u16> = combined.encode_utf16().collect();
                let tail_start = combined16.len().saturating_sub(key_len16);
                let is_upper = match combined16.get(tail_start).copied() {
                    None => true, // empty firstOfKey: "" == "".uppercase()
                    Some(u) => match char::from_u32(u as u32) {
                        Some(ch) => {
                            let up: String = ch.to_uppercase().collect();
                            up == ch.to_string()
                        }
                        None => true, // surrogate half uppercases to itself
                    },
                };
                let out = if is_upper { value.to_uppercase() } else { value.clone() };
                return (key_len16 as i32 - 1, out);
            }
        }
        (0, to_insert.to_string())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn h(p: &str, t: &str) -> (i32, String) {
        hangul_unicode_actions(p, t)
    }
    fn k(p: &str, t: &str) -> (i32, String) {
        kana_unicode_actions(p, t)
    }

    #[test]
    fn hangul_basic_syllable_assembly() {
        // ㅎ + ㅏ -> 하
        assert_eq!(h("ㅎ", "ㅏ"), (1, "하".to_string()));
        // 하 + ㄴ -> 한
        assert_eq!(h("하", "ㄴ"), (1, "한".to_string()));
        // 한 + ㅏ -> 하 + 나 (final splits off as initial of next syllable)
        assert_eq!(h("한", "ㅏ"), (1, "하나".to_string()));
    }

    #[test]
    fn hangul_composed_finals_and_medials() {
        // 갑 + ㅅ -> 값 (ㅂ+ㅅ composed final)
        assert_eq!(h("갑", "ㅅ"), (1, "값".to_string()));
        // 값 + ㅏ -> 갑 + 사 (composed final splits)
        assert_eq!(h("값", "ㅏ"), (1, "갑사".to_string()));
        // 고 + ㅏ -> 과 (ㅗ+ㅏ composed medial)
        assert_eq!(h("고", "ㅏ"), (1, "과".to_string()));
        // bare jamo composition: ㅗ + ㅏ -> ㅘ, ㄱ + ㅅ -> ㄳ
        assert_eq!(h("ㅗ", "ㅏ"), (1, "ㅘ".to_string()));
        assert_eq!(h("ㄱ", "ㅅ"), (1, "ㄳ".to_string()));
    }

    #[test]
    fn hangul_sentinels_and_passthrough() {
        assert_eq!(h("한", "_"), (0, "_".to_string()));
        assert_eq!(h("", "ㅏ"), (0, "ㅏ".to_string()));
        assert_eq!(h("a", "b"), (0, "b".to_string()));
        // syllable with final + another final that doesn't compose
        assert_eq!(h("한", "ㄷ"), (0, "ㄷ".to_string()));
    }

    #[test]
    fn kana_dakuten_toggle() {
        // か + dakuten -> が, が + dakuten -> か (non-sticky toggles back)
        assert_eq!(k("か", "゛"), (1, "が".to_string()));
        assert_eq!(k("が", "゛"), (1, "か".to_string()));
        // は + handakuten -> ぱ, ぱ + handakuten -> は
        assert_eq!(k("は", "゜"), (1, "ぱ".to_string()));
        assert_eq!(k("ぱ", "゜"), (1, "は".to_string()));
        // cross-family: ぱ + dakuten -> ば (via base char は)
        assert_eq!(k("ぱ", "゛"), (1, "ば".to_string()));
    }

    #[test]
    fn kana_small_and_sentinel() {
        assert_eq!(k("つ", "〓"), (1, "っ".to_string()));
        assert_eq!(k("っ", "〓"), (1, "つ".to_string()));
        // astral small kana
        assert_eq!(k("ゐ", "〓"), (1, "𛅐".to_string()));
        // sentinel on non-transformable char is swallowed
        assert_eq!(k("x", "〓"), (0, String::new()));
        // sentinel with empty preceding is swallowed
        assert_eq!(k("", "〓"), (0, String::new()));
        // dakuten on non-transformable char passes the mark through
        assert_eq!(k("x", "゛"), (0, "゛".to_string()));
    }

    #[test]
    fn kana_combining_marks() {
        // combining + same combining cancels
        assert_eq!(k("\u{3099}", "\u{3099}"), (1, String::new()));
        // combining + different combining replaces
        assert_eq!(k("\u{3099}", "\u{309A}"), (1, "\u{309A}".to_string()));
        // empty preceding + combining mark is swallowed
        assert_eq!(k("", "\u{3099}"), (0, String::new()));
    }

    #[test]
    fn rules_longest_match_and_case() {
        let rc = RuleComposer::new(vec![
            ("aa".into(), "â".into()),
            ("a".into(), "á".into()),
            ("dd".into(), "đ".into()),
        ]);
        // longest key wins
        assert_eq!(rc.get_actions("a", "a"), (1, "â".to_string()));
        // single-char key deletes nothing
        assert_eq!(rc.get_actions("", "a"), (0, "á".to_string()));
        // uppercase first char of matched tail uppercases the value
        assert_eq!(rc.get_actions("D", "d"), (1, "Đ".to_string()));
        assert_eq!(rc.get_actions("x", "y"), (0, "y".to_string()));
    }

    #[test]
    fn rules_tie_order_is_reverse_insertion() {
        // Two same-length keys matching the same tail: Kotlin's
        // sortedBy{length}.reversed() puts the LATER entry first.
        let rc = RuleComposer::new(vec![
            ("ab".into(), "FIRST".into()),
            ("ab".into(), "SECOND".into()),
        ]);
        assert_eq!(rc.get_actions("a", "b").1, "second".to_uppercase());
    }

    #[test]
    fn rules_utf16_lengths() {
        // astral key: UTF-16 length 2, so delete count must be 1
        let rc = RuleComposer::new(vec![("𝕒".into(), "A".into())]);
        assert_eq!(rc.get_actions("", "𝕒"), (1, "A".to_string()));
    }

    #[test]
    fn surrogate_halves_never_match() {
        // preceding ending in an astral char: last UTF-16 unit is a low
        // surrogate; composers must fall through to no-action.
        assert_eq!(h("𝕒", "ㅏ"), (0, "ㅏ".to_string()));
        assert_eq!(k("𝕒", "゛"), (0, "゛".to_string()));
    }
}
