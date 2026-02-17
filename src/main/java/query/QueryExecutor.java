package query;

import index.Dictionary;

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

//    public Optional<Set<Integer>> andSearch(String term1, String term2) {
//        var result1 = search(term1);
//        var result2 = search(term2);
//
//        if (result1.isEmpty() || result2.isEmpty()) {
//            return Optional.empty();
//        }
//
//        Set<Integer> finalResult = new HashSet<>(result1.get());
//        finalResult.retainAll(result2.get());
//        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
//    }
//
//    public Optional<Set<Integer>> orSearch(String term1, String term2) {
//        var result1 = search(term1);
//        var result2 = search(term2);
//
//        if (result1.isEmpty() && result2.isEmpty()) {
//            return Optional.empty();
//        }
//
//        Set<Integer> finalResult = new HashSet<>();
//        result1.ifPresent(finalResult::addAll);
//        result2.ifPresent(finalResult::addAll);
//
//        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
//    }
//
//    public Optional<Set<Integer>> notSearch(String term, Set<Integer> allDocsIds) {
//        if (allDocsIds.isEmpty()) {
//            return Optional.empty();
//        }
//
//        var docsWithTerm = dictionary.getDocuments(term);
//
//        Set<Integer> finalResult = new HashSet<>(allDocsIds);
//        docsWithTerm.ifPresent(finalResult::removeAll);
//        return finalResult.isEmpty() ? Optional.empty() : Optional.of(finalResult);
//    }
}
