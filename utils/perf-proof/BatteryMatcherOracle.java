import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Differential oracle for the BatteryIndicatorWidget trigger matcher
 * (Smartbar.kt:417-431).
 *
 * OLD (exact port of shipped code):
 *   tb = textBeforeSelection.lowercase(); comp = composingText.lowercase()
 *   keys.any { tb.endsWith(it) || tb.endsWith(it + " ") || comp == it }
 *
 * NEW (proposed): bound the work to a tail window, precompute suffixes,
 * set-lookup the composing word. Must be decision-identical on every input.
 */
public class BatteryMatcherOracle {
    static final String[] KEYS = {"battery", "batteries", "supercharge", "overcharge", "power", "charge"};

    static boolean oldMatch(String textBeforeSelection, String composingText) {
        String tb = textBeforeSelection.toLowerCase();
        String comp = composingText.toLowerCase();
        for (String k : KEYS) {
            if (tb.endsWith(k) || tb.endsWith(k + " ") || comp.equals(k)) return true;
        }
        return false;
    }

    // ---- NEW implementation (mirror of the Kotlin replacement) ----
    static final String[] SUFFIXES;
    static final Set<String> KEY_SET = new HashSet<>(Arrays.asList(KEYS));
    static final int WINDOW = 24; // longest key 11 + trailing space + unicode case-fold growth margin
    static {
        SUFFIXES = new String[KEYS.length * 2];
        for (int i = 0; i < KEYS.length; i++) {
            SUFFIXES[i * 2] = KEYS[i];
            SUFFIXES[i * 2 + 1] = KEYS[i] + " ";
        }
    }

    static boolean newMatch(String textBeforeSelection, String composingText) {
        int n = textBeforeSelection.length();
        String tbTail = (n <= WINDOW ? textBeforeSelection : textBeforeSelection.substring(n - WINDOW)).toLowerCase();
        for (String s : SUFFIXES) {
            if (tbTail.endsWith(s)) return true;
        }
        // composing word can only equal a key if it's short; avoid lowercasing long buffers
        if (composingText.length() <= 16) {
            return KEY_SET.contains(composingText.toLowerCase());
        }
        return false;
    }

    public static void main(String[] args) {
        Random rnd = new Random(42);
        String[] words = {"battery", "BATTERY", "Batteries", "supercharge", "OVERCHARGE", "power", "charge",
            "charger", "charged", "recharge", "empower", "powerful", "batter", "b", "chargé",
            "the", "my", "is", "flat", "dead", "İSTANBUL", "naïve", "❤️", "🔋"};
        String[] delims = {" ", ".", "!", ",", "?", "\n", ""};
        long cases = 0, mismatches = 0;

        // 1. exhaustive-ish: every word/delim combo as suffix of random prefixes
        for (int trial = 0; trial < 40_000; trial++) {
            StringBuilder sb = new StringBuilder();
            int len = rnd.nextInt(8);
            for (int i = 0; i < len; i++) {
                sb.append(words[rnd.nextInt(words.length)]).append(delims[rnd.nextInt(delims.length)]);
            }
            String tb = sb.toString();
            String comp = words[rnd.nextInt(words.length)];
            cases++;
            if (oldMatch(tb, comp) != newMatch(tb, comp)) {
                mismatches++;
                if (mismatches <= 5) System.out.println("MISMATCH tb=<" + tb + "> comp=<" + comp + ">");
            }
        }
        // 2. random char soup incl. unicode edge cases
        String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ .!?,\nİıß❤";
        for (int trial = 0; trial < 60_000; trial++) {
            int len = rnd.nextInt(300);
            StringBuilder sb = new StringBuilder(len);
            for (int i = 0; i < len; i++) sb.append(alphabet.charAt(rnd.nextInt(alphabet.length())));
            // half the time, splice a trigger near the end
            if (rnd.nextBoolean() && len > 0) {
                sb.append(words[rnd.nextInt(words.length)]);
                if (rnd.nextBoolean()) sb.append(delims[rnd.nextInt(delims.length)]);
            }
            String tb = sb.toString();
            String comp = rnd.nextBoolean() ? words[rnd.nextInt(words.length)] : tb.substring(Math.max(0, tb.length() - rnd.nextInt(20)));
            cases++;
            if (oldMatch(tb, comp) != newMatch(tb, comp)) {
                mismatches++;
                if (mismatches <= 5) System.out.println("MISMATCH tb=<" + tb + "> comp=<" + comp + ">");
            }
        }
        System.out.println("cases=" + cases + " mismatches=" + mismatches);
        if (mismatches > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: old and new matcher are decision-identical");
    }
}
