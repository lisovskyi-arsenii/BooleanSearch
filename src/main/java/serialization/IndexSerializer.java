package serialization;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface IndexSerializer {
    void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException;
    Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException;
    String getFormat();
}
