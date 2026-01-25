package tokenization;

import java.util.Objects;

public final class TextNormalizer {
    private TextNormalizer() {
        throw new UnsupportedOperationException("TextNormalizer class is utility class - cannot create instance of it");
    }

    public static String normalize(String text) {
        Objects.requireNonNull(text, "Text in normalize() must not be null");
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "").trim();
    }
}
