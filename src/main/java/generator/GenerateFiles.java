package generator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

import static constants.Filenames.DIRECTORY_PATH;

public final class GenerateFiles {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateFiles.class);
    private static final String DIRECTORY_SMALL = "small";
    private static final String DIRECTORY_MEDIUM = "medium";
    private static final String DIRECTORY_LARGE = "large";
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private GenerateFiles() {
        throw new UnsupportedOperationException("GenerateFiles class cannot be instantiated");
    }

    private static String downloadBook(int bookID) throws IOException, InterruptedException {
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

    private static void saveToFile(String filename, String content) throws IOException {
        Path filePath = Path.of(filename);
        if (!Files.exists(filePath)) {
            Files.createFile(filePath);
        }

        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        System.out.printf("✓ Saved %s: %,d chars (%,d KB)%n",
                filename, content.length(), content.length() / 1024);
    }

    public static void generateSmallFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        LOGGER.info("Generating Small Files (~200-500 KB)");

        generateFilesParallel(BookIDs.SMALL, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_SMALL), quantityOfFiles);
    }

    public static void generateMediumFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        LOGGER.info("Generating Medium Files (~500-800 KB)");

        generateFilesParallel(BookIDs.MEDIUM, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_MEDIUM), quantityOfFiles);
    }

    public static void generateLargeFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        LOGGER.info("Generating Large Files (~1-3 MB)");

        generateFilesParallel(BookIDs.LARGE, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_LARGE), quantityOfFiles);
    }

    private static void generateFilesParallel(int[] bookIDs, String prefix, int quantityOfFiles) throws ExecutionException, InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < quantityOfFiles; i++) {
                final int index = i;
                final int bookID = bookIDs[i % bookIDs.length];

                Future<Void> future = executor.submit(() -> {
                    try {
                        Thread.sleep(index * 2000L);
                        LOGGER.info("  [{}] Downloading book {}...", index + 1, bookID);

                        String text = downloadBook(bookID);
                        String filename = String.format("%s_%d_%d.txt", prefix, bookID, index);

                        saveToFile(filename, text);
                        return null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOGGER.error("Task interrupted for book {}", bookID);
                        throw new RuntimeException("Task interrupted", e);
                    } catch (IOException e) {
                        LOGGER.error("Failed to download/save book {}: {}",
                                bookID, e.getMessage());
                        throw new RuntimeException("Failed to process book " + bookID, e);
                    }
                });

                futures.add(future);
            }

            int successCount = 0;
            int failureCount = 0;

            for (Future<Void> future : futures) {
                try {
                    future.get();
                    successCount++;
                } catch (InterruptedException | ExecutionException e) {
                    failureCount++;
                    LOGGER.error("Task failed: {}", e.getCause().getMessage());
                }
            }

            if (failureCount > 0) {
                LOGGER.warn("  Generated {}/{} files ({} failed)", successCount, quantityOfFiles, failureCount);
            } else {
                LOGGER.info("  Successfully generated all {} files", quantityOfFiles);
            }
        }
    }

    private static final class BookIDs {
        static final int[] SMALL = {
                43, 1342, 11, 174, 1661
        };

        static final int[] MEDIUM = {
                84, 345, 76, 98
        };

        static final int[] LARGE = {
                2701, 2600, 1399, 1661
        };
    }
}
