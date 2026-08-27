use floris_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};
use jni::objects::{JByteArray, JClass, JFloatArray, JIntArray, JObjectArray, JString};
use jni::sys::{jboolean, jfloat, jint, jobjectArray, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::sync::RwLock;

static NLP_ENGINE: Lazy<RwLock<NlpEngine>> = Lazy::new(|| RwLock::new(NlpEngine::new()));
static HIT_TESTER: Lazy<RwLock<floris_core::HitTester>> =
    Lazy::new(|| RwLock::new(floris_core::HitTester::new()));
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
            // learn_word records the entry in the persisted learned set as
            // well as the trie, so user vocabulary survives restarts.
            engine.learn_word(word_str, frequency.max(0) as u32);
        }
    }
}

/// Three-fragment split repair ("cha nbn ges" -> "changes"); empty string
/// when the fragments should not weld. See NlpEngine::merge_repair3.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpMergeRepair3(
    mut env: JNIEnv,
    _class: JClass,
    preceding: JString,
    first: JString,
    second: JString,
    third: JString,
) -> jstring {
    let empty = env
        .new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut());
    let get = |env: &mut JNIEnv, s: &JString| {
        env.get_string(s)
            .map(|v| v.to_str().unwrap_or("").to_string())
            .unwrap_or_default()
    };
    let ctx = get(&mut env, &preceding);
    let f1 = get(&mut env, &first);
    let f2 = get(&mut env, &second);
    let f3 = get(&mut env, &third);
    let merged = match NLP_ENGINE.read() {
        Ok(engine) => engine.merge_repair3(&ctx, &f1, &f2, &f3),
        Err(_) => None,
    };
    match merged {
        Some(word) => env.new_string(&word).map(|s| s.into_raw()).unwrap_or(empty),
        None => empty,
    }
}

/// Records a personal bigram: the user wrote `next` after `prev`. Feeds the
/// personal context layer consulted by every bigram consumer.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpRecordPersonalBigram(
    mut env: JNIEnv,
    _class: JClass,
    prev_word: JString,
    next_word: JString,
) {
    let prev = env
        .get_string(&prev_word)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();
    let next = env
        .get_string(&next_word)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();
    if let Ok(mut engine) = NLP_ENGINE.write() {
        engine.record_personal_bigram(&prev, &next);
    }
}

/// Serializes the learned state (user words + correction habits) for the
/// Kotlin side to write to app-private storage.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpExportLearned(
    env: JNIEnv,
    _class: JClass,
) -> jni::sys::jbyteArray {
    let data = match NLP_ENGINE.read() {
        Ok(engine) => engine.export_learned(),
        Err(_) => Vec::new(),
    };
    env.byte_array_from_slice(&data)
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Restores learned state from a previously exported blob. Returns the
/// number of learned words restored, or -1 for a corrupt blob (which
/// restores nothing).
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpImportLearned(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jint {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return -1,
    };
    match NLP_ENGINE.write() {
        Ok(mut engine) => match engine.import_learned(&bytes) {
            Ok(count) => count.min(jint::MAX as usize) as jint,
            Err(_) => -1,
        },
        Err(_) => -1,
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
            engine.corpus_insert(word, freq);
        }) {
            Ok(count) => count.min(jint::MAX as u32) as jint,
            Err(_) => -1,
        }
    } else {
        -1
    }
}

/// Uploads a keyboard layout's touch bounds as a flat [l,t,r,b]*n array for
/// shadow hit-testing. Returns the layout generation (>= 1), or -1 on error.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeHitSetKeys(
    mut env: JNIEnv,
    _class: JClass,
    rects: JFloatArray,
    chars: JString,
) -> jint {
    let len = match env.get_array_length(&rects) {
        Ok(l) if l >= 0 => l as usize,
        _ => return -1,
    };
    let mut buf = vec![0f32; len];
    if env.get_float_array_region(&rects, 0, &mut buf).is_err() {
        return -1;
    }
    let labels: Vec<char> = env
        .get_string(&chars)
        .map(|s| s.to_str().unwrap_or("").chars().collect())
        .unwrap_or_default();
    match HIT_TESTER.write() {
        Ok(mut tester) => match tester.set_keys(&buf, &labels) {
            Some(generation) => generation.min(jint::MAX as u32) as jint,
            None => -1,
        },
        Err(_) => -1,
    }
}

/// Shadow hit test: first key containing (x, y) in upload order, half-open
/// bounds — the FlorisRect contract. Returns the key index, -1 for no key
/// (Kotlin's null), or -2 when `generation` is not the current layout (a
/// different keyboard page was uploaded since; the comparison must be
/// skipped, not counted).
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeHitTest(
    _env: JNIEnv,
    _class: JClass,
    generation: jint,
    x: jfloat,
    y: jfloat,
) -> jint {
    let Ok(mut tester) = HIT_TESTER.write() else {
        return -2;
    };
    if generation < 0 || tester.generation() != generation as u32 {
        return -2;
    }
    match tester.hit(x, y) {
        Some(index) => {
            // In-bounds hits feed per-key offset learning (EMA, clamped).
            tester.record_hit(index, x, y);
            index.min(jint::MAX as usize) as jint
        }
        None => -1,
    }
}

/// Learned per-key touch offsets as a CRKT blob, for persistence.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeHitExportOffsets(
    env: JNIEnv,
    _class: JClass,
) -> jni::sys::jbyteArray {
    let data = match HIT_TESTER.read() {
        Ok(tester) => tester.export_offsets(),
        Err(_) => Vec::new(),
    };
    env.byte_array_from_slice(&data)
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Restores per-key touch offsets; returns entries restored or -1.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeHitImportOffsets(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jint {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return -1,
    };
    match HIT_TESTER.write() {
        Ok(mut tester) => match tester.import_offsets(&bytes) {
            Ok(count) => count.min(jint::MAX as usize) as jint,
            Err(()) => -1,
        },
        Err(_) => -1,
    }
}

/// Loads the CRKB bigram language model for context re-ranking. Returns the
/// pair count, or -1 on any parse error (in which case a previously loaded
/// table, if any, stays in effect).
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpLoadBigramBlob(
    env: JNIEnv,
    _class: JClass,
    data: JByteArray,
) -> jint {
    let bytes = match env.convert_byte_array(&data) {
        Ok(b) => b,
        Err(_) => return -1,
    };
    match NLP_ENGINE.write() {
        Ok(mut engine) => match engine.load_bigrams(&bytes) {
            Ok(count) => count.min(jint::MAX as usize) as jint,
            Err(_) => -1,
        },
        Err(_) => -1,
    }
}

/// Context-aware suggest: identical to nativeNlpSuggest plus the previous
/// word, which feeds homophone disambiguation and the bigram re-ranker.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpSuggestCtx(
    mut env: JNIEnv,
    _class: JClass,
    query: JString,
    prev_word: JString,
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
    let prev_str = env
        .get_string(&prev_word)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();

    let candidates = {
        if let Ok(engine) = NLP_ENGINE.read() {
            engine
                .suggest_with_context(&query_str, &prev_str, limit.max(1) as usize)
                .candidates
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

/// Two-token spurious-space repair ("shou kd" -> "should"). Returns the
/// merged dictionary word, or an empty string when the fragments should not
/// merge (legitimate pairs never do — see NlpEngine::merge_repair).
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpMergeRepair(
    mut env: JNIEnv,
    _class: JClass,
    preceding: JString,
    prev_word: JString,
    current: JString,
) -> jstring {
    let empty = env
        .new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut());
    let ctx = env
        .get_string(&preceding)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();
    let prev = match env.get_string(&prev_word) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return empty,
    };
    let cur = match env.get_string(&current) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return empty,
    };
    let merged = match NLP_ENGINE.read() {
        Ok(engine) => engine.merge_repair_with_context(&ctx, &prev, &cur),
        Err(_) => None,
    };
    match merged {
        Some(word) => env.new_string(&word).map(|s| s.into_raw()).unwrap_or(empty),
        None => empty,
    }
}

/// The static corpus as loaded from the CRKD blob, in blob order. Serves the
/// glide classifier's word list now that the JVM no longer keeps its own copy
/// of the dictionary.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpCorpusWords(
    mut env: JNIEnv,
    _class: JClass,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let Ok(engine) = NLP_ENGINE.read() else {
        return empty_array;
    };
    let words = engine.corpus_words();
    let Ok(array) = env.new_object_array(words.len() as i32, "java/lang/String", JString::default()) else {
        return empty_array;
    };
    for (i, word) in words.iter().enumerate() {
        let Ok(jword) = env.new_string(word) else {
            return empty_array;
        };
        if env.set_object_array_element(&array, i as i32, jword).is_err() {
            return empty_array;
        }
    }
    array.into_raw()
}

/// Frequency of a corpus word, 0 when absent — the JVM map's lookup contract.
#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpCorpusFreq(
    mut env: JNIEnv,
    _class: JClass,
    word: JString,
) -> jint {
    let Ok(w) = env.get_string(&word) else {
        return 0;
    };
    let word_str = w.to_str().unwrap_or("");
    match NLP_ENGINE.read() {
        Ok(engine) => engine.corpus_freq(word_str).min(jint::MAX as u32) as jint,
        Err(_) => 0,
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
    prev_word: JString,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let query_str = env.get_string(&query).map(|s| s.to_str().unwrap_or("").to_string()).unwrap_or_default();
    let prev_word_str = env.get_string(&prev_word).map(|s| s.to_str().unwrap_or("").to_string()).unwrap_or_default();

    let predictions = {
        if let Ok(engine) = NLP_ENGINE.read() {
            engine.predict_next_letter_words(&query_str, &prev_word_str)
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

    // The same geometry feeds the Gaussian touch model, so autocorrect slip
    // costs always describe the layout the user is actually typing on
    // (Dvorak gets Dvorak neighbours, not a hardcoded union table). Learned
    // per-key touch offsets shift each centre to where THIS user actually
    // taps. Locks here are taken strictly SEQUENTIALLY (each guard dropped
    // before the next is acquired) — no nesting, no ordering hazard.
    let offsets: std::collections::HashMap<char, (f32, f32)> = match HIT_TESTER.read() {
        Ok(tester) => keys
            .iter()
            .map(|k| (k.character, tester.offset_for(k.character)))
            .collect(),
        Err(_) => Default::default(),
    };
    let model_keys: Vec<(char, f32, f32)> = keys
        .iter()
        .map(|k| {
            let (dx, dy) = offsets.get(&k.character).copied().unwrap_or((0.0, 0.0));
            (k.character, k.center.x + dx, k.center.y + dy)
        })
        .collect();
    if let Ok(mut engine) = NLP_ENGINE.write() {
        engine.set_touch_model(floris_core::TouchModel::from_layout(&model_keys));
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
    prev_word: JString,
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

    let prev = env
        .get_string(&prev_word)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();

    let matches = {
        let glide_guard = GLIDE_ENGINE.read();
        let nlp_guard = NLP_ENGINE.read();

        if let (Ok(glide), Ok(nlp)) = (glide_guard, nlp_guard) {
            let context = if prev.is_empty() { None } else { Some((&*nlp, prev.as_str())) };
            glide.match_gesture_with_context(&path, &nlp.trie, max_results.max(1) as usize, context)
        } else {
            Vec::new()
        }
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    // Stray-flick guard: when NOTHING in the result set is a solid word,
    // the whole set is display-only. A leading empty string is the
    // documented sentinel — the Kotlin side shows the words but commits
    // nothing (device traces 2026-08-27: short flicks committed junk like
    // "oui"/"upi" because every candidate was junk).
    let commit_safe = matches
        .iter()
        .any(|m| m.frequency >= floris_core::glide::GLIDE_COMMIT_MIN_FREQ);
    let offset: usize = if commit_safe { 0 } else { 1 };

    let result_array = match env.new_object_array(
        (matches.len() + offset) as jint,
        string_class,
        JString::default(),
    ) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    if !commit_safe {
        if let Ok(jstr) = env.new_string("") {
            let _ = env.set_object_array_element(&result_array, 0, jstr);
        }
    }
    for (i, m) in matches.iter().enumerate() {
        if let Ok(jstr) = env.new_string(&m.word) {
            let _ = env.set_object_array_element(&result_array, (i + offset) as jint, jstr);
        }
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpPredictNextWords(
    mut env: JNIEnv,
    _class: JClass,
    prev_word: JString,
    max_results: jint,
    include_personal: jboolean,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let prev = env
        .get_string(&prev_word)
        .map(|s| s.to_str().unwrap_or("").to_string())
        .unwrap_or_default();

    let words = {
        if let Ok(engine) = NLP_ENGINE.read() {
            engine.predict_next_words_filtered(
                &prev,
                max_results.max(0) as usize,
                include_personal != 0,
            )
        } else {
            Vec::new()
        }
    };

    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };
    let result_array = match env.new_object_array(words.len() as jint, string_class, JString::default()) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };
    for (i, w) in words.iter().enumerate() {
        if let Ok(jstr) = env.new_string(w) {
            let _ = env.set_object_array_element(&result_array, i as jint, jstr);
        }
    }
    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeNlpRecordPersonalCorrection(
    mut env: JNIEnv,
    _class: JClass,
    typo: JString,
    intended: JString,
) {
    if let (Ok(t), Ok(i)) = (env.get_string(&typo), env.get_string(&intended)) {
        if let Ok(mut engine) = NLP_ENGINE.write() {
            let typo_str = t.to_str().unwrap_or("");
            let intended_str = i.to_str().unwrap_or("");
            engine.record_personal_correction(typo_str, intended_str);
        }
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativePgponyGenerateKeypair(
    mut env: JNIEnv,
    _class: JClass,
) -> jobjectArray {
    let empty_array = env
        .new_object_array(0, "java/lang/String", JString::default())
        .map(|arr| arr.into_raw())
        .unwrap_or(std::ptr::null_mut());

    let keypair = crake_privacy::generate_keypair();
    let string_class = match env.find_class("java/lang/String") {
        Ok(cls) => cls,
        Err(_) => return empty_array,
    };

    let result_array = match env.new_object_array(2, string_class, JString::default()) {
        Ok(arr) => arr,
        Err(_) => return empty_array,
    };

    if let Ok(j_priv) = env.new_string(&keypair.private_key_hex) {
        let _ = env.set_object_array_element(&result_array, 0, j_priv);
    }
    if let Ok(j_pub) = env.new_string(&keypair.public_key_bech) {
        let _ = env.set_object_array_element(&result_array, 1, j_pub);
    }

    result_array.into_raw()
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativePgponyEncrypt(
    mut env: JNIEnv,
    _class: JClass,
    plaintext: JString,
    recipient_pubkey: JString,
) -> jstring {
    let plain_str = match env.get_string(&plaintext) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return std::ptr::null_mut(),
    };
    let pub_str = match env.get_string(&recipient_pubkey) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return std::ptr::null_mut(),
    };

    match crake_privacy::pgpony_encrypt(&plain_str, &pub_str) {
        Ok(armored) => env.new_string(&armored).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativePgponyDecrypt(
    mut env: JNIEnv,
    _class: JClass,
    armored_text: JString,
    private_key_hex: JString,
) -> jstring {
    let arm_str = match env.get_string(&armored_text) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return std::ptr::null_mut(),
    };
    let priv_str = match env.get_string(&private_key_hex) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return std::ptr::null_mut(),
    };

    match crake_privacy::pgpony_decrypt(&arm_str, &priv_str) {
        Ok(decrypted) => env.new_string(&decrypted).map(|s| s.into_raw()).unwrap_or(std::ptr::null_mut()),
        Err(_) => std::ptr::null_mut(),
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativePgponyIsArmored(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
) -> jboolean {
    let str_val = match env.get_string(&text) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return 0,
    };
    if crake_privacy::is_pgpony_message(&str_val) {
        1
    } else {
        0
    }
}

#[no_mangle]
pub extern "system" fn Java_org_florisboard_libnative_FlorisNative_nativeToBritishSpelling(
    mut env: JNIEnv,
    _class: JClass,
    word: JString,
) -> jstring {
    let empty = env
        .new_string("")
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut());
    let w = match env.get_string(&word) {
        Ok(s) => s.to_str().unwrap_or("").to_string(),
        Err(_) => return empty,
    };
    match floris_core::british_spelling::to_british_spelling(&w.to_lowercase()) {
        Some(br) => env.new_string(br).map(|s| s.into_raw()).unwrap_or(empty),
        None => empty,
    }
}
