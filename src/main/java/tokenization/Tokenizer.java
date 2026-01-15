package tokenization;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Pattern;

public final class Tokenizer {
    private static final Logger LOGGER = LoggerFactory.getLogger(Tokenizer.class);
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]+");



    private Tokenizer() {
        throw new AssertionError("Instantiation of Tokenizer - Utility class");
    }

    public static List<String> tokenize(String content) {
        List<String> tokens = new ArrayList<>();

        String cleaned = PUNCTUATION_PATTERN.matcher(content).replaceAll("").toLowerCase();

        StringTokenizer tokenizer = new StringTokenizer(cleaned);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (!token.isEmpty() && !STOP_WORDS.contains(token)) {
                tokens.add(token);
            }
        }

        return tokens;
    }

    public static boolean isStopWord(String word) {
        return STOP_WORDS.contains(word.toLowerCase());
    }

    public static Set<String> getStopWords() {
        return Collections.unmodifiableSet(STOP_WORDS);
    }
}
