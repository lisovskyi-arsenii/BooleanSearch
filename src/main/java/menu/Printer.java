package menu;

import core.BooleanSearchEngine;

import java.util.List;

public final class Printer {
    private Printer() {
        throw new UnsupportedOperationException("Printer class cannot be instantiated");
    }

    private static int counter = 1;

    public static void printMenu(BooleanSearchEngine.IndexingMode mode) {
        resetCounter();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    BOOLEAN SEARCH ENGINE");
        System.out.println("=".repeat(80));

        printModeStatus(mode);

        switch (mode) {
            case NOT_INIT -> printNotInitializedMenu();
            case IN_MEMORY -> printInMemoryMenu();
            case DISK_BASED -> printDiskBasedMenu();
        }

        System.out.println();
        System.out.println("    0. Exit");
        System.out.println("=".repeat(80));
        System.out.print("Enter your choice: ");
    }

    private static void printModeStatus(BooleanSearchEngine.IndexingMode mode) {
        String statusText = switch (mode) {
            case NOT_INIT -> "NOT INITIALIZED";
            case IN_MEMORY -> "IN-MEMORY MODE (All features available)";
            case DISK_BASED -> "DISK-BASED MODE (Memory efficient)";
        };

        System.out.printf("  Current Mode: %s%n", statusText);
        System.out.println();
    }

    private static void printNotInitializedMenu() {
        printSection("INITIALIZATION - Choose Indexing Mode", List.of(
                "Index documents (IN-MEMORY - fast, requires RAM)",
                "Index large collection (DISK-BASED - SPIMI, scalable)",
                "Load saved index (switches to IN-MEMORY)"
        ));

        printSection("FILE MANAGEMENT", List.of(
                "Generate test files",
                "List files in directory",
                "Clear all files from directory"
        ));

        System.out.println("\n  Please initialize an index to access search features");
    }

    private static void printInMemoryMenu() {
        printSection("INDEXING & MANAGEMENT", List.of(
                "Reindex documents (clear + rebuild)",
                "Build wildcard indexes (BTree, ReverseBTree, 3-gram)"
        ));

        printSection("SEARCH OPERATIONS - All Available", List.of(
                "Simple search (single term)",
                "Boolean AND search (both terms)",
                "Boolean OR search (either term)",
                "Boolean NOT search (exclude term)",
                "Advanced search (complex boolean expressions)",
                "Phrase search (exact match)",
                "Proximity search (NEAR/k) - requires positional index",
                "Wildcard search (*, ?) - prefix/suffix/middle"
        ));

        printSection("ANALYTICS & STATISTICS", List.of(
                "View detailed index statistics",
                "Show top N most frequent terms"
        ));

        printSection("SERIALIZATION", List.of(
                "Save index to file (binary/text/json)",
                "Compare serialization formats (performance)"
        ));

        printSection("BENCHMARKS", List.of(
                "Compare search structures (Index/Matrix/Biword/Positional)",
                "Compare compression algorithms"
        ));

        printSection("UTILITY", List.of(
                "Clear index (reset to NOT_INIT, free memory)",
                "Generate test files",
                "List files in directory",
                "Clear all files from directory"
        ));
    }

    private static void printDiskBasedMenu() {
        printSection("SEARCH OPERATIONS - Disk I/O", List.of(
                "Simple search (single term)",
                "Boolean AND search (both terms)",
                "Boolean OR search (either term)",
                "Boolean NOT search (exclude term)",
                "Advanced search (complex boolean expressions)",
                "Phrase search (exact match)",
                "Wildcard search (*, ?) - prefix/suffix/middle"
        ));

        System.out.println("\n   UNAVAILABLE IN DISK-BASED MODE:");
        System.out.println("     ✗ Proximity search (requires full positional index in RAM)");
        System.out.println("     ✗ Structure selection (Index/Matrix/Biword - RAM only)");
        System.out.println("     ✗ Detailed statistics (requires full index in RAM)");
        System.out.println("     ✗ Index serialization (already optimized on disk)");
        System.out.println("     ✗ Performance benchmarks (requires RAM structures)");
        System.out.println();
        System.out.println("     TIP: Call clear() to switch back to IN-MEMORY mode");

        printSection("BASIC ANALYTICS", List.of(
                "View basic statistics (documents, terms, disk usage)"
        ));

        printSection("FILE MANAGEMENT", List.of(
                "Generate test files",
                "List files in directory",
                "Clear all files from directory"
        ));

        printSection("UTILITY", List.of(
                "Clear index (close disk files, reset to NOT_INIT)"
        ));
    }

    private static void printSection(String title, List<String> items) {
        System.out.printf("%n  %s%n", title);
        for (String item : items) {
            System.out.printf("    %2d. %s%n", counter++, item);
        }
    }

    private static void resetCounter() {
        counter = 1;
    }
}
