package index;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
public class InvertedIndex implements Dictionary {
    private final Map<String, Set<Integer>> index = new ConcurrentHashMap<>(); // term -> [document ids]

    public void addTerm(String term, int docID) {
        index.computeIfAbsent(term, _ -> ConcurrentHashMap.newKeySet()).add(docID);
    }

    public void loadIndex(Map<String, Set<Integer>> newIndex) {
        index.clear();

        newIndex.forEach((term, docIDs) -> {
            Set<Integer> threadSafeSet = ConcurrentHashMap.newKeySet();
            threadSafeSet.addAll(docIDs);
            index.put(term, threadSafeSet);
        });
    }

    public Map<String, Set<Integer>> getIndex() {
        return Collections.unmodifiableMap(index);
    }

    public Set<Map.Entry<String, Set<Integer>>> entrySet() {
        return Collections.unmodifiableSet(index.entrySet());
    }

    @Override
    public Optional<Set<Integer>> getDocuments(String term) {
        Set<Integer> docs = index.get(term);
        return docs != null && !docs.isEmpty()
                ? Optional.of(new HashSet<>(docs))
                : Optional.empty();
    }

    public Set<String> getAllTerms() {
        return Collections.unmodifiableSet(index.keySet());
    }

    @Override
    public int size() {
        return index.size();
    }

    public int getTotalTermOccurrences() {
        return index.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Override
    public void clear() {
        index.clear();
    }

    public void print() {
        index.forEach((term, docID) ->
                System.out.println(term + ": " + docID));
    }
}
