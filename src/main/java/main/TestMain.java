package main;

import core.BooleanSearchEngine;
import index.BiwordIndex;
import index.PositionalIndex;
import query.PhraseSearch;
import query.ReversePolishNotation;
import query.ShuntingYard;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) throws IOException {
        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
        PositionalIndex positionalIndex = new PositionalIndex();
        BiwordIndex biwordIndex = new BiwordIndex();
        PhraseSearch phraseSearch = new PhraseSearch(positionalIndex, biwordIndex, searchEngine);

        searchEngine.indexDocuments(DIRECTORY_PATH);

        System.out.println(phraseSearch.searchPhraseBiword("document doctor"));

    }
    /**
     * Тестує один булевий запит
     */
    private static void testQuery(BooleanSearchEngine engine, String query) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("📝 QUERY: " + query);

        try {
            // Конвертуємо в RPN
            String rpn = ShuntingYard.toRPN(query);
            System.out.println("🔄 RPN:   " + rpn);

            // Обчислюємо
            long startTime = System.nanoTime();
            Set<Integer> results = ReversePolishNotation.evaluate(rpn, engine);
            long duration = (System.nanoTime() - startTime) / 1_000; // мікросекунди

            // Виводимо результати
            System.out.println("✅ FOUND: " + results.size() + " document(s) in " + duration + "μs");

            if (!results.isEmpty()) {
                List<String> names = engine.getDocumentNames(results);
                System.out.println("📄 Documents:");
                names.forEach(name -> System.out.println("   • " + name));
            } else {
                System.out.println("   (no documents found)");
            }

        } catch (IllegalArgumentException e) {
            System.err.println("❌ ERROR: " + e.getMessage());
        }
    }
}
