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

    public Optional<Set<Integer>> searchPhraseBiword(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return Optional.empty();
        }

        String[] tokens = phrase.split("\\s+");

        if (tokens.length < 2) {
            log.debug("Phrase '{}' has less than 2 tokens, cannot form biwords", phrase);
            return Optional.empty();
        }

        System.out.println(Arrays.toString(tokens));

        Set<Integer> result = null;

        for (int i = 0; i < tokens.length - 1; i++) {
            if (tokens[i].isBlank() || tokens[i + 1].isBlank()) {
                continue;
            }

            var tempResult = searchEngine.andSearch(tokens[i], tokens[i + 1], SearchStructureType.BIWORD);
            if (tempResult.isEmpty()) {
                log.debug("Phrase " + phrase + " not found");
                return Optional.empty();
            }

            if (result == null) {
                result = new HashSet<>(tempResult.get());
            } else {
                result = new HashSet<>(Sets.intersection(result, tempResult.get()));
                if (result.isEmpty()) {
                    log.debug("Phrase " + phrase + " not found");
                    return Optional.empty();
                }
            }
        }

        System.out.println(Arrays.toString(tokens));

        return Optional.ofNullable(result);
    }

    public Optional<Set<Integer>> searchPhrasePositional(List<String> terms) {
        if (terms == null || terms.isEmpty()) {
            log.error("PhraseSearch: terms is null or empty");
            return Optional.empty();
        }

        if (terms.size() == 1) {
            return positionalIndex.getDocuments(terms.getFirst());
        }

        Optional<Map<Integer, List<Integer>>> firstTermPositions = positionalIndex.getPositions(terms.getFirst());

        if (firstTermPositions.isEmpty()) {
            log.error("PhraseSearch: firstTermPositions is empty");
            return Optional.empty();
        }

        Set<Integer> resultDocs = new HashSet<>();

        for (var entry : firstTermPositions.get().entrySet()) {
            int docId = entry.getKey();
            List<Integer> positions = entry.getValue();

            for (int startPosition : positions) {
                if (isPhraseAtPosition(terms, docId, startPosition)) {
                    resultDocs.add(docId);
                    break;
                }
            }
        }

        if (resultDocs.isEmpty()) {
            log.debug("Phrase '{}' not found", String.join(" ", terms));
            return Optional.empty();
        }

        log.debug("Phrase '{}' found in {} documents",
                String.join(" ", terms), resultDocs.size());
        return Optional.of(resultDocs);
    }

    private boolean isPhraseAtPosition(List<String> terms, int docId, int startPosition) {
        for (int i = 1; i < terms.size(); i++) {
            String term = terms.get(i);
            int expectedPosition = startPosition + i;

            Optional<List<Integer>> termPositions = positionalIndex.getPositionsInDocument(term, docId);

            if (termPositions.isEmpty() || !termPositions.get().contains(expectedPosition)) {
                return false;
            }
        }
        return true;
    }
}
