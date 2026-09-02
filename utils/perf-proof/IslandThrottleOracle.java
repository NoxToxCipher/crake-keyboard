import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Guard for throttling the download-progress island updates (UpdateManager.kt).
 * OLD: emit whenever `percent != lastReportPercent` (every 1% step -> ~100 emits, each a
 *      forced recomposition of the island overlaid on the keyboard during download).
 * NEW: emit only when percent advances >= 5 from the last emitted value, OR when it first
 *      reaches 100 (completion must always show). lastReported updates on emit.
 *
 * Throttle correctness contract (verified over random byte streams):
 *   1. every NEW emit is also an OLD emit (never emit a percent old wouldn't have);
 *   2. NEW emits are >= 5 apart, except the mandatory final 100;
 *   3. if the stream reaches 100, NEW emits 100 exactly once (completion always shown);
 *   4. NEW count <= OLD count (it is a throttle).
 */
public class IslandThrottleOracle {
    static List<Integer> oldEmits(int[] byteChunks, long total) {
        List<Integer> out = new ArrayList<>();
        long downloaded = 0; int last = 0;
        for (int c : byteChunks) {
            downloaded += c;
            int percent = total > 0 ? (int)((downloaded * 100) / total) : 0;
            if (percent != last) { last = percent; out.add(percent); }
        }
        return out;
    }
    static List<Integer> newEmits(int[] byteChunks, long total) {
        List<Integer> out = new ArrayList<>();
        long downloaded = 0; int last = 0;
        for (int c : byteChunks) {
            downloaded += c;
            int percent = total > 0 ? (int)((downloaded * 100) / total) : 0;
            if (percent >= last + 5 || (percent == 100 && last != 100)) { last = percent; out.add(percent); }
        }
        return out;
    }

    public static void main(String[] args) {
        Random rnd = new Random(2026);
        long trials = 0, fail = 0;
        for (int t = 0; t < 200_000; t++) {
            long total = 1 + (long) rnd.nextInt(60_000_000);
            int nChunks = 1 + rnd.nextInt(400);
            int[] chunks = new int[nChunks];
            long remaining = total;
            for (int i = 0; i < nChunks; i++) {
                int c = (int) Math.min(remaining, 1 + rnd.nextInt(400_000));
                chunks[i] = c; remaining -= c;
                if (remaining <= 0) { chunks = java.util.Arrays.copyOf(chunks, i + 1); break; }
            }
            boolean reaches100 = java.util.Arrays.stream(chunks).asLongStream().sum() >= total;
            List<Integer> oldE = oldEmits(chunks, total);
            List<Integer> newE = newEmits(chunks, total);
            trials++;

            // 1. subset
            if (!oldE.containsAll(newE)) { fail++; if (fail<=5) System.out.println("subset fail"); continue; }
            // 2. spacing >=5 except a final 100
            boolean spacing = true;
            for (int i = 1; i < newE.size(); i++) {
                int d = newE.get(i) - newE.get(i-1);
                if (d < 5 && newE.get(i) != 100) { spacing = false; break; }
            }
            if (!spacing) { fail++; if (fail<=5) System.out.println("spacing fail " + newE); continue; }
            // 3. completion shown exactly once if reached
            long count100 = newE.stream().filter(p -> p == 100).count();
            if (reaches100 && count100 != 1) { fail++; if (fail<=5) System.out.println("completion fail " + newE); continue; }
            // 4. throttle (fewer or equal)
            if (newE.size() > oldE.size()) { fail++; if (fail<=5) System.out.println("throttle fail"); continue; }
        }
        System.out.println("trials=" + trials + " failures=" + fail);
        if (fail > 0) { System.out.println("ORACLE FAILED"); System.exit(1); }
        System.out.println("ORACLE PASSED: 5% throttle is a subset, >=5 apart, always shows 100, never more emits than old");
    }
}
