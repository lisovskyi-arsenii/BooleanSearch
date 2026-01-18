package serialization.serializers;

import serialization.IndexSerializer;

import java.io.*;
import java.util.Map;
import java.util.Set;

public class BinarySerializer implements IndexSerializer {
    private static final String FORMAT = "Binary";

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        try (
            ObjectOutputStream oos = new ObjectOutputStream(
                    new BufferedOutputStream(new FileOutputStream(filepath + ".bin"))
            )
        ) {
            oos.writeObject(index);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(new FileInputStream(filepath + ".bin")))) {
            return (Map<String, Set<Integer>>) ois.readObject();
        }
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
