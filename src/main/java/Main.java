import java.io.IOException;
import java.util.*;

public class Main {
    private static final String DIRECTORY_NAME = "documents";

    public static void main(String[] args) {
        try {
            BooleanSearchEngine searchEngine = new BooleanSearchEngine();

            searchEngine.indexDocumentsFromDirectory(DIRECTORY_NAME);
            searchEngine.printIndex();

            Optional<Set<Integer>> docIDs = searchEngine.search("study");
            docIDs.ifPresentOrElse((ids) -> {
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
            andSearch.ifPresentOrElse((ids) -> {
                List<String> findFilenames = new ArrayList<>();
                for (Map.Entry<String, Integer> entry : searchEngine.getDocMetadata().entrySet()) {
                    if (ids.contains(entry.getValue())) {
                        findFilenames.add(entry.getKey());
                    }
                }
                findFilenames.forEach(System.out::println);
            },
                    () -> System.out.println("No and search result found"));



        } catch (IOException | IllegalArgumentException e) {
            System.err.println(e.getMessage());
        }
    }
}
