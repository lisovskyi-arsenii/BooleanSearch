package tokenization;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.tartarus.snowball.ext.EnglishStemmer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
public final class Tokenizer {
    private static final Pattern PUNCTUATION_PATTERN = Pattern.compile("[^\\p{L}\\p{N}\\s]+");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final ThreadLocal<EnglishStemmer> STEMMER =
        ThreadLocal.withInitial(EnglishStemmer::new);

    private static final int MAX_CACHE_SIZE = 50_000;
    private static final Cache<String, String> STEM_CACHE = Caffeine.newBuilder()
            .maximumSize(MAX_CACHE_SIZE)
            .build();

    private Tokenizer() {
        throw new UnsupportedOperationException("Instantiation of Tokenizer - Utility class");
    }

    public static List<String> tokenize(String content) {
        if (content == null || content.isBlank()) {
            return new ArrayList<>();
        }

        StringBuilder sb = new StringBuilder(64);

        String cleaned = PUNCTUATION_PATTERN.matcher(content)
                .replaceAll("")
                .toLowerCase(Locale.ROOT);

        String trimmed = cleaned.trim();
        if (trimmed.isEmpty()) {
            return new ArrayList<>();
        }

        String[] words = WHITESPACE_PATTERN.split(trimmed);
        List<String> tokens = new ArrayList<>(words.length);

        for (String word : words) {
            if (!word.isBlank()) {
                tokens.add(stem(word));
            }
        }

        return tokens;
    }

    private static String stem(String word) {
        return STEM_CACHE.get(word, key -> {
           EnglishStemmer stemmer = STEMMER.get();
           stemmer.setCurrent(key);
           stemmer.stem();
           return stemmer.getCurrent();
        });
    }
}
