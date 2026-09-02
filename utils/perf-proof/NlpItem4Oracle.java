import java.util.Random;
import java.util.regex.Pattern;

/**
 * Guards for item 4.
 *
 * PART A — SentenceEndMatcher: the old code runs `".*[.?!]\\s+$".matches(textBefore)`
 *   (Kotlin Regex.matches == full match). Replaced with a hand-rolled tail scan. This
 *   ports the exact Java regex as the reference and diffs the scan over generated inputs
 *   incl. newlines, long whitespace runs, exotic line terminators, multiple terminators.
 *
 * PART B — evalSimpleMath: the inner regex is hoisted to a file-level constant with the
 *   IDENTICAL pattern string. This ports old (inline compile) vs new (hoisted) and diffs
 *   over generated math expressions to prove the pattern string was copied faithfully.
 */
public class NlpItem4Oracle {

    // ---------- PART A ----------
    static final Pattern SENTENCE = Pattern.compile(".*[.?!]\\s+$"); // exact old pattern
    static boolean oldSentence(String s) { return SENTENCE.matcher(s).matches(); }

    // Java default \s = [ \t\n\x0B\f\r]; Java default '.' excludes [\n \r \u0085 \u2028 \u2029]
    static boolean isJavaWs(char c) { return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r'; }
    static boolean isLineTerm(char c) { return c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029'; }

    static boolean newSentence(String s) {
        int i = s.length() - 1;
        if (i < 0 || !isJavaWs(s.charAt(i))) return false;      // must end with >=1 whitespace (\s+$)
        while (i >= 0 && isJavaWs(s.charAt(i))) i--;            // skip maximal trailing whitespace run
        if (i < 0) return false;                                // all whitespace -> no [.?!]
        char c = s.charAt(i);
        if (c != '.' && c != '?' && c != '!') return false;    // char before the run must be a terminator
        for (int j = 0; j < i; j++) if (isLineTerm(s.charAt(j))) return false; // .* cannot cross a line terminator
        return true;
    }

    // ---------- PART B ----------
    static String oldEvalMath(String expr) {
        try {
            String sanitized = expr.replace("x","*").replace("X","*").replace("\u00d7","*").replace("\u00f7","/");
            var m = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*([\\+\\-\\*/\\^%])\\s*(-?\\d+(?:\\.\\d+)?)$").matcher(sanitized);
            if (m.matches()) return compute(m.group(1), m.group(2), m.group(3));
            return null;
        } catch (Exception e) { return null; }
    }
    static final Pattern SIMPLE_MATH = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*([\\+\\-\\*/\\^%])\\s*(-?\\d+(?:\\.\\d+)?)$");
    static String newEvalMath(String expr) {
        try {
            String sanitized = expr.replace("x","*").replace("X","*").replace("\u00d7","*").replace("\u00f7","/");
            var m = SIMPLE_MATH.matcher(sanitized);
            if (m.matches()) return compute(m.group(1), m.group(2), m.group(3));
            return null;
        } catch (Exception e) { return null; }
    }
    static String compute(String as, String op, String bs) {
        double a = Double.parseDouble(as), b = Double.parseDouble(bs);
        Double res;
        switch (op) {
            case "+": res = a + b; break;  case "-": res = a - b; break;  case "*": res = a * b; break;
            case "/": res = (b != 0.0) ? a / b : null; break;  case "^": res = Math.pow(a, b); break;
            case "%": res = a % b; break;  default: res = null;
        }
        if (res == null) return null;
        if (res % 1.0 == 0.0 && res <= Long.MAX_VALUE && res >= Long.MIN_VALUE) return Long.toString((long)(double) res);
        String t = String.format("%.4f", res);
        // trimEnd('0').trimEnd('.')
        int e = t.length(); while (e > 0 && t.charAt(e-1) == '0') e--; t = t.substring(0, e);
        e = t.length(); while (e > 0 && t.charAt(e-1) == '.') e--; return t.substring(0, e);
    }

    public static void main(String[] args) {
        Random rnd = new Random(99);
        // PART A corpus
        String[] atoms = {"a","b",".","?","!"," ","\t","\n","\r","\u000B","\f","hello","bye","word","\u0085","\u2028","x."," ."};
        long aCases = 0, aMis = 0;
        for (int t = 0; t < 4_000_000; t++) {
            int n = rnd.nextInt(8);
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < n; k++) sb.append(atoms[rnd.nextInt(atoms.length)]);
            String s = sb.toString();
            aCases++;
            if (oldSentence(s) != newSentence(s)) {
                aMis++;
                if (aMis <= 10) System.out.println("A MISMATCH <" + s.replace("\n","\\n").replace("\r","\\r") + "> old="+oldSentence(s)+" new="+newSentence(s));
            }
        }
        // targeted A cases
        String[] targeted = {"", " ", ".", ". ", "hi. ", "hi.  ", "hi.\n", "hi.\t", "a\nb. ", "a\nb.", "word.   \n\t ", "!  ", "no", "yes.x", "line1\nline2? ", "\u2028x. ", "x.\u0085"};
        for (String s : targeted) {
            aCases++;
            if (oldSentence(s) != newSentence(s)) { aMis++; System.out.println("A TARGETED MISMATCH <"+s.replace("\n","\\n").replace("\r","\\r")+"> old="+oldSentence(s)+" new="+newSentence(s)); }
        }

        // PART B corpus
        String[] nums = {"0","1","12","3.5","-4","-2.25","100","999999999999","1.0","2"};
        String[] ops = {"+","-","*","/","^","%","x","X","\u00d7","\u00f7"};
        long bCases = 0, bMis = 0;
        for (int t = 0; t < 2_000_000; t++) {
            String a = nums[rnd.nextInt(nums.length)], op = ops[rnd.nextInt(ops.length)], b = nums[rnd.nextInt(nums.length)];
            String sp1 = rnd.nextBoolean() ? " " : "", sp2 = rnd.nextBoolean() ? " " : "";
            String expr = a + sp1 + op + sp2 + b;
            if (rnd.nextInt(10) == 0) expr = expr + "junk";
            bCases++;
            String o = oldEvalMath(expr), nw = newEvalMath(expr);
            if (o == null ? nw != null : !o.equals(nw)) { bMis++; if (bMis <= 10) System.out.println("B MISMATCH <"+expr+"> old="+o+" new="+nw); }
        }

        System.out.println("A: cases=" + aCases + " mismatches=" + aMis);
        System.out.println("B: cases=" + bCases + " mismatches=" + bMis);
        if (aMis > 0 || bMis > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: SentenceEnd tail-scan == regex; evalSimpleMath hoisted-regex == inline");
    }
}
