package index;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Getter
public class BTree extends AbstractBTree {
    private final TreeSet<String> terms = new TreeSet<>();

    @Override
    public void buildFromDictionary(Dictionary dictionary) {
        terms.clear();
        terms.addAll(dictionary.getAllTerms());
    }

    @Override
    public void addTerm(String term) throws IllegalArgumentException {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term is null or blank");
        }

        terms.add(term);
    }

    @Override
    protected void validateWildcardStarIndex(int starIndex, String wildcard) throws IllegalArgumentException {
        if (starIndex != wildcard.length() - 1) {
            throw new IllegalArgumentException(
                    "BTree supports only trailing wildcards like 'mon*'. " +
                    "Use ReverseBTree for '*ing' or KGramIndex for 'm*n'"
            );
        }
    }

    @Override
    protected List<String> searchWildcard(String wildcard, int starIndex) {
        String prefix = wildcard.substring(0, starIndex);
        return findByPrefix(prefix);
    }

    @Override
    public boolean contains(String term) {
        return terms.contains(term);
    }

    @Override
    public int size() {
        return terms.size();
    }

    @Override
    public void clear() {
        terms.clear();
    }

    public List<String> findByPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return new ArrayList<>(terms);
        }

        String upperBound = getNextPrefix(prefix);
        return new ArrayList<>(terms.subSet(prefix, upperBound));
    }
}
