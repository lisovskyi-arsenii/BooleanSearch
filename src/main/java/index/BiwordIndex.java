package index;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
public class BiwordIndex implements Dictionary {
    // term1 + term2 -> [docIds]
    private final Map<String, Set<Integer>> biwordIndex = new ConcurrentHashMap<>();

    public void addWord(String term1, String term2, int docId) {
        String biword = term1 + " " + term2;
        biwordIndex.computeIfAbsent(biword, _ -> ConcurrentHashMap.newKeySet())
                .add(docId);
    }

    public void loadIndex(Map<String, Set<Integer>> newBiwordIndex) {
        biwordIndex.clear();

        newBiwordIndex.forEach((biword, set) -> {
            Set<Integer> newSet = ConcurrentHashMap.newKeySet();
            newSet.addAll(set);
            biwordIndex.put(biword, newSet);
        });
    }

    public Map<String, Set<Integer>> getBiwordIndex() {
        return Collections.unmodifiableMap(biwordIndex);
    }

    public Optional<Set<Integer>> getBiword(String term1, String term2) {
        String biword = term1 + " " + term2;
        return getDocuments(biword);
    }

    @Override
    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(biwordIndex.keySet());
    }

    @Override
    public Optional<Set<Integer>> getDocuments(String term) {
        Set<Integer> docs = biwordIndex.get(term);
        return docs != null && !docs.isEmpty()
                ? Optional.of(new HashSet<>(docs))
                : Optional.empty();
    }

    @Override
    public int size() {
        return biwordIndex.size();
    }

    @Override
    public int getTotalTermOccurrences() {
        return biwordIndex.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Override
    public void clear() {
        biwordIndex.clear();
    }

    public void print() {
        if (biwordIndex.isEmpty()) {
            System.out.println("Biword index is empty");
            return;
        }

        System.out.println("=== BIWORD INDEX ===");
        biwordIndex.forEach((biword, docIds) ->
                System.out.printf("'%s': %s%n", biword, docIds));
        System.out.println("====================");
    }
}
