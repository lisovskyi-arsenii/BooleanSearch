package tokenization;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

@Slf4j
public final class Tokenizer {
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
//    private static final StopWordsFilter STOP_WORD_FILTER = new StopWordsFilter();

    private Tokenizer() {
        throw new UnsupportedOperationException("Instantiation of Tokenizer - Utility class");
    }

    public static List<String> tokenize(String content) {
        List<String> tokens = new ArrayList<>();

        String cleaned = PUNCTUATION_PATTERN.matcher(content).replaceAll("").toLowerCase();

        StringTokenizer tokenizer = new StringTokenizer(cleaned);
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken().trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

//        return STOP_WORD_FILTER.filter(tokens);
        return tokens;
    }
}
