package index;

import document.Document;
import document.DocumentRegistry;
import statistics.DictionaryStats;

public class IndexStatistics {
    public DictionaryStats getStatistics(InvertedIndex index,
                                         DocumentRegistry registry) {
        return null;
    }

    public int termFrequency(String term, InvertedIndex index) {
        return 0;
    }

}
