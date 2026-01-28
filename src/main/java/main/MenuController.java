package main;

import benchmark.PerformanceBenchmark;
import core.BooleanSearchEngine;
import enums.*;
import generator.GenerateFiles;
import lombok.extern.slf4j.Slf4j;
import query.QueryParser;
import serialization.SerializationComparison;
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
import static enums.SearchOperators.*;

@Slf4j
public class MenuController {
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

    public boolean handleUserChoice(int code) throws IllegalArgumentException, IOException {
        var userChoice = MenuChoice.fromCode(code);
        if (userChoice.isEmpty()) {
            System.out.println("Invalid choice.");
            return true;
        }
        String description = userChoice.get().getDescription();

        return switch (userChoice.get()) {
            case INDEX_DOCUMENTS -> {
                System.out.println(description);
                indexDocuments();
                yield true;
            }
            case REINDEX_DOCUMENTS -> {
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
            case ADVANCED_SEARCH -> {
                System.out.println("Advanced search");
                advancedSearch();
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
            case COMPARE_PERFORMANCE -> {
                System.out.println("Compare performance");
                compareBenchmarks();
                yield true;
            }
            case EXIT -> {
                System.out.println("Exiting program.");
                yield false;
            }
        };
    }

    public void indexDocuments() throws IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        log.info("Indexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        log.info("Indexing completed");
    }

    public void reindexDocuments() throws IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        searchEngine.getIndex().clear();
        searchEngine.getMatrix().clear();
        log.info("Reindexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        log.info("Reindex completed");
    }

    public void generateFiles() throws IllegalArgumentException {
        System.out.println("What type of files do you want to generate?");
        System.out.println("  - small");
        System.out.println("  - medium");
        System.out.println("  - large");
        System.out.print("Enter type: ");

        String fileType = scanner.parseString();

        Optional<FileGenerationType> fileGenerationType = FileGenerationType.fromString(fileType);
        if (fileGenerationType.isEmpty()) {
            System.out.println("Invalid file type");
            return;
        }

        System.out.print("Enter number of files to generate: ");
        OptionalInt quantityOpt = scanner.parseInt();

        if (quantityOpt.isEmpty()) {
            System.out.println("Invalid number");
            return;
        }

        int quantityOfFiles = quantityOpt.getAsInt();
        if (quantityOfFiles <= 0) {
            System.out.println("Number cannot be negative");
        }
        try {
            log.info("Generating {} {} files...", quantityOpt, fileGenerationType.get().getType());

            switch (fileGenerationType.get()) {
                case SMALL -> GenerateFiles.generateSmallFiles(quantityOfFiles);
                case MEDIUM -> GenerateFiles.generateMediumFiles(quantityOfFiles);
                case LARGE -> GenerateFiles.generateLargeFiles(quantityOfFiles);
            }

            System.out.println("  File generation completed!");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("File generation interrupted", e);
            System.err.println("  File generation was interrupted");

        } catch (ExecutionException e) {
            log.error("File generation failed", e.getCause());
            System.err.println("  Failed to generate files: " + e.getCause().getMessage());
        }
    }

    private void listDirectory() throws IllegalArgumentException, IOException {
        System.out.println("Enter directory path (Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        log.info("Listing directory {}", directoryPath);
        Path path = Paths.get(directoryPath);
        if (!Files.exists(path)) {
            System.out.println("Directory does not exist");
            return;
        }

        if (!Files.isDirectory(path)) {
            System.out.println("Path is not a directory");
            return;
        }

        try (var stream = Files.list(path)) {
            List<Path> files = stream.sorted().toList();

            if (files.isEmpty()) {
                System.out.println("Empty directory");
            } else {
                for (Path file : files) {
                    String type = Files.isDirectory(file) ? "Directory" : "File";
                    long size = Files.isRegularFile(file) ? Files.size(file) : 0;
                    String sizeStr = Files.isRegularFile(file) ?
                            String.format("%,d bytes", size) : "";
                    System.out.printf("\t%s %s %s%n", type, file.getFileName(), sizeStr);
                }
                System.out.printf("%nTotal: %d item(s)%n", files.size());
            }
        }
    }

    private void clearAllFiles() throws IOException {
        Path dir = Path.of(DIRECTORY_PATH);

        if (!Files.exists(dir)) {
            log.info("Directory {} does not exist", DIRECTORY_PATH);
            return;
        }

        if (!Files.isDirectory(dir)) {
            System.out.println("Path is not a directory");
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
                    log.info("File {} deleted", file.getFileName());
                } catch (IOException e) {
                    failedCount++;
                    log.error("Failed to delete file {}", file.getFileName(), e);
                }
            }
        }

        System.out.printf("Deleted %d file(s)%n", deletedCount);
        if (failedCount > 0) {
            System.out.printf("\t%d failed", failedCount);
        }
        System.out.println();
        log.info("Deleted {} file(s), {} failed", deletedCount, failedCount);
    }

    private Optional<SearchStructureType> getType() {
        System.out.println("Enter structure where to search for (index/matrix): ");
        String structure = scanner.parseString();

        if (structure.isEmpty()) {
            System.out.println("Structure cannot be empty");
            return Optional.empty();
        }

        return SearchStructureType.fromString(structure);
    }

    public void simpleSearch() {
        System.out.println("Enter term to search for: ");
        String term = scanner.parseString();

        if (term.isEmpty()) {
            System.out.println("Term cannot be empty");
            return;
        }

        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();
        log.debug("Searching for {}", term);
        Optional<Set<Integer>> result = searchEngine.search(term, type);

        result.ifPresentOrElse(
                ids -> {
                    List<String> filenames = searchEngine.getDocumentNames(ids);
                    if (filenames.isEmpty()) {
                        System.out.println("No files found");
                        return;
                    }

                    System.out.printf("Term '%s' was found in %d file(s):%n ", term, filenames.size());
                    filenames.forEach(filename -> System.out.println("  • " + filename));
                },
                () -> System.out.printf("Term %s was not found in any document%n", term));
    }

    public void andSearch() {
        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();

        performSearch(
                AND,
                2,
                terms -> searchEngine.andSearch(terms[0], terms[1], type));
    }

    public void orSearch() {
        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();

        performSearch(
                OR,
                2,
                terms -> searchEngine.orSearch(terms[0], terms[1], type));
    }

    public void notSearch() {
        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();

        performSearch(
                NOT,
                1,
                terms -> {
                    Set<Integer> allDocs = searchEngine.getAllDocumentIDs();
                    return searchEngine.notSearch(terms[0], allDocs, type);
                });
    }

    public void advancedSearch() {
        System.out.println("Enter complex boolean query:");
        System.out.println("Examples: 'java AND python', 'rust OR golang', 'data AND NOT test'");
        System.out.print("> ");

        String query = scanner.parseString();
        if (query == null || query.isEmpty()) {
            System.out.println("Query cannot be empty");
            return;
        }

        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();

        var result = QueryParser.parseAndExecute(query, searchEngine, type);

        result.ifPresentOrElse(
                ids -> {
                    List<String> filenames = searchEngine.getDocumentNames(ids);

                    if (filenames.isEmpty()) {
                        System.out.println("No files found");
                        return;
                    }

                    System.out.printf("%nQuery '%s' found in %d file(s):%n", query, filenames.size());
                    filenames.stream().parallel().forEach(filename -> System.out.println("  • " + filename));
                },
                () -> System.out.printf("No documents found for query: %s%n", query)
        );
    }

    private void performSearch(
            SearchOperators operation,
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

                    System.out.printf("%n%s: found %d file(s):%n", strOperation, filenames.size());
                    filenames.forEach(filename -> System.out.println("  • " + filename));
                },
                () -> System.out.printf("  No documents found for %s%n", strOperation)
        );
    }

    public void viewStatistics() {
        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();
        DictionaryStats stats = searchEngine.getStatistics(type);

        System.out.println("\n" + "=".repeat(80));
        System.out.println("INDEX STATISTICS");
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

    public void showTopTerms() {
        System.out.print("Enter how many terms you want to show: ");
        OptionalInt topCountOpt = scanner.parseInt();

        if (topCountOpt.isEmpty()) {
            System.out.println("Invalid number");
            return;
        }

        int topCount = topCountOpt.getAsInt();
        int maxTerms = searchEngine.getIndex().size();

        if (topCount <= 0) {
            System.out.println("Number must be positive");
            return;
        }

        if (topCount > maxTerms) {
            System.out.printf("Only %d terms available. Showing all%n", maxTerms);
            topCount = maxTerms;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("TOP " + topCount + " TERMS");
        System.out.println("=".repeat(80));

        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : searchEngine.getIndex().getAllTerms()) {
            var documents = searchEngine.getIndex().getDocuments(term);
            if (documents.isEmpty()) {
                termFrequency.put(term, 0);
            } else {
                int freq = documents.get().size();
                termFrequency.put(term, freq);
            }
        }

        List<Map.Entry<String, Integer>> sorted = termFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(topCount)
                .toList();

        System.out.printf("%-5s %-30s %15s%n", "Rank", "Term", "Documents");
        System.out.println("-".repeat(80));

        for (int i = 0; i < sorted.size(); i++) {
            Map.Entry<String, Integer> entry = sorted.get(i);
            System.out.printf("%-5d %-30s %,15d%n",
                    i + 1, entry.getKey(), entry.getValue());
        }

        System.out.println("=".repeat(80));
    }

    public void saveIndex() {
        performFileOperationOnIndex(
                SAVE,
                (filepath, extension) -> {
                    try {
                        searchEngine.saveIndex(filepath, extension);
                        String fullPath = filepath + "." + extension;
                        System.out.printf("Index was saved into %s%n", fullPath);
                        log.info("Index was saved into {}%n", fullPath);
                    } catch (IOException e) {
                        System.err.printf("Failed to save index: %s%n", filepath);
                        log.error("Failed to load index", e);
                    }
                    return null;
                }
        );
    }

    public void loadIndex() {
        performFileOperationOnIndex(
                LOAD,
                (filepath, extension) -> {
                    try {
                        searchEngine.loadIndex(filepath, extension);
                        String fullPath = filepath + "." + extension;
                        System.out.printf("Index was loaded from %s%n", fullPath);
                        log.info("Index was loaded from {}%n", fullPath);
                    } catch (IOException e) {
                        System.err.printf("Failed to load index: %s%n", filepath);
                        log.error("Failed to load index", e);
                    } catch (ClassNotFoundException e) {
                        System.err.printf("Error: %s%n", e.getMessage());
                        log.error("Error: {}", e.getMessage());
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
        System.out.println("ser/txt/json: ");
        String formatChoice = scanner.parseString();
        Optional<FileSerializationFormat> format = FileSerializationFormat.fromFormat(formatChoice);

        if (format.isEmpty()) {
            System.err.println("Invalid format");
            log.error("Invalid format");
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
            log.error("Failed to {} index: {}", operation.getOperation(), e.getMessage());
        }
    }

    public void compareFormats() {
        SerializationComparison comparison = null;
        if (searchEngine.getIndex().size() == 0) {
            System.out.println("Index is empty. Please index some documents first");
            return;
        }

        System.out.println("No comparison data found. Measuring now");

        try {
            comparison = searchEngine.measureAllFormats();
            searchEngine.setSerializationComparison(comparison);
        } catch (IOException e) {
            System.err.println("Failed to measure formats: " + e.getMessage());
            log.error("Failed to measure formats", e);
            return;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SERIALIZATION FORMAT COMPARISON");
        System.out.println("=".repeat(80));

        comparison.printData();

        System.out.println("=".repeat(80));
    }


    public void clearIndex() {
        searchEngine.clear();
        System.out.println("Index has been cleared successfully");
    }

    public void printIndex() {
        int termCount = searchEngine.getIndex().size();

        if (termCount == 0) {
            System.out.println("Index is empty. Nothing to print.");
            return;
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("INDEX CONTENT");
        System.out.println("=".repeat(80));
        System.out.printf("Total terms: %,d%n", termCount);
        System.out.println("-".repeat(80));

        searchEngine.printIndex();
    }

    public void compareBenchmarks() {
        System.out.println("Enter test terms (comma-separated, e.g.: java,python,algorithm,data):");
        String input = scanner.parseString();

        if (input.isEmpty()) {
            System.out.println("Using default terms: document, text, file, data");
            input = "document,text,file,data";
        }

        List<String> testTerms = Arrays.asList(input.split(","))
                .stream()
                .map(String::trim)
                .toList();

        PerformanceBenchmark benchmark = new PerformanceBenchmark(searchEngine);
        benchmark.runAllBenchmarks(testTerms);
    }

}
