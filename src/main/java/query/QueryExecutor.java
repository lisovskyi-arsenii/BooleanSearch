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
}
