package udem.tj.taln;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class BPETest {

    private HashMap<String, AtomicInteger> toyCounts() {
        // toy corpus
        HashMap<String, AtomicInteger> m = new HashMap<>();
        m.put("de", new AtomicInteger(120));
        m.put("la", new AtomicInteger(80));
        m.put("paris", new AtomicInteger(15));
        m.put("partir", new AtomicInteger(12));
        m.put("dela", new AtomicInteger(5));
        return m;
    }

    @Test
    void encode_basicMergesAndVocabularyAreProduced() {
        BPE bpe = new BPE();
        HashMap<String, AtomicInteger> counts = toyCounts();

        int targetVocab = 200;
        BPE.Encoding enc = bpe.encode(counts, targetVocab);

        assertFalse(enc.merges().isEmpty(), "no merge produced");
        assertFalse(enc.vocabulary().isEmpty(), "empty voc after training");
        assertTrue(enc.merges().getFirst().contains(" "),
                "merge format incorrect");
    }

    @Test
    void tokenizeWord_preservesSurfaceForm() {
        BPE bpe = new BPE();
        BPE.Encoding enc = bpe.encode(toyCounts(), 200);

        String w = "paris";
        List<String> toks = bpe.tokenizeWordFast(w, enc.merges(), enc.charset(), true);

        // concat of tokens == "_" + mot
        assertEquals("_" + w, String.join("", toks),
                "tokenisation does not preserve surface form");
    }

    @Test
    void encode_respectsBudgetAndStops() {
        BPE bpe = new BPE();
        HashMap<String, AtomicInteger> counts = toyCounts();

        int targetVocab = 100;
        BPE.Encoding enc = bpe.encode(counts, targetVocab);

        assertFalse(enc.merges().isEmpty(), "No merge (should be > 0)");
        assertTrue(enc.merges().size() < 50_000, "merge number too high (should be < 50k)");

        // charset should be unchanged
        assertTrue(enc.tokens().size() >= enc.charset().size(),
                "Token set can't be smaller that charset");
    }

    @Test
    void tokenizeWord_unknownCharYieldsUnk() {
        BPE bpe = new BPE();
        BPE.Encoding enc = bpe.encode(toyCounts(), 200);

        // introduces unknown char
        List<String> toks = bpe.tokenizeWord("euros€", enc.merges(), enc.charset(), true);
        assertTrue(toks.contains("<UNK>"),
                "Unknown char should produce <UNK>");
    }
}