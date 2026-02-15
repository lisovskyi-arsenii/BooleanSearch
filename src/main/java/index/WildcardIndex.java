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
}
