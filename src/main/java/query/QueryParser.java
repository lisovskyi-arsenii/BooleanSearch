package query;

import core.BooleanSearchEngine;
import enums.SearchStructureType;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public final class QueryParser {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\w+|AND|OR|NOT");

    private QueryParser() {
        throw new UnsupportedOperationException("QueryParser cannot be instantiated");
    }

    public static Optional<Set<Integer>> parseAndExecute(
            String query,
            BooleanSearchEngine searchEngine,
            SearchStructureType type
    ) {
        if (query == null || query.isBlank()) {
            log.warn("Query is empty or blank");
            System.out.println("Query can't be empty");
            return Optional.empty();
        }

        query = cleanupQuery(query);
        List<String> tokens = tokenize(query);

        if (tokens.isEmpty()) {
            log.warn("No tokens found after parsing");
            return Optional.empty();
        }

        return evaluateTokens(tokens, searchEngine, type);
    }

    private static Optional<Set<Integer>> evaluateTokens(
            List<String> tokens,
            BooleanSearchEngine searchEngine,
            SearchStructureType type
    ) {
        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        if (tokens.getFirst().equals("AND") || tokens.getFirst().equals("OR")) {
            System.out.println("Query cannot start with AND/OR operator");
            return Optional.empty();
        }

        String lastToken = tokens.get(tokens.size() - 1);
        if (lastToken.equals("AND") || lastToken.equals("OR") || lastToken.equals("NOT")) {
            System.out.println("Query cannot end with operator: " + lastToken);
            return Optional.empty();
        }

        List<Integer> operatorIndices = new ArrayList<>();
        List<String> operators = new ArrayList<>();

        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if (token.equals("AND") || token.equals("OR")) {
                operatorIndices.add(i);
                operators.add(token);
            }
        }

        List<List<String>> operandSegments = new ArrayList<>();

        int start = 0;
        for (int opIndex : operatorIndices) {
            operandSegments.add(tokens.subList(start, opIndex));
            start = opIndex + 1;
        }
        operandSegments.add(tokens.subList(start, tokens.size()));

        List<Set<Integer>> operandResults = new ArrayList<>();

        for (int i = 0; i < operandSegments.size(); i++) {
            List<String> segment = operandSegments.get(i);

            if (segment.isEmpty()) {
                System.out.printf("Empty operand at position %d%n", i);
                return Optional.empty();
            }

            Set<Integer> operandResult = parseOperandSegment(segment, searchEngine, type);
            if (operandResult == null) {
                return Optional.empty();
            }

            operandResults.add(operandResult);
        }

        Set<Integer> result = operandResults.getFirst();

        for (int i = 0; i < operators.size(); i++) {
            String operator = operators.get(i);
            Set<Integer> rightOperand = operandResults.get(i + 1);

            result = applyBinaryOperation(result, operator, rightOperand);

            if (result.isEmpty() && operator.equals("AND")) {
                log.debug("AND resulted in empty set, short-circuiting");
                return Optional.empty();
            }
        }

        return result.isEmpty() ? Optional.empty() : Optional.of(result);
    }

    /**
     * - ["term"] → звичайний пошук
     * - ["NOT", "term"] → унарний NOT
     * - ["term1", "NOT", "term2"] → term1 NOT term2 (бінарний)
     * - ["term1", "NOT", "term2", "NOT", "term3"] → (term1 NOT term2) NOT term3
     */
    private static Set<Integer> parseOperandSegment(
            List<String> segment,
            BooleanSearchEngine searchEngine,
            SearchStructureType type
    ) {
        if (segment.isEmpty()) {
            System.out.println("Empty operand segment");
            return null;
        }

        if (segment.size() == 1) {
            String term = segment.get(0);

            if (term.equals("NOT") || term.equals("AND") || term.equals("OR")) {
                System.out.printf("Expected term, but got operator '%s'%n", term);
                return null;
            }

            Set<Integer> result = searchEngine.search(term, type)
                    .orElse(Collections.emptySet());
            log.debug("Search for '{}': {} docs", term, result.size());
            return result;
        }

        if (segment.get(0).equals("NOT")) {
            if (segment.size() < 2) {
                System.out.println("NOT operator requires a term");
                return null;
            }

            List<String> restSegment = segment.subList(1, segment.size());
            Set<Integer> operandResult = parseOperandSegment(restSegment, searchEngine, type);

            if (operandResult == null) {
                return null;
            }

            Set<Integer> allDocs = searchEngine.getAllDocumentIDs();
            Set<Integer> result = new HashSet<>(allDocs);
            result.removeAll(operandResult);

            log.debug("Unary NOT result: {} docs", result.size());
            return result;
        }

        int notIndex = segment.indexOf("NOT");

        if (notIndex > 0) {
            // Ліва частина (до NOT)
            List<String> leftSegment = segment.subList(0, notIndex);
            Set<Integer> leftResult = parseOperandSegment(leftSegment, searchEngine, type);

            if (leftResult == null) {
                return null;
            }

            List<String> rightSegment = segment.subList(notIndex + 1, segment.size());
            Set<Integer> rightResult = parseOperandSegment(rightSegment, searchEngine, type);

            if (rightResult == null) {
                return null;
            }

            // Застосовуємо бінарний NOT (різниця множин)
            Set<Integer> result = new HashSet<>(leftResult);
            result.removeAll(rightResult);

            log.debug("Binary NOT result: {} docs", result.size());
            return result;
        }

        System.out.println("Invalid operand segment: " + String.join(" ", segment));
        return null;
    }

    private static Set<Integer> applyBinaryOperation(
            Set<Integer> left,
            String operator,
            Set<Integer> right
    ) {
        return switch (operator) {
            case "AND" -> {
                if (left.isEmpty() || right.isEmpty()) {
                    yield Collections.emptySet();
                }
                Set<Integer> intersection = new HashSet<>(left);
                intersection.retainAll(right);
                log.debug("AND result: {} docs", intersection.size());
                yield intersection;
            }
            case "OR" -> {
                Set<Integer> union = new HashSet<>(left);
                union.addAll(right);
                log.debug("OR result: {} docs", union.size());
                yield union;
            }
            default -> {
                log.error("Unknown operator: {}", operator);
                yield Collections.emptySet();
            }
        };
    }

    private static List<String> tokenize(String query) {
        Matcher matcher = TOKEN_PATTERN.matcher(query);
        List<String> tokens = new ArrayList<>();

        while (matcher.find()) {
            tokens.add(matcher.group());
        }

        return tokens;
    }

    private static String cleanupQuery(String query) {
        return query.trim()
                .replaceAll("\\s+", " ")
                .replaceAll("(?i)\\band\\b", "AND")
                .replaceAll("(?i)\\bor\\b", "OR")
                .replaceAll("(?i)\\bnot\\b", "NOT");
    }
}
