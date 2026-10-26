package io.github.lino9999.customenchants.util;

import org.bukkit.entity.Player;

public final class Experience {
    private Experience() {}

    public static int total(Player player) {
        int level = player.getLevel();
        double base =
                level <= 16
                        ? level * level + 6.0 * level
                        : level <= 31
                                ? 2.5 * level * level - 40.5 * level + 360
                                : 4.5 * level * level - 162.5 * level + 2220;
        return (int)
                Math.min(
                        Integer.MAX_VALUE,
                        base + Math.round(player.getExp() * player.getExpToLevel()));
    }

    public static void spend(Player player, int cost) {
        int total = total(player);
        if (total < cost)
            throw new IllegalArgumentException("You need " + cost + " experience points.");
        if (cost == 0) return;
        player.setExp(0);
        player.setLevel(0);
        player.setTotalExperience(0);
        player.giveExp(total - cost);
    }
}
