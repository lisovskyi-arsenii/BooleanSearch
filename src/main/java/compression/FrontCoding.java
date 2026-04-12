package compression;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

//
// алгоритм - сортую словник і зберігаю не слова, а довжину спільного префіксу з попереднім словом і залишок (суфікс)
//
@Slf4j
public final class FrontCoding {
    private FrontCoding() {
        throw new UnsupportedOperationException("Utility class");
    }

    public record CompressedDictionary(List<String> blocks, int originalSize, int compressedSize) {
        public double getCompressionRatio() {
            if (originalSize <= 0) return 0.0;
            return (double) compressedSize / originalSize * 100;
        }
    }

    public static CompressedDictionary compress(List<String> terms, int blockSize) {
        List<String> sorted = new ArrayList<>(terms);
        Collections.sort(sorted);

        List<String> blocks = new ArrayList<>();
        int originalSize   = 0;
        int compressedSize = 0;

        for (int i = 0; i < sorted.size(); i += blockSize) {
            int end = Math.min(i + blockSize, sorted.size());
            List<String> blockTerms = sorted.subList(i, end);

            StringBuilder compressed = new StringBuilder();
            String previous = "";

            for (String term : blockTerms) {
                originalSize += term.length() + 1;

                // спільний префікс для попереднього та поточного слів
                int prefixLength = commonPrefixLength(previous, term);
                String suffix    = term.substring(prefixLength);

                // формат запису: <довжина_префіксу>:<суфікс>|
                compressed.append(prefixLength).append(':')
                        .append(suffix).append('|');

                compressedSize += String.valueOf(prefixLength).length() + 1 // розмір :
                        + suffix.length() + 1; // розмір |

                previous = term;
            }

            blocks.add(compressed.toString());
        }

        log.info("Front Coding: {} terms → {} blocks, ratio: {}%",
                sorted.size(),
                blocks.size(),
                String.format("%.2f", (double) compressedSize / originalSize * 100));

        return new CompressedDictionary(blocks, originalSize, compressedSize);
    }

    public static byte[] compressToBytes(List<String> terms, int blockSize) throws IOException {
        CompressedDictionary dictionary = compress(terms, blockSize);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        dos.writeInt(dictionary.blocks.size());
        for (String block : dictionary.blocks()) {
            byte[] blockBytes = block.getBytes(StandardCharsets.UTF_8);
            dos.writeInt(blockBytes.length);
            dos.write(blockBytes);
        }
        dos.flush();
        return baos.toByteArray();
    }

    public static List<String> decompress(CompressedDictionary dictionary) {
        List<String> terms = new ArrayList<>();

        for (String block : dictionary.blocks()) {
            String previous = "";
            // розділяю на блоки
            String[] entries = block.split("\\|");

            for (String entry : entries) {
                if (entry.isBlank()) continue;

                // розділюю на дві частини: довж_префіксу,суфікс
                String[] parts = entry.split(":", 2);
                if (parts.length < 2) continue;

                try {
                    int prefixLength = Integer.parseInt(parts[0]);
                    String suffix = parts[1];

                    if (prefixLength > previous.length()) {
                        log.warn("Invalid prefix length");
                        continue;
                    }

                    String term = previous.substring(0, prefixLength) + suffix;
                    terms.add(term);
                    previous = term;
                } catch (NumberFormatException e) {
                    log.warn("Skipping invalid entry: {}", entry, e);
                }
            }
        }
        return terms;
    }

    public static List<String> decompressFromBytes(byte[] compressed) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
        DataInputStream dis = new DataInputStream(bais);

        int blockCount = dis.readInt();
        List<String> blockList = new ArrayList<>(blockCount);

        for (int i = 0; i < blockCount; i++) {
            int blockLength = dis.readInt();
            byte[] blockBytes = new byte[blockLength];
            dis.readFully(blockBytes);
            blockList.add(new String(blockBytes, StandardCharsets.UTF_8));
        }

        CompressedDictionary dictionary = new CompressedDictionary(blockList, -1, compressed.length);
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
