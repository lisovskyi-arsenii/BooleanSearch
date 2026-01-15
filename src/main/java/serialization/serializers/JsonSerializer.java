package serialization.serializers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import serialization.IndexSerializer;

import java.io.*;
import java.util.Map;
import java.util.Set;

public class JsonSerializer implements IndexSerializer {
    private static final String FORMAT = "Json";

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonString = objectMapper.writeValueAsString(index);
        try (
                OutputStream file = new FileOutputStream(filepath);
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(file));
        ) {
            writer.write(jsonString);
        }
    }

    @Override
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        ObjectMapper objectMapper = new ObjectMapper();
        TypeReference<Map<String, Set<Integer>>> typeRef = new TypeReference<>(){};

        return objectMapper.readValue(
                new File(filepath),
                typeRef
        );
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
