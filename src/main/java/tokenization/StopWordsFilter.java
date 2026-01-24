package tokenization;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        addAllStopWordsToList();
    }

    public List<String> filter(List<String> tokens) {
        return tokens.stream()
                .filter(token -> !stopWords.contains(token))
                .toList();
    }

    public boolean isStopWord(String word) {
        return stopWords.contains(word);
    }

    private void addAllStopWordsToList() {
        try {
            FileReader.addContentToCollection(FILENAME_STOPWORDS, stopWords);
        } catch (IOException e) {
            log.error("Failed to add stop words to list.", e);
        }
    }

}
