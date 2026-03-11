package index;

import enums.ZoneWeight;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import tokenization.Tokenizer;

import java.util.*;

@Slf4j
@Getter
public class ZoneIndex {
    private final Map<ZoneWeight, InvertedIndex> zoneIndex = new EnumMap<>(ZoneWeight.class);

    public ZoneIndex() {
        for (var zone : ZoneWeight.values()) {
            zoneIndex.put(zone, new InvertedIndex());
        }
    }

    public void addDocument(Map<ZoneWeight, String> zones, int docId) {
        zones.forEach((zone, text) -> {
            List<String> tokens = Tokenizer.tokenize(text);
            InvertedIndex invertedIndex = zoneIndex.get(zone);
            tokens.forEach(token -> invertedIndex.addTerm(token, docId));
        });
    }

    public double score(String term, int docId) {
        double score = 0.0;
        for (var entry : zoneIndex.entrySet()) {
            var zone = entry.getKey();
            var index = entry.getValue();

            var documentsOpt = index.getDocuments(term);
            if (documentsOpt.isEmpty()) {
                continue;
            }

            var documents = documentsOpt.get();
            if (documents.contains(docId)) {
                score += zone.getWeight().doubleValue();
            }
        }

        return score;
    }

    public Set<Integer> search(String term) {
        Set<Integer> result = new HashSet<>();
        zoneIndex.values().forEach(index -> {
            index.getDocuments(term).ifPresent(result::addAll);
        });
        return result;
    }

    public void clear() {
        zoneIndex.values().forEach(InvertedIndex::clear);
    }
}
