package serialization.data;

import java.io.Serializable;

public record IndexMetadata(
        long uniqueTerms,
        long documentsCount,
        long totalBytesProcessed,
        long finalIndexSize,
        int blocksCreated,
        long indexingTimeMs,
        String indexPath
) implements Serializable {

    public double avgTermsPerDoc() {
        return documentsCount > 0 ? (double) uniqueTerms / documentsCount : 0;
    }

    public double compressionRatio() {
        return totalBytesProcessed > 0
                ? (double) finalIndexSize / totalBytesProcessed * 100
                : 0;
    }
}
