package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.util.Expression;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record TargetSelector(String name, Map<String, String> arguments) {
    public static final Set<String> NAMES =
            Set.of(
                    "self",
                    "attacker",
                    "defender",
                    "offset",
                    "all_players",
                    "area",
                    "sight_block",
                    "hit_block",
                    "sight_entities",
                    "eye_pos",
                    "nearest_player",
                    "player_by_name",
                    "cube_area",
                    "tunnel_area",
                    "vein_area");
    private static final Pattern SYNTAX = Pattern.compile("([a-z_]+)(?:\\[([^\\]]*)])?");

    public TargetSelector {
        arguments = Map.copyOf(arguments);
    }

    public static TargetSelector parse(String input) {
        Matcher match = SYNTAX.matcher(input.trim());
        if (!match.matches())
            throw new IllegalArgumentException("Invalid target scope: " + input);
        String name = match.group(1).toLowerCase(Locale.ROOT);
        if (!NAMES.contains(name))
            throw new IllegalArgumentException("Unknown target scope: " + name);
        Map<String, String> arguments = new LinkedHashMap<>();
        if (match.group(2) != null && !match.group(2).isBlank())
            for (String argument : match.group(2).split(",")) {
                String[] pair = argument.split("=", 2);
                if (pair.length != 2 || pair[1].isBlank())
                    throw new IllegalArgumentException("Invalid scope argument: " + argument);
                String key =
                        switch (pair[0].trim().toLowerCase(Locale.ROOT)) {
                            case "any_tool" -> "any_tool";
                            case "size" -> "size";
                            case "bounds" -> "bounds";
                            case "filter" -> "filter";
                            case "max" -> "max";
                            case "range" -> "range";
                            case "max_blocks" -> "max_blocks";
                            case "points" -> "points";
                            case "cone" -> "cone";
                            case "direction" -> "direction";
                            case "x", "y", "z" -> pair[0].trim().toLowerCase(Locale.ROOT);
                            default ->
                                    throw new IllegalArgumentException(
                                            "Unknown scope argument: " + pair[0]);
                        };
                if (arguments.putIfAbsent(key, pair[1].trim()) != null)
                    throw new IllegalArgumentException("Duplicate scope argument: " + key);
            }
        return new TargetSelector(name, arguments);
    }

    public String text(String key, String fallback) {
        return arguments.getOrDefault(key, fallback);
    }

    public double number(String key, double fallback) {
        return arguments.containsKey(key) ? Expression.evaluate(arguments.get(key)) : fallback;
    }

    public boolean flag(String key, boolean fallback) {
        String value = arguments.get(key);
        if (value == null) return fallback;
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Expected a boolean scope argument: " + key);
        return Boolean.parseBoolean(value);
    }
}
