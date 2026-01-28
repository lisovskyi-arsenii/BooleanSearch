package document;

import serialization.data.RegistryData;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class DocumentRegistry {
    private final Map<String, Integer> filenameToId = new ConcurrentHashMap<>(); // filename -> document id
    private final Map<Integer, String> idToFilename = new ConcurrentHashMap<>(); // document id -> filename
    private final Map<String, Long> filenameToSize = new ConcurrentHashMap<>(); // filename -> size
    private final AtomicInteger nextDocID = new AtomicInteger(1);

    public int registerDocument(String filename, long size) {
        return filenameToId.computeIfAbsent(filename, fn -> {
            int docID = nextDocID.getAndIncrement();
            idToFilename.put(docID, fn);
            filenameToSize.put(fn, size);
            return docID;
        });
    }

    public List<String> getDocumentNames(Set<Integer> docIDs) {
        return docIDs.stream()
                .map(idToFilename::get)
                .filter(Objects::nonNull)
                .sorted()
                .toList();
    }

    public int documentCount() {
        return filenameToId.size();
    }

    public Map<String, Integer> getDocumentMetadata() {
        return Collections.unmodifiableMap(filenameToId);
    }

    public RegistryData exportData() {
        return new RegistryData(
                new HashMap<>(filenameToId),
                new HashMap<>(idToFilename),
                new HashMap<>(filenameToSize),
                nextDocID.get()
        );
    }

    public void loadData(RegistryData registryData) {
        clear();

        filenameToId.putAll(registryData.filenameToId());
        idToFilename.putAll(registryData.idToFilename());
        filenameToSize.putAll(registryData.filenameToSize());
        nextDocID.set(registryData.nextDocID());
    }

    public void clear() {
        filenameToId.clear();
        idToFilename.clear();
        filenameToSize.clear();
        nextDocID.set(1);
    }
}
