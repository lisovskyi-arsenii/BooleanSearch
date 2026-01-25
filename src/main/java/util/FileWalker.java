package util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class FileWalker {
    private FileWalker() {
        throw new UnsupportedOperationException("FileWalker is utility class - cannot create instance");
    }

    @SafeVarargs
    public static List<Path> findFiles(String directoryPath, Predicate<Path>... predicates) throws IOException, IllegalArgumentException {
        Path dir = Paths.get(directoryPath);

        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            throw new IllegalArgumentException("%s is not a directory".formatted(directoryPath));
        }

        Predicate<Path> totalPredicates = Arrays.stream(predicates)
                .reduce(_ -> true, Predicate::and);

        try (Stream<Path> stream = Files.walk(dir)) {
            return stream
                    .parallel()
                    .filter(Files::isRegularFile)
                    .filter(totalPredicates)
                    .toList();
        }
    }
}
