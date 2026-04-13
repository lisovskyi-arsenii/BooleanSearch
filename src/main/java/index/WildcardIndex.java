package index;

import java.io.Serializable;
import java.util.List;

public interface WildcardIndex extends Serializable {
    void buildFromDictionary(Dictionary dictionary);
    void addTerm(String term);
    List<String> search(String wildcardQuery);
    boolean contains(String term);
    int size();
    void clear();

    default String nextPrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return String.valueOf(Character.MAX_VALUE);
        }

        char last = prefix.charAt(prefix.length() - 1);
        if (last == Character.MAX_VALUE) {
            return null;
        }
        return prefix.substring(0, prefix.length() - 1) + (char)(last + 1);
    }
}
