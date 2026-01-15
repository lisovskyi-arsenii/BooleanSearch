package serialization.serializers;

import serialization.IndexSerializer;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class TextSerializer implements IndexSerializer {
    private static final String FORMAT = "Text";

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        try (
            OutputStream file = new FileOutputStream(filepath);
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(file));
        ) {
            for (Map.Entry<String, Set<Integer>> entry : index.entrySet()) {
                writer.write(entry.getKey() + " " + entry.getValue());
                writer.newLine();
            }
        }
    }

    @Override
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        try (
                InputStream file = new FileInputStream(filepath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(file));
        ) {
            Map<String, Set<Integer>> index = new HashMap<>();
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
                        docIDs.add(Integer.parseInt(id));
                    }
                }

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
