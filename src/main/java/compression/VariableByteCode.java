package compression;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

//
// алгоритм - замість збереження числа в 4 байти - оптимізуємо і записуємо це в меншу кількість байтів
//
@Slf4j
public final class VariableByteCode {
    private static final byte MASK = 0x7F; // 0111 1111

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

        // 32 / 7 -> приблизно 5, тому створюємо масив на 5 елементів щоб записувати туди байти
        int[] chunks = new int[5];
        int count = 0;

        do {
            chunks[count++] = num & MASK;
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
            if (num < previous) {
                throw new IllegalArgumentException(
                        "Input must be sorted ascending, but " + num + " < " + previous);
            }
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
            current = (current << 7) | (val & MASK);

            if ((val & 0x80) != 0) {
                numbers.add(current);
                current = 0;
            }
        }

        if (current != 0) {
            throw new IllegalStateException(
                    "Incomplete VByte number at end of stream — data may be corrupted");
        }
        return numbers;
    }

    public static List<Integer> decodeWithGaps(byte[] encoded) {
        if (encoded.length == 0) return new ArrayList<>();

        List<Integer> gaps = decode(encoded);
        List<Integer> numbers = new ArrayList<>();

        int current = 0;
        for (int gap : gaps) {
            current += gap;
            numbers.add(current);
        }

        return numbers;
    }
}
