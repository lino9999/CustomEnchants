package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.util.BooleanExpression;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Conditions {
    private static final Pattern ENDING =
            Pattern.compile(
                    "(allow|cancel|force|pass)|rate\\s*([+-])\\s*(\\d+(?:\\.\\d+)?)",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATOR = Pattern.compile("\\s*=>\\s*");
    private final Variables variables;

    public Conditions(Variables variables) {
        this.variables = variables;
    }

    public record Rule(String expression, String action, double amount) {}

    public static Rule parse(String input) {
        input = input.trim();
        if (input.length() > 4096) throw new IllegalArgumentException("Condition is too long.");
        Matcher separator = SEPARATOR.matcher(input);
        if (!separator.find())
            throw new IllegalArgumentException(
                    "Condition requires an expression and outcome separated by '=>'.");
        String expression = input.substring(0, separator.start()).trim();
        String action = input.substring(separator.end()).trim();
        Matcher ending = ENDING.matcher(action);
        if (!ending.matches())
            throw new IllegalArgumentException("Invalid condition outcome: " + action);
        BooleanExpression.validate(expression);
        return ending.group(1) != null
                ? new Rule(expression, ending.group(1).toLowerCase(Locale.ROOT), 0)
                : new Rule(
                        expression,
                        "rate",
                        Double.parseDouble(ending.group(2) + ending.group(3)));
    }

    public static void validate(String condition) {
        Variables.validate(condition);
        parse(condition);
    }

    public boolean allow(List<String> conditions, EffectContext context) {
        double bonus = 0;
        Double forced = null;
        for (String input : conditions) {
            Rule rule = parse(input);
            boolean matches =
                    BooleanExpression.evaluate(
                            rule.expression(), text -> variables.resolve(text, context));
            if (rule.action().equals("allow") && !matches) return false;
            if (!matches) continue;
            switch (rule.action()) {
                case "cancel" -> {
                    return false;
                }
                case "force" -> {
                    forced = 100.0;
                    bonus = 0;
                }
                case "rate" -> {
                    if (forced == null) bonus += rule.amount();
                    else forced += rule.amount();
                }
                default -> {}
            }
        }
        context.values.put("chance_bonus", bonus);
        if (forced != null) context.values.put("chance_override", forced);
        return true;
    }

    public boolean inline(String condition, EffectContext context) {
        Rule rule = parse(condition);
        boolean matches =
                BooleanExpression.evaluate(
                        rule.expression(), text -> variables.resolve(text, context));
        return rule.action().equals("allow")
                ? matches
                : !rule.action().equals("cancel") || !matches;
    }

    public boolean evaluate(String expression) {
        return BooleanExpression.evaluate(expression);
    }
}
