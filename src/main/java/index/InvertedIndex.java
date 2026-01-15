package index;

import java.util.*;

public class InvertedIndex {
    private final Map<String, Set<Integer>> index = new HashMap<>(); // term -> [document ids]

    public void addTerm(String term, int docID) {
        index.computeIfAbsent(term, k -> new HashSet<>()).add(docID);
    }

    public Optional<Set<Integer>> getDocuments(String term) {
        return Optional.ofNullable(index.get(term));
    }

    public Set<Integer> getTerm(String term) {
        return index.get(term);
    }

    public Set<Integer> getTermOrDefault(String term, int defaultValue) {
        return index.getOrDefault(term, Set.of(defaultValue));
    }

    public Set<Integer> getAllTerms() {
        Set<Integer> allTerms = new HashSet<>();
        for (Map.Entry<String, Set<Integer>> entry : index.entrySet()) {
            allTerms.addAll(entry.getValue());
        }
        return allTerms;
    }

    public int size() {
        return index.size();
    }

    public void clear() {
        index.clear();
    }
}
