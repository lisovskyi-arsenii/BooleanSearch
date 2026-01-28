package index;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
public class PositionalIndex implements Dictionary {
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
            var docMap = new ConcurrentHashMap<Integer, List<Integer>>();

            positionData.forEach((docId, positions) -> {
                docMap.put(docId, new ArrayList<>(positions));
            });

            index.put(term, docMap);
        });
    }

    public Optional<Map<Integer, List<Integer>>> getPositions(String term) {
        var map = index.get(term);
        return map != null && !map.isEmpty()
                ? Optional.of(new HashMap<>(map))
                : Optional.empty();
    }

    public Optional<List<Integer>> getPositionsInDocument(String term, int docId) {
        var map = index.get(term);
        if (map == null) {
            return Optional.empty();
        }

        List<Integer> positions = map.get(docId);
        return positions != null && !positions.isEmpty()
                ? Optional.of(new ArrayList<>(positions))
                : Optional.empty();
    }

    @Override
    public Optional<Set<Integer>> getDocuments(String term) {
        var map = index.get(term);

        if (map == null || map.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(map.keySet());
    }


    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(index.keySet());
    }

    @Override
    public int size() {
        return index.size();
    }

    @Override
    public int getTotalTermOccurrences() {
        return index.values().stream()
                .mapToInt(docMap -> docMap.values().stream()
                        .mapToInt(List::size)
                        .sum())
                .sum();
    }

    @Override
    public void clear() {
        index.clear();
    }

    public void print() {
        if (index.isEmpty()) {
            System.out.println("Positional index is empty");
            return;
        }

        System.out.println("=== POSITIONAL INDEX ===");
        index.forEach((term, docMap) -> {
            System.out.println(term + ":");
            docMap.forEach((docId, positions) ->
                    System.out.printf("  Doc %d: %s%n", docId, positions));
        });
        System.out.println("========================");
    }

}
