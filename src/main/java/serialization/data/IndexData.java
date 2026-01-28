package serialization.data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;
import java.util.Set;

public record IndexData(Map<String, Set<Integer>> index,
                        RegistryData registryData) implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}
