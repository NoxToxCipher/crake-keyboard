import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ports the easter-egg trigger scan in TextKeyboardLayout.kt:522-841 (runs in
 * LaunchedEffect(activeContent) — once per keystroke on the main thread).
 *
 * Current shape: ~34 trigger groups, ~160 trigger strings, 6 delimiters.
 * Flat groups:    keys.any { tb.endsWith(it) || tb.endsWith(it + " ") || comp == it ... }  (3-9 interpolated variants)
 * Nested groups:  keys.any { k -> delims.any { d -> tb == k+d || tb.endsWith(" "+k+d) || tb.endsWith("\n"+k+d) ... } }
 *
 * Fix shape: precompute all key+delimiter suffix strings once (they are constants),
 * then per keystroke extract the trailing word(s) of tb once and do Set lookups.
 */
public class EasterEggProof {
    static final int WARMUP = 5_000;
    static final int ITERS = 20_000;

    // 34 groups totalling ~160 keys, mirroring real list sizes (1-9 keys each)
    static final List<List<String>> FLAT_GROUPS = new ArrayList<>();
    static final List<List<String>> NESTED_GROUPS = new ArrayList<>();
    static final String[] DELIMS = {" ", ".", "!", ",", "?", "\n"};

    static {
        String[][] flat = {
            {"eclectus","ecky","eckies","roratus"}, {"sun conure","sunconure","conure"},
            {"soccer","football","futbol"}, {"rain","rainy","raining","rainfall","rainstorm"},
            {"mango","mangoes","mangos"}, {"halo","chief","masterchief","master chief","117","spartan","cortana"},
            {"rink","skating","iceskating","ice skating","skate","figure skating"},
            {"berry","berries","strawberry","blueberry","raspberry","blackberry"},
            {"sundae","sundaes","icecream","ice cream","gelato","parfait"},
            {"louie","pitty","pitbull","red nose","rednose","red nose pitty"},
            {"android","bugdroid","green dude","google android","apk"},
            {"i love you","iloveyou","love you","i <3 you","i love u"},
            {"murmur","flock","murmuration","starlings"},
            {"terra","luna","ust","lunc","do kwon","terra luna","terra usd"},
            {"thor","mjolnir","god of thunder","asgard","odinson"},
            {"mushu","mulan","mulsn","cri-kee","dishonor on your cow","dragon","great stone dragon"},
            {"snipe","snipes","sniper","sniped","sniping","headshot","360 noscope","awp"},
            {"drive","car","driving","cars","driver","drives","drove","aston martin","aston"},
            {"berrytwo","fig","apricot"},
        };
        String[][] nested = {
            {"noble train","nobletrain","noble_train","sniping trains"},
            {"train","trains","choo choo","choochoo","locomotive","steam train"},
            {"xbox","xbox 360","series x","series s","xbox one","game pass","achievement unlocked","gamertag","majornelson"},
            {"tribalwars","tribal wars","tribal_wars"}, {"tw"},
            {"pubg","airdrop","pochinki","chicken dinner","winner winner"},
            {"duku","langsat","longkong"}, {"bawen"}, {"lucia"},
            {"artificial intelligence","irobot","i, robot","ns5","ns-5","sonny","viki","three laws"}, {"ai"},
            {"hidden","assassin","hooded figure","ninja"},
            {"go-kart","gokart","kart","karting","go kart","gokarts","karts","go-karts","go karts","kartings"},
            {"ram","rams","battering ram","batteringram"},
            {"serenity","calm","peaceful"},
        };
        for (String[] g : flat) FLAT_GROUPS.add(List.of(g));
        for (String[] g : nested) NESTED_GROUPS.add(List.of(g));
    }

    // ============ CURRENT CODE SHAPE (interpolation per comparison) ============
    static int scanCurrent(String tb, String comp) {
        int fired = 0;
        for (List<String> keys : FLAT_GROUPS) {
            boolean m = false;
            for (String it : keys) {
                // mirrors: tb.endsWith(it) || tb.endsWith("$it ") || comp == it || tb.endsWith("$it.") ... (7 variants)
                if (tb.endsWith(it) || tb.endsWith(it + " ") || comp.equals(it) || tb.endsWith(it + ".")
                    || tb.endsWith(it + "!") || tb.endsWith(it + ",") || tb.endsWith(it + "?")) { m = true; break; }
            }
            if (m) fired++;
        }
        for (List<String> keys : NESTED_GROUPS) {
            boolean m = false;
            outer:
            for (String k : keys) {
                for (String d : DELIMS) {
                    // mirrors: tb == "$k$d" || tb.endsWith(" $k$d") || tb.endsWith("\n$k$d") || (comp == k && d == " ")
                    if (tb.equals(k + d) || tb.endsWith(" " + k + d) || tb.endsWith("\n" + k + d)
                        || (comp.equals(k) && d.equals(" "))) { m = true; break outer; }
                }
            }
            if (m) fired++;
        }
        return fired;
    }

    // ============ FIX SHAPE (precomputed suffix sets, one tail extraction) ============
    static final Set<String> FLAT_SET = new HashSet<>();      // bare keys (also matches comp)
    static final Set<String> NESTED_SET = new HashSet<>();    // bare keys, delimiter checked separately
    static final int MAX_KEY_LEN;
    static {
        int max = 0;
        for (List<String> g : FLAT_GROUPS) for (String k : g) { FLAT_SET.add(k); max = Math.max(max, k.length()); }
        for (List<String> g : NESTED_GROUPS) for (String k : g) { NESTED_SET.add(k); max = Math.max(max, k.length()); }
        MAX_KEY_LEN = max;
    }

    static int scanOptimized(String tb, String comp) {
        int fired = 0;
        // strip at most one trailing delimiter, remember whether one was present
        int end = tb.length();
        boolean hadDelim = false;
        if (end > 0) {
            char c = tb.charAt(end - 1);
            if (c == ' ' || c == '.' || c == '!' || c == ',' || c == '?' || c == '\n') { end--; hadDelim = true; }
        }
        // candidate suffixes: every token-boundary suffix of the last MAX_KEY_LEN chars
        int start = Math.max(0, end - MAX_KEY_LEN);
        for (int i = start; i < end; i++) {
            if (i == start || tb.charAt(i - 1) == ' ' || tb.charAt(i - 1) == '\n') {
                String cand = tb.substring(i, end);
                if (FLAT_SET.contains(cand)) fired++;
                if (hadDelim && NESTED_SET.contains(cand)) fired++;
            }
        }
        if (FLAT_SET.contains(comp)) fired++;
        if (NESTED_SET.contains(comp)) fired++;
        return fired;
    }

    static volatile int sink;

    public static void main(String[] args) {
        // realistic 64-char lowercased tail mid-sentence, no trigger hit (the common case)
        String tb = "and then we went down to the shops to grab some milk and brea".toLowerCase();
        String comp = "brea";

        for (int i = 0; i < WARMUP; i++) { sink = scanCurrent(tb, comp); sink = scanOptimized(tb, comp); }
        System.gc();

        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) sink = scanCurrent(tb, comp);
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) sink = scanOptimized(tb, comp);
        long t2 = System.nanoTime();

        long cur = (t1 - t0) / ITERS, opt = (t2 - t1) / ITERS;
        System.out.printf("easter-egg scan, current code shape:   %,8d ns/keystroke%n", cur);
        System.out.printf("easter-egg scan, precomputed-set fix:  %,8d ns/keystroke%n", opt);
        System.out.printf("-> %.0fx slower; runs on the main thread once per typed character%n", (double) cur / Math.max(opt, 1));

        // allocation estimate for one pass of current shape
        long allocs = 0;
        for (List<String> g : FLAT_GROUPS) allocs += g.size() * 6L;      // 6 concats per key
        for (List<String> g : NESTED_GROUPS) allocs += g.size() * 6L * 3L; // 3 concats per key per delim
        System.out.printf("string concatenations per keystroke (miss case, exact count from group sizes): %,d%n", allocs);
    }
}
