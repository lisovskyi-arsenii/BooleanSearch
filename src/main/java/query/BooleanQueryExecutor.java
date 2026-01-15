package query;

import index.InvertedIndex;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BooleanQueryExecutor {
    private final InvertedIndex index;

    public BooleanQueryExecutor(InvertedIndex index) {
        this.index = index;
    }

    public Optional<Set<Integer>> search(String term) {
        return Optional.ofNullable(index.getTerm(term));
    }

    public Optional<Set<Integer>> search(String term) {
        return Optional.ofNullable(index.getTerm(term));
    }

    public Optional<Set<Integer>> andSearch(String term1, String term2) {
        Optional<Set<Integer>> result1 = search(term1);
        Optional<Set<Integer>> result2 = search(term2);

        if (result1.isPresent() && result2.isPresent()) {
            Set<Integer> finalResult = new HashSet<>(result1.get());
            finalResult.retainAll(result2.get());
            return Optional.of(finalResult);
        }
        return Optional.empty();
    }

    public Set<Integer> orSearch(String term1, String term2) {
        Set<Integer> result1 = index.getOrDefault(term1, Collections.emptySet());
        Set<Integer> result2 = index.getOrDefault(term2, Collections.emptySet());

        Set<Integer> finalResult = new HashSet<>(result1);
        finalResult.addAll(result2);
        return finalResult;
    }

    public Set<Integer> notSearch(String term, Set<Integer> allDocsIDs) {
        Set<Integer> docsWithTerm = index.getOrDefault(term, Collections.emptySet());

        Set<Integer> finalResult = new HashSet<>(allDocsIDs);
        finalResult.removeAll(docsWithTerm);
        return finalResult;
    }
}
