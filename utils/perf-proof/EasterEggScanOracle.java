import java.util.*;

/**
 * Differential oracle for the easter-egg trigger scan (TextKeyboardLayout.kt:522-841).
 *
 * OLD side: faithful port of each group's exact expression (bare/suffix endsWith,
 *   nested key×delimiter boundary loops with tb==k+d equality + " "/"\n"(+quote)
 *   prefixed endsWith + comp conditions), building the interpolated strings the same
 *   way the Kotlin does.
 * NEW side: the rule engine the Kotlin replacement will use — precomputed suffix
 *   lists / equals-sets / comp-sets, allocation-free per call.
 *
 * Both sides receive tb/comp already lowercased & tb tail-bounded, exactly as the
 * effect does at lines 527-528. Any per-group decision mismatch fails the oracle.
 */
public class EasterEggScanOracle {
    static final List<String> DELIMS = Arrays.asList(" ", ".", "!", ",", "?", "\n");
    static final List<String> DELIMS_EMPTY = Arrays.asList("", " ", ".", "!", ",", "?", "\n");

    // ---------- OLD-side faithful ports ----------
    static boolean oldSuffix(List<String> keys, String[] suffixes, String tb, String comp) {
        for (String k : keys) {
            for (String s : suffixes) {
                if (tb.endsWith(k + s)) return true; // s="" gives bare endsWith(k)
            }
            if (comp.equals(k)) return true;
        }
        return false;
    }

    // boundary: tb==k+d || endsWith(p+k+d for p in prefixes) || (compMatches && comp==k && someDelimIsSpaceOrEmpty)
    static boolean oldBoundary(List<String> keys, List<String> delims, boolean compMatches,
                               String[] prefixes, String tb, String comp) {
        for (String k : keys) {
            for (String d : delims) {
                if (tb.equals(k + d)) return true;
                for (String p : prefixes) {
                    if (tb.endsWith(p + k + d)) return true;
                }
                if (compMatches && comp.equals(k) && (d.isEmpty() || d.equals(" "))) return true;
            }
        }
        return false;
    }

    // ---------- NEW-side rule engine ----------
    static final String[] PRE_STD = {" ", "\n"};
    static final String[] PRE_QUOTE = {" ", "\n", "\"", "'"};

    static class SuffixRule {
        final List<String> endsWith = new ArrayList<>();
        final HashSet<String> compSet;
        SuffixRule(List<String> keys, String[] suffixes) {
            for (String k : keys) for (String s : suffixes) endsWith.add(k + s);
            compSet = new HashSet<>(keys);
        }
        boolean match(String tb, String comp) {
            for (String e : endsWith) if (tb.endsWith(e)) return true;
            return compSet.contains(comp);
        }
    }
    static class BoundaryRule {
        final HashSet<String> equalsSet = new HashSet<>();
        final List<String> endsWith = new ArrayList<>();
        final HashSet<String> compSet;
        BoundaryRule(List<String> keys, List<String> delims, boolean compMatches, String[] prefixes) {
            for (String k : keys) for (String d : delims) {
                equalsSet.add(k + d);
                for (String p : prefixes) endsWith.add(p + k + d);
            }
            compSet = compMatches ? new HashSet<>(keys) : new HashSet<>();
        }
        boolean match(String tb, String comp) {
            if (equalsSet.contains(tb)) return true;
            for (String e : endsWith) if (tb.endsWith(e)) return true;
            return compSet.contains(comp);
        }
    }

    // ---------- group registry ----------
    static final List<String> ECLECTUS = Arrays.asList("eclectus","ecky","eckies","roratus");
    static final List<String> SUNCONURE = Arrays.asList("sun conure","sunconure","conure");
    static final List<String> SOCCER = Arrays.asList("soccer","football","futbol");
    static final List<String> RAIN = Arrays.asList("rain","rainy","raining","rainfall","rainstorm");
    static final List<String> MANGO = Arrays.asList("mango","mangoes","mangos");
    static final List<String> CHIEF = Arrays.asList("halo","chief","masterchief","master chief","117","spartan","cortana");
    static final List<String> SKATE = Arrays.asList("rink","skating","iceskating","ice skating","skate","figure skating");
    static final List<String> BERRY = Arrays.asList("berry","berries","strawberry","blueberry","raspberry","blackberry");
    static final List<String> FULLTW = Arrays.asList("tribalwars","tribal wars","tribal_wars");
    static final List<String> SHORTTW = Arrays.asList("tw");
    static final List<String> BAWEN = Arrays.asList("bawen");
    static final List<String> PUBG = Arrays.asList("pubg","airdrop","pochinki","chicken dinner","winner winner");
    static final List<String> LUCIA = Arrays.asList("lucia");
    static final List<String> DUKU = Arrays.asList("duku","langsat","longkong");
    static final List<String> CAR = Arrays.asList("drive","car","driving","cars","driver","drives","drove","aston martin","aston");
    static final List<String> CRYPTO = Arrays.asList("btc","bitcoin","eth","ethereum","sol","solana","arb","arbitrum","atom","cosmos hub","cosmos","rune","thorchain","xmr","monero","ltc","litecoin","to the moon","crypto");
    static final List<String> MURMUR = Arrays.asList("murmur","flock","murmuration","starlings");
    static final List<String> LUNA = Arrays.asList("terra","luna","ust","lunc","do kwon","terra luna","terra usd");
    static final List<String> SUNDAE = Arrays.asList("sundae","sundaes","icecream","ice cream","gelato","parfait");
    static final List<String> NOBLETRAIN = Arrays.asList("noble train","nobletrain","noble_train","sniping trains");
    static final List<String> REGTRAIN = Arrays.asList("train","trains","choo choo","choochoo","locomotive","steam train");
    static final List<String> LOUIE = Arrays.asList("louie","pitty","pitbull","red nose","rednose","red nose pitty");
    static final List<String> FULLAI = Arrays.asList("artificial intelligence","irobot","i, robot","ns5","ns-5","sonny","viki","three laws");
    static final List<String> SHORTAI = Arrays.asList("ai");
    static final List<String> ANDROID = Arrays.asList("android","bugdroid","green dude","google android","apk");
    static final List<String> LOVE = Arrays.asList("i love you","iloveyou","love you","i <3 you","i love u");
    static final List<String> XBOX = Arrays.asList("xbox","xbox 360","series x","series s","xbox one","game pass","achievement unlocked","gamertag","majornelson");
    static final List<String> HIDDEN = Arrays.asList("hidden","assassin","hooded figure","ninja");
    static final List<String> SERENITY = Arrays.asList("serenity","zen garden","stressed","stress","sad","depressed","anxious","anxiety","overwhelmed","unhappy");
    static final List<String> SNIPER = Arrays.asList("snipe","snipes","sniper","sniped","sniping","headshot","360 noscope","awp");
    static final List<String> THOR = Arrays.asList("thor","mjolnir","god of thunder","asgard","odinson");
    static final List<String> MUSHU = Arrays.asList("mushu","mulan","mulsn","cri-kee","dishonor on your cow","dragon","great stone dragon");
    static final List<String> GOKART = Arrays.asList("go-kart","gokart","kart","karting","go kart","gokarts","karts","go-karts","go karts","kartings");
    static final List<String> LICORICE = Arrays.asList("licorice");
    static final List<String> POKEBANK = Arrays.asList("pokemon bank","pokebank");
    static final List<String> RAM = Arrays.asList("ram","rams","battering ram","batteringram");
    static final List<String> BB = Arrays.asList("blackberry bold","blackberry priv","blackberry q10","blackberry passport","blackberry classic","blackberry 9900","blackberry key2","rim blackberry");
    static final List<String> EGG = Arrays.asList("egg");

    static final String[] S2 = {"", " "};
    static final String[] S4 = {"", " ", ".", "!"};
    static final String[] S6 = {"", " ", ".", "!", ",", "?"};
    static final String[] S_LOVE = {"", " ", ".", "!", ",", "?", "❤️", "🌹"};

    interface Grp { boolean oldM(String tb, String comp); boolean newM(String tb, String comp); String name(); }

    static Grp suffixGrp(String name, List<String> keys, String[] s) {
        SuffixRule r = new SuffixRule(keys, s);
        return new Grp() {
            public boolean oldM(String tb, String comp) { return oldSuffix(keys, s, tb, comp); }
            public boolean newM(String tb, String comp) { return r.match(tb, comp); }
            public String name() { return name; }
        };
    }
    static Grp boundGrp(String name, List<String> keys, List<String> d, boolean cm, String[] pre, boolean compEmptyGuard) {
        BoundaryRule r = new BoundaryRule(keys, d, cm, pre);
        return new Grp() {
            public boolean oldM(String tb, String comp) {
                if (compEmptyGuard && !comp.isEmpty()) return false;
                return oldBoundary(keys, d, cm, pre, tb, comp);
            }
            public boolean newM(String tb, String comp) {
                if (compEmptyGuard && !comp.isEmpty()) return false;
                return r.match(tb, comp);
            }
            public String name() { return name; }
        };
    }

    static List<Grp> groups() {
        List<Grp> g = new ArrayList<>();
        g.add(suffixGrp("eclectus", ECLECTUS, S2));
        g.add(suffixGrp("sunconure", SUNCONURE, S2));
        g.add(suffixGrp("soccer", SOCCER, S2));
        g.add(suffixGrp("mango", MANGO, S2));
        g.add(suffixGrp("skate", SKATE, S2));
        g.add(suffixGrp("berry", BERRY, S2));
        g.add(suffixGrp("bawen", BAWEN, S4));
        g.add(suffixGrp("pubg", PUBG, S4));
        g.add(suffixGrp("lucia", LUCIA, S4));
        g.add(suffixGrp("duku", DUKU, S4));
        g.add(suffixGrp("murmur", MURMUR, S4));
        g.add(suffixGrp("fullTw", FULLTW, S6));
        g.add(suffixGrp("sundae", SUNDAE, S6));
        g.add(suffixGrp("louie", LOUIE, S6));
        g.add(suffixGrp("fullAi", FULLAI, S6));
        g.add(suffixGrp("android", ANDROID, S6));
        g.add(suffixGrp("love", LOVE, S_LOVE));
        g.add(suffixGrp("egg", EGG, S2));
        // boundary groups
        g.add(boundGrp("rain", RAIN, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("chief", CHIEF, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("shortTw", SHORTTW, DELIMS, false, PRE_STD, false));
        g.add(boundGrp("car", CAR, DELIMS, false, PRE_QUOTE, true));
        g.add(boundGrp("crypto", CRYPTO, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("luna", LUNA, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("nobleTrain", NOBLETRAIN, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("regTrain", REGTRAIN, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("shortAi", SHORTAI, DELIMS, false, PRE_STD, false));
        g.add(boundGrp("xbox", XBOX, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("hidden", HIDDEN, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("serenity", SERENITY, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("sniper", SNIPER, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("thor", THOR, DELIMS, true, PRE_STD, false));
        g.add(boundGrp("mushu", MUSHU, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("goKart", GOKART, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("licorice", LICORICE, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("pokeBank", POKEBANK, DELIMS_EMPTY, true, PRE_STD, false));
        g.add(boundGrp("ram", RAM, DELIMS, false, PRE_QUOTE, true));
        g.add(boundGrp("bb", BB, DELIMS_EMPTY, true, PRE_STD, false));
        return g;
    }

    public static void main(String[] args) {
        List<Grp> groups = groups();
        Random rnd = new Random(1234);

        // vocabulary of fragments to build adversarial inputs
        List<String> frags = new ArrayList<>();
        for (Grp ignored : groups) {}
        for (List<String> ks : Arrays.asList(ECLECTUS,SUNCONURE,SOCCER,RAIN,MANGO,CHIEF,SKATE,BERRY,FULLTW,SHORTTW,
                BAWEN,PUBG,LUCIA,DUKU,CAR,CRYPTO,MURMUR,LUNA,SUNDAE,NOBLETRAIN,REGTRAIN,LOUIE,FULLAI,SHORTAI,
                ANDROID,LOVE,XBOX,HIDDEN,SERENITY,SNIPER,THOR,MUSHU,GOKART,LICORICE,POKEBANK,RAM,BB,EGG)) {
            frags.addAll(ks);
        }
        String[] joiners = {"", " ", ".", "!", ",", "?", "\n", "\"", "'", "a", "s", "ing", "x", "  ", "-", "_"};
        String[] prefixesTest = {"", " ", "\n", "\"", "'", "a", "the ", "x", "just ", "must", "re", "un", "card", "strain"};

        long cases = 0; long mismatches = 0;
        Map<String,Long> perGroupMismatch = new TreeMap<>();

        for (int trial = 0; trial < 3_000_000; trial++) {
            // build tb from prefix + word + joiner (word-boundary adversarial)
            String pre = prefixesTest[rnd.nextInt(prefixesTest.length)];
            String w = frags.get(rnd.nextInt(frags.size()));
            String j = joiners[rnd.nextInt(joiners.length)];
            String rawTb;
            int mode = rnd.nextInt(4);
            if (mode == 0) rawTb = pre + w + j;
            else if (mode == 1) rawTb = w + j;                       // word at buffer start
            else if (mode == 2) rawTb = pre + w;                     // no trailing delim
            else {                                                    // multi-word
                rawTb = frags.get(rnd.nextInt(frags.size())) + " " + pre + w + j;
            }
            // mimic the effect: takeLast(64).lowercase()
            String tb = rawTb.length() > 64 ? rawTb.substring(rawTb.length() - 64) : rawTb;
            tb = tb.toLowerCase();
            String comp = (rnd.nextInt(3) == 0) ? "" : frags.get(rnd.nextInt(frags.size())).toLowerCase();
            if (rnd.nextInt(5) == 0) comp = w.toLowerCase();

            for (Grp gr : groups) {
                cases++;
                if (gr.oldM(tb, comp) != gr.newM(tb, comp)) {
                    mismatches++;
                    perGroupMismatch.merge(gr.name(), 1L, Long::sum);
                    if (mismatches <= 10) System.out.println("MISMATCH ["+gr.name()+"] tb=<"+tb.replace("\n","\\n")+"> comp=<"+comp+">");
                }
            }
        }
        System.out.println("groups=" + groups.size() + " decisions=" + cases + " mismatches=" + mismatches);
        if (!perGroupMismatch.isEmpty()) System.out.println("perGroup=" + perGroupMismatch);
        if (mismatches > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: all 38 group matchers decision-identical (old vs precomputed-set)");
    }
}
