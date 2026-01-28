package main;

import core.BooleanSearchEngine;
import query.ReversePolishNotation;
import query.ShuntingYard;

import java.io.IOException;

import static constants.Filenames.DIRECTORY_PATH;

public class TestMain {
    public static void main(String[] args) throws IOException {
//        var rpnExpression = ShuntingYard.toRPN("( 1 + 2 ) / 2 - 3 + ( 3 + 4 ) / 4");
//        System.out.println(ReversePolishNotation.evaluate(rpnExpression));
        BooleanSearchEngine searchEngine = new BooleanSearchEngine();
        searchEngine.indexDocuments(DIRECTORY_PATH);
        String expr = "(apple OR banana) AND (banana OR carrot)";
        var rpnExpression = ShuntingYard.toRPN(expr);
        System.out.println(ReversePolishNotation.evaluate(rpnExpression, searchEngine));


    }
}
