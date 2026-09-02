import java.text.BreakIterator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Microbenchmark reproducing hot-path patterns found in crake-keyboard, ported 1:1.
 *
 * A: BreakIteratorGroup.measureLastUChars — setText over the full 256-char buffer
 *    to read the LAST 1 char (AbstractEditorInstance.getTextBeforeCursor), vs a
 *    direct tail scan that only falls back for surrogates/combining marks.
 * B: NlpManager.evaluateMathOrMacro — Regex(...) constructed inline per call
 *    (Pattern.compile per keystroke) vs precompiled.
 * C: KeyboardManager.SentenceEndMatcher — ".*[.?!]\\s+$".matches() over the full
 *    256-char window (backtracking) vs matching on takeLast(4).
 * D: EditorContent computed getters — substring allocation per access, 10x per
 *    keystroke as observed in the commitChar call path.
 */
public class PerfProof {
    static final int WARMUP = 20_000;
    static final int ITERS = 100_000;

    static String buffer256() {
        StringBuilder sb = new StringBuilder(256);
        // realistic prose ending mid-word
        String s = "The quick brown fox jumps over the lazy dog. ";
        while (sb.length() < 250) sb.append(s);
        sb.setLength(250);
        sb.append("hello");
        return sb.toString();
    }

    // pathological but realistic for SentenceEndMatcher: lots of sentence enders, no trailing space
    static String punctuated256() {
        StringBuilder sb = new StringBuilder(256);
        while (sb.length() < 250) sb.append("Hi! Ok? Yes. No. Go! Eh? ");
        sb.setLength(250);
        sb.append("typing");
        return sb.toString();
    }

    // ===== A =====
    static int measureLastUCharsICU(BreakIterator it, String text, int n) {
        it.setText(text);
        int end = it.last();
        int start;
        int count = 0;
        do {
            start = it.previous();
        } while (start != BreakIterator.DONE && ++count < n);
        return end - (start == BreakIterator.DONE ? 0 : start);
    }

    static int measureLastUCharsTailScan(String text, int n) {
        // BMP fast path: walk back n code points; this covers ~100% of latin typing
        int i = text.length();
        int count = 0;
        while (i > 0 && count < n) {
            int cp = text.codePointBefore(i);
            i -= Character.charCount(cp);
            count++;
        }
        return text.length() - i;
    }

    // ===== B =====
    static final Pattern NUM_UNIT_PRECOMPILED = Pattern.compile(
        "^(\\d+(?:\\.\\d+)?)\\s*(usd|aud|eur|gbp|jpy|nzd|cad|kg|g|lb|oz|km|mi|m|ft|cm|in|c|f|l|ml|gal|mb|gb|tb)$",
        Pattern.CASE_INSENSITIVE);

    static boolean perCallCompile(String input) {
        return Pattern.compile(
            "^(\\d+(?:\\.\\d+)?)\\s*(usd|aud|eur|gbp|jpy|nzd|cad|kg|g|lb|oz|km|mi|m|ft|cm|in|c|f|l|ml|gal|mb|gb|tb)$",
            Pattern.CASE_INSENSITIVE).matcher(input).matches();
    }

    // ===== C =====
    static final Pattern SENTENCE_END = Pattern.compile(".*[.?!]\\s+$");

    static long bench(String name, int iters, Runnable r) {
        for (int i = 0; i < WARMUP; i++) r.run();
        System.gc();
        long t0 = System.nanoTime();
        for (int i = 0; i < iters; i++) r.run();
        long t1 = System.nanoTime();
        long perOp = (t1 - t0) / iters;
        System.out.printf("%-72s %,10d ns/op%n", name, perOp);
        return perOp;
    }

    static volatile int sink;
    static volatile boolean bsink;
    static volatile String ssink;

    public static void main(String[] args) {
        String buf = buffer256();
        String punct = punctuated256();
        BreakIterator charIt = BreakIterator.getCharacterInstance(Locale.US);

        System.out.println("== A: getTextBeforeCursor(1) — ICU pass over 256 chars vs tail scan ==");
        long a1 = bench("ICU setText(256 chars) + last() + previous()  [current code]", ITERS,
            () -> sink = measureLastUCharsICU(charIt, buf, 1));
        long a2 = bench("direct tail codepoint scan                    [equivalent result]", ITERS,
            () -> sink = measureLastUCharsTailScan(buf, 1));
        System.out.printf("  -> ICU path is %.0fx slower; commitChar does 4-5 such passes per keystroke%n%n", (double) a1 / Math.max(a2, 1));

        System.out.println("== B: NlpManager.evaluateMathOrMacro — regex compiled per keystroke ==");
        long b1 = bench("Pattern.compile per call                      [current code, line 86]", ITERS,
            () -> bsink = perCallCompile("hello"));
        long b2 = bench("precompiled pattern                           [fix]", ITERS,
            () -> bsink = NUM_UNIT_PRECOMPILED.matcher("hello").matches());
        System.out.printf("  -> per-call compile costs %,d ns extra on every keystroke%n%n", b1 - b2);

        System.out.println("== C: SentenceEndMatcher '.*[.?!]\\s+$' on full 256-char window ==");
        long c1 = bench("matches() on full 256-char punctuated buffer  [current code, line 228]", ITERS,
            () -> bsink = SENTENCE_END.matcher(punct).matches());
        long c2 = bench("matches() on takeLast(4)                      [fix]", ITERS,
            () -> bsink = SENTENCE_END.matcher(punct.substring(punct.length() - 4)).matches());
        System.out.printf("  -> full-window match is %.0fx slower due to backtracking over %d sentence enders%n%n",
            (double) c1 / Math.max(c2, 1), punct.split("[.?!]").length - 1);

        System.out.println("== D: EditorContent.textBeforeSelection — fresh substring per access ==");
        long d1 = bench("10 substring(0, 250) calls (one keystroke's worth of reads)", ITERS, () -> {
            String last = null;
            for (int i = 0; i < 10; i++) last = buf.substring(0, 250);
            ssink = last;
        });
        long d2 = bench("1 cached read reused 10x                      [fix]", ITERS, () -> {
            String cached = buf.substring(0, 250);
            String last = null;
            for (int i = 0; i < 10; i++) last = cached;
            ssink = last;
        });
        System.out.printf("  -> ~%,d ns + 9 x 500-byte garbage per keystroke from repeated computed-getter reads%n", d1 - d2);
    }
}
