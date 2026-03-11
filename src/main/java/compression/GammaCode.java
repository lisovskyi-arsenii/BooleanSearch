package compression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class GammaCode {
    private GammaCode() {
        throw new UnsupportedOperationException("GammaCode class is utility class - cannot create instance of it");
    }

    public static String encodeNumber(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Number must be positive");
        }

        int m = (int) (Math.log(n) / Math.log(2));

        String unary = "0".repeat(m);
        String binary = Integer.toBinaryString(n);

        return unary + binary;
    }

    public static byte[] encode(List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return new byte[0];
        }

        BitWriter writer = new BitWriter();

        for (int num : numbers) {
            String encoded = encodeNumber(num);

            for (char c : encoded.toCharArray()) {
                writer.writeBit(c == '1' ? 1 : 0);
            }
        }

        return writer.toByteArray();
    }

    public static byte[] encodeWithGaps(List<Integer> sortedNumbers) {
        if (sortedNumbers.isEmpty()) return new byte[0];

        List<Integer> gaps = new ArrayList<>();
        int previous = 0;

        for (int num : sortedNumbers) {
            gaps.add((num - previous) + 1);
            previous = num;
        }

        return encode(gaps);
    }

    public static List<Integer> decode(byte[] encoded) {
        if (encoded.length == 0) {
            return new ArrayList<>();
        }

        List<Integer> numbers = new ArrayList<>();
        BitReader reader = new BitReader(encoded);

        while (reader.hasNext()) {
            int m = 0;
            int bit = reader.readBit();
            if (bit == -1) break;

            while (bit == 0) {
                m++;
                bit = reader.readBit();
                if (bit == -1) return numbers;
            }

            int value = 1;
            for (int i = 0; i < m; i++) {
                bit = reader.readBit();
                if (bit == -1) return numbers;
                value = (value << 1) | bit;
            }

            numbers.add(value);
        }

        return numbers;
    }

    public static List<Integer> decodeWithGaps(byte[] encoded) {
        if (encoded.length == 0) return new ArrayList<>();

        List<Integer> gaps    = decode(encoded);
        List<Integer> numbers = new ArrayList<>();
        int current = 0;

        for (int gap : gaps) {
            current += (gap - 1);
            numbers.add(current);
        }

        return numbers;
    }

    private static class BitWriter {
        private byte[] bytes = new byte[256];
        private int size;
        private int currentByte = 0;
        private int bitPosition = 0;

        public void writeBit(int bit) {
            if (bit == 1) {
                currentByte |= (1 << (7 - bitPosition));
            }

            bitPosition++;

            if (bitPosition == 8) {
                if (size == bytes.length) {
                    bytes = Arrays.copyOf(bytes, bytes.length * 2);
                }
                bytes[size++] = (byte) currentByte;
                currentByte = 0;
                bitPosition = 0;
            }
        }

        public byte[] toByteArray() {
            if (bitPosition > 0) {
                if (size == bytes.length) {
                    bytes = Arrays.copyOf(bytes, bytes.length + 1);
                }
                bytes[size++] = (byte) currentByte;
            }
            return Arrays.copyOf(bytes, size);
        }
    }

    private static class BitReader {
        private final byte[] bytes;
        private int byteIndex = 0;
        private int bitPosition = 0;

        public BitReader(byte[] bytes) {
            this.bytes = bytes;
        }

        public int readBit() {
            if (byteIndex >= bytes.length) {
                return -1; // EOF
            }

            int bit = (bytes[byteIndex] >> (7 - bitPosition)) & 1;

            bitPosition++;

            if (bitPosition == 8) {
                byteIndex++;
                bitPosition = 0;
            }

            return bit;
        }

        public boolean hasNext() {
            return byteIndex < bytes.length;
        }
    }
}
