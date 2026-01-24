package main;

import core.BooleanSearchEngine;
import matrix.TermDocumentMatrix;
import query.QueryExecutor;

import java.io.IOException;

import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) throws IOException {
        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
        searchEngine.indexDocuments(DIRECTORY_PATH);
        var matrix = searchEngine.getMatrix();

        QueryExecutor<TermDocumentMatrix> queryExecutor = new QueryExecutor<>(matrix);
        var documents = queryExecutor.search("alpha");
        if (documents.isPresent()) {
            var documentNames = searchEngine.getDocumentNames(documents.get());
            System.out.println(documentNames);
        }
    }

}
