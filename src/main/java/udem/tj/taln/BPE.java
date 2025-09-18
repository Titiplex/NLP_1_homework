package udem.tj.taln;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Byte Pair Encoding (BPE) algorithm for subword tokenization and vocabulary management.
 * Code written originally by hand.
 * LLM usage for improvement in performance, otherwise couldn't process more than ~10 sentences.
 */
public class BPE {

    private static final int MAX_MERGES_CAP = 50_000;  // hard cap
    private static final int MIN_PAIR_FREQ = 2;        // stop if best pair < 2

    // utilitaires pour la tokenization
    private static final Map<List<String>, HashMap<String, Integer>> TOP_CACHE = Collections.synchronizedMap(new WeakHashMap<>());
    private static final int WORD_CACHE_CAP = 100000;
    private static final Map<String, List<String>> WORD_CACHE =
            Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<String>> e) {
                    return size() > WORD_CACHE_CAP;
                }
            });

    /**
     * The Encoding record represents a structure used for byte pair encoding (BPE) techniques.
     * This immutable data structure holds the internal components needed for encoding and tokenization processes.
     * <p>
     * Fields:
     * - vocabulary: A mapping of subword strings to their respective integer indices in the vocabulary.
     * - merges: A list representing the predefined merge operations sorted by priority for BPE.
     * - charset: A set of allowed characters for segmentation during the tokenization process.
     * - tokens: A set of known tokens used for encoding and validation processes.
     * <p>
     * This class is typically used in conjunction with BPE algorithms for subword tokenization and vocabulary management.
     */
    public record Encoding(HashMap<String, Integer> vocabulary,
                           List<String> merges,
                           HashSet<String> charset,
                           HashSet<String> tokens) {
    }

    /**
     * Represents an entry for a word, consisting of its identifier, frequency,
     * and a list of token segmentations.
     * This class encapsulates the properties and behavior of a single word
     * entry used in tokenization or encoding processes.
     */
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

    /**
     * A record used to represent a pair of strings along with its frequency count.
     * <p>
     * This class is utilized to store and manipulate data related to adjacent pairs of strings
     * in the context of tokenization and byte pair encoding (BPE) operations. It provides
     * an immutable representation of a pair of strings combined with its computed occurrence count.
     */
    private record PairCount(String pair, int count) {
    }

    /**
     * Splits a given string into a list of individual character tokens.
     *
     * @param marked the input string to be tokenized into character tokens
     * @return a list of character tokens derived from the input string
     */
    private static List<String> charTokens(String marked) {
        List<String> out = new ArrayList<>(marked.length());
        for (int i = 0; i < marked.length(); i++) out.add(String.valueOf(marked.charAt(i)));
        return out;
    }

    /**
     * Computes the occurrence frequency of consecutive token pairs in a list of tokens.
     * For each pair of adjacent tokens, it creates a combined key and calculates the
     * frequency of such pairs within the list.
     *
     * @param toks a list of tokens where consecutive pairs of tokens will be analyzed
     *             and their frequencies computed
     * @return a map where the keys are string representations of adjacent token pairs
     * joined by a single space, and the values are the corresponding frequencies
     */
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

    /**
     * Merges consecutive instances of the specified token pair (left, right) into a single token within the given list of tokens.
     * Each occurrence of the two tokens being consecutive in the list will be replaced by their concatenated form.
     *
     * @param toks  the list of tokens to be processed
     * @param left  the left token in the pair to be merged
     * @param right the right token in the pair to be merged
     * @return the number of substitutions made where the pair of tokens was merged
     */
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

    /**
     * Encodes the provided word frequencies into a compact representation using a variant
     * of the byte pair encoding (BPE) algorithm. The method reduces the vocabulary size by
     * merging token pairs iteratively until a specific vocabulary size or stopping condition is met.
     *
     * @param counts    a mapping of words to their respective frequencies, which determines
     *                  how often each token or subword appears in the dataset
     * @param vocabSize the desired size of the final vocabulary after performing the encoding;
     *                  the algorithm attempts to achieve this size by merging token pairs
     * @return an Encoding object that contains the final vocabulary, the sequence of merge
     * operations performed, the original character set, and the resulting tokens
     * @throws IllegalArgumentException if the input vocabulary (counts) is null or empty
     */
    public Encoding encode(HashMap<String, AtomicInteger> counts, int vocabSize) {
        if (counts == null || counts.isEmpty()) throw new IllegalArgumentException("Empty vocabulary.");
        long first = System.nanoTime();

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
                        checkDelta(pairCounts, pq, p, deltaOcc);

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

        long second = System.nanoTime();
        System.out.println("Encoding took " + ((second - first) / 1_000_000) + " ms " +
                "(merges=" + merges.size() + ", symbols=" + tokens.size() + ")");

        return new Encoding(vocabOut, merges, charset, tokens);
    }

    /**
     * Tokenizes a given word into a list of subword tokens using a byte pair encoding (BPE) process.
     * The method processes the input word by segmenting it into individual characters (or unknown tokens
     * if the character is not in the charset), optionally prepends a boundary marker, and applies merge
     * operations based on the provided rules to generate subword tokens.
     *
     * @param word     the input word to tokenize
     * @param merges   the list of predefined merge operations, in hierarchical order, used to combine tokens
     * @param charset  the set of allowed characters for initial tokenization; any characters
     *                 outside this set will be replaced by an unknown token
     * @param boundary a flag indicating whether a boundary marker ('_') should be prepended to the word
     *                 during tokenization
     * @return a list of subword tokens representing the tokenized word after applying the BPE algorithm
     */
    public List<String> tokenizeWord(String word, List<String> merges,
                                     HashSet<String> charset, boolean boundary) {
        final String unkToken = "<UNK>";
        String surface = boundary ? ("_" + word.toLowerCase()) : word.toLowerCase();

        List<String> toks = new ArrayList<>(surface.length());
        for (int i = 0; i < surface.length(); i++) {
            String c = String.valueOf(surface.charAt(i));
            toks.add(charset.contains(c) ? c : unkToken);
        }
        return getStringsMerges(merges, toks);
    }

    /**
     * Generates a rank mapping for a given list of merge operations. The rank mapping assigns
     * an integer index to each merge operation based on its position in the list.
     *
     * @param merges a list of merge operation strings where each operation is mapped to its
     *               corresponding rank (index) in the list
     * @return a HashMap where the keys are merge operation strings, and the values are their ranks
     * (indices) in the provided list
     */
    private static HashMap<String, Integer> ranksFor(List<String> merges) {
        return TOP_CACHE.computeIfAbsent(merges, m -> {
            HashMap<String, Integer> r = new HashMap<>(m.size() * 2);
            for (int i = 0; i < m.size(); i++) r.put(m.get(i), i); // "a b" -> rang i
            return r;
        });
    }

    /**
     * Tokenizes a given word into a list of subword tokens using a fast byte pair encoding (BPE) algorithm.
     * <p>
     * The method tokenizes the word by segmenting it into known characters and performing merges
     * according to the provided merge list and character set.
     *
     * @param word     the input word to tokenize
     * @param merges   the predefined list of merge operations sorted by rank
     * @param charset  the set of allowed characters for segmentation
     * @param boundary whether to prepend a boundary marker ('_') to the word before tokenizing
     * @return a list of subword tokens representing the tokenized word
     */
    public List<String> tokenizeWordFast(String word, List<String> merges,
                                         HashSet<String> charset, boolean boundary) {
        final String unkToken = "<UNK>";
        final String lower = word.toLowerCase();
        final String surface = boundary ? ("_" + lower) : lower;

        // cache
        final String cacheKey = (boundary ? "1" : "0") + "|" + System.identityHashCode(merges) + "|" + lower;
        List<String> cached = WORD_CACHE.get(cacheKey);
        if (cached != null) return cached;

        // segmentation into known chars
        ArrayList<String> toks = new ArrayList<>(surface.length());
        for (int i = 0; i < surface.length(); i++) {
            String c = String.valueOf(surface.charAt(i));
            toks.add(charset.contains(c) ? c : unkToken);
        }
        if (toks.size() < 2) {
            List<String> out = List.copyOf(toks);
            WORD_CACHE.put(cacheKey, out);
            return out;
        }

        // rank for merges
        HashMap<String, Integer> ranks = ranksFor(merges);

        // always take the best ranked pair
        while (true) {
            int bestRank = Integer.MAX_VALUE;
            int bestI = -1;

            // searches for the best ranked pair in the current segment
            for (int i = 0; i < toks.size() - 1; i++) {
                String key = toks.get(i) + " " + toks.get(i + 1);
                Integer r = ranks.get(key);
                if (r != null && r < bestRank) {
                    bestRank = r;
                    bestI = i;
                    if (bestRank == 0) break;
                }
            }
            if (bestI == -1) break; // no pair found -> done

            toks.set(bestI, toks.get(bestI) + toks.get(bestI + 1));
            toks.remove(bestI + 1);
        }
        List<String> out = List.copyOf(toks);
        WORD_CACHE.put(cacheKey, out);
        return out;
    }

    /**
     * Iteratively merges pairs of adjacent tokens in a list to create new tokens, based on a predefined
     * list of merge operations and their ranks. The method continues merging until no applicable pairs
     * are found or all merges are exhausted.
     *
     * @param merges the list of predefined merge operations, where each operation consists of two
     *               tokens to be merged
     * @param toks   the list of tokens to be processed and merged iteratively
     * @return the list of tokens after applying the merge operations
     */
    private List<String> getStringsMerges(List<String> merges, List<String> toks) {
        if (toks.size() < 2 || merges.isEmpty()) return toks;
        final HashMap<String, Integer> ranks = ranksFor(merges);

        while (true) {
            int bestRank = Integer.MAX_VALUE;
            int bestI = -1;

            // search for best pair in current token/segment
            for (int i = 0; i < toks.size() - 1; i++) {
                Integer r = ranks.get(toks.get(i) + " " + toks.get(i + 1));
                if (r != null && r < bestRank) {
                    bestRank = r;
                    bestI = i;
                    if (bestRank == 0) break;
                }
            }
            if (bestI == -1) break; // no pair found -> done

            toks.set(bestI, toks.get(bestI) + toks.get(bestI + 1));
            toks.remove(bestI + 1);
        }
        return toks;
    }

    /**
     * Encodes the given parameters to generate an encoding structure which includes the vocabulary,
     * merges, character set, and tokens based on the input word frequency counts and configuration parameters.
     *
     * @param counts       A map where the key is the word and the value is its frequency.
     *                     Must not be null or empty.
     * @param vocabSize    The desired vocabulary size after encoding. Determines the target size of the token set.
     * @param minPairFreq  The minimum frequency of a pair of tokens required for it to be considered for merging.
     * @param maxMergesCap The maximum number of merges allowed in the encoding process. Acts as a processing limit.
     * @param boundary     A flag indicating whether to treat words with underscores (e.g., "_word") as boundary-marked tokens.
     * @return An {@link Encoding} object containing the vocabulary mapping, merges list, character set, and final token set.
     * @throws IllegalArgumentException If the `counts` parameter is null or empty.
     */
    public Encoding encodeParam(HashMap<String, AtomicInteger> counts,
                                int vocabSize,
                                int minPairFreq,
                                int maxMergesCap,
                                boolean boundary) {
        if (counts == null || counts.isEmpty()) throw new IllegalArgumentException("Empty vocabulary.");
        long first = System.nanoTime();

        // init
        List<WordEntry> words = new ArrayList<>(counts.size());
        HashSet<String> charset = new HashSet<>();
        int wid = 0;
        for (var e : counts.entrySet()) {
            String surface = boundary ? ("_" + e.getKey()) : e.getKey();
            List<String> toks = charTokens(surface);
            charset.addAll(toks);
            words.add(new WordEntry(wid++, e.getValue().get(), toks));
        }

        HashMap<String, Integer> pairCounts = new HashMap<>();
        HashMap<String, HashSet<Integer>> pairToWords = new HashMap<>();
        for (WordEntry w : words) {
            Map<String, Integer> pm = pairMult(w.tokens);
            if (!pm.isEmpty()) {
                for (var pe : pm.entrySet()) {
                    int add = pe.getValue() * w.freq;
                    if (add > 0) {
                        pairCounts.merge(pe.getKey(), add, Integer::sum);
                        pairToWords.computeIfAbsent(pe.getKey(), _ -> new HashSet<>()).add(w.id);
                    }
                }
            }
        }

        PriorityQueue<PairCount> pq = new PriorityQueue<>(
                (a, b) -> Integer.compare(b.count, a.count)
        );
        for (var e : pairCounts.entrySet()) if (e.getValue() > 0) pq.offer(new PairCount(e.getKey(), e.getValue()));

        HashSet<String> tokens = new HashSet<>(charset);
        List<String> merges = new ArrayList<>();

        int want = Math.max(vocabSize, tokens.size());
        int budget = Math.min(maxMergesCap, Math.max(0, want - tokens.size()));

        for (int done = 0; done < budget; ) {
            PairCount top = null;
            while (!pq.isEmpty()) {
                PairCount cand = pq.poll();
                int cur = pairCounts.getOrDefault(cand.pair(), 0);
                if (cur != cand.count()) {
                    if (cur > 0) pq.offer(new PairCount(cand.pair(), cur));
                    continue;
                }
                top = cand;
                break;
            }
            if (top == null || top.count() < minPairFreq) break;

            String[] pr = top.pair().split(" ");
            String left = pr[0], right = pr[1];
            HashSet<Integer> wordIds = pairToWords.get(top.pair());
            if (wordIds == null || wordIds.isEmpty()) {
                pairCounts.put(top.pair(), 0);
                continue;
            }

            int totalRepl = 0;
            List<Integer> impacted = new ArrayList<>(wordIds);

            for (int id : impacted) {
                WordEntry w = words.get(id);
                Map<String, Integer> before = pairMult(w.tokens);
                int r = mergeInWord(w.tokens, left, right);
                totalRepl += r * w.freq;
                Map<String, Integer> after = pairMult(w.tokens);

                if (!before.equals(after)) {
                    HashSet<String> keys = new HashSet<>(before.keySet());
                    keys.addAll(after.keySet());
                    for (String p : keys) {
                        int delta = (after.getOrDefault(p, 0) - before.getOrDefault(p, 0)) * w.freq;
                        checkDelta(pairCounts, pq, p, delta);
                        if (after.getOrDefault(p, 0) > 0) {
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
            if (totalRepl == 0) break;

            merges.add(top.pair());
            tokens.add(left + right);
            done++;
        }

        int initialSymbols = charset.size();
//        if (vocabSize <= initialSymbols) {
        long second = System.nanoTime();
        // reconstruire le vocab (séquences espacées) sans aucun merge
        HashMap<String, Integer> vocabOut = new HashMap<>();
        for (WordEntry w : words) {
            String key = String.join(" ", w.tokens);
            vocabOut.merge(key, w.freq, Integer::sum);
        }
        System.out.println("Encoding(param) took " + ((second - first) / 1_000_000) + " ms " +
                " (merges=" + merges.size() + ", symbols=" + tokens.size() + ", boundary=" + boundary + ", minPair=" + minPairFreq + ")");
        return new Encoding(vocabOut, merges, charset, tokens);

//        System.err.println("Vocab Size is >= initial symbols");
//        return null;
    }

    /**
     * Updates the frequency count of a token pair and manages its presence in the priority queue.
     * If the resulting frequency of the pair becomes zero or less, it is removed from the map
     * and the priority queue. Otherwise, it updates its count and ensures it is added to the queue.
     *
     * @param pairCounts a map where keys represent token pairs and values represent their frequency counts
     * @param pq         a priority queue that manages token pairs based on their frequencies
     * @param p          the token pair to be updated
     * @param delta      the change in frequency to apply to the token pair
     */
    private void checkDelta(HashMap<String, Integer> pairCounts, PriorityQueue<PairCount> pq, String p, int delta) {
        if (delta != 0) {
            int nv = pairCounts.getOrDefault(p, 0) + delta;
            if (nv <= 0) pairCounts.remove(p);
            else pairCounts.put(p, nv);
            if (nv > 0) pq.offer(new PairCount(p, nv));
        }
    }
}