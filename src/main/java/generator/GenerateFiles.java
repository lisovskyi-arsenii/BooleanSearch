package generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static constants.Filenames.DIRECTORY_PATH;

record AuthorMetadata (String name, int birthYear) {}
record BookMetadata (int bookId, String title, AuthorMetadata authorMetadata, List<String> subjects, List<String> languages) {}

@Slf4j
public final class GenerateFiles {
    private static final String DIRECTORY_SMALL = "small";
    private static final String DIRECTORY_MEDIUM = "medium";
    private static final String DIRECTORY_LARGE = "large";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private GenerateFiles() {
        throw new UnsupportedOperationException("GenerateFiles class cannot be instantiated");
    }

    //
    // public API
    //
    public static void generateSmallFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        log.info("Generating Small Files (~200-500 KB)");

        generateFilesParallel(BookIDs.SMALL, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_SMALL), quantityOfFiles);
    }

    public static void generateMediumFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        log.info("Generating Medium Files (~500-800 KB)");

        generateFilesParallel(BookIDs.MEDIUM, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_MEDIUM), quantityOfFiles);
    }

    public static void generateLargeFiles(int quantityOfFiles) throws ExecutionException, InterruptedException {
        log.info("Generating Large Files (~1-3 MB)");

        generateFilesParallel(BookIDs.LARGE, String.format("%s/%s", DIRECTORY_PATH, DIRECTORY_LARGE), quantityOfFiles);
    }

    //
    // private methods
    //
    private static String downloadBook(int bookId) throws IOException, InterruptedException {
        String[] urlTemplates = {
                "https://www.gutenberg.org/files/%d/%d-0.txt",
                "https://www.gutenberg.org/files/%d/%d.txt",
                "https://www.gutenberg.org/cache/epub/%d/pg%d.txt"
        };

        for (String urlTemplate : urlTemplates) {
            String url = String.format(urlTemplate, bookId, bookId);
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
                    try {
                        BookMetadata meta = fetchMetadata(bookId);
                        log.info("Downloaded book {} - \"{}\" ({} KB)",
                                bookId, meta.title(), body.length() / 1024);
                        return prependMetadata(meta, body);
                    } catch (IOException e) {
                        log.warn("Could not fetch metadata for book {}: {}", bookId, e.getMessage());
                        return body;
                    }
                }
            }
        }

        throw new IOException("Failed to download book " + bookId);
    }

    private static void saveToFile(String filename, String content) throws IOException {
        Path filePath = Path.of(filename);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        System.out.printf("  Saved %s: %,d chars (%,d KB)%n",
                filename, content.length(), content.length() / 1024);
    }

    private static void generateFilesParallel(int[] bookIds, String prefix, int quantityOfFiles) throws ExecutionException, InterruptedException {
        List<Integer> shuffledIds = new ArrayList<>();
        for (int id : bookIds) shuffledIds.add(id);
        Collections.shuffle(shuffledIds);

        int actualCount = Math.min(quantityOfFiles, shuffledIds.size());
        if (actualCount < quantityOfFiles) {
            log.warn("Requested {} files but only {} unique books available. Generating {}.",
                    quantityOfFiles, shuffledIds.size(), actualCount);
        }

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Void>> futures = new ArrayList<>();

            for (int i = 0; i < actualCount; i++) {
                final int index = i;
                final int bookID = shuffledIds.get(index);

                Future<Void> future = executor.submit(() -> {
                    try {
                        log.info("  [{}] Downloading book {}...", index + 1, bookID);

                        String text = downloadBook(bookID);
                        String filename = String.format("%s_%d.txt", prefix, bookID);

                        saveToFile(filename, text);
                        return null;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        log.error("Task interrupted for book {}", bookID);
                        throw new RuntimeException("Task interrupted", e);
                    } catch (IOException e) {
                        log.error("Failed to download/save book {}: {}",
                                bookID, e.getMessage());
                        throw new UncheckedIOException("Failed to process book " + bookID, e);
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
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failureCount++;
                    log.error("Task interrupted: {}", e.getCause().getMessage());
                } catch (ExecutionException e) {
                    failureCount++;
                    log.error("Task failed: {}", e.getCause().getMessage());
                }
            }

            if (failureCount > 0) {
                log.warn("  Generated {}/{} files ({} failed)", successCount, actualCount, failureCount);
            } else {
                log.info("  Successfully generated all {} files", actualCount);
            }
        }
    }

    private static BookMetadata fetchMetadata(int bookId)
            throws IOException, InterruptedException {
        final String url = "https://gutendex.com/books/" + bookId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "BooleanSearch")
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Metadata not found for book " + bookId);
        }

        JsonNode root = OBJECT_MAPPER.readTree(response.body());

        String title = root.path("title").asText("Unknown title");

        String authorName = "Unknown author";
        int authorBirthYear = 0;
        JsonNode authors = root.path("authors");
        if (authors.isArray() && !authors.isEmpty()) {
            JsonNode firstAuthor = authors.get(0);
            authorName = firstAuthor.path("name").asText("Unknown author");
            authorBirthYear = firstAuthor.path("birth_year").asInt(0);
        }

        List<String> subjects = new ArrayList<>();
        root.path("subjects").forEach(subject -> subjects.add(subject.asText()));

        List<String> languages = new ArrayList<>();
        root.path("languages").forEach(lang -> languages.add(lang.asText()));

        return new BookMetadata(bookId, title, new AuthorMetadata(authorName, authorBirthYear), subjects, languages);
    }

    private static String prependMetadata(BookMetadata metadata, String content) {
        log.info("Prepending metadata to the start of the document");
        String subjects = String.join(", ", metadata.subjects());
        return String.format(
                "Title: %s%nAuthor: %s%nSubject: %s%nEBook-No.: %d%n%n%s",
                metadata.title(),
                metadata.authorMetadata().name(),
                subjects.isEmpty() ? "Unknown" : subjects,
                metadata.bookId(),
                content
        );
    }


    private static final class BookIDs {
        static final int[] SMALL = {
                43, 11, 1661, 74, 1952, 46, 514, 16, 1400, 1232,
                2591, 2542, 1727, 55, 219, 3207, 4300, 5200, 120, 768
        };

        static final int[] MEDIUM = {
                84, 345, 76, 98, 1260, 2413, 526, 1322, 6593, 2814,
                786, 1184, 3825, 2500, 244, 1080, 4363, 2097, 1250, 158
        };

        static final int[] LARGE = {
                2701, 2600, 1399, 1661, 135, 3296, 2554, 1342, 4300,
                8800, 6761, 3207, 19942, 16328, 4517, 7370, 2148, 3600
        };
    }


}
