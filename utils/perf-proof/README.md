# Performance-review proof harness (2026-09-02)

Companions to `PERFORMANCE_REVIEW.md`. Each file ports a hot-path pattern
from the Kotlin sources 1:1 and measures it against the equivalent fix.
Run with a plain JDK: `java PerfProof.java` etc. (no build needed).

- `PerfProof.java` — getTextBeforeCursor ICU pass, per-keystroke regex
  compile, SentenceEndMatcher backtracking, EditorContent substring churn.
- `EasterEggProof.java` — the TextKeyboardLayout easter-egg trigger scan
  (exact group/delimiter shapes) vs a precomputed suffix set.
- `ThemeEmojiProof.java` — Snygg per-query UUID cost, Emoji-ctor boxing.

Native numbers come from
`libnative/crates/floris-core/tests/perf_review_bench.rs`:
`cargo test -p floris-core --release --test perf_review_bench -- --ignored --nocapture`
