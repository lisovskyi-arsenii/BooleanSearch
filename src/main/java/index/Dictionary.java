package index;

import java.io.Serializable;
import java.util.Optional;
import java.util.Set;

public interface Dictionary extends Serializable {
    Optional<Set<Integer>> getDocuments(String term);

    Set<String> getAllTerms();

    int size();

    int getTotalTermOccurrences();

    void clear();
}
