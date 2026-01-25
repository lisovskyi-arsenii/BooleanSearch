package main;

import query.QueryParser;

import java.io.IOException;

public class TestMain {
    public static void main(String[] args) throws IOException {
        QueryParser parser = new QueryParser();
        parser.parseQuery("java and python or apple");

    }

}
