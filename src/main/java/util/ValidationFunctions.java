package util;

import lombok.extern.slf4j.Slf4j;
import tokenization.Tokenizer;

import java.util.List;
import java.util.Optional;

@Slf4j
public final class ValidationFunctions {
    private ValidationFunctions() {
        throw new UnsupportedOperationException("ValidationFunctions is utility class");
    }

    public static Optional<List<String>> validateStringAndCheckForEmpty(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            log.warn("Phrase is null or blank");
            return Optional.empty();
        }

        List<String> terms = Tokenizer.tokenize(phrase);
        return terms.isEmpty() ? Optional.empty() : Optional.of(terms);
    }
}
