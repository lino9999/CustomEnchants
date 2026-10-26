package io.github.lino9999.customenchants.util;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BooleanExpression {
    private static final Pattern COMPARISON =
            Pattern.compile(
                    "^(.+?)\\s*(\\bmatchesregex\\b|\\bcontains\\b|>=|<=|!=|==|=|>|<)\\s*(.+)$",
                    Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    private BooleanExpression() {}

    public static void validate(String expression) {
        parse(expression, 0);
    }

    public static boolean evaluate(String expression) {
        return evaluate(expression, UnaryOperator.identity());
    }

    public static boolean evaluate(String expression, UnaryOperator<String> resolver) {
        return parse(expression, 0).evaluate(resolver);
    }

    private interface Node {
        boolean evaluate(UnaryOperator<String> resolver);
    }

    private record Junction(Node left, Node right, boolean conjunction) implements Node {
        @Override
        public boolean evaluate(UnaryOperator<String> resolver) {
            boolean first = left.evaluate(resolver);
            boolean second = right.evaluate(resolver);
            return conjunction ? first && second : first || second;
        }
    }

    private record Comparison(String left, String operator, String right) implements Node {
        @Override
        public boolean evaluate(UnaryOperator<String> resolver) {
            String first = unquote(resolver.apply(left)).trim();
            String second = unquote(resolver.apply(right)).trim();
            return switch (operator.toLowerCase(Locale.ROOT)) {
                case "contains" -> {
                    String haystack = first.replace(" ", "").toLowerCase(Locale.ROOT);
                    boolean found = false;
                    for (String needle : second.split("[,;]", -1))
                        found |=
                                haystack.contains(needle.replace(" ", "").toLowerCase(Locale.ROOT));
                    yield found;
                }
                case "matchesregex" -> {
                    if (second.length() > 512 || first.length() > 4096)
                        throw new IllegalArgumentException("Regular expression input is too long.");
                    yield Pattern.compile(second, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE)
                            .matcher(first)
                            .find();
                }
                case "=", "==", "!=" -> {
                    boolean equal;
                    try {
                        equal = new BigDecimal(first).compareTo(new BigDecimal(second)) == 0;
                    } catch (NumberFormatException error) {
                        equal = first.replace(" ", "").equalsIgnoreCase(second.replace(" ", ""));
                    }
                    yield operator.equals("!=") ? !equal : equal;
                }
                case ">" -> Expression.evaluate(first) > Expression.evaluate(second);
                case "<" -> Expression.evaluate(first) < Expression.evaluate(second);
                case ">=" -> Expression.evaluate(first) >= Expression.evaluate(second);
                case "<=" -> Expression.evaluate(first) <= Expression.evaluate(second);
                default -> throw new IllegalArgumentException("Unknown comparison operator.");
            };
        }
    }

    private static Node parse(String input, int depth) {
        if (input.length() > 4096 || depth > 32)
            throw new IllegalArgumentException("Condition complexity exceeds its limit.");
        String expression = input.trim();
        if (expression.isEmpty()) throw new IllegalArgumentException("Empty condition expression.");
        while (expression.startsWith("(") && closing(expression, 0) == expression.length() - 1)
            expression = expression.substring(1, expression.length() - 1).trim();
        int split = logical(expression, false);
        if (split >= 0)
            return new Junction(
                    parse(expression.substring(0, split), depth + 1),
                    parse(expression.substring(split + 2), depth + 1),
                    false);
        split = logical(expression, true);
        if (split >= 0)
            return new Junction(
                    parse(expression.substring(0, split), depth + 1),
                    parse(
                            expression.substring(
                                    split + (expression.startsWith("&&", split) ? 2 : 3)),
                            depth + 1),
                    true);
        Matcher comparison = COMPARISON.matcher(expression);
        if (!comparison.matches())
            throw new IllegalArgumentException("Invalid comparison: " + expression);
        return new Comparison(
                comparison.group(1).trim(), comparison.group(2).trim(), comparison.group(3).trim());
    }

    private static int logical(String expression, boolean conjunction) {
        int depth = 0;
        char quote = 0;
        for (int i = 0; i < expression.length(); i++) {
            char current = expression.charAt(i);
            if (quote != 0) {
                if (current == quote && (i == 0 || expression.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (current == '\\' && i + 1 < expression.length()) {
                i++;
                continue;
            }
            if ((current == '\'' || current == '"') && quoteStart(expression, i)) {
                quote = current;
                continue;
            }
            if (current == '{') {
                int end = expression.indexOf('}', i + 1);
                if (end > i + 1) {
                    i = end;
                    continue;
                }
            }
            if (current == '(') depth++;
            if (current == ')') {
                if (--depth < 0)
                    throw new IllegalArgumentException("Unbalanced condition parentheses.");
            }
            if (depth != 0) continue;
            if (conjunction && expression.startsWith("&&", i)) return i;
            if (conjunction
                    && i > 0
                    && i + 3 < expression.length()
                    && expression.regionMatches(true, i, "and", 0, 3)
                    && Character.isWhitespace(expression.charAt(i - 1))
                    && Character.isWhitespace(expression.charAt(i + 3))) return i;
            if (!conjunction && expression.startsWith("||", i)) return i;
            if (!conjunction
                    && i > 0
                    && i + 2 < expression.length()
                    && expression.regionMatches(true, i, "or", 0, 2)
                    && Character.isWhitespace(expression.charAt(i - 1))
                    && Character.isWhitespace(expression.charAt(i + 2))) return i;
        }
        if (depth != 0 || quote != 0)
            throw new IllegalArgumentException("Unclosed condition group or quote.");
        return -1;
    }

    private static int closing(String expression, int start) {
        int depth = 0;
        char quote = 0;
        for (int i = start; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (quote != 0) {
                if (c == quote && (i == 0 || expression.charAt(i - 1) != '\\')) quote = 0;
                continue;
            }
            if (c == '\\' && i + 1 < expression.length()) {
                i++;
                continue;
            }
            if ((c == '\'' || c == '"') && quoteStart(expression, i)) {
                quote = c;
                continue;
            }
            if (c == '(') depth++;
            if (c == ')' && --depth == 0) return i;
        }
        throw new IllegalArgumentException("Unclosed condition parenthesis.");
    }

    private static String unquote(String input) {
        String value = input.trim();
        if (value.length() >= 2
                && (value.charAt(0) == '\'' || value.charAt(0) == '"')
                && value.charAt(value.length() - 1) == value.charAt(0))
            return value.substring(1, value.length() - 1);
        return value;
    }

    private static boolean quoteStart(String input, int index) {
        return index == 0
                || Character.isWhitespace(input.charAt(index - 1))
                || "=<>!(".indexOf(input.charAt(index - 1)) >= 0;
    }
}
