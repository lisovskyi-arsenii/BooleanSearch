package enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum FileOperation {
    SAVE("save"),
    LOAD("load");

    private final String operation;

    FileOperation(String operation) {
        this.operation = operation;
    }

    public static Optional<FileOperation> fromString(String operation) {
        return Arrays.stream(FileOperation.values())
                .filter(op -> op.getOperation().equalsIgnoreCase(operation))
                .findFirst();
    }
}
