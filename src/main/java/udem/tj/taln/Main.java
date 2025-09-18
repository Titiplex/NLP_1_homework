package udem.tj.taln;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
 * click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
 */
public class Main {
    /**
     * Tip: To <b>Run</b> code, press <shortcut actionId="Run"/> or
     */
    public static void main(String[] args) {

        String file = "/Wikipedia/Wikipedia_CHARS/Wikipedia_CHARS.txt";
        Count count = new Count();
        // nécessaires pour les questions une et deux.
        block(count, 1000, 10, file, "Types for 1000 lines");

        testBpe(count, file);
        // test for 100,000 lines
        count = new Count();
        block(count, 100000, 1000, file, "Types for 100,000 lines");

        try {
            runAddonsTests(file, count);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Processes a given file to count words, generate statistics, and create a graph. This method
     * checks if output for the given subtitle already exists, performs a word count, measures execution
     * time, and saves the results in CSV format. Additionally, it generates a graph based on the
     * counting results.
     *
     * @param count      an object that contains methods for word frequency count and data handling.
     * @param lineNumber the total number of lines to process for word counting.
     * @param increment  the step increment for processing lines in the file.
     * @param file       the path to the input file containing the text data.
     * @param subtitle   a descriptive subtitle used for output file naming and graph labeling.
     */
    public static void block(Count count, int lineNumber, int increment, String file, String subtitle) {
        File outputFile = new File("output/count/output_" + subtitle + ".csv");
        if (outputFile.exists()) {
            System.out.println("Skipping a process already executed : " + subtitle);
            return;
        }
        System.out.println("Starting word counting...");

        // fetching data -> number of types per number of examples treated
        long first = System.nanoTime();
        Map<Integer, Integer> typeOnEx = new HashMap<>();
        for (int i = 0; i < lineNumber; i += increment) {
            typeOnEx.put(i, count.execute(i, file));
        }
        long second = System.nanoTime();
        System.out.println("-----------------------------------------------------------");
        System.out.println("Done!");
        System.out.println("Total Execution time: " + (second - first) / 1000000 + " ms");
        System.out.printf("Processed %d lines (%d words)\n", lineNumber, count.getWordCount());

        // creating graph
        try {
            Graph.graph(typeOnEx, subtitle, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        count.entryToCmd(10);
        List<List<String>> rows = new ArrayList<>();
        for (var count_ : count.getCounts().entrySet()) {
            rows.add(List.of(count_.getKey(), count_.getValue().get() + ""));
        }
        Utils.writeCsv(outputFile.getPath(), null, rows);
    }

    /**
     * Executes a test for Byte Pair Encoding (BPE) on text data using a set of word frequencies and samples
     * from a specified file. It performs two methods of tokenization, measures their performance, and writes the
     * results and encoding data to output files.
     *
     * @param count an object containing word frequency data used to generate BPE encoding.
     * @param file  the path to the file containing the text data to be tokenized.
     */
    private static void testBpe(Count count, String file) {
        File encodingFile = new File("output/bpe/encoding_BPE_test.csv");
        File output = new File("output/bpe/output_BPE_test.csv");
        if (encodingFile.exists() && output.exists()) {
            System.out.println("Skipping test BPE, already executed in a prior run");
            return;
        }
        if (encodingFile.getParentFile() != null && !encodingFile.getParentFile().exists()) encodingFile.getParentFile().mkdirs();
        if (output.getParentFile() != null && !output.getParentFile().exists()) output.getParentFile().mkdirs();
        // questions sur le BPE
        var wordCount = count.getCounts();
        System.out.println("Initalizing BPE...");
        BPE bpe = new BPE();
        BPE.Encoding encoding = bpe.encode(wordCount, wordCount.size());
        System.out.println("BPE initialized!");

        List<List<String>> bpeResults = new ArrayList<>();
        try {
            System.out.println("Processing sentences...");
            var sentences = getSentences(file, 1001, 50);
            System.out.println("Sentences fetched!");
            System.out.println("Testing 100 pages book tokenization method");
            System.out.println("Tokenizing sentences...");

            long time_bpe_first = System.nanoTime();
            for (var sentence : sentences) {
                List<String> result = new ArrayList<>();
                for (var word : sentence) {
                    result.addAll(bpe.tokenizeWord(word, encoding.merges(), encoding.charset(), true));
                }
                bpeResults.add(result);
            }
            long time_bpe_last = System.nanoTime();
            System.out.println("BPE time for " + sentences.size() + " sentences : " + (time_bpe_last - time_bpe_first) / 1000000 + " ms");

            sentences = getSentences(file, 1001, 1000);
            System.out.println("Sentences fetched!");
            System.out.println("Testing fast tokenization method");
            System.out.println("Tokenizing sentences...");

            time_bpe_first = System.nanoTime();
            for (var sentence : sentences) {
                List<String> result = new ArrayList<>();
                for (var word : sentence) {
                    result.addAll(bpe.tokenizeWordFast(word, encoding.merges(), encoding.charset(), true));
                }
                bpeResults.add(result);
            }
            time_bpe_last = System.nanoTime();
            System.out.println("fast BPE time for " + sentences.size() + " sentences : " + (time_bpe_last - time_bpe_first) / 1000000 + " ms");

            System.out.println("Writing results to file...");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output, false))) {
                for (var sentence : bpeResults) {
                    writer.write(String.join(" ", sentence) + "\n");
                }
            }
            System.out.println("Done!");

            System.out.println("Printing Encoding to file...");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(encodingFile, false))) {
                for (var merge : encoding.merges()) {
                    writer.write(merge + "\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * AI Assisted (correction of errors).
     * Processes the file by reading lines, applying a splitter function to extract types,
     * and generating statistics on the number of unique types encountered over a set number
     * of lines. Outputs the results in a CSV file
     *
     * @param label    the name of the variant being processed. Used for naming the output files.
     * @param file     the path to the file containing the text data to be tokenized.
     * @param maxLines the maximum number of lines to be processed.
     * @param step     the number of lines to be processed between each output.
     * @param splitter the function to be applied to each line to extract types.
     */
    private static void blockVariant(String label, String file, int maxLines, int step, Function<String, List<String>> splitter) {
        System.out.println("Counting variant = " + label);
        long first = System.nanoTime();
        Map<Integer, Integer> curve = new LinkedHashMap<>();
        HashSet<String> types = new HashSet<>();

        try (BufferedReader br = new BufferedReader(Utils.getReader(file))) {

            String line;
            int lineNo = 0;
            int nextMark = step;
            curve.put(0, 0);
            while ((line = br.readLine()) != null && lineNo < maxLines) {
                lineNo++;
                types.addAll(splitter.apply(line));
                if (lineNo >= nextMark) {
                    curve.put(lineNo, types.size());
                    nextMark += step;
                }
            }
            if (!curve.containsKey(lineNo)) curve.put(lineNo, types.size());

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // CSV
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("examples", "types"));
        for (var e : curve.entrySet()) rows.add(List.of(String.valueOf(e.getKey()), String.valueOf(e.getValue())));
        Utils.writeCsv("output/count/types-tokens" + label + ".csv", null, rows);

        // graphs
        try {
            Graph.graph(curve, label, false);
        } catch (IOException e) {
            System.err.println("Graph failed for " + label + ": " + e.getMessage());
        }
        long second = System.nanoTime();
        System.out.println("Variant " + label + " done in " + ((second - first) / 1000000) + " ms");
    }

    /**
     * Reads a text file and extracts sentences based on specified parameters.
     * The method processes the input file line by line, tokenizing characters
     * and identifying sentences based on punctuation marks (e.g., '.', '?', '!').
     * It skips initial lines as specified, and limits the result to a given number of sentences.
     *
     * @param file            the path to the input text file to be processed.
     * @param firstLine       the number of initial lines to skip in the file before processing.
     * @param numberSentences the maximum number of sentences to extract from the file.
     * @return a list of sentences, where each sentence is represented as a list of tokenized words and punctuation symbols.
     * @throws IOException if an I/O error occurs while reading the file.
     */
    private static List<List<String>> getSentences(String file, int firstLine, int numberSentences) throws IOException {
        // large buffer
        try (BufferedReader br = new BufferedReader(Utils.getReader(file), 1 << 20)) {
            // skip first lines (ex: those used for the encoding)
            for (int i = 0; i < firstLine; i++) {
                if (br.readLine() == null) break;
            }
            List<List<String>> sentences = new ArrayList<>(numberSentences);
            List<String> cur = new ArrayList<>(32);
            StringBuilder tok = new StringBuilder(32);
            String line;
            outer:
            // outer for breaking loops even while in nested loops
            while (sentences.size() < numberSentences && (line = br.readLine()) != null) {
                if (line.isEmpty()) continue;

                final int n = line.length();
                for (int i = 0; i < n; i++) {
                    // chars processing
                    char c = Character.toLowerCase(line.charAt(i));
                    if (c >= '0' && c <= '9') {
                        if (!tok.isEmpty()) {
                            cur.add(tok.toString());
                            tok.setLength(0);
                        }
                        cur.add("@");
                        continue;
                    }
                    if (c == '"' || c == '(' || c == ')' || c == '{' || c == '}' || c == '[' || c == ']' || c == '\t')
                        continue;
                    if (c == ' ') {
                        if (!tok.isEmpty()) {
                            cur.add(tok.toString());
                            tok.setLength(0);
                        }
                        continue;
                    }
                    if (c == '.' || c == '?' || c == '!' || c == ',' || c == ':' || c == ';') {
                        if (!tok.isEmpty()) {
                            cur.add(tok.toString());
                            tok.setLength(0);
                        }
                        if (c == '.' && i + 2 < n && line.charAt(i + 1) == '.' && line.charAt(i + 2) == '.') {
                            cur.add("...");
                            i += 2;
                        } else cur.add(String.valueOf(c));
                        if (c == '.' || c == '?' || c == '!') {
                            sentences.add(cur);
                            cur = new ArrayList<>(32);
                            if (sentences.size() == numberSentences) break outer;
                        }
                        continue;
                    }
                    tok.append(c);
                }
                if (!tok.isEmpty()) {
                    cur.add(tok.toString());
                    tok.setLength(0);
                }
            }
            // adding last useful unfinished sentence
            if (!cur.isEmpty() && sentences.size() < numberSentences) sentences.add(cur);
            return sentences;
        }
    }

    /**
     * AI assisted (suggested to improve code readability).
     * <p>
     * Represents statistical data related to tokenization processes.
     * This class stores various aggregated metrics, including average subwords per word,
     * statistics for token splits across different segment counts, and timing information.
     */
    private static class TokStats {
        double avgSubwordsPerWord;
        Map<Integer, Double> pctByNSegments = new LinkedHashMap<>();
        Map<Integer, Double> decileAvg = new LinkedHashMap<>();
        long tokenizeMillis;
        int sampleWords;
    }

    /**
     * AI assisted (correction of errors and improvements).
     * <p>
     * Computes tokenization statistics for a given set of word counts using a specified tokenizer.
     * The method calculates the average subwords per word, tokenization timing, distribution
     * of tokens by segment size, and decile averages for tokenization results. It returns
     * a TokStats object containing these aggregated statistics.
     *
     * @param counts       a HashMap containing word counts, where keys are words and values are their frequencies as AtomicInteger.
     * @param tokenizer    a Function that takes a string and returns a list of tokens, used to tokenize the words in the input.
     * @param timingSample an integer specifying the number of samples to tokenize for timing purposes.
     * @return a TokStats object containing computed statistics, including average subwords per word, tokenization timing, and distribution metrics.
     */
    private static TokStats computeTokenizationStats(HashMap<String, AtomicInteger> counts,
                                                     Function<String, List<String>> tokenizer,
                                                     int timingSample) {
        List<Map.Entry<String, AtomicInteger>> entries = new ArrayList<>(counts.entrySet());
        entries.sort((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()));

        long t0 = System.nanoTime();
        int n = Math.min(timingSample, entries.size());
        for (int i = 0; i < n; i++) tokenizer.apply(entries.get(i).getKey());
        long ms = (System.nanoTime() - t0) / 1000000;

        long totalSeg = 0, totalWords = 0;
        Map<Integer, Integer> bucket = new HashMap<>();
        for (var e : entries) {
            int segs = tokenizer.apply(e.getKey()).size();
            totalSeg += segs;
            totalWords++;
            bucket.merge(segs, 1, Integer::sum);
        }

        TokStats s = new TokStats();
        s.sampleWords = n;
        s.tokenizeMillis = ms;
        s.avgSubwordsPerWord = totalWords == 0 ? 0.0 : (double) totalSeg / totalWords;

        int N = entries.size();
        SortedSet<Integer> keys = new TreeSet<>(bucket.keySet());
        for (int k : keys) s.pctByNSegments.put(k, 100.0 * bucket.get(k) / Math.max(1, N));

        for (int d = 1; d <= 10; d++) {
            int start = (int) Math.floor((d - 1) / 10.0 * N);
            int end = (int) Math.floor(d / 10.0 * N);
            if (end <= start) {
                s.decileAvg.put(d, Double.NaN);
                continue;
            }
            double sum = 0;
            int cnt = 0;
            for (int i = start; i < end; i++) {
                sum += tokenizer.apply(entries.get(i).getKey()).size();
                cnt++;
            }
            s.decileAvg.put(d, sum / Math.max(1, cnt));
        }
        return s;
    }

    /**
     * AI assisted (correction of errors and improvements).
     * <p>
     * Tokenizes a given word based on a set of merges and a character set,
     * and optionally adds a boundary marker to the word. The method first
     * breaks the word into individual
     *
     * @param word     the word to be tokenized.
     * @param merges   the list of merges to be applied to the word.
     * @param charset  the set of characters that can be used in the word.
     * @param boundary whether to add a boundary marker to the word.
     */
    private static List<String> tokenizeWithMergesFile(String word, List<String> merges, Set<String> charset, boolean boundary) {
        final String UNK = "<UNK>";
        String s = boundary ? ("_" + word.toLowerCase()) : word.toLowerCase();
        List<String> toks = new ArrayList<>(s.length());
        for (int i = 0; i < s.length(); i++) {
            String c = String.valueOf(s.charAt(i));
            toks.add(charset.contains(c) ? c : UNK);
        }
        for (String m : merges) {
            String line = m.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] sp = line.split("\\s+");
            if (sp.length != 2) continue;
            String left = sp[0], right = sp[1];
            int i = 0;
            while (i < toks.size() - 1) {
                if (toks.get(i).equals(left) && toks.get(i + 1).equals(right)) {
                    toks.set(i, left + right);
                    toks.remove(i + 1);
                } else i++;
            }
        }
        return toks;
    }

    /**
     * Reads a file containing merge rules and returns a list of strings where each string is a line from the file.
     * If the specified file does not exist or an error occurs while reading, an empty list is returned.
     *
     * @param path the path to the file containing the merge rules.
     * @return a list of strings representing the lines of the file, or an empty list if the file does not exist or cannot be read.
     */
    private static List<String> readMergesFile(String path) {
        List<String> out = new ArrayList<>();
        File f = new File(path);
        if (!f.isFile()) return out;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) out.add(line);
        } catch (IOException e) {
            System.err.println("Cannot read merges file " + path + ": " + e.getMessage());
        }
        return out;
    }

    /**
     * Extracts a set of unique characters present in the provided list of merge rules.
     * Each merge rule is processed to identify the characters used, and comments or invalid lines are ignored.
     *
     * @param merges a list of strings representing merge rules. Each rule should contain two tokens separated by whitespace.
     *               Lines starting with a '#' or empty lines are ignored.
     * @return a set of unique characters found in the valid merge rules.
     */
    private static Set<String> charsetFromMerges(List<String> merges) {
        HashSet<String> cs = new HashSet<>();
        for (String m : merges) {
            String line = m.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] sp = line.split("\\s+");
            if (sp.length != 2) continue;
            for (char c : sp[0].toCharArray()) cs.add(String.valueOf(c));
            for (char c : sp[1].toCharArray()) cs.add(String.valueOf(c));
        }
        return cs;
    }

    /**
     * AI assisted (correction of errors, suggestions and improvements).
     * <p>
     * Executes a series of tests focused on processing various word segmentation configurations,
     * applies Byte Pair Encoding (BPE) for subword tokenization experiments, and evaluates
     * aspects such as tokenization statistics, segmentation percentages, and external comparisons.
     *
     * @param file  the path to the file containing input data.
     * @param count an instance of the Count class providing word frequency counts and associated methods.
     * @throws IOException if I/O operations such as reading or writing files encounter issues.
     */
    private static void runAddonsTests(String file, Count count) throws IOException {

        // processing variants
        Utils.SplitConfig keepHyphen = new Utils.SplitConfig();
        keepHyphen.keepHyphen = true;
        Utils.SplitConfig keepApostropheAndSplitClitics = new Utils.SplitConfig();
        keepApostropheAndSplitClitics.keepApostrophe = true;
        keepApostropheAndSplitClitics.splitClitics = true;

        blockVariant("keepHyphen", file, 1000, 10, line -> Utils.splitLine(line, keepHyphen));
        blockVariant("apostrophe_split_clitics", file, 1000, 10, line -> Utils.splitLine(line, keepApostropheAndSplitClitics));

        HashMap<String, AtomicInteger> counts = count.getCounts();

        // BPE
        BPE bpe = new BPE();
        int[] V = {1000, 5000, 10000};
        int[] MIN = {2, 5, 10};
        boolean[] BOUND = {true, false};

        // reduce vocab to most frequent types
        final int TOP_K = 50000;
        HashMap<String, AtomicInteger> countsTop = new HashMap<>(Math.min(TOP_K, counts.size()));
        counts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(TOP_K)
                .forEach(e -> countsTop.put(e.getKey(), new AtomicInteger(e.getValue().get())));

        List<List<String>> trainProfile = new ArrayList<>();
        trainProfile.add(List.of("label", "vocabTarget", "minPairFreq", "boundary", "train_ms", "merges"));

        for (int v : V)
            for (int m : MIN)
                for (boolean bound : BOUND) {
                    String label = "V" + v + "_M" + m + "_B" + (bound ? "1" : "0");
                    String spec_file = "output/bpe/merges" + label + ".txt";
                    if (new File(spec_file).exists()) {
                        System.out.println("Skipping file, already ran in previous run : " + spec_file);
                        continue;
                    }
                    long enc_first = System.nanoTime();
                    BPE.Encoding enc = bpe.encodeParam(countsTop, v, m, 20000, bound);
                    long enc_last = System.nanoTime();
                    // save merges
                    List<List<String>> mergeRows = new ArrayList<>();
                    for (String line : enc.merges()) mergeRows.add(List.of(line));
                    Utils.writeCsv(spec_file, null, mergeRows);

                    // profil line
                    trainProfile.add(List.of(
                            label, String.valueOf(v), String.valueOf(m), String.valueOf(bound),
                            ((enc_last - enc_first) / 1000000) + "",
                            String.valueOf(enc.merges().size())
                    ));
                }
        Utils.writeCsv("output/bpe/train_profile.csv", null, trainProfile);
        System.out.println("Train profile written to csv.");

        // tokenization stats
        BPE.Encoding enc = bpe.encodeParam(countsTop, 10000, 5, 20000, true);

        System.out.println("Tokenizing for stats");
        TokStats stats = computeTokenizationStats(
                counts,
                w -> bpe.tokenizeWordFast(w, enc.merges(), enc.charset(), true),
                50000
        );

        System.out.println("Overview");
        // overview
        List<List<String>> ov = new ArrayList<>();
        ov.add(List.of("metric", "value"));
        ov.add(List.of("avg_subwords_per_word", String.format(java.util.Locale.US, "%.4f", stats.avgSubwordsPerWord)));
        ov.add(List.of("tokenize_ms_on" + stats.sampleWords + "_words", String.valueOf(stats.tokenizeMillis)));
        Utils.writeCsv("output/bpe/tokenization_stats_overview.csv", null, ov);

        System.out.println("Percentages for segments");
        // percentages for segments
        List<List<String>> pct = new ArrayList<>();
        pct.add(List.of("n_segments", "pct_words"));
        for (var e : stats.pctByNSegments.entrySet())
            pct.add(List.of(String.valueOf(e.getKey()), String.format(Locale.US, "%.4f", e.getValue())));
        Utils.writeCsv("output/addons/pct_by_n_segments.csv", null, pct);

        System.out.println("Deciles");
        // deciles
        List<List<String>> dec = new ArrayList<>();
        dec.add(List.of("decile", "avg_subwords"));
        for (var e : stats.decileAvg.entrySet())
            dec.add(List.of(String.valueOf(e.getKey()), String.format(Locale.US, "%.4f", e.getValue())));
        Utils.writeCsv("output/addons/avg_subwords_by_decile.csv", null, dec);

        System.out.println("Tokenized sentences for inspection");
        // tokenized sentences for inspection
        List<List<String>> sentences = getSentences(file, 1001, 1000);
        List<List<String>> bpeSent = new ArrayList<>();
        for (var s : sentences) {
            List<String> toks = new ArrayList<>();
            for (String w : s) toks.addAll(bpe.tokenizeWord(w, enc.merges(), enc.charset(), true));
            bpeSent.add(List.of(String.join(" ", toks)));
        }
        Utils.writeCsv("output/bpe/tokenized_sentences.csv", null, bpeSent);

        System.out.println("External comparison");
        // external comparison
        String hfPath = "hf_merges.txt";
        List<String> hfM = readMergesFile(hfPath);
        if (!hfM.isEmpty()) {
            Set<String> hfCharset = charsetFromMerges(hfM);

            var topWords = counts.entrySet().stream()
                    .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                    .limit(2000).map(java.util.Map.Entry::getKey).toList();

            List<List<String>> cmp = new ArrayList<>();
            cmp.add(List.of("word", "len_bpe", "len_hf"));
            for (String w : topWords) {
                int lb = bpe.tokenizeWord(w, enc.merges(), enc.charset(), true).size();
                int lh = tokenizeWithMergesFile(w, hfM, hfCharset, true).size();
                cmp.add(List.of(w, String.valueOf(lb), String.valueOf(lh)));
            }
            Utils.writeCsv("output/addons/compare_hf_len.csv", null, cmp);
        } else {
            System.out.println("HF comparison skipped (no " + hfPath + ").");
        }
    }
}