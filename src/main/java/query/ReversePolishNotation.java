package query;


import com.google.common.collect.Sets;
import core.BooleanSearchEngine;
import edu.princeton.cs.algs4.Stack;
import enums.SearchOperators;
import enums.SearchStructureType;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

import static enums.SearchOperators.isLogicalOperator;

@Slf4j
public class ReversePolishNotation {
    private ReversePolishNotation() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Set<Integer> evaluate(String expression, BooleanSearchEngine searchEngine) throws IllegalArgumentException {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or blank");
        }
        if (searchEngine == null) {
            throw new IllegalArgumentException("SearchEngine cannot be null");
        }


        var allDocIds = searchEngine.getAllDocumentIDs();

        Stack<Set<Integer>> stack = new Stack<>();
        String[] tokens = expression.trim().split("\\s+");

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            if (isLogicalOperator(token)) {
                processOperator(token, stack, allDocIds);

            } else {
                Set<Integer> docIds = searchEngine.search(token, SearchStructureType.INDEX)
                        .orElse(Set.of());

                log.debug("Term `{}` -> `{}` docs", token, docIds.size());
                stack.push(docIds);
            }
        }

        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Stack is empty");
        }
        if (stack.size() > 1) {
            throw new IllegalArgumentException(
                    "Invalid RPN: too many operands remaining (missing operators?)");
        }

        return stack.pop();
    }

    private static void processOperator(String token,
                                        Stack<Set<Integer>> stack,
                                        Set<Integer> allDocIds) {
        SearchOperators operator = SearchOperators.fromOperation(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token: " + token));

        switch (operator) {
            case NOT -> {
                if (stack.isEmpty()) {
                    throw new IllegalArgumentException("Insufficient operands for NOT");
                }

                Set<Integer> operand = stack.pop();

                var difference = Sets.difference(allDocIds, operand);

                var result = new HashSet<>(difference);
                stack.push(result);
            }
            case AND -> {
                if (stack.size() < 2) {
                    throw new IllegalArgumentException("Insufficient operands for AND");
                }

                Set<Integer> operand1 = stack.pop();
                Set<Integer> operand2 = stack.pop();

                if (operand1.isEmpty() || operand2.isEmpty()) {
                    stack.push(Set.of());
                    return;
                }

                var intersection = Sets.intersection(operand1, operand2);
                var result = new HashSet<>(intersection);
                stack.push(result);
            }
            case OR -> {
                if (stack.size() < 2) {
                    throw new IllegalArgumentException("Insufficient operands for OR");
                }

                Set<Integer> operand1 = stack.pop();
                Set<Integer> operand2 = stack.pop();

                if (operand1.isEmpty()) {
                    stack.push(operand2);
                    return;
                }
                if (operand2.isEmpty()) {
                    stack.push(operand1);
                    return;
                }

                var union = Sets.union(operand1, operand2);
                var result = new HashSet<>(union);
                stack.push(result);
            }
            default -> throw new IllegalArgumentException("Invalid operator: " + operator);
        }
    }
}
