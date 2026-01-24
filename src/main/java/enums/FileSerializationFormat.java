package enums;

import java.util.Arrays;
import java.util.Optional;

public enum FileSerializationFormat {
    BINARY("ser"),
    TEXT("txt"),
    JSON("json");

    private final String extension;

    FileSerializationFormat(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public String getFilenameWithExtension(String basename) {
        return basename + "." + extension;
    }

    public static Optional<FileSerializationFormat> fromFormat(String format) {
        return Arrays.stream(values())
                .filter(extension -> extension.extension.equalsIgnoreCase(format))
                .findFirst();
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
