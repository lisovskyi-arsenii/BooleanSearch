package serialization.serializers;

import core.IndexData;

import java.io.IOException;

public interface IndexSerializer {
    void serialize(IndexData indexData, String filepath) throws IOException;
    IndexData deserialize(String filepath) throws IOException, ClassNotFoundException;
    String getFormat();
}
