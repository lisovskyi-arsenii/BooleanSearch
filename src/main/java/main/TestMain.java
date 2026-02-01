package main;

import core.BooleanSearchEngine;
import enums.SearchStructureType;
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
        searchEngine.indexDocuments(DIRECTORY_PATH);

        var positionalIndex = searchEngine.getPositionalIndex();
        var biwordIndex = searchEngine.getBiwordIndex();
        var phraseSearch = new PhraseSearch(positionalIndex, biwordIndex, searchEngine);

//        var result = phraseSearch.searchPhraseBiword("book with great ending");
//        if (result.isPresent()) {
////            System.out.printf("RESULT: %s%n", result.get());
//            var documentNames = searchEngine.getDocumentNames(result.get());
//            System.out.println(documentNames);
//        } else {
//            System.out.println("NO RESULT");
//        }

        String phrase = "Steering north-eastward from the Crozetts, we fell in with vast meadows";
        var result = phraseSearch.searchPhrasePositional(phrase);
        if (result.isEmpty()) {
            System.out.println("No phrase found");
            return;
        }

        System.out.println(result.get());
//        System.out.println(result);

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
