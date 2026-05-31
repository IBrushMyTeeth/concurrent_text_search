import java.nio.file.Path;

public class LocatedWord {

    // The extracted word content
    private final String word;

    // File where the word was found
    private final Path filepath;

    // Line number within the file where the word appears
    private final int line;

    // Creates a new record of a word with its source location
    public LocatedWord(String word, Path filepath, int line) {
        this.word = word;
        this.filepath = filepath;
        this.line = line;
    }

    public String getWord() {
        return word;
    }

    public Path getFilepath() {
        return filepath;
    }

    public int getLine() {
        return line;
    }

    // Returns a simple string representation for logging/debugging
    // Format: word:filePath:line
    @Override
    public String toString() {
        return word + ":" + filepath + ":" + line;
    }
}