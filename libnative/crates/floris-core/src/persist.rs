//! CRKL: persistence for the keyboard's LEARNED state — user-learned words,
//! personal typo-correction habits, personal bigrams, anti-sticky rejected
//! corrections, and recency-decay epochs.
//!
//! Format (little-endian), hostile-parse discipline with backwards compatibility:
//! ```text
//! magic                b"CRKL"
//! version              u8 = 3
//! words                u32 count, then { len: u16, word: UTF-8, freq: u32 }*
//! pairs                u32 count, then { tlen: u16, typo, ilen: u16, intended, n: u32 }*
//! bigrams (v2+)        u32 count, then { w1len: u16, w1, w2len: u16, w2, n: u32 }*
//! rejected (v3+)       u32 count, then { tlen: u16, typo, wlen: u16, wrong, n: u32 }*
//! word_epochs (v3+)    u32 count, then { len: u16, word: UTF-8, epoch: u64 }*
//! ```

pub const LEARNED_MAGIC: [u8; 4] = *b"CRKL";
/// v1: words + corrections
/// v2: words + corrections + personal bigrams
/// v3: words + corrections + personal bigrams + rejected corrections + decay epochs
pub const LEARNED_VERSION: u8 = 3;

/// Size discipline (standing directive: no bloat): every section capped.
pub const MAX_LEARNED_WORDS: u32 = 5_000;
pub const MAX_CORRECTIONS: u32 = 5_000;
pub const MAX_PERSONAL_BIGRAMS: u32 = 2_000;
pub const MAX_REJECTED_CORRECTIONS: u32 = 2_000;
pub const MAX_WORD_EPOCHS: u32 = 5_000;
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
    /// The user's own consecutive word pairs with use counts (v2+).
    pub bigrams: Vec<(String, String, u32)>,
    /// Anti-sticky rejected corrections (v3+).
    pub rejected: Vec<(String, String, u32)>,
    /// Interaction epochs for recency decay (v3+).
    pub word_epochs: Vec<(String, u64)>,
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

fn read_u64(data: &[u8], offset: &mut usize) -> Result<u64, LearnedError> {
    if data.len() - *offset < 8 {
        return Err(LearnedError::Truncated);
    }
    let mut bytes = [0u8; 8];
    bytes.copy_from_slice(&data[*offset..*offset + 8]);
    *offset += 8;
    Ok(u64::from_le_bytes(bytes))
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
        let bigrams = &self.bigrams[..self.bigrams.len().min(MAX_PERSONAL_BIGRAMS as usize)];
        out.extend_from_slice(&(bigrams.len() as u32).to_le_bytes());
        for (w1, w2, n) in bigrams {
            push_token(&mut out, w1);
            push_token(&mut out, w2);
            out.extend_from_slice(&n.to_le_bytes());
        }
        let rejected = &self.rejected[..self.rejected.len().min(MAX_REJECTED_CORRECTIONS as usize)];
        out.extend_from_slice(&(rejected.len() as u32).to_le_bytes());
        for (typo, wrong, n) in rejected {
            push_token(&mut out, typo);
            push_token(&mut out, wrong);
            out.extend_from_slice(&n.to_le_bytes());
        }
        let epochs = &self.word_epochs[..self.word_epochs.len().min(MAX_WORD_EPOCHS as usize)];
        out.extend_from_slice(&(epochs.len() as u32).to_le_bytes());
        for (word, epoch) in epochs {
            push_token(&mut out, word);
            out.extend_from_slice(&epoch.to_le_bytes());
        }
        out
    }

    pub fn parse(data: &[u8]) -> Result<Self, LearnedError> {
        if data.len() < 5 || data[0..4] != LEARNED_MAGIC {
            return Err(LearnedError::NotOurs);
        }
        let version = data[4];
        if version == 0 || version > LEARNED_VERSION {
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
        let mut bigrams = Vec::new();
        if version >= 2 {
            let bigram_count = read_u32(data, &mut offset)?;
            if bigram_count > MAX_PERSONAL_BIGRAMS {
                return Err(LearnedError::CountTooLarge(bigram_count));
            }
            bigrams.reserve(bigram_count as usize);
            for _ in 0..bigram_count {
                let w1 = read_token(data, &mut offset)?.to_string();
                let w2 = read_token(data, &mut offset)?.to_string();
                let n = read_u32(data, &mut offset)?;
                bigrams.push((w1, w2, n));
            }
        }
        let mut rejected = Vec::new();
        let mut word_epochs = Vec::new();
        if version >= 3 {
            let rej_count = read_u32(data, &mut offset)?;
            if rej_count > MAX_REJECTED_CORRECTIONS {
                return Err(LearnedError::CountTooLarge(rej_count));
            }
            rejected.reserve(rej_count as usize);
            for _ in 0..rej_count {
                let typo = read_token(data, &mut offset)?.to_string();
                let wrong = read_token(data, &mut offset)?.to_string();
                let n = read_u32(data, &mut offset)?;
                rejected.push((typo, wrong, n));
            }
            let epoch_count = read_u32(data, &mut offset)?;
            if epoch_count > MAX_WORD_EPOCHS {
                return Err(LearnedError::CountTooLarge(epoch_count));
            }
            word_epochs.reserve(epoch_count as usize);
            for _ in 0..epoch_count {
                let word = read_token(data, &mut offset)?.to_string();
                let ep = read_u64(data, &mut offset)?;
                word_epochs.push((word, ep));
            }
        }
        Ok(Self { words, corrections, bigrams, rejected, word_epochs })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> LearnedState {
        LearnedState {
            words: vec![("crake".into(), 180), ("roratus".into(), 120)],
            corrections: vec![("thay".into(), "that".into(), 3), ("hte".into(), "the".into(), 7)],
            bigrams: vec![("glossy".into(), "cockatoo".into(), 2)],
            rejected: vec![("idk".into(), "ink".into(), 2)],
            word_epochs: vec![("roratus".into(), 105)],
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
    fn v1_blobs_still_parse_with_empty_bigrams_and_rejected() {
        let mut v1 = Vec::new();
        v1.extend_from_slice(&LEARNED_MAGIC);
        v1.push(1);
        v1.extend_from_slice(&1u32.to_le_bytes());
        push_token(&mut v1, "crake");
        v1.extend_from_slice(&180u32.to_le_bytes());
        v1.extend_from_slice(&0u32.to_le_bytes());
        let parsed = LearnedState::parse(&v1).unwrap();
        assert_eq!(parsed.words, vec![("crake".to_string(), 180)]);
        assert!(parsed.bigrams.is_empty());
        assert!(parsed.rejected.is_empty());
        assert!(parsed.word_epochs.is_empty());
    }

    #[test]
    fn v2_blobs_still_parse_with_empty_rejected_and_epochs() {
        let mut v2 = Vec::new();
        v2.extend_from_slice(&LEARNED_MAGIC);
        v2.push(2);
        v2.extend_from_slice(&1u32.to_le_bytes());
        push_token(&mut v2, "crake");
        v2.extend_from_slice(&180u32.to_le_bytes());
        v2.extend_from_slice(&0u32.to_le_bytes());
        v2.extend_from_slice(&1u32.to_le_bytes());
        push_token(&mut v2, "glossy");
        push_token(&mut v2, "cockatoo");
        v2.extend_from_slice(&2u32.to_le_bytes());
        let parsed = LearnedState::parse(&v2).unwrap();
        assert_eq!(parsed.words, vec![("crake".to_string(), 180)]);
        assert_eq!(parsed.bigrams, vec![("glossy".to_string(), "cockatoo".to_string(), 2)]);
        assert!(parsed.rejected.is_empty());
        assert!(parsed.word_epochs.is_empty());
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
