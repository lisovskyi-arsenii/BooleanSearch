package core;

import document.DocumentRegistry;
import index.InvertedIndex;
import query.BooleanQueryExecutor;
import serialization.FileType;
import serialization.SerializationComparison;
import serialization.serializers.BinarySerializer;
import serialization.serializers.JsonSerializer;
import serialization.serializers.TextSerializer;
import statistics.DictionaryStats;
import tokenization.Tokenizer;
import util.FileWalker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class BooleanSearchEngine implements SearchEngine {
    private final InvertedIndex index;
    private final DocumentRegistry documentRegistry;
    private final BooleanQueryExecutor queryExecutor;
    private SerializationComparison serializationComparison;
    private long totalCollectionSize = 0;

    public BooleanSearchEngine() {
        this.index = new InvertedIndex();
        this.documentRegistry = new DocumentRegistry();
        this.queryExecutor = new BooleanQueryExecutor(index);
    }

    // indexing
    public void indexDocuments(String directoryPath) throws IOException {
        Objects.requireNonNull(directoryPath, "Directory path must not be null");
        List<Path> paths = FileWalker.findFiles(directoryPath);

        for (Path path : paths) {
            indexFile(path);
        }
    }

    private void indexFile(Path path) throws IOException {
        long size = Files.size(path);
        totalCollectionSize += size;

        String filename = path.getFileName().toString();
        int docID = documentRegistry.registerDocument(filename, Files.size(path));

        String content = Files.readString(path);
        List<String> tokens = Tokenizer.tokenize(content);

        for (String token : tokens) {
            index.addTerm(token, docID);
        }
    }


    // searching
    @Override
    public Optional<Set<Integer>> search(String term) {
        Objects.requireNonNull(term, "Term in search() must not be null");
        return queryExecutor.search(term);
    }

    public Optional<Set<Integer>> andSearch(String term1, String term2) {
        Objects.requireNonNull(term1, "First term in andSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in andSearch() must not be null");
        return queryExecutor.andSearch(term1, term2);
    }

    public Optional<Set<Integer>> orSearch(String term1, String term2) {
        Objects.requireNonNull(term1, "First term in orSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in orSearch() must not be null");
        return queryExecutor.orSearch(term1, term2);
    }

    public Optional<Set<Integer>> notSearch(String term, Set<Integer> docIDs) {
        Objects.requireNonNull(term, "First term in notSearch() must not be null");
        Objects.requireNonNull(docIDs, "Second term in notSearch() must not be null");
        return queryExecutor.notSearch(term, docIDs);
    }


    // serialization/deserialization
    @Override
    public void saveIndex(String filepath, FileType format) throws IllegalArgumentException, IOException {
        Objects.requireNonNull(filepath, "Filepath in saveIndex() must not be null");
        Objects.requireNonNull(format, "Format in saveIndex() must not be null");
        switch (format) {
            case JSON -> saveDictionaryJson(filepath);
            case TEXT -> saveDictionaryText(filepath);
            case BINARY -> saveDictionaryBinary(filepath);
        }
    }

    @Override
    public void loadIndex(String filepath, FileType format) throws IOException, IllegalArgumentException, ClassNotFoundException {
        Objects.requireNonNull(filepath, "Filepath in loadIndex() must not be null");
        Objects.requireNonNull(format, "Format in loadIndex() must not be null");
        switch (format) {
            case JSON -> loadDictionaryJson(filepath);
            case TEXT -> loadDictionaryText(filepath);
            case BINARY -> loadDictionaryBinary(filepath);
        }
    }

    private void saveDictionaryBinary(String filepath) throws IOException {
        new BinarySerializer().serialize(index.getIndex(), filepath);
    }

    private void saveDictionaryText(String filepath) throws IOException {
        new TextSerializer().serialize(index.getIndex(), filepath);
    }

    private void saveDictionaryJson(String filepath) throws IOException {
        new JsonSerializer().serialize(index.getIndex(), filepath);
    }

    private void loadDictionaryBinary(String filepath) throws IOException, ClassNotFoundException {
        index.loadIndex(new BinarySerializer().deserialize(filepath));
    }

    private void loadDictionaryText(String filepath) throws IOException, ClassNotFoundException {
        index.loadIndex(new TextSerializer().deserialize(filepath));
    }

    private void loadDictionaryJson(String filepath) throws IOException, ClassNotFoundException {
        index.loadIndex(new JsonSerializer().deserialize(filepath));
    }


    // statistics
    public DictionaryStats getStatistics() {
        int uniqueTerms = index.size();
        int totalWords = index.getTotalTermOccurrences();

        return new DictionaryStats(
                documentRegistry.documentCount(),
                uniqueTerms,
                totalWords,
                totalCollectionSize
        );
    }


    // getters
    public SerializationComparison getSerializationComparison() {
        return serializationComparison;
    }

    public void setSerializationComparison(SerializationComparison serializationComparison) {
        Objects.requireNonNull(serializationComparison, "SerializationComparison must not be null");
        this.serializationComparison = serializationComparison;
    }

    public List<String> getDocumentNames(Set<Integer> docIDs) {
        Objects.requireNonNull(docIDs, "DocIDs must not be null");
        return documentRegistry.getDocumentNames(docIDs);
    }

    public int documentCount() {
        return documentRegistry.documentCount();
    }

    public InvertedIndex getIndex() {
        return index;
    }



    // utility
    public void printIndex() {
        index.print();
    }

    public void clear() {
        index.clear();
        documentRegistry.clear();
        totalCollectionSize = 0;
    }
}
