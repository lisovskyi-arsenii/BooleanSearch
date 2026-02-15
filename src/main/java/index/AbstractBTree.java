package index;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractBTree implements WildcardIndex {
    @Override
    public abstract void buildFromDictionary(Dictionary dictionary);
    @Override
    public abstract void addTerm(String term) throws IllegalArgumentException;
    @Override
    public abstract boolean contains(String term);
    @Override
    public abstract int size();
    @Override
    public abstract void clear();

    @Override
    public List<String> search(String wildcard) throws IllegalArgumentException {
        if (wildcard == null || wildcard.isBlank()) {
            return new ArrayList<>();
        }

        if (!wildcard.contains("*")) {
            return contains(wildcard)
                    ? List.of(wildcard)
                    : Collections.emptyList();
        }

        int starIndex = wildcard.indexOf("*");

        validateWildcardStarIndex(starIndex, wildcard);

        return searchWildcard(wildcard, starIndex);
    }

    protected abstract void validateWildcardStarIndex(int starIndex, String wildcard) throws IllegalArgumentException;
    protected abstract List<String> searchWildcard(String wildcard, int starIndex);

    protected String getNextPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return String.valueOf(Character.MAX_VALUE);
        }

        char[] currentPrefix = prefix.toCharArray();
        currentPrefix[currentPrefix.length - 1]++;
        return new String(currentPrefix);
    }

    protected String reverseString(String str) {
        return new StringBuilder(str).reverse().toString();
    }
}
