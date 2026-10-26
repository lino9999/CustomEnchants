package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;

import org.bukkit.Material;

final class SpawnEffects {
    private final CustomEnchants plugin;

    SpawnEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register(
                "summon_mob",
                (c, t, a) ->
                        plugin.spawns().entity(c, t.getLocation(), UtilityEffects.required(a, 0)));
        registry.registerLocation(
                "summon_mob",
                (c, t, a) -> plugin.spawns().entity(c, t, UtilityEffects.required(a, 0)));
        registry.register(
                "fire_projectile",
                (c, t, a) -> plugin.spawns().projectile(c, t, UtilityEffects.required(a, 0)));
        registry.register("create_explosion", (c, t, a) -> explode(c, t.getLocation(), a));
        registry.registerLocation("create_explosion", this::explode);
        registry.register(
                "rain_falling_blocks", (c, t, a) -> rain(c, t.getLocation().add(0, 1, 0), a));
        registry.registerLocation("rain_falling_blocks", this::rain);
    }

    private void explode(EffectContext context, org.bukkit.Location location, String[] args) {
        UtilityEffects.required(args, 0);
        plugin.spawns()
                .explode(
                        context,
                        location,
                        EffectRegistry.number(args, 0, 0),
                        flag(args, 1),
                        flag(args, 2));
    }

    private void rain(EffectContext context, org.bukkit.Location location, String[] args) {
        Material material = Material.matchMaterial(UtilityEffects.required(args, 0));
        if (material == null) throw new IllegalArgumentException("Unknown falling-block material.");
        UtilityEffects.required(args, 1);
        plugin.spawns().rain(context, location, material, EffectRegistry.number(args, 1, 0));
    }

    private static boolean flag(String[] args, int index) {
        String value = UtilityEffects.required(args, index);
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Explosion flags must be true or false.");
        return Boolean.parseBoolean(value);
    }
}
