import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public final class FileReader {
    private FileReader() {
        throw new AssertionError("Utility class");
    }

    public static List<String> readFile(String filename) throws IllegalArgumentException, IOException {
        ClassLoader classLoader = FileReader.class.getClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(filename);

        if (inputStream == null) {
            throw new IllegalArgumentException("File " + filename + " not found");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            List<String> lines = new ArrayList<>();
            String line;
            StringTokenizer tokenizer;
            while ((line = reader.readLine()) != null) {
                tokenizer = new StringTokenizer(line);
                while (tokenizer.hasMoreTokens()) {
                    lines.add(tokenizer.nextToken().toLowerCase());
                }
            }
            return lines;
        } finally {
            inputStream.close();
        }
    }
}
