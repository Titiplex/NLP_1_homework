package udem.tj.taln;

import java.io.*;
import java.util.*;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    /**
     * Tip: To <b>Run</b> code, press <shortcut actionId="Run"/> or
     */
    public static void main(String[] args) {

        String file = "/Wikipedia/Wikipedia_CHARS/Wikipedia_CHARS.txt";
        Count count = new Count();
        // nécessaires pour les questions une et deux.
        block(count, 1000, 10, file);

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
            System.out.println("Tokenizing sentences...");

            for (var sentence : sentences) {
                List<String> result = new ArrayList<>();
                for (var word : sentence) {
                    result.addAll(bpe.tokenizeWord(word, encoding.merges(), encoding.charset()));
                }
                bpeResults.add(result);
            }

            System.out.println("Done!");
            System.out.println("Writing results to file...");

            int randomNumber = new Random().nextInt(1000000);
            File output = new File("output/bpe/output_BPE" + randomNumber + ".csv");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(output, false))) {
                for (var sentence : bpeResults) {
                    writer.write(String.join(" ", sentence) + "\n");
                }
            }
            System.out.println("Done!");

            System.out.println("Printing Encoding to file...");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter("output/bpe/encoding_BPE" + randomNumber + ".csv", false))) {
                for (var merge : encoding.merges()) {
                    writer.write(merge + "\n");
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void block(Count count, int lineNumber, int increment, String file) {
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

        // creating graph
        try {
            Graph.graph(typeOnEx, false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        // count.entryToCmd();

        count.printInCsvFormat();
    }

    private static List<List<String>> getSentences(String file, int firstLine, int numberSentences) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(Count.class.getResourceAsStream(file)), java.nio.charset.StandardCharsets.UTF_8))) {

            List<List<String>> sentences = new ArrayList<>(numberSentences);
            String line;
            int lineNo = 0;

            while (lineNo < firstLine && br.readLine() != null) {
                lineNo++;
            }

            List<String> cur = new ArrayList<>();
            while (sentences.size() < numberSentences && (line = br.readLine()) != null) {
                if (line.isEmpty()) continue;

                List<String> split = Utils.splitLine(line);
                for (String word : split) {
                    cur.add(word);
                    if (word.equals(".") || word.equals("?") || word.equals("!")) {
                        sentences.add(cur);
                        cur = new ArrayList<>();
                        if (sentences.size() == numberSentences) break;
                    }
                }
            }

            if (!cur.isEmpty() && sentences.size() < numberSentences) sentences.add(cur);

            return sentences;
        }
    }
}