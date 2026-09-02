import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Guard for moving FlorisNative.recordTouchHit off the UI thread (TextKeyboardLayout.kt).
 * The native call is unchanged (covered by cargo suites); the two properties the Kotlin
 * change relies on are:
 *   1. a single-thread executor executes tasks in submission order (so EMA/Gaussian touch
 *      updates apply in tap order, matching the old synchronous ordering);
 *   2. capturing the touch args into locals before the lambda snapshots the tap-time values,
 *      so a later mutation of the source vars cannot corrupt an in-flight record.
 */
public class TouchHitDispatchOracle {
    public static void main(String[] args) throws Exception {
        ExecutorService ex = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "crake-touchhit"); t.setDaemon(true); t.setPriority(Thread.MIN_PRIORITY); return t;
        });

        // Property 1: submission order == execution order
        int N = 500_000;
        int[] seen = new int[N];
        AtomicInteger idx = new AtomicInteger(0);
        CountDownLatch done = new CountDownLatch(N);
        for (int i = 0; i < N; i++) {
            final int order = i;
            ex.execute(() -> { seen[idx.getAndIncrement()] = order; done.countDown(); });
        }
        done.await(30, TimeUnit.SECONDS);
        for (int i = 0; i < N; i++) if (seen[i] != i) { System.out.println("FAIL order at " + i + " got " + seen[i]); System.exit(1); }

        // Property 2: primitive capture snapshots the value at submission, not at execution
        final int[] source = { 0 };
        final int TRIALS = 100_000;
        int[] captured = new int[TRIALS];
        CountDownLatch d2 = new CountDownLatch(TRIALS);
        for (int i = 0; i < TRIALS; i++) {
            source[0] = i;
            final int snapshot = source[0];           // mirrors `val hitX = touchX`
            final int slot = i;
            ex.execute(() -> { captured[slot] = snapshot; d2.countDown(); });
        }
        source[0] = -999;                              // later mutation must not affect captured values
        d2.await(30, TimeUnit.SECONDS);
        for (int i = 0; i < TRIALS; i++) if (captured[i] != i) { System.out.println("FAIL capture at " + i + " got " + captured[i]); System.exit(1); }

        ex.shutdown();
        System.out.println("ORACLE PASSED: single-thread executor preserves order (" + N + ") and primitive capture snapshots tap-time values (" + TRIALS + ")");
    }
}
