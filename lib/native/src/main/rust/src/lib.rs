use floris_core::NlpEngine;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jobjectArray, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::sync::RwLock;

static NLP_ENGINE: Lazy<RwLock<NlpEngine>> = Lazy::new(|| RwLock::new(NlpEngine::new()));
static BOREAL_SCANNER: Lazy<RwLock<crake_privacy::BorealScanner>> =
    Lazy::new(|| RwLock::new(crake_privacy::BorealScanner::new().expect("Failed to init Boreal")));

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpInsertWord(
    mut env: JNIEnv,
    _class: JClass,
    word: JString,
    frequency: jint,
) {
    if let Ok(w) = env.get_string(&word) {
        if let Ok(mut engine) = NLP_ENGINE.write() {
            let word_str = w.to_str().unwrap_or("");
            engine.trie.insert(word_str, frequency.max(0) as u32);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpSuggest(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    limit: jint,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let query_str = match env.get_string(&query) {
        Ok(s) => match s.to_str() {
            Ok(valid) => valid.to_string(),
            Err(_) => return empty_array,
        },
        Err(_) => return empty_array,
    };

    let candidates = {
        if let Ok(engine) = NLP_ENGINE.read() {
            let res = engine.suggest(&query_str, limit.max(1) as usize);
            res.candidates
        } else {
            Vec::new()
        }
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    let result_array = match env.new_object_array(
        candidates.len() as jint,
        string_class,
        JString::default(),
    ) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    for (i, cand) in candidates.iter().enumerate() {
        if let Ok(jstr) = env.new_string(cand) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeSanitizeUrl(
    mut env: JNIEnv,
    _class: JClass,
    raw_url: JString,
) -> jstring {
    if let Ok(s) = env.get_string(&raw_url) {
        let clean = crake_privacy::sanitize_url(s.to_str().unwrap_or(""));
        if let Ok(out) = env.new_string(&clean) {
            return out.into_raw();
        }
    }
    raw_url.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeSanitizeText(
    mut env: JNIEnv,
    _class: JClass,
    raw_text: JString,
) -> jstring {
    if let Ok(s) = env.get_string(&raw_text) {
        let res = crake_privacy::metascrub_text(s.to_str().unwrap_or(""));
        if let Ok(out) = env.new_string(&res.cleaned_text) {
            return out.into_raw();
        }
    }
    raw_text.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeMetaScrubText(
    mut env: JNIEnv,
    _class: JClass,
    raw_text: JString,
) -> jstring {
    if let Ok(s) = env.get_string(&raw_text) {
        let res = crake_privacy::metascrub_text(s.to_str().unwrap_or(""));
        let payload = format!(
            "{}|{}|{}",
            res.invisible_chars_removed,
            if res.urls_sanitized { "1" } else { "0" },
            res.cleaned_text.replace('|', "_")
        );
        if let Ok(out) = env.new_string(&payload) {
            return out.into_raw();
        }
    }
    raw_text.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeInspectSecret(
    mut env: JNIEnv,
    _class: JClass,
    raw_text: JString,
) -> jstring {
    if let Ok(s) = env.get_string(&raw_text) {
        let res = crake_privacy::inspect_text(s.to_str().unwrap_or(""));
        let warning = res.warning_message.unwrap_or_default();
        let payload = format!(
            "{}|{}|{}",
            if res.is_secret_detected { "1" } else { "0" },
            warning.replace('|', "_"),
            res.redacted_text.replace('|', "_")
        );
        if let Ok(out) = env.new_string(&payload) {
            return out.into_raw();
        }
    }
    env.new_string("0||").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeScanThreats(
    mut env: JNIEnv,
    _class: JClass,
    raw_text: JString,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let text_str = match env.get_string(&raw_text) {
        Ok(s) => match s.to_str() {
            Ok(v) => v.to_string(),
            Err(_) => return empty_array,
        },
        Err(_) => return empty_array,
    };

    let matches = {
        if let Ok(scanner) = BOREAL_SCANNER.read() {
            scanner.scan_text(&text_str)
        } else {
            Vec::new()
        }
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    let result_array = match env.new_object_array(
        matches.len() as jint,
        string_class,
        JString::default(),
    ) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    for (i, m) in matches.iter().enumerate() {
        let serialized = format!("{}:{}:{}", m.rule_name, m.category, m.severity);
        if let Ok(jstr) = env.new_string(&serialized) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }

    result_array.into_raw()
}
