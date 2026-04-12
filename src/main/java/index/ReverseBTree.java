package index;

import lombok.Getter;

import java.util.*;

@Getter
public class ReverseBTree extends AbstractBTree {
    // red-black tree map: reverse term -> term
    private final TreeMap<String, String> reverseMap = new TreeMap<>();

    // для швидшого пошуку додав цю структуру, щоб за O(1) знаходити чи є такий термін в моєму дереві
    private final Set<String> terms = new HashSet<>();

    @Override
    public void buildFromDictionary(Dictionary dictionary) {
        reverseMap.clear();
        terms.clear();

        for (String term : dictionary.getAllTerms()) {
            addTerm(term);
        }
    }

    @Override
    public void addTerm(String term) throws IllegalArgumentException {
        if (term == null || term.isBlank()) {
            throw new IllegalArgumentException("Term is null or blank");
        }

        String reversed = reverseString(term);
        reverseMap.put(reversed, term);
        terms.add(term);
    }

    @Override
    protected void validateWildcardStarIndex(int starIndex, String wildcard) throws IllegalArgumentException {
        if (starIndex != 0) {
            throw new IllegalArgumentException(
                    "ReverseBTree supports only leading wildcards like '*ing'. " +
                    "Use BTree for 'mon*' or KGramIndex for 'm*n'"
            );
        }
    }

    @Override
    protected List<String> searchWildcard(String wildcard, int starIndex) {
        String suffix = wildcard.substring(1);
        return findBySuffix(suffix);
    }

    @Override
    public boolean contains(String term) {
        return terms.contains(term);
    }

    @Override
    public int size() {
        return reverseMap.size();
    }

    @Override
    public void clear() {
        reverseMap.clear();
        terms.clear();
    }

    public List<String> findBySuffix(String suffix) {
        if (suffix == null || suffix.isBlank()) {
            return new ArrayList<>(new TreeSet<>(terms));
        }

        String reversedSuffix = reverseString(suffix);
        String upperBound = getNextPrefix(reversedSuffix);

        if (upperBound == null) {
            return reverseMap.tailMap(reversedSuffix)
                    .values().stream()
                    .sorted()
                    .toList();
        }

        return reverseMap.subMap(reversedSuffix, upperBound)
                .values().stream()
                .sorted()
                .toList();
    }
}
