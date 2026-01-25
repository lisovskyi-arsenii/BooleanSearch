package benchmark;

import core.BooleanSearchEngine;
import enums.SearchStructureType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PerformanceBenchmark {
    private final BooleanSearchEngine searchEngine;
    private static final int WARMUP_ITERATIONS = 5;
    private static final int BENCHMARK_ITERATIONS = 100;

    public PerformanceBenchmark(BooleanSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
    }

    public BenchmarkResult compareSearch(String term) {
        warmup(() -> searchEngine.search(term, SearchStructureType.INDEX));
        warmup(() -> searchEngine.search(term, SearchStructureType.MATRIX));

        // Вимірювання
        long indexTime = measureSearch(() ->
                searchEngine.search(term, SearchStructureType.INDEX));

        long matrixTime = measureSearch(() ->
                searchEngine.search(term, SearchStructureType.MATRIX));

        return new BenchmarkResult("SEARCH", term, indexTime, matrixTime);
    }

    public BenchmarkResult compareAndSearch(String term1, String term2) {
        warmup(() -> searchEngine.andSearch(term1, term2, SearchStructureType.INDEX));
        warmup(() -> searchEngine.andSearch(term1, term2, SearchStructureType.MATRIX));

        long indexTime = measureSearch(() ->
                searchEngine.andSearch(term1, term2, SearchStructureType.INDEX));

        long matrixTime = measureSearch(() ->
                searchEngine.andSearch(term1, term2, SearchStructureType.MATRIX));

        return new BenchmarkResult("AND", term1 + " AND " + term2, indexTime, matrixTime);
    }

    public BenchmarkResult compareOrSearch(String term1, String term2) {
        warmup(() -> searchEngine.orSearch(term1, term2, SearchStructureType.INDEX));
        warmup(() -> searchEngine.orSearch(term1, term2, SearchStructureType.MATRIX));

        long indexTime = measureSearch(() ->
                searchEngine.orSearch(term1, term2, SearchStructureType.INDEX));

        long matrixTime = measureSearch(() ->
                searchEngine.orSearch(term1, term2, SearchStructureType.MATRIX));

        return new BenchmarkResult("OR", term1 + " OR " + term2, indexTime, matrixTime);
    }

    public BenchmarkResult compareNotSearch(String term) {
        Set<Integer> allDocs = searchEngine.getAllDocumentIDs();

        warmup(() -> searchEngine.notSearch(term, allDocs, SearchStructureType.INDEX));
        warmup(() -> searchEngine.notSearch(term, allDocs, SearchStructureType.MATRIX));

        long indexTime = measureSearch(() ->
                searchEngine.notSearch(term, allDocs, SearchStructureType.INDEX));

        long matrixTime = measureSearch(() ->
                searchEngine.notSearch(term, allDocs, SearchStructureType.MATRIX));

        return new BenchmarkResult("NOT", "NOT " + term, indexTime, matrixTime);
    }


    public List<BenchmarkResult> runAllBenchmarks(List<String> testTerms) {
        List<BenchmarkResult> results = new ArrayList<>();

        log.info("Starting performance benchmarks...");
        System.out.println("\n" + "=".repeat(80));
        System.out.println("PERFORMANCE BENCHMARK - Index vs Matrix");
        System.out.println("=".repeat(80));

        // Simple search
        for (String term : testTerms) {
            results.add(compareSearch(term));
        }

        // AND operations
        if (testTerms.size() >= 2) {
            results.add(compareAndSearch(testTerms.get(0), testTerms.get(1)));
            if (testTerms.size() >= 4) {
                results.add(compareAndSearch(testTerms.get(2), testTerms.get(3)));
            }
        }

        // OR operations
        if (testTerms.size() >= 2) {
            results.add(compareOrSearch(testTerms.get(0), testTerms.get(1)));
        }

        // NOT operations
        if (!testTerms.isEmpty()) {
            results.add(compareNotSearch(testTerms.getFirst()));
        }

        printResults(results);
        return results;
    }

    private void warmup(Runnable operation) {
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            operation.run();
        }
    }

    private long measureSearch(Runnable operation) {
        long totalTime = 0;

        for (int i = 0; i < BENCHMARK_ITERATIONS; i++) {
            long start = System.nanoTime();
            operation.run();
            long end = System.nanoTime();
            totalTime += (end - start);
        }

        return totalTime / BENCHMARK_ITERATIONS;
    }

    private void printResults(List<BenchmarkResult> results) {
        System.out.println("\n" + "=".repeat(80));
        System.out.printf("%-10s %-30s %15s %15s %15s%n",
                "Operation", "Query", "Index (μs)", "Matrix (μs)", "Winner");
        System.out.println("-".repeat(80));

        for (BenchmarkResult result : results) {
            System.out.printf("%-10s %-30s %15.3f %15.3f %15s%n",
                    result.operation(),
                    truncate(result.query(), 30),
                    result.indexTimeNanos() / 1000.0,
                    result.matrixTimeNanos() / 1000.0,
                    result.getWinner()
            );
        }

        System.out.println("=".repeat(80));
        printSummary(results);
    }

    private void printSummary(List<BenchmarkResult> results) {
        long indexWins = results.stream()
                .filter(r -> r.indexTimeNanos() < r.matrixTimeNanos())
                .count();

        long matrixWins = results.size() - indexWins;

        double avgSpeedup = results.stream()
                .mapToDouble(r -> (double) r.matrixTimeNanos() / r.indexTimeNanos())
                .average()
                .orElse(1.0);

        System.out.println("\nSUMMARY:");
        System.out.printf("  Index wins: %d/%d%n", indexWins, results.size());
        System.out.printf("  Matrix wins: %d/%d%n", matrixWins, results.size());
        System.out.printf("  Average speedup (Index/Matrix): %.2fx%n", avgSpeedup);
        System.out.println();
    }

    private String truncate(String str, int maxLength) {
        if (str.length() <= maxLength) return str;
        return str.substring(0, maxLength - 3) + "...";
    }


    public record BenchmarkResult(
            String operation,
            String query,
            long indexTimeNanos,
            long matrixTimeNanos
    ) {
        public String getWinner() {
            if (indexTimeNanos < matrixTimeNanos) {
                double speedup = (double) matrixTimeNanos / indexTimeNanos;
                return String.format("Index (%.2fx)", speedup);
            } else if (matrixTimeNanos < indexTimeNanos) {
                double speedup = (double) indexTimeNanos / matrixTimeNanos;
                return String.format("Matrix (%.2fx)", speedup);
            } else {
                return "Tie";
            }
        }

        public double getSpeedup() {
            return (double) Math.max(indexTimeNanos, matrixTimeNanos)
                    / Math.min(indexTimeNanos, matrixTimeNanos);
        }
    }
}
