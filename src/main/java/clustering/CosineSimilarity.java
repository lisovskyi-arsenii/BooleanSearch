package clustering;

import java.util.Map;

public final class CosineSimilarity {
    private CosineSimilarity() {
        throw new UnsupportedOperationException("CosineSimilarity is utility class - cannot be instantiated");
    }

    public static double compute(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> smaller = a.size() < b.size() ? a : b;
        Map<String, Double> larger = a.size() < b.size() ? b : a;

        double dotProduct = 0.0;
        for (var entry : smaller.entrySet()) {
            var bValue = larger.get(entry.getKey());
            if (bValue != null) {
                dotProduct += entry.getValue() * bValue;
            }
        }

        var normA = Math.sqrt(a.values().stream().mapToDouble(x -> x * x).sum());
        var normB = Math.sqrt(b.values().stream().mapToDouble(x -> x * x).sum());

        if (normA == 0 || normB == 0) return 0.0;
        return (dotProduct) / (normA * normB);
    }
}
