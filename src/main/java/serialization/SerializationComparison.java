package serialization;

import java.util.Comparator;
import java.util.stream.Stream;

public record SerializationComparison(FormatMetrics binary, FormatMetrics text, FormatMetrics json) {

    public SerializationComparison {
        if (binary == null || text == null || json == null) {
            throw new IllegalArgumentException("All format metrics must be non-null");
        }
    }

    public static double compareCompressionRatio(FormatMetrics f1, FormatMetrics f2) throws IllegalArgumentException {
        if (f2.sizeInBytes() == 0) {
            throw new IllegalArgumentException("Base format size cannot be zero");
        }
        return (double) f1.sizeInBytes() / (double) f2.sizeInBytes() * 100;
    }

    public void printData() {
        System.out.println("================================================================================");
        System.out.println("                    SERIALIZATION FORMAT COMPARISON");
        System.out.println("================================================================================");

        // Заголовок таблиці
        System.out.printf("%-10s | %10s | %10s | %10s | %12s | %11s%n",
                "Format", "Save Time", "Load Time", "Total Time", "File Size", "Compression");
        System.out.println("-----------|------------|------------|------------|--------------|-------------");

        // Дані для кожного формату
        printFormatRow(binary);
        printFormatRow(text);
        printFormatRow(json);

        System.out.println("================================================================================");
        System.out.println();

        // Аналіз
        System.out.println("ANALYSIS:");
        System.out.println("✓ Smallest file size:      " + getBestForSize().formatName() +
                " (" + getBestForSize().getFormattedSize() + ")");
        System.out.println("✓ Fastest serialization:   " + getBestForSaveTime().formatName() +
                " (" + getBestForSaveTime().timeSerialization() + " ms)");
        System.out.println("✓ Fastest deserialization: " + getBestForLoadTime().formatName() +
                " (" + getBestForLoadTime().timeDeserialization() + " ms)");
        System.out.println("✓ Fastest overall:         " + getBestForSpeed().formatName() +
                " (" + getBestForSpeed().totalTime() + " ms)");
        System.out.println();

        // Рекомендація
        FormatMetrics best = getBestInTotal();
        System.out.println("RECOMMENDATION: Use " + best.formatName() + " format for best overall performance");
        System.out.println("================================================================================");
    }

    public FormatMetrics getBestForSize() throws IllegalArgumentException {
        return Stream.of(binary, text, json)
                .min(Comparator.comparingLong(FormatMetrics::sizeInBytes))
                .orElseThrow(() -> new IllegalStateException("No metrics available"));
    }

    public FormatMetrics getBestForSaveTime() {
        return Stream.of(binary, text, json)
                .min(Comparator.comparingLong(FormatMetrics::timeSerialization))
                .orElseThrow(() -> new IllegalStateException("No metrics available"));
    }

    public FormatMetrics getBestForLoadTime() {
        return Stream.of(binary, text, json)
                .min(Comparator.comparingLong(FormatMetrics::timeDeserialization))
                .orElseThrow(() -> new IllegalStateException("No metrics available"));
    }

    public FormatMetrics getBestForSpeed() {
        return Stream.of(binary, text, json)
                .min(Comparator.comparingLong(FormatMetrics::totalTime))
                .orElseThrow(() -> new IllegalStateException("No metrics available"));
    }

    public FormatMetrics getBestInTotal() {
        int binaryScore = 0;
        int textScore = 0;
        int jsonScore = 0;

        FormatMetrics[] bySize = sortBySize();
        binaryScore += getScore(binary, bySize);
        textScore += getScore(text, bySize);
        jsonScore += getScore(json, bySize);

        FormatMetrics[] bySaveTime = sortBySaveTime();
        binaryScore += getScore(binary, bySaveTime);
        textScore += getScore(text, bySaveTime);
        jsonScore += getScore(json, bySaveTime);

        FormatMetrics[] byLoadTime = sortByLoadTime();
        binaryScore += getScore(binary, byLoadTime);
        textScore += getScore(text, byLoadTime);
        jsonScore += getScore(json, byLoadTime);

        int maxScore = Math.max(Math.max(binaryScore, textScore), jsonScore);

        if (binaryScore == maxScore) return binary;
        else if (textScore == maxScore) return text;
        return json;
    }

    private FormatMetrics[] sortBySize() {
        return Stream.of(binary, text, json)
                .sorted(Comparator.comparingLong(FormatMetrics::sizeInBytes))
                .toArray(FormatMetrics[]::new);
    }

    private FormatMetrics[] sortBySaveTime() {
        return Stream.of(binary, text, json)
                .sorted(Comparator.comparingLong(FormatMetrics::timeSerialization))
                .toArray(FormatMetrics[]::new);
    }

    private FormatMetrics[] sortByLoadTime() {
        return Stream.of(binary, text, json)
                .sorted(Comparator.comparingLong(FormatMetrics::timeDeserialization))
                .toArray(FormatMetrics[]::new);
    }

    private int getScore(FormatMetrics metrics, FormatMetrics[] sorted) {
        for (int i = 0; i < sorted.length; i++) {
            if (sorted[i] == metrics) {
                return 3 - i;
            }
        }
        return 0;
    }

    private void printFormatRow(FormatMetrics metrics) {
        double compressionRatio = compareCompressionRatio(metrics, binary);

        System.out.printf("%-10s | %8d ms | %8d ms | %8d ms | %12s | %10.1f%%%n",
                metrics.formatName(),
                metrics.timeSerialization(),
                metrics.timeDeserialization(),
                metrics.totalTime(),
                metrics.getFormattedSize(),
                compressionRatio
        );
    }

}
