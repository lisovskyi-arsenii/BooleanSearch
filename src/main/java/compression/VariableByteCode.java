package compression;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class VariableByteCode {
    private VariableByteCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static byte[] encode(List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int num : numbers) {
            encodeNumber(num, baos);
        }
        return baos.toByteArray();
    }

    private static void encodeNumber(int num, ByteArrayOutputStream baos) {
        if (num < 0) {
            throw new IllegalArgumentException("VByte supports only non-negative integers");
        }

        int[] chunks = new int[5];
        int count = 0;

        do {
            chunks[count++] = num & 0x7F;
            num >>>= 7;
        } while (num != 0);

        for (int i = count - 1; i >= 0; i--) {
            int b = chunks[i];
            if (i == 0) {
                b |= 0x80;
            }
            baos.write(b);
        }
    }

    public static byte[] encodeWithGaps(List<Integer> sortedNumbers) {
        if (sortedNumbers.isEmpty()) {
            return new byte[0];
        }

        List<Integer> gaps = new ArrayList<>();
        int previous = 0;

        for (int num : sortedNumbers) {
            gaps.add(num - previous);
            previous = num;
        }

        return encode(gaps);
    }

    public static List<Integer> decode(byte[] encoded) {
        List<Integer> numbers = new ArrayList<>();
        int current = 0;

        for (byte b : encoded) {
            int val = b & 0xFF;
            current = (current << 7) | (val & 0x7F);

            if ((val & 0x80) != 0) {
                numbers.add(current);
                current = 0;
            }
        }

        return numbers;
    }

    public static List<Integer> decodeWithGaps(byte[] encoded) {
        List<Integer> gaps = decode(encoded);
        List<Integer> numbers = new ArrayList<>();

        int current = 0;
        for (int gap : gaps) {
            current += gap;
            numbers.add(current);
        }

        return numbers;
    }

    public static double compressionRatio(List<Integer> original, byte[] compressed) {
        int originalBytes = original.size() * 4; // Integer = 4 bytes
        return (double) compressed.length / originalBytes * 100;
    }
}
