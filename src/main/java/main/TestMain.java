package main;

import enums.ZoneWeight;
import index.ZoneIndex;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

public class TestMain {
    static void main() throws IOException {
//        var result = ZoneParser.parseDocuments(Path.of("documents/large_1342.txt"));
//        System.out.println(result.get(ZoneWeight.TITLE));
//        System.out.println(result.get(ZoneWeight.AUTHOR));
//        System.out.println(result.get(ZoneWeight.SUBJECT));

        ZoneIndex zoneIndex = new ZoneIndex();

        zoneIndex.addDocument(Map.of(
                ZoneWeight.TITLE,  "Java Programming Language",
                ZoneWeight.BODY,   "Java is a high-level programming language",
                ZoneWeight.AUTHOR, "James Gosling"
        ), 1);

        zoneIndex.addDocument(Map.of(
                ZoneWeight.TITLE,  "Python Basics",
                ZoneWeight.BODY,   "Python is a popular programming language",
                ZoneWeight.AUTHOR, "Guido van Rossum"
        ), 2);

        Set<Integer> result = zoneIndex.search("program");
        System.out.println(result);

        zoneIndex.search("program").stream()
                .map(docId -> Map.entry(docId, zoneIndex.score("program", docId)))
                .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                .forEach(e -> System.out.println("docId=" + e.getKey() + " score=" + e.getValue()));
    }
}
