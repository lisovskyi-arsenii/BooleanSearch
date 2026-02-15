package menu;

import java.util.List;

public final class Printer {
    private Printer() {
        throw new UnsupportedOperationException("Printer class cannot be instantiated");
    }

    private static int counter = 1;

    public static void printMenu() {
        resetCounter();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    BOOLEAN SEARCH ENGINE");
        System.out.println("=".repeat(80));

        printSection("INDEXING", List.of(
                "Index documents from directory",
                "Reindex (clear + index)",
                "Generate files",
                "List files",
                "Clear all files"
        ));

        printSection("SEARCH", List.of(
                "Simple search (single term)",
                "AND search (both terms)",
                "OR search (either term)",
                "NOT search (exclude term)",
                "Advanced search (query parser)",
                "Phrase search",
                "Proximity search",
                "Wildcard search"
        ));

        printSection("STATISTICS", List.of(
                "View index statistics",
                "Show top N terms"
        ));

        printSection("SERIALIZATION", List.of(
                "Save index (choose format)",
                "Load index (choose format)",
                "Compare serialization formats"
        ));

        printSection("UTILITY", List.of(
                "Clear index",
                "Compare structure types"
        ));

        System.out.println();
        System.out.println("    0. Exit");
        System.out.println("=".repeat(80));
        System.out.print("Enter your choice: ");
    }

    private static void printSection(String title, List<String> items) {
        System.out.printf("%n  %s:%n", title.toUpperCase());
        for (String item : items) {
            System.out.printf("    %2d. %s%n", counter++, item);
        }
    }

    private static void resetCounter() {
        counter = 1;
    }
}
