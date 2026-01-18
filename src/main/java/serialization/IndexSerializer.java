package serialization;

import core.IndexData;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public interface IndexSerializer {
    void serialize(IndexData indexData, String filepath) throws IOException;
    IndexData deserialize(String filepath) throws IOException, ClassNotFoundException;
    String getFormat();
}
