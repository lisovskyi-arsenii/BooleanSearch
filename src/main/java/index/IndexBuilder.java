package index;

import ch.qos.logback.core.subst.Tokenizer;

import java.nio.file.Path;

public class IndexBuilder {
    private final InvertedIndex invertedIndex;
    private final Tokenizer tokenizer;

    // TODO
    public IndexBuilder() {
        invertedIndex = new InvertedIndex();
        tokenizer = new Tokenizer("");
    }

    public void buildFromDirectory(String directory) {

    }

    public void buildFromFile(Path file, int docID) {

    }
}
