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
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Slf4j
public class SPIMI {

    // Constants

    private static final long MEMORY_THRESHOLD_SIZE =
            Math.max(512 * 1024 * 1024L, (long) (Runtime.getRuntime().maxMemory() * 0.7));

    private static final int DISK_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();

    private static final String TEMP_DIR      = "temp_blocks";
    private static final String POSTINGS_FILE = "postings.dat";
    private static final String OFFSETS_FILE  = "offsets.bin";
    private static final String REGISTRY_FILE = "registry.dat";

    // State

    // term → byte offset in postings.dat
    private final ConcurrentHashMap<String, Long> termOffsets = new ConcurrentHashMap<>();
    private final DocumentRegistry globalRegistry = new DocumentRegistry();

    @Getter private final AtomicInteger blocksCreated    = new AtomicInteger();
    @Getter private final AtomicInteger documentsIndexed = new AtomicInteger();
    @Getter private final AtomicLong    bytesProcessed   = new AtomicLong();

    // API

    public void buildIndex(String directoryPath) throws IOException {
        long start = System.currentTimeMillis();
        log.info("SPIMI: starting indexing from {}", directoryPath);
        log.info("Memory threshold: {} MB", MEMORY_THRESHOLD_SIZE / 1024 / 1024);

        Files.createDirectories(Paths.get(TEMP_DIR));

        List<Path> files = FileWalker.findFiles(directoryPath);
        if (files.isEmpty()) throw new IOException("No files found in: " + directoryPath);
        log.info("Found {} files", files.size());

        List<String> blockFiles = buildBlocks(files);
        mergeBlocks(blockFiles);
        persistRegistry();
        persistOffsets();
        deleteTempDir();

        long elapsed      = System.currentTimeMillis() - start;
        long postingsSize = Files.size(Paths.get(POSTINGS_FILE));
        long offsetsSize  = Files.size(Paths.get(OFFSETS_FILE));
        long registrySize = Files.size(Paths.get(REGISTRY_FILE));

        IndexMetadata meta = new IndexMetadata(
                termOffsets.size(),
                globalRegistry.documentCount(),
                bytesProcessed.get(),
                postingsSize + offsetsSize + registrySize,
                blocksCreated.get(),
                elapsed,
                POSTINGS_FILE
        );
        log.info("SPIMI completed in {} ms", elapsed);
        printStats(meta);
    }

    private List<String> buildBlocks(List<Path> files) throws IOException {
        Queue<String> createdBlocks = new ConcurrentLinkedQueue<>();

        Map<Path, Integer> docIds = new LinkedHashMap<>();
        for (Path file : files) {
            int id = globalRegistry.registerDocument(
                    file.getFileName().toString(), Files.size(file));
            docIds.put(file, id);
        }

        try (var pool = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            List<Future<?>> futures = new ArrayList<>();

            for (var entry : docIds.entrySet()) {
                Path file  = entry.getKey();
                int  docId = entry.getValue();

                futures.add(pool.submit(() -> {
                    try {
                        indexFile(file, docId, createdBlocks);
                    } catch (IOException e) {
                        log.error("Error processing file {}", file, e);
                    }
                }));
            }

            awaitAll(futures);
        }

        log.info("Blocks created: {}", createdBlocks.size());
        return new ArrayList<>(createdBlocks);
    }

    private void indexFile(Path file, int docId, Queue<String> out) throws IOException {
        PositionalIndex block  = new PositionalIndex();
        long            memUse = 0;
        int             pos    = 0;

        long localThreshold = MEMORY_THRESHOLD_SIZE / THREAD_POOL_SIZE;

        try (BufferedReader reader = openReader(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;

                for (String token : Tokenizer.tokenize(line)) {
                    if (token.isBlank()) continue;

                    block.addTerm(token, docId, pos++);
                    memUse += estimateTokenMemory(token);

                    if (memUse >= localThreshold) {
                        out.add(saveBlock(block));
                        block  = new PositionalIndex();
                        memUse = 0;
                    }
                }

                bytesProcessed.addAndGet(
                        line.getBytes(StandardCharsets.UTF_8).length + 1L
                );
            }
        }

        if (block.size() > 0) {
            out.add(saveBlock(block));
        }

        int count = documentsIndexed.incrementAndGet();
        if (count % 100 == 0) {
            log.info("Processed {} files ({} MB)",
                    count, bytesProcessed.get() / (1024 * 1024));
        }
    }

    private void mergeBlocks(List<String> blockFiles) throws IOException {
        if (blockFiles.isEmpty()) { log.warn("No blocks to merge"); return; }
        log.info("K-way merge of {} blocks...", blockFiles.size());

        PriorityQueue<BlockReader> heap =
                new PriorityQueue<>(Comparator.comparing(BlockReader::peekTerm));

        for (String f : blockFiles) {
            BlockReader reader = new BlockReader(f);
            if (reader.hasNext()) heap.add(reader); else reader.close();
        }

        long offset = 0;

        try (DataOutputStream out = bufferedOutput(POSTINGS_FILE)) {
            while (!heap.isEmpty()) {
                String term = heap.peek().peekTerm();

                Map<Integer, List<Integer>> merged = new TreeMap<>();
                while (!heap.isEmpty() && heap.peek().peekTerm().equals(term)) {
                    BlockReader r = heap.poll();
                    r.nextPostings().forEach((docId, positions) ->
                            merged.computeIfAbsent(docId, k -> new ArrayList<>())
                                    .addAll(positions));
                    if (r.hasNext()) heap.add(r); else r.close();
                }

                termOffsets.put(term, offset);
                offset += writeTerm(out, term, merged);

                if (termOffsets.size() % 10_000 == 0) {
                    log.info("Merged {} terms (offset: {} bytes)",
                            termOffsets.size(), offset);
                }
            }
        }

        log.info("Merge complete: {} unique terms", termOffsets.size());
    }

    private String saveBlock(PositionalIndex index) throws IOException {
        int    id   = blocksCreated.getAndIncrement();
        String path = TEMP_DIR + "/block_" + id + ".bin";

        Map<String, Map<Integer, List<Integer>>> idx = index.getIndex();
        List<String> terms = new ArrayList<>(idx.keySet());
        Collections.sort(terms);

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path), 65_536))) {
            for (String term : terms) {
                writeTermEntry(out, term, idx.get(term));
            }
        }

        log.debug("Block {} saved: {} terms", id, terms.size());
        return path;
    }

    private int writeTerm(DataOutputStream out,
                          String term,
                          Map<Integer, List<Integer>> postings) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (DataOutputStream tmp = new DataOutputStream(buf)) {
            writeTermEntry(tmp, term, postings);
        }
        byte[] data = buf.toByteArray();
        out.write(data);
        return data.length;
    }

    private void writeTermEntry(DataOutputStream out,
                                String term,
                                Map<Integer, List<Integer>> postings) throws IOException {
        out.writeUTF(term);
        out.writeInt(postings.size());
        for (var entry : postings.entrySet()) {
            out.writeInt(entry.getKey());
            List<Integer> pos = entry.getValue();
            out.writeInt(pos.size());
            for (int p : pos) out.writeInt(p);
        }
    }

    private void persistRegistry() throws IOException {
        writeObject(REGISTRY_FILE, globalRegistry.exportData());
        log.info("Registry saved: {} documents", globalRegistry.documentCount());
    }

    private void persistOffsets() throws IOException {
        writeObject(OFFSETS_FILE, termOffsets);
        log.info("Offsets saved: {} terms", termOffsets.size());
    }

    public static Map<String, Long> loadOffsets() throws IOException, ClassNotFoundException {
        return readObject(OFFSETS_FILE);
    }

    public static RegistryData loadRegistry() throws IOException, ClassNotFoundException {
        return readObject(REGISTRY_FILE);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readObject(String path) throws IOException, ClassNotFoundException {
        try (ObjectInputStream in = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(path)))) {
            return (T) in.readObject();
        }
    }

    private static void writeObject(String path, Object obj) throws IOException {
        try (ObjectOutputStream out = new ObjectOutputStream(
                new BufferedOutputStream(new FileOutputStream(path)))) {
            out.writeObject(obj);
        }
    }

    private BufferedReader openReader(Path file) throws IOException {
        InputStream raw = new BufferedInputStream(Files.newInputStream(file), 64 * 1024);
        if (file.getFileName().toString().toLowerCase().endsWith(".bz2")) {
            raw = new BZip2CompressorInputStream(raw);
        }
        return new BufferedReader(new InputStreamReader(raw, StandardCharsets.UTF_8));
    }

    private DataOutputStream bufferedOutput(String path) throws IOException {
        return new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path), DISK_BUFFER_SIZE));
    }

    private long estimateTokenMemory(String token) {
        return 40L + (token.length() * 2L) + 80L;
    }

    private void awaitAll(List<Future<?>> futures) throws IOException {
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Indexing interrupted", e);
            } catch (ExecutionException e) {
                throw new IOException("File processing failed", e);
            }
        }
    }

    private void deleteTempDir() {
        try (var stream = Files.walk(Paths.get(TEMP_DIR))) {
            long n = stream.sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .filter(File::delete)
                    .count();
            log.info("Temporary files deleted: {}", n);
        } catch (IOException e) {
            log.warn("Failed to delete temporary files: {}", e.getMessage());
        }
    }

    private void printStats(IndexMetadata m) {
        double sec   = m.indexingTimeMs() / 1000.0;
        double mb    = m.totalBytesProcessed() / (1024.0 * 1024.0);
        double idxMb = m.finalIndexSize()       / (1024.0 * 1024.0);

        System.out.println("\n" + "=".repeat(72));
        System.out.println("SPIMI RESULTS");
        System.out.println("=".repeat(72));
        System.out.printf("  Documents:       %,d%n",     m.documentsCount());
        System.out.printf("  Unique terms:    %,d%n",     m.uniqueTerms());
        System.out.printf("  Data processed:  %.2f MB%n", mb);
        System.out.printf("  Index size:      %.2f MB%n", idxMb);
        System.out.printf("  Blocks created:  %d%n",      m.blocksCreated());
        System.out.printf("  Time:            %.2f sec%n", sec);
        System.out.printf("  Throughput:      %.2f MB/s%n", mb / sec);
        System.out.println("=".repeat(72));
    }

    private static class BlockReader implements AutoCloseable {
        private final DataInputStream in;
        private String currentTerm;
        private Map<Integer, List<Integer>> currentPostings;

        BlockReader(String path) throws IOException {
            this.in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(path), DISK_BUFFER_SIZE));
            advance();
        }

        private void advance() {
            try {
                currentTerm = in.readUTF();
                int docCount = in.readInt();
                currentPostings = new HashMap<>(docCount * 2);
                for (int i = 0; i < docCount; i++) {
                    int docId    = in.readInt();
                    int posCount = in.readInt();
                    List<Integer> positions = new ArrayList<>(posCount);
                    for (int j = 0; j < posCount; j++) positions.add(in.readInt());
                    currentPostings.put(docId, positions);
                }
            } catch (EOFException _) {
                currentTerm     = null;
                currentPostings = null;
            } catch (IOException e) {
                log.error("Error reading block", e);
                currentTerm     = null;
                currentPostings = null;
            }
        }

        boolean hasNext()  { return currentTerm != null; }
        String  peekTerm() { return currentTerm; }

        Map<Integer, List<Integer>> nextPostings() {
            var p = currentPostings;
            advance();
            return p;
        }

        @Override
        public void close() throws IOException { in.close(); }
    }
}
