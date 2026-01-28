package serialization.serializers;

import serialization.data.IndexData;
import serialization.data.RegistryData;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TextSerializer implements IndexSerializer {
    private static final String FORMAT = "TEXT";
    private static final String SEPARATOR = ": ";
    private static final String DOC_SEPARATOR = ", ";

    private static final String INDEX_SECTION = "### INDEX ###";
    private static final String DOC_NAMES_SECTION = "### DOCUMENT_NAMES ###";
    private static final String DOC_SIZES_SECTION = "### DOCUMENT_SIZES ###";
    private static final String METADATA_SECTION = "### METADATA ###";

    @Override
    public void serialize(IndexData indexData, String filepath) throws IOException {
        try (
                BufferedWriter writer = new BufferedWriter(
                        new FileWriter(filepath, StandardCharsets.UTF_8));
        ) {
            // nextDocID
            writer.write(METADATA_SECTION);
            writer.newLine();
            writer.write("nextDocID" + SEPARATOR + indexData.registryData().nextDocID());
            writer.newLine();
            writer.newLine();

            writer.write(INDEX_SECTION);
            writer.newLine();

            List<String> sortedTerms = new ArrayList<>(indexData.index().keySet());
            Collections.sort(sortedTerms);

            for (String term : sortedTerms) {
                Set<Integer> docIDs = indexData.index().get(term);
                String docIDsStr = docIDs.stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(DOC_SEPARATOR));
                writer.write(term + SEPARATOR + docIDsStr);
                writer.newLine();
            }
            writer.newLine();

            writer.write(DOC_NAMES_SECTION);
            writer.newLine();

            var sortedNames = new ArrayList<>(indexData.registryData().idToFilename().entrySet());
            sortedNames.sort(Comparator.comparing(Map.Entry::getKey));

            // idToFilename
            for (var entry : sortedNames) {
                writer.write(entry.getKey() + SEPARATOR + entry.getValue());
                writer.newLine();
            }
            writer.newLine();

            // filenameToSize
            writer.write(DOC_SIZES_SECTION);
            writer.newLine();

            var sortedSize = new ArrayList<>(indexData.registryData().filenameToSize().entrySet());
            sortedSize.sort(Comparator.comparing(Map.Entry::getKey));

            for (var entry : sortedSize) {
                writer.write(entry.getKey() + SEPARATOR + entry.getValue());
                writer.newLine();
            }
        }
    }

    @Override
    public IndexData deserialize(String filepath) throws IOException {
        try (
                BufferedReader reader = new BufferedReader(
                        new FileReader(filepath, StandardCharsets.UTF_8));
        ) {
            Map<String, Set<Integer>> index = new ConcurrentHashMap<>();
            Map<Integer, String> idToFilename = new ConcurrentHashMap<>();
            Map<String, Long> filenameToSize = new ConcurrentHashMap<>();
            int nextDocID = 1;

            String currentSection = null;
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                switch (line) {
                    case METADATA_SECTION -> {
                        currentSection = METADATA_SECTION;
                        continue;
                    }
                    case INDEX_SECTION -> {
                        currentSection = INDEX_SECTION;
                        continue;
                    }
                    case DOC_NAMES_SECTION -> {
                        currentSection = DOC_NAMES_SECTION;
                        continue;
                    }
                    case DOC_SIZES_SECTION -> {
                        currentSection = DOC_SIZES_SECTION;
                        continue;
                    }
                    case "" -> {
                        continue;
                    }
                }

                int separatorIndex = line.indexOf(SEPARATOR);
                if (separatorIndex == -1) continue;

                String key = line.substring(0, separatorIndex).trim();
                String value = line.substring(separatorIndex + SEPARATOR.length()).trim();

                if (METADATA_SECTION.equals(currentSection)) {
                    if ("nextDocID".equals(key)) {
                        nextDocID = Integer.parseInt(value);
                    }
                } else if (INDEX_SECTION.equals(currentSection)) {
                    Set<Integer> docIDs = Arrays.stream(value.split(DOC_SEPARATOR))
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .map(Integer::parseInt)
                            .collect(Collectors.toSet());
                    index.put(key, docIDs);
                } else if (DOC_NAMES_SECTION.equals(currentSection)) {
                    int docID = Integer.parseInt(key);
                    idToFilename.put(docID, value);
                } else if (DOC_SIZES_SECTION.equals(currentSection)) {
                    filenameToSize.put(key, Long.parseLong(value));
                }

            }

            Map<String, Integer> filenameToId = new ConcurrentHashMap<>();
            for (final var entry : idToFilename.entrySet()) {
                filenameToId.put(entry.getValue(), entry.getKey());
            }

            RegistryData registryData = new RegistryData(
                    filenameToId,
                    idToFilename,
                    filenameToSize,
                    nextDocID
            );

            return new IndexData(index, registryData);
        }
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
