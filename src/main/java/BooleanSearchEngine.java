import annotations.Loggable;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.FileWalker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class BooleanSearchEngine {
    private static final Logger LOGGER = LoggerFactory.getLogger(BooleanSearchEngine.class);
    private static final String FILENAME_STOPWORDS = "stopwords.txt";
    private static final List<String> STOP_WORDS = new ArrayList<>();

    private Map<String, Set<Integer>> invertedIndex = new HashMap<>(); // term -> [document ids]
    private final Map<String, Integer> docMetadata = new HashMap<>(); // filename -> document id
    private final Map<Integer, String> idToFilename = new HashMap<>(); // document id -> filename

    private SerializationComparison serializationComparison;
    private int nextDocID = 1;

    private long totalCollectionSize = 0;


    static {
        try {
            addAllStopWordsToList(FILENAME_STOPWORDS);
        } catch (IOException | IllegalArgumentException e) {
            LOGGER.error("Unable to load stopwords file: {}", e.getMessage());
        }
    }


    // indexing files
    public void indexDocumentsFromDirectory(String directoryPath) throws IllegalArgumentException, IOException {
        List<Path> paths = FileWalker.findFiles(directoryPath);

        for (Path path : paths) {
            LOGGER.info("Indexing documents from {}", path);
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
    @Loggable(message = "Saving dictionary into binary file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryBinary(String filepath) throws IOException {
        try (
            OutputStream file = new FileOutputStream(filepath);
            OutputStream buffer = new BufferedOutputStream(file);
            ObjectOutput output = new ObjectOutputStream(buffer);
        ) {
            output.writeObject(invertedIndex);
        }
    }

    @Loggable(message = "Saving dictionary into txt file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryText(String filepath) throws IOException {
        try (
            OutputStream file = new FileOutputStream(filepath);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(file));
        ) {
            for (Map.Entry<String, Set<Integer>> entry : invertedIndex.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        }
    }

    @Loggable(message = "Saving dictionary into json file", level = Loggable.LoggingLevel.INFO)
    public void saveDictionaryJSON(String filepath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(invertedIndex);
        try (
            OutputStream file = new FileOutputStream(filepath);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(file));
        ) {
            writer.write(jsonString);
        }
    }

    // deserialization
    @SuppressWarnings("unchecked")
    @Loggable(message = "Loading dictionary from binary file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryBinary(String filepath) throws IOException, ClassNotFoundException {
        try (
            InputStream file = new FileInputStream(filepath);
            InputStream buffer = new BufferedInputStream(file);
            ObjectInput input = new ObjectInputStream(buffer);
        ) {
            invertedIndex = (HashMap<String, Set<Integer>>) input.readObject();
        }
    }

    @Loggable(message = "Loading dictionary from txt file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryText(String filepath) throws IOException {
        try (
            InputStream file = new FileInputStream(filepath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(file));
        ) {
            invertedIndex.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                int spaceIndex = line.indexOf(' ');
                if (spaceIndex == -1) continue;
                String term = line.substring(0, spaceIndex);
                String setString = line.substring(spaceIndex + 1).trim();

                if (setString.startsWith("[") && setString.endsWith("]")) {
                    setString = setString.substring(1, setString.length() - 1);
                }

                Set<Integer> docIDs = new HashSet<>();
                if (!setString.isEmpty()) {
                    String[] ids = setString.split(", ");
                    for (String id : ids) {
                        try {
                            docIDs.add(Integer.parseInt(id));
                        } catch (NumberFormatException e) {
                            LOGGER.warn("Invalid document ID: {}", id);
                        }
                    }
                }

                invertedIndex.put(term, docIDs);
            }
        }
    }

    @Loggable(message = "Load dictionary from json file", level = Loggable.LoggingLevel.INFO)
    public void loadDictionaryJSON(String filepath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<Map<String, Set<Integer>>> typeRef = new TypeReference<>(){};

        Map<String, Set<Integer>> map = objectMapper.readValue(
                new File(filepath),
                typeRef
        );

        invertedIndex.clear();
        invertedIndex.putAll(map);
    }

    // порівняння форматів серіалізації
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
            throw new IllegalArgumentException("File not found: " + filename);
        }

        try (inputStream; BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            String line;
            StringTokenizer tokenizer;
            while ((line = reader.readLine()) != null) {
                tokenizer = new StringTokenizer(line);
                while (tokenizer.hasMoreTokens()) {
                    STOP_WORDS.add(tokenizer.nextToken().toLowerCase());
                }
            }
        }
    }
}
