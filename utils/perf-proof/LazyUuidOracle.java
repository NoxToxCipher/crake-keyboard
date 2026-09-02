import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Guard for making SnyggSinglePropertySetEditor.uuid lazy (SnyggPropertySetEditor.kt).
 * The only consumer is `key(propertySet.uuid)` in ThemeEditorScreen — Compose's key()
 * needs each editor instance to expose ONE stable id, and sibling instances to expose
 * DISTINCT ids. This checks the lazy form preserves both properties vs the eager form.
 */
public class LazyUuidOracle {
    // eager form (current): computed in ctor
    static final class Eager { final String uuid = UUID.randomUUID().toString(); }

    // lazy form (proposed): computed on first access, cached, thread-safe
    static final class Lazy {
        private String cached;
        String uuid() { // mirrors Kotlin `by lazy` (SYNCHRONIZED)
            if (cached == null) synchronized (this) { if (cached == null) cached = UUID.randomUUID().toString(); }
            return cached;
        }
    }

    public static void main(String[] args) throws Exception {
        int N = 200_000;
        // property 1: stable across repeated reads of the same instance
        for (int i = 0; i < N; i++) {
            Lazy l = new Lazy();
            String a = l.uuid(), b = l.uuid(), c = l.uuid();
            if (!a.equals(b) || !b.equals(c)) { System.out.println("FAIL: unstable id"); System.exit(1); }
        }
        // property 2: distinct across instances (same as eager)
        Set<String> lazySeen = new HashSet<>(), eagerSeen = new HashSet<>();
        for (int i = 0; i < N; i++) {
            Lazy l = new Lazy();
            if (!lazySeen.add(l.uuid())) { System.out.println("FAIL: lazy collision"); System.exit(1); }
            if (!eagerSeen.add(new Eager().uuid)) { System.out.println("FAIL: eager collision"); System.exit(1); }
        }
        // property 3: concurrent first-access on one instance yields one id (no torn value)
        for (int t = 0; t < 2000; t++) {
            final Lazy l = new Lazy();
            final String[] got = new String[8];
            Thread[] ts = new Thread[8];
            for (int k = 0; k < 8; k++) { final int kk = k; ts[k] = new Thread(() -> got[kk] = l.uuid()); }
            for (Thread th : ts) th.start();
            for (Thread th : ts) th.join();
            for (int k = 1; k < 8; k++) if (!got[0].equals(got[k])) { System.out.println("FAIL: concurrent torn id"); System.exit(1); }
        }
        System.out.println("ORACLE PASSED: lazy uuid is per-instance-stable, cross-instance-distinct, concurrency-safe ("
            + N + " instances, 2000 concurrent trials)");
    }
}
