//! Real-time URL and metadata tracking parameter sanitizer.

/// Known privacy-invasive tracking query parameter keys.
pub const TRACKING_PARAMS: &[&str] = &[
    // Google Analytics / Urchin
    "utm_source",
    "utm_medium",
    "utm_campaign",
    "utm_term",
    "utm_content",
    "utm_id",
    "utm_name",
    "utm_cid",
    "utm_reader",
    "utm_viz_id",
    "utm_pubreferrer",
    "utm_swu",
    // Facebook / Meta
    "fbclid",
    "fb_action_ids",
    "fb_action_types",
    "fb_source",
    "fb_ref",
    // Google Ads
    "gclid",
    "gbraid",
    "wbraid",
    "dclid",
    "gclsrc",
    // YouTube / Spotify / Social Share Identifiers
    "si",
    "igshid",
    "igsh",
    "share_id",
    "share_token",
    // Microsoft / Bing
    "msclkid",
    // Twitter / X
    "twclid",
    "ref_src",
    "ref_url",
    // Mailchimp / Hubspot / Marketing
    "mc_eid",
    "mc_cid",
    "_hsenc",
    "_hsmi",
    "hsCtaTracking",
    // Yandex
    "yclid",
    "_openstat",
    // LinkedIn
    "trk",
    "trackingId",
    "midToken",
    // TikTok
    "tt_medium",
    "tt_content",
];

/// Sanitizes a single URL string by stripping all known tracking parameters while preserving benign queries and anchors.
#[must_use]
pub fn sanitize_url(raw_url: &str) -> String {
    let trimmed = raw_url.trim();
    if !trimmed.starts_with("http://") && !trimmed.starts_with("https://") {
        return raw_url.to_string();
    }

    // Split anchor #fragment if present
    let (url_without_fragment, fragment) = match trimmed.find('#') {
        Some(idx) => (&trimmed[..idx], Some(&trimmed[idx..])),
        None => (trimmed, None),
    };

    // Split query ?params if present
    let (base, query_str) = match url_without_fragment.find('?') {
        Some(idx) => (&url_without_fragment[..idx], Some(&url_without_fragment[idx + 1..])),
        None => return raw_url.to_string(),
    };

    let Some(query) = query_str else {
        return raw_url.to_string();
    };

    if query.is_empty() {
        return format!("{}{}", base, fragment.unwrap_or(""));
    }

    let mut clean_params: Vec<String> = Vec::new();

    for param in query.split('&') {
        if param.is_empty() {
            continue;
        }

        let key = match param.find('=') {
            Some(eq_idx) => &param[..eq_idx],
            None => param,
        };

        let key_lower = key.to_ascii_lowercase();

        let is_tracking = TRACKING_PARAMS
            .iter()
            .any(|&t| t.eq_ignore_ascii_case(&key_lower));

        if !is_tracking {
            clean_params.push(param.to_string());
        }
    }

    let mut result = base.to_string();
    if !clean_params.is_empty() {
        result.push('?');
        result.push_str(&clean_params.join("&"));
    }

    if let Some(frag) = fragment {
        result.push_str(frag);
    }

    result
}

/// Scans an arbitrary block of text and sanitizes any URLs contained within it.
#[must_use]
pub fn sanitize_text(text: &str) -> String {
    let mut output = String::with_capacity(text.len());
    let mut cursor = 0;

    while cursor < text.len() {
        let remaining = &text[cursor..];
        let next_http = remaining.find("http://").or_else(|| remaining.find("https://"));

        match next_http {
            Some(offset) => {
                let url_start = cursor + offset;
                output.push_str(&text[cursor..url_start]);

                let url_remaining = &text[url_start..];
                let url_len = url_remaining
                    .find(|c: char| c.is_whitespace() || c == ')' || c == ']' || c == '>' || c == '"' || c == '\'')
                    .unwrap_or(url_remaining.len());

                let raw_url = &text[url_start..url_start + url_len];
                let clean_url = sanitize_url(raw_url);
                output.push_str(&clean_url);

                cursor = url_start + url_len;
            }
            None => {
                output.push_str(&text[cursor..]);
                break;
            }
        }
    }

    output
}

#[cfg(test)]
mod tests {
    use super::*;
    use proptest::prelude::*;

    #[test]
    fn test_standalone_url_sanitization() {
        let dirty = "https://example.com/item?id=12345&utm_source=facebook&utm_medium=cpc&fbclid=IwAR123#reviews";
        let clean = sanitize_url(dirty);
        assert_eq!(clean, "https://example.com/item?id=12345#reviews");
    }

    #[test]
    fn test_youtube_share_id() {
        let dirty = "https://youtu.be/dQw4w9WgXcQ?si=abcdef123456";
        let clean = sanitize_url(dirty);
        assert_eq!(clean, "https://youtu.be/dQw4w9WgXcQ");
    }

    #[test]
    fn test_text_containing_multiple_urls() {
        let message = "Check out https://news.site/article?utm_campaign=winter_sale and also https://shop.com/buy?item=shoes&fbclid=xyz999 for details!";
        let sanitized = sanitize_text(message);
        assert_eq!(
            sanitized,
            "Check out https://news.site/article and also https://shop.com/buy?item=shoes for details!"
        );
    }

    #[test]
    fn test_non_url_text_preserved() {
        let raw = "Just a normal conversation with no links: 1 + 1 = 2 & a < b";
        assert_eq!(sanitize_text(raw), raw);
    }

    // Property-Based Verification & Fuzzing Oracles
    proptest! {
        #[test]
        fn prop_sanitization_is_idempotent(text in "\\PC{0,100}") {
            let once = sanitize_text(&text);
            let twice = sanitize_text(&once);
            prop_assert_eq!(once, twice, "Sanitization must be strictly idempotent");
        }

        #[test]
        fn prop_never_panics_on_arbitrary_input(text in "\\PC*") {
            let _ = sanitize_text(&text);
        }

        #[test]
        fn prop_zero_tracking_leakage(
            domain in "[a-z]{3,10}\\.com",
            path in "/[a-z0-9/]{0,10}",
            benign_key_suffix in "[a-z0-9]{1,5}",
            benign_v in "[a-z0-9]{1,5}"
        ) {
            let benign_k = format!("app_param_{}", benign_key_suffix);
            let url = format!(
                "https://{}{}?{}={}&utm_source=evil&fbclid=123456&si=share_token_abc&gclid=ad999",
                domain, path, benign_k, benign_v
            );
            let cleaned = sanitize_url(&url);

            for &tracking in TRACKING_PARAMS {
                let needle_start = format!("?{}=", tracking);
                let needle_mid = format!("&{}=", tracking);
                prop_assert!(
                    !cleaned.contains(&needle_start) && !cleaned.contains(&needle_mid),
                    "Found leaked tracking parameter: {}",
                    tracking
                );
            }

            let expected_benign = format!("{}={}", benign_k, benign_v);
            prop_assert!(cleaned.contains(&domain));
            prop_assert!(cleaned.contains(&expected_benign));
        }
    }
}
