package compression;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
public class FrontCoding {
    public record CompressedDictionary(List<String> blocks, int originalSize, int compressedSize) {
        public double getCompressionRatio() {
            return (double) compressedSize / originalSize * 100;
        }
    }

    public static CompressedDictionary compress(List<String> terms, int blockSize) {
        Collections.sort(terms);

        List<String> blocks = new ArrayList<>();
        int originalSize = 0;
        int compressedSize = 0;


        for (int i = 0; i < terms.size(); i += blockSize) {
            int end = Math.min(i + blockSize, terms.size());
            List<String> blockTerms = terms.subList(i, end);

            StringBuilder compressed = new StringBuilder();
            String previous = "";

            for (String term : blockTerms) {
                originalSize += term.length() + 1;

                int prefixLength = commonPrefixLength(previous, term);
                String suffix = term.substring(prefixLength);

                compressed.append(prefixLength).append(':')
                        .append(suffix).append('|');

                compressedSize += String.valueOf(prefixLength).length() + 1 +
                        suffix.length() + 1;

                previous = term;
            }

            blocks.add(compressed.toString());
        }

        log.info("Front Coding: {} terms → {} blocks, ratio: {}%",
                terms.size(),
                blocks.size(),
                String.format("%.2f", (double) compressedSize / originalSize * 100));

        return new CompressedDictionary(blocks, originalSize, compressedSize);
    }

    public static byte[] compressToBytes(List<String> terms, int blockSize) {
        CompressedDictionary dictionary = compress(terms, blockSize);

        StringBuilder combined = new StringBuilder();
        for (String block : dictionary.blocks()) {
            combined.append(block).append("||");
        }

        return combined.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static List<String> decompress(CompressedDictionary dictionary) {
        List<String> terms = new ArrayList<>();

        for (String block : dictionary.blocks()) {
            String previous = "";

            String[] entries = block.split("\\|");

            for (String entry : entries) {
                if (entry.isBlank()) continue;

                String[] parts = entry.split(":", 2);
                if (parts.length < 2) continue;

                int prefixLength = Integer.parseInt(parts[0]);
                String suffix = parts[1];

                String term = previous.substring(0, prefixLength) + suffix;
                terms.add(term);
                previous = term;
            }
        }

        return terms;
    }

    public static List<String> decompressFromBytes(byte[] compressed) {
        String data = new String(compressed, StandardCharsets.UTF_8);
        String[] blocks = data.split("\\|\\|");

        List<String> blockList = new ArrayList<>();
        for (String block : blocks) {
            if (!block.isBlank()) {
                blockList.add(block);
            }
        }

        CompressedDictionary dictionary = new CompressedDictionary(blockList, compressed.length, compressed.length);
        return decompress(dictionary);
    }

    public static int commonPrefixLength(String s1, String s2) {
        int minLength = Math.min(s1.length(), s2.length());
        for (int i = 0; i < minLength; i++) {
            if (s1.charAt(i) != s2.charAt(i)) {
                return i;
            }
        }
        return minLength;
    }
}
