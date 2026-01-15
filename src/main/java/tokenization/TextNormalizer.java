package tokenization;

public final class TextNormalizer {
    private TextNormalizer() {
        throw new AssertionError("%s class is utility class - cannot create instance of it".formatted(getClass().getSimpleName()));
    }

    public static String normalize(String text) {
        return text.toLowerCase().replaceAll("[^\\p{L}\\p{N}]+", "").trim();
    }
}
