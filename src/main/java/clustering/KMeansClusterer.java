package clustering;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@RequiredArgsConstructor
public class KMeansClusterer {
    private final int k;             // кількість кластерів яку ми хочемо отримати
    private final int maxIterations;

    public ClusterResult cluster(Map<Integer, Map<String, Double>> vectors) {
        if (vectors.isEmpty()) return new ClusterResult(Map.of());

        List<Integer> docIds = new ArrayList<>(vectors.keySet());

        // обрати k випадкових документів як початкові центри кластерів
        Map<Integer, Map<String, Double>> centroids = initializeCentroids(docIds, vectors);
        // docId -> clusterId (до якого кластера належить документ)
        Map<Integer, Integer> assignments = new HashMap<>();

        for (int iter = 0; iter < maxIterations; iter++) {
            Map<Integer, Integer> newAssignments = assignClusters(vectors, centroids);

            // якщо кластер не змінився - кінець
            if (newAssignments.equals(assignments)) {
                log.info("K-Means converged at iteration {}", iter);
                break;
            }

            assignments = newAssignments;
            centroids = recalculateCentroids(assignments, vectors, centroids);
        }

        Map<Integer, List<Integer>> clusters = new HashMap<>();
        for (var entry : assignments.entrySet()) {
            clusters.computeIfAbsent(entry.getValue(), _ -> new ArrayList<>())
                    .add(entry.getKey());
        }

        if (clusters.size() < k) {
            log.warn("K-Means produced {} clusters instead of requested {} " +
                    "(collection too small or k too large)", clusters.size(), k);
        }

        log.info("K-Means completed: {} clusters, {} documents", clusters.size(), vectors.size());
        return new ClusterResult(clusters);
    }

    private Map<Integer, Map<String, Double>> initializeCentroids(
            List<Integer> docIds,
            Map<Integer, Map<String, Double>> vectors) {

        Map<Integer, Map<String, Double>> centroids = new HashMap<>();
        List<Integer> shuffled = new ArrayList<>(docIds);
        // рандомно генеруємо перші центроїди
        Collections.shuffle(shuffled, ThreadLocalRandom.current());

        int count = Math.min(k, shuffled.size());
        for (int i = 0; i < count; i++) {
            centroids.put(i, new HashMap<>(vectors.get(shuffled.get(i))));
        }
        return centroids;
    }

    // призначає документ до найближчого центроїда, близькість вимірюється через cosine similarity
    private Map<Integer, Integer> assignClusters(
            Map<Integer, Map<String, Double>> vectors,
            Map<Integer, Map<String, Double>> centroids) {

        Map<Integer, Integer> assignments = new HashMap<>();

        for (var docEntry : vectors.entrySet()) {
            int docId = docEntry.getKey();
            Map<String, Double> docVector = docEntry.getValue();

            int bestCluster = 0;
            double bestSimilarity = -1.0;

            for (var centroidEntry : centroids.entrySet()) {
                double similarity = CosineSimilarity.compute(docVector, centroidEntry.getValue());
                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestCluster = centroidEntry.getKey();
                }
            }

            assignments.put(docId, bestCluster);
        }

        return assignments;
    }

    private Map<Integer, Map<String, Double>> recalculateCentroids(
            Map<Integer, Integer> assignments,
            Map<Integer, Map<String, Double>> vectors,
            Map<Integer, Map<String, Double>> oldCentroids) {

        // clusterId → (term → список tfidf значень)
        Map<Integer, Map<String, List<Double>>> clusterTerms = new HashMap<>();

        for (var entry : assignments.entrySet()) {
            int docId = entry.getKey();
            int clusterId = entry.getValue();
            Map<String, Double> docVector = vectors.get(docId);

            Map<String, List<Double>> termAccumulator =
                    clusterTerms.computeIfAbsent(clusterId, _ -> new HashMap<>());

            for (var termEntry : docVector.entrySet()) {
                termAccumulator.computeIfAbsent(termEntry.getKey(), _ -> new ArrayList<>())
                        .add(termEntry.getValue());
            }
        }

        Map<Integer, Map<String, Double>> newCentroids = new HashMap<>();

        for (var clusterEntry : clusterTerms.entrySet()) {
            int clusterId = clusterEntry.getKey();
            Map<String, Double> centroid = new HashMap<>();

            for (var termEntry : clusterEntry.getValue().entrySet()) {
                List<Double> values = termEntry.getValue();
                double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                centroid.put(termEntry.getKey(), avg);
            }

            newCentroids.put(clusterId, centroid);
        }

        for (int i = 0; i < k; i++) {
            newCentroids.putIfAbsent(i, oldCentroids.get(i));
        }

        return newCentroids;
    }
}
