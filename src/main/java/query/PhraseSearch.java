package query;

import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class PhraseSearch {
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public PhraseSearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex) {
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
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
