package index;

import document.DocumentRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import serialization.data.IndexMetadata;
import serialization.data.RegistryData;
import tokenization.Tokenizer;
import util.FileWalker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SPIMI {
    private static final long MEMORY_THRESHOLD_BYTES = (long) Math.max(
            512 * 1024 * 1024L,
            Runtime.getRuntime().maxMemory() * 0.7
    );

    private static final String TEMP_DIR = "temp_blocks";
    private static final String POSTINGS_FILE = "postings.dat";
    private static final String OFFSETS_FILE = "offsets.bin";
    private static final String REGISTRY_FILE = "registry.dat";

    private static final int DISK_BUFFER_SIZE = 4 * 1024 * 1024; // 4 MB
    private static final int CHUNK_SIZE = 10 * 1024 * 1024; // 10 MB
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    // term -> offset in disk
    private final Map<String, Long> termOffsets = new ConcurrentHashMap<>();
    private final DocumentRegistry globalRegistry = new DocumentRegistry();

    @Getter private final AtomicInteger blocksCreated = new AtomicInteger(0);
    @Getter private final AtomicInteger documentsIndexed = new AtomicInteger(0);
    @Getter private final AtomicLong bytesProcessed = new AtomicLong(0);

    private final ThreadLocal<PositionalIndex> threadLocalBlocks = ThreadLocal.withInitial(PositionalIndex::new);
    private final ThreadLocal<AtomicLong> threadLocalMemory = ThreadLocal.withInitial(() -> new AtomicLong(0));

    private final ConcurrentHashMap<String, AtomicInteger> filePositionsCounters = new ConcurrentHashMap<>();

    public void buildIndex(String directoryPath) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("Starting SPIMI indexing from: {}", directoryPath);
        log.info("Memory limit: {} MB", MEMORY_THRESHOLD_BYTES / 1024 / 1024);

        Files.createDirectories(Paths.get(TEMP_DIR));
        List<Path> files = FileWalker.findFiles(directoryPath);

        if (files.isEmpty()) {
            throw new IOException("No files found in directory: " + directoryPath);
        }

        log.info("Found {} files to index", files.size());

        List<String> blockFiles = createBlocks(files);
        mergeBlocksWithOffsets(blockFiles);

        saveRegistry();
        saveOffsetsMap();
        cleanupTempFiles();

        long endTime = System.currentTimeMillis();
        long postingsSize = Files.size(Paths.get(POSTINGS_FILE));
        long offsetsSize = Files.size(Paths.get(OFFSETS_FILE));
        long registrySize = Files.size(Paths.get(REGISTRY_FILE));

        IndexMetadata metadata = new IndexMetadata(
                termOffsets.size(),
                globalRegistry.documentCount(),
                bytesProcessed.get(),
                postingsSize + offsetsSize + registrySize,
                blocksCreated.get(),
                endTime - startTime,
                POSTINGS_FILE
        );

        log.info("SPIMI indexing finished in {} ms", endTime - startTime);
        printStatistics(metadata);
    }

    private List<String> createBlocks(List<Path> files) throws IOException {
        List<String> createdBlocks = Collections.synchronizedList(new ArrayList<>());

        BlockingQueue<FileChunk> workQueue = new LinkedBlockingQueue<>();

        for (Path file : files) {
            long fileSize = Files.size(file);
            String filename = file.getFileName().toString();

            int docId = globalRegistry.registerDocument(filename, fileSize);
            filePositionsCounters.put(filename, new AtomicInteger(0));

            if (fileSize > CHUNK_SIZE) {
                long numChunks = (fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE;

                for (long i = 0; i < numChunks; i++) {
                    long startOffset = i * CHUNK_SIZE;
                    long endOffset = Math.min((i + 1) * CHUNK_SIZE, fileSize);

                    workQueue.add(new FileChunk(file, docId, filename, startOffset, endOffset, i));
                }
            } else {
                workQueue.add(new FileChunk(file, docId, filename, 0, fileSize, 0));
            }
        }

        int totalChunks = workQueue.size();
        log.info("Total chunks to process: {}", totalChunks);

        try (var executor = Executors.newWorkStealingPool(THREAD_POOL_SIZE)) {
            List<Future<?>> futures = new ArrayList<>();

            for (var file : files) {
                Future<?> future = executor.submit(() -> {
                    try {
                        while (true) {
                            var chunk = workQueue.poll(100, TimeUnit.MILLISECONDS);

                            if (chunk == null) {
                                if (workQueue.isEmpty()) {
                                    break;
                                }
                                continue;
                            }
                            processFile(chunk, createdBlocks);
                        }
                    } catch (Exception e) {
                        log.error("Failed to process file {}", file, e);
                    }
                });
                futures.add(future);
            }

            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Indexing interrupted", e);
                } catch (ExecutionException e) {
                    throw new IOException("Processing of files failed", e);
                }
            }

            executor.shutdown();
        }

        log.info("Block creation completed");
        return createdBlocks;
    }

    private void processFile(FileChunk chunk, List<String> createdBlocks) throws IOException {
        var localBlock = threadLocalBlocks.get();
        var localMemory = threadLocalMemory.get();

        long bytesRead = 0;
        long targetBytes = chunk.endOffset - chunk.startOffset;

        try (BufferedReader reader = createReader(chunk.file)) {
            if (chunk.startOffset > 0) {
                reader.skip(chunk.startOffset);
            }

            String line;
            while ((line = reader.readLine()) != null && bytesRead < targetBytes) {
                if (line.isBlank()) continue;

                bytesRead += line.getBytes(StandardCharsets.UTF_8).length + 1;

                var tokens = Tokenizer.tokenize(line);

                for (var token : tokens) {
                    if (token.isBlank()) continue;

                    int position = filePositionsCounters.get(chunk.filename).getAndIncrement();

                    localBlock.addTerm(token, chunk.docId, position);

                    long memoryUsage = estimatedMemory(token);
                    localMemory.addAndGet(memoryUsage);

                    if (localMemory.get() >= MEMORY_THRESHOLD_BYTES / THREAD_POOL_SIZE) {
                        flushBlock(localBlock, createdBlocks);

                        localBlock = new PositionalIndex();
                        threadLocalBlocks.set(localBlock);
                        localMemory.set(0);
                    }
                }
            }
        }

        if (localBlock.size() > 0) {
            flushBlock(localBlock, createdBlocks);
            threadLocalBlocks.set(new PositionalIndex());
            localMemory.set(0);
        }

        bytesProcessed.addAndGet(targetBytes);

        if (chunk.chunkIndex == 0 || chunk.endOffset == Files.size(chunk.file)) {
            int count = documentsIndexed.incrementAndGet();
            if (count % 100 == 0) {
                log.info("Processed {} files... ({} MB processed)",
                        count, bytesProcessed.get() / (1024 * 1024));
            }
        }
    }

    private void flushBlock(PositionalIndex block, List<String> createdBlocks) throws IOException {
        synchronized (createdBlocks) {
            String blockFile = saveBlockToDisk(block);
            createdBlocks.add(blockFile);
        }
    }

    private long estimatedMemory(String term) {
        return 40 + 24 + (term.length() * 2L) + 32 + 40 + 16;
    }

    private String saveBlockToDisk(PositionalIndex index) throws IOException {
        int blockId = blocksCreated.getAndIncrement();
        String filename = TEMP_DIR + "/block_" + blockId + ".bin";

        Map<String, Map<Integer, List<Integer>>> indexMap = index.getIndex();
        List<String> sortedTerms = new ArrayList<>(indexMap.keySet());
        Collections.sort(sortedTerms);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(filename), 65536))) {

            for (String term : sortedTerms) {
                Map<Integer, List<Integer>> postings = indexMap.get(term);
                out.writeUTF(term);
                out.writeInt(postings.size());

                for (var docEntry : postings.entrySet()) {
                    out.writeInt(docEntry.getKey());
                    List<Integer> positions = docEntry.getValue();
                    out.writeInt(positions.size());
                    for (int pos : positions) {
                        out.writeInt(pos);
                    }
                }
            }
        }

        log.info("Block {} saved: {} terms", blockId, sortedTerms.size());
        return filename;
    }

    private record FileChunk (
            Path file,
            int docId,
            String filename,
            long startOffset,
            long endOffset,
            long chunkIndex
    ) {}

    private void mergeBlocksWithOffsets(List<String> blockFiles) throws IOException {
        log.info("Starting K-way merge of {} blocks...", blockFiles.size());

        if (blockFiles.isEmpty()) {
            log.warn("No block files found");
            return;
        }

        PriorityQueue<BlockReader> queue = new PriorityQueue<>(
                Comparator.comparing(BlockReader::peekTerm)
        );

        for (String file : blockFiles) {
            BlockReader reader = new BlockReader(file);
            if (reader.hasNext()) {
                queue.add(reader);
            } else {
                reader.close();
            }
        }

        // counter for bytes in a file
        long currentOffset = 0;

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(POSTINGS_FILE), DISK_BUFFER_SIZE))) {

            while (!queue.isEmpty()) {
                String term = queue.peek().peekTerm();
                Map<Integer, List<Integer>> mergedPostings = new TreeMap<>();

                while (!queue.isEmpty() && queue.peek().peekTerm().equals(term)) {
                    BlockReader reader = queue.poll();
                    var postings = reader.nextPostings();

                    for (var entry : postings.entrySet()) {
                        mergedPostings.computeIfAbsent(entry.getKey(), k -> new ArrayList<>())
                                .addAll(entry.getValue());
                    }

                    if (reader.hasNext()) {
                        queue.add(reader);
                    } else {
                        reader.close();
                    }
                }

                termOffsets.put(term, currentOffset);

                int bytesWritten = writeTermToDisk(out, term, mergedPostings);

                // зсунути offset на вже записану кількість байтів
                currentOffset += bytesWritten;

                mergedPostings.clear();

                if (termOffsets.size() % 10000 == 0) {
                    log.info("Merged {} terms... (offset: {} bytes)",
                            termOffsets.size(), currentOffset);
                }
            }
        }

        log.info("K-way merge complete: {} unique terms", termOffsets.size());
    }

    private int writeTermToDisk(DataOutputStream out, String term,
                                 Map<Integer, List<Integer>> postings) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream temp = new DataOutputStream(baos);

        temp.writeUTF(term);
        temp.writeInt(postings.size());

        for (var entry : postings.entrySet()) {
            temp.writeInt(entry.getKey());
            List<Integer> positions = entry.getValue();
            temp.writeInt(positions.size());
            for (int pos : positions) {
                temp.writeInt(pos);
            }
        }

        byte[] data = baos.toByteArray();
        out.write(data);
        return data.length;
    }

    private void saveRegistry() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(REGISTRY_FILE)))) {
            oos.writeObject(globalRegistry.exportData());
        }
        log.info("Registry saved: {} documents", globalRegistry.documentCount());
    }

    private void saveOffsetsMap() throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(OFFSETS_FILE)))) {
            oos.writeObject(termOffsets);
        }

        long offsetsSize = Files.size(Paths.get(OFFSETS_FILE));
        log.info("Offsets map saved: {} terms, {} MB",
                termOffsets.size(), offsetsSize / (1024.0 * 1024.0));
    }

    public static Map<String, Long> loadOffsets() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(OFFSETS_FILE)))) {
            @SuppressWarnings("unchecked")
            Map<String, Long> offsets = (Map<String, Long>) ois.readObject();
            return offsets;
        }
    }


    public static RegistryData loadRegistry() throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(REGISTRY_FILE)))) {
            return (RegistryData) ois.readObject();
        }
    }

    private BufferedReader createReader(Path file) throws IOException {
        int bufferSize = 64 * 1024; // 64 KB
        String name = file.getFileName().toString().toLowerCase();

        InputStream is = new BufferedInputStream(Files.newInputStream(file), bufferSize);

        if (name.endsWith(".bz2")) {
            is = new BZip2CompressorInputStream(is);
        }
        return new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
    }

    private void printStatistics(IndexMetadata metadata) {
        double durationSec = metadata.indexingTimeMs() / 1000.0;
        double mbProcessed = metadata.totalBytesProcessed() / (1024.0 * 1024.0);
        double gbProcessed = mbProcessed / 1024.0;
        double mbPerSec = mbProcessed / durationSec;
        double indexSizeMB = metadata.finalIndexSize() / (1024.0 * 1024.0);
        double memoryThresholdMB = MEMORY_THRESHOLD_BYTES / (1024.0 * 1024.0);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SPIMI INDEXING RESULTS");
        System.out.println("=".repeat(80));
        System.out.println("\n📊 COLLECTION STATISTICS:");
        System.out.printf("  Documents indexed:     %,d%n", metadata.documentsCount());
        System.out.printf("  Unique terms:          %,d%n", metadata.uniqueTerms());
        System.out.printf("  Avg terms per doc:     %,.0f%n",
                metadata.uniqueTerms() / (double) metadata.documentsCount());

        System.out.println("\n💾 DATA PROCESSING:");
        System.out.printf("  Raw data processed:    %.2f GB (%.2f MB)%n", gbProcessed, mbProcessed);
        System.out.printf("  Final index size:      %.2f MB%n", indexSizeMB);
        System.out.printf("  Compression ratio:     %.2f%%%n", metadata.compressionRatio());

        System.out.println("\n🔧 SPIMI PARAMETERS:");
        System.out.printf("  Memory threshold:      %.2f MB%n", memoryThresholdMB);
        System.out.printf("  Blocks created:        %d%n", metadata.blocksCreated());
        System.out.printf("  Avg block size:        %.2f MB%n",
                (mbProcessed / metadata.blocksCreated()));

        System.out.println("\n⏱️  PERFORMANCE:");
        System.out.printf("  Total time:            %.2f sec (%.2f min)%n",
                durationSec, durationSec / 60.0);
        System.out.printf("  Throughput:            %.2f MB/sec%n", mbPerSec);
        System.out.printf("  Avg time per doc:      %.2f ms%n",
                metadata.indexingTimeMs() / (double) metadata.documentsCount());
        System.out.printf("  Docs per second:       %.2f%n",
                metadata.documentsCount() / durationSec);

        System.out.println("\n📁 OUTPUT FILES:");
        System.out.printf("  Index file:            %s (%.2f MB)%n",
                metadata.indexPath(), indexSizeMB);
        System.out.printf("  Registry file:         %s%n", REGISTRY_FILE);

        System.out.println("\n" + "=".repeat(80));
    }

    private void cleanupTempFiles() {
        try {
            Path tempPath = Paths.get(TEMP_DIR);

            if (!Files.exists(tempPath)) {
                log.info("Temp directory does not exist, nothing to clean");
                return;
            }

            try (var stream = Files.walk(tempPath)) {
                long filesDeleted = stream
                        .sorted(Comparator.reverseOrder())
                        .map(Path::toFile)
                        .filter(File::delete)
                        .count();

                log.info("Temporary files cleaned up: {} items deleted", filesDeleted);
            }

        } catch (IOException e) {
            log.warn("Failed to cleanup temp files: {}", e.getMessage());
        }
    }

    private static class BlockReader implements AutoCloseable {
        private final DataInputStream in;
        private final String filename;
        private volatile String currentTerm;
        private Map<Integer, List<Integer>> currentPostings;
        private volatile boolean closed = false;

        public BlockReader(String filename) throws IOException {
            this.filename = filename;
            this.in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(filename), DISK_BUFFER_SIZE));
            readNext();
        }

        private void readNext() {
            if (closed) {
                currentTerm = null;
                return;
            }

            try {
                    currentTerm = in.readUTF();
                    int docCount = in.readInt();
                    currentPostings = new ConcurrentHashMap<>();

                    for (int i = 0; i < docCount; i++) {
                        int docId = in.readInt();
                        int posCount = in.readInt();
                        List<Integer> positions = new ArrayList<>(posCount);
                        for (int j = 0; j < posCount; j++) {
                            positions.add(in.readInt());
                        }
                        currentPostings.put(docId, positions);
                    }
            } catch (EOFException _) {
                    currentTerm = null;
                    currentPostings = null;
            } catch (IOException e) {
                log.error("Error reading block", e);
                currentTerm = null;
                currentPostings = null;
            }
        }

        public boolean hasNext() {
            return currentTerm != null && !closed;
        }

        public String peekTerm() {
            return currentTerm;
        }

        public Map<Integer, List<Integer>> nextPostings() {
            var postings = currentPostings;
            readNext();
            return postings;
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        log.error("Error closing block", e);
                        throw e;
                    }
                }
            }
        }
    }
}
