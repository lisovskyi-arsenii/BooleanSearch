package menu;

import benchmark.CompressionBenchmark;
import benchmark.PerformanceBenchmark;
import core.BooleanSearchEngine;
import enums.*;
import generator.GenerateFiles;
import lombok.extern.slf4j.Slf4j;
import query.ProximitySearch;
import query.ReversePolishNotation;
import query.ShuntingYard;
import scanner.CustomScanner;
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
import java.util.stream.Collectors;

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
            throw new IllegalArgumentException("Scanner cannot be null");
        }
        if (searchEngine == null) {
            throw new IllegalArgumentException("SearchEngine cannot be null");
        }
        this.scanner = scanner;
        this.searchEngine = searchEngine;
    }

    public boolean handleUserChoice(int code) throws IllegalArgumentException, IOException {
        var userChoice = ModeMenuMapping.resolveChoice(code, searchEngine.getCurrentMode());
        if (userChoice == null) {
            System.out.println("Invalid choice.");
            return true;
        }
        System.out.println("\n" + userChoice.getDescription());

        switch (userChoice) {
            case INDEX_DOCUMENTS -> indexDocuments();
            case REINDEX_DOCUMENTS -> reindexDocuments();
            case INDEX_LARGE_DOCUMENTS -> indexLargeCollection();
            case GENERATE_FILES -> generateFiles();
            case LIST_DIRECTORY -> listDirectory();
            case CLEAR_ALL_FILES -> clearAllFiles();
            case SIMPLE_SEARCH -> simpleSearch();
            case AND_SEARCH -> andSearch();
            case OR_SEARCH -> orSearch();
            case NOT_SEARCH -> notSearch();
            case ADVANCED_SEARCH -> advancedSearch();
            case PHRASE_SEARCH -> phraseSearch();
            case PROXIMITY_SEARCH -> proximitySearch();
            case WILDCARD_SEARCH -> wildcardSearch();
            case VIEW_STATISTICS -> viewStatistics();
            case SHOW_TOP_TERMS -> showTopTerms();
            case SAVE_INDEX -> saveIndex();
            case LOAD_INDEX -> loadIndex();
            case COMPARE_FORMATS -> compareFormats();
            case CLEAR_INDEX -> clearIndex();
            case COMPARE_PERFORMANCE -> compareBenchmarks();
            case COMPRESSION_PERFORMANCE -> compareCompression();
            case EXIT -> {
                System.out.println("Exiting program.");
                searchEngine.close();
                return false;
            }
            default -> System.out.println("Invalid input choice");
        }
        return true;
    }

    private void showMessage() {
        if (!isDocumentsIndexed()) {
            System.out.println("Consider indexing documents first before doing this task!\n".toUpperCase());
        }
    }

    private boolean isDocumentsIndexed() {
        return searchEngine.getCurrentMode() != BooleanSearchEngine.IndexingMode.NOT_INIT;
    }

    public void indexDocuments() throws IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        log.info("Indexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        log.info("Indexing completed");

        System.out.println("\nBuilding wildcard indexes...");
        searchEngine.buildWildcardIndexes();
        System.out.println("Wildcard indexes ready");
    }

    public void reindexDocuments() throws IOException {
        System.out.println("Enter directory path from where indexing should be done(Press `Enter` to use default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) directoryPath = DIRECTORY_PATH;

        searchEngine.clear();
        log.info("Reindexing from {}", directoryPath);
        searchEngine.indexDocuments(directoryPath);
        log.info("Reindex completed");

        System.out.println("\nRebuilding wildcard indexes...");
        searchEngine.buildWildcardIndexes();
        System.out.println("Wildcard indexes ready");
    }

    public void indexLargeCollection() {
        System.out.println("\n=== SPIMI Large Collection Indexing ===");
        System.out.println("This method is optimized for collections larger than RAM");
        System.out.println("Files will be processed in 50MB blocks");
        System.out.println();

        System.out.print("Enter directory path (Press Enter for default): ");
        String directoryPath = scanner.parseString();
        if (directoryPath.isEmpty()) {
            directoryPath = DIRECTORY_PATH;
        }

        try {
            log.info("Starting SPIMI indexing from {}", directoryPath);
            searchEngine.indexLargeCollection(directoryPath);

            System.out.println("\n  Large collection indexed successfully!");
            System.out.println("\nYou can now search the indexed collection");
        } catch (IOException e) {
            System.err.println("\n  Indexing failed: " + e.getMessage());
            log.error("SPIMI indexing failed", e);
        }
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
            return;
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
        if (searchEngine.getCurrentMode() == BooleanSearchEngine.IndexingMode.DISK_BASED) {
            return Optional.of(SearchStructureType.INDEX);
        }

        String[] valid = {"index", "matrix", "biword", "positional"};
        System.out.printf("Enter structure (%s): ", String.join("/", valid));

        String input = scanner.parseString().toLowerCase();

        return switch (input) {
            case "index" -> Optional.of(SearchStructureType.INDEX);
            case "matrix" -> Optional.of(SearchStructureType.MATRIX);
            case "biword" -> Optional.of(SearchStructureType.BIWORD);
            case "positional", "position" -> Optional.of(SearchStructureType.POSITIONAL);
            default -> {
                System.out.println("Invalid: " + input + ". Use: " + String.join(", ", valid));
                yield Optional.empty();
            }
        };
    }


    public void simpleSearch() {
        showMessage();

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
        showMessage();

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
        showMessage();

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
        showMessage();

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
        showMessage();

        System.out.println("Enter complex boolean query:");
        System.out.println("Examples:");
        System.out.println("  'java AND python'");
        System.out.println("  'rust OR golang'");
        System.out.println("  'data AND NOT test'");
        System.out.println("  '(java OR python) AND database'");
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

        try {
            String rpn = ShuntingYard.toRPN(query);

            Set<Integer> ids = ReversePolishNotation.evaluate(rpn, searchEngine, type);

            if (ids.isEmpty()) {
                System.out.printf("No documents found for query: %s%n", query);
                return;
            }

            List<String> filenames= searchEngine.getDocumentNames(ids);
            System.out.printf("%nQuery '%s' found in %d file(s):%n", query, filenames.size());
            filenames.forEach(filename -> System.out.println("  - " + filename));
        } catch (IllegalArgumentException e) {
            System.err.println("Invalid query: " + e.getMessage());
            log.error("Query parsing failed: {}", query, e);
        }
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

    public void phraseSearch() {
        showMessage();
        System.out.println("Enter phrase to search for: ");
        String phrase = scanner.parseString();

        if (phrase.isEmpty()) {
            System.out.println("Phrase cannot be empty");
            return;
        }

        System.out.println("Choose search type:");
        System.out.println("  1. Biword Index");
        System.out.println("  2. Positional Index");
        System.out.print("Enter choice: ");

        OptionalInt choiceOpt = scanner.parseInt();
        if (choiceOpt.isEmpty()) {
            System.out.println("Invalid choice");
            return;
        }

        int choice = choiceOpt.getAsInt();
        if (choice != 1 && choice != 2) {
            System.out.println("Invalid choice. Please enter 1 or 2");
            return;
        }

        SearchStructureType type = choice == 1
                ? SearchStructureType.BIWORD
                : SearchStructureType.POSITIONAL;

        log.debug("Searching phrase '{}' using {}", phrase, type);

        Optional<Set<Integer>> result = searchEngine.phraseSearch(phrase, type);

        result.ifPresentOrElse(
                ids -> {
                    if (ids.isEmpty()) {
                        System.out.println("No files found");
                        return;
                    }

                    List<String> filenames = searchEngine.getDocumentNames(ids);
                    if (filenames.isEmpty()) {
                        System.out.println("No files found");
                        return;
                    }
                    System.out.printf("Phrase '%s' found in %d file(s):%n", phrase, filenames.size());
                    filenames.forEach(filename -> System.out.println("  • " + filename));
                },
                () -> System.out.printf("Phrase '%s' not found%n", phrase));
    }

    public void proximitySearch() {
        showMessage();
        System.out.println("Enter first term: ");
        String term1 = scanner.parseString();

        System.out.println("Enter second term: ");
        String term2 = scanner.parseString();

        if (term1.isEmpty() || term2.isEmpty()) {
            System.out.println("Terms cannot be empty");
            return;
        }

        System.out.print("Enter maximum distance: ");
        OptionalInt distanceOpt = scanner.parseInt();

        if (distanceOpt.isEmpty() || distanceOpt.getAsInt() < 1) {
            System.out.println("Invalid distance");
            return;
        }

        int distance = distanceOpt.getAsInt();

        log.debug("Proximity search: '{}' and '{}' within distance {}", term1, term2, distance);

        var matchesOpt = searchEngine.proximitySearchDetailed(term1, term2, distance);

        if (matchesOpt.isEmpty()) {
            System.out.printf("No matches found for '%s' and '%s' within distance %d%n",
                    term1, term2, distance);
            return;
        }

        var matches = matchesOpt.get();

        Map<Integer, Long> byDoc = matches.stream()
                .collect(Collectors.groupingBy(
                        ProximitySearch.ProximityMatch::docId,
                        Collectors.counting()
                ));

        System.out.printf("%nFound %d match(es) in %d document(s):%n",
                matches.size(), byDoc.size());

        List<String> allDocNames = searchEngine.getDocumentNames(byDoc.keySet());
        List<Integer> docIdsList = new ArrayList<>(byDoc.keySet());

        Map<Integer, String> docIdToName = new HashMap<>();
        for (int i = 0; i < docIdsList.size(); i++) {
            docIdToName.put(docIdsList.get(i), allDocNames.get(i));
        }

        for (var entry : byDoc.entrySet()) {
            int docId = entry.getKey();
            long count = entry.getValue();
            String docName = docIdToName.getOrDefault(docId, "Unknown");
            System.out.printf("  • %s (%d match(es))%n", docName, count);
        }
    }

    public void wildcardSearch() {
        showMessage();
        System.out.println("\n=== Wildcard Search ===");
        System.out.println("Supported patterns:");
        System.out.println("  mon*     - words starting with 'mon' (using BTree)");
        System.out.println("  *ing     - words ending with 'ing' (using ReverseBTree)");
        System.out.println("  m*n      - middle wildcard (using 3-gram)");
        System.out.println("  te*ti*   - multiple wildcards (using 3-gram)");
        System.out.println();

        if (searchEngine.getCurrentMode() == BooleanSearchEngine.IndexingMode.IN_MEMORY) {
            System.out.println("\nChoose wildcard strategy for single-'*' queries:");
            System.out.println("  1. Permuterm Index  (precise, handles all patterns)");
            System.out.println("  2. BTree/ReverseBTree (BTree for prefix, ReverseBTree for suffix)");
            System.out.print("Enter choice (default = 1): ");

            var strategyOpt = scanner.parseInt();
            if (strategyOpt.isPresent() && strategyOpt.getAsInt() == 2) {
                searchEngine.setWildcardStrategy(WildcardStrategy.BTREE);
            } else {
                searchEngine.setWildcardStrategy(WildcardStrategy.PERMUTERM);
            }
        }

        System.out.print("Enter wildcard query: ");
        String query = scanner.parseString().toLowerCase();
        if (query.isBlank()) {
            System.out.println("Query cannot be empty");
            return;
        }

        try {
            long startTime = System.nanoTime();

            var resultOpt = searchEngine.wildcardSearch(query);

            long endTime = System.nanoTime();
            double timeMs = (endTime - startTime) / 1_000_000.0;

            if (resultOpt.isEmpty() || resultOpt.get().isEmpty()) {
                System.out.println("\nNo documents found");
                return;
            }

            Map<String, Set<Integer>> results = resultOpt.get();

            for (var entry : results.entrySet()) {
                String term = entry.getKey();
                Set<Integer> docIds = entry.getValue();

                final int limit = 100;

                System.out.printf("%-15s → %d documents: %s%n",
                        term,
                        docIds.size(),
                        docIds.stream()
                                .sorted()
                                .map(String::valueOf)
                                .limit(limit)
                                .collect(Collectors.joining(", "))
                                + (docIds.size() > limit ? "..." : ""));
            }

            int totalDocs = results.values().stream()
                    .flatMap(Set::stream)
                    .collect(Collectors.toSet()).size();

            System.out.printf("%n📊 Terms found: %d | Unique docs: %d (from %d)%n",
                    results.size(), totalDocs, searchEngine.getRegistry().documentCount());
            System.out.printf("%nSearch time: %.2f ms%n", timeMs);
        } catch (IllegalStateException e) {
            System.err.println("\nError: " + e.getMessage());
            System.out.println("Please index documents first and build wildcard indexes.");
        } catch (Exception e) {
            System.err.println("\nError: " + e.getMessage());
        }
    }

    public void viewStatistics() {
        showMessage();
        if (searchEngine.getCurrentMode() == BooleanSearchEngine.IndexingMode.DISK_BASED) {
            searchEngine.printStatistics();
            return;
        }

        var typeOptional = getType();
        if (typeOptional.isEmpty()) {
            System.out.println("Type cannot be empty");
            return;
        }

        var type = typeOptional.get();
        DictionaryStats stats = searchEngine.getStatistics(type);
        if (stats == null) {
            System.out.println("No statistics available for this type");
            return;
        }

        String title = typeOptional.get().name() + " STATISTICS";
        System.out.println("\n" + "=".repeat(80));
        System.out.println(title.toUpperCase());
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

        System.out.println("\n" + "-".repeat(80));
        System.out.println("WILDCARD INDEXES");
        System.out.println("-".repeat(80));

        if (searchEngine.getBTree() != null) {
            System.out.printf("  BTree size:             %,d terms%n", searchEngine.getBTree().size());
            System.out.printf("  ReverseBTree size:      %,d terms%n", searchEngine.getReverseBTree().size());
            System.out.printf("  ThreeGramIndex size:    %,d terms%n", searchEngine.getThreeGramIndex().size());
            System.out.printf("  ThreeGram n-grams:      %,d%n",
                    searchEngine.getThreeGramIndex().getIndex().size());
        } else {
            System.out.println("  Wildcard indexes not built");
            System.out.println("  Use option 1 (Index documents) to build them");
        }

        System.out.println("=".repeat(80));
    }

    public void showTopTerms() {
        showMessage();
        if (searchEngine.getCurrentMode() == BooleanSearchEngine.IndexingMode.DISK_BASED) {
            System.out.println("showTopTerms() is not available in DISK-BASED mode.");
            System.out.println("Use wildcardSearch() or simpleSearch() instead.");
            return;
        }

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
        showMessage();
        performFileOperationOnIndex(
                SAVE,
                (filepath, extension) -> {
                    try {
                        searchEngine.saveIndex(filepath, extension);
                        System.out.printf("Index was saved into %s%n", filepath);
                        log.info("Index was saved into {}%n", filepath);
                    } catch (IOException e) {
                        System.err.printf("Failed to save index: %s%n", filepath);
                        log.error("Failed to load index", e);
                    }
                    return null;
                }
        );
    }

    public void loadIndex() {
        showMessage();
        performFileOperationOnIndex(
                LOAD,
                (filepath, extension) -> {
                    try {
                        searchEngine.loadIndex(filepath, extension);
                        System.out.printf("Index was loaded from %s%n", filepath);
                        log.info("Index was loaded from {}%n", filepath);

                        System.out.println("\nRebuilding wildcard indexes");
                        searchEngine.buildWildcardIndexes();
                        System.out.println("Wildcard indexes ready");

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
        String fullPath = filename + '.' + extension;
        try {
            function.apply(fullPath, extension);
        } catch (Exception e) {
            System.err.printf("Failed to %s index: %s", operation.getOperation(), e.getMessage());
            log.error("Failed to {} index: {}", operation.getOperation(), e.getMessage());
        }
    }

    public void compareFormats() {
        showMessage();
        if (searchEngine.getIndex().size() == 0) {
            System.out.println("Index is empty. Please index some documents first");
            return;
        }

        System.out.println("No comparison data found. Measuring now");

        var comparison = searchEngine.getSerializationComparison();

        if (comparison != null) {
            System.out.println("Using cached comparison data");
        } else {
            System.out.println("No comparison data found. Measuring now...");
            try {
                comparison = searchEngine.measureAllFormats();
                if (comparison == null) return;
                searchEngine.setSerializationComparison(comparison);
            } catch (IOException e) {
                System.err.println("Failed to measure formats: " + e.getMessage());
                log.error("Failed to measure formats", e);
                return;
            }
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println("SERIALIZATION FORMAT COMPARISON");
        System.out.println("=".repeat(80));
        comparison.printData();
        System.out.println("=".repeat(80));
    }


    public void clearIndex() {
        showMessage();
        searchEngine.clear();
        System.out.println("Index has been cleared successfully");
    }

    public void compareBenchmarks() {
        showMessage();

        System.out.println("Enter test terms (comma-separated, e.g.: java,python,algorithm,data):");
        String input = scanner.parseString();

        if (input.isEmpty()) {
            System.out.println("Using default terms: document, text, file, data");
            input = "document,text,file,data";
        }

        List<String> testTerms = Arrays.stream(input.split(","))
                .map(String::trim)
                .toList();

        PerformanceBenchmark benchmark = new PerformanceBenchmark(searchEngine);
        benchmark.runAllBenchmarks(testTerms);
    }

    public void compareCompression() {
        if (searchEngine.getCurrentMode() != BooleanSearchEngine.IndexingMode.IN_MEMORY) {
            System.out.println("Compression benchmark requires IN_MEMORY mode.");
            System.out.println("Call indexDocuments() first, or call clear() to leave DISK_BASED mode.");
            return;
        }

        var benchmark = new CompressionBenchmark();
        benchmark.runAllBenchmarks(searchEngine.getIndex());
    }
}
