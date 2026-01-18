package serialization.serializers;

import serialization.IndexSerializer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class TextSerializer implements IndexSerializer {
    private static final String FORMAT = "TEXT";
    private static final String SEPARATOR = ": ";
    private static final String DOC_SEPARATOR = ", ";

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        try (
            BufferedWriter writer = new BufferedWriter(
                new FileWriter(filepath, StandardCharsets.UTF_8));
        ) {
            List<String> sortedTerms = new ArrayList<>(index.keySet());
            Collections.sort(sortedTerms);

            for (String term : sortedTerms) {
                Set<Integer> docIDs = index.get(term);

                String docIDsStr = docIDs.stream()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(DOC_SEPARATOR));

                writer.write(term);
                writer.write(SEPARATOR);
                writer.write(docIDsStr);
                writer.newLine();
            }
        }
    }

    @Override
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        try (
            BufferedReader reader = new BufferedReader(
                    new FileReader(filepath, StandardCharsets.UTF_8));
        ) {
            Map<String, Set<Integer>> index = new HashMap<>();
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;

                int separatorIndex = line.indexOf(SEPARATOR);
                if (separatorIndex == -1) continue;

                String term = line.substring(0, separatorIndex).trim();
                String docIDsStr = line.substring(separatorIndex + SEPARATOR.length()).trim();

                Set<Integer> docIDs = Arrays.stream(docIDsStr.split(DOC_SEPARATOR))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .map(Integer::parseInt)
                                        .collect(Collectors.toSet());
                index.put(term, docIDs);
            }
            return index;
        }
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
