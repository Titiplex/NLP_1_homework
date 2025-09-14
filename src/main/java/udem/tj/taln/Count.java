package udem.tj.taln;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

public class Count {
    private final HashSet<String> types = new HashSet<>();
    private final HashMap<String, AtomicInteger> counts = new HashMap<>();
    private final AtomicInteger wordCount = new AtomicInteger(0);

    // track processed lines and buffered reader
    private BufferedReader bufferedReader = null;
    private int processedLines = 0;
    private boolean csvInitialized = false;

    /**
     *
     * @param exampleNumber Number of examples for the program to work on (total = 228938)
     * @return Returns the number of types (number of different words) in the corpus
     */
    public int execute(int exampleNumber, String file) {
        return count(exampleNumber, file);
    }

    private int count(int exampleNumber, String file) {
        try {
            if (bufferedReader == null) {
                bufferedReader = new BufferedReader(new InputStreamReader(
                        Objects.requireNonNull(Count.class.getResourceAsStream(file))));
                processedLines = 0;
            }

            // skip already processed lines if needed
            int linesToProcess = exampleNumber - processedLines;
            if (linesToProcess <= 0) {
                // already processed enough lines
                System.out.println("Using cached results.");
            } else {
                // process only the new lines
                for (int i = 0; i < linesToProcess; i++) {
                    if (bufferedReader.ready()) {
                        String line = bufferedReader.readLine();
                        if (line != null) {
                            for (String word : Utils.splitLine(line)) {
                                if (word.isEmpty()) continue;
                                if (word.length() > 1 &&
                                        (word.endsWith(".") ||
                                                word.endsWith("?") ||
                                                word.endsWith("!") ||
                                                word.endsWith(":") ||
                                                word.endsWith(";") ||
                                                word.endsWith(","))
                                ) {
                                    count(word.substring(0, word.length() - 2));
                                    count(word.charAt(word.length() - 1) + "");
                                    continue;
                                }
                                count(word);
                            }
                            processedLines++;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
        }
        return types.size();
    }

    public void printInCsvFormat() {
        try {
            int randomNumber = (int) (Math.random() * 1000000);
            File outputFile = new File("output/count/output" + randomNumber + ".csv");

            if (!csvInitialized) {
                // delete existing file when program starts
                if (outputFile.exists() && !outputFile.delete()) {
                    System.err.println("Error deleting existing output.csv file");
                }
                csvInitialized = true;
                System.out.println("Initializing output...");
            } else {
                System.out.println("Updating output...");
            }

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile, false))) {
                for (var entry : counts.entrySet()) {
                    writer.write(entry.getKey() + "\t" + entry.getValue().get() + "\n");
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void init(String word) {
        if (types.contains(word) && counts.containsKey(word)) return;
        types.add(word);
        counts.put(word, new AtomicInteger(0));
    }

    // method to reset state if needed
    public void reset() {
        if (bufferedReader != null) {
            try {
                bufferedReader.close();
            } catch (IOException e) {
                System.err.println("Error closing buffered reader: " + e.getMessage());
            }
        }
        bufferedReader = null;
        processedLines = 0;
        csvInitialized = false;
        types.clear();
        counts.clear();
        wordCount.set(0);
    }

    public void entryToCmd() {
        TreeMap<String, AtomicInteger> tree = new TreeMap<>(counts);
        for (var entry : tree.entrySet()) {
            System.out.printf("%s - %s\n", entry.getKey(), entry.getValue().get());
        }
    }

    private void count(String entry) {
        init(entry);
        counts.get(entry).incrementAndGet();
        wordCount.incrementAndGet();
    }

    public HashMap<String, AtomicInteger> getCounts() {
        return counts;
    }
}
