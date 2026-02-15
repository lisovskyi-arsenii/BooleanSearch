package serialization.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

public record IndexData(
        Map<String, Map<Integer, List<Integer>>> positionalIndex,
        RegistryData registryData
) implements Serializable {
    @Serial
    private static final long serialVersionUID = 10L;
}
