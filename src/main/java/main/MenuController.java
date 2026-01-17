package main;

import core.BooleanSearchEngine;
import enums.FileGenerationType;
import enums.MenuChoice;
import generator.GenerateFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static constants.Filenames.DIRECTORY_PATH;

public class MenuController {
    private static final Logger LOGGER = LoggerFactory.getLogger(MenuController.class);
    private final CustomScanner scanner;
    private final BooleanSearchEngine searchEngine;

    public MenuController(BooleanSearchEngine searchEngine, CustomScanner scanner) {
        if (scanner == null) {
            throw new NullPointerException("Scanner cannot be null.");
        }
        if (searchEngine == null) {
            throw new NullPointerException("SearchEngine cannot be null.");
        }
        this.scanner = scanner;
        this.searchEngine = searchEngine;
    }

    public boolean handleUserChoice(int code) throws IllegalArgumentException, IOException, ExecutionException, InterruptedException {
        var userChoice = MenuChoice.fromCode(code);
        if (userChoice.isEmpty()) {
            System.out.println("Invalid choice.");
            return true;
        }

        return switch (userChoice.get()) {
            case INDEX_DOCUMENTS -> {
                System.out.println("Index documents");
                indexDocuments();
                yield true;
            }
            case REINDEX_DOCUMENTS -> {
                System.out.println("Reindex documents");
                reindexDocuments();
                yield true;
            }
            case GENERATE_FILES -> {
                System.out.println("Generate files");
                generateFiles();
                yield true;
            }
            case LIST_DIRECTORY -> {
                System.out.println("List directory");
                listDirectory();
                yield true;
            }
            case CLEAR_ALL_FILES -> {
                System.out.println("Clear all files");
                clearAllFiles();
                yield true;
            }
            case SIMPLE_SEARCH -> {
                System.out.println("Simple search");
                simpleSearch();
                yield true;
            }
            case AND_SEARCH -> {
                System.out.println("And search");
                andSearch();
                yield true;
            }
            case OR_SEARCH -> {
                System.out.println("Or search");
                orSearch();
                yield true;
            }
            case NOT_SEARCH -> {
                System.out.println("Not search");
                notSearch();
                yield true;
            }
            case VIEW_STATISTICS -> {
                System.out.println("View statistics");
                viewStatistics();
                yield true;
            }
            case SHOW_TOP_TERMS -> {
                showTopTerms();
                yield true;
            }
            case SAVE_INDEX -> {
                System.out.println("Save to file");
                saveIndex();
                yield true;
            }
            case LOAD_INDEX -> {
                System.out.println("Load from file");
                loadIndex();
                yield true;
            }
            case COMPARE_FORMATS -> {
                System.out.println("Compare serialization formats");
                compareFormats();
                yield true;
            }
            case CLEAR_INDEX -> {
                System.out.println("Clear index");
                clearIndex();
                yield true;
            }
            case PRINT_INDEX -> {
                System.out.println("Print index");
                printIndex();
                yield true;
            }
            case EXIT -> {
                System.out.println("Exiting program.");
                yield false;
            }
        };
    }

    public void indexDocuments() throws IllegalArgumentException, IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        LOGGER.info("Indexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        LOGGER.info("Indexing completed");
    }

    public void reindexDocuments() throws IllegalArgumentException, IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        searchEngine.getIndex().clear();
        LOGGER.info("Reindexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        LOGGER.info("Reindex completed");
    }

    public void generateFiles() throws IllegalArgumentException {
        System.out.println("What type of files do you want to generate?");
        System.out.println("  - small");
        System.out.println("  - medium");
        System.out.println("  - large");
        System.out.print("Enter type: ");

        String fileType = scanner.parseString();

        Optional<FileGenerationType> fIleGenerationType = FileGenerationType.fromString(fileType);
        if (fIleGenerationType.isEmpty()) {
            System.out.println("Invalid file type.");
            return;
        }

        System.out.print("Enter number of files to generate: ");
        int quantityOfFiles = scanner.parseInt();

        if (quantityOfFiles <= 0) {
            System.out.println("  Quantity must be positive");
            return;
        }

        try {
            LOGGER.info("Generating {} {} files...", quantityOfFiles, fIleGenerationType.get().getType());

            switch (fIleGenerationType.get()) {
                case SMALL -> GenerateFiles.generateSmallFiles(quantityOfFiles);
                case MEDIUM -> GenerateFiles.generateMediumFiles(quantityOfFiles);
                case LARGE -> GenerateFiles.generateLargeFiles(quantityOfFiles);
            }

            System.out.println("✅ File generation completed!");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.error("File generation interrupted", e);
            System.err.println("  File generation was interrupted");

        } catch (ExecutionException e) {
            LOGGER.error("File generation failed", e.getCause());
            System.err.println("  Failed to generate files: " + e.getCause().getMessage());
        }
    }

    private void listDirectory() throws IllegalArgumentException, IOException {
        System.out.println("Enter directory path (Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        LOGGER.info("Listing directory {}", directoryPath);
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            System.out.println("Directory does not exist.");
            return;
        }

        try (var stream = Files.list(path)) {
            stream.forEach(System.out::println);
        }
    }

    private void clearAllFiles() throws IOException {
        Path dir = Paths.get(DIRECTORY_PATH);

        if (!Files.exists(dir)) {
            LOGGER.info("Directory {} does not exist", DIRECTORY_PATH);
            return;
        }

        if (!Files.isDirectory(dir)) {
            throw new IOException("Path is not a directory: " + dir);
        }

        try (var stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .forEach(file -> {
                        try {
                            Files.delete(file);
                            LOGGER.info("File {} deleted", file.getFileName());
                        } catch (IOException e) {
                            LOGGER.error("Failed to delete file {}", file.getFileName(), e);
                        }
                    });
        }

        LOGGER.info("All files deleted!");
    }

    public void simpleSearch() throws IllegalArgumentException {
        System.out.println("Enter term to search for: ");
        String term = scanner.parseString();

        if (term.isEmpty()) {
            System.out.println("Term cannot be empty");
            return;
        }

        LOGGER.info("Searching for {}", term);
        Optional<Set<Integer>> result = searchEngine.search(term);

        result.ifPresentOrElse(
                ids -> {
            List<String> filenames = searchEngine.getDocumentNames(ids);
            System.out.printf("Term '%s' was found in %d file(s):%n ", term, filenames.size());

            if  (filenames.isEmpty()) {
                System.out.println("No files found");
            } else {
                filenames.forEach(filename -> System.out.println("  • " + filename));
            }
        }, () -> System.out.printf("Term %s was not found in any document", term));
    }

    public void andSearch() throws IllegalArgumentException {
        System.out.println("Enter first term to search for: ");
        String term1 = scanner.parseString();

        System.out.println("Enter second term to search for: ");
        String term2 = scanner.parseString();

        if (term1.isEmpty() || term2.isEmpty()) {
            System.out.println("Terms cannot be empty");
            return;
        }

        Optional<Set<Integer>> result = searchEngine.andSearch(term1, term2);
        result.ifPresentOrElse(
                ids -> {
                    List<String> filenames = searchEngine.getDocumentNames(ids);
                    filenames.parallelStream().forEach(System.out::println);
                },
                () -> System.out.printf("Term %s was not found in any document", term1)
        );
    }

    public void orSearch() throws IllegalArgumentException {
        System.out.println("Enter first term to search for: ");
        String term1 = scanner.parseString();

        System.out.println("Enter second term to search for: ");
        String term2 = scanner.parseString();

        if (term1.isEmpty() || term2.isEmpty()) {
            System.out.println("Terms cannot be empty");
            return;
        }

        Optional<Set<Integer>> result = searchEngine.orSearch(term1, term2);
        result.ifPresentOrElse(
                ids -> {
                    List<String> filenames = searchEngine.getDocumentNames(ids);
                    filenames.parallelStream().forEach(System.out::println);
                },
                () -> System.out.printf("Term %s was not found in any document", term1)
        );

    }

    public void notSearch() throws IllegalArgumentException{

    }

    public void viewStatistics() throws IllegalArgumentException{

    }

    public void showTopTerms() throws IllegalArgumentException {
        System.out.println("Show top terms");
        System.out.println("Enter how many terms you want to show: ");
        int topCount = scanner.parseInt();
        if (topCount < 0 || topCount > searchEngine.getIndex().size()) {
            throw new IllegalArgumentException("Invalid top count of terms you want to show.");
        }

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

    public void saveIndex() throws IllegalArgumentException {

    }

    public void loadIndex() throws IllegalArgumentException{

    }

    public void compareFormats() throws IllegalArgumentException{

    }


    public void clearIndex() throws IllegalArgumentException {

    }

    public void printIndex() throws IllegalArgumentException {

    }

}
