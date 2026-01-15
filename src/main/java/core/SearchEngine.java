package core;

import serialization.FileType;

import java.io.IOException;
import java.util.Optional;
import java.util.Set;

public interface SearchEngine {
    void indexDocuments(String directory) throws IOException;
    Optional<Set<Integer>> search(String query);
    void saveIndex(String filepath, FileType format) throws IOException;
    void loadIndex(String filepath, FileType format) throws IOException, IllegalArgumentException, ClassNotFoundException;
}
