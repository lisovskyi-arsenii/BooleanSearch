package tokenization;

import java.util.Objects;

public final class TextNormalizer {
    private TextNormalizer() {
        throw new AssertionError("%s class is utility class - cannot create instance of it".formatted(getClass().getSimpleName()));
    }

    public static String normalize(String text) {
        Objects.requireNonNull(text, "Text in normalize() must not be null");
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "").trim();
    }
}
