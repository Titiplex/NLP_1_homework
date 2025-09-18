package udem.tj.taln;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A utility class providing various helper functions, such as text processing, file reading,
 * and CSV writing utilities. It includes methods for splitting strings into processed words,
 * obtaining readers for input files, and writing CSV files, as well as a configuration class for customizing
 * text-splitting behavior.
 */
public class Utils {

    /**
     * Splits a given line of text into a list of cleaned words. This method removes empty strings,
     * processes each word by cleaning out punctuation, replacing numbers with '@', and normalizing the text to lowercase.
     * Punctuation and unwanted characters are excluded.
     *
     * @param line the input line of text to be split and processed
     * @return a list of strings representing the cleaned words from the input line
     */
    public static List<String> splitLine(String line) {
        List<String> example = new ArrayList<>(Arrays.asList(line.split(" ")));
        example.removeIf(String::isEmpty);
        List<String> lineWords = new ArrayList<>();

        for (String word : example) {
            List<Character> num = Arrays.asList('0', '1', '2', '3', '4', '5', '6', '7', '8', '9');
            List<Character> punctuation = Arrays.asList('"', '(', ')', '{', '}', '-', '[', ']', '\t');
            List<Character> processed = new ArrayList<>();

            // cleaning the entry
            for (char c : word.toLowerCase().toCharArray()) {
                if (num.contains(c)) {
                    c = '@';
                    lineWords.add(c + "");
                    continue;
                }
                if (punctuation.contains(c)) continue;
                processed.add(c);
            }

            // counting the string made of the concat' of all chars
            lineWords.add(processed.stream().map(Object::toString).reduce("", String::concat));
        }
        return lineWords;
    }

    /**
     * Function debugged with LLM.
     * <p>
     * Attempts to retrieve an InputStreamReader for the specified file. The method first tries to locate the file
     * as a classpath resource (with and without a leading slash). If not found, it checks several other locations,
     * including external file paths, the current working directory, and predefined common directories.
     *
     * @param file the file name or path to locate and open as an InputStreamReader
     * @return an InputStreamReader for the specified file if found
     * @throws RuntimeException if the file cannot be found or an I/O error occurs during the process
     */
    public static InputStreamReader getReader(String file) {
        try {
            // First, try as classpath resource (with leading slash)
            InputStream resourceStream = Utils.class.getResourceAsStream(file);
            if (resourceStream != null) {
                return new InputStreamReader(resourceStream);
            }

            // Try as classpath resource without leading slash
            if (file.startsWith("/")) {
                resourceStream = Utils.class.getResourceAsStream(file.substring(1));
                if (resourceStream != null) {
                    return new InputStreamReader(resourceStream);
                }
            }

            // Try as external file with absolute path
            File externalFile = new File(file);
            if (externalFile.exists() && externalFile.isFile()) {
                return new InputStreamReader(new FileInputStream(externalFile));
            }

            // Try relative to current working directory
            File relativeFile = new File(System.getProperty("user.dir"), file.startsWith("/") ? file.substring(1) : file);
            if (relativeFile.exists() && relativeFile.isFile()) {
                return new InputStreamReader(new FileInputStream(relativeFile));
            }

            // Try in common data directories
            String[] commonPaths = {
                    "src/main/resources" + file,
                    "data" + file,
                    "resources" + file,
                    "." + file
            };

            for (String path : commonPaths) {
                File commonFile = new File(path);
                if (commonFile.exists() && commonFile.isFile()) {
                    return new InputStreamReader(new FileInputStream(commonFile));
                }
            }

            throw new FileNotFoundException("File not found in classpath, external path, or common locations: " + file +
                    "\nSearched locations:" +
                    "\n- Classpath: " + file +
                    "\n- Absolute path: " + file +
                    "\n- Relative to working directory: " + relativeFile.getAbsolutePath() +
                    "\n- Common data directories");
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Unable to read file: " + file, e);
        }
    }

    /**
     * Writes a CSV file to the specified path with the given header and rows.
     * Each row and the optional header are joined into a single string using spaces and then written line by line.
     * Any invalid path characters will be sanitized and replaced with underscores.
     *
     * @param path   the destination file path where the CSV will be written
     * @param header a list of strings representing the columns in the header row, can be null or empty for no header
     * @param rows   a list of lists of strings, where each inner list represents a single row of data to be written
     * @throws RuntimeException if an I/O error occurs during the write process
     */
    public static void writeCsv(String path, List<String> header, List<java.util.List<String>> rows) {
        try {
            File f = new File(path.toLowerCase().replaceAll(" \\.,:;\\?!", "_"));
            File parent = f.getParentFile();
            if (parent != null) parent.mkdirs();
            try (BufferedWriter w = new BufferedWriter(new FileWriter(f, false))) {
                if (header != null && !header.isEmpty()) w.write(String.join(" ", header) + "\n");
                for (var r : rows) w.write(String.join(" ", r) + "\n");
            }
        } catch (IOException e) {
            throw new RuntimeException("CSV write failed: " + path, e);
        }
    }

    /**
     * The SplitConfig class provides configuration options for text splitting and word processing
     * when using the associated utility methods. It allows you to customize how text lines are
     * parsed and modified, such as handling case transformation, digit replacement, punctuation
     * retention, and the splitting of clitics.
     * <p>
     * Fields:
     * - lowercase: Indicates whether all words should be converted to lowercase.
     * - digitsToAt: Specifies if numeric digits should be replaced with the '@' character.
     * - keepHyphen: Determines whether hyphens should be retained in the processed text.
     * - keepApostrophe: Specifies whether apostrophes should be preserved in the processed text.
     * - splitClitics: Indicates whether clitics (e.g., contractions) should be separated into
     * individual words during processing.
     * <p>
     * This configuration class is intended to be used with utility methods such as
     * {@link Utils#splitLine(String, SplitConfig)} to control the behavior of text processing.
     * By modifying the fields in this class, users can adjust the processing rules as needed
     * for their specific use case.
     */
    public static final class SplitConfig {
        public boolean lowercase = true;
        public boolean digitsToAt = true;
        public boolean keepHyphen = false;
        public boolean keepApostrophe = true;
        public boolean splitClitics = false;
    }

    /**
     * Splits a given line of text into a list of processed words based on the specified configuration.
     * The method cleans the input line by removing unnecessary characters, optionally converting text
     * to lowercase, replacing digits with '@', and splitting clitics when configured. It excludes empty
     * strings and manages punctuation and special characters based on the provided configuration.
     *
     * @param line a string representing the line of text to be processed and split
     * @param cfg  an instance of SplitConfig that defines the rules for processing the input line
     * @return a list of strings containing the words processed from the input line based on the provided configuration
     */
    public static List<String> splitLine(String line, SplitConfig cfg) {
        if (line == null || line.isEmpty()) return List.of();
        if (!cfg.keepApostrophe && cfg.splitClitics)
            System.err.println("Will not split clitics as apostrophes are discarded.");
        String s = cfg.lowercase ? line.toLowerCase() : line;

        List<String> out = new ArrayList<>();
        for (String raw : s.split(" ")) {
            if (raw.isEmpty()) continue;

            StringBuilder token = new StringBuilder(raw.length());
            for (int i = 0; i < raw.length(); i++) {
                char c = raw.charAt(i);

                if (Character.isDigit(c)) {
                    if (cfg.digitsToAt) out.add("@");
                    else token.append(c);
                    continue;
                }
                if (c == '\'' && cfg.keepApostrophe) {
                    token.append(c);
                    continue;
                }
                if (c == '-' && cfg.keepHyphen) {
                    token.append(c);
                    continue;
                }

                if ("\"(){}[]\t".indexOf(c) >= 0) continue;

                if (".?!,:;".indexOf(c) >= 0) {
                    if (!token.isEmpty()) {
                        out.add(token.toString());
                        token.setLength(0);
                    }
                    out.add(String.valueOf(c));
                    continue;
                }

                token.append(c);

                if (cfg.splitClitics && cfg.keepApostrophe && c == '\'') {
                    if (!token.isEmpty()) out.add(token.toString());
                    token = new StringBuilder(raw.length() - i);
                }
            }
            if (!token.isEmpty()) out.add(token.toString());
        }
        out.removeIf(String::isEmpty);
        return out;
    }
}
