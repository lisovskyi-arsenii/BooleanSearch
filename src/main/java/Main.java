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


        try {
            BooleanSearchEngine searchEngine = new BooleanSearchEngine();

            searchEngine.indexDocumentsFromDirectory(DIRECTORY_NAME);
            searchEngine.printIndex();

            Optional<Set<Integer>> docIDs = searchEngine.search("study");
            docIDs.ifPresentOrElse(ids -> {
                        List<String> findFilenames = new ArrayList<>();
                        for (Map.Entry<String, Integer> entry : searchEngine.getDocMetadata().entrySet()) {
                            if (ids.contains(entry.getValue())) {
                                findFilenames.add(entry.getKey());
                            }
                        }
                        findFilenames.forEach(System.out::println);
                    },
                    () -> System.out.println("No document found")
            );

            Optional<Set<Integer>> andSearch = searchEngine.andSearch("study", "machine");
            andSearch.ifPresentOrElse(ids -> {
                List<String> findFilenames = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : searchEngine.getDocMetadata().entrySet()) {
                    if (ids.contains(entry.getValue())) {
                        findFilenames.add(entry.getKey());
                    }
                }
                findFilenames.forEach(System.out::println);
            },
                    () -> System.out.println("No and search result found"));


            System.out.println(searchEngine.getStats());
//            GenerateFiles.generateMediumFiles();

        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
        } finally {
            LOGGER.info("APPLICATION FINISHED");
            LOGGER.info("=".repeat(80) + "\n");
        }
    }
}
