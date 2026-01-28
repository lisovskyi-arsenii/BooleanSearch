package tokenization;

import lombok.extern.slf4j.Slf4j;
import util.FileReader;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
public class StopWordsFilter {
    private static final String FILENAME_STOPWORDS = "stopwords.txt";
    private final Set<String> stopWords = new HashSet<>();

    public StopWordsFilter() {
        loadStopWords();
    }

    public List<String> filter(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return tokens;
        }

        return tokens.stream()
                .filter(token -> !stopWords.contains(token.toLowerCase()))
                .toList();
    }

    public boolean isStopWord(String word) {
        return word != null && stopWords.contains(word.toLowerCase());
    }

    public int size() {
        return stopWords.size();
    }

    private void loadStopWords() {
        try {
            FileReader.addContentToCollection(FILENAME_STOPWORDS, stopWords);

            if (stopWords.isEmpty()) {
                log.warn("Stop words file is empty or not found, using default set");

                loadDefaultStopWords();
            }
        } catch (IOException e) {
            log.error("Failed to add stop words to list", e);
            loadDefaultStopWords();
        }
    }
    private void loadDefaultStopWords() {
        stopWords.addAll(Set.of(
                "a", "an", "and", "are", "as", "at", "be", "but", "by",
                "for", "if", "in", "into", "is", "it", "no", "not", "of",
                "on", "or", "such", "that", "the", "their", "then", "there",
                "these", "they", "this", "to", "was", "were", "will", "with"
        ));
        log.info("Loaded {} default stop words", stopWords.size());
    }
}
