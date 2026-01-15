package query;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


// Розпізнає оператори: AND, OR, NOT
public class QueryParser {
    private static final Pattern AND_PATTERN = Pattern.compile("(.+?)\\s+AND\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern OR_PATTERN = Pattern.compile("(.+?)\\s+OR\\s+(.+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOT_PATTERN = Pattern.compile("(.+?)\\s+NOT\\s+(.+)", Pattern.CASE_INSENSITIVE);

    public static QueryType parseQueryType(String query) {
        if (AND_PATTERN.matcher(query).matches()) {
            return QueryType.AND;
        } else if (OR_PATTERN.matcher(query).matches()) {
            return QueryType.OR;
        } else if (NOT_PATTERN.matcher(query).matches()) {
            return QueryType.NOT;
        }
        return QueryType.SIMPLE;
    }

    public static String[] extractTerms(String query) {
        Matcher andMatcher = AND_PATTERN.matcher(query);
        if (andMatcher.matches()) {
            return new String[]{andMatcher.group(1).trim(), andMatcher.group(2).trim()};
        }

        Matcher orMatcher = OR_PATTERN.matcher(query);
        if (orMatcher.matches()) {
            return new String[]{orMatcher.group(1).trim(), orMatcher.group(2).trim()};
        }

        Matcher notMatcher = NOT_PATTERN.matcher(query);
        if (notMatcher.matches()) {
            return new String[]{notMatcher.group(1).trim(), notMatcher.group(2).trim()};
        }

        return new String[]{query.trim()};
    }

    public enum QueryType {
        SIMPLE, AND, OR, NOT
    }
}
