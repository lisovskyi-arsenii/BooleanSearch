package parser;

import enums.ZoneWeight;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

enum State { BEFORE_CONTENT, IN_BODY, FINISHED }

public final class ZoneParser {
    private ZoneParser() {
        throw new UnsupportedOperationException("ZoneParser is utility class - cannot be instantiated");
    }

    public static Map<ZoneWeight, String> parseDocuments(Path file) throws IllegalArgumentException, IOException {
        if (file == null) {
            throw new IllegalArgumentException("File cannot be null");
        }
        if (!Files.exists(file)) {
            throw new IllegalArgumentException("File does not exist");
        }

        Map<ZoneWeight, String> result = new EnumMap<>(ZoneWeight.class);

        List<String> lines = Files.readAllLines(file);

        Map<ZoneWeight, StringBuilder> builders = new EnumMap<>(ZoneWeight.class);
        for (var zone : ZoneWeight.values()) {
            builders.put(zone, new StringBuilder());
        }

        State state = State.BEFORE_CONTENT;

        for (var line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);

            String title = "title: ";
            String author = "author: ";
            if (lower.startsWith(title)) {
                builders.get(ZoneWeight.TITLE)
                        .append(trimmed.substring(title.length()));
            } else if (lower.startsWith(author)) {
                builders.get(ZoneWeight.AUTHOR)
                        .append(trimmed.substring(author.length()));
            } else if (lower.contains("*** start of the project gutenberg")) {
                state = State.IN_BODY;
            } else if (lower.contains("*** end of the project gutenberg")) {
                state = State.FINISHED;
                break;
            }

            if (state == State.IN_BODY && !trimmed.isBlank()) {
                builders.get(ZoneWeight.BODY)
                        .append(trimmed).append(" ");
            }
        }

        for (var entry : builders.entrySet()) {
            result.put(entry.getKey(), entry.getValue().toString().trim());
        }

        return result;
    }


}
