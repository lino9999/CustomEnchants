package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Locale;

final class LivingEffects {
    private final CustomEnchants plugin;

    LivingEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register(
                "grant_potion",
                (c, t, a) -> {
                    if (c.removal()) return;
                    PotionEffectType type = potion(EffectRegistry.argument(a, 0, "SPEED"));
                    if (type == null) throw new IllegalArgumentException("Unknown potion: " + a[0]);
                    int amplifier = StateEffects.integer(a, 1, 0, 0, 255);
                    int duration = StateEffects.integer(a, 2, 100, -1, 72000);
                    boolean passive =
                            c.trigger.equals("ARMOR_PASSIVE") || c.trigger.equals("HAND_PASSIVE");
                    if (passive) plugin.states().potion(c, t, type, amplifier, -1, false);
                    else if (plugin.canAffect(c.owner, t)) {
                        if (a.length > 3) {
                            if (!a[3].equalsIgnoreCase("true") && !a[3].equalsIgnoreCase("false"))
                                throw new IllegalArgumentException(
                                        "Potion application argument must be true or false.");
                            if (!Boolean.parseBoolean(a[3])) return;
                        }
                        t.addPotionEffect(new PotionEffect(type, duration, amplifier));
                    }
                });
        EffectRegistry.Action cure =
                (c, t, a) -> {
                    PotionEffectType type = potion(EffectRegistry.argument(a, 0, "POISON"));
                    if (type != null && plugin.canAffect(c.owner, t)) plugin.states().cure(t, type);
                };
        registry.register("clear_potion", cure);
        registry.register("purge_potion", cure);
        registry.register(
                "send_message", (c, t, a) -> t.sendMessage(Text.color(String.join(" | ", a))));
        registry.register(
                "restore_hunger",
                (c, t, a) -> {
                    if (t instanceof Player p)
                        p.setFoodLevel(
                                (int)
                                        Math.clamp(
                                                p.getFoodLevel() + EffectRegistry.number(a, 0, 1),
                                                0,
                                                20));
                });
        registry.register(
                "restore_air",
                (c, t, a) ->
                        t.setRemainingAir(
                                (int)
                                        Math.clamp(
                                                t.getRemainingAir()
                                                        + EffectRegistry.number(
                                                                a, 0, t.getMaximumAir()),
                                                0,
                                                t.getMaximumAir())));
        registry.register(
                "ignite_target",
                (c, t, a) -> {
                    if (plugin.canAffect(c.owner, t))
                        t.setFireTicks(
                                (int) Math.clamp(EffectRegistry.number(a, 0, 1) * 20, 0, 1200));
                });
        registry.register("extinguish_target", (c, t, a) -> t.setFireTicks(0));
        registry.register(
                "propel_entity",
                (c, t, a) -> {
                    double strength = Math.clamp(EffectRegistry.number(a, 1, 1) / 4, -4, 4);
                    Vector vector =
                            a.length > 0 && a[0].equals("UP")
                                    ? new Vector(0, strength, 0)
                                    : t.getLocation().getDirection().multiply(strength);
                    if (plugin.canAffect(c.owner, t)) t.setVelocity(vector);
                });
        registry.register(
                "push_away", (c, t, a) -> pull(c, t, EffectRegistry.number(a, 0, 1), false));
        registry.register(
                "draw_closer", (c, t, a) -> pull(c, t, EffectRegistry.number(a, 0, 1), true));
        registry.register(
                "blink_behind",
                (c, t, a) -> {
                    var other = other(c, t);
                    if (other == null || other == t || !plugin.canAffect(c.owner, t)) return;
                    Vector direction = other.getLocation().getDirection().setY(0);
                    if (direction.lengthSquared() < 0.001) return;
                    Location destination =
                            other.getLocation().subtract(direction.normalize().multiply(1.5));
                    if (destination
                                    .getWorld()
                                    .isChunkLoaded(
                                            destination.getBlockX() >> 4,
                                            destination.getBlockZ() >> 4)
                            && destination.getBlock().isPassable()
                            && destination.clone().add(0, 1, 0).getBlock().isPassable())
                        t.teleport(destination);
                });
        registry.register(
                "enable_flight",
                (c, t, a) -> {
                    if (!c.removal() && t instanceof Player player) plugin.passive().flight(player);
                });
        registry.register(
                "walk_on_water",
                (c, t, a) -> {
                    if (!c.removal()) plugin.passive().walker(c.owner, "WATER");
                });
        registry.register(
                "walk_on_lava",
                (c, t, a) -> {
                    if (!c.removal()) plugin.passive().walker(c.owner, "LAVA");
                });
        registry.register("reset_combo", (c, t, a) -> plugin.engine().resetCombo(c.owner));
        registry.register(
                "suppress_enchant",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        plugin.engine()
                                .disable(
                                        player,
                                        EffectRegistry.argument(a, 0, ""),
                                        EffectRegistry.number(a, 1, 1));
                });
    }

    private void pull(
            EffectContext c,
            org.bukkit.entity.LivingEntity target,
            double strength,
            boolean closer) {
        var other = other(c, target);
        if (other == null || other == target || !plugin.canAffect(c.owner, target)) return;
        Vector delta = other.getLocation().toVector().subtract(target.getLocation().toVector());
        if (delta.lengthSquared() < 0.0001) return;
        target.setVelocity(
                delta.normalize()
                        .multiply(Math.clamp(strength, 0, 3) * (closer ? 1 : -1))
                        .setY(0.25));
    }

    static org.bukkit.entity.LivingEntity other(
            EffectContext context, org.bukkit.entity.LivingEntity target) {
        if (target == context.attacker) return context.victim;
        if (target == context.victim) return context.attacker;
        return target == context.owner
                ? context.attacker == context.owner ? context.victim : context.attacker
                : context.owner;
    }

    static PotionEffectType potion(String name) {
        String normalized =
                switch (name.toUpperCase(Locale.ROOT)) {
                    case "SLOW" -> "slowness";
                    case "SLOW_DIGGING" -> "mining_fatigue";
                    case "FAST_DIGGING" -> "haste";
                    case "INCREASE_DAMAGE" -> "strength";
                    case "DAMAGE_RESISTANCE" -> "resistance";
                    case "CONFUSION" -> "nausea";
                    case "JUMP" -> "jump_boost";
                    case "HEAL" -> "instant_health";
                    default -> name.toLowerCase(Locale.ROOT);
                };
        return Registry.EFFECT.get(NamespacedKey.minecraft(normalized));
    }
}
