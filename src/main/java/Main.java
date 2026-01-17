import core.BooleanSearchEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import static constants.Filenames.*;

public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        LOGGER.info("=".repeat(80));
        LOGGER.info("APPLICATION STARTED at {}", LocalDateTime.now());
        LOGGER.info("=".repeat(80));

        BooleanSearchEngine searchEngine = new BooleanSearchEngine();

        try {
            LOGGER.info("Indexing documents from: {}", DIRECTORY_NAME);
            searchEngine.indexDocuments(DIRECTORY_NAME);

            System.out.println(searchEngine.getStatistics());

            String term = "machine";
            Optional<Set<Integer>> results = searchEngine.search(term);
            results.ifPresentOrElse(result -> printSearchResults(searchEngine, "search", result, term),
                    () -> System.err.println("No results found"));

            String term1 = "school", term2 = "telephone";
            Optional<Set<Integer>> andResults = searchEngine.andSearch(term1, term2);
            andResults.ifPresentOrElse(result -> printSearchResults(searchEngine, "andSearch", result, term1, term2),
                    () -> System.err.println("No results found"));

            Optional<Set<Integer>> orResults = searchEngine.orSearch("document", "apple");
            orResults.ifPresentOrElse(result -> printSearchResults(searchEngine, "orResults", result, term1, term2),
                    () -> System.err.println("No results found"));

            printTopTerms(searchEngine, 10);


        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
        } finally {
            LOGGER.info("APPLICATION FINISHED");
            LOGGER.info("=".repeat(80) + "\n");
        }
    }

    private static void printTopTerms(BooleanSearchEngine searchEngine, int topCount) {
        System.out.println("Top " + topCount + " terms found:");
        Map<String, Integer> termFrequency = new HashMap<>();

        for (String term : searchEngine.getIndex().getAllTerms()) {
            int freq = searchEngine.getIndex().getTerm(term).size();
            termFrequency.put(term, freq);
        }

        List<Map.Entry<String,Integer>> sorted = termFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topCount)
                .toList();

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String,Integer> entry = sorted.get(i);
            System.out.printf("%2d. %-20s → appears in %d document(s)%n",
                    i + 1, entry.getKey(), entry.getValue());
        }
    }

    private static void printSearchResults(BooleanSearchEngine searchEngine, String operation, Set<Integer> ids, String... terms) {
        if (ids == null || ids.isEmpty()) {
            System.out.println("No documents found");
            return;
        }

        List<String> filenames = searchEngine.getDocumentNames(ids);
        String termsText = String.join("\", \"", terms);
        System.out.printf("\n🔎 Word(s) \"%s\" were found in %d file(s):%n",
                termsText, filenames.size());
        filenames.forEach(filename -> System.out.println("  • " + filename));
    }

    private static void testSerialization() {

    }
}
