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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
public class SPIMI {
    private static final long MEMORY_THRESHOLD_BYTES = 150 * 1024 * 1024L;
    private static final String TEMP_DIR = "temp_blocks";
    private static final String POSTINGS_FILE = "postings.dat";
    private static final String OFFSETS_FILE = "offsets.bin";
    private static final String REGISTRY_FILE = "registry.dat";

    // term -> offset in disk
    private final Map<String, Long> termOffsets = new HashMap<>();

    private final DocumentRegistry globalRegistry = new DocumentRegistry();

    @Getter private final AtomicInteger blocksCreated = new AtomicInteger(0);
    @Getter private final AtomicInteger documentsIndexed = new AtomicInteger(0);
    @Getter private final AtomicLong bytesProcessed = new AtomicLong(0);

    public void buildIndex(String directoryPath) throws IOException {
        long startTime = System.currentTimeMillis();
        log.info("Starting SPIMI indexing from: {}", directoryPath);

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
        List<String> createdBlocks = new ArrayList<>();
        PositionalIndex currentBlock = new PositionalIndex();
        Runtime runtime = Runtime.getRuntime();

        int processedFiles = 0;
        int termsInBlock = 0;

        for (Path file : files) {
            String filename = file.getFileName().toString();
            long fileSize = Files.size(file);
            int docId = globalRegistry.registerDocument(filename, fileSize);

            try (BufferedReader reader = createReader(file)) {
                String line;
                int position = 0;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;

                    for (String token : Tokenizer.tokenize(line)) {
                        currentBlock.addTerm(token, docId, position++);
                        termsInBlock++;

                        if (termsInBlock % 10000 == 0) {
                            long usedMemory = runtime.totalMemory() - runtime.freeMemory();
                            if (usedMemory >= MEMORY_THRESHOLD_BYTES) {
                                log.info("Memory threshold reached ({} MB). Flushing block with {} terms",
                                        usedMemory / (1024 * 1024), currentBlock.size());

                                createdBlocks.add(saveBlockToDisk(currentBlock));
                                currentBlock = new PositionalIndex();
                                termsInBlock = 0;
                                //
                                Thread.yield();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Error indexing file: {}", filename, e);
                System.err.println("Error indexing file: " + filename);
            }

            bytesProcessed.addAndGet(fileSize);
            documentsIndexed.incrementAndGet();
            processedFiles++;

            if (processedFiles % 50 == 0) {
                log.info("Processed {} / {} files", processedFiles, files.size());
            }
        }

        if (currentBlock.size() > 0) {
            createdBlocks.add(saveBlockToDisk(currentBlock));
        }

        log.info("Block creation complete: {} blocks created", createdBlocks.size());

        return createdBlocks;
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

    private void mergeBlocksWithOffsets(List<String> blockFiles) throws IOException {
        log.info("Starting K-way merge of {} blocks...", blockFiles.size());

        PriorityQueue<BlockReader> queue = new PriorityQueue<>(
                Comparator.comparing(BlockReader::peekTerm)
        );

        for (String file : blockFiles) {
            BlockReader reader = new BlockReader(file);
            if (reader.hasNext()) {
                queue.add(reader);
            }
        }

        // counter for bytes in a file
        long currentOffset = 0;

        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(new FileOutputStream(POSTINGS_FILE), 65536))) {

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

        // записуємо у файл
        byte[] data = baos.toByteArray();
        out.write(data);

        // повертаємо розмір
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
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".bz2")) {
            log.debug("Decompressing .bz2 file: {}", name);
            return new BufferedReader(new InputStreamReader(
                    new BZip2CompressorInputStream(Files.newInputStream(file)),
                    StandardCharsets.UTF_8));
        }
        return Files.newBufferedReader(file, StandardCharsets.UTF_8);
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

    private static class BlockReader {
        private final DataInputStream in;
        private String currentTerm;
        private Map<Integer, List<Integer>> currentPostings;

        public BlockReader(String filename) throws IOException {
            this.in = new DataInputStream(
                    new BufferedInputStream(new FileInputStream(filename), 65536));
            readNext();
        }

        private void readNext() {
            try {
                if (in.available() > 0) {
                    currentTerm = in.readUTF();
                    int docCount = in.readInt();
                    currentPostings = new HashMap<>();

                    for (int i = 0; i < docCount; i++) {
                        int docId = in.readInt();
                        int posCount = in.readInt();
                        List<Integer> positions = new ArrayList<>(posCount);
                        for (int j = 0; j < posCount; j++) {
                            positions.add(in.readInt());
                        }
                        currentPostings.put(docId, positions);
                    }
                } else {
                    currentTerm = null;
                    in.close();
                }
            } catch (IOException _) {
                currentTerm = null;
                try {
                    in.close();
                } catch (IOException _) {
                }
            }
        }

        public boolean hasNext() {
            return currentTerm != null;
        }

        public String peekTerm() {
            return currentTerm;
        }

        public Map<Integer, List<Integer>> nextPostings() {
            var postings = currentPostings;
            readNext();
            return postings;
        }

        public void close() throws IOException {
            if (in != null) {
                in.close();
            }
        }
    }
}
