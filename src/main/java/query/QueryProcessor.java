package query;

import index.InvertedIndex;

import java.util.Optional;
import java.util.Set;

public class QueryProcessor {
    private final BooleanQueryExecutor executor;
    public QueryProcessor(InvertedIndex invertedIndex) {
        this.executor = new BooleanQueryExecutor(invertedIndex);
    }

    public Optional<Set<Integer>> processQuery(String query) {
        QueryParser.QueryType type = QueryParser.parseQueryType(query);
        String[] terms = QueryParser.extractTerms(query);

        return switch (type) {
            case SIMPLE -> executor.search(terms[0]);
            case AND -> executor.andSearch(terms[0], terms[1]);
            case OR -> executor.orSearch(terms[0], terms[1]);
            case NOT -> {
                if (terms.length >= 2) {
                    Optional<Set<Integer>> allDocs = executor.search(terms[0]);
                    if (allDocs.isPresent()) {
                        yield executor.notSearch(terms[1], allDocs.get());
                    }
                }
                yield Optional.empty();
            }
        };
    }
}
