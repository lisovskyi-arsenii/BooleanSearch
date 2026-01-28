package index;

import java.util.Optional;
import java.util.Set;

public interface Dictionary {
    Optional<Set<Integer>> getDocuments(String term);

    int size();

    int getTotalTermOccurrences();

    void clear();
}
