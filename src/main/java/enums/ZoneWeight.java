package enums;

import lombok.Getter;

@Getter
public enum ZoneWeight {
    TITLE(0.40),
    AUTHOR(0.20),
    CHAPTER_HEADING(0.30),
    BODY(0.10);

    private final double weight;

    ZoneWeight(double weight) {
        this.weight = weight;
    }

    static {
        double sum = 0.0;
        for (var zone : values()) {
            sum += zone.weight;
        }
        if (Math.abs(sum - 1.0) > 0.0) {
            throw new ExceptionInInitializerError(
                    "ZoneWeight values must sum to 1.0, but got: " + sum
            );
        }
    }
}
