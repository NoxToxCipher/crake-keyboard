//! Binary dictionary blob ("CRKD") loading.
//!
//! The shipped dictionary used to travel as JSON parsed on the JVM and fed to
//! the trie one JNI call per word — measured on device at ~860ms of JSON
//! parsing plus ~530ms across ~50k JNI crossings, all on the perceived
//! cold-start path. The blob replaces that with one JNI call handing the raw
//! asset bytes to this parser.
//!
//! The format is deliberately dumb — a counted sequence of length-prefixed
//! entries, no serialized trie, nothing clever to version-skew on:
//!
//! ```text
//! magic   b"CRKD"          4 bytes
//! version u8 = 1
//! count   u32 LE
//! entry*  { len: u16 LE, word: UTF-8 bytes, freq: u32 LE }
//! ```
//!
//! Declared lengths are treated as hostile even though the asset ships inside
//! our own APK: every read is bounds-checked *before* the offset advances, and
//! guards are written so they cannot themselves underflow on the inputs they
//! exist to reject.

pub const BLOB_MAGIC: [u8; 4] = *b"CRKD";
pub const BLOB_VERSION: u8 = 1;
/// Ceiling on the declared entry count; far above any real dictionary but low
/// enough that a corrupt count cannot drive unbounded work.
pub const MAX_WORDS: u32 = 1_000_000;
/// Longest word we accept; matches the trie's practical key sizes.
pub const MAX_WORD_LEN: usize = 64;

#[derive(Debug, PartialEq, Eq)]
pub enum BlobError {
    /// Too short for even the header, or not our magic/version.
    NotOurs,
    /// Declared count exceeds [`MAX_WORDS`].
    CountTooLarge(u32),
    /// The bytes ended before the declared entries did, or an entry length
    /// is zero or exceeds [`MAX_WORD_LEN`].
    Truncated,
    /// An entry's bytes are not valid UTF-8.
    BadUtf8,
}

const HEADER_LEN: usize = 4 + 1 + 4;

/// Parses `data` and calls `on_word(word, freq)` for each entry.
///
/// Returns the number of entries delivered. On error, some entries may
/// already have been delivered; callers that need all-or-nothing semantics
/// should parse into a scratch collection first. The single caller (the JNI
/// bulk load) treats any error as "fall back to the JSON path", where a
/// partially warmed trie is harmless — inserts are idempotent.
pub fn parse_dict_blob(
    data: &[u8],
    mut on_word: impl FnMut(&str, u32),
) -> Result<u32, BlobError> {
    if data.len() < HEADER_LEN || data[0..4] != BLOB_MAGIC || data[4] != BLOB_VERSION {
        return Err(BlobError::NotOurs);
    }
    let count = u32::from_le_bytes([data[5], data[6], data[7], data[8]]);
    if count > MAX_WORDS {
        return Err(BlobError::CountTooLarge(count));
    }

    let mut offset = HEADER_LEN;
    for _ in 0..count {
        // Bounds first, arithmetic second: `remaining` can never underflow.
        let remaining = data.len() - offset;
        if remaining < 2 {
            return Err(BlobError::Truncated);
        }
        let word_len = u16::from_le_bytes([data[offset], data[offset + 1]]) as usize;
        if word_len == 0 || word_len > MAX_WORD_LEN {
            return Err(BlobError::Truncated);
        }
        if remaining < 2 + word_len + 4 {
            return Err(BlobError::Truncated);
        }
        let word_start = offset + 2;
        let word = core::str::from_utf8(&data[word_start..word_start + word_len])
            .map_err(|_| BlobError::BadUtf8)?;
        let freq_start = word_start + word_len;
        let freq = u32::from_le_bytes([
            data[freq_start],
            data[freq_start + 1],
            data[freq_start + 2],
            data[freq_start + 3],
        ]);
        on_word(word, freq);
        offset = freq_start + 4;
    }
    Ok(count)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn encode(entries: &[(&str, u32)]) -> Vec<u8> {
        let mut out = Vec::new();
        out.extend_from_slice(&BLOB_MAGIC);
        out.push(BLOB_VERSION);
        out.extend_from_slice(&(entries.len() as u32).to_le_bytes());
        for (word, freq) in entries {
            out.extend_from_slice(&(word.len() as u16).to_le_bytes());
            out.extend_from_slice(word.as_bytes());
            out.extend_from_slice(&freq.to_le_bytes());
        }
        out
    }

    fn collect(data: &[u8]) -> Result<Vec<(String, u32)>, BlobError> {
        let mut words = Vec::new();
        parse_dict_blob(data, |w, f| words.push((w.to_string(), f)))?;
        Ok(words)
    }

    #[test]
    fn round_trips_entries_in_order() {
        let blob = encode(&[("the", 255), ("quick", 200), ("naïve", 90)]);
        let words = collect(&blob).unwrap();
        assert_eq!(
            words,
            vec![
                ("the".to_string(), 255),
                ("quick".to_string(), 200),
                ("naïve".to_string(), 90),
            ]
        );
    }

    #[test]
    fn empty_dictionary_is_valid() {
        assert_eq!(collect(&encode(&[])).unwrap(), vec![]);
    }

    #[test]
    fn rejects_wrong_magic_version_and_short_input() {
        assert_eq!(collect(b"JSON{}"), Err(BlobError::NotOurs));
        assert_eq!(collect(&[]), Err(BlobError::NotOurs));
        let mut blob = encode(&[("a", 1)]);
        blob[4] = 2; // future version
        assert_eq!(collect(&blob), Err(BlobError::NotOurs));
    }

    #[test]
    fn rejects_absurd_count_without_reading_entries() {
        let mut blob = encode(&[]);
        blob[5..9].copy_from_slice(&(MAX_WORDS + 1).to_le_bytes());
        assert_eq!(collect(&blob), Err(BlobError::CountTooLarge(MAX_WORDS + 1)));
    }

    #[test]
    fn rejects_truncation_at_every_boundary() {
        let blob = encode(&[("word", 42)]);
        // Every proper prefix that still passes the header must be Truncated.
        for cut in HEADER_LEN..blob.len() {
            assert_eq!(collect(&blob[..cut]), Err(BlobError::Truncated), "cut={cut}");
        }
    }

    #[test]
    fn rejects_zero_and_oversized_word_lengths() {
        let mut blob = encode(&[("word", 42)]);
        blob[9..11].copy_from_slice(&0u16.to_le_bytes());
        assert_eq!(collect(&blob), Err(BlobError::Truncated));
        blob[9..11].copy_from_slice(&((MAX_WORD_LEN + 1) as u16).to_le_bytes());
        assert_eq!(collect(&blob), Err(BlobError::Truncated));
    }

    #[test]
    fn rejects_invalid_utf8() {
        let mut blob = encode(&[("ab", 1)]);
        blob[11] = 0xFF; // first word byte
        assert_eq!(collect(&blob), Err(BlobError::BadUtf8));
    }

    #[test]
    fn declared_count_beyond_data_is_truncated_not_panic() {
        let mut blob = encode(&[("a", 1)]);
        blob[5..9].copy_from_slice(&2u32.to_le_bytes());
        assert_eq!(collect(&blob), Err(BlobError::Truncated));
    }
}
