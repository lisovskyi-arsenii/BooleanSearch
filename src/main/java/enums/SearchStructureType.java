package enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum SearchStructureType {
    INDEX("index"),
    MATRIX("matrix");

    private final String type;

    SearchStructureType(String type) {
        this.type = type;
    }

    public static Optional<SearchStructureType> fromString(String type) {
        return Arrays.stream(SearchStructureType.values())
                .filter(t -> t.type.equalsIgnoreCase(type))
                .findFirst();
    }

}
