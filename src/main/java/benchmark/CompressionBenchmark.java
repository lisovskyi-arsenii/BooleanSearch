package benchmark;

import compression.FrontCoding;
import compression.GammaCode;
import compression.VariableByteCode;
import enums.CompressionMethod;
import index.InvertedIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class CompressionBenchmark {
    private static final int WARMUP_ITERATIONS    = 3;
    private static final int BENCHMARK_ITERATIONS = 10;

    public void runAllBenchmarks(InvertedIndex original) {
        if (original == null || original.getAllTerms().isEmpty()) {
            System.out.println("Index is empty — nothing to benchmark");
            return;
        }

        System.out.println("=".repeat(80));
        System.out.println("COMPRESSION BENCHMARK");
        System.out.printf("  Terms in index: %,d%n", original.getAllTerms().size());
        System.out.println("=".repeat(80));

        List<CompressionResult> results = new ArrayList<>();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BENCHMARKING: Variable Byte Code (VBC)");
        System.out.println("=".repeat(80));
        results.add(benchmarkMethod(original, CompressionMethod.VBC));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BENCHMARKING: Gamma Code");
        System.out.println("=".repeat(80));
        results.add(benchmarkMethod(original, CompressionMethod.G));

        printComparison(results);
    }

    private CompressionResult benchmarkMethod(InvertedIndex original, CompressionMethod method) {
        List<String> sortedTerms = new ArrayList<>(original.getAllTerms());
        Collections.sort(sortedTerms);

        System.out.printf("Warming up (%d iterations)...%n", WARMUP_ITERATIONS);
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CompressedData data = compress(sortedTerms, original, method);
            InvertedIndex decompressed = decompress(data, method);
            if (decompressed == null) throw new RuntimeException("Warmup failed");
        }

        System.out.printf("Measuring compression (%d iterations)...%n", BENCHMARK_ITERATIONS);
        long[] compressTimes = new long[BENCHMARK_ITERATIONS];
        CompressedData finalData = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            CompressedData data = compress(sortedTerms, original, method);
            compressTimes[i] = System.nanoTime() - start;
            if (i == 0) finalData = data;
        }

        long avgCompressTime = trimmedAverage(compressTimes);

        System.out.printf("Measuring decompression (%d iterations)...%n", BENCHMARK_ITERATIONS);
        long[] decompressTimes = new long[BENCHMARK_ITERATIONS];
        InvertedIndex lastDecompressed = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            InvertedIndex decompressed = decompress(finalData, method);
            decompressTimes[i] = System.nanoTime() - start;
            if (i == BENCHMARK_ITERATIONS - 1) lastDecompressed = decompressed;
        }

        long avgDecompressTime = trimmedAverage(decompressTimes);

        System.out.println("Verifying integrity...");
        if (lastDecompressed == null) throw new IllegalStateException("Decompression failed");

        boolean valid = verifyIndices(original, lastDecompressed);
        if (!valid) System.err.println("WARNING: Decompressed data does not match original!");

        printMethodResults(method, finalData, avgCompressTime, avgDecompressTime, valid);

        return new CompressionResult(
                method,
                finalData.originalDictSize + finalData.originalPostingsSize,
                finalData.compressedDictSize + finalData.compressedPostingsSize,
                finalData.originalDictSize,
                finalData.compressedDictSize,
                finalData.originalPostingsSize,
                finalData.compressedPostingsSize,
                avgCompressTime,
                avgDecompressTime,
                valid
        );
    }

    private CompressedData compress(List<String> sortedTerms,
                                    InvertedIndex original,
                                    CompressionMethod method) {
        FrontCoding.CompressedDictionary dict = FrontCoding.compress(
                new ArrayList<>(sortedTerms), 8);

        Map<String, byte[]> postings = new LinkedHashMap<>();
        long originalPostingsSize    = 0;
        long compressedPostingsSize  = 0;

        for (String term : sortedTerms) {
            List<Integer> docIds = new ArrayList<>(
                    original.getDocuments(term).orElse(Collections.emptySet()));
            Collections.sort(docIds);

            byte[] encoded = switch (method) {
                case VBC -> VariableByteCode.encodeWithGaps(docIds);
                case G   -> GammaCode.encodeWithGaps(docIds);
            };

            postings.put(term, encoded);
            originalPostingsSize   += (long) docIds.size() * Integer.BYTES;
            compressedPostingsSize += encoded.length;
        }

        return new CompressedData(
                dict,
                postings,
                (long) dict.originalSize(),
                (long) dict.compressedSize(),
                originalPostingsSize,
                compressedPostingsSize
        );
    }

    private InvertedIndex decompress(CompressedData data, CompressionMethod method) {
        List<String> terms = FrontCoding.decompress(data.dict);
        InvertedIndex result = new InvertedIndex();

        for (String term : terms) {
            byte[] encoded = data.postings.get(term);
            if (encoded == null) continue;

            List<Integer> docIds = switch (method) {
                case VBC -> VariableByteCode.decodeWithGaps(encoded);
                case G   -> GammaCode.decodeWithGaps(encoded);
            };

            for (int docId : docIds) {
                result.addTerm(term, docId);
            }
        }

        return result;
    }

    private boolean verifyIndices(InvertedIndex original, InvertedIndex decompressed) {
        Set<String> originalTerms     = original.getAllTerms();
        Set<String> decompressedTerms = decompressed.getAllTerms();

        if (originalTerms.size() != decompressedTerms.size()) {
            log.error("Terms count mismatch: {} vs {}",
                    originalTerms.size(), decompressedTerms.size());
            return false;
        }

        if (!originalTerms.equals(decompressedTerms)) {
            log.error("Terms set mismatch");
            return false;
        }

        for (String term : originalTerms) {
            Optional<Set<Integer>> orig   = original.getDocuments(term);
            Optional<Set<Integer>> decomp = decompressed.getDocuments(term);
            if (!orig.equals(decomp)) {
                log.error("Posting list mismatch for term: '{}'", term);
                return false;
            }
        }

        return true;
    }

    private long trimmedAverage(long[] times) {
        Arrays.sort(times);
        return Arrays.stream(times, 1, times.length - 1)
                .sum() / (times.length - 2);
    }

    private void printMethodResults(CompressionMethod method,
                                    CompressedData data,
                                    long compressNs,
                                    long decompressNs,
                                    boolean valid) {
        long totalOrig  = data.originalDictSize    + data.originalPostingsSize;
        long totalComp  = data.compressedDictSize  + data.compressedPostingsSize;
        double ratio    = (double) totalComp / totalOrig * 100;

        System.out.printf("%nResults for %s:%n", method);
        System.out.println("-".repeat(80));
        System.out.printf("Original size:      %,d bytes (%.2f MB)%n",
                totalOrig, totalOrig / (1024.0 * 1024.0));
        System.out.printf("Compressed size:    %,d bytes (%.2f MB)%n",
                totalComp, totalComp / (1024.0 * 1024.0));
        System.out.printf("Compression ratio:  %.2f%%%n", ratio);
        System.out.println();
        System.out.printf("Dictionary:         %,d → %,d bytes (%.2f%%)%n",
                data.originalDictSize, data.compressedDictSize,
                (double) data.compressedDictSize / data.originalDictSize * 100);
        System.out.printf("Postings:           %,d → %,d bytes (%.2f%%)%n",
                data.originalPostingsSize, data.compressedPostingsSize,
                (double) data.compressedPostingsSize / data.originalPostingsSize * 100);
        System.out.println();
        System.out.printf("Compression time:   %.2f ms (avg of %d runs)%n",
                compressNs / 1_000_000.0, BENCHMARK_ITERATIONS);
        System.out.printf("Decompression time: %.2f ms (avg of %d runs)%n",
                decompressNs / 1_000_000.0, BENCHMARK_ITERATIONS);
        System.out.printf("Verification:       %s%n", valid ? "PASSED" : "FAILED");
    }

    private void printComparison(List<CompressionResult> results) {
        if (results.size() != 2) return;

        CompressionResult vbc   = results.get(0);
        CompressionResult gamma = results.get(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPARISON: VBC vs GAMMA");
        System.out.println("=".repeat(80));

        System.out.println("\nCompression ratios:");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s %15s %15s %15s%n", "Component", "VBC", "Gamma", "Winner");
        System.out.println("-".repeat(80));
        printCompRow("Dictionary",
                vbc.getCompressionRatio("dictionary"),
                gamma.getCompressionRatio("dictionary"));
        printCompRow("Postings",
                vbc.getCompressionRatio("postings"),
                gamma.getCompressionRatio("postings"));
        printCompRow("Total",
                vbc.getCompressionRatio("total"),
                gamma.getCompressionRatio("total"));

        System.out.println("\nPerformance:");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s %15s %15s %15s%n", "Operation", "VBC (ms)", "Gamma (ms)", "Winner");
        System.out.println("-".repeat(80));
        printTimeRow("Compression",   vbc.compressTimeNanos(),   gamma.compressTimeNanos());
        printTimeRow("Decompression", vbc.decompressTimeNanos(), gamma.decompressTimeNanos());

        double vbcRatio     = vbc.getCompressionRatio("total");
        double gammaRatio   = gamma.getCompressionRatio("total");
        double vbcTotalNs   = vbc.compressTimeNanos()   + vbc.decompressTimeNanos();
        double gammaTotalNs = gamma.compressTimeNanos() + gamma.decompressTimeNanos();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(80));
        System.out.printf("Space savings:%n");
        System.out.printf("  VBC:   %.2f MB saved (%.1f%% reduction)%n",
                (vbc.originalSize() - vbc.compressedSize()) / (1024.0 * 1024.0),
                100 - vbcRatio);
        System.out.printf("  Gamma: %.2f MB saved (%.1f%% reduction)%n",
                (gamma.originalSize() - gamma.compressedSize()) / (1024.0 * 1024.0),
                100 - gammaRatio);
        System.out.printf("%nBest for size:  %s (%.2f%% smaller)%n",
                vbcRatio < gammaRatio ? "VBC" : "Gamma",
                Math.abs(vbcRatio - gammaRatio));
        System.out.printf("Best for speed: %s (%.2fx faster)%n",
                vbcTotalNs < gammaTotalNs ? "VBC" : "Gamma",
                Math.max(vbcTotalNs, gammaTotalNs) / Math.min(vbcTotalNs, gammaTotalNs));
        System.out.println("=".repeat(80));
    }

    private void printCompRow(String label, double vbcRatio, double gammaRatio) {
        System.out.printf("%-20s %14.2f%% %14.2f%% %15s%n",
                label, vbcRatio, gammaRatio,
                vbcRatio < gammaRatio ? "VBC" : "Gamma");
    }

    private void printTimeRow(String label, long vbcNs, long gammaNs) {
        System.out.printf("%-20s %15.2f %15.2f %15s%n",
                label,
                vbcNs / 1_000_000.0,
                gammaNs / 1_000_000.0,
                vbcNs < gammaNs ? "VBC" : "Gamma");
    }

    private record CompressedData(
            FrontCoding.CompressedDictionary dict,
            Map<String, byte[]> postings,
            long originalDictSize,
            long compressedDictSize,
            long originalPostingsSize,
            long compressedPostingsSize
    ) {}

    public record CompressionResult(
            CompressionMethod method,
            long originalSize,
            long compressedSize,
            long originalDictionarySize,
            long compressedDictionarySize,
            long originalPostingsSize,
            long compressedPostingsSize,
            long compressTimeNanos,
            long decompressTimeNanos,
            boolean valid
    ) {
        public double getCompressionRatio(String component) {
            return switch (component.toLowerCase()) {
                case "dictionary" ->
                        (double) compressedDictionarySize / originalDictionarySize * 100;
                case "postings"   ->
                        (double) compressedPostingsSize   / originalPostingsSize   * 100;
                case "total"      ->
                        (double) compressedSize           / originalSize           * 100;
                default -> 0.0;
            };
        }
    }
}
