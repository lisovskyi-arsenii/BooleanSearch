package clustering;

import lombok.Getter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ClusterResult {
    // clusterId -> список docId
    @Getter
    private final Map<Integer, List<Integer>> clusters;
    // docId -> кластер (для швидкого пошуку)
    private final Map<Integer, Integer> docToCluster;

    public ClusterResult(Map<Integer, List<Integer>> clusters) {
        Objects.requireNonNull(clusters, "Clusters must not be null");
        this.clusters = clusters;

        this.docToCluster = new HashMap<>();
        for (var entry : clusters.entrySet()) {
            int clusterId = entry.getKey();
            for (int docId : entry.getValue()) {
                docToCluster.put(docId, clusterId);
            }
        }
    }

    public int getClusterForDoc(int docId) {
        return docToCluster.getOrDefault(docId, -1);
    }

    public int clusterCount() {
        return clusters.size();
    }
}
