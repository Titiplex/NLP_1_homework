package udem.tj.taln;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BPE {

    private static final int MAX_MERGES_CAP = 50_000;  // hard cap
    private static final int MIN_PAIR_FREQ = 2;        // stop if best pair < 2

    public record Encoding(HashMap<String, Integer> vocabulary,
                           List<String> merges,
                           HashSet<String> charset,
                           HashSet<String> tokens) {
    }

    private static final class WordEntry {
        final int id;
        final int freq;
        List<String> tokens; // segmentation courante

        WordEntry(int id, int freq, List<String> tokens) {
            this.id = id;
            this.freq = freq;
            this.tokens = tokens;
        }
    }

    private record PairCount(String pair, int count) {
    }

    private static List<String> charTokens(String marked) {
        List<String> out = new ArrayList<>(marked.length());
        for (int i = 0; i < marked.length(); i++) out.add(String.valueOf(marked.charAt(i)));
        return out;
    }

    // adjacent pair in words
    private static Map<String, Integer> pairMult(List<String> toks) {
        int n = toks.size();
        if (n < 2) return Collections.emptyMap();
        Map<String, Integer> m = new HashMap<>(Math.max(4, n - 1));
        for (int i = 0; i < n - 1; i++) {
            String p = toks.get(i) + " " + toks.get(i + 1);
            m.merge(p, 1, Integer::sum);
        }
        return m;
    }

    private static int mergeInWord(List<String> toks, String left, String right) {
        int i = 0, replaced = 0;
        while (i < toks.size() - 1) {
            if (toks.get(i).equals(left) && toks.get(i + 1).equals(right)) {
                toks.set(i, left + right);
                toks.remove(i + 1);
                replaced++;
            } else {
                i++;
            }
        }
        return replaced;
    }

    public Encoding encode(HashMap<String, AtomicInteger> counts, int vocabSize) {
        if (counts == null || counts.isEmpty()) throw new IllegalArgumentException("Empty vocabulary.");
        long t0 = System.nanoTime();

        List<WordEntry> words = new ArrayList<>(counts.size());
        HashSet<String> charset = new HashSet<>();
        int wid = 0;
        for (var e : counts.entrySet()) {
            String marked = "_" + e.getKey();
            List<String> toks = charTokens(marked);
            charset.addAll(toks);
            words.add(new WordEntry(wid++, e.getValue().get(), toks));
        }

        // global counts, index pair -> words
        HashMap<String, Integer> pairCounts = new HashMap<>();
        HashMap<String, HashSet<Integer>> pairToWords = new HashMap<>();
        for (WordEntry w : words) {
            Map<String, Integer> pm = pairMult(w.tokens);
            if (!pm.isEmpty()) {
                for (var pe : pm.entrySet()) {
                    int add = pe.getValue() * w.freq;
                    pairCounts.merge(pe.getKey(), add, Integer::sum);
                    if (pe.getValue() > 0) {
                        pairToWords.computeIfAbsent(pe.getKey(), _ -> new HashSet<>()).add(w.id);
                    }
                }
            }
        }

        PriorityQueue<PairCount> pq = new PriorityQueue<>(
                (a, b) -> Integer.compare(b.count, a.count)
        );
        for (var e : pairCounts.entrySet()) {
            if (e.getValue() > 0) pq.offer(new PairCount(e.getKey(), e.getValue()));
        }

        HashSet<String> tokens = new HashSet<>(charset);
        List<String> merges = new ArrayList<>();

        int initialSymbols = tokens.size();
        int want = Math.max(vocabSize, initialSymbols);
        int mergesBudget = Math.min(MAX_MERGES_CAP, Math.max(0, want - initialSymbols));

        for (int mergesMade = 0; mergesMade < mergesBudget; mergesMade++) {
            PairCount top = null;
            while (!pq.isEmpty()) {
                PairCount cand = pq.poll();
                int cur = pairCounts.getOrDefault(cand.pair, 0);
                if (cur != cand.count) {
                    if (cur > 0) pq.offer(new PairCount(cand.pair, cur));
                    continue;
                }
                top = cand;
                break;
            }
            if (top == null) break; // no more pairs
            if (top.count < MIN_PAIR_FREQ) break; // early stop gain marginal

            String bestPair = top.pair;
            String[] pr = bestPair.split(" ");
            String left = pr[0], right = pr[1];
            String mergedSym = left + right;

            HashSet<Integer> wordIds = pairToWords.get(bestPair);
            if (wordIds == null || wordIds.isEmpty()) {
                // nobody contains pair -> clean and move on
                pairCounts.put(bestPair, 0);
                continue;
            }

            int totalReplacements = 0;
            List<Integer> impacted = new ArrayList<>(wordIds);

            for (int id : impacted) {
                WordEntry w = words.get(id);

                // comptage "avant"
                Map<String, Integer> before = pairMult(w.tokens);

                // merge dans le mot
                int replacedHere = mergeInWord(w.tokens, left, right);

                totalReplacements += replacedHere * w.freq;

                Map<String, Integer> after = pairMult(w.tokens);

                // delta global (counts + index)
                if (!before.equals(after)) {
                    // fusion keys
                    HashSet<String> keys = new HashSet<>(before.keySet());
                    keys.addAll(after.keySet());

                    for (String p : keys) {
                        int b = before.getOrDefault(p, 0);
                        int a = after.getOrDefault(p, 0);
                        int deltaOcc = (a - b) * w.freq;
                        if (deltaOcc != 0) {
                            int newVal = pairCounts.getOrDefault(p, 0) + deltaOcc;
                            if (newVal <= 0) {
                                pairCounts.remove(p);
                            } else {
                                pairCounts.put(p, newVal);
                            }
                            if (newVal > 0) pq.offer(new PairCount(p, newVal)); // lazy refresh
                        }

                        // index -> pair words
                        if (a > 0) {
                            pairToWords.computeIfAbsent(p, _ -> new HashSet<>()).add(w.id);
                        } else {
                            HashSet<Integer> set = pairToWords.get(p);
                            if (set != null) {
                                set.remove(w.id);
                                if (set.isEmpty()) pairToWords.remove(p);
                            }
                        }
                    }
                }
            }

            // nothing changed -> stop
            if (totalReplacements == 0) break;

            merges.add(bestPair);
            tokens.add(mergedSym);
        }

        // voc -> global freq
        HashMap<String, Integer> vocabOut = new HashMap<>();
        for (WordEntry w : words) {
            String key = String.join(" ", w.tokens);
            vocabOut.merge(key, w.freq, Integer::sum);
        }

        long t1 = System.nanoTime();
        System.out.println("Encoding took " + ((t1 - t0) / 1_000_000) + " ms " +
                "(merges=" + merges.size() + ", symbols=" + tokens.size() + ")");

        return new Encoding(vocabOut, merges, charset, tokens);
    }

    // merges in order
    public List<String> tokenizeWord(String word, List<String> merges,
                                     HashSet<String> charset) {
        final String unkToken = "<UNK>";
        String _word = "_" + word.toLowerCase();

        // initial segmentation
        List<String> toks = new ArrayList<>(_word.length());
        for (int i = 0; i < _word.length(); i++) {
            String c = String.valueOf(_word.charAt(i));
            toks.add(charset.contains(c) ? c : unkToken);
        }

        // replays merges
        for (String m : merges) {
            String[] pr = m.split(" ");
            String left = pr[0], right = pr[1];

            int i = 0;
            while (i < toks.size() - 1) {
                if (toks.get(i).equals(left) && toks.get(i + 1).equals(right)) {
                    toks.set(i, left + right);
                    toks.remove(i + 1);
                } else {
                    i++;
                }
            }
        }
        return toks;
    }
}