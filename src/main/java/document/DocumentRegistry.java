package document;

import java.util.*;

public class DocumentRegistry {
    private final Map<String, Integer> filenameToId = new HashMap<>(); // filename -> document id
    private final Map<Integer, String> idToFilename = new HashMap<>(); // document id -> filename
    private final Map<String, Long> filenameToSize = new  HashMap<>(); // filename -> size
    private int nextDocID = 1;

    public int registerDocument(String filename, long size) {
        if (filenameToId.containsKey(filename)) {
            return filenameToId.get(filename);
        }

        int docID = nextDocID++;
        filenameToId.put(filename, docID);
        idToFilename.put(docID, filename);
        filenameToSize.put(filename, size);

        return docID;
    }

    public Optional<String> getFilename(int docID) {
        return Optional.ofNullable(idToFilename.get(docID));
    }

    public Optional<Long> getSize(String filename) {
        return Optional.ofNullable(filenameToSize.get(filename));
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

    public void clear() {
        filenameToId.clear();
        idToFilename.clear();
        filenameToSize.clear();
        nextDocID = 1;
    }
}
