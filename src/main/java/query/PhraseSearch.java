package query;

import com.google.common.collect.Sets;
import core.BooleanSearchEngine;
import enums.SearchStructureType;
import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PhraseSearch {
    private final BooleanSearchEngine searchEngine;
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public PhraseSearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex, BooleanSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
    }

    public Optional<Set<Integer>> searchPhraseBiword(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            log.warn("Terms in searchPhraseBiword is null or empty");
            return Optional.empty();
        }

        if (terms.size() < 2) {
            log.debug("Phrase '{}' has less than 2 tokens, cannot form biwords", terms);
            return Optional.empty();
        }

        Set<Integer> result = null;
        for (int i = 0; i < terms.size() - 1; i++) {
            if (terms.get(i).isBlank() || terms.get(i + 1).isBlank()) {
                continue;
            }

            final String biword = terms.get(i) + " " + terms.get(i + 1);
            var tempResult = searchEngine.search(biword, SearchStructureType.BIWORD);
            if (tempResult.isEmpty()) {
                log.debug("Phrase {} not found", terms);
                return Optional.empty();
            }

            if (result == null) {
                result = new HashSet<>(tempResult.get());
            } else {
                result = new HashSet<>(Sets.intersection(result, tempResult.get()));
                if (result.isEmpty()) {
                    log.debug("Phrase {} not found", terms);
                    return Optional.empty();
                }
            }
        }

        return Optional.ofNullable(result);
    }

    public Optional<Set<Integer>> searchPhrasePositional(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            log.warn("Terms in searchPhrasePositional is null or empty");
            return Optional.empty();
        }

        if (terms.size() < 2) {
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>();

        var firstTermData = positionalIndex.getPositions(terms.getFirst());
        if (firstTermData.isEmpty()) {
            return Optional.empty();
        }

        for (var entry : firstTermData.get().entrySet()) {
            int currentDocId = entry.getKey();

            for (var position : entry.getValue()) {
                if (isCorrectPlace(terms, currentDocId, position)) {
                    result.add(currentDocId);
                    break;
                }
            }
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    private boolean isCorrectPlace(List<String> terms, int currentDocId, int startPosition) {
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
