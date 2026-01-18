package main;

import core.BooleanSearchEngine;
import enums.*;
import generator.GenerateFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import statistics.DictionaryStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiFunction;
import java.util.function.Function;

import static constants.Filenames.DEFAULT_FILENAME;
import static constants.Filenames.DIRECTORY_PATH;
import static enums.FileOperation.LOAD;
import static enums.FileOperation.SAVE;
import static enums.SearchOperation.*;

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

        if (!Files.isDirectory(path)) {
            System.out.println("Path is not a directory.");
            return;
        }

        try (var stream = Files.list(path)) {
            List<Path> files = stream.sorted().toList();

            if (files.isEmpty()) {
                System.out.println("Empty directory.");
            } else {
                for (Path file : files) {
                    String type = Files.isDirectory(file) ? "Directory" : "File";
                    long size = Files.isRegularFile(file) ? Files.size(file) : 0;
                    String sizeStr = Files.isRegularFile(file) ?
                            String.format("%,d bytes", size) : "";
                    System.out.printf("\t%s %s %s%n", type, file.getFileName(), sizeStr);
                }
                System.out.printf("\nTotal: %d item(s)%n", files.size());
            }
        }
    }

    private void clearAllFiles() throws IOException {
        Path dir = Path.of(DIRECTORY_PATH);

        if (!Files.exists(dir)) {
            LOGGER.info("Directory {} does not exist", DIRECTORY_PATH);
            return;
        }

        if (!Files.isDirectory(dir)) {
            System.out.println("Path is not a directory.");
            return;
        }

        int deletedCount = 0;
        int failedCount = 0;

        try (var stream = Files.walk(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();

            for (Path file : files) {
                try {
                    Files.delete(file);
                    deletedCount++;
                    LOGGER.info("File {} deleted", file.getFileName());
                } catch (IOException e) {
                    failedCount++;
                    LOGGER.error("Failed to delete file {}", file.getFileName(), e);
                }
            }
        }

        System.out.printf("Deleted %d file(s)%n", deletedCount);
        if (failedCount > 0) {
            System.out.printf("\t%d failed", failedCount);
        }
        System.out.println();
        LOGGER.info("Deleted {} file(s), {} failed", deletedCount, failedCount);
    }

    public void simpleSearch() throws IllegalArgumentException {
        System.out.println("Enter term to search for: ");
        String term = scanner.parseString();

        if (term.isEmpty()) {
            System.out.println("Term cannot be empty");
            return;
        }

        LOGGER.debug("Searching for {}", term);
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
        },
        () -> System.out.printf("Term %s was not found in any document", term));
    }

    public void andSearch() throws IllegalArgumentException {
        performSearch(
                AND,
                2,
                terms -> searchEngine.andSearch(terms[0], terms[1]));
    }

    public void orSearch() throws IllegalArgumentException {
        performSearch(
                OR,
                2,
                terms -> searchEngine.orSearch(terms[0], terms[1]));
    }

    public void notSearch() throws IllegalArgumentException{
        performSearch(
                NOT,
                1,
                terms -> {
                    Set<Integer> allDocs = searchEngine.getAllDocumentIDs();
                    return searchEngine.notSearch(terms[0], allDocs);
                });
    }

    private void performSearch(
            SearchOperation operation,
            int termsCount,
            Function<String[], Optional<Set<Integer>>> searchFunction
    ) {
        String[] terms = new String[termsCount];

        for (int i = 0; i < termsCount; i++) {
            System.out.println("Enter " +
                    ((termsCount == 1) ? "term" : "term " + (i + 1)) + ": ");
            terms[i] = scanner.parseString();

            if (terms[i].isEmpty()) {
                System.out.println("Term cannot be empty");
                return;
            }
        }
        String strOperation = operation.getOperation();

        Optional<Set<Integer>> result = searchFunction.apply(terms);
        result.ifPresentOrElse(
                ids -> {
                    List<String> filenames = searchEngine.getDocumentNames(ids);
                    if (filenames.isEmpty()) {
                        System.out.println("No files found");
                        return;
                    }

                    System.out.printf("\n %s: found %d file(s):%n", strOperation, filenames.size());

                    filenames.forEach(filename -> System.out.println("  • " + filename));
                },
                () -> System.out.printf("  No documents found for %s%n", strOperation)
        );
    }

    public void viewStatistics() throws IllegalArgumentException{
        DictionaryStats stats = searchEngine.getStatistics();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("📊 INDEX STATISTICS");
        System.out.println("=".repeat(80));
        System.out.printf("  Documents indexed:      %,d%n", stats.documentsCount());
        System.out.printf("  Unique terms:           %,d%n", stats.uniqueTerms());
        System.out.printf("  Total term occurrences: %,d%n", stats.totalWords());
        System.out.printf("  Collection size:        %,d bytes (%.2f MB)%n",
                stats.collectionSizeInBytes(),
                stats.collectionSizeInBytes() / (1024.0 * 1024.0));

        if (stats.uniqueTerms() > 0 && stats.documentsCount() > 0) {
            System.out.printf("  Average terms per doc:  %.2f%n",
                    (double) stats.totalWords() / stats.documentsCount());
            System.out.printf("  Average doc size:       %,d bytes%n",
                    stats.collectionSizeInBytes() / stats.documentsCount());
        }

        System.out.println("=".repeat(80));
    }

    public void showTopTerms() throws IllegalArgumentException {
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
        performFileOperationOnIndex(
                SAVE,
                (filepath, extension) -> {
                    try {
                        searchEngine.saveIndex(filepath, extension);
                        String fullpath = filepath + "." + extension;
                        System.out.printf("Index was saved into %s%n", fullpath);
                        LOGGER.info("Index was saved into %s%n", fullpath);
                    } catch (IOException e) {
                        System.err.printf("Failed to save index: %s%n", filepath);
                        LOGGER.error("Failed to load index", e);
                    }
                    return null;
                }
        );
    }

    public void loadIndex() throws IllegalArgumentException {
        performFileOperationOnIndex(
                LOAD,
                (filepath, extension) -> {
                    try {
                        searchEngine.loadIndex(filepath, extension);
                        String fullpath = filepath + "." + extension;
                        System.out.printf("Index was loaded from %s%n", fullpath);
                        LOGGER.info("Index was loaded from %s%n", fullpath);
                    } catch (IOException e) {
                        System.err.printf("Failed to load index: %s%n", filepath);
                        LOGGER.error("Failed to load index", e);
                    } catch (ClassNotFoundException e) {
                        System.err.printf("Error: %s%n", e.getMessage());
                        LOGGER.error("Error: {}", e.getMessage());
                    }
                    return null;
                }
        );
    }

    private void performFileOperationOnIndex(
            FileOperation operation,
            BiFunction<String, String, Void> function
    ) {
        System.out.println("Choose format: ");
        System.out.println("bin/txt/json: ");
        String formatChoice = scanner.parseString();
        Optional<FileSerializationFormat> format = FileSerializationFormat.fromFormat(formatChoice);

        if (format.isEmpty()) {
            System.err.println("Invalid format");
            LOGGER.error("Invalid format");
            return;
        }

        System.out.println("Enter filename (without extension): ");
        String filename = scanner.parseString();
        if (filename.isEmpty()) {
            filename = DEFAULT_FILENAME;
        }

        String extension = format.get().getExtension();
        String fullPath = filename + "." + extension;
        try {
            function.apply(fullPath, extension);
        } catch (Exception e) {
            System.err.printf("Failed to %s index: %s", operation.getOperation(), e.getMessage());
            LOGGER.error("Failed to {} index: {}", operation.getOperation(), e.getMessage());
        }
    }

    public void compareFormats() throws IllegalArgumentException{

    }


    public void clearIndex() throws IllegalArgumentException {

    }

    public void printIndex() throws IllegalArgumentException {

    }

}
