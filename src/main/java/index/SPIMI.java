package index;

import document.DocumentRegistry;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import serialization.data.IndexMetadata;
import serialization.data.RegistryData;
import tokenization.Tokenizer;
import util.FileWalker;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;

@Slf4j
public class SPIMI {

    //
    // Constants
    //

    private static final long MEMORY_THRESHOLD_SIZE =
            Math.max(512 * 1024 * 1024L, (long) (Runtime.getRuntime().maxMemory() * 0.7));

    private static final int DISK_BUFFER_SIZE = 4 * 1024 * 1024;
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private static final int SPARSE_INTERVAL = 128;

    //
    // Files
    //

    private static final String TEMP_DIR            = "temp_blocks";
    private static final String REGISTRY_FILE       = "registry.dat";

    public static final String POSTINGS_FILE       = "postings.dat";
    public static final String DICT_FILE           = "dictionary.dat";
    public static final String SPARSE_OFFSETS_FILE = "sparse_offsets.bin";


    //
    // State
    //

    private FileChannel postingsChannel;
    private FileChannel dictionaryChannel;
    private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

    private final TreeMap<String, Long> sparseOffsets       = new TreeMap<>();
    private final DocumentRegistry      globalRegistry      = new DocumentRegistry();

    @Getter private final AtomicInteger blocksCreated       = new AtomicInteger();
    @Getter private final AtomicInteger documentsIndexed    = new AtomicInteger();
    @Getter private final AtomicLong    bytesProcessed      = new AtomicLong();

    // public API

    public void buildIndex(String directoryPath) throws IOException {
        long start = System.currentTimeMillis();
        log.info("SPIMI: starting indexing from {}", directoryPath);
        log.info("Memory threshold: {} MB", MEMORY_THRESHOLD_SIZE / 1024 / 1024);

        Files.createDirectories(Paths.get(TEMP_DIR));

        List<Path> files = FileWalker.findFiles(directoryPath);
        if (files.isEmpty()) throw new IOException("No files found in: " + directoryPath);
        log.info("Found {} files", files.size());

        List<String> blockFiles = buildBlocks(files);
        int uniqueTerms = mergeBlocks(blockFiles);
        persistRegistry();
        persistSparseOffset();
        deleteTempDir();

        long elapsed      = System.currentTimeMillis() - start;
        long postingsSize = Files.size(Paths.get(POSTINGS_FILE));
        long registrySize = Files.size(Paths.get(REGISTRY_FILE));

        IndexMetadata meta = new IndexMetadata(
                uniqueTerms,
                globalRegistry.documentCount(),
                bytesProcessed.get(),
                postingsSize + registrySize,
                blocksCreated.get(),
                elapsed,
                POSTINGS_FILE
        );
        log.info("SPIMI completed in {} ms", elapsed);
        printStats(meta);
    }

    public void open() throws IOException, ClassNotFoundException {
        stateLock.writeLock().lock();
        try {
            TreeMap<String, Long> loaded = readObject(SPARSE_OFFSETS_FILE);
            sparseOffsets.clear();
            sparseOffsets.putAll(loaded);
            log.info("Sparse index loaded: {} anchor terms", sparseOffsets.size());

            var tempPostings = FileChannel.open(Paths.get(POSTINGS_FILE), StandardOpenOption.READ);
            try {
                dictionaryChannel = FileChannel.open(Paths.get(DICT_FILE), StandardOpenOption.READ);
            } catch (IOException e) {
                tempPostings.close();
                throw e;
            }

            postingsChannel = tempPostings;
            log.info("Sparse index loaded: {} anchor terms", sparseOffsets.size());
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    public void close() {
        stateLock.writeLock().lock();
        try {
            sparseOffsets.clear();
            closeQuietly(postingsChannel);
            closeQuietly(dictionaryChannel);
            postingsChannel   = null;
            dictionaryChannel = null;
            log.info("SPIMI closed");
        } finally {
            stateLock.writeLock().unlock();
        }
    }

    //
    // Search methods
    //
    public Map<Integer, List<Integer>> lookup(String term) throws IOException {
        stateLock.readLock().lock();
        try {
            Map.Entry<String, Long> floor = sparseOffsets.floorEntry(term);
            Map.Entry<String, Long> ceil  = sparseOffsets.higherEntry(term);

            long position = (floor != null) ? floor.getValue() : 0L;
            long rangeEnd   = (ceil != null)  ? ceil.getValue()  : dictionaryChannel.size();

            while (position < rangeEnd) {
                DictionaryEntry entry = readDictionaryEntry(position);
                int cmp = entry.term().compareTo(term);
                if (cmp == 0) return readPostings(entry.postingsOffset());
                if (cmp > 0) break;
                position = entry.nextPosition();
            }
            return Collections.emptyMap();
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public List<String> findTermsWithPrefix(String prefix) throws IOException {
        stateLock.readLock().lock();
        try {
            Map.Entry<String, Long> floor = sparseOffsets.floorEntry(prefix);
            long position = (floor != null) ? floor.getValue() : 0L;
            long fileSize = dictionaryChannel.size();
            List<String> result = new ArrayList<>();

            while (position < fileSize) {
                DictionaryEntry entry = readDictionaryEntry(position);
                if (entry.term().startsWith(prefix)) {
                    result.add(entry.term());
                } else if (entry.term().compareTo(prefix) > 0) {
                    break;
                }
                position = entry.nextPosition();
            }
            return result;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public List<String> findTerms(Predicate<String> filter) throws IOException {
        stateLock.readLock().lock();
        try {
            long position = 0L;
            long fileSize = dictionaryChannel.size();
            List<String> result = new ArrayList<>();

            while (position < fileSize) {
                DictionaryEntry entry = readDictionaryEntry(position);
                if (filter.test(entry.term())) result.add(entry.term());
                position = entry.nextPosition();
            }
            return result;
        } finally {
            stateLock.readLock().unlock();
        }
    }

    public static RegistryData loadRegistry() throws IOException, ClassNotFoundException {
        return readObject(REGISTRY_FILE);
    }

    //
    // Block building
    //


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

    private int mergeBlocks(List<String> blockFiles) throws IOException {
        if (blockFiles.isEmpty()) {
            log.warn("No blocks to merge"); return 0;
        }

        log.info("K-way merge of {} blocks...", blockFiles.size());

        PriorityQueue<BlockReader> heap =
                new PriorityQueue<>(Comparator.comparing(BlockReader::peekTerm));

        List<BlockReader> allReaders = new ArrayList<>();
        try {
            for (String f : blockFiles) {
                BlockReader reader = new BlockReader(f);
                allReaders.add(reader);
                if (reader.hasNext()) heap.add(reader);
                else reader.close();
            }
        } catch (IOException e) {
            for (BlockReader r : allReaders) {
                try { r.close(); } catch (IOException ex) {
                    log.warn("Failed to close block reader", ex);
                }
            }
            throw e;
        }

        int uniqueTerms     = 0;
        long postingsOffset = 0;
        long dictOffset     = 0;
        sparseOffsets.clear();

        try (DataOutputStream postingsOut = bufferedOutput(POSTINGS_FILE);
             DataOutputStream dictOut     = bufferedOutput(DICT_FILE)) {

            while (!heap.isEmpty()) {
                String term = heap.peek().peekTerm();

                Map<Integer, List<Integer>> merged = new TreeMap<>();
                while (!heap.isEmpty() && heap.peek().peekTerm().equals(term)) {
                    BlockReader r = heap.poll();
                    r.nextPostings().forEach((docId, positions) ->
                            merged.computeIfAbsent(docId, k -> new ArrayList<>())
                                    .addAll(positions));
                    if (r.hasNext()) heap.add(r);
                }

                int postingsWritten = writeTermPostings(postingsOut, merged);

                byte[] termBytes = term.getBytes(StandardCharsets.UTF_8);
                dictOut.writeShort(termBytes.length);
                dictOut.write(termBytes);
                dictOut.writeLong(postingsOffset);
                int dictEntrySize = Short.BYTES + termBytes.length + Long.BYTES;

                if (uniqueTerms % SPARSE_INTERVAL == 0) {
                    sparseOffsets.put(term, dictOffset);
                }

                postingsOffset += postingsWritten;
                dictOffset     += dictEntrySize;
                uniqueTerms++;

                if (uniqueTerms % 10_000 == 0) {
                    log.info("Merged {} terms (postings: {} MB, dict: {} KB)",
                            uniqueTerms, postingsOffset / (1024 * 1024), dictOffset / 1024);
                }
            }

        } finally {
            for (var reader : allReaders) {
                try {
                    reader.close();
                } catch (IOException e) {
                    log.warn("Failed to close block reader", e);
                }
            }
        }

        log.info("Merge complete: {} unique terms", uniqueTerms);
        return uniqueTerms;
    }

    //
    // Binary Read Helpers
    //

    private record DictionaryEntry(String term, long postingsOffset, long nextPosition) {}

    private DictionaryEntry readDictionaryEntry(long position) throws IOException {
        ByteBuffer lenBuf = ByteBuffer.allocate(Short.BYTES);
        dictionaryChannel.read(lenBuf, position);
        position += Short.BYTES;

        int termLen = lenBuf.flip().getShort() & 0xFFFF;
        ByteBuffer termBuf = ByteBuffer.allocate(termLen);
        dictionaryChannel.read(termBuf, position);
        position += termLen;

        String term = new String(termBuf.flip().array(), StandardCharsets.UTF_8);

        ByteBuffer offBuf = ByteBuffer.allocate(Long.BYTES);
        dictionaryChannel.read(offBuf, position);
        position += Long.BYTES;
        long postOff = offBuf.flip().getLong();

        return new DictionaryEntry(term, postOff, position);
    }

    private Map<Integer, List<Integer>> readPostings(long offset) throws IOException {
        ByteBuffer countBuf = ByteBuffer.allocate(Integer.BYTES);
        postingsChannel.read(countBuf, offset);
        int docCount = countBuf.flip().getInt();
        offset += Integer.BYTES;

        Map<Integer, List<Integer>> result = LinkedHashMap.newLinkedHashMap(docCount);
        for (int i = 0; i < docCount; i++) {
            ByteBuffer docBuf = ByteBuffer.allocate(Integer.BYTES * 2);
            postingsChannel.read(docBuf, offset);
            docBuf.flip();
            offset += Integer.BYTES * 2;

            int docId    = docBuf.getInt();
            int posCount = docBuf.getInt();

            ByteBuffer posBuf = ByteBuffer.allocate(Integer.BYTES * posCount);
            postingsChannel.read(posBuf, offset);
            posBuf.flip();
            offset += (long) Integer.BYTES * posCount;

            List<Integer> positions = new ArrayList<>(posCount);
            for (int j = 0; j < posCount; j++) positions.add(posBuf.getInt());
            result.put(docId, positions);
        }
        return result;
    }

    //
    // Binary Write Helpers
    //

    private int writeTermPostings(DataOutputStream out,
                                  Map<Integer, List<Integer>> postings) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (DataOutputStream tmp = new DataOutputStream(buf)) {
            tmp.writeInt(postings.size());
            for (var entry : postings.entrySet()) {
                tmp.writeInt(entry.getKey());
                List<Integer> pos = entry.getValue();
                tmp.writeInt(pos.size());
                for (int p : pos) tmp.writeInt(p);
            }
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

    //
    // Persistence Helpers
    //

    private void persistRegistry() throws IOException {
        writeObject(REGISTRY_FILE, globalRegistry.exportData());
        log.info("Registry saved: {} documents", globalRegistry.documentCount());
    }

    private void persistSparseOffset() throws IOException {
        writeObject(SPARSE_OFFSETS_FILE, sparseOffsets);
        log.info("Sparse offsets saved: {} terms", sparseOffsets.size());
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

    //
    // Utility
    //

    private BufferedReader openReader(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new BufferedInputStream(Files.newInputStream(file), 64 * 1024),
                StandardCharsets.UTF_8));
    }

    private DataOutputStream bufferedOutput(String path) throws IOException {
        return new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(path),
                        DISK_BUFFER_SIZE));
    }

    private long estimateTokenMemory(String token) {
        return 40L + (token.length() * 2L) + 80L;
    }

    private void closeQuietly(FileChannel file) {
        if (file != null) {
            try {
                file.close();
            } catch (IOException e) {
                log.warn("Failed to close file: {}", e.getMessage());
            }
        }
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

        System.out.println("\n" + "=".repeat(75));
        System.out.println("SPIMI RESULTS");
        System.out.println("=".repeat(75));
        System.out.printf("  Documents:       %,d%n",     m.documentsCount());
        System.out.printf("  Unique terms:    %,d%n",     m.uniqueTerms());
        System.out.printf("  Data processed:  %.2f MB%n", mb);
        System.out.printf("  Index size:      %.2f MB%n", idxMb);
        System.out.printf("  Blocks created:  %d%n",      m.blocksCreated());
        System.out.printf("  Time:            %.2f sec%n", sec);
        System.out.printf("  Throughput:      %.2f MB/s%n", mb / sec);
        System.out.println("=".repeat(75));
    }

    //
    // Inner class
    //

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
                currentPostings = HashMap.newHashMap(docCount);
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
