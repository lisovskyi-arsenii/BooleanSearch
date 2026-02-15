package core;

import document.DocumentRegistry;
import enums.FileSerializationFormat;
import enums.SearchStructureType;
import index.*;
import index.Dictionary;
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
@Getter
public class BooleanSearchEngine implements SearchEngine {
    private final InvertedIndex index;
    private final BiwordIndex biwordIndex;
    private final PositionalIndex positionalIndex;
    private final TermDocumentMatrix matrix;
    private BTree bTree;
    private ReverseBTree reverseBTree;
    private ThreeGramIndex threeGramIndex;
    private final DocumentRegistry registry;
    private final Map<SearchStructureType, QueryExecutor<?>> executors;
    private final Map<SearchStructureType, Dictionary> dictionaries;
    private final AtomicLong totalCollectionSize = new AtomicLong(0);
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private SerializationComparison serializationComparison;


    public BooleanSearchEngine() {
        this.index = new InvertedIndex();
        this.biwordIndex = new BiwordIndex();
        this.positionalIndex = new PositionalIndex();
        this.matrix = new TermDocumentMatrix();
        this.registry = new DocumentRegistry();
        this.executors = Map.of(
                SearchStructureType.INDEX, new QueryExecutor<>(index),
                SearchStructureType.MATRIX, new QueryExecutor<>(matrix),
                SearchStructureType.BIWORD, new QueryExecutor<>(biwordIndex),
                SearchStructureType.POSITIONAL, new QueryExecutor<>(positionalIndex)
        );
        this.dictionaries = Map.of(
                SearchStructureType.INDEX, index,
                SearchStructureType.MATRIX, matrix,
                SearchStructureType.BIWORD, biwordIndex,
                SearchStructureType.POSITIONAL, positionalIndex
        );
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

    public void buildWildcardIndexes() {
        lock.writeLock().lock();
        try {
            log.info("Building wildcard indexes");

            bTree = new BTree();
            bTree.buildFromDictionary(index);

            reverseBTree = new ReverseBTree();
            reverseBTree.buildFromDictionary(index);

            threeGramIndex = new ThreeGramIndex();
            threeGramIndex.buildFromDictionary(index);

            log.info("Wildcard indexes built: BTree={}, ReverseBTree={}, ThreeGram={}",
                    bTree.size(), reverseBTree.size(), threeGramIndex.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    // searching
    @Override
    public Optional<Set<Integer>> search(String term, SearchStructureType type) {
        Objects.requireNonNull(term, "Term in search() must not be null");
        return getExecutor(type).search(term);
    }

    @Override
    public Optional<Set<Integer>> andSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in andSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in andSearch() must not be null");
        return getExecutor(type).andSearch(term1, term2);
    }

    @Override
    public Optional<Set<Integer>> orSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in orSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in orSearch() must not be null");
        return getExecutor(type).orSearch(term1, term2);
    }

    @Override
    public Optional<Set<Integer>> notSearch(String term, Set<Integer> docIDs, SearchStructureType type) {
        Objects.requireNonNull(term, "First term in notSearch() must not be null");
        Objects.requireNonNull(docIDs, "Second term in notSearch() must not be null");
        return getExecutor(type).notSearch(term, docIDs);
    }

    public Optional<Map<String, Set<Integer>>> wildcardSearch(String wildcardQuery) {
        Objects.requireNonNull(wildcardQuery, "Wildcard query must be not null");

        lock.readLock().lock();
        try {
            if (bTree == null || reverseBTree == null || threeGramIndex == null) {
                throw new IllegalStateException(
                        "Wildcard indexes not built. Call buildWildcardIndexes() first."
                );
            }
            List<String> matchingTerms;

            if (wildcardQuery.endsWith("*") && wildcardQuery.indexOf("*") == wildcardQuery.length() - 1) {
                matchingTerms = bTree.search(wildcardQuery);
            } else if (wildcardQuery.startsWith("*") && wildcardQuery.indexOf("*") == 0) {
                matchingTerms = reverseBTree.search(wildcardQuery);
            } else {
                matchingTerms = threeGramIndex.search(wildcardQuery);
            }

            Map<String, Set<Integer>> termToDocs = new TreeMap<>();
            for (String term : matchingTerms) {
                Optional<Set<Integer>> docs = index.getDocuments(term);
                docs.ifPresent(documents -> termToDocs.put(term, documents));
            }

            return termToDocs.isEmpty() ? Optional.empty() : Optional.of(termToDocs);

        } finally {
            lock.readLock().unlock();
        }
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
                    new ConcurrentHashMap<>(positionalIndex.getIndex()),
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

            positionalIndex.loadIndex(indexData.positionalIndex());
            registry.loadData(indexData.registryData());

            long totalSize = indexData.registryData().filenameToSize().values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum();
            totalCollectionSize.set(totalSize);

            log.info("Rebuilding derived indexes from PositionalIndex");
            rebuildDerivedIndexes();

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

    private void rebuildDerivedIndexes() {
        index.clear();
        biwordIndex.clear();
        matrix.clear();

        // index + matrix
        for (var termEntry : positionalIndex.getIndex().entrySet()) {
            String term = termEntry.getKey();
            Set<Integer> docIds = termEntry.getValue().keySet();

            for (int docId : docIds) {
                index.addTerm(term, docId);
                matrix.addTerm(term, docId);
            }
        }
        log.info("INDEX rebuilt: {} terms", index.size());

        // biword
        Set<Integer> allDocIds = registry.getAllDocumentIds();
        for (int docId : allDocIds) {
            TreeMap<Integer, String> positionToTerm = new TreeMap<>();

            for (var termEntry : positionalIndex.getIndex().entrySet()) {
                Map<Integer, List<Integer>> docPositions = termEntry.getValue();
                List<Integer> positions = docPositions.get(docId);

                if (positions != null) {
                    for (int pos : positions) {
                        positionToTerm.put(pos, termEntry.getKey());
                    }
                }
            }

            positionToTerm.forEach((pos, term) -> {
                Integer nextPos = positionToTerm.higherKey(pos);
                if (nextPos != null && nextPos == pos + 1) {
                    String nextTerm = positionToTerm.get(nextPos);
                    biwordIndex.addWord(term, nextTerm, docId);
                }
            });
        }

        log.info("Rebuilt: InvertedIndex={}, BiwordIndex={}, Matrix={}",
                index.size(), biwordIndex.size(), matrix.size());
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
            return switch (type) {
                case INDEX -> statsFor(index);
                case MATRIX -> statsFor(matrix);
                case BIWORD -> statsFor(biwordIndex);
                case POSITIONAL -> statsFor(positionalIndex);
            };
        } finally {
            lock.readLock().unlock();
        }
    }

    private DictionaryStats statsFor(Dictionary dict) {
        return new DictionaryStats(
                registry.documentCount(),
                dict.size(),
                dict.getTotalTermOccurrences(),
                totalCollectionSize.get()
        );
    }



    // getters
    public void setSerializationComparison(SerializationComparison serializationComparison) {
        Objects.requireNonNull(serializationComparison, "SerializationComparison must not be null");
        this.serializationComparison = serializationComparison;
    }

    public Set<Integer> getAllDocumentIDs() {
        lock.readLock().lock();
        try {
            return registry.getAllDocumentIds();
        } finally {
            lock.readLock().unlock();
        }
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

            if (bTree != null) {
                bTree.clear();
            }
            if (reverseBTree != null) {
                reverseBTree.clear();
            }
            if (threeGramIndex != null) {
                threeGramIndex.clear();
            }

            log.info("All indexes cleared");
        } finally {
            lock.writeLock().unlock();
        }
    }

    private QueryExecutor<?> getExecutor(SearchStructureType type) throws IllegalArgumentException {
        Objects.requireNonNull(type, "Type must not be null");

        QueryExecutor<?> executor = executors.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown search structure type: " + type);
        }
        return executor;
    }
}
