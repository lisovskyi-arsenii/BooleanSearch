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
        // docId -> { term, tfidf }
        Map<Integer, Map<String, Double>> result = new HashMap<>();

        var terms = positionalIndex.getAllTerms();
        if (terms.isEmpty()) return result;

        for (var term : terms) {
            var documentsOpt = positionalIndex.getDocuments(term);
            if (documentsOpt.isEmpty()) continue;

            // скільки документів містять цей термін
            int docFreq = documentsOpt.get().size();
            // рідкісні терміни отримують більше вагу
            double idf = Math.log(1.0 + (double) totalDocs / docFreq);

            for (int docId : documentsOpt.get()) {
                var positionsInDocOpt = positionalIndex.getPositionsInDocument(term, docId);
                if (positionsInDocOpt.isEmpty()) continue;

                // скільки разів термін зустрічається в документі
                int tf = positionsInDocOpt.get().size();
                // docFreq * inverse doc freq
                double tfidf = tf * idf;

                if (tfidf > 0) {
                    result.computeIfAbsent(docId, _ -> new HashMap<>())
                            .put(term, tfidf);
                }
            }
        }

        return result;
    }
}
