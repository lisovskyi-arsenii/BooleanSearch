package document;

import java.util.*;

public class DocumentRegistry {
    private final Map<String, Integer> docMetadata = new HashMap<>(); // filename -> document id
    private final Map<Integer, DocumentMetadata> idToFilename = new HashMap<>(); // document id -> filename
    private int nextDocID = 1;

    public int registerDocument(String filename, long size) {
        return 0;
    }

    public Optional<DocumentMetadata> getMetadata(int docID) {
        return Optional.ofNullable(idToFilename.get(docID));
    }

    public List<String> getDocumentNames(Set<Integer> docIDs) {
        return null;
    }

    public int documentCount() {
        return docMetadata.size();
    }
}
