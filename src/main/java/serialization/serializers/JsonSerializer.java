package serialization.serializers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import serialization.data.IndexData;
import serialization.data.RegistryData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class JsonSerializer implements IndexSerializer {
    private static final String FORMAT = "JSON";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};

    @Override
    public void serialize(IndexData indexData, String filepath) throws IOException {
        Path path = Path.of(filepath);

        Map<String, Object> data = new HashMap<>();
        data.put("index", indexData.positionalIndex());

        Map<String, Object> registryMap = new LinkedHashMap<>();
        registryMap.put("filenameToID", indexData.registryData().filenameToId());
        registryMap.put("idToFilename", indexData.registryData().idToFilename());
        registryMap.put("filenameToSize", indexData.registryData().filenameToSize());
        registryMap.put("nextDocID", indexData.registryData().nextDocID());

        data.put("registry", registryMap);

        String jsonString = MAPPER.writeValueAsString(data);
        Files.writeString(path, jsonString, StandardCharsets.UTF_8);
    }

    @Override
    @SuppressWarnings("unchecked")
    public IndexData deserialize(String filepath) throws IOException {
        Path path = Path.of(filepath);
        String jsonString = Files.readString(path, StandardCharsets.UTF_8);

        var data = MAPPER.readValue(jsonString, typeRef);

        var positionalIndexRaw = (Map<String, Object>) data.get("index");
        Map<String, Map<Integer, List<Integer>>> positionalIndex = new HashMap<>();

        if (positionalIndexRaw != null) {
            for (var entry : positionalIndexRaw.entrySet()) {
                String term = entry.getKey();
                var docPositionsRaw = (Map<String, Object>) entry.getValue();

                Map<Integer, List<Integer>> docPositions = new HashMap<>();

                for (var docEntry : docPositionsRaw.entrySet()) {
                    int docId = Integer.parseInt(docEntry.getKey());
                    List<Integer> positions = (List<Integer>) docEntry.getValue();
                    docPositions.put(docId, new ArrayList<>(positions));
                }

                positionalIndex.put(term, docPositions);
            }
        }

        var registryMap = (Map<String, Object>) data.getOrDefault("registry", new ConcurrentHashMap<>());
        var filenameToId = (Map<String, Integer>) registryMap.getOrDefault("filenameToID", new ConcurrentHashMap<>());
        var idToFilenameRaw = (Map<String, Object>) registryMap.getOrDefault("idToFilename", new ConcurrentHashMap<>());
        Map<Integer, String> idToFilename = new ConcurrentHashMap<>();
        for (var entry : idToFilenameRaw.entrySet()) {
            idToFilename.put(Integer.parseInt(entry.getKey()), String.valueOf(entry.getValue()));
        }

        var filenameToSizeRaw = (Map<String, Object>) registryMap.getOrDefault("filenameToSize", new ConcurrentHashMap<>());
        Map<String, Long> filenameToSize = new ConcurrentHashMap<>();
        for (var entry : filenameToSizeRaw.entrySet()) {
            filenameToSize.put(entry.getKey(), ((Number) entry.getValue()).longValue());
        }

        int nextDocID = ((Number) registryMap.getOrDefault("nextDocID", 1)).intValue();

        RegistryData registryData = new RegistryData(
                filenameToId,
                idToFilename,
                filenameToSize,
                nextDocID
        );

        return new IndexData(positionalIndex, registryData);
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }
}
