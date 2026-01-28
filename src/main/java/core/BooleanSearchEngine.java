package core;

import document.DocumentRegistry;
import enums.FileSerializationFormat;
import enums.SearchStructureType;
import index.BiwordIndex;
import index.InvertedIndex;
import index.PositionalIndex;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import matrix.TermDocumentMatrix;
import query.QueryExecutor;
import serialization.FormatMetrics;
import serialization.SerializationComparison;
import serialization.data.IndexData;
import serialization.serializers.BinarySerializer;
import serialization.serializers.IndexSerializer;
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

@Slf4j
public class BooleanSearchEngine implements SearchEngine {
    @Getter
    private final InvertedIndex index;
    @Getter
    private final BiwordIndex biwordIndex;
    @Getter
    private final PositionalIndex positionalIndex;
    @Getter
    private final TermDocumentMatrix matrix;
    private final DocumentRegistry registry;
    private final QueryExecutor<InvertedIndex> indexQueryExecutor;
    private final QueryExecutor<TermDocumentMatrix> matrixQueryExecutor;
    private final QueryExecutor<BiwordIndex> biwordIndexQueryExecutor;
    private final QueryExecutor<PositionalIndex> positionalIndexQueryExecutor;
    private final AtomicLong totalCollectionSize = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    @Getter
    private SerializationComparison serializationComparison;


    public BooleanSearchEngine() {
        this.index = new InvertedIndex();
        this.biwordIndex = new BiwordIndex();
        this.positionalIndex = new PositionalIndex();
        this.matrix = new TermDocumentMatrix();
        this.registry = new DocumentRegistry();
        this.indexQueryExecutor = new QueryExecutor<>(index);
        this.matrixQueryExecutor = new QueryExecutor<>(matrix);
        this.biwordIndexQueryExecutor = new QueryExecutor<>(biwordIndex);
        this.positionalIndexQueryExecutor = new QueryExecutor<>(positionalIndex);
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
                            log.error("Error indexing files in path {}", path, e);
                        }
                    }))
                    .toList();

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (ExecutionException | InterruptedException e) {
                    log.error("Task execution failed", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void indexFile(Path path) throws IOException {
        long size = Files.size(path);
        String filename = path.getFileName().toString();
        String content = Files.readString(path);

        int docId;
        lock.writeLock().lock();
        try {
            docId = registry.registerDocument(filename, Files.size(path));
            totalCollectionSize.addAndGet(size);
        } finally {
            lock.writeLock().unlock();
        }

        List<String> tokens = Tokenizer.tokenize(content);

        for (int position = 0; position < tokens.size(); position++) {
            String token = tokens.get(position);

            index.addTerm(token, docId);
            matrix.addTerm(token, docId);

            positionalIndex.addTerm(token, docId, position);

            if (position < tokens.size() - 1) {
                String nextToken = tokens.get(position + 1);
                biwordIndex.addWord(token, nextToken, docId);
            }
        }
    }


    // searching
    @Override
    public Optional<Set<Integer>> search(String term, SearchStructureType type) {
        Objects.requireNonNull(term, "Term in search() must not be null");
        return switch (type) {
            case INDEX -> indexQueryExecutor.search(term);
            case MATRIX -> matrixQueryExecutor.search(term);
            case BIWORD -> biwordIndexQueryExecutor.search(term);
            case POSITIONAL -> positionalIndexQueryExecutor.search(term);
        };
    }

    @Override
    public Optional<Set<Integer>> andSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in andSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in andSearch() must not be null");
        return switch (type) {
            case INDEX -> indexQueryExecutor.andSearch(term1, term2);
            case MATRIX -> matrixQueryExecutor.andSearch(term1, term2);
            case BIWORD -> biwordIndexQueryExecutor.andSearch(term1, term2);
            case POSITIONAL -> positionalIndexQueryExecutor.andSearch(term1, term2);
        };
    }

    @Override
    public Optional<Set<Integer>> orSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in orSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in orSearch() must not be null");
        return switch (type) {
            case INDEX -> indexQueryExecutor.orSearch(term1, term2);
            case MATRIX -> matrixQueryExecutor.orSearch(term1, term2);
            case BIWORD -> biwordIndexQueryExecutor.orSearch(term1, term2);
            case POSITIONAL -> positionalIndexQueryExecutor.orSearch(term1, term2);
        };
    }

    @Override
    public Optional<Set<Integer>> notSearch(String term, Set<Integer> docIDs, SearchStructureType type) {
        Objects.requireNonNull(term, "First term in notSearch() must not be null");
        Objects.requireNonNull(docIDs, "Second term in notSearch() must not be null");
        return switch (type) {
            case INDEX -> indexQueryExecutor.notSearch(term, docIDs);
            case MATRIX -> matrixQueryExecutor.notSearch(term, docIDs);
            case BIWORD -> biwordIndexQueryExecutor.notSearch(term, docIDs);
            case POSITIONAL -> positionalIndexQueryExecutor.notSearch(term, docIDs);
        };
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

        lock.readLock().lock();
        try {
            IndexData indexData = new IndexData(
                    new ConcurrentHashMap<>(index.getIndex()),
                    registry.exportData()
            );

            IndexSerializer serializer = getSerializer(typeFormat.get());
            serializer.serialize(indexData, filepath);

            log.info("Index saved to {} (format: {}, docs: {}, terms: {})",
                    filepath, format, registry.documentCount(), index.size());
        } finally {
            lock.readLock().unlock();
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

        lock.writeLock().lock();
        try {
            IndexSerializer serializer = getSerializer(typeFormat.get());
            IndexData indexData = serializer.deserialize(filepath);

            index.loadIndex(indexData.index());

            registry.loadData(indexData.registryData());

            long totalSize = indexData.registryData().filenameToSize().values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum();
            totalCollectionSize.set(totalSize);

            log.info("Index loaded from {} (format: {}, docs: {}, terms: {}, next ID: {})",
                    filepath, format, registry.documentCount(), index.size(),
                    indexData.registryData().nextDocID());
        } finally {
            lock.writeLock().unlock();
        }
    }

    public SerializationComparison measureAllFormats() throws IOException {
        String tempFilename = "temp_comparison";

        System.out.println("\nMeasuring serialization formats...");

        FormatMetrics binaryMetrics = measureFormat(tempFilename, "ser", "binary");

        FormatMetrics textMetrics = measureFormat(tempFilename, "txt", "text");

        FormatMetrics jsonMetrics = measureFormat(tempFilename, "json", "json");

        deleteIfExists(tempFilename + ".ser");
        deleteIfExists(tempFilename + ".txt");
        deleteIfExists(tempFilename + ".json");

        System.out.println("Measurement completed!\n");

        return new SerializationComparison(binaryMetrics, textMetrics, jsonMetrics);
    }

    private FormatMetrics measureFormat(String filename, String extension, String format) throws IOException {
        String filepath = filename + "." + extension;

        System.out.printf("Measuring %s format...%n", format.toUpperCase());

        long saveStart = System.nanoTime();
        saveIndex(filepath, extension);
        long saveTime = (System.nanoTime() - saveStart) / 1_000_000; // convert to ms

        long fileSize = Files.size(Path.of(filepath));

        long loadStart = System.nanoTime();
        try {
            loadIndex(filepath, extension);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load index", e);
        }
        long loadTime = (System.nanoTime() - loadStart) / 1_000_000; // convert to ms

        System.out.printf("    Save: %d ms, Load: %d ms, Size: %.2f KB%n",
                saveTime, loadTime, fileSize / 1024.0);

        return new FormatMetrics(format.toUpperCase(), saveTime, loadTime, fileSize);
    }

    private void deleteIfExists(String filepath) {
        try {
            Files.deleteIfExists(Path.of(filepath));
        } catch (IOException e) {
            log.warn("Failed to delete temporary file: {}", filepath, e);
        }
    }


    private IndexSerializer getSerializer(FileSerializationFormat format) {
        return switch (format) {
            case JSON -> new JsonSerializer();
            case TEXT -> new TextSerializer();
            case BINARY -> new BinarySerializer();
        };
    }


    // statistics
    public DictionaryStats getStatistics(SearchStructureType type) {
        lock.readLock().lock();
        try {
            int uniqueTerms = switch (type) {
                case INDEX -> index.size();
                case MATRIX -> matrix.size();
                case BIWORD -> biwordIndex.size();
                case POSITIONAL -> positionalIndex.size();
            };
            int totalWords = switch (type) {
                case INDEX -> index.getTotalTermOccurrences();
                case MATRIX -> matrix.getTotalTermOccurrences();
                case BIWORD -> biwordIndex.getTotalTermOccurrences();
                case POSITIONAL -> positionalIndex.getTotalTermOccurrences();
            };

            return new DictionaryStats(
                    registry.documentCount(),
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
            return registry.getDocumentNames(docIDs);
        } finally {
            lock.readLock().unlock();
        }
    }

    // utility
    public void printIndex() {
        index.print();
    }

    public void printPositionalIndex() {
        positionalIndex.print();
    }

    public void printBiwordIndex() {
        biwordIndex.print();
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            index.clear();
            matrix.clear();
            biwordIndex.clear();
            positionalIndex.clear();
            registry.clear();
            totalCollectionSize.set(0);
        } finally {
            lock.writeLock().unlock();
        }
    }
}
