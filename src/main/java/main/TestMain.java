package main;

import core.BooleanSearchEngine;
import query.PhraseSearch;

import java.io.IOException;
import java.util.List;

import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) throws IOException {
        BooleanSearchEngine engine = new BooleanSearchEngine();
        engine.indexDocuments(DIRECTORY_PATH);


        var biwordIndex = engine.getBiwordIndex();
        var positionalIndex = engine.getPositionalIndex();

        System.out.println("Positional index size: " + positionalIndex.size());


        PhraseSearch search = new PhraseSearch(positionalIndex, biwordIndex);
        var searchResult = List.of("ran", "to", "his", "horse");
        var result = search.searchPhrasePositional(searchResult);
        result.ifPresentOrElse(
                docs -> System.out.println("Found in documents: " + docs),
                () -> System.out.println("Phrase not found")
        );
    }
}
