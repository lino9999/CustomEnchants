package io.github.lino9999.customenchants.util;

import java.util.concurrent.ThreadLocalRandom;

public final class Numbers {
    private Numbers() {}

    public static boolean roll(double percent) {
        return percent >= 100
                || percent > 0 && ThreadLocalRandom.current().nextDouble(100) < percent;
    }

    public static int integer(String value, int minimum, int maximum) {
        int result;
        try {
            result = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Expected a whole number.");
        }
        if (result < minimum || result > maximum)
            throw new IllegalArgumentException(
                    "Number must be between " + minimum + " and " + maximum + ".");
        return result;
    }

    public static int random(int min, int max) {
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
