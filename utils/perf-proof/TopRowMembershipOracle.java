import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Guard for the getKeyForPosAdaptive top-row precompute (TextKeyboard.kt).
 * OLD: per key, `arrangement.firstOrNull()?.contains(key) == true` — Array.contains,
 *      identity-based (TextKey is a plain class, no equals/hashCode override).
 * NEW: precompute `arrangement.firstOrNull()?.toHashSet()` once, then `key in set`.
 * For reference-identity objects, array-linear-contains and identity-HashSet membership
 * must agree for every key. This models that over random top rows and query keys.
 */
public class TopRowMembershipOracle {
    static final class Key { } // identity equality, like TextKey

    static boolean oldContains(Key[] topRow, Key k) {
        if (topRow == null) return false;
        for (Key e : topRow) if (e == k) return true; // Array.contains == identity for non-overridden equals
        return false;
    }
    static boolean newContains(Set<Key> topRowSet, Key k) { return topRowSet.contains(k); }

    public static void main(String[] args) {
        Random rnd = new Random(7);
        long cases = 0, mism = 0;
        for (int trial = 0; trial < 500_000; trial++) {
            int n = rnd.nextInt(12);
            Key[] topRow = n == 0 && rnd.nextBoolean() ? null : new Key[n];
            Key[] all = new Key[n + 5];
            for (int i = 0; i < all.length; i++) all[i] = new Key();
            if (topRow != null) for (int i = 0; i < n; i++) topRow[i] = all[i]; // first n are in the row
            Set<Key> set = new HashSet<>();
            if (topRow != null) for (Key e : topRow) set.add(e);
            // query every key (in-row and out-of-row) plus a fresh non-member
            for (Key q : all) { cases++; if (oldContains(topRow, q) != newContains(set, q)) mism++; }
            Key fresh = new Key(); cases++; if (oldContains(topRow, fresh) != newContains(set, fresh)) mism++;
        }
        System.out.println("cases=" + cases + " mismatches=" + mism);
        if (mism > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: identity HashSet membership == Array.contains for all keys");
    }
}
