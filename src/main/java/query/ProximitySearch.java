package query;

import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class ProximitySearch {
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public ProximitySearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex) {
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
    }

    public Optional<Set<Integer>> searchProximityBiword(String term1, String term2, int k) {
        if (term1 == null || term2 == null) {
            log.warn("Term1 or term2 is null");
            return Optional.empty();
        }

        if (term1.isBlank() || term2.isBlank()) {
            log.warn("Term1 or term2 is blank");
            return Optional.empty();
        }

        if (k < 1) {
            log.warn("k must be positive");
            return Optional.empty();
        }
        if (k == 1) {
            return biwordIndex.getBiword(term1, term2);
        }

        log.debug("k={} > 1, biword index cannot handle this efficiently. Failing back to positional index", k);

        // fallback to positional index
        var searchMatchesOpt = searchProximity(term1, term2, k);
        if (searchMatchesOpt.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> docIds = searchMatchesOpt.get().stream()
                .map(ProximityMatch::docId)
                .collect(Collectors.toSet());

        return docIds.isEmpty() ? Optional.empty() : Optional.of(docIds);
    }

    public Optional<Set<ProximityMatch>> searchProximity(String term1, String term2, int k) {
        if (term1 == null || term2 == null) {
            log.warn("term1 or term2 is null");
            return Optional.empty();
        }

        if (term1.isBlank() || term2.isBlank()) {
            log.warn("term1 or term2 is blank");
            return Optional.empty();
        }

        var postings1 = positionalIndex.getPositions(term1);
        var postings2 = positionalIndex.getPositions(term2);

        if (postings1.isEmpty() || postings2.isEmpty()) {
            return Optional.empty();
        }

        Set<ProximityMatch> result = new HashSet<>();

        var p1 = postings1.get().entrySet().iterator();
        var p2 = postings2.get().entrySet().iterator();

        var entry1 = p1.hasNext() ? p1.next() : null;
        var entry2 = p2.hasNext() ? p2.next() : null;

        while (entry1 != null && entry2 != null) {
            int docId1 = entry1.getKey();
            int docId2 = entry2.getKey();

            if (docId1 == docId2) {
                var positions1 = entry1.getValue();
                var positions2 = entry2.getValue();

                for (int pos1 : positions1) {
                    for (int pos2 : positions2) {
                        int distance = pos2 - pos1;

                        if (distance > k) {
                            break;
                        }

                        if (distance < -k) {
                            continue;
                        }

                        result.add(new ProximityMatch(docId1, pos1, pos2));
                    }
                }

                entry1 = p1.hasNext() ? p1.next() : null;
                entry2 = p2.hasNext() ? p2.next() : null;

            } else if (docId1 < docId2) {
                entry1 = p1.hasNext() ? p1.next() : null;
            } else {
                entry2 = p2.hasNext() ? p2.next() : null;
            }
        }
        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    public record ProximityMatch(int docId, int position1, int position2) {
        public int distance() {
            return Math.abs(position1 - position2);
        }
    }
}
