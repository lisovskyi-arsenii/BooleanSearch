package query;

import index.Dictionary;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class QueryExecutor<T extends Dictionary> {
    private final T dictionary;

    public QueryExecutor(T dictionary) {
        this.dictionary = dictionary;
    }

    public Optional<Set<Integer>> search(String term) {
        return dictionary.getDocuments(term);
    }

    public Optional<Set<Integer>> andSearch(String term1, String term2) {
        var result1 = search(term1);
        var result2 = search(term2);

        if (result1.isEmpty() || result2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> finalResult = new HashSet<>(result1.get());
        finalResult.retainAll(result2.get());
        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
    }

    public Optional<Set<Integer>> orSearch(String term1, String term2) {
        var result1 = search(term1);
        var result2 = search(term2);

        if (result1.isEmpty() && result2.isEmpty()) {
            return Optional.empty();
        }

        Set<Integer> finalResult = new HashSet<>();
        result1.ifPresent(finalResult::addAll);
        result2.ifPresent(finalResult::addAll);

        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
    }

    public Optional<Set<Integer>> notSearch(String term, Set<Integer> allDocsIds) {
        if (allDocsIds.isEmpty()) {
            return Optional.empty();
        }

        var docsWithTerm = dictionary.getDocuments(term);

        Set<Integer> finalResult = new HashSet<>(allDocsIds);
        docsWithTerm.ifPresent(finalResult::removeAll);
        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
    }

    public Optional<Set<Integer>> andSearchMultiple(String... terms) {
        if (terms == null || terms.length == 0) {
            return Optional.empty();
        }

        Optional<Set<Integer>> result = search(terms[0]);

        for (int i = 1; i < terms.length; i++) {
            if (result.isEmpty()) break;

            Optional<Set<Integer>> nextResult = search(terms[i]);
            if (nextResult.isEmpty()) {
                return Optional.empty();
            }

            Set<Integer> intersection = new HashSet<>(result.get());
            intersection.retainAll(nextResult.get());
            result = intersection.isEmpty() ? Optional.empty() : Optional.of(intersection);
        }

        return result;
    }

    public Optional<Set<Integer>> orSearchMultiple(String... terms) {
        if (terms == null || terms.length == 0) {
            return Optional.empty();
        }

        Set<Integer> result = new HashSet<>();

        for (String term : terms) {
            search(term).ifPresent(result::addAll);
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }
}
