package menu;

public final class Printer {
    private Printer() {
        throw new UnsupportedOperationException("Printer class cannot be instantiated.");
    }

    public static void printMenu() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("                    BOOLEAN SEARCH ENGINE");
        System.out.println("=".repeat(80));
        System.out.println("  INDEXING:");
        System.out.println("    1. Index documents from directory");
        System.out.println("    2. Reindex (clear + index)");
        System.out.println("    3. Generate files");
        System.out.println("    4. List files");
        System.out.println("    5. Clear all files");
        System.out.println();
        System.out.println("  SEARCH:");
        System.out.println("    6. Simple search (single term)");
        System.out.println("    7. AND search (both terms)");
        System.out.println("    8. OR search (either term)");
        System.out.println("    9. NOT search (exclude term)");
        System.out.println("   10. Advanced search (query parser)");
        System.out.println();
        System.out.println("  STATISTICS:");
        System.out.println("   11. View index statistics");
        System.out.println("   12. Show top N terms");
        System.out.println();
        System.out.println("  SERIALIZATION:");
        System.out.println("   13. Save index (choose format)");
        System.out.println("   14. Load index (choose format)");
        System.out.println("   15. Compare serialization formats");
        System.out.println();
        System.out.println("  UTILITY:");
        System.out.println("   16. Clear index");
        System.out.println("   17. Print index");
        System.out.println("   18. Compare structure types");
        System.out.println();
        System.out.println("    0. Exit");
        System.out.println("=".repeat(80));
        System.out.print("Enter your choice: ");
    }
}
