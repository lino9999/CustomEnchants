package io.github.lino9999.customenchants.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public final class Expression {
    public static final Set<String> FUNCTIONS =
            Set.of(
                    "FACT", "NOT", "IF", "RANDOM", "SINR", "COSR", "TANR", "COTR", "SECR", "CSCR",
                    "SIN", "COS", "TAN", "COT", "SEC", "CSC", "ASINR", "ACOSR", "ATANR", "ACOTR",
                    "ATAN2R", "ASIN", "ACOS", "ATAN", "ACOT", "ATAN2", "SINH", "COSH", "TANH",
                    "SECH", "CSCH", "COTH", "ASINH", "ACOSH", "ATANH", "RAD", "DEG", "MAX", "MIN",
                    "ABS", "LOG", "LOG10", "ROUND", "FLOOR", "CEILING", "SQRT");
    private static final MathContext PRECISION = MathContext.DECIMAL128;
    private final String input;
    private int position;
    private int operations;

    private Expression(String input) {
        if (input.length() > 4096) throw new IllegalArgumentException("Expression is too long.");
        this.input = input;
    }

    public static double evaluate(String input) {
        try {
            Expression parser = new Expression(conditionalTags(input, 0));
            Node root = parser.expression(0, 0);
            parser.space();
            if (parser.position != parser.input.length())
                throw new IllegalArgumentException(
                        "Unexpected expression input at " + parser.position + ".");
            BigDecimal result = required(root.evaluate());
            double value = result.doubleValue();
            if (!Double.isFinite(value))
                throw new IllegalArgumentException("Expression result must be finite.");
            return value;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(
                    "Invalid arithmetic expression: " + error.getMessage(), error);
        }
    }

    private interface Node {
        BigDecimal evaluate();
    }

    private record Value(BigDecimal value) implements Node {
        @Override
        public BigDecimal evaluate() {
            return value;
        }
    }

    private record Unary(Node child, boolean negative) implements Node {
        @Override
        public BigDecimal evaluate() {
            BigDecimal value = required(child.evaluate());
            return negative ? value.negate(PRECISION) : value;
        }
    }

    private record Binary(Node left, String operator, Node right) implements Node {
        @Override
        public BigDecimal evaluate() {
            BigDecimal first = left.evaluate();
            if (operator.equals("&&")) return bool(truth(first) && truth(right.evaluate()));
            if (operator.equals("||")) return bool(truth(first) || truth(right.evaluate()));
            BigDecimal second = right.evaluate();
            if (operator.equals("=") || operator.equals("=="))
                return bool(
                        first == null || second == null
                                ? first == second
                                : first.compareTo(second) == 0);
            if (operator.equals("!=") || operator.equals("<>"))
                return bool(
                        first == null || second == null
                                ? first != second
                                : first.compareTo(second) != 0);
            required(first);
            required(second);
            return switch (operator) {
                case "+" -> first.add(second, PRECISION);
                case "-" -> first.subtract(second, PRECISION);
                case "*" -> first.multiply(second, PRECISION);
                case "/" -> first.divide(second, PRECISION);
                case "%" -> first.remainder(second, PRECISION);
                case "^" -> power(first, second);
                case ">" -> bool(first.compareTo(second) > 0);
                case "<" -> bool(first.compareTo(second) < 0);
                case ">=" -> bool(first.compareTo(second) >= 0);
                case "<=" -> bool(first.compareTo(second) <= 0);
                default ->
                        throw new IllegalArgumentException(
                                "Unknown arithmetic operator: " + operator);
            };
        }
    }

    private record Function(String name, List<Node> arguments) implements Node {
        @Override
        public BigDecimal evaluate() {
            int expected =
                    switch (name) {
                        case "RANDOM" -> 0;
                        case "IF" -> 3;
                        case "ROUND", "ATAN2", "ATAN2R" -> 2;
                        case "MIN", "MAX" -> -1;
                        default -> 1;
                    };
            if (expected >= 0 ? arguments.size() != expected : arguments.isEmpty())
                throw new IllegalArgumentException("Invalid argument count for " + name + ".");
            if (name.equals("IF"))
                return arguments.get(truth(arguments.get(0).evaluate()) ? 1 : 2).evaluate();
            if (name.equals("RANDOM"))
                return BigDecimal.valueOf(ThreadLocalRandom.current().nextDouble());
            List<BigDecimal> values =
                    arguments.stream().map(Node::evaluate).map(Expression::required).toList();
            BigDecimal first = values.get(0);
            double number = first.doubleValue();
            return switch (name) {
                case "NOT" -> bool(!truth(first));
                case "FACT" -> factorial(first.intValue());
                case "MIN" -> values.stream().min(BigDecimal::compareTo).orElseThrow();
                case "MAX" -> values.stream().max(BigDecimal::compareTo).orElseThrow();
                case "ABS" -> first.abs(PRECISION);
                case "ROUND" -> first.setScale(boundedScale(values.get(1)), RoundingMode.HALF_UP);
                case "FLOOR" -> first.setScale(0, RoundingMode.FLOOR);
                case "CEILING" -> first.setScale(0, RoundingMode.CEILING);
                case "SQRT" -> first.sqrt(PRECISION);
                case "LOG" -> decimal(Math.log(number));
                case "LOG10" -> decimal(Math.log10(number));
                case "RAD" -> decimal(Math.toRadians(number));
                case "DEG" -> decimal(Math.toDegrees(number));
                case "SINR" -> decimal(Math.sin(number));
                case "COSR" -> decimal(Math.cos(number));
                case "TANR" -> decimal(Math.tan(number));
                case "COTR" -> decimal(1 / Math.tan(number));
                case "SECR" -> decimal(1 / Math.cos(number));
                case "CSCR" -> decimal(1 / Math.sin(number));
                case "SIN" -> decimal(Math.sin(Math.toRadians(number)));
                case "COS" -> decimal(Math.cos(Math.toRadians(number)));
                case "TAN" -> decimal(Math.tan(Math.toRadians(number)));
                case "COT" -> decimal(1 / Math.tan(Math.toRadians(number)));
                case "SEC" -> decimal(1 / Math.cos(Math.toRadians(number)));
                case "CSC" -> decimal(1 / Math.sin(Math.toRadians(number)));
                case "ASINR" -> decimal(Math.asin(number));
                case "ACOSR" -> decimal(Math.acos(number));
                case "ATANR" -> decimal(Math.atan(number));
                case "ACOTR" -> decimal(Math.atan(1 / nonzero(number)));
                case "ATAN2R" -> decimal(Math.atan2(number, values.get(1).doubleValue()));
                case "ASIN" -> decimal(Math.toDegrees(Math.asin(number)));
                case "ACOS" -> decimal(Math.toDegrees(Math.acos(number)));
                case "ATAN" -> decimal(Math.toDegrees(Math.atan(number)));
                case "ACOT" -> decimal(Math.toDegrees(Math.atan(1 / nonzero(number))));
                case "ATAN2" ->
                        decimal(Math.toDegrees(Math.atan2(number, values.get(1).doubleValue())));
                case "SINH" -> decimal(Math.sinh(number));
                case "COSH" -> decimal(Math.cosh(number));
                case "TANH" -> decimal(Math.tanh(number));
                case "SECH" -> decimal(1 / Math.cosh(number));
                case "CSCH" -> decimal(1 / Math.sinh(number));
                case "COTH" -> decimal(1 / Math.tanh(number));
                case "ASINH" -> decimal(Math.log(number + Math.sqrt(number * number + 1)));
                case "ACOSH" -> decimal(Math.log(number + Math.sqrt(number * number - 1)));
                case "ATANH" -> decimal(0.5 * Math.log((1 + number) / (1 - number)));
                default -> throw new IllegalArgumentException("Unknown math function: " + name);
            };
        }
    }

    private Node expression(int minimum, int depth) {
        if (depth > 64 || ++operations > 1024)
            throw new IllegalArgumentException("Expression complexity exceeds its limit.");
        Node left = atom(depth + 1);
        while (true) {
            space();
            String operator = operator();
            int priority = precedence(operator);
            if (priority < minimum) return left;
            position += operator.length();
            Node right = expression(priority + (operator.equals("^") ? 0 : 1), depth + 1);
            left = new Binary(left, operator, right);
        }
    }

    private Node atom(int depth) {
        space();
        if (take('+')) return new Unary(expression(60, depth + 1), false);
        if (take('-')) return new Unary(expression(60, depth + 1), true);
        if (take('(')) {
            Node result = expression(0, depth + 1);
            if (!take(')')) throw new IllegalArgumentException("Unclosed arithmetic group.");
            return result;
        }
        if (position < input.length()
                && (Character.isLetter(input.charAt(position)) || input.charAt(position) == '_')) {
            int start = position++;
            while (position < input.length()
                    && (Character.isLetterOrDigit(input.charAt(position))
                            || input.charAt(position) == '_')) position++;
            String name = input.substring(start, position).toUpperCase(Locale.ROOT);
            if (!take('('))
                return switch (name) {
                    case "PI" -> new Value(BigDecimal.valueOf(Math.PI));
                    case "E" -> new Value(BigDecimal.valueOf(Math.E));
                    case "TRUE" -> new Value(BigDecimal.ONE);
                    case "FALSE" -> new Value(BigDecimal.ZERO);
                    case "NULL" -> new Value(null);
                    default -> throw new IllegalArgumentException("Unknown math constant: " + name);
                };
            if (!FUNCTIONS.contains(name))
                throw new IllegalArgumentException("Unknown math function: " + name);
            List<Node> arguments = new ArrayList<>();
            if (!take(')')) {
                do {
                    arguments.add(expression(0, depth + 1));
                    if (arguments.size() > 128)
                        throw new IllegalArgumentException("Too many math arguments.");
                } while (take(','));
                if (!take(')'))
                    throw new IllegalArgumentException("Unclosed math function: " + name);
            }
            return new Function(name, List.copyOf(arguments));
        }
        int start = position;
        if (position + 2 < input.length() && input.regionMatches(true, position, "0x", 0, 2)) {
            position += 2;
            int digits = position;
            while (position < input.length() && Character.digit(input.charAt(position), 16) >= 0)
                position++;
            if (digits == position)
                throw new IllegalArgumentException("Invalid hexadecimal literal.");
            return new Value(new BigDecimal(new BigInteger(input.substring(digits, position), 16)));
        }
        while (position < input.length()
                && (Character.isDigit(input.charAt(position)) || input.charAt(position) == '.'))
            position++;
        if (position < input.length()
                && (input.charAt(position) == 'e' || input.charAt(position) == 'E')) {
            position++;
            if (position < input.length()
                    && (input.charAt(position) == '+' || input.charAt(position) == '-')) position++;
            while (position < input.length() && Character.isDigit(input.charAt(position)))
                position++;
        }
        if (start == position)
            throw new IllegalArgumentException("Expected an arithmetic value at " + position + ".");
        BigDecimal number = new BigDecimal(input.substring(start, position));
        if (Math.abs((long) number.scale()) > 10000)
            throw new IllegalArgumentException("Numeric exponent is too large.");
        return new Value(number);
    }

    private String operator() {
        for (String candidate :
                List.of(
                        "&&", "||", ">=", "<=", "!=", "<>", "==", "+", "-", "*", "/", "%", "^", ">",
                        "<", "=")) if (input.startsWith(candidate, position)) return candidate;
        return "";
    }

    private static int precedence(String operator) {
        return switch (operator) {
            case "||" -> 2;
            case "&&" -> 4;
            case "=", "==", "!=", "<>" -> 7;
            case ">", "<", ">=", "<=" -> 10;
            case "+", "-" -> 20;
            case "*", "/", "%" -> 30;
            case "^" -> 40;
            default -> -1;
        };
    }

    private boolean take(char wanted) {
        space();
        if (position < input.length() && input.charAt(position) == wanted) {
            position++;
            return true;
        }
        return false;
    }

    private void space() {
        while (position < input.length() && Character.isWhitespace(input.charAt(position)))
            position++;
    }

    private static BigDecimal required(BigDecimal value) {
        if (value == null) throw new IllegalArgumentException("Null is not a numeric value.");
        return value;
    }

    private static boolean truth(BigDecimal value) {
        return value != null && value.signum() != 0;
    }

    private static BigDecimal bool(boolean value) {
        return value ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private static BigDecimal decimal(double value) {
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Math function result must be finite.");
        return BigDecimal.valueOf(value);
    }

    private static double nonzero(double value) {
        if (value == 0)
            throw new IllegalArgumentException("Math function argument must not be zero.");
        return value;
    }

    private static int boundedScale(BigDecimal value) {
        int scale = value.intValue();
        if (Math.abs((long) scale) > 1000)
            throw new IllegalArgumentException("Round scale is too large.");
        return scale;
    }

    private static BigDecimal factorial(int value) {
        if (value < 0 || value > 4096)
            throw new IllegalArgumentException("Factorial argument must be between 0 and 4096.");
        BigDecimal result = BigDecimal.ONE;
        for (int i = 2; i <= value; i++) result = result.multiply(BigDecimal.valueOf(i), PRECISION);
        return result;
    }

    private static BigDecimal power(BigDecimal value, BigDecimal exponent) {
        if (exponent.stripTrailingZeros().scale() <= 0) {
            int integral = exponent.intValueExact();
            if (Math.abs((long) integral) > 10000)
                throw new IllegalArgumentException("Power exponent is too large.");
            BigDecimal result = value.pow(Math.abs(integral), PRECISION);
            return integral < 0 ? BigDecimal.ONE.divide(result, PRECISION) : result;
        }
        return decimal(Math.pow(value.doubleValue(), exponent.doubleValue()));
    }

    private static String conditionalTags(String input, int depth) {
        if (depth > 32 || input.length() > 4096)
            throw new IllegalArgumentException("Conditional math complexity exceeds its limit.");
        int start = input.indexOf("<if>");
        if (start < 0) return input;
        int nesting = 1, end = start + 4;
        while (nesting > 0) {
            int open = input.indexOf("<if>", end), close = input.indexOf("</if>", end);
            if (close < 0)
                throw new IllegalArgumentException("Unclosed conditional math function.");
            if (open >= 0 && open < close) {
                nesting++;
                end = open + 4;
            } else {
                nesting--;
                end = close + 5;
            }
        }
        String body = input.substring(start + 4, end - 5);
        int question = body.indexOf('?');
        if (question < 0) throw new IllegalArgumentException("Conditional math requires '?'.");
        int separator = -1;
        nesting = 0;
        for (int i = question + 1; i < body.length(); i++) {
            if (body.startsWith("<if>", i)) {
                nesting++;
                i += 3;
            } else if (body.startsWith("</if>", i)) {
                nesting--;
                i += 4;
            } else if (body.charAt(i) == ':' && nesting == 0) {
                separator = i;
                break;
            }
        }
        if (separator < 0) throw new IllegalArgumentException("Conditional math requires ':'.");
        boolean selected;
        try {
            selected = evaluate(body.substring(0, question)) != 0;
        } catch (IllegalArgumentException error) {
            selected = BooleanExpression.evaluate(body.substring(0, question));
        }
        String branch =
                selected ? body.substring(question + 1, separator) : body.substring(separator + 1);
        return conditionalTags(
                input.substring(0, start)
                        + conditionalTags(branch, depth + 1)
                        + input.substring(end),
                depth + 1);
    }
}
