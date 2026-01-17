package enums;

import java.util.Arrays;
import java.util.Optional;

public enum FileGenerationType {
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large");

    private final String type;

    FileGenerationType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public static Optional<FileGenerationType> fromString(String type) {
        return Arrays.stream(FileGenerationType.values())
                .filter(fileType -> fileType.getType().equalsIgnoreCase(type))
                .findFirst();
    }
}
