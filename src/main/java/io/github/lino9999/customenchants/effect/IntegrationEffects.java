package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.feature.SkillIntegrationService;

import org.bukkit.entity.Player;

final class IntegrationEffects {
    private final SkillIntegrationService integrations = new SkillIntegrationService();

    void register(EffectRegistry registry) {
        registry.register(
                "grant_mcmmo_xp",
                (context, target, args) -> {
                    if (target instanceof Player player) {
                        arguments(args, 2);
                        integrations.addExperience(
                                player, UtilityEffects.required(args, 0), amount(args, 1));
                    }
                });
    }

    private static int amount(String[] args, int index) {
        return StateEffects.integer(args, index, 0, 0, Integer.MAX_VALUE);
    }

    private static void arguments(String[] args, int expected) {
        if (args.length != expected)
            throw new IllegalArgumentException(
                    "This action requires exactly " + expected + " arguments.");
        for (int index = 0; index < expected; index++) UtilityEffects.required(args, index);
    }
}
