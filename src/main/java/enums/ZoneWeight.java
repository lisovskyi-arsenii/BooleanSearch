package enums;

import lombok.Getter;

import java.math.BigDecimal;

@Getter
public enum ZoneWeight {
    TITLE(0.40),
    AUTHOR(0.20),
    SUBJECT(0.25),
    BODY(0.15);

    private final BigDecimal weight;

    ZoneWeight(double weight) {
        this.weight = BigDecimal.valueOf(weight);
    }

    static {
        BigDecimal sum = BigDecimal.ZERO;
        for (var zone : values()) {
            sum = sum.add(zone.weight);
        }
        if (sum.compareTo(BigDecimal.ONE) != 0) {
            throw new ExceptionInInitializerError(
                    "ZoneWeight values must sum to 1.0, but got: " + sum
            );
        }
    }
}
