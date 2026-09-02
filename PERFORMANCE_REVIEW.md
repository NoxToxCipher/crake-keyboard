# Crake Keyboard — App-Wide Performance Review

Date: 2026-09-02. Scope: full app (Kotlin IME + settings, Rust `floris-core`, startup, assets).
Method: four parallel code-path audits (input hot path, NLP/editor pipeline, theme/clipboard/media,
startup/prefs/native), every headline finding re-verified against source, then **measured** where the
environment allows: the Rust findings against the real shipped dictionary via
`libnative/crates/floris-core/tests/perf_review_bench.rs` (added by this review, `#[ignore]`d so it
only runs on demand), and the Kotlin patterns via 1:1 JVM ports. Desktop x86 numbers; a mid-range
phone core is typically 2–4× slower.

Every `file:line` below was checked against the working tree at `896d9be`.

---

## Measured proof (summary)

Native, real shipped 49,470-word dictionary, release build
(`cargo test -p floris-core --release --test perf_review_bench -- --ignored --nocapture`):

| Path | Measured | Frequency |
|---|---|---|
| `trie.prefix_search("s", 12)` | **207 µs** | every keystroke while a 1-char word is composing |
| `trie.prefix_search("s", 300)` | **412 µs** | per glide start-char candidate pull |
| `suggest_with_context("s")` | **243 µs** | per keystroke |
| `suggest_with_context("thoriufhky")` (typo) | **1,176 µs** | per keystroke on a misspelled word |
| `suggest_with_context("helllo")` | **986 µs** | per keystroke on a misspelled word |
| `predict_next_letter_words("s")` | **281 µs** | per word-state change (flick warmer) |
| `match_gesture_timed("something", 200 pts)` | **1,367 µs** | ×4–5 per glide gesture (preview every 150 ms + final) |
| `detect_double_letter_loops(200 pts)` | 20 µs | per glide match call |
| UI-thread write-lock stall behind suggest reads (`recordTouchHit` topology) | **avg 376 µs, worst 1,651 µs** (starves outright under continuous reads) | per letter key-down |

Kotlin patterns, ported 1:1 to JVM (`PerfProof.java`, `EasterEggProof.java`, `ThemeEmojiProof.java`):

| Pattern | Current | Equivalent fix | Ratio |
|---|---|---|---|
| `getTextBeforeCursor(1)`: ICU BreakIterator over 256-char buffer to read last char | 4,228 ns | 26 ns (tail scan) | **163×** |
| Easter-egg trigger scan (34 groups, exact port) | 22,745 ns + **1,746 string concats** | 209 ns (precomputed set) | **109×** |
| `Regex(...)` built inline in `evaluateMathOrMacro` | 1,533 ns | 67 ns (precompiled) | 23× |
| `SentenceEndMatcher.matches()` on full 256-char window | 2,303 ns | 139 ns (takeLast(4)) | 17× |
| `UUID.randomUUID().toString()` per Snygg style query | 588 ns (≈59 µs per 100-query restyle) | 0 (dead on IME path) | — |
| `Emoji` init boxed `codePoints().toList()` ×1500 emoji | 0.31 ms | 0.03 ms | 10× |

All of the above are on paths proven hot by call-chain (documented per finding).

---

## Tier 1 — Per-keystroke cost on the main/UI thread

These run once per typed character (some per touch event) on the thread that must hit the frame deadline.

### 1.1 Easter-egg trigger scan: ~1,750 string allocations per keystroke
`app/src/main/kotlin/dev/patrickgold/florisboard/ime/text/keyboard/TextKeyboardLayout.kt:522-841`

`LaunchedEffect(activeContent)` re-runs on every committed character on the Main dispatcher. Inside:
34 matcher groups over ~160 trigger strings; the flat groups build up to 9 interpolated variants per
key (`tb.endsWith("$it ")`, `"$it."`, `"$it!"`, …) and ~15 nested groups multiply each key by 6
delimiters with 3 concatenations each. Exact count from the shipped lists: **1,746 string
concatenations per keystroke** in the common no-match case. Measured 22.7 µs on desktop JVM
(≈100–200 µs on a phone) plus young-gen GC pressure, every character.
The `takeLast(64)` bounding (the comment at :524 calls it "O(1)") capped comparison length but not
allocation count.
**Fix:** precompute one `Set` of key+delimiter combinations at class init; extract the trailing
token(s) of `tb` once; hash-lookup. Same semantics, ~100× cheaper (measured).

### 1.2 The editor pipeline runs 5 ICU BreakIterator passes + ~10 `runBlocking` blocks per character
`ime/editor/AbstractEditorInstance.kt:536-543` (`getTextBeforeCursor`), `:90-102` (`activeContent`),
`:338-346` (`commitChar`), `ime/editor/EditorInstance.kt:182-234`, `ime/nlp/BreakIteratorGroup.kt:70-85`

One `commitChar` on the UI thread chains: `textBeforeSelection` (runBlocking + 256-char substring) →
`shouldInsertAutoSpaceBefore` → `getTextBeforeCursor(1)` (runBlocking + **ICU `setText` over the full
256-char window to read 1 char**) → `shouldInsertAutoSpaceAfter` → ICU pass 2 → `phantomSpace.determine`
→ ICU pass 3 → `measureUChars(char)` → ICU pass 4 → `generateCopy`/`determineLocalComposing` → ICU
*word*-iterator pass 5 (`NlpProviders.kt:204-206`). Measured: each "read last 1–3 chars via ICU" pass
is 4.2 µs vs 26 ns for a codepoint tail scan (163×). On top: `activeContent`'s getter is
`runBlocking { expectedContentQueue.peekNewestOrNull() }` behind a coroutine `Mutex` — and there are
**98 call sites**, 32 of them in `TextKeyboardLayout`'s touch handlers. `EditorContent`'s
`textBeforeSelection`/`currentWordText`/`composingText` are computed getters that allocate a fresh
string per access (`EditorContent.kt:43-92`); several paths read them 2–3× per keystroke.
**Fix:** BMP fast path for `getTextBeforeCursor(1..3)` (ICU only on surrogate/combining boundary);
`@Volatile` newest-content field instead of runBlocking+Mutex; cache the substrings on the immutable
`EditorContent`.

### 1.3 Every letter key-down takes the global native NLP write lock on the UI thread
`lib/native/src/main/rust/src/lib.rs:255-282`, called from `TextKeyboardLayout.kt:9166-9174`

`nativeRecordTouchHit` acquires `NLP_ENGINE.write()` synchronously in the touch-down handler.
Meanwhile `nativeNlpSuggestCtx` (per keystroke) and `nativeGlideMatch` (per glide preview) hold
`NLP_ENGINE.read()` for their full duration — measured 0.24–1.4 ms per call on desktop
(plausibly 1–5 ms on device). **Measured contention** (`perf_review_lock_stall`, same lock topology,
real engine + dictionary): a key-down's write acquisition stalls **avg 376 µs, worst 1,651 µs**
behind in-flight suggest reads; with back-to-back reads (no gap) the writer **starved outright for
4+ minutes** — Rust's `RwLock` guarantees no fairness, and suggest + glide preview + the flick
warmer all take reads on this one lock. `nativeGlideMatch` additionally holds **two** global read
locks (`GLIDE_ENGINE` + `NLP_ENGINE`) across the whole match. Nothing on the touch path consumes
`recordTouchHit`'s result — it doesn't need to be synchronous at all.
**Fix:** own lock (or MPSC queue) for the touch model; post the record from a background executor.

### 1.4 Adaptive hit-test: ~30 JNI round-trips + O(rows·keys) scan per tap (default-on)
`ime/text/keyboard/TextKeyboard.kt:133-175`, pref default `AppPrefs.kt:765-768`

`getKeyForPosAdaptive` loops all ~30 character keys per ACTION_DOWN and calls
`FlorisNative.getTouchOffset(char)` for each — one JNI crossing + native `FloatArray` + `Pair` boxing
per key (`FlorisNative.kt:103-107`) — plus `arrangement.firstOrNull()?.contains(key)` (a linear
top-row scan) *inside* the loop. ≈30 JNI crossings, ≈60 allocations, ≈330 identity comparisons per
tap, on the input thread, before the key resolves.
**Fix:** bulk-fetch offsets once per layout into a char-indexed `FloatArray` (a bulk getter already
exists); precompute top-row membership on `TextKey` at layout time.

### 1.5 System user dictionary: binder IPC + cross-process SQLite query per keystroke (default-on)
`ime/dictionary/DictionaryManager.kt:82-93`, `ime/dictionary/UserDictionary.kt:294-306`,
default `AppPrefs.kt:297-300`

`LatinLanguageProvider.suggest()` → `queryUserDictionary` → `SystemUserDictionaryDatabase` →
`ContentResolver.query(UserDictionary.Words.CONTENT_URI, …)` — a cross-process round-trip per
keystroke, unbounded (blocks on the system provider), behind a `@Synchronized` dao getter that
contends with cache warming. The Floris-owned dictionary was correctly moved to an in-memory cache;
the system one was left on the raw IPC path, and `enableSystemUserDictionary` defaults to `true`.
**Fix:** cache system shortcuts like `UserDictionaryCache` does, refresh via `ContentObserver`.

### 1.6 Telemetry argument evaluation runs even with the flight recorder disabled
`TextKeyboardLayout.kt:9137-9164` (per key-down), `KeyboardManager.kt:538-544` (per backspace,
auto-repeating), `KeyboardManager.kt:308-343` (per candidate commit)

`FlightRecorderManager.isLoggingAllowed()` is checked inside the callee, so all arguments are built
first: `activeContent.textBeforeSelection` (runBlocking + 256-char substring — **twice** on the
backspace site), `computedData.asString` (StringBuilder + ICU), `displayMetrics.density` lookup, a
candidate-list `map{toString()}`. The recorder is **off by default** (`AppPrefs.kt:232`) — in the
default config this is pure waste on every key event. `handleSpace` (`KeyboardManager.kt:673`)
already shows the correct guarded pattern, with a comment explaining why.
**Fix:** apply the `handleSpace` guard to the other call sites (cached `@Volatile` enabled flag).

### 1.7 Battery easter-egg widget: coroutine relaunch + 2 full-buffer `lowercase()` per keystroke
`ime/smartbar/Smartbar.kt:413-432` (composed unconditionally via `StickyAction` at :314)

`LaunchedEffect(activeContent)` fires per character: cancels/relaunches a coroutine, lowercases the
entire ≤256-char `textBeforeSelection` **and** `composingText`, allocates the 6-element key list and
up to 6 `"$it "` templates. Also `Smartbar.kt:510-580`: when triggered, the canvas allocates ~7
`Paint`s + a `Path` per frame at 60 fps for ~5 s (~2,400 allocations per trigger) — the pattern
Milestone 392 fixed elsewhere.
**Fix:** match against `takeLast(~12)`, hoist the lists, key the effect on `composingText`; hoist
the `Paint`s/`Path` out of the draw lambda.

### 1.8 Assorted per-keystroke overhead in the suggestion orchestration (all verified)
- `NlpManager.kt:52-114` `evaluateMathOrMacro`: **`Regex` compiled per keystroke** (measured 1.5 µs
  vs 67 ns precompiled) + `ZonedDateTime.now()` before the branch that needs it; second inline regex
  in `evalSimpleMath`. Off-main (Dispatchers.Default) but directly in suggestion latency.
- `KeyboardManager.kt:224-232`: `SentenceEndMatcher` (`.*[.?!]\s+$`) run with `matches()` over the
  full 256-char window per selection update — greedy backtracking over every sentence ender
  (measured 2.3 µs vs 139 ns on `takeLast(4)`); redundant with the `endsWith` checks above it.
- `NlpManager.kt:411-430`: **two preference writes per keystroke** —
  `sharedActionsExpandWithAnimation.set(false)` unconditional, no equality guard → datastore marked
  dirty and persist scheduled per character; plus another `activeContent` runBlocking read.
- `LatinLanguageProvider.kt:72-85`: `ensureLoaded()` pays a `withContext(Dispatchers.IO)` hop + Mutex
  per suggest call after load; early-return on a `@Volatile` flag removes it.
- `LatinLanguageProvider.kt:331`: O(n²) candidate dedup with `toString()` per comparison (small n,
  minor).
- `TextKeyboardLayout.kt:287-293`: `remember{}.also{}` re-runs `glideTypingManager.setLayout` per
  recomposition — 8 allocations + six `contentEquals` scans to conclude "unchanged"; the correctly
  keyed duplicate already exists at :407-410.
- `TextKeyboardLayout.kt:1073-1076`: per-keystroke loop over all keys recomputing labels
  (`asString` + ICU category + `lowercase()`) that are already stored on `TextKey.label`, just to
  probe `flickPredictions`.
- `InputEventDispatcher.kt:100-192`: `sendDown`/`sendUp`/`isPressed` each wrap a `runBlocking` +
  Mutex — 4–6 blocking event loops per keystroke to guard a collection only touched from the UI
  thread + one coroutine.

---

## Tier 2 — Suggestion & glide latency (native)

### 2.1 Trie prefix search walks the entire subtree — no frequency pruning
`libnative/crates/floris-core/src/trie.rs:238-260`

`collect_top` maintains a bounded top-k (a real prior fix) but still recurses into **every child
unconditionally** (:257). On the shipped 49,470-word dictionary: **measured 207 µs for prefix "s"
(limit 12) and 412 µs at glide's limit 300** — per keystroke / per glide pull. The in-code comment
(:169-174) says the 2026-08-27 fix addressed "474 µs for prefix 's'"; roughly half that cost remains
because the walk itself was never pruned. `predict_next_letter_words` (`nlp.rs:2629-2639`) multiplies
this: up to 26 subtree walks per call (measured 281 µs for "s").
**Fix:** store `max_subtree_frequency` per node (maintained on insert) and prune children that can't
beat the current worst top-k entry — typically >95 % of the subtree for frequency-ordered results.
Path compression (the `RadixTrie` name notwithstanding, children are `BTreeMap<char, TrieNode>` with
no compression — ~430k nodes) is the follow-on win.

### 2.2 Typo keystrokes cost ~1 ms in the engine; the fast paths allocate ~40 objects each
`nlp.rs:1839-1903`, `:2213-2222` — measured `suggest_with_context("thoriufhky")` = **1,176 µs**,
`("helllo")` = **986 µs** per keystroke (desktop; device 2–4× worse — a visible suggestion lag while
typing misspelled words). The transposition/double-letter scan clones the `Vec<char>` and builds a
fresh `String` **per position** (~20 each for a 10-char word); the rejection-memory lookup builds a
`(String, String)` tuple key per candidate. Three linear scans of static tables
(`TECH_BRAND_CASING`, `COMPOUND_HYPHEN_PHRASES`, `CONTRACTIONS`) run per keystroke.
**Fix:** one reusable scratch buffer for the swap probes; nested-map shape for
`rejected_corrections` (as `personal_corrections` already does); `binary_search` the sorted tables.

### 2.3 Glide scoring: ~10 heap allocations × 300–900 candidates × 4–5 calls per gesture
`glide.rs:735-830`, `nlp.rs:887-924` — measured **1,367 µs per `match_gesture_timed` call** on a
9-letter word (preview fires every 150 ms during the stroke + final on lift → 4–5 calls/gesture).
Per candidate: word clone, ideal-keypath `Vec`, a *second* path `Vec` (`soften_corners` takes by
value then clones, :564-578), two `Vec<char>`s, and — in a function whose comments claim
"Zero-allocation" (`nlp.rs:899-907`) — `to_ascii_lowercase()` on candidate and context, then
`bigram_pair_score` **re-lowercases both** (:887-888). The context half is identical for every
candidate yet recomputed 300–900×. Final `sort_by` over all matches when only 5–8 survive.
`detect_double_letter_loops` (:231-237) recomputes segment sums per window — O(11²·n) sqrt calls
(measured 20 µs; ×4–5 per gesture).
**Fix:** lowercase the context once per call; add a pre-lowered `bigram_pair_score` variant; in-place
`soften_corners`; scratch buffers; bounded heap instead of full sort; incremental path-length
accumulation.

### 2.4 JNI boundary waste per call
`lib/native/src/main/rust/src/lib.rs:369-372` (and the same pattern at :461, :514, :1091, :1188):
every suggestion call eagerly builds a zero-length `String[]` (a `FindClass` by name + array alloc,
discarded on the success path) and then does a **second** `find_class("java/lang/String")`. ~2–20 µs
+ garbage per keystroke.
**Fix:** cache the class as a `GlobalRef` in a `OnceCell`; build the empty array only on error paths.

### 2.5 Learning events: Android Keystore IPC ×2 + file writes on `Dispatchers.Default`, and a full LRU wipe
`LatinLanguageProvider.kt:411-424`, `LearnedStateStore.kt:89-111`

Every accepted suggestion (and every backspace-revert): `KeyStore.getInstance().load(null)` +
`getKey` (binder to the keystore daemon, key never cached) + AES-GCM seal + synchronous
`writeBytes` — twice (learned state + touch offsets) — on the CPU-bound Default pool. Plus
`nativeSuggestCache.evictAll()` throws away all 128 LRU entries per learn event, so the next 128
suggest calls take the full JNI path.
**Fix:** cache the `SecretKey`; move saves to `Dispatchers.IO`; debounce; invalidate selectively.

---

## Tier 3 — Keyboard-open & panel latency

### 3.1 Emoji panel: asset parse on the main thread, and the prewarm warms the wrong file
`ime/media/MediaInputLayout.kt:74-77`, `ime/media/emoji/EmojiData.kt:58-132`, `FlorisApplication.kt:167`

`EmojiData.get(context, "ime/media/emoji/root.txt")` runs in a `LaunchedEffect` on the Main
dispatcher; `loadEmojiDataMap` reads and parses the asset synchronously — **the parse runs on the
main thread at panel open**. The startup prewarm loads `en.txt` (locale overload), but the cache is
keyed by *path*, so the palette's `root.txt` is a guaranteed cache miss. Compounding: every `Emoji`
constructor allocates a boxed codepoint list + up to 11 linear scans (`Emoji.kt:64-68`) — measured
10× the cost of an unboxed pass, ×~1,500 emoji. Estimated 50–150 ms main-thread stall on first
emoji-panel open on a mid device.
**Fix:** `withContext(Dispatchers.IO)` inside `EmojiData.get`; prewarm the same path; unboxed scan.

### 3.2 Clipboard history: full-resolution image/video decode on the main thread during scroll
`ime/clipboard/ClipboardInputLayout.kt:448-495`

`BitmapFactory.decodeFile` with **no** `inSampleSize`/bounds pass, inside `remember(id)` in item
composition — a 12 MP screenshot decodes ~48 MB ARGB to fill a ~160 dp cell, mid-fling, on the main
thread. Video is worse: `createVideoThumbnail` at the video's **native resolution** plus a
`MediaMetadataRetriever` container parse, synchronously. ANR-class jank with media in history.
Adjacent (:585-596): the staggered grid passes **no item keys** (the emoji grid does), so every new
copy shifts identity of all visible cells and re-runs those decodes; `ClipboardFileStorage.kt:33-34`
adds a `mkdirs()` syscall per item getter access. `ClipboardManager.kt:112-130` collects the Room
flow on `Dispatchers.Main` and builds `ClipboardHistory` there — sort + JNI classify + four filtered
list copies per copy event (~1–3 ms at N≈500), then `enforceHistoryLimit` adds three more arrays and
a second JNI call.
**Fix:** bounds pass + `inSampleSize` (or reuse the coil dependency), decode via
`produceState(Dispatchers.IO)`; `items(items, key = { it.id })`; drop the `withContext(Main)`.

### 3.3 Theme engine: unmemoized Material color derivation invalidates the whole styled tree
`lib/snygg/.../ui/SnyggUi.kt:166-180`, `SnyggPropertySetEditor.kt:67-69`, `SnyggTheme.kt:72-78`

`dynamicColorScheme(...)` is computed **twice, un-remembered, per `ProvideSnyggTheme`
recomposition** (two full HCT/CAM16 tonal-palette derivations). `ColorScheme` lacks `equals`, the
schemes feed non-static `compositionLocalOf`s, and they are `remember` keys inside
`rememberQuery` — so every recomposition re-runs `SnyggTheme.query()` for **every** SnyggBox/Text/
Icon/Chip in the smartbar/candidates/clipboard/emoji trees. `ProvideSnyggTheme` recomposes whenever
`activeState` changes — shift/auto-caps transitions, i.e. every sentence. Each query also allocates a
`SnyggSinglePropertySetEditor` whose `val uuid = UUID.randomUUID().toString()` (measured 588 ns;
~59 µs per 100-query restyle) exists solely for the settings theme editor, runs `inheritImplicitly`
N+1 times per query, and `build()` copies the map again.
**Fix:** `remember(dynamicAccentColor, materialYouFlags)` around both schemes (two lines, biggest
single lever in this tier); `uuid` → `by lazy`; hoist `inheritImplicitly` out of the rule loop.
(The 40 letter keys are canvas-drawn and unaffected — verified.)

### 3.4 Panel odds and ends (verified)
- `lib/compose/ScrollableModifiers.kt:190-217`: `florisScrollbar` reads `LazyGridState.layoutInfo`
  in composition through identity `derivedStateOf` — recomposes per scroll frame; ×3 grids inside
  the emoji pager. (Also latent: un-keyed `remember`s freeze thumb size.)
- `ClipboardInputLayout.kt:244-246` / `EmojiPaletteView.kt:152-154`: `isDeviceLocked`/
  `isKeyguardLocked` binder IPCs per recomposition.
- `QuickActionButton.kt:82-157`: `remember(action, evaluator)` defeated because a fresh evaluator
  instance is created per state change (`KeyboardManager.kt:202-214`) → tooltip string, attribute
  map, icon/label recompute for every button on every state transition; `TextKeyButton`'s attribute
  `mapOf` (TextKeyboardLayout.kt:8126-8130) same story on evaluator changes.
- `TextKeyboardLayout.kt:895-913, 1030-1050`: fret-pulse `rememberInfiniteTransition` created
  unconditionally (runs even with frets hidden by theme; keyboard never fully idles) and the
  `drawBehind` builds a fresh gradient `Brush` (new shader) per frame per fret line.
- `ImeWindow.kt:134`: `DevtoolsOverlay` composed in release — 8 state subscriptions incl. per-layout
  and per-theme flows, for debug-only UI. Same for `CrakeDynamicIslandOverlay` (`Smartbar.kt:137`)
  collecting flows while idle.

---

## Tier 4 — Startup, background & battery

### 4.1 Auto-updater lives in the IME process: 15-min wake loop, hourly checks, and a 56 MB download that ignores the Wi-Fi pref
`app/updater/UpdateManager.kt:253-270, 301-303, 327-467, 567-578`; defaults `AppPrefs.kt:242-248, 290-293`

Started unconditionally from `FlorisApplication.onCreate` (no process/WorkManager separation): a
coroutine wakes every 15 min forever; defaults `autoCheckEnabled=true, checkIntervalHours=1` → up to
3 HTTPS requests/hour from the keyboard process. `autoDownloadOnWifi=true` gates the 56 MB APK
download — but **no connectivity-type check exists anywhere in the file** (verified by grep:
no `NetworkCapabilities`/`activeNetwork`/`ConnectivityManager`), so it downloads on metered data
too. During download, progress updates per percent flow into `DynamicIslandManager` →
`CrakeDynamicIslandOverlay` **inside the smartbar**: ~100 recompositions of an elevated, gradient,
`animateContentSize` surface plus an infinite pulse animation invalidating draw every frame — while
the user types.
**Fix:** WorkManager with `NetworkType.UNMETERED` + sane interval; throttle progress to ~5 % steps;
don't animate over the keyboard.

### 4.2 Cold start: theme parsed 3–4×, racing a concurrent cache wipe
`FlorisApplication.kt:118-173`, `ThemeManager.kt:87-152`, `ExtensionManager.kt:110-116`

Four unordered IO coroutines: block 5 reads pref-backed state (`activeSubtype`, theme ids) that
block 1 is still loading — nothing awaits the existing `preferenceStoreLoaded` flag. The stylesheet
is then loaded up to 4× per cold start (explicit call, combine-initial, combine-after-datastore,
extension-index emission which *clears the cache first*). Each miss unzips the whole 240 KB theme
extension into `cacheDir/loaded/<UUID>/` (never deleted — the TODO at :139 admits the leak) — while
block 2 concurrently runs `cacheDir.deleteContentsRecursively()`, which can delete the tree between
`mkdirs()` and `readText()` (upstream did the wipe synchronously *before* init; the "never blocks UI
thread" comment at :130 marks the change that introduced the race). Extension indexing itself is
serial (3 inits, ~115 KB polymorphic JSON) with a redundant `AssetManager.list` per entry. The
startup log line times coroutine *launch* (µs) and calls it "cold-start app init" — it measures
nothing.
**Fix:** await `preferenceStoreLoaded` in block 5; wipe cache before fan-out or exclude `loaded/`;
no-op `updateActiveTheme` when the name is unchanged; `async`/`awaitAll` the three indexes.

### 4.3 Baseline profile covers the settings screen, not the keyboard
`app/src/main/baseline-prof.txt`, generator `benchmark/.../BaselineProfileGenerator.kt:33-42`

8,108 lines, zero entries for Crake-specific code; the generator launches the settings Activity and
idles. The IME cold-start path — `FlorisImeService`, the Compose keyboard tree, dictionary load — is
never exercised, so the code that must hit "keyboard visible <100 ms" is not AOT-compiled on install.
**Fix:** add a profile journey that focuses a text field with the IME active.

### 4.4 Settings-side polling (not IME-hot, but battery/binder churn)
- `lib/util/InputMethodUtils.kt:132-142`: IMM `enabledInputMethodList` binder round-trip every
  500 ms; the `foregroundOnly=true` flag every caller passes is **ignored on the API 34+ branch**,
  so Home-screen observers keep polling at 2/s with the app backgrounded; `SetupScreen.kt:151-155`
  adds its own 200 ms loop → ~7 binder calls/s on setup.
- `DiagnosticSyncManager.kt:314-315`: two regexes compiled per record × 350 records per 20-min sync
  (~700 `Pattern.compile`s); `FlightRecorderManager.kt:123-126, 542-552`: per-record
  `SimpleDateFormat` construction + open/write/close + 2 stat syscalls despite a Channel that makes
  batching trivial (recorder-on only).
- `ThemeManager.kt:140-152`: every uncached theme load leaks a `cacheDir/loaded/<uuid>` copy (disk
  growth; also feeds 4.2).
- Native memory: every corpus word is stored 4× (`trie` terminal, `corpus_words`, `corpus_freqs`,
  `word_ids` — `nlp.rs:616-632`) ≈ 200k `String`s built during cold start.

---

## Verified-clean (checked, no action)

Release build config (R8 + resource shrink on, fat-LTO `opt-level=3` native); `flog*` logging
properly gated inline; `Context.vectorResource` memoized; popup config memoized;
`predictNextLetterWordsCached` memo + background warmer; audio/haptic feedback off-thread;
`ShadowHitTest` debug-gated; theme info cached with mutex on Default; dictionary blob load is a
single JNI crossing; `fuzzy_search_weighted` has real branch-and-bound pruning; jetpref reads are
memory-backed (no per-read disk IO); `GlideTypingManager.setLayout` geometry short-circuit; emoji
grids pass item keys; `TypingTelemetricsManager` heavy work is settings-screen-only on IO.

---

## Priorities

**Best value-per-effort, roughly in order:**
1. Guard the flight-recorder call sites (1.6) and fix the battery widget (1.7) — trivial, pure win.
2. Replace the easter-egg scan with a precomputed set (1.1) — measured 109×.
3. `remember` the two `dynamicColorScheme`s + `by lazy` the Snygg uuid (3.3) — three lines, stops
   whole-tree restyles.
4. Hoist regexes / `ZonedDateTime` / `takeLast(4)` / equality-guard the pref writes (1.8).
5. Bulk-fetch touch offsets per layout (1.4); cache the system dictionary (1.5).
6. Move `recordTouchHit` off the UI thread and off `NLP_ENGINE` (1.3).
7. Trie subtree-frequency pruning (2.1) — the single biggest native win; helps typing *and* glide.
8. Emoji parse off-main + prewarm fix (3.1); clipboard decode fix + grid keys (3.2).
9. Editor-pipeline structural work: BMP fast path, volatile content, cached substrings (1.2).
10. Updater to WorkManager + real unmetered check (4.1); startup ordering (4.2); baseline profile
    (4.3).

Benchmark harness lives at `libnative/crates/floris-core/tests/perf_review_bench.rs`
(`cargo test -p floris-core --release --test perf_review_bench -- --ignored --nocapture`), so every
native number above is reproducible before/after a fix.
