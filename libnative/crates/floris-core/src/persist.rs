//! CRKL: persistence for the keyboard's LEARNED state — user-learned words
//! and personal typo-correction habits.
//!
//! Until this existed, everything the keyboard learned lived in process
//! memory and died on every restart (during development: every install,
//! i.e. constantly). The blob is written to app-private storage by the
//! Kotlin side; nothing here does I/O. Learned content is guarded upstream:
//! the Secret Shield filters secrets before words are ever learned, and
//! incognito sessions never learn at all.
//!
//! Format (little-endian), hostile-parse discipline as with CRKD/CRKB:
//! ```text
//! magic   b"CRKL"
//! version u8 = 1
//! words   u32 count, then { len: u16, word: UTF-8, freq: u32 }*
//! pairs   u32 count, then { tlen: u16, typo, ilen: u16, intended, n: u32 }*
//! ```

pub const LEARNED_MAGIC: [u8; 4] = *b"CRKL";
pub const LEARNED_VERSION: u8 = 1;
/// Size discipline (standing directive: no bloat): both sections capped.
pub const MAX_LEARNED_WORDS: u32 = 5_000;
pub const MAX_CORRECTIONS: u32 = 5_000;
pub const MAX_TOKEN_LEN: usize = 64;

#[derive(Debug, PartialEq, Eq)]
pub enum LearnedError {
    NotOurs,
    CountTooLarge(u32),
    Truncated,
    BadUtf8,
}

#[derive(Debug, Default, PartialEq)]
pub struct LearnedState {
    pub words: Vec<(String, u32)>,
    pub corrections: Vec<(String, String, u32)>,
}

fn push_token(out: &mut Vec<u8>, token: &str) {
    out.extend_from_slice(&(token.len() as u16).to_le_bytes());
    out.extend_from_slice(token.as_bytes());
}

fn read_token<'a>(data: &'a [u8], offset: &mut usize) -> Result<&'a str, LearnedError> {
    let rest = data.len() - *offset;
    if rest < 2 {
        return Err(LearnedError::Truncated);
    }
    let len = u16::from_le_bytes([data[*offset], data[*offset + 1]]) as usize;
    if len == 0 || len > MAX_TOKEN_LEN || rest < 2 + len {
        return Err(LearnedError::Truncated);
    }
    let s = core::str::from_utf8(&data[*offset + 2..*offset + 2 + len])
        .map_err(|_| LearnedError::BadUtf8)?;
    *offset += 2 + len;
    Ok(s)
}

fn read_u32(data: &[u8], offset: &mut usize) -> Result<u32, LearnedError> {
    if data.len() - *offset < 4 {
        return Err(LearnedError::Truncated);
    }
    let v = u32::from_le_bytes([
        data[*offset],
        data[*offset + 1],
        data[*offset + 2],
        data[*offset + 3],
    ]);
    *offset += 4;
    Ok(v)
}

impl LearnedState {
    pub fn serialize(&self) -> Vec<u8> {
        let mut out = Vec::new();
        out.extend_from_slice(&LEARNED_MAGIC);
        out.push(LEARNED_VERSION);
        let words = &self.words[..self.words.len().min(MAX_LEARNED_WORDS as usize)];
        out.extend_from_slice(&(words.len() as u32).to_le_bytes());
        for (word, freq) in words {
            push_token(&mut out, word);
            out.extend_from_slice(&freq.to_le_bytes());
        }
        let pairs = &self.corrections[..self.corrections.len().min(MAX_CORRECTIONS as usize)];
        out.extend_from_slice(&(pairs.len() as u32).to_le_bytes());
        for (typo, intended, n) in pairs {
            push_token(&mut out, typo);
            push_token(&mut out, intended);
            out.extend_from_slice(&n.to_le_bytes());
        }
        out
    }

    pub fn parse(data: &[u8]) -> Result<Self, LearnedError> {
        if data.len() < 5 || data[0..4] != LEARNED_MAGIC || data[4] != LEARNED_VERSION {
            return Err(LearnedError::NotOurs);
        }
        let mut offset = 5;
        let word_count = read_u32(data, &mut offset)?;
        if word_count > MAX_LEARNED_WORDS {
            return Err(LearnedError::CountTooLarge(word_count));
        }
        let mut words = Vec::with_capacity(word_count as usize);
        for _ in 0..word_count {
            let word = read_token(data, &mut offset)?.to_string();
            let freq = read_u32(data, &mut offset)?;
            words.push((word, freq));
        }
        let pair_count = read_u32(data, &mut offset)?;
        if pair_count > MAX_CORRECTIONS {
            return Err(LearnedError::CountTooLarge(pair_count));
        }
        let mut corrections = Vec::with_capacity(pair_count as usize);
        for _ in 0..pair_count {
            let typo = read_token(data, &mut offset)?.to_string();
            let intended = read_token(data, &mut offset)?.to_string();
            let n = read_u32(data, &mut offset)?;
            corrections.push((typo, intended, n));
        }
        Ok(Self { words, corrections })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> LearnedState {
        LearnedState {
            words: vec![("crake".into(), 180), ("roratus".into(), 120)],
            corrections: vec![("thay".into(), "that".into(), 3), ("hte".into(), "the".into(), 7)],
        }
    }

    #[test]
    fn round_trips() {
        let s = sample();
        assert_eq!(LearnedState::parse(&s.serialize()).unwrap(), s);
    }

    #[test]
    fn empty_state_round_trips() {
        let s = LearnedState::default();
        assert_eq!(LearnedState::parse(&s.serialize()).unwrap(), s);
    }

    #[test]
    fn rejects_garbage_and_truncation_at_every_boundary() {
        assert_eq!(LearnedState::parse(b"junk"), Err(LearnedError::NotOurs));
        let blob = sample().serialize();
        for cut in 5..blob.len() {
            assert!(
                LearnedState::parse(&blob[..cut]).is_err(),
                "cut={cut} must not parse"
            );
        }
    }

    #[test]
    fn rejects_absurd_counts() {
        let mut blob = LearnedState::default().serialize();
        blob[5..9].copy_from_slice(&(MAX_LEARNED_WORDS + 1).to_le_bytes());
        assert_eq!(
            LearnedState::parse(&blob),
            Err(LearnedError::CountTooLarge(MAX_LEARNED_WORDS + 1))
        );
    }

    #[test]
    fn serialize_enforces_the_size_caps() {
        let mut s = LearnedState::default();
        for i in 0..(MAX_LEARNED_WORDS as usize + 100) {
            s.words.push((format!("w{i}"), 10));
        }
        let parsed = LearnedState::parse(&s.serialize()).unwrap();
        assert_eq!(parsed.words.len(), MAX_LEARNED_WORDS as usize);
    }
}
