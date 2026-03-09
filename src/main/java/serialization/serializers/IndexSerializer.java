package serialization.serializers;

import serialization.data.IndexData;

import java.io.IOException;

public interface IndexSerializer {
    void serialize(IndexData allIndexesData, String filepath) throws IOException;

    IndexData deserialize(String filepath) throws IOException, ClassNotFoundException;

    String getFormat();
}
