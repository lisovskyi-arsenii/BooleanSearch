package serialization;

public enum FileType {
    BINARY("bin"),
    TEXT("txt"),
    JSON("json");

    private final String extension;

    FileType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public String getFilenameWithExtension(String basename) {
        return basename + "." + extension;
    }

    @Override
    public String toString() {
        return name().toLowerCase();
    }
}
