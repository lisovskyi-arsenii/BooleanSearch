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
import serialization.data.RegistryData;
import serialization.serializers.BinarySerializer;
import serialization.serializers.IndexSerializer;
import serialization.serializers.JsonSerializer;
import serialization.serializers.TextSerializer;
import statistics.DictionaryStats;
import tokenization.Tokenizer;
import util.FileWalker;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Slf4j
@Getter
public class BooleanSearchEngine implements SearchEngine {
    public enum IndexingMode {
        NOT_INIT,
        IN_MEMORY,
        DISK_BASED
    }

    private volatile IndexingMode currentMode = IndexingMode.NOT_INIT;

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
    private final SPIMI spimi;

    // Disk-based mode
    private final SPIMI spimi;
    private RandomAccessFile postingsFile;
    private Map<String, Long> termOffsets;
    private final Lock fileLock = new ReentrantLock();

    public BooleanSearchEngine() {
        this.index = new InvertedIndex();
        this.biwordIndex = new BiwordIndex();
        this.positionalIndex = new PositionalIndex();
        this.matrix = new TermDocumentMatrix();
        this.registry = new DocumentRegistry();
        this.spimi = new SPIMI();
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

    // ============================================================================
    // INDEXING (in-memory mode)
    // ============================================================================
    @Override
    public void indexDocuments(String directoryPath) throws IOException, IllegalStateException {
        Objects.requireNonNull(directoryPath, "Directory path must not be null");

        lock.writeLock().lock();
        try {
            if (currentMode == IndexingMode.DISK_BASED) {
                System.out.println("⚠️ Current mode is DISK-BASED.");
                System.out.println("   Call clear() first to switch to IN-MEMORY mode.");
                return;
            }
            currentMode = IndexingMode.IN_MEMORY;
        } finally {
            lock.writeLock().unlock();
        }

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

        log.info("Documents indexed in IN-MEMORY mode");
        log.info("   Documents: {}, Terms: {}", registry.documentCount(), index.size());
    }

    public void indexLargeCollection(String directoryPath) throws IOException {
        Objects.requireNonNull(directoryPath, "Directory path must not be null");

        lock.writeLock().lock();
        try {
            if (currentMode == IndexingMode.IN_MEMORY) {
                System.out.println("⚠️ Current mode is IN-MEMORY.");
                System.out.println("   Call clear() first to switch to DISK-BASED mode.");
                return;
            }

            log.info("Starting SPIMI disk-based indexing");

            spimi.buildIndex(directoryPath);
            enableDiskBasedMode();
            currentMode = IndexingMode.DISK_BASED;

            log.info("✅ Large collection indexed successfully!");
            log.info("   Mode: DISK-BASED (Random Access)");
            log.info("   Documents: {}", registry.documentCount());
            log.info("   Terms: {}", termOffsets.size());
            log.info("   You can now search using disk-based mode");

        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load index", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void indexLargeCollection(String directoryPath) throws IOException {
        Objects.requireNonNull(directoryPath, "Directory path must not be null");

        lock.writeLock().lock();
        try {
            IndexData indexData = spimi.buildIndex(directoryPath);

            positionalIndex.loadIndex(indexData.positionalIndex());
            registry.loadData(indexData.registryData());

            long totalSize = indexData.registryData().filenameToSize().values()
                    .stream()
                    .mapToLong(Long::longValue)
                    .sum();
            totalCollectionSize.set(totalSize);

            log.info("Rebuilding derived indexes from PositionalIndex");
            rebuildDerivedIndexes();

            log.info("Large collection indexed: {} docs, {} terms",
                    registry.documentCount(), index.size());

        } finally {
            lock.writeLock().unlock();
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
            if (currentMode == IndexingMode.NOT_INIT) {
                System.out.println("⚠️Index not initialized.");
                System.out.println("  Call indexDocuments() first.");
                return;
            }

            if (currentMode == IndexingMode.DISK_BASED) {
                System.out.println("ℹ️Wildcard search works automatically in DISK-BASED mode.");
                System.out.println("  No need to build separate wildcard indexes.");
                return;
            }

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

    // ============================================================================
    // DISK-BASED MODE
    // ============================================================================

    private void enableDiskBasedMode() throws IOException, ClassNotFoundException {
        log.info("Enabling disk-based mode...");

        // Завантажуємо тільки offsets (маленький файл)
        termOffsets = SPIMI.loadOffsets();

        // Завантажуємо registry
        RegistryData registryData = SPIMI.loadRegistry();
        registry.loadData(registryData);

        // Відкриваємо postings.dat для Random Access
        var postingsFilename = "postings.dat";
        postingsFile = new RandomAccessFile(postingsFilename, "r");

        long offsetsSize = Files.size(Path.of("offsets.bin"));
        long postingsSize = Files.size(Path.of(postingsFilename));

        log.info("✅ Disk-based mode enabled!");
        log.info(String.format("   Offsets in RAM:    %.2f MB (%d terms)",
                offsetsSize / (1024.0 * 1024.0), termOffsets.size()));
        log.info(String.format("   Postings on disk:  %.2f MB",
                postingsSize / (1024.0 * 1024.0)));
    }

    private Optional<Set<Integer>> searchFromDisk(String term) throws IOException {
        Long offset = termOffsets.get(term);
        if (offset == null) {
            log.debug("Term '{}' not found in dictionary", term);
            return Optional.empty();
        }

        fileLock.lock();
        try {
            postingsFile.seek(offset);
            postingsFile.readUTF();
            int docCount = postingsFile.readInt();

            Set<Integer> documents = new HashSet<>();
            for (int i = 0; i < docCount; i++) {
                int docId = postingsFile.readInt();
                int posCount = postingsFile.readInt();
                postingsFile.skipBytes(posCount * 4);
                documents.add(docId);
            }

            log.debug("Found '{}' in {} documents (disk seek)", term, documents.size());
            return Optional.of(documents);
        } finally {
            fileLock.unlock();
        }
    }

    private Optional<Map<Integer, List<Integer>>> searchFromDiskWithPositions(String term)
            throws IOException {
        Long offset = termOffsets.get(term);
        if (offset == null) {
            return Optional.empty();
        }

        fileLock.lock();
        try {
            postingsFile.seek(offset);

            postingsFile.readUTF();
            int docCount = postingsFile.readInt();

            Map<Integer, List<Integer>> postings = new HashMap<>();

            for (int i = 0; i < docCount; i++) {
                int docId = postingsFile.readInt();
                int posCount = postingsFile.readInt();

                List<Integer> positions = new ArrayList<>(posCount);
                for (int j = 0; j < posCount; j++) {
                    positions.add(postingsFile.readInt());
                }

                postings.put(docId, positions);
            }

            return Optional.of(postings);
        } finally {
            fileLock.unlock();
        }
    }

    // ============================================================================
    // SEARCHING
    // ============================================================================

    @Override
    public Optional<Set<Integer>> search(String term, SearchStructureType type) {
        Objects.requireNonNull(term, "Term in search() must not be null");

        lock.readLock().lock();
        try {
            return switch (currentMode) {
                case IndexingMode.NOT_INIT -> {
                    System.out.println("Index not initialized. Call indexDocuments() or indexLargeDocuments() first");
                    yield Optional.empty();
                }
                case IndexingMode.DISK_BASED -> searchFromDisk(term);
                default -> getExecutor(type).search(term);
            };
        } catch (IOException e) {
            log.error("Disk-based search failed for term: {}", term, e);
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Optional<Set<Integer>> andSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in andSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in andSearch() must not be null");

        Optional<Set<Integer>> docs1 = search(term1, type);
        Optional<Set<Integer>> docs2 = search(term2, type);

        if (docs1.isEmpty() || docs2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>(docs1.get());
        result.retainAll(docs2.get());

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    @Override
    public Optional<Set<Integer>> orSearch(String term1, String term2, SearchStructureType type) {
        Objects.requireNonNull(term1, "First term in orSearch() must not be null");
        Objects.requireNonNull(term2, "Second term in orSearch() must not be null");

        Optional<Set<Integer>> docs1 = search(term1, type);
        Optional<Set<Integer>> docs2 = search(term2, type);

        if (docs1.isEmpty() && docs2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>();
        docs1.ifPresent(result::addAll);
        docs2.ifPresent(result::addAll);

        return Optional.of(result);
    }

    @Override
    public Optional<Set<Integer>> notSearch(String term, Set<Integer> docIDs, SearchStructureType type) {
        Objects.requireNonNull(term, "First term in notSearch() must not be null");
        Objects.requireNonNull(docIDs, "Second term in notSearch() must not be null");

        Optional<Set<Integer>> termDocs = search(term, type);

        if (termDocs.isEmpty()) {
            return Optional.of(new HashSet<>(docIDs));
        }

        Set<Integer> result = new HashSet<>(docIDs);
        result.removeAll(termDocs.get());

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    public Optional<Set<Integer>> phraseSearch(String... terms) {
        lock.readLock().lock();
        try {
            if (currentMode == IndexingMode.NOT_INIT) {
                log.warn("Phrase search called on uninitialized index");
                return Optional.empty();
            }

            if (terms.length == 0) {
                return Optional.empty();
            }

            // Отримуємо позиції (з пам'яті або з диска)
            List<Map<Integer, List<Integer>>> allPostings = new ArrayList<>();

            for (String term : terms) {
                Optional<Map<Integer, List<Integer>>> postings =
                        currentMode == IndexingMode.IN_MEMORY ?
                                getPositionsFromMemory(term) :
                                searchFromDiskWithPositions(term);

                if (postings.isEmpty()) {
                    return Optional.empty();
                }
                allPostings.add(postings.get());
            }

            Set<Integer> commonDocs = new HashSet<>(allPostings.get(0).keySet());
            for (int i = 1; i < allPostings.size(); i++) {
                commonDocs.retainAll(allPostings.get(i).keySet());
            }

            Set<Integer> result = new HashSet<>();
            for (int docId : commonDocs) {
                if (isPhraseInDocument(docId, allPostings)) {
                    result.add(docId);
                }
            }

            return result.isEmpty() ? Optional.empty() : Optional.of(result);

        } catch (IOException e) {
            log.error("Phrase search failed", e);
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    private Optional<Map<Integer, List<Integer>>> getPositionsFromMemory(String term) {
        Map<Integer, List<Integer>> postings = positionalIndex.getIndex().get(term);
        return (postings == null || postings.isEmpty()) ?
                Optional.empty() :
                Optional.of(postings);
    }

    private boolean isPhraseInDocument(int docId, List<Map<Integer, List<Integer>>> allPostings) {
        List<Integer> firstTermPositions = allPostings.getFirst().get(docId);

        for (int startPos : firstTermPositions) {
            boolean isPhrase = true;

            for (int i = 1; i < allPostings.size(); i++) {
                List<Integer> positions = allPostings.get(i).get(docId);
                if (!positions.contains(startPos + i)) {
                    isPhrase = false;
                    break;
                }
            }

            if (isPhrase) {
                return true;
            }
        }
        return false;
    }

    public Optional<Map<String, Set<Integer>>> wildcardSearch(String wildcardQuery) {
        Objects.requireNonNull(wildcardQuery, "Wildcard query must be not null");

        lock.readLock().lock();
        try {
            if (currentMode == IndexingMode.NOT_INIT) {
                log.warn("Wildcard search called on uninitialized index");
                return Optional.empty();
            }

            if (currentMode == IndexingMode.IN_MEMORY
                    && (bTree == null || reverseBTree == null || threeGramIndex == null)) {
                    throw new IllegalStateException(
                            "Wildcard indexes not built. Call buildWildcardIndexes() first."
                    );
                }


            List<String> matchingTerms = findMatchingTerms(wildcardQuery);
            Map<String, Set<Integer>> termToDocs = new TreeMap<>();

            for (String term : matchingTerms) {
                Optional<Set<Integer>> docs = currentMode == IndexingMode.DISK_BASED ?
                        searchFromDisk(term) :
                        index.getDocuments(term);
                docs.ifPresent(documents -> termToDocs.put(term, documents));
            }

            return termToDocs.isEmpty() ? Optional.empty() : Optional.of(termToDocs);

        } catch (IOException e) {
            log.error("Wildcard search failed", e);
            return Optional.empty();
        } finally {
            lock.readLock().unlock();
        }
    }

    private List<String> findMatchingTerms(String wildcardQuery) {
        boolean endsWithWildcard = wildcardQuery.endsWith("*") &&
                wildcardQuery.indexOf("*") == wildcardQuery.length() - 1;
        boolean startsWithWildcard = wildcardQuery.startsWith("*") &&
                wildcardQuery.indexOf("*") == 0;

        if (currentMode == IndexingMode.DISK_BASED) {
            // Disk-based
            if (endsWithWildcard) {
                String prefix = wildcardQuery.substring(0, wildcardQuery.length() - 1);
                return termOffsets.keySet().stream()
                        .filter(term -> term.startsWith(prefix))
                        .sorted()
                        .toList();
            } else if (startsWithWildcard) {
                String suffix = wildcardQuery.substring(1);
                return termOffsets.keySet().stream()
                        .filter(term -> term.endsWith(suffix))
                        .sorted()
                        .toList();
            } else {
                String pattern = wildcardQuery.replace("*", ".*");
                return termOffsets.keySet().stream()
                        .filter(term -> term.matches(pattern))
                        .sorted()
                        .toList();
            }
        } else {
            // RAM-based
            if (endsWithWildcard) {
                return bTree.search(wildcardQuery);
            } else if (startsWithWildcard) {
                return reverseBTree.search(wildcardQuery);
            } else {
                return threeGramIndex.search(wildcardQuery);
            }
        }
    }

    // ============================================================================
    // SERIALIZATION/DESERIALIZATION
    // ============================================================================

    @Override
    public void saveIndex(String filepath, String format) throws IllegalArgumentException, IOException {
        Objects.requireNonNull(filepath, "Filepath in saveIndex() must not be null");
        Objects.requireNonNull(format, "Format in saveIndex() must not be null");

        lock.writeLock().lock();
        try {
            if (currentMode != IndexingMode.IN_MEMORY) {
                System.out.println("Index not initialized. Call indexDocuments() or indexLargeDocuments() first");
                return;
            }

            var typeFormat = FileSerializationFormat.fromFormat(format);
            if (typeFormat.isEmpty()) {
                System.out.println("Format in saveIndex() must be one of: " +
                        Arrays.toString(FileSerializationFormat.values()));
                return;
            }

            IndexData indexData = new IndexData(
                    new ConcurrentHashMap<>(positionalIndex.getIndex()),
                    registry.exportData()
            );

            IndexSerializer serializer = getSerializer(typeFormat.get());
            serializer.serialize(indexData, filepath);

            log.info("Index saved to {} (format: {}, docs: {}, terms: {})",
                    filepath, format, registry.documentCount(), index.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void loadIndex(String filepath, String format)
            throws IOException, IllegalArgumentException, ClassNotFoundException {
        Objects.requireNonNull(filepath, "Filepath in loadIndex() must not be null");
        Objects.requireNonNull(format, "Format in loadIndex() must not be null");


        lock.writeLock().lock();
        try {
            if (currentMode != IndexingMode.IN_MEMORY) {
                System.out.println("Cannot loadIndex() in DISK_BASED mode. Call clear() first.");
                return;
            }

            var typeFormat = FileSerializationFormat.fromFormat(format);
            if (typeFormat.isEmpty()) {
                System.out.println("Format in saveIndex() must be one of: " +
                        Arrays.toString(FileSerializationFormat.values()));
                return;
            }
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
        if (currentMode != IndexingMode.IN_MEMORY) {
            System.out.println("Cannot measureAllFormats() in DISK_BASED mode. Call clear() first.");
            return null;
        }

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

    private FormatMetrics measureFormat(String filename, String extension, String format)
            throws IOException {
        String filepath = filename + "." + extension;

        System.out.printf("Measuring %s format...%n", format.toUpperCase());

        long saveStart = System.nanoTime();
        saveIndex(filepath, extension);
        long saveTime = (System.nanoTime() - saveStart) / 1_000_000;

        long fileSize = Files.size(Path.of(filepath));

        long loadStart = System.nanoTime();
        try {
            loadIndex(filepath, extension);
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to load index", e);
        }
        long loadTime = (System.nanoTime() - loadStart) / 1_000_000;

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

    // ============================================================================
    // REBUILDING
    // ============================================================================
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

    // ============================================================================
    // STATISTICS
    // ============================================================================
    public DictionaryStats getStatistics(SearchStructureType type) {
        lock.readLock().lock();
        try {
            if (currentMode != IndexingMode.IN_MEMORY) {
                System.out.println("getStatistics() only works in IN_MEMORY mode");
                return null;
            }

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

    public void printStatistics() {
        lock.readLock().lock();
        try {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("BOOLEAN SEARCH ENGINE STATISTICS");
            System.out.println("=".repeat(70));

            System.out.println("Current mode:        " + currentMode);
            System.out.println();

            switch (currentMode) {
                case IndexingMode.DISK_BASED -> {
                    System.out.printf("Documents:           %,d%n", registry.documentCount());
                    System.out.printf("Terms:               %,d%n", termOffsets.size());

                    try {
                        System.out.printf("RAM usage:           ~%.2f MB (offsets only)%n",
                                Files.size(Path.of("offsets.bin")) / (1024.0 * 1024.0));
                        System.out.printf("Disk storage:        %.2f MB (postings)%n",
                                Files.size(Path.of("postings.dat")) / (1024.0 * 1024.0));
                    } catch (IOException e) {
                        log.warn("Could not read file sizes", e);
                    }

                    System.out.println("Search type:         O(1) disk seek per term");
                    System.out.println("Expected latency:    5-10 ms per term");

                }
                case IndexingMode.IN_MEMORY -> {
                    System.out.printf("Documents:           %,d%n", registry.documentCount());
                    System.out.printf("Terms:               %,d%n", index.size());
                    System.out.println("Search type:         RAM-based (instant)");

                }
                default -> {
                    System.out.println("No data indexed yet.");
                    System.out.println("Use indexDocuments() or indexLargeCollection()");
                }
            }

            System.out.println("=".repeat(70));
        } finally {
            lock.readLock().unlock();
        }
    }

    // ============================================================================
    // GETTERS & UTILITY
    // ============================================================================

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

    public void clear() {
        lock.writeLock().lock();
        try {
            // Close disk resources
            if (postingsFile != null) {
                try {
                    postingsFile.close();
                } catch (IOException e) {
                    log.warn("Error closing postings file", e);
                }
                postingsFile = null;
            }

            // Clear all structures
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

            if (termOffsets != null) {
                termOffsets.clear();
                termOffsets = null;
            }

            currentMode = IndexingMode.NOT_INIT;

            log.info("All indexes cleared, mode reset to NOT_INIT");
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void close() {
        lock.writeLock().lock();
        try {
            if (postingsFile != null) {
                try {
                    postingsFile.close();
                    log.info("Disk-based index closed");
                } catch (IOException e) {
                    log.warn("Error closing postings file", e);
                }
                postingsFile = null;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean isDiskBasedMode() {
        return currentMode == IndexingMode.DISK_BASED;
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
