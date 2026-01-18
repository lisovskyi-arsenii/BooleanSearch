package serialization.serializers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import serialization.IndexSerializer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class JsonSerializer implements IndexSerializer {
    private static final String FORMAT = "JSON";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final TypeReference<Map<String, Set<Integer>>> INDEX_TYPE =
            new TypeReference<>() {};

    @Override
    public void serialize(Map<String, Set<Integer>> index, String filepath) throws IOException {
        Path path = Path.of(filepath);

        String jsonString = MAPPER.writeValueAsString(index);
        Files.writeString(path, jsonString, StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, Set<Integer>> deserialize(String filepath) throws IOException, ClassNotFoundException {
        Path path = Path.of(filepath);

        String jsonString = Files.readString(path, StandardCharsets.UTF_8);
        var result = MAPPER.readValue(jsonString, INDEX_TYPE);
        return result != null ? result : new HashMap<>();
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
