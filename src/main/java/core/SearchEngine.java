package core;

import enums.FileSerializationType;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public interface SearchEngine {
    void indexDocuments(String directory) throws IOException;
    Optional<Set<Integer>> search(String query);
    void saveIndex(String filepath, String format) throws IOException;
    void loadIndex(String filepath, String format) throws IOException, IllegalArgumentException, ClassNotFoundException;
}
