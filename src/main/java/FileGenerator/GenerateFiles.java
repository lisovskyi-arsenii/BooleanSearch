package FileGenerator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class GenerateFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateFiles.class);

    private static final int SMALL_FILE_CHARS = 200_000;    // ~200 KB
    private static final int MEDIUM_FILE_CHARS = 500_000;   // ~500 KB
    private static final int LARGE_FILE_CHARS = 1_000_000; // ~1 MB

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final class BookIDs {
        static final int[] SMALL = {
                43,    // The Strange Case of Dr. Jekyll and Mr. Hyde
                1342,  // Pride and Prejudice
                11,    // Alice's Adventures in Wonderland
                174,   // The Picture of Dorian Gray
                1661   // The Adventures of Sherlock Holmes
        };

        // Середні книги (~400-800 KB)
        static final int[] MEDIUM = {
                84,    // Frankenstein
                345,   // Dracula
                76,    // Adventures of Huckleberry Finn
                98     // A Tale of Two Cities
        };

        // Великі книги (~1-3 MB)
        static final int[] LARGE = {
                2701,  // Moby Dick (~1.2 MB)
                2600,  // War and Peace (~3.2 MB)
                1399,  // Anna Karenina (~2.1 MB)
                1661   // Sherlock Holmes Complete (~1.5 MB)
        };
    }



    private GenerateFiles() {
        throw new AssertionError("%s cannot be instantiated".formatted(getClass().getSimpleName()));
    }

    public static String downloadBook(int bookID) throws IOException, InterruptedException {
        String[] urlTemplates = {
                "https://www.gutenberg.org/files/%d/%d-0.txt",
                "https://www.gutenberg.org/files/%d/%d.txt",
                "https://www.gutenberg.org/cache/epub/%d/pg%d.txt"
        };

        for (String urlTemplate : urlTemplates) {
            String url = String.format(urlTemplate, bookID, bookID);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "BooleanSearch")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                if (body != null && !body.isEmpty()) {
                    LOGGER.info("✓ Downloaded book {} ({} KB)", bookID, body.length() / 1024);
                    return body;
                }
            }
        }

        throw new IOException("Failed to download book " + bookID);
    }


    public static void saveToFile(String filename, String content) throws IOException {
        Path filePath = Path.of(filename);
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        System.out.printf("✓ Saved %s: %,d chars (%,d KB)%n",
                filename, content.length(), content.length() / 1024);
    }



    public static void generateSmallFiles() throws ExecutionException, InterruptedException {
        LOGGER.info("=== Generating Small Files (~200-500 KB) ===");

        generateFilesParallel(BookIDs.SMALL, "document/small", 3);
    }

    public static void generateMediumFiles() throws ExecutionException, InterruptedException {
        LOGGER.info("=== Generating Medium Files (~500-800 KB) ===");

        generateFilesParallel(BookIDs.MEDIUM, "document/medium", 3);
    }

    public static void generateLargeFiles() throws ExecutionException, InterruptedException {
        LOGGER.info("=== Generating Large Files (~1-3 MB) ===");

        generateFilesParallel(BookIDs.LARGE, "document/large", 2);
    }

    private static void generateFilesParallel(int[] bookIDs, String prefix, int count) throws ExecutionException, InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < Math.min(bookIDs.length, count); i++) {
                final int index = i;
                final int bookID = bookIDs[i];

                Future<Void> future = executor.submit(() -> {
                   Thread.sleep(index * 2000L);

                   LOGGER.info("  [{}] Downloading book {}...", index + 1, bookID);

                   String text = downloadBook(bookID);

                   String filename = String.format("%s_%d.txt", prefix, bookID);

                   saveToFile(filename, text);

                   return null;
                });

                futures.add(future);
            }

            for (Future<Void> future : futures) {
                future.get();
            }

            LOGGER.info("✓ Completed");
        }

    }
}
