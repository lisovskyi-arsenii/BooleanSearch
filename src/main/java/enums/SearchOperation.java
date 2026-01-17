package enums;

import java.util.Arrays;
import java.util.Optional;

public enum SearchOperation {
    AND("and"),
    OR("or"),
    NOT("not");

    private final String operation;

    SearchOperation(String operation) {
        this.operation = operation;
    }

    public String getOperation() {
        return operation;
    }

    public static Optional<SearchOperation> getOperation(String operation) {
        return Arrays.stream(SearchOperation.values())
                .filter(op -> op.operation.equalsIgnoreCase(operation))
                .findFirst();
    }
}
