package clustering;

import index.PositionalIndex;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TfIdfVectorizer {
    private final PositionalIndex positionalIndex;
    private final int totalDocs;

    public Map<Integer, Map<String, Double>> buildVectors() {
        Map<Integer, Map<String, Double>> result = new HashMap<>();

        var terms = positionalIndex.getAllTerms();
        if (terms.isEmpty()) return result;

        for (var term : terms) {
            var documentsOpt = positionalIndex.getDocuments(term);
            if (documentsOpt.isEmpty()) continue;

            int docFreq = documentsOpt.get().size();
            double idf = Math.log((double) totalDocs / (1 + docFreq));

            for (int docId : documentsOpt.get()) {
                var positionsInDocOpt = positionalIndex.getPositionsInDocument(term, docId);
                if (positionsInDocOpt.isEmpty()) continue;

                int tf = positionsInDocOpt.get().size();
                double tfidf = tf * idf;

                result.computeIfAbsent(docId, _ -> new HashMap<>())
                        .put(term, tfidf);
            }
        }

        return result;
    }
}
