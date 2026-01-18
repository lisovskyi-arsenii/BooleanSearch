package core;

import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public class IndexData implements Serializable {
    private static final long serialVersionUID = 1L;
    private final Map<String, Set<Integer>> index;
    private final Map<Integer, String> documentNames;
    private final Map<Integer, Long> documentSizes;

    public IndexData(
            Map<String, Set<Integer>> index,
            Map<Integer, String> documentNames,
            Map<Integer, Long> documentSizes
    ) {
        this.index = index;
        this.documentNames = documentNames;
        this.documentSizes = documentSizes;
    }

    public Map<String, Set<Integer>> getIndex() {
        return index;
    }

    public Map<Integer, String> getDocumentNames() {
        return documentNames;
    }

    public Map<Integer, Long> getDocumentSizes() {
        return documentSizes;
    }
}
