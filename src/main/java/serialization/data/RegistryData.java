package serialization.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

public record RegistryData(Map<String, Integer> filenameToId, Map<Integer, String> idToFilename,
                           Map<String, Long> filenameToSize, int nextDocID) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
