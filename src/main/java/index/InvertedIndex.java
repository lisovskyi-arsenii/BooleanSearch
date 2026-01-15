package index;

import java.util.*;

public class InvertedIndex {
    private Map<String, Set<Integer>> index = new HashMap<>(); // term -> [document ids]

    public void addTerm(String term, int docID) {
        index.computeIfAbsent(term, k -> new HashSet<>()).add(docID);
    }

    public void loadIndex(Map<String, Set<Integer>> index) {
        this.index = new HashMap<>(index);
    }

    public Map<String, Set<Integer>> getIndex() {
        return Collections.unmodifiableMap(index);
    }

    public Optional<Set<Integer>> getDocuments(String term) {
        Set<Integer> docs = index.get(term);
        return docs != null && !docs.isEmpty()
                ? Optional.of(new HashSet<>(docs))
                : Optional.empty();
    }

    public Set<Integer> getTerm(String term) {
        return index.getOrDefault(term, Collections.emptySet());
    }

    public Set<Integer> getTermOrDefault(String term, int defaultValue) {
        return index.getOrDefault(term, Set.of(defaultValue));
    }

    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(index.keySet());
    }

    public int size() {
        return index.size();
    }

    public int getTotalTermOccurrences() {
        return index.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    public void clear() {
        index.clear();
    }

    public void print() {
        index.forEach((term, docID) -> System.out.println(term + ": " + docID));
    }
}
