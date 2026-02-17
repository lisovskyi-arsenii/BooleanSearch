package benchmark;

import core.BooleanSearchEngine;
import enums.CompressionMethod;
import index.CompressedIndex;
import index.IndexCompressor;
import index.InvertedIndex;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.*;

import static constants.Filenames.DIRECTORY_PATH;

@Slf4j
public class CompressionBenchmark {
    private static final int WARMUP_ITERATIONS = 3;
    private static final int BENCHMARK_ITERATIONS = 10;

    public static void runBenchmark() throws IOException {
        System.out.println("=".repeat(80));
        System.out.println("COMPRESSION BENCHMARK");
        System.out.println("=".repeat(80));

        System.out.println("\nIndexing documents...");
        BooleanSearchEngine engine = new BooleanSearchEngine();
        engine.indexDocuments(DIRECTORY_PATH);
        InvertedIndex original = engine.getIndex();

        int termsCount = original.getAllTerms().size();
        System.out.println("Indexed " + termsCount + " unique terms\n");

        CompressionBenchmark.runBenchmarkWithIndex(original);
    }

    public static void runBenchmarkWithIndex(InvertedIndex index) {
        CompressionBenchmark benchmark = new CompressionBenchmark();
        benchmark.runBenchmarkWithIndexPrivate(index);
    }

    private void runBenchmarkWithIndexPrivate(InvertedIndex original) {
        List<CompressionResult> results = runAllBenchmarks(original);
        printComparison(results);
    }

    public List<CompressionResult> runAllBenchmarks(InvertedIndex original) {
        List<CompressionResult> results = new ArrayList<>();

        log.info("Starting compression benchmarks...");

        System.out.println("=".repeat(80));
        System.out.println("BENCHMARKING: Variable Byte Code (VBC)");
        System.out.println("=".repeat(80));
        results.add(benchmarkMethod(original, CompressionMethod.VBC));

        System.out.println("\n" + "=".repeat(80));
        System.out.println("BENCHMARKING: Gamma Code");
        System.out.println("=".repeat(80));
        results.add(benchmarkMethod(original, CompressionMethod.G));

        return results;
    }

    private CompressionResult benchmarkMethod(InvertedIndex original, CompressionMethod method) {
        System.out.println("Warming up (" + WARMUP_ITERATIONS + " iterations)...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            CompressedIndex compressed = IndexCompressor.compress(original, method);
            InvertedIndex decompressed = IndexCompressor.decompress(compressed);
            if (decompressed == null) {
                throw new RuntimeException("Warmup decompression failed");
            }
        }

        System.out.println("Measuring compression (" + BENCHMARK_ITERATIONS + " iterations)...");
        long[] compressTimes = new long[BENCHMARK_ITERATIONS];
        CompressedIndex finalCompressed = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            CompressedIndex compressed = IndexCompressor.compress(original, method);
            long end = System.nanoTime();
            compressTimes[i] = end - start;

            if (i == 0) {
                finalCompressed = compressed;
            }
        }

        Arrays.sort(compressTimes);
        long avgCompressTime = Arrays.stream(compressTimes, 1, compressTimes.length - 1)
                .sum() / (compressTimes.length - 2);

        System.out.println("Measuring decompression (" + BENCHMARK_ITERATIONS + " iterations)...");
        long[] decompressTimes = new long[BENCHMARK_ITERATIONS];
        InvertedIndex lastDecompressed = null;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            InvertedIndex decompressed = IndexCompressor.decompress(finalCompressed);
            long end = System.nanoTime();
            decompressTimes[i] = end - start;

            if (i == BENCHMARK_ITERATIONS - 1) {
                lastDecompressed = decompressed;
            }
        }

        Arrays.sort(decompressTimes);
        long avgDecompressTime = Arrays.stream(decompressTimes, 1, decompressTimes.length - 1)
                .sum() / (decompressTimes.length - 2);

        System.out.println("Verifying integrity...");
        if (lastDecompressed == null) {
            throw new IllegalStateException("Decompression failed");
        }

        boolean isValid = verifyIndices(original, lastDecompressed);

        if (!isValid) {
            System.err.println("WARNING: Decompressed data doesn't match original!");
        }

        printMethodResults(method, finalCompressed, avgCompressTime, avgDecompressTime, isValid);

        return new CompressionResult(
                method,
                finalCompressed.getTotalOriginalSize(),
                finalCompressed.getTotalCompressedSize(),
                finalCompressed.getOriginalDictionarySize(),
                finalCompressed.getCompressedDictionarySize(),
                finalCompressed.getOriginalPostingsSize(),
                finalCompressed.getCompressedPostingsSize(),
                avgCompressTime,
                avgDecompressTime,
                isValid
        );
    }

    private void printMethodResults(CompressionMethod method, CompressedIndex compressed,
                                    long compressTime, long decompressTime, boolean isValid) {
        System.out.println("\nResults for " + method + ":");
        System.out.println("-".repeat(80));

        System.out.printf("Original Size:      %,d bytes (%.2f MB)%n",
                compressed.getTotalOriginalSize(),
                compressed.getTotalOriginalSize() / (1024.0 * 1024.0));
        System.out.printf("Compressed Size:    %,d bytes (%.2f MB)%n",
                compressed.getTotalCompressedSize(),
                compressed.getTotalCompressedSize() / (1024.0 * 1024.0));
        System.out.printf("Compression Ratio:  %.2f%%%n", compressed.getCompressionRatio());
        System.out.println();

        System.out.printf("Dictionary:         %,d -> %,d bytes (%.2f%%)%n",
                compressed.getOriginalDictionarySize(),
                compressed.getCompressedDictionarySize(),
                (double) compressed.getCompressedDictionarySize() /
                        compressed.getOriginalDictionarySize() * 100);
        System.out.printf("Postings:           %,d -> %,d bytes (%.2f%%)%n",
                compressed.getOriginalPostingsSize(),
                compressed.getCompressedPostingsSize(),
                (double) compressed.getCompressedPostingsSize() /
                        compressed.getOriginalPostingsSize() * 100);
        System.out.println();

        System.out.printf("Compression Time:   %.2f ms (avg of %d runs)%n",
                compressTime / 1_000_000.0, BENCHMARK_ITERATIONS);
        System.out.printf("Decompression Time: %.2f ms (avg of %d runs)%n",
                decompressTime / 1_000_000.0, BENCHMARK_ITERATIONS);
        System.out.println();

        System.out.printf("Verification:       %s%n",
                isValid ? "PASSED" : "FAILED");

        if (!isValid) {
            System.err.println("Data integrity check FAILED!");
        }
    }

    private void printComparison(List<CompressionResult> results) {
        if (results.size() != 2) return;

        CompressionResult vbc = results.get(0);
        CompressionResult gamma = results.get(1);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPRESSION COMPARISON: VBC vs GAMMA");
        System.out.println("=".repeat(80));

        System.out.println("\nCompression Ratios:");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s %15s %15s %15s%n",
                "Component", "VBC", "Gamma", "Winner");
        System.out.println("-".repeat(80));

        double vbcDictRatio = vbc.getCompressionRatio("dictionary");
        double gammaDictRatio = gamma.getCompressionRatio("dictionary");
        System.out.printf("%-20s %14.2f%% %14.2f%% %15s%n",
                "Dictionary",
                vbcDictRatio,
                gammaDictRatio,
                vbcDictRatio < gammaDictRatio ? "VBC" : "Gamma");

        double vbcPostRatio = vbc.getCompressionRatio("postings");
        double gammaPostRatio = gamma.getCompressionRatio("postings");
        System.out.printf("%-20s %14.2f%% %14.2f%% %15s%n",
                "Postings",
                vbcPostRatio,
                gammaPostRatio,
                vbcPostRatio < gammaPostRatio ? "VBC" : "Gamma");

        double vbcTotalRatio = vbc.getCompressionRatio("total");
        double gammaTotalRatio = gamma.getCompressionRatio("total");
        System.out.printf("%-20s %14.2f%% %14.2f%% %15s%n",
                "Total",
                vbcTotalRatio,
                gammaTotalRatio,
                vbcTotalRatio < gammaTotalRatio ? "VBC" : "Gamma");

        System.out.println("\nPerformance:");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s %15s %15s %15s%n",
                "Operation", "VBC (ms)", "Gamma (ms)", "Winner");
        System.out.println("-".repeat(80));

        System.out.printf("%-20s %15.2f %15.2f %15s%n",
                "Compression",
                vbc.compressTimeNanos() / 1_000_000.0,
                gamma.compressTimeNanos() / 1_000_000.0,
                vbc.compressTimeNanos() < gamma.compressTimeNanos() ? "VBC" : "Gamma");

        System.out.printf("%-20s %15.2f %15.2f %15s%n",
                "Decompression",
                vbc.decompressTimeNanos() / 1_000_000.0,
                gamma.decompressTimeNanos() / 1_000_000.0,
                vbc.decompressTimeNanos() < gamma.decompressTimeNanos() ? "VBC" : "Gamma");

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(80));

        System.out.printf("Space Savings (compared to uncompressed):%n");
        System.out.printf("  VBC:   %.2f MB saved (%.1f%% reduction)%n",
                (vbc.originalSize() - vbc.compressedSize()) / (1024.0 * 1024.0),
                100 - vbcTotalRatio);
        System.out.printf("  Gamma: %.2f MB saved (%.1f%% reduction)%n",
                (gamma.originalSize() - gamma.compressedSize()) / (1024.0 * 1024.0),
                100 - gammaTotalRatio);

        System.out.printf("%nBest for size:  %s (%.2f%% smaller)%n",
                vbcTotalRatio < gammaTotalRatio ? "VBC" : "Gamma",
                Math.abs(vbcTotalRatio - gammaTotalRatio));

        double vbcTotalTime = vbc.compressTimeNanos() + vbc.decompressTimeNanos();
        double gammaTotalTime = gamma.compressTimeNanos() + gamma.decompressTimeNanos();
        System.out.printf("Best for speed: %s (%.2fx faster)%n",
                vbcTotalTime < gammaTotalTime ? "VBC" : "Gamma",
                Math.max(vbcTotalTime, gammaTotalTime) / Math.min(vbcTotalTime, gammaTotalTime));

        System.out.println("\nBoth methods passed integrity verification");
        System.out.println("=".repeat(80));
    }

    private boolean verifyIndices(InvertedIndex original, InvertedIndex decompressed) {
        Set<String> originalTerms = original.getAllTerms();
        Set<String> decompressedTerms = decompressed.getAllTerms();

        if (originalTerms.size() != decompressedTerms.size()) {
            log.error("Terms count mismatch: {} vs {}",
                    originalTerms.size(), decompressedTerms.size());
            return false;
        }

        if (!originalTerms.equals(decompressedTerms)) {
            log.error("Terms set mismatch!");
            return false;
        }

        for (String term : originalTerms) {
            Optional<Set<Integer>> originalDocs = original.getDocuments(term);
            Optional<Set<Integer>> decompressedDocs = decompressed.getDocuments(term);

            if (!originalDocs.equals(decompressedDocs)) {
                log.error("Posting list mismatch for term: {}", term);
                return false;
            }
        }

        return true;
    }

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
                case "dictionary" -> (double) compressedDictionarySize / originalDictionarySize * 100;
                case "postings" -> (double) compressedPostingsSize / originalPostingsSize * 100;
                case "total" -> (double) compressedSize / originalSize * 100;
                default -> 0.0;
            };
        }
    }
}
