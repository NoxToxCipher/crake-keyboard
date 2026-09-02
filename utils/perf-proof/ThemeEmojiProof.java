import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * E: SnyggSinglePropertySetEditor — `val uuid = UUID.randomUUID().toString()` runs on
 *    every SnyggTheme.query() (SnyggPropertySetEditor.kt:68, SnyggTheme.kt:72); the uuid
 *    is only consumed by the settings theme editor. 50-150 queries per keyboard restyle.
 * F: Emoji.kt:64-68 — value.codePoints().toList() (boxed) + up to 11 List.contains scans,
 *    per Emoji constructed; ~1500 emoji in root.txt parsed on the MAIN thread at panel open
 *    (MediaInputLayout.kt:74-77 LaunchedEffect without dispatcher switch).
 */
public class ThemeEmojiProof {
    static volatile Object sink;

    static final int[] SKIN_TONE_IDS = {0x1F3FB, 0x1F3FC, 0x1F3FD, 0x1F3FE, 0x1F3FF};
    static final int[] HAIR_STYLE_IDS = {0x1F9B0, 0x1F9B1, 0x1F9B2, 0x1F9B3, 0x2642, 0x2640};

    // current: IntStream -> boxed List, then linear contains per enum entry
    static int emojiInitCurrent(String value) {
        List<Integer> codePoints = new ArrayList<>();
        value.codePoints().forEach(codePoints::add); // same boxing as codePoints().toList()
        int found = 0;
        for (int id : SKIN_TONE_IDS) if (codePoints.contains(id)) { found++; break; }
        for (int id : HAIR_STYLE_IDS) if (codePoints.contains(id)) { found++; break; }
        return found;
    }

    // fix: single unboxed pass
    static int emojiInitFixed(String value) {
        int found = 0, foundTone = 0, foundHair = 0;
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            if (foundTone == 0) for (int id : SKIN_TONE_IDS) if (cp == id) { foundTone = 1; break; }
            if (foundHair == 0) for (int id : HAIR_STYLE_IDS) if (cp == id) { foundHair = 1; break; }
        }
        return found + foundTone + foundHair;
    }

    public static void main(String[] args) {
        int WARM = 20_000, ITERS = 100_000;

        // E: UUID per style query
        for (int i = 0; i < WARM; i++) sink = UUID.randomUUID().toString();
        long t0 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) sink = UUID.randomUUID().toString();
        long perUuid = (System.nanoTime() - t0) / ITERS;
        System.out.printf("UUID.randomUUID().toString() per style query: %,d ns  -> x100 queries/restyle = %,d ns per keyboard restyle%n",
            perUuid, perUuid * 100);

        // F: emoji ctor scan, typical multi-codepoint emoji with skin tone
        String emoji = "👩🏽‍💻"; // woman technologist, medium skin tone
        for (int i = 0; i < WARM; i++) { sink = emojiInitCurrent(emoji); sink = emojiInitFixed(emoji); }
        long t1 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) sink = emojiInitCurrent(emoji);
        long t2 = System.nanoTime();
        for (int i = 0; i < ITERS; i++) sink = emojiInitFixed(emoji);
        long t3 = System.nanoTime();
        long cur = (t2 - t1) / ITERS, fix = (t3 - t2) / ITERS;
        System.out.printf("Emoji init, boxed codePoints().toList():      %,d ns/emoji  -> x1500 emoji = %.2f ms on main thread at panel open (desktop JVM)%n",
            cur, cur * 1500 / 1e6);
        System.out.printf("Emoji init, unboxed single pass:              %,d ns/emoji  -> x1500 emoji = %.2f ms%n",
            fix, fix * 1500 / 1e6);
    }
}
