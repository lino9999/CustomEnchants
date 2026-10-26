package io.github.lino9999.customenchants.util;

import org.bukkit.ChatColor;

public final class Text {
    private Text() {}

    public static String color(String value) {
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    public static String plain(String value) {
        return ChatColor.stripColor(color(value));
    }

    public static String roman(int number) {
        if (number < 1 || number > 20) return Integer.toString(number);
        String[] tens = {"", "X", "XX"};
        String[] ones = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return tens[number / 10] + ones[number % 10];
    }
}
