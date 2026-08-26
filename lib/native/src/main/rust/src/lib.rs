use floris_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};
use jni::objects::{JByteArray, JClass, JFloatArray, JIntArray, JObjectArray, JString};
use jni::sys::{jfloatArray, jint, jintArray, jobjectArray, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::sync::RwLock;

static NLP_ENGINE: Lazy<RwLock<NlpEngine>> = Lazy::new(|| RwLock::new(NlpEngine::new()));
static GLIDE_ENGINE: Lazy<RwLock<GlideEngine>> = Lazy::new(|| RwLock::new(GlideEngine::new()));
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

/// Bulk dictionary load from the CRKD binary asset: one JNI crossing instead
/// of one per word. Skips the per-word secret inspection deliberately — the
/// blob is the static corpus shipped in our APK, and single words cannot trip
/// the shield's phrase/prefix checks anyway. Returns the entry count, or -1
/// so the Kotlin side falls back to the JSON path.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpLoadDictBlob(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jint {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return -1,
    };
    if let Ok(mut engine) = NLP_ENGINE.write() {
        match floris_core::parse_dict_blob(&bytes, |word, freq| {
            engine.trie.insert(word, freq);
        }) {
            Ok(count) => count.min(jint::MAX as u32) as jint,
            Err(_) => -1,
        }
    } else {
        -1
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
        let serialized = format!("{}:{}", cand.word, if cand.is_autocorrect { 1 } else { 0 });
        if let Ok(jstr) = env.new_string(&serialized) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpPredictNextLetterWords(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
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

    let predictions = {
        if let Ok(engine) = NLP_ENGINE.read() {
            engine.predict_next_letter_words(&query_str)
        } else {
            Vec::new()
        }
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    let result_array = match env.new_object_array(
        predictions.len() as jint,
        string_class,
        JString::default(),
    ) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    for (i, (ch, word)) in predictions.iter().enumerate() {
        let serialized = format!("{}:{}", ch, word);
        if let Ok(jstr) = env.new_string(&serialized) {
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

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeGenerateQrMatrix(
    mut env: JNIEnv,
    _class: JClass,
    data: JString,
) -> jstring {
    if let Ok(s) = env.get_string(&data) {
        if let Some(matrix) = crake_privacy::generate_qr_matrix(s.to_str().unwrap_or("")) {
            if let Ok(out) = env.new_string(&matrix) {
                return out.into_raw();
            }
        }
    }
    env.new_string("").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeCreateSyncBundle(
    mut env: JNIEnv,
    _class: JClass,
    key_hex: JString,
    raw_data: JString,
    chunk_size: jint,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let key_bytes: [u8; 32] = match env.get_string(&key_hex) {
        Ok(s) => {
            let mut k = [0u8; 32];
            let str_val = s.to_str().unwrap_or("");
            if str_val.len() == 64 {
                for i in 0..32 {
                    if let Ok(b) = u8::from_str_radix(&str_val[i * 2..i * 2 + 2], 16) {
                        k[i] = b;
                    }
                }
            }
            k
        }
        Err(_) => return empty_array,
    };

    let data_bytes = match env.get_string(&raw_data) {
        Ok(s) => s.to_str().unwrap_or("").as_bytes().to_vec(),
        Err(_) => return empty_array,
    };

    let encrypted_bundle = crake_privacy::create_encrypted_sync_bundle(&key_bytes, &data_bytes);
    let frames = crake_privacy::chunk_for_optical_qr(&encrypted_bundle, chunk_size.max(32) as usize);

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    let result_array = match env.new_object_array(
        frames.len() as jint,
        string_class,
        JString::default(),
    ) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    for (i, f) in frames.iter().enumerate() {
        let frame_str = f.to_string_repr();
        if let Ok(jstr) = env.new_string(&frame_str) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeReassembleSyncBundle(
    mut env: JNIEnv,
    _class: JClass,
    key_hex: JString,
    frames_array: JObjectArray,
) -> jstring {
    let key_bytes: [u8; 32] = match env.get_string(&key_hex) {
        Ok(s) => {
            let mut k = [0u8; 32];
            let str_val = s.to_str().unwrap_or("");
            if str_val.len() == 64 {
                for i in 0..32 {
                    if let Ok(b) = u8::from_str_radix(&str_val[i * 2..i * 2 + 2], 16) {
                        k[i] = b;
                    }
                }
            }
            k
        }
        Err(_) => return env.new_string("").unwrap().into_raw(),
    };

    let count = env.get_array_length(&frames_array).unwrap_or(0);
    let mut frames = Vec::with_capacity(count as usize);

    for i in 0..count {
        if let Ok(elem) = env.get_object_array_element(&frames_array, i) {
            let jstr: JString = elem.into();
            let parsed = if let Ok(s) = env.get_string(&jstr) {
                crake_privacy::QrFrame::parse_string_repr(s.to_str().unwrap_or(""))
            } else {
                None
            };
            if let Some(f) = parsed {
                frames.push(f);
            }
        }
    }

    if let Some(bundle) = crake_privacy::reassemble_qr_frames(&frames) {
        if let Some(decrypted) = crake_privacy::open_encrypted_sync_bundle(&key_bytes, &bundle) {
            if let Ok(text) = String::from_utf8(decrypted) {
                if let Ok(out) = env.new_string(&text) {
                    return out.into_raw();
                }
            }
        }
    }

    env.new_string("").map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeGlideSetLayout(
    mut env: JNIEnv,
    _class: JClass,
    codes: JIntArray,
    chars: JString,
    xs: JFloatArray,
    ys: JFloatArray,
    widths: JFloatArray,
    heights: JFloatArray,
) {
    let chars_str = match env.get_string(&chars) {
        Ok(s) => match s.to_str() {
            Ok(v) => v.to_string(),
            Err(_) => return,
        },
        Err(_) => return,
    };

    let len = chars_str.chars().count();
    let mut code_buf = vec![0i32; len];
    let mut x_buf = vec![0.0f32; len];
    let mut y_buf = vec![0.0f32; len];
    let mut w_buf = vec![0.0f32; len];
    let mut h_buf = vec![0.0f32; len];

    if env.get_int_array_region(&codes, 0, &mut code_buf).is_err()
        || env.get_float_array_region(&xs, 0, &mut x_buf).is_err()
        || env.get_float_array_region(&ys, 0, &mut y_buf).is_err()
        || env.get_float_array_region(&widths, 0, &mut w_buf).is_err()
        || env.get_float_array_region(&heights, 0, &mut h_buf).is_err()
    {
        return;
    }

    let mut keys = Vec::with_capacity(len);
    for (i, ch) in chars_str.chars().enumerate() {
        keys.push(KeyInfo {
            code: code_buf[i],
            character: ch,
            center: Point2D::new(x_buf[i], y_buf[i]),
            width: w_buf[i],
            height: h_buf[i],
        });
    }

    if let Ok(mut engine) = GLIDE_ENGINE.write() {
        engine.set_layout(keys);
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeGlideMatch(
    mut env: JNIEnv,
    _class: JClass,
    xs: JFloatArray,
    ys: JFloatArray,
    max_results: jint,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let len = match env.get_array_length(&xs) {
        Ok(l) => l as usize,
        Err(_) => return empty_array,
    };

    if len < 2 {
        return empty_array;
    }

    let mut x_buf = vec![0.0f32; len];
    let mut y_buf = vec![0.0f32; len];

    if env.get_float_array_region(&xs, 0, &mut x_buf).is_err()
        || env.get_float_array_region(&ys, 0, &mut y_buf).is_err()
    {
        return empty_array;
    }

    let mut path = Vec::with_capacity(len);
    for i in 0..len {
        path.push(Point2D::new(x_buf[i], y_buf[i]));
    }

    let matches = {
        let glide_guard = GLIDE_ENGINE.read();
        let nlp_guard = NLP_ENGINE.read();

        if let (Ok(glide), Ok(nlp)) = (glide_guard, nlp_guard) {
            glide.match_gesture(&path, &nlp.trie, max_results.max(1) as usize)
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
        if let Ok(jstr) = env.new_string(&m.word) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }

    result_array.into_raw()
}
