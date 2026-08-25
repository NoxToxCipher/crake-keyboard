use floris_core::{GlideEngine, KeyInfo, NlpEngine, Point2D};
use jni::objects::{JClass, JFloatArray, JIntArray, JString};
use jni::sys::{jint, jobjectArray};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::sync::RwLock;

static NLP_ENGINE: Lazy<RwLock<NlpEngine>> = Lazy::new(|| RwLock::new(NlpEngine::new()));
static GLIDE_ENGINE: Lazy<RwLock<GlideEngine>> = Lazy::new(|| RwLock::new(GlideEngine::new()));

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
