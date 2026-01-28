package main;

import core.BooleanSearchEngine;
import query.ReversePolishNotation;
import query.ShuntingYard;
import tokenization.StopWordsFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) {

    System.out.println("=".repeat(70));
        System.out.println("STOP WORDS FILTER TEST");
        System.out.println("=".repeat(70));

    StopWordsFilter filter = new StopWordsFilter();

        System.out.println("\n✅ Stop words loaded: " + filter.size());

    // Тест 1: Фільтрація
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 1: Filtering");
        System.out.println("-".repeat(70));

    List<String> tokens = List.of(
            "the", "quick", "brown", "fox", "jumps", "over", "the", "lazy", "dog"
    );

        System.out.println("BEFORE: " + tokens);
    List<String> filtered = filter.filter(tokens);
        System.out.println("AFTER:  " + filtered);
        System.out.println("Removed: " + (tokens.size() - filtered.size()) + " stop words");

    // Тест 2: Перевірка окремих слів
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 2: Individual word check");
        System.out.println("-".repeat(70));

    String[] testWords = {"the", "java", "and", "programming", "is", "cool"};
        for (String word : testWords) {
        System.out.printf("  '%s' is stop word: %s%n",
                word, filter.isStopWord(word));
    }

    // Тест 3: Case insensitive
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 3: Case insensitive");
        System.out.println("-".repeat(70));

    List<String> mixedCase = List.of("The", "QUICK", "Brown", "FOX");
        System.out.println("MIXED CASE: " + mixedCase);
        System.out.println("FILTERED:   " + filter.filter(mixedCase));

    // Тест 4: Реальний текст
        System.out.println("\n" + "-".repeat(70));
        System.out.println("TEST 4: Real text");
        System.out.println("-".repeat(70));

    String text = "I am learning about data structures and algorithms in Java";
    List<String> wordsFromText = List.of(text.toLowerCase().split("\\s+"));

        System.out.println("TEXT: " + text);
        System.out.println("TOKENS BEFORE (" + wordsFromText.size() + "): " + wordsFromText);

    List<String> filteredText = filter.filter(wordsFromText);
        System.out.println("TOKENS AFTER  (" + filteredText.size() + "): " + filteredText);

        System.out.println("\n" + "=".repeat(70));
    }


//        // 1️⃣ Ініціалізація
//        System.out.println("=".repeat(70));
//        System.out.println("BOOLEAN SEARCH ENGINE - TEST");
//        System.out.println("=".repeat(70));
//
//        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
//
//        System.out.println("\n📁 Indexing documents from: " + DIRECTORY_PATH);
//        searchEngine.indexDocuments(DIRECTORY_PATH);
//        System.out.println("✅ Indexing complete!");
//        System.out.println("   Total documents: " + searchEngine.getAllDocumentIDs().size());
//
//        // 2️⃣ Тестові запити
//        System.out.println("\n" + "=".repeat(70));
//        System.out.println("TESTING BOOLEAN QUERIES");
//        System.out.println("=".repeat(70));
//
//        // Прості запити
//        testQuery(searchEngine, "apple");
//        testQuery(searchEngine, "banana");
//
//        // AND запити
//        testQuery(searchEngine, "apple AND banana");
//
//        // OR запити
//        testQuery(searchEngine, "apple OR banana");
//
//        // NOT запити
//        testQuery(searchEngine, "NOT apple");
//
//        // Комплексні запити з дужками
//        testQuery(searchEngine, "(apple OR banana) AND carrot");
//        testQuery(searchEngine, "(apple OR banana) AND (banana OR carrot)");
//        testQuery(searchEngine, "apple AND NOT banana");
//        testQuery(searchEngine, "(apple AND banana) OR (carrot AND date)");
//        testQuery(searchEngine, "NOT (apple AND banana)");
//
//        // Складні вкладені запити
//        testQuery(searchEngine, "((apple OR banana) AND carrot) OR date");
//        testQuery(searchEngine, "apple AND (banana OR (carrot AND date))");
//
//        // 1. Тести NOT з різними комбінаціями
//        testQuery(searchEngine, "NOT NOT apple");           // подвійне заперечення
//        testQuery(searchEngine, "NOT (apple OR carrot)");   // NOT для об'єднання
//        testQuery(searchEngine, "NOT apple AND NOT carrot"); // декілька NOT
//
//// 2. Тести пріоритету операторів
//        testQuery(searchEngine, "apple OR carrot AND date"); // OR має нижчий пріоритет
//        testQuery(searchEngine, "apple AND carrot OR date"); // AND має вищий пріоритет
//
//// 3. Тести з багатьма дужками
//        testQuery(searchEngine, "((apple))");                // зайві дужки
//        testQuery(searchEngine, "(((apple OR carrot)))");    // багато дужок
//
//// 4. Граничні випадки
//        testQuery(searchEngine, "nonexistent");              // термін якого немає
//        testQuery(searchEngine, "NOT nonexistent");          // NOT для неіснуючого
//        testQuery(searchEngine, "apple AND apple");          // дублікати
//
//
//        System.out.println("\n" + "=".repeat(70));
//        System.out.println("✅ ALL TESTS COMPLETED");
//        System.out.println("=".repeat(70));

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
