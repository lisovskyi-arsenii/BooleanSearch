package index;

import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class PositionalIndex {
    // term -> {docId, position}
    private final Map<String, Map<Integer, List<Integer>>> index = new ConcurrentHashMap<>();

    public void addTerm(String term, int docId, int position) {
        index.computeIfAbsent(term, _ -> new ConcurrentHashMap<>())
                .computeIfAbsent(docId, _ -> new ArrayList<>())
                .add(position);
    }

    public void loadIndex(Map<String, Map<Integer, List<Integer>>> newIndex) {
        index.clear();

        newIndex.forEach((term, positionData) -> {
            var map = new ConcurrentHashMap<Integer, List<Integer>>();
            positionData.forEach((docId, _) -> {
                map.computeIfAbsent(docId, _ -> new ArrayList<>());
            });
            index.put(term, map);
        });
    }

    Optional<Map<Integer, List<Integer>>> getPositions(String term) {
        var map = index.get(term);
        return map != null ? Optional.of(map) : Optional.empty();
    }

    Optional<List<Integer>> getPositionsInDocument(String term, int docId) {
        var map = index.get(term);
        if (map == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(map.get(docId));
    }

    Optional<Set<Integer>> getDocuments(String term) {
        var map = index.get(term);
        return Optional.of(map.keySet());
    }

    int size() {
        return index.size();
    }

    int getTotalTermOccurrences() {
        return 0;
    }

}
