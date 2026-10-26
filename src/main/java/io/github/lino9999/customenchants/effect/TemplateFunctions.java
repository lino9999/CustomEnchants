package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.util.Expression;

import java.util.Locale;
import java.util.Set;
import java.util.random.RandomGenerator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateFunctions {
    public static final Set<String> NAMES =
            Set.of(
                    "rand",
                    "pick",
                    "roll",
                    "calc",
                    "round",
                    "floor",
                    "when",
                    "on_equip",
                    "on_unequip",
                    "title",
                    "shuffle");
    private static final Pattern RANGE = Pattern.compile("(-?\\d+)(?:\\.\\.(-?\\d+))?");

    public interface Environment {
        String variable(String name);

        String function(String name, String body);

        default boolean active(String name) {
            return true;
        }
    }

    private final String input;
    private final Environment environment;
    private final boolean validation;
    private int position;
    private int operations;
    private boolean skipped;

    private TemplateFunctions(String input, Environment environment, boolean validation) {
        if (input.length() > 16384)
            throw new IllegalArgumentException("Action template is too long.");
        this.input = input;
        this.environment = environment;
        this.validation = validation;
    }

    public static String render(String input, Environment environment) {
        TemplateFunctions parser = new TemplateFunctions(input, environment, false);
        String result = parser.section(null, 0, true);
        return parser.skipped ? null : result;
    }

    public static void validate(String input) {
        new TemplateFunctions(input, null, true).section(null, 0, true);
    }

    private String section(String closing, int depth, boolean enabled) {
        if (depth > 32) throw new IllegalArgumentException("Action function nesting exceeds 32.");
        StringBuilder output = new StringBuilder();
        while (position < input.length()) {
            char current = input.charAt(position);
            if (closing != null && current == ']') {
                position++;
                return output.toString();
            }
            if (current == '[') {
                int colon = input.indexOf(':', position + 1);
                if (colon > position + 1) {
                    String token = input.substring(position + 1, colon).toLowerCase(Locale.ROOT);
                    if (NAMES.contains(token)) {
                        if (++operations > 256)
                            throw new IllegalArgumentException("Too many action functions.");
                        position = colon + 1;
                        boolean active =
                                enabled && (validation || !skipped && environment.active(token));
                        String body = section(token, depth + 1, active);
                        if (validation && token.equals("when")) Conditions.validate(body);
                        String value =
                                validation ? body : active ? environment.function(token, body) : "";
                        if (value == null) skipped = true;
                        else output.append(value);
                        check(output);
                        continue;
                    }
                }
            }
            if (current == '{' && !validation && enabled && !skipped) {
                int end = input.indexOf('}', position + 1);
                if (end > position + 1) {
                    String name = input.substring(position + 1, end);
                    if (name.matches("[A-Za-z0-9_.: /-]+")) {
                        String value = environment.variable(name);
                        output.append(value == null ? "{" + name + "}" : value);
                        position = end + 1;
                        check(output);
                        continue;
                    }
                }
            }
            output.append(current);
            position++;
            check(output);
        }
        if (closing != null)
            throw new IllegalArgumentException("Unclosed action function: " + closing);
        return output.toString();
    }

    private static void check(StringBuilder result) {
        if (result.length() > 16384)
            throw new IllegalArgumentException("Expanded action exceeds 16384 characters.");
    }

    public static int randomNumber(String body, RandomGenerator random) {
        Matcher range = RANGE.matcher(body.trim());
        if (!range.matches())
            throw new IllegalArgumentException("Invalid random number range: " + body);
        long first = Long.parseLong(range.group(1));
        long last = range.group(2) == null ? first : Long.parseLong(range.group(2));
        if (first < Integer.MIN_VALUE || last > Integer.MAX_VALUE || first > last)
            throw new IllegalArgumentException("Random range must contain ordered integer bounds.");
        return (int) random.nextLong(first, last + 1);
    }

    public static String textFunction(String name, String body, RandomGenerator random) {
        return switch (name) {
            case "calc" -> Double.toString(Expression.evaluate(body));
            case "round" -> Long.toString(Math.round(Expression.evaluate(body)));
            case "floor" -> Long.toString((long) Expression.evaluate(body));
            case "title" -> title(body);
            case "pick" -> {
                String[] words = body.split(",", -1);
                yield words[random.nextInt(words.length)];
            }
            case "shuffle" -> {
                int[] points = body.codePoints().toArray();
                for (int i = points.length - 1; i > 0; i--) {
                    int other = random.nextInt(i + 1);
                    int old = points[i];
                    points[i] = points[other];
                    points[other] = old;
                }
                yield new String(points, 0, points.length);
            }
            default -> throw new IllegalArgumentException("Unknown text function: " + name);
        };
    }

    private static String title(String input) {
        String[] words = input.toLowerCase(Locale.ROOT).replace('_', ' ').split(" +");
        StringBuilder output = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!output.isEmpty()) output.append(' ');
            output.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return output.toString();
    }
}
