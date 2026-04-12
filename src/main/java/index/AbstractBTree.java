package index;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public abstract class AbstractBTree implements WildcardIndex {
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

        long starCount = wildcard.chars().filter(c -> c == '*').count();
        if (starCount > 1) {
            throw new IllegalArgumentException(
                    "BTree/ReverseBTree support only one '*'. Use ThreeGramIndex for '" + wildcard + "'"
            );
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

        char last = prefix.charAt(prefix.length() - 1);
        if (last == Character.MAX_VALUE) {
            return null;
        }
        return prefix.substring(0, prefix.length() - 1) + (char)(last + 1);
    }

    protected String reverseString(String str) {
        return new StringBuilder(str).reverse().toString();
    }
}
