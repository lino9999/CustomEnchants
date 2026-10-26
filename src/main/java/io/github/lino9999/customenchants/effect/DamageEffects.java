package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;

import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.Cancellable;
import org.bukkit.event.entity.EntityDamageEvent;

final class DamageEffects {
    private final CustomEnchants plugin;

    DamageEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register("reduce_health", (c, t, a) -> remove(c, t, a, false, false));
        registry.register("damage_health", (c, t, a) -> remove(c, t, a, true, false));
        registry.register("reduce_health_totem", (c, t, a) -> remove(c, t, a, false, true));
        registry.register("damage_health_totem", (c, t, a) -> remove(c, t, a, true, true));
        registry.register(
                "pierce_armor",
                (c, t, a) -> {
                    if (c.event instanceof EntityDamageEvent event)
                        plugin.health().ignoreArmor(event);
                });
        registry.register(
                "trigger_totem_buffs",
                (c, t, a) ->
                        plugin.health()
                                .totem(
                                        t,
                                        StateEffects.ticks(a, 0, 900),
                                        StateEffects.ticks(a, 1, 800),
                                        StateEffects.ticks(a, 2, 100)));
        registry.register(
                "boost_damage",
                (c, t, a) -> multiply(c, 1 + EffectRegistry.number(a, 0, 0) / 100));
        registry.register(
                "reduce_damage",
                (c, t, a) -> multiply(c, Math.max(0, 1 - EffectRegistry.number(a, 0, 0) / 100)));
        registry.register("double_damage", (c, t, a) -> multiply(c, 2));
        registry.register("halve_damage", (c, t, a) -> multiply(c, 0.5));
        registry.register(
                "absorb_damage",
                (c, t, a) -> {
                    if (c.event instanceof EntityDamageEvent event)
                        event.setDamage(
                                Math.max(0, event.getDamage() - EffectRegistry.number(a, 0, 0)));
                });
        registry.register(
                "cancel_event",
                (c, t, a) -> {
                    if (c.event instanceof Cancellable event) event.setCancelled(true);
                });
        registry.register("inflict_damage", (c, t, a) -> harm(c, t, EffectRegistry.number(a, 0, 1)));
        registry.register("deal_damage", (c, t, a) -> harm(c, t, EffectRegistry.number(a, 0, 1)));
        registry.register("execute_target", (c, t, a) -> harm(c, t, 2048));
        registry.register(
                "drain_health",
                (c, t, a) -> {
                    LivingEntity victim = c.victim;
                    if (victim == null || victim == t) victim = c.attacker;
                    if (victim == null || victim == t) return;
                    double before = victim.getHealth();
                    harm(c, victim, EffectRegistry.number(a, 0, 1));
                    heal(t, Math.max(0, before - victim.getHealth()));
                });
        registry.register("restore_health", (c, t, a) -> heal(t, EffectRegistry.number(a, 0, 1)));
        registry.register(
                "prevent_death",
                (c, t, a) -> {
                    if (c.event instanceof EntityDamageEvent event
                            && event.getFinalDamage() >= t.getHealth()) {
                        event.setCancelled(true);
                        heal(t, 20);
                        t.setFireTicks(0);
                        t.setNoDamageTicks(40);
                    }
                });
        registry.register(
                "anchor_position",
                (c, t, a) -> {
                    var velocity = t.getVelocity().clone();
                    plugin.getServer()
                            .getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        if (t.isValid()) t.setVelocity(velocity);
                                    });
                });
    }

    private void remove(
            EffectContext context,
            LivingEntity target,
            String[] arguments,
            boolean animation,
            boolean totem) {
        UtilityEffects.required(arguments, 0);
        plugin.health()
                .remove(
                        context,
                        target,
                        LivingEffects.other(context, target),
                        StateEffects.integer(arguments, 0, 0, 0, Integer.MAX_VALUE),
                        animation,
                        totem);
    }

    private void harm(EffectContext c, LivingEntity target, double amount) {
        if (!Double.isFinite(amount) || amount <= 0 || !plugin.canAffect(c.owner, target)) return;
        plugin.engine().effectDamage(() -> target.damage(Math.min(2048, amount), c.owner));
    }

    private static void multiply(EffectContext c, double factor) {
        if (c.event instanceof EntityDamageEvent event)
            event.setDamage(Math.clamp(event.getDamage() * factor, 0, 2048));
    }

    static void heal(LivingEntity entity, double amount) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = attribute == null ? 20 : attribute.getValue();
        entity.setHealth(Math.clamp(entity.getHealth() + Math.max(0, amount), 0, max));
    }
}
