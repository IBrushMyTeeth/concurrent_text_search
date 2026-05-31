import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class WordUtils {

    // Utility class: prevent instantiation
    private WordUtils() {}

    // Vowel set used for consonant classification, including Nordic vowels
    private static final Set<Character> vowels = Set.of(
        'a','e','i','o','u','A','E','I','O','U',
        'æ','ø','å','Æ','Ø','Å'
    );

    // Extracts word tokens from text using locale-aware word boundaries
    // Only keeps tokens that start with a letter or digit
    public static List<String> extractWords(String text) {

        // Set up the BreakIterator
        BreakIterator it = BreakIterator.getWordInstance();
        it.setText(text);

        // Local list to collect valid words
        List<String> words = new ArrayList<>();

        int start = it.first();
        int end = it.next();

        while (end != BreakIterator.DONE) {
            String current = text.substring(start, end);

            // Filter out punctuation/whitespace tokens
            if (!current.isEmpty() &&
                Character.isLetterOrDigit(current.charAt(0))) {
                words.add(current);
            }

            start = end;
            end = it.next();
        }

        return words;
    }

    // Counts consonant letters in a word (non-letters are ignored)
    // Uses a predefined vowel set to determine consonants
    public static int countConsonants(String word) {
        int count = 0;

        // Iterate through the word
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);

            // Count only letters that are not vowels
            if (Character.isLetter(c) && !vowels.contains(c)) {
                count++;
            }
        }

        return count;
    }
}