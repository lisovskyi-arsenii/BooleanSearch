package compression;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// використовується для docId в inverted index
// алгоритм - запис додатних цілих чисел
// m = |log_2(n)| - кількість бітів - 1
// сам вигляд: m нулів + повний двійковий запис числа n
//
@Slf4j
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

        for (int num : numbers) {
            if (num <= 0) throw new IllegalArgumentException("Gamma requires positive integers, got: " + num);
        }

        BitWriter writer = new BitWriter();
        for (int num : numbers) {
            String encoded = encodeNumber(num);
            for (char c : encoded.toCharArray()) {
                writer.writeBit(c == '1' ? 1 : 0);
            }
        }

        byte[] bits = writer.toByteArray();
        byte[] result = new byte[4 + bits.length];
        int count = numbers.size();
        result[0] = (byte) (count >>> 24);
        result[1] = (byte) (count >>> 16);
        result[2] = (byte) (count >>> 8);
        result[3] = (byte) (count);
        System.arraycopy(bits, 0, result, 4, bits.length);

        return result;
    }

    public static byte[] encodeWithGaps(List<Integer> sortedNumbers) {
        if (sortedNumbers.isEmpty()) return new byte[0];

        for (int i = 1; i < sortedNumbers.size(); i++) {
            if (sortedNumbers.get(i) < sortedNumbers.get(i - 1)) {
                throw new IllegalArgumentException(
                        "Input must be sorted ascending, but " + sortedNumbers.get(i) + " < " + sortedNumbers.get(i - 1));
            }
        }

        List<Integer> gaps = new ArrayList<>();
        int previous = 0;

        for (int num : sortedNumbers) {
            // кодується різниця між docId
            // +1 бо 0 не кодується
            gaps.add((num - previous) + 1);
            previous = num;
        }

        return encode(gaps);
    }

    public static List<Integer> decode(byte[] encoded) {
        if (encoded.length == 0) return new ArrayList<>();
        if (encoded.length < 4) throw new IllegalArgumentException("Missing count header");

        int count = ((encoded[0] & 0xFF) << 24) | ((encoded[1] & 0xFF) << 16)
                | ((encoded[2] & 0xFF) << 8) | (encoded[3] & 0xFF);

        List<Integer> numbers = new ArrayList<>();
        BitReader reader = new BitReader(encoded, 32);

        for (int i = 0; i < count; i++) {
            int bit = reader.readBit();
            if (bit == -1) break;

            int m = 0;
            while (bit == 0) {
                m++;
                bit = reader.readBit();
                if (bit == -1) return numbers;
            }

            int value = 1;
            for (int j = 0; j < m; j++) {
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

        List<Integer> gaps = decode(encoded);
        List<Integer> numbers = new ArrayList<>();
        int current = 0;

        for (int gap : gaps) {
            // -1 щоб прибрати +1 коли encode виконуємо
            current += (gap - 1);
            numbers.add(current);
        }

        return numbers;
    }

    // цей клас накопичує біти в байтах, коли повний байт заповнюється, він записується в масив
    private static class BitWriter {
        private byte[] bytes = new byte[256];
        private int size        = 0;
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

    // цей клас зчитує байти
    private static class BitReader {
        private final byte[] bytes;
        private int byteIndex;
        private int bitPosition;

        public BitReader(byte[] bytes, int startBit) {
            this.bytes = bytes;
            this.byteIndex = startBit / 8;
            this.bitPosition = startBit % 8;
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
    }
}
