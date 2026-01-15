import core.BooleanSearchEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static constants.Filenames.*;
import static org.junit.jupiter.api.Assertions.*;

class BooleanSearchEngineTest {
    private BooleanSearchEngine searchEngine;

    @BeforeEach
    void setUp() {
        searchEngine = new BooleanSearchEngine();
        try {
            searchEngine.indexDocumentsFromDirectory(DIRECTORY_NAME);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @AfterEach
    void tearDown() {
        searchEngine = null;
    }

    void saveAndLoadDictionary(String filepath) throws IOException, ClassNotFoundException {
        Map<String, Set<Integer>> copyInvertedIndex = new HashMap<>(searchEngine.getInvertedIndex());

        switch (filepath) {
            case SERIALIZATION_FILENAME_SER -> {
                searchEngine.saveDictionaryBinary(filepath);
                searchEngine.loadDictionaryBinary(filepath);
            }
            case SERIALIZATION_FILENAME_TXT -> {
                searchEngine.saveDictionaryText(filepath);
                searchEngine.loadDictionaryText(filepath);
            }
            case SERIALIZATION_FILENAME_JSON -> {
                searchEngine.saveDictionaryJSON(SERIALIZATION_FILENAME_JSON);
                searchEngine.loadDictionaryJSON(SERIALIZATION_FILENAME_JSON);
            }
            default -> throw new RuntimeException("Invalid filepath");
        }
        assertEquals(searchEngine.getInvertedIndex(), copyInvertedIndex);
    }

    @Test
    void saveAndLoadDictionarySer() throws IOException, ClassNotFoundException {
        saveAndLoadDictionary(SERIALIZATION_FILENAME_SER);
    }

    @Test
    void saveAndLoadDictionaryText() throws IOException, ClassNotFoundException {
        saveAndLoadDictionary(SERIALIZATION_FILENAME_TXT);
    }

    @Test
    void saveAndLoadDictionaryJSON() throws IOException, ClassNotFoundException {
        saveAndLoadDictionary(SERIALIZATION_FILENAME_JSON);
    }
}
