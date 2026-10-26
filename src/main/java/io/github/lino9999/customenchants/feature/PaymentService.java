package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.config.Settings;
import io.github.lino9999.customenchants.util.Experience;

import org.bukkit.entity.Player;

public final class PaymentService {
    private PaymentService() {}

    public static void charge(Player player, Settings.Group group) {
        switch (group.currency()) {
            case "XP" -> Experience.spend(player, group.cost());
            case "LEVELS" -> {
                if (player.getLevel() < group.cost())
                    throw new IllegalArgumentException(
                            "You need " + group.cost() + " experience levels.");
                player.giveExpLevels(-group.cost());
            }
            case "MONEY" -> new EconomyService().withdraw(player, group.cost());
            default -> throw new IllegalArgumentException("Unknown currency.");
        }
    }
}
