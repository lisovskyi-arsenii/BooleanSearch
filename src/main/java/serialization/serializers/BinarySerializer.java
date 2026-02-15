package serialization.serializers;

import serialization.data.IndexData;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BinarySerializer implements IndexSerializer {
    private static final String FORMAT = "BINARY";
    private static final int BUFFER_SIZE = 65536;

    @Override
    public void serialize(IndexData allIndexesData, String filepath) throws IOException {
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new GZIPOutputStream(
                        new BufferedOutputStream(
                                new FileOutputStream(filepath), BUFFER_SIZE)))) {

            oos.writeObject(allIndexesData);
        }
    }

    @Override
    public IndexData deserialize(String filepath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new GZIPInputStream(
                        new BufferedInputStream(
                                new FileInputStream(filepath), BUFFER_SIZE)))) {

            return (IndexData) ois.readObject();
        }
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
