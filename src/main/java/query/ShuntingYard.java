package query;

import edu.princeton.cs.algs4.Stack;
import enums.SearchOperators;

import java.util.ArrayList;
import java.util.List;

import static enums.SearchOperators.isLogicalOperator;

public class ShuntingYard {
    private ShuntingYard() {
        throw new UnsupportedOperationException("Utility class");
    }


    public static String toRPN(String expression) throws IllegalArgumentException {
        if (expression == null || expression.isBlank()) {
            throw new IllegalArgumentException("Expression cannot be null or blank");
        }

        List<String> output = new ArrayList<>();
        Stack<String> operators = new Stack<>();

        String[] tokens = tokenize(expression);

        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }

            var op = SearchOperators.fromOperation(token);

            if (op.isPresent() && op.get() == SearchOperators.LEFT_PAREN) {
                operators.push(token);

            } else if (op.isPresent() && op.get() == SearchOperators.RIGHT_PAREN) {
                while (!operators.isEmpty() &&
                    !operators.peek().equals(SearchOperators.LEFT_PAREN.getOperation())) {
                    output.add(operators.pop());
                }
                if (operators.isEmpty()) {
                    throw new IllegalArgumentException("Mismatched parentheses: no matching '('");
                }
                operators.pop(); // remove left paren

            } else if (token.equals("NOT")) {
                operators.push(token);
            } else if (op.isPresent() && isLogicalOperator(token)) {
                while (!operators.isEmpty() &&
                        !operators.peek().equals(SearchOperators.LEFT_PAREN.getOperation()) &&
                        !operators.peek().equals("NOT") &&
                        getPriority(operators.peek()) >= getPriority(token)) {
                    output.add(operators.pop());
                }
                operators.push(token);
            } else {
                // word for search
                output.add(token);

                while (!operators.isEmpty() && operators.peek().equals("NOT")) {
                    output.add(operators.pop());
                }
            }
        }

        while (!operators.isEmpty()) {
            String remaining = operators.pop();
            if (remaining.equals(SearchOperators.LEFT_PAREN.getOperation()) ||
                    remaining.equals(SearchOperators.RIGHT_PAREN.getOperation())) {
                throw new IllegalArgumentException("Mismatched parentheses");
            }

            output.add(remaining);
        }

        return String.join(" ", output);
    }

    private static String[] tokenize(String expression) {
        return expression
                .replaceAll("(AND|OR|NOT|\\(|\\))", " $1 ")
                .trim()
                .split("\\s+");
    }

    private static int getPriority(String token) {
        return SearchOperators.fromOperation(token)
                .map(SearchOperators::getPriority)
                .orElseThrow(() -> new IllegalArgumentException("Invalid operator: " + token));
    }
}
