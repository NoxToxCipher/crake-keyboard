//! MetaScrub: Advanced zero-width steganography, invisible watermark, and metadata scrubber.

use crate::sanitizer::sanitize_text;

/// Unicode invisible codepoints used for text fingerprinting and steganography.
pub const INVISIBLE_CODEPOINTS: &[char] = &[
    '\u{200B}', // Zero Width Space
    '\u{200C}', // Zero Width Non-Joiner
    '\u{200D}', // Zero Width Joiner
    '\u{200E}', // Left-to-Right Mark
    '\u{200F}', // Right-to-Left Mark
    '\u{202A}', // Left-to-Right Embedding
    '\u{202B}', // Right-to-Left Embedding
    '\u{202C}', // Pop Directional Formatting
    '\u{202D}', // Left-to-Right Override
    '\u{202E}', // Right-to-Left Override
    '\u{2060}', // Word Joiner
    '\u{2061}', // Function Application
    '\u{2062}', // Invisible Times
    '\u{2063}', // Invisible Separator
    '\u{2064}', // Invisible Plus
    '\u{FEFF}', // Zero Width No-Break Space (BOM)
];

/// Result of scrubbing text with MetaScrub.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct MetaScrubResult {
    pub cleaned_text: String,
    pub invisible_chars_removed: usize,
    pub urls_sanitized: bool,
}

/// Checks whether a character is an invisible steganography / watermark marker.
#[inline]
#[must_use]
pub fn is_invisible_char(c: char) -> bool {
    // Check known invisible codepoints
    if INVISIBLE_CODEPOINTS.contains(&c) {
        return true;
    }
    // Check Unicode tag block (U+E0000 - U+E007F) used for LLM watermarking
    let u = c as u32;
    (0xE0000..=0xE007F).contains(&u)
}

/// Strips all invisible zero-width watermarks and tracking bytes from text.
#[must_use]
pub fn strip_invisible_characters(text: &str) -> (String, usize) {
    if text.is_empty() {
        return (String::new(), 0);
    }
    let mut cleaned = String::with_capacity(text.len());
    let mut removed = 0;

    for c in text.chars() {
        if is_invisible_char(c) {
            removed += 1;
        } else {
            cleaned.push(c);
        }
    }

    (cleaned, removed)
}

/// Full MetaScrub pipeline: cleans invisible watermarks and purges tracking URLs.
#[must_use]
pub fn metascrub_text(text: &str) -> MetaScrubResult {
    if text.is_empty() {
        return MetaScrubResult {
            cleaned_text: String::new(),
            invisible_chars_removed: 0,
            urls_sanitized: false,
        };
    }
    let (stripped_text, removed_count) = strip_invisible_characters(text);
    let sanitized_url_text = sanitize_text(&stripped_text);
    let urls_sanitized = sanitized_url_text != stripped_text;

    MetaScrubResult {
        cleaned_text: sanitized_url_text,
        invisible_chars_removed: removed_count,
        urls_sanitized,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    #[test]
    fn test_zero_width_space_removal() {
        let dirty = "Hello\u{200B}World\u{FEFF}Test\u{200C}!";
        let (clean, count) = strip_invisible_characters(dirty);
        assert_eq!(clean, "HelloWorldTest!");
        assert_eq!(count, 3);
    }

    #[test]
    fn test_llm_tag_watermark_removal() {
        let dirty = "Document\u{E0020}\u{E0041}Clean";
        let (clean, count) = strip_invisible_characters(dirty);
        assert_eq!(clean, "DocumentClean");
        assert_eq!(count, 2);
    }

    #[test]
    fn test_full_metascrub_text() {
        let dirty = "Visit\u{200B} https://site.org/post?utm_source=tracker\u{FEFF} now!";
        let res = metascrub_text(dirty);
        assert_eq!(res.cleaned_text, "Visit https://site.org/post now!");
        assert_eq!(res.invisible_chars_removed, 2);
        assert!(res.urls_sanitized);
    }

    proptest! {
        #[test]
        fn prop_metascrub_is_idempotent(text in "\\PC{0,100}") {
            let once = metascrub_text(&text);
            let twice = metascrub_text(&once.cleaned_text);
            prop_assert_eq!(once.cleaned_text, twice.cleaned_text);
            prop_assert_eq!(twice.invisible_chars_removed, 0);
        }
    }
}
