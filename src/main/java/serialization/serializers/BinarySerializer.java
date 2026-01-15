package serialization.serializers;

import serialization.IndexSerializer;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class BinarySerializer implements IndexSerializer {
    private static final String FORMAT = "Binary";

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        try (
                OutputStream file = new FileOutputStream(filepath);
                OutputStream buffer = new BufferedOutputStream(file);
                ObjectOutput output = new ObjectOutputStream(buffer);
        ) {
            output.writeObject(index);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        try (
                InputStream file = new FileInputStream(filepath);
                InputStream buffer = new BufferedInputStream(file);
                ObjectInput input = new ObjectInputStream(buffer);
        ) {
            return (HashMap<String, Set<Integer>>) input.readObject();
        }
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
