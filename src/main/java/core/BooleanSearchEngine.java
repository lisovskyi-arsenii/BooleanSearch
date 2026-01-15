package core;

import annotations.Loggable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import statistics.DictionaryStats;
import util.FileWalker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BooleanSearchEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanSearchEngine.class);

    private SerializationComparison serializationComparison;

    private long totalCollectionSize = 0;


    // indexing files
    public void indexDocumentsFromDirectory(String directoryPath) throws IllegalArgumentException, IOException {
        List<Path> paths = FileWalker.findFiles(directoryPath);

        for (Path path : paths) {
            LOGGER.info("Indexing documents from {}", path);
            indexFileFromDisk(path);
        }
    }

    private void indexFileFromDisk(Path filePath) throws IOException {
        totalCollectionSize += Files.size(filePath);
        String filename = filePath.getFileName().toString();

        if (!docMetadata.containsKey(filename)) {
            registerDocument(filename);
        }

        int documentID = docMetadata.get(filename);

        String content = Files.readString(filePath);
        List<String> tokens = Tokenizer.tokenize(content);

        for (String token : tokens) {
            invertedIndex.computeIfAbsent(token, k -> new HashSet<>()).add(documentID);
        }
    }

    // search by one term


    private String normalizeTerm(String term) {
    }

    // serialization
    @Loggable(message = "Saving dictionary into binary file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryBinary(String filepath) throws IOException {

    }

    @Loggable(message = "Saving dictionary into txt file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryText(String filepath) throws IOException {

    }

    @Loggable(message = "Saving dictionary into json file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryJSON(String filepath) throws IOException {

    }

    // deserialization
    @SuppressWarnings("unchecked")
    @Loggable(message = "Loading dictionary from binary file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryBinary(String filepath) throws IOException, ClassNotFoundException {

    }

    @Loggable(message = "Loading dictionary from txt file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryText(String filepath) throws IOException {

    }

    @Loggable(message = "Load dictionary from json file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryJSON(String filepath) throws IOException {

    }

    // порівняння форматів серіалізації
    public SerializationComparison getSerializationComparison() {
        return serializationComparison;
    }


    // work with data after queries
    public List<String> getDocumentNames(Set<Integer> docIDs) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : docMetadata.entrySet()) {
            if (docIDs.contains(entry.getValue())) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public Optional<String> getDocumentName(int docID) throws IllegalArgumentException {
        return Optional.ofNullable(idToFilename.get(docID));
    }

    public int documentCount() {
        return docMetadata.size();
    }


    // statistics
    public int termFrequency(String term) {
        return invertedIndex.getOrDefault(term, Collections.emptySet()).size();
    }

    public Set<String> getAllTerms() {
        return invertedIndex.keySet();
    }

    public Map<String, Set<Integer>> getInvertedIndex() {
        return invertedIndex;
    }

    public Map<String, Integer> getDocMetadata() {
        return docMetadata;
    }


    // статистика
    public DictionaryStats getStats() {
        int uniqueTerms = invertedIndex.size();
        int totalWords = invertedIndex.values().stream()
                .mapToInt(Set::size)
                .sum();

        return new DictionaryStats(
                docMetadata.size(),
                uniqueTerms,
                totalWords,
                totalCollectionSize
        );
    }

    // print out
    public void printIndex() {
        for (Map.Entry<String, Set<Integer>> entry : invertedIndex.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }


    // util methods
    public void clearIndex() {
        invertedIndex.clear();
    }

    private void registerDocument(String filename) {
        int id = nextDocID++;
        docMetadata.put(filename, id);
        idToFilename.put(id, filename);
    }

}
