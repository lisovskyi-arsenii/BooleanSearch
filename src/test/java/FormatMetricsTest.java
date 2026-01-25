import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import serialization.FormatMetrics;

import static org.junit.jupiter.api.Assertions.*;

class FormatMetricsTest {
    private static final String FORMAT_BINARY = "Binary";
    private static final String FORMAT_TEXT = "Text";
    private static final String FORMAT_JSON = "JSON";

    private static final long SIZE_SMALL = 10_240; // 10 KB
    private static final long TIME_SAVE_SMALL = 50;
    private static final long TIME_LOAD_SMALL = 30;

    private static final long SIZE_MEDIUM = 102_400; // 100 KB
    private static final long TIME_SAVE_MEDIUM = 150;
    private static final long TIME_LOAD_MEDIUM = 100;

    private static final long SIZE_LARGE = 512_000; // 500 KB
    private static final long TIME_SAVE_LARGE = 300;
    private static final long TIME_LOAD_LARGE = 250;

    private FormatMetrics smallFileMetrics;
    private FormatMetrics mediumFileMetrics;
    private FormatMetrics largeFileMetrics;

    @BeforeEach
    void setUp() {
        smallFileMetrics = new FormatMetrics(
                FORMAT_BINARY,
                TIME_SAVE_SMALL,
                TIME_LOAD_SMALL,
                SIZE_SMALL
        );

        mediumFileMetrics = new FormatMetrics(
                FORMAT_JSON,
                TIME_SAVE_MEDIUM,
                TIME_LOAD_MEDIUM,
                SIZE_MEDIUM
        );

        largeFileMetrics = new FormatMetrics(
                FORMAT_TEXT,
                TIME_SAVE_LARGE,
                TIME_LOAD_LARGE,
                SIZE_LARGE
        );
    }

    // Tests for accessors in record class FormatMetrics

    @Nested
    @DisplayName("Record Accessor Tests")
    class RecordAccessorTests {

        @Test
        @DisplayName("formatName() returns correct name")
        void formatName_ReturnsCorrectName() {
            assertEquals(FORMAT_BINARY, smallFileMetrics.formatName());
            assertEquals(FORMAT_JSON, mediumFileMetrics.formatName());
            assertEquals(FORMAT_TEXT, mediumFileMetrics.formatName());
        }

        @Test
        @DisplayName("timeSerialization() returns correct time")
        void timeSerialization_ReturnsCorrectTime() {
            assertEquals(TIME_SAVE_SMALL, smallFileMetrics.timeSerialization());
            assertEquals(TIME_SAVE_MEDIUM, mediumFileMetrics.timeSerialization());
            assertEquals(TIME_SAVE_LARGE, largeFileMetrics.timeSerialization());
        }

        @Test
        @DisplayName("timeDeserialization() returns correct time")
        void timeDeserialization_ReturnsCorrectTime() {
            assertEquals(TIME_LOAD_SMALL, smallFileMetrics.timeDeserialization());
            assertEquals(TIME_LOAD_MEDIUM, mediumFileMetrics.timeDeserialization());
            assertEquals(TIME_LOAD_LARGE, largeFileMetrics.timeDeserialization());
        }

        @Test
        @DisplayName("sizeInBytes() returns correct size")
        void sizeInBytes_ReturnsCorrectSize() {
            assertEquals(SIZE_SMALL, smallFileMetrics.sizeInBytes());
            assertEquals(SIZE_MEDIUM, mediumFileMetrics.sizeInBytes());
            assertEquals(SIZE_LARGE, largeFileMetrics.sizeInBytes());
        }
    }


    // Tests for totalTime()

    @Nested
    @DisplayName("totalTime() tests")
    class TotalTimeTests {

        @Test
        @DisplayName("totalTime() returns total time of serialization and deserialization")
        void totalTime_ReturnsCorrectTime() {
            assertEquals(TIME_SAVE_SMALL + TIME_LOAD_SMALL, smallFileMetrics.totalTime());
            assertEquals(TIME_SAVE_MEDIUM + TIME_LOAD_MEDIUM, mediumFileMetrics.totalTime());
            assertEquals(TIME_SAVE_LARGE + TIME_LOAD_LARGE, largeFileMetrics.totalTime());
        }

        @Test
        @DisplayName("totalTime() when both returns zero")
        void totalTime_WhenBothReturnsZero() {
            FormatMetrics zeroFormatMetrics = new FormatMetrics("test", 0, 0, 1000);

            assertEquals(0, zeroFormatMetrics.totalTime());
        }

        @Test
        @DisplayName("totalTime() with big value")
        void totalTime_WithBigValue() {
            long largeTime = Integer.MAX_VALUE;
            FormatMetrics bigFormatMetrics = new FormatMetrics("test", largeTime, largeTime, 1000);

            long totalTime = bigFormatMetrics.totalTime();

            assertTrue(totalTime > 0, "Must be positive number without overflow");
            assertEquals(largeTime * 2L, bigFormatMetrics.totalTime());
        }
    }


    // Tests for getSizeIn*()
    @Nested
    @DisplayName("getSizeIn*() Tests")
    class GetSizeInTests {

        @Test
        @DisplayName("getSizeInKB() and getSizeInMB() for small file 10 kb")
        void getSizeIn_ReturnsCorrectSizeSmallFile() {
            double sizeKB = smallFileMetrics.getSizeInKB();
            double sizeMB = mediumFileMetrics.getSizeInMB();

            assertEquals(10, sizeKB);
            assertEquals(10 / 1024, sizeMB);
        }

        @Test
        @DisplayName("getSizeIn() for large file")
        void getSizeIn_ReturnsCorrectSizeLargeFile() {
            double sizeKB = largeFileMetrics.getSizeInKB();
            double sizeMB = largeFileMetrics.getSizeInMB();

            assertEquals(SIZE_LARGE / 1024, sizeKB);
            assertEquals(SIZE_LARGE / 1024, sizeMB);
        }
    }


    // Tests for speedSerialization
    @Nested
    @DisplayName("speedSerialization() Tests")
    class SpeedSerializationTests {

        @Test
        @DisplayName("speedSerialization() returns correct speed")
        void speedSerialization_ReturnsCorrectSpeed() {
            long speed = smallFileMetrics.speedSerialization();

            assertTrue(speed > 0, "Speed must be positive number");
        }

        @Test
        @DisplayName("speedSerialization() with 0 speed do not throw exception")
        void speedSerialization_With0Speed() {
            FormatMetrics zeroTime = new FormatMetrics("test", 0, 100, 1000);

            assertDoesNotThrow(() -> {
                long speed = zeroTime.speedSerialization();
            });
        }
    }


}
