package core;

import java.util.Optional;
import java.util.Set;

@FunctionalInterface
public interface Dictionary {
    Optional<Set<Integer>> getDocuments(String term);
}
