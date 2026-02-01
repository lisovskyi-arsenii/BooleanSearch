package query;

import core.BooleanSearchEngine;
import index.BiwordIndex;
import index.PositionalIndex;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class ProximitySearch {
    private final BooleanSearchEngine searchEngine;
    private final PositionalIndex positionalIndex;
    private final BiwordIndex biwordIndex;

    public ProximitySearch(PositionalIndex positionalIndex, BiwordIndex biwordIndex, BooleanSearchEngine searchEngine) {
        this.searchEngine = searchEngine;
        this.positionalIndex = positionalIndex;
        this.biwordIndex = biwordIndex;
    }

    public List<ProximityMatch> searchProximity(String term1, String term2, int k) {
        if (term1 == null || term2 == null) {
            log.warn("term1 or term2 is null");
            return List.of();
        }

        if (term1.isBlank() || term2.isBlank()) {
            log.warn("term1 or term2 is blank");
            return List.of();
        }

        var postings1 = positionalIndex.getPositions(term1);
        var postings2 = positionalIndex.getPositions(term2);

        if (postings1.isEmpty() || postings2.isEmpty()) {
            return List.of();
        }

        List<ProximityMatch> result = new ArrayList<>();

        var p1 = postings1.get().entrySet().iterator();
        var p2 = postings2.get().entrySet().iterator();

        var entry1 = p1.hasNext() ? p1.next() : null;
        var entry2 = p2.hasNext() ? p2.next() : null;

        while (entry1 != null && entry2 != null) {
            int docId1 = entry1.getKey();
            int docId2 = entry2.getKey();

            if (docId1 == docId2) {
                List<Integer> l = new ArrayList<>();

                var positions1 = entry1.getValue();
                var positions2 = entry2.getValue();

                for (int pos1 : positions1) {
                    for (int pos2 : positions2) {
                        if (Math.abs(pos1 - pos2) <= k) {
                            l.add(pos2);
                        } else if (pos2 > pos1) {
                            break;
                        }
                    }

                    while (!l.isEmpty() && Math.abs(l.getFirst() - pos1) > k) {
                        l.removeFirst();
                    }

                    for (int ps : l) {
                        result.add(new ProximityMatch(docId1, pos1, ps));
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
        return result;
    }

    public record ProximityMatch(int docId, int position1, int position2) {}
}
