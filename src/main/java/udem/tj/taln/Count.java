package udem.tj.taln;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The Count class is responsible for processing textual data, tracking word occurrences, and
 * analyzing their frequency. It provides methods to manage and reset state, process lines
 * from files incrementally, and display frequency counts in a sorted manner.
 */
public class Count {
    private final HashSet<String> types = new HashSet<>();
    private final HashMap<String, AtomicInteger> counts = new HashMap<>();
    private final AtomicInteger wordCount = new AtomicInteger(0);

    // track processed lines and buffered reader
    // buffered reader because of the large file size
    private BufferedReader bufferedReader = null;
    private int processedLines = 0;

    /**
     * Executes the processing of a specified number of lines from a given file and returns the
     * number of unique types encountered during the processing. This method tracks and processes
     * lines incrementally, ensuring that previously processed lines are skipped.
     *
     * @param exampleNumber the number of lines to process from the file (total = 228938)
     * @param file          the path to the file to be processed
     * @return the number of unique types encountered during the processing of the specified lines
     */
    public int execute(int exampleNumber, String file) {
        return count(exampleNumber, file);
    }

    private int count(int exampleNumber, String file) {
        try {
            if (bufferedReader == null) {
                bufferedReader = new BufferedReader(Utils.getReader(file));
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
                                    count(word.substring(0, word.length() - 1));
                                    count(word.charAt(word.length() - 1) + "");
                                    continue;
                                }
                                if ((word.endsWith("...") && word.length() > 3)
                                ) {
                                    count(word.substring(0, word.length() - 3));
                                    count("...");
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

    private void init(String word) {
        if (types.contains(word) && counts.containsKey(word)) return;
        types.add(word);
        counts.put(word, new AtomicInteger(0));
    }

    /**
     * Resets the state of the Count object by clearing all internal tracking structures and resources.
     * This method performs the following actions:
     * - Closes the bufferedReader if it is not null, handling any potential IOException.
     * - Sets the bufferedReader reference to null.
     * - Resets the processedLines counter to 0.
     * - Clears the types and counts collections.
     * - Resets the wordCount value to 0.
     */
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
        types.clear();
        counts.clear();
        wordCount.set(0);
    }

    /**
     * Prints the top entries from a frequency map, sorted by their frequency in descending order,
     * and then alphabetically for entries with the same frequency. The number of entries printed
     * is limited by the specified maximum number of lines.
     *
     * @param maxLines the maximum number of entries to be printed
     */
    public void entryToCmd(int maxLines) {
        TreeMap<String, AtomicInteger> tree = new TreeMap<>((a, b) -> {
            // sorting by value (=frequency)
            int compareValue = counts.get(b).get() - counts.get(a).get();
            return compareValue != 0 ? compareValue : a.compareTo(b);
        });
        tree.putAll(counts);

        int i = 0;
        for (var entry : tree.entrySet()) {
            System.out.printf("%s - %s\n", entry.getKey(), entry.getValue().get());
            if (++i == maxLines) break;
        }
    }

    private void count(String entry) {
        init(entry);
        counts.get(entry).incrementAndGet();
        wordCount.incrementAndGet();
    }

    /**
     * Retrieves the counts of words tracked by this instance.
     * This method returns a mapping of words to their corresponding counts
     * in the form of an AtomicInteger, allowing for thread-safe updates of word counts.
     *
     * @return a HashMap where the keys are words (strings) and the values are their respective counts (AtomicInteger).
     */
    public HashMap<String, AtomicInteger> getCounts() {
        return counts;
    }

    /**
     * Retrieves the total number of words tracked by this instance.
     *
     * @return the total number of words tracked by this instance
     */
    public int getWordCount() {
        return wordCount.get();
    }
}
