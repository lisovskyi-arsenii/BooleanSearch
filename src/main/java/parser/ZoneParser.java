package parser;

import enums.ZoneWeight;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class ZoneParser {
    private enum State { BEFORE_CONTENT, IN_BODY }

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


        Map<ZoneWeight, StringBuilder> builders = new EnumMap<>(ZoneWeight.class);
        for (var zone : ZoneWeight.values()) {
            builders.put(zone, new StringBuilder());
        }

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        State state = State.BEFORE_CONTENT;

        String title = "title: ";
        String author = "author: ";
        String subject = "subject: ";
        String startOfBody = "*** start of the project gutenberg";
        String endOfBody = "*** end of the project gutenberg";

        for (var line : lines) {
            String trimmed = line.trim();
            String lower = trimmed.toLowerCase(Locale.ROOT);

            if (lower.startsWith(title)) {
                builders.get(ZoneWeight.TITLE)
                        .append(trimmed.substring(title.length()));
            } else if (lower.startsWith(author)) {
                builders.get(ZoneWeight.AUTHOR)
                        .append(trimmed.substring(author.length()));
            } else if (lower.startsWith(subject)) {
                builders.get(ZoneWeight.SUBJECT)
                        .append(trimmed.substring(subject.length()));
            } else if (lower.contains(startOfBody)) {
                state = State.IN_BODY;
            } else if (lower.contains(endOfBody)) {
                break;
            } else if (state == State.IN_BODY && !trimmed.isBlank()) {
                builders.get(ZoneWeight.BODY)
                        .append(trimmed).append(" ");
            }
        }

        Map<ZoneWeight, String> result = new EnumMap<>(ZoneWeight.class);
        builders.forEach((zone, sb) -> result.put(zone, sb.toString().trim()));
        return result;
    }


}
