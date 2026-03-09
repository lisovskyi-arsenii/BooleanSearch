package index;

import enums.CompressionMethod;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Setter
@Getter
public class CompressedIndex {
    // dictionary of terms
    private byte[] compressedDictionary;

    // term_index -> compressed_bytes
    private Map<Integer, byte[]> compressedPostings;

    // metadata
    private int originalDictionarySize;
    private int originalPostingsSize;
    private CompressionMethod postingCompressionMethod; // VBC or G

    public CompressedIndex() {
        this.compressedPostings = new HashMap<>();
    }

    public int getCompressedDictionarySize() {
        return compressedDictionary != null ? compressedDictionary.length : 0;
    }

    public int getCompressedPostingsSize() {
        return compressedPostings.values().stream()
                .mapToInt(bytes -> bytes.length)
                .sum();
    }

    public int getTotalCompressedSize() {
        return getCompressedDictionarySize() + getCompressedPostingsSize();
    }

    public int getTotalOriginalSize() {
        return getOriginalDictionarySize() + getOriginalPostingsSize();
    }

    public double getCompressionRatio() {
        if (getTotalOriginalSize() == 0) return 0.0;
        return (double) getTotalCompressedSize() / getTotalOriginalSize() * 100;
    }

}
