package core;

import document.DocumentRegistry;
import index.InvertedIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import query.BooleanQueryExecutor;
import enums.FileSerializationFormat;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BooleanSearchEngine implements SearchEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanSearchEngine.class);
    private final InvertedIndex index;
    private final DocumentRegistry documentRegistry;
    private final BooleanQueryExecutor queryExecutor;
    private SerializationComparison serializationComparison;
    private final AtomicLong totalCollectionSize = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public BooleanSearchEngine() {
        this.index = new InvertedIndex();
        this.documentRegistry = new DocumentRegistry();
        this.queryExecutor = new BooleanQueryExecutor(index);
    }

    // indexing
    @Override
    public void indexDocuments(String directoryPath) throws IOException {
        Objects.requireNonNull(directoryPath, "Directory path must not be null");
        List<Path> paths = FileWalker.findFiles(directoryPath);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = paths.stream()
                    .<Future<?>>map(path -> executor.submit(() -> {
                        try {
                            indexFile(path);
                        } catch (IOException e) {
                            LOGGER.error("Error indexing files in path {}", path, e);
                        }
                    }))
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException | InterruptedException e) {
                    LOGGER.error("Task execution failed", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void indexFile(Path path) throws IOException {
        long size = Files.size(path);
        String filename = path.getFileName().toString();
        String content = Files.readString(path);

        int docID;
        lock.writeLock().lock();
        try {
            docID = documentRegistry.registerDocument(filename, Files.size(path));
            totalCollectionSize.addAndGet(size);
        } finally {
            lock.writeLock().unlock();
        }

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
    public void saveIndex(String filepath, String format) throws IllegalArgumentException, IOException {
        Objects.requireNonNull(filepath, "Filepath in saveIndex() must not be null");
        Objects.requireNonNull(format, "Format in saveIndex() must not be null");

        var typeFormat = FileSerializationFormat.fromFormat(format);
        if (typeFormat.isEmpty()) {
            System.out.println("Format in saveIndex() must be one of: " + Arrays.toString(FileSerializationFormat.values()));
            return;
        }

        switch (typeFormat.get()) {
            case JSON -> saveDictionaryJson(filepath);
            case TEXT -> saveDictionaryText(filepath);
            case BINARY -> saveDictionaryBinary(filepath);
        }
    }

    @Override
    public void loadIndex(String filepath, String format) throws IOException, IllegalArgumentException, ClassNotFoundException {
        Objects.requireNonNull(filepath, "Filepath in loadIndex() must not be null");
        Objects.requireNonNull(format, "Format in loadIndex() must not be null");
        var typeFormat = FileSerializationFormat.fromFormat(format);
        if (typeFormat.isEmpty()) {
            System.out.println("Format in saveIndex() must be one of: " + Arrays.toString(FileSerializationFormat.values()));
            return;
        }
        switch (typeFormat.get()) {
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
        lock.readLock().lock();
        try {
            int uniqueTerms = index.size();
            int totalWords = index.getTotalTermOccurrences();

            return new DictionaryStats(
                    documentRegistry.documentCount(),
                    uniqueTerms,
                    totalWords,
                    totalCollectionSize.get()
            );
        } finally {
            lock.readLock().unlock();
        }
    }


    // getters
    public void setSerializationComparison(SerializationComparison serializationComparison) {
        Objects.requireNonNull(serializationComparison, "SerializationComparison must not be null");
        this.serializationComparison = serializationComparison;
    }

    public Set<Integer> getAllDocumentIDs() {
        Set<Integer> ids = ConcurrentHashMap.newKeySet();
        for (var entry : index.entrySet()) {
            ids.addAll(entry.getValue());
        }

        return ids;
    }

    public List<String> getDocumentNames(Set<Integer> docIDs) {
        Objects.requireNonNull(docIDs, "DocIDs must not be null");

        lock.readLock().lock();
        try {
            return documentRegistry.getDocumentNames(docIDs);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int documentCount() {
        lock.readLock().lock();
        try {
            return documentRegistry.documentCount();
        } finally {
            lock.readLock().unlock();
        }
    }

    public InvertedIndex getIndex() {
        return index;
    }

    public SerializationComparison getSerializationComparison() {
        return serializationComparison;
    }


    // utility
    public void printIndex() {
        index.print();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            index.clear();
            documentRegistry.clear();
            totalCollectionSize.set(0);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
