package compression;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class VariableByteCode {
    public static byte[] encode(List<Integer> numbers) {
        if (numbers.isEmpty()) {
            return new byte[0];
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        for (int num : numbers) {
            while (num >= 128) {
                byte chunk = (byte) (num & 0x7F);
                baos.write(chunk);
                num >>>= 7;
            }

            byte lastChunk = (byte) (num | 0x80);
            baos.write(lastChunk);
        }

        return baos.toByteArray();
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
        int shift = 0;

        for (byte b : encoded) {
            if ((b & 0x80) == 0) {
                current |= (b & 0x7F) << shift;
                shift += 7;
            } else {
                current |= (b & 0x7F) << shift;
                numbers.add(current);
                current = 0;
                shift = 0;
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
