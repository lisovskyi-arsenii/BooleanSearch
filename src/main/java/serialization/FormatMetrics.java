package serialization;

public record FormatMetrics(String formatName, long timeSerialization, long timeDeserialization, long sizeInBytes) {

    public double getSizeInKB() {
        return (double) sizeInBytes / 1024;
    }

    public double getSizeInMB() {
        return getSizeInKB() / 1024;
    }

    public String getFormattedSize() {
        if (sizeInBytes < 1024) {
            return sizeInBytes + " B";
        } else if (sizeInBytes < 1024 * 1024) {
            return String.format("%.2f KB", getSizeInKB());
        } else {
            return String.format("%.2f MB", getSizeInMB());
        }
    }

    public long totalTime() {
        return this.timeSerialization + this.timeDeserialization;
    }

    public long speedSerializationBytes() {
        if (timeSerialization <= 0) {
            return Long.MAX_VALUE;
        }

        return sizeInBytes / timeSerialization; // bytes/ms
    }

    public double speedSerializationKB() {
        if (timeSerialization <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        return (sizeInBytes * 1000.0) / (1024.0 * timeSerialization);
    }

    public long speedDeserializationBytes() {
        if (timeDeserialization <= 0) {
            return Long.MAX_VALUE;
        }

        return sizeInBytes / timeDeserialization; // bytes/ms
    }

    public double speedDeserializationKB() {
        if (timeDeserialization <= 0) {
            return Double.POSITIVE_INFINITY;
        }

        return (sizeInBytes * 1000.0) / (1024.0 * timeDeserialization);
    }
}
