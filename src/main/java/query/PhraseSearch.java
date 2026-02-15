package query;

import enums.SearchStructureType;
import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static util.ValidationFunctions.validateStringAndCheckForEmpty;

@Slf4j
public class PhraseSearch {
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public PhraseSearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex) {
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
    }


    public Optional<Set<Integer>> search(String phrase, SearchStructureType type) {
        var termsOpt = validateStringAndCheckForEmpty(phrase);
        if (termsOpt.isEmpty()) {
            log.debug("Terms were not found");
            return Optional.empty();
        }

        List<String> terms = termsOpt.get();
        if (terms.size() < 2) {
            log.debug("Phrase '{}' has less than 2 tokens, cannot do phrase search", terms);
            return Optional.empty();
        }

        return switch (type) {
            case BIWORD -> searchPhraseBiwordInternal(terms);
            case POSITIONAL -> searchPhrasePositionalInternal(terms);
            default -> {
                log.error("Invalid search structure type: {}", type);
                throw new IllegalArgumentException("Invalid type of search structure: " + type);
            }
        };
    }


    private Optional<Set<Integer>> searchPhraseBiwordInternal(List<String> terms) {
        String firstTerm = terms.get(0);
        String secondTerm = terms.get(1);

        if (firstTerm.isBlank() || secondTerm.isBlank()) {
            log.warn("Found blank term in first biword");
            return Optional.empty();
        }

        var firstBiwordResult = biwordIndex.getBiword(firstTerm, secondTerm);
        if (firstBiwordResult.isEmpty()) {
            log.debug("First biword '{}' '{}' not found", firstTerm, secondTerm);
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>(firstBiwordResult.get());

        for (int i = 1; i < terms.size() - 1; i++) {
            String term1 = terms.get(i);
            String term2 = terms.get(i + 1);

            if (term1.isBlank() || term2.isBlank()) {
                log.warn("Found blank term in phrase at position {}", i);
                return Optional.empty();
            }

            var tempResult = biwordIndex.getBiword(term1, term2);

            if (tempResult.isEmpty()) {
                log.debug("Phrase {} not found", terms);
                return Optional.empty();
            }

            result.retainAll(tempResult.get());

            if (result.isEmpty()) {
                log.debug("Phrase {} not found", terms);
                return Optional.empty();
            }
        }

        return Optional.of(result);
    }

    private Optional<Set<Integer>> searchPhrasePositionalInternal(List<String> terms) {
        Set<Integer> result = new HashSet<>();

        var firstTermData = positionalIndex.getPositions(terms.getFirst());
        if (firstTermData.isEmpty()) {
            return Optional.empty();
        }

        for (var entry : firstTermData.get().entrySet()) {
            int currentDocId = entry.getKey();

            for (var position : entry.getValue()) {
                if (isCorrectPosition(terms, currentDocId, position)) {
                    result.add(currentDocId);
                    break;
                }
            }
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private boolean isCorrectPosition(List<String> terms, int currentDocId, int startPosition) {
        for (int i = 1; i < terms.size(); i++) {
            String currentTerm = terms.get(i);

            int expectedPosition = startPosition + i;
            var termPositions = positionalIndex.getPositionsInDocument(currentTerm, currentDocId);

            if (termPositions.isEmpty() || !termPositions.get().contains(expectedPosition)) {
                return false;
            }
        }

        return true;
    }
}
