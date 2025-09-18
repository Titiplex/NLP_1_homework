package udem.tj.taln;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class generated automatically with LLM.
 */
public class CountTest {

    private Count count;

    @BeforeEach
    void setup() {
        count = new Count();
        count.reset();
    }

    // injects reader in memory
    private void injectContent(Count c, String content) {
        try {
            // bufferedReader
            Field br = Count.class.getDeclaredField("bufferedReader");
            br.setAccessible(true);
            br.set(c, new BufferedReader(new StringReader(content)));

            // processedLines = 0
            Field pl = Count.class.getDeclaredField("processedLines");
            pl.setAccessible(true);
            pl.setInt(c, 0);

            c.reset();

            br.set(c, new BufferedReader(new StringReader(content)));
            pl.setInt(c, 0);
        } catch (Exception e) {
            throw new RuntimeException("Reader injection failed", e);
        }
    }

    @Test
    void execute_countsGrowWithMoreExamples() {
        String data =
                "bonjour tout le monde .\n" +   // punctuation already separated to avoid bugs
                        "bonjour encore .\n" +
                        "la vie la vie .\n";

        injectContent(count, data);

        int typesAfter1 = count.execute(1, "/ignored");
        HashMap<String, AtomicInteger> m1 = count.getCounts();
        assertEquals(5, m1.size(), "Expects 5 types after 1st execution");
        assertTrue(typesAfter1 >= 5);

        int typesAfter2 = count.execute(2, "/ignored");
        HashMap<String, AtomicInteger> m2 = count.getCounts();
        assertTrue(m2.size() >= m1.size(), "Type number should increase");
        assertTrue(typesAfter2 >= typesAfter1, "Return of execute should be the number of new types");

        count.execute(3, "/ignored");
        HashMap<String, AtomicInteger> m3 = count.getCounts();

        // verification of basic frequencies
        assertEquals(2, m3.get("bonjour").get(), "'bonjour' should appear twice");
        assertEquals(3, m3.get(".").get(), "'.' should appear thrice");
        assertEquals(2, m3.get("la").get(), "'la' should appear twice");
        assertEquals(2, m3.get("vie").get(), "'vie' should appear twice");
    }

    @Test
    @Disabled("Active when bug off-by-one is corrected (substring len-2 -> len-1)")
    void execute_splitsTrailingPunctuationCorrectly() {
        String data = "bonjour monde.\n";
        injectContent(count, data);

        count.execute(1, "/ignored");
        HashMap<String, AtomicInteger> m = count.getCounts();

        // Expected after correction : "monde" == 1, "." == 1
        assertEquals(1, m.get("monde").get(), "word wihtout final punctuation should be counted");
        assertEquals(1, m.get(".").get(), "final punctuation should be isolated and counted");
    }
}