package constants;

public final class Filenames {
    private Filenames() {
        throw new AssertionError("Filenames is class which contains constants - cannot create instance");
    }

    public static final String SERIALIZATION_FILENAME_SER = "serialization.ser";
    public static final String SERIALIZATION_FILENAME_TXT = "serialization.txt";
    public static final String SERIALIZATION_FILENAME_JSON = "serialization.json";
    public static final String DIRECTORY_PATH = "documents";
    public static final String DEFAULT_FILENAME = "serialization";

}
