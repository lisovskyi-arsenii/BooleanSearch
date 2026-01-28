package util;

import core.BooleanSearchEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;

public final class FileReader {
    private FileReader() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void addContentToCollection(String filename, Collection<String> collection) throws IOException {
        ClassLoader classLoader = BooleanSearchEngine.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filename);

        if (inputStream == null) {
            throw new IllegalArgumentException("File not found: " + filename);
        }

        try (
                inputStream;
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String word = line.toLowerCase();
                collection.add(word);
            }
        }
    }

    public static void addContentToCollectionMultiWord(String filename, Collection<String> collection) throws IOException {
        ClassLoader classLoader = BooleanSearchEngine.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filename);

        if (inputStream == null) {
            throw new IllegalArgumentException("File not found: " + filename);
        }

        try (
                inputStream;
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }

                String[] words = line.split("\\s+");
                for (String word : words) {
                    word = word.toLowerCase().trim();
                    if (!word.isEmpty()) {
                        collection.add(word);
                    }
                }
            }
        }
    }
}
