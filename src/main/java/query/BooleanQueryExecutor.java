package query;

import index.InvertedIndex;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class BooleanQueryExecutor {
    private final InvertedIndex index;

    public BooleanQueryExecutor(InvertedIndex index) {
        this.index = index;
    }

    public Optional<Set<Integer>> search(String term) {
        return index.getDocuments(term);
    }

    public Optional<Set<Integer>> andSearch(String term1, String term2) {
        Optional<Set<Integer>> result1 = search(term1);
        Optional<Set<Integer>> result2 = search(term2);

        if (result1.isEmpty() || result2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> finalResult = new HashSet<>(result1.get());
        finalResult.retainAll(result2.get());

        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
    }

    public Optional<Set<Integer>> orSearch(String term1, String term2) {
        Optional<Set<Integer>> result1 = search(term1);
        Optional<Set<Integer>> result2 = search(term2);

        if (result1.isEmpty() && result2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> finalResult = new HashSet<>();
        result1.ifPresent(finalResult::addAll);
        result2.ifPresent(finalResult::addAll);

        return Optional.of(finalResult);
    }

    public Optional<Set<Integer>> notSearch(String term, Set<Integer> allDocsIDs) {
        Set<Integer> docsWithTerm = index.getTerm(term);
        if (allDocsIDs.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> finalResult = new HashSet<>(allDocsIDs);
        finalResult.removeAll(docsWithTerm);

        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
    }
}
