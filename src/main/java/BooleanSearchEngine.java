import annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.FileWalker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class BooleanSearchEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanSearchEngine.class);
    private static final String FILENAME_STOPWORDS = "stopwords.txt";
    private static final List<String> STOP_WORDS = new ArrayList<>();

    private Map<String, Set<Integer>> invertedIndex = new HashMap<>(); // term -> [document ids]
    private final Map<String, Integer> docMetadata = new HashMap<>(); // filename -> document id
    private final Map<Integer, String> idToFilename = new HashMap<>(); // document id -> filename

    // TODO
    private  SerializationComparison serializationComparison;
    private int nextDocID = 1;

    private long totalCollectionSize = 0;


    static {
        try {
            addAllStopWordsToList(FILENAME_STOPWORDS);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Unable to load stopwords file: {}", e.getMessage());
        }
    }

//    public BooleanSearchEngine() {}

    // indexing files
    public void indexDocumentsFromDirectory(@NotNull String directoryPath) throws IllegalArgumentException, IOException {
        List<Path> paths = FileWalker.findFiles(directoryPath);

        for (Path path : paths) {
            LOGGER.info("Indexing documents from {}", path.toString());
            indexFileFromDisk(path);
        }
    }

    private void indexFileFromDisk(Path filePath) throws IOException {
        totalCollectionSize += Files.size(filePath);

        String filename = filePath.getFileName().toString();

        if (!docMetadata.containsKey(filename)) {
            registerDocument(filename);
        }

        int documentID = docMetadata.get(filename);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath.toFile())))) {
            String line;
            while ((line = reader.readLine()) != null) {
                StringTokenizer tokenizer = new StringTokenizer(line);
                while (tokenizer.hasMoreTokens()) {
                    String word = tokenizer.nextToken().trim().toLowerCase();
                    if (!STOP_WORDS.contains(word)) {
                        invertedIndex.computeIfAbsent(word, k -> new HashSet<>()).add(documentID);
                    }
                }
            }
        }
    }

    // search by one term
    public Optional<Set<Integer>> search(String term) {
        return Optional.ofNullable(invertedIndex.get(term));
    }

    public Optional<Set<Integer>> andSearch(String term1, String term2) {
        Optional<Set<Integer>> result1 = search(term1);
        Optional<Set<Integer>> result2 = search(term2);

        if (result1.isPresent() && result2.isPresent()) {
            Set<Integer> finalResult = new HashSet<>(result1.get());
            finalResult.retainAll(result2.get());
            return Optional.of(finalResult);
        }
        return Optional.empty();
    }

    public Set<Integer> orSearch(String term1, String term2) {
        Set<Integer> result1 = invertedIndex.getOrDefault(term1, Collections.emptySet());
        Set<Integer> result2 = invertedIndex.getOrDefault(term2, Collections.emptySet());

        Set<Integer> finalResult = new HashSet<>(result1);
        finalResult.addAll(result2);
        return finalResult;
    }

    public Set<Integer> notSearch(String term, Set<Integer> allDocsIDs) {
        Set<Integer> docsWithTerm = invertedIndex.getOrDefault(term, Collections.emptySet());

        Set<Integer> finalResult = new HashSet<>(allDocsIDs);
        finalResult.removeAll(docsWithTerm);
        return finalResult;
    }


    // serialization
    public void saveDictionaryBinary(String filepath) throws IOException {
        try (
            OutputStream file = new FileOutputStream(filepath);
            OutputStream buffer = new BufferedOutputStream(file);
            ObjectOutput output = new ObjectOutputStream(buffer);
        ) {
            output.writeObject(invertedIndex);
            Path path = Paths.get(filepath);
            if (Files.exists(path)) {
                LOGGER.info("Saving dictionary into {}", filepath);
            } else {
                LOGGER.warn("Could not save dictionary into {}", filepath);
            }
        }
    }

    // TODO
    public void saveDictionaryText(String filepath) throws IOException {

    }

    // TODO
    public void saveDictionaryJSON(String filepath) throws IOException {

    }

    // deserialization
    // TODO
    @SuppressWarnings("unchecked")
    public void loadDictionaryBinary(String filepath) throws IOException, ClassNotFoundException {
        try (
            InputStream file = new FileInputStream(filepath);
            InputStream buffer = new BufferedInputStream(file);
            ObjectInput input = new ObjectInputStream(buffer);
        ) {
            invertedIndex = (HashMap<String, Set<Integer>>) input.readObject();
            for (Map.Entry<String, Set<Integer>> entry : invertedIndex.entrySet()) {
                System.out.println(entry.getKey() + " : " + entry.getValue());
            }
            if (!invertedIndex.isEmpty()) {
                LOGGER.info("Loading dictionary into variable `invertedIndex` from {}", filepath);
            } else {
                LOGGER.warn("Could not load dictionary into variable `invertedIndex` from {}", filepath);
            }
        }
    }

    // TODO
    public void loadDictionaryText(String filepath) throws IOException {

    }

    // TODO
    public void loadDictionaryJSON(String filepath) throws IOException {

    }

    // порівняння форматів сереалізації
    public SerializationComparison getSerializationComparison() {
        return serializationComparison;
    }


    // work with data after queries
    public List<String> getDocumentNames(Set<Integer> docIDs) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : docMetadata.entrySet()) {
            if (docIDs.contains(entry.getValue())) {
                result.add(entry.getKey());
            }
        }

        return result;
    }

    public Optional<String> getDocumentName(int docID) throws IllegalArgumentException {
        return Optional.ofNullable(idToFilename.get(docID));
    }

    public int documentCount() {
        return docMetadata.size();
    }


    // statistics
    public int termFrequency(String term) {
        return invertedIndex.getOrDefault(term, Collections.emptySet()).size();
    }

    public Set<String> getAllTerms() {
        return invertedIndex.keySet();
    }

    public Map<String, Set<Integer>> getInvertedIndex() {
        return invertedIndex;
    }

    public Map<String, Integer> getDocMetadata() {
        return docMetadata;
    }


    // статистика
    public DictionaryStats getStats() {
        int uniqueTerms = invertedIndex.size();
        int totalWords = invertedIndex.values().stream()
                .mapToInt(Set::size)
                .sum();

        return new DictionaryStats(
                docMetadata.size(),
                uniqueTerms,
                totalWords,
                totalCollectionSize
        );
    }

    // print out
    public void printIndex() {
        for (Map.Entry<String, Set<Integer>> entry : invertedIndex.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }


    // util methods
    public void clearIndex() {
        invertedIndex.clear();
    }

    private void registerDocument(String filename) {
        int id = nextDocID++;
        docMetadata.put(filename, id);
        idToFilename.put(id, filename);
    }

    private static void addAllStopWordsToList(String filename) throws IllegalArgumentException, IOException {
        ClassLoader classLoader = BooleanSearchEngine.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filename);

        if (inputStream == null) {
            throw new IllegalArgumentException("File " + filename + " not found");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            StringTokenizer tokenizer;
            while ((line = reader.readLine()) != null) {
                tokenizer = new StringTokenizer(line);
                while (tokenizer.hasMoreTokens()) {
                    STOP_WORDS.add(tokenizer.nextToken().toLowerCase());
                }
            }
        } finally {
            inputStream.close();

        }
    }
}
