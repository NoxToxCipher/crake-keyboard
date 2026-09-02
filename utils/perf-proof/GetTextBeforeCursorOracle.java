import java.text.BreakIterator;
import java.util.Random;

/**
 * Guard for the BMP fast path in getTextBeforeCursor(n) (AbstractEditorInstance.kt).
 *
 * OLD: length = measureLastUChars(tb, n)  // ICU character-instance (grapheme) BreakIterator
 *      return tb.takeLast(length)
 * NEW: if every one of the last min(n, len) chars is in [U+0020, U+02FF] (a standalone-grapheme
 *      range under ANY Unicode version — no Grapheme_Extend below U+0300, no surrogates, and the
 *      only multi-char graphemes below it, CR+LF, are excluded because CR/LF are < U+0020),
 *      then the last n graphemes are exactly the last n chars -> return tb.takeLast(n);
 *      else fall back to the ICU measureLastUChars path (unchanged).
 *
 * This ports measureLastUChars against java.text.BreakIterator (authoritative and version-
 * identical to android.icu for the SIMPLE inputs the fast path accepts) and asserts the new
 * function equals the reference for every input and n in {1,2,3}. For non-simple inputs the new
 * function just calls the reference, so only the fast-path claim (takeLast(n) == reference) is
 * actually under test — exactly what must hold.
 */
public class GetTextBeforeCursorOracle {

    static int measureLastUChars(String text, int n) {
        BreakIterator it = BreakIterator.getCharacterInstance();
        it.setText(text);
        int end = it.last();
        int start;
        int count = 0;
        do {
            start = it.previous();
        } while (start != BreakIterator.DONE && ++count < n);
        int len = end - (start == BreakIterator.DONE ? 0 : start);
        return Math.max(0, Math.min(len, text.length()));
    }

    static String takeLast(String s, int k) {
        if (k >= s.length()) return s;
        if (k <= 0) return "";
        return s.substring(s.length() - k);
    }

    static String oldFn(String tb, int n) {
        if (n < 1 || tb.isEmpty()) return "";
        return takeLast(tb, measureLastUChars(tb, n));
    }

    static boolean simpleChar(char c) { return c >= '\u0020' && c < '\u0300'; }
    static boolean simpleTail(String tb, int n) {
        int w = Math.min(n, tb.length());
        for (int i = tb.length() - w; i < tb.length(); i++) if (!simpleChar(tb.charAt(i))) return false;
        return true;
    }
    static String newFn(String tb, int n) {
        if (n < 1 || tb.isEmpty()) return "";
        if (simpleTail(tb, n)) return takeLast(tb, n);           // fast path
        return takeLast(tb, measureLastUChars(tb, n));           // ICU fallback (unchanged)
    }

    public static void main(String[] args) {
        String[] targeted = {
            "", "a", "ab", "abc", "abcd", "hello world", "the cat. ",
            "cafe\u0301",           // café (e + combining acute) — last char combining -> fallback
            "a\u0301",              // á, n=1 spans 2 chars
            "x\r\n",                // CRLF one grapheme -> \r,\n < 0x20 so not-simple -> fallback
            "x\ny",                 // lone LF
            "\uD83D\uDE00",         // grinning face (surrogate pair)
            "a\uD83D\uDE00",        // letter + emoji
            "\uD83D\uDC69\uD83C\uDFFD", // woman + skin tone
            "test\u200Dzwj",        // ZWJ
            "num\u00e9ro",          // precomposed é (U+00E9, simple, one grapheme)
            "  ", "..!", "a.b.c ",
        };
        long cases = 0, mism = 0;
        for (String s : targeted) for (int n = 1; n <= 3; n++) {
            cases++;
            if (!oldFn(s, n).equals(newFn(s, n))) { mism++; System.out.println("TARGETED MISMATCH <"+vis(s)+"> n="+n+" old=<"+vis(oldFn(s,n))+"> new=<"+vis(newFn(s,n))+">"); }
        }

        // random corpus mixing simple BMP, combining marks, surrogates, CR/LF, ZWJ
        int[] pool = {'a','b','c','.',' ','\t','\n','\r', 0x00E9, 0x0301, 0x0308, 0x200D, 0x1F600, 0x1F469, 0x1F3FD, 0xFE0F, 0x1100, 0x1161};
        Random rnd = new Random(11);
        for (int t = 0; t < 3_000_000; t++) {
            int len = rnd.nextInt(10);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) sb.appendCodePoint(pool[rnd.nextInt(pool.length)]);
            String s = sb.toString();
            for (int n = 1; n <= 3; n++) {
                cases++;
                if (!oldFn(s, n).equals(newFn(s, n))) { mism++; if (mism <= 8) System.out.println("MISMATCH <"+vis(s)+"> n="+n+" old=<"+vis(oldFn(s,n))+"> new=<"+vis(newFn(s,n))+">"); }
            }
        }
        System.out.println("cases=" + cases + " mismatches=" + mism);
        if (mism > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: BMP fast path == ICU grapheme measureLastUChars for n in {1,2,3}");
    }
    static String vis(String s){ StringBuilder b=new StringBuilder(); for(int i=0;i<s.length();i++){char c=s.charAt(i); b.append(c=='\n'?"\\n":c=='\r'?"\\r":c=='\t'?"\\t":c<0x20||c>0x7e?String.format("\\u%04x",(int)c):String.valueOf(c));} return b.toString(); }
}
