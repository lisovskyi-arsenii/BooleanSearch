package core;

import enums.SearchStructureType;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public interface SearchEngine {
    void indexDocuments(String directory) throws IOException;
    Optional<Set<Integer>> search(String term, SearchStructureType type);
    Optional<Set<Integer>> andSearch(String term1, String term2, SearchStructureType type);
    Optional<Set<Integer>> orSearch(String term1, String term2, SearchStructureType type);
    Optional<Set<Integer>> notSearch(String term, Set<Integer> docIds, SearchStructureType type);
    void saveIndex(String filepath, String format) throws IOException;
    void loadIndex(String filepath, String format) throws IOException, IllegalArgumentException, ClassNotFoundException;
}
