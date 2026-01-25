package query;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
enum Operation {
    AND("and"),
    OR("or"),
    NOT("not");

    private final String operation;
    Operation(String operation) {
        this.operation = operation;
    }

    public static Optional<Operation> fromString(String operation) {
        return Arrays.stream(Operation.values())
                .filter(op -> op.operation.equalsIgnoreCase(operation))
                .findFirst();
    }
}


@Slf4j
public class QueryParser {
    private static final String PATTERN = "\\w+|AND|OR|NOT|\\(|\\)";
    private static final Pattern TOKEN_PATTERN = Pattern.compile(PATTERN);

    public void parseQuery(String query) {
        if (query == null || query.isEmpty()) {
            System.out.println("Query can't be empty");
            return;
        }

        query = cleanupQuery(query);
        List<String> tokens = tokenize(query);

        List<Operation> operations = getOperations(tokens);
        if (operations.isEmpty()) {
            System.out.println("No operations found");
            return;
        }

        var result = doOperations(operations);
        System.out.println("Result: " + result);
    }


    private Set<Integer> doOperations(List<Operation> operations) {
        Set<Integer> result = new HashSet<>();

    }

    private List<Operation> getOperations(List<String> tokens) {
        return tokens.stream()
                .filter(token -> token.equalsIgnoreCase("and")
                        || token.equalsIgnoreCase("or")
                        || token.equalsIgnoreCase("not"))
                .map(Operation::fromString)
                .flatMap(Optional::stream)
                .toList();
    }

    private List<String> tokenize(String query) {
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        List<String> tokens = new ArrayList<>();

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    private String cleanupQuery(String query) {
        return query.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("\\band\\b", "AND")
                .replaceAll("\\bor\\b", "OR")
                .replaceAll("\\bnot\b", "NOT");
    }
}
