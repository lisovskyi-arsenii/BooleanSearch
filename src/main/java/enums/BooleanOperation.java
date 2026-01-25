package enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum BooleanOperation {
    AND("and"),
    OR("or"),
    NOT("not");

    private final String operation;

    BooleanOperation(String operation) {
        this.operation = operation;
    }

    public static Optional<BooleanOperation> fromString(String operation) {
        return Arrays.stream(BooleanOperation.values())
                .filter(op -> op.operation.equalsIgnoreCase(operation))
                .findFirst();
    }
}
