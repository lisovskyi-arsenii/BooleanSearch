package enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum SearchOperators {
    OR("OR", 1),
    AND("AND", 2),
    NOT("NOT", 3),

    LEFT_PAREN("(", -1),
    RIGHT_PAREN(")", -1);

    private final String operation;
    private final int priority;

    SearchOperators(String operation, int priority) {
        this.operation = operation;
        this.priority = priority;
    }

    public static Optional<SearchOperators> fromOperation(String operation) {
        return Arrays.stream(SearchOperators.values())
                .filter(op -> op.operation.equals(operation))
                .findFirst();
    }

    public static boolean isOperator(String token) {
        return SearchOperators.fromOperation(token).isPresent();
    }

    public static boolean isLogicalOperator(String token) {
        return fromOperation(token)
                .map(op -> op == AND || op == OR || op == NOT)
                .orElse(false);
    }

    public static boolean isParenthesisOperator(String token) {
        return fromOperation(token)
                .map(op -> op == LEFT_PAREN || op == RIGHT_PAREN)
                .orElse(false);
    }

    public boolean isLogicalOperator() {
        return this == AND || this == OR || this == NOT;
    }

    public boolean isParenthesis() {
        return this == LEFT_PAREN || this == RIGHT_PAREN;
    }
}
