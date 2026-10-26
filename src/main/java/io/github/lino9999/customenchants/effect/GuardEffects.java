package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;

final class GuardEffects {
    private final CustomEnchants plugin;

    GuardEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register(
                "summon_guardian",
                (context, selected, arguments) ->
                        plugin.guardService()
                                .spawn(
                                        context,
                                        selected,
                                        EffectRegistry.argument(arguments, 0, "IRON_GOLEM"),
                                        StateEffects.integer(arguments, 1, 8, 0, 3600),
                                        StateEffects.integer(arguments, 2, 1, 0, 128),
                                        EffectRegistry.argument(arguments, 3, ""),
                                        flag(arguments)));
        registry.register(
                "convert_guardians",
                (context, selected, arguments) ->
                        plugin.guardService()
                                .steal(context, selected, LivingEffects.other(context, selected)));
    }

    private static boolean flag(String[] arguments) {
        String value = EffectRegistry.argument(arguments, 4, "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Guard role switching requires true or false.");
        return Boolean.parseBoolean(value);
    }
}
