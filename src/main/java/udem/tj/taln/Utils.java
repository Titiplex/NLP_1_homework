package udem.tj.taln;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Utils {
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
}
