package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.feature.EntityStateService;
import io.github.lino9999.customenchants.util.Inventories;

import org.bukkit.EntityEffect;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.List;
import java.util.Locale;

final class StateEffects {
    private final CustomEnchants plugin;

    StateEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register("set_walk_speed", (c, t, a) -> speed(c, t, a, false, false));
        registry.register("modify_walk_speed", (c, t, a) -> speed(c, t, a, false, true));
        registry.register("set_fly_speed", (c, t, a) -> speed(c, t, a, true, false));
        registry.register(
                "grant_invulnerability",
                (c, t, a) -> {
                    if (!c.removal()) plugin.states().invincible(c, t);
                });
        registry.register(
                "snare_movement",
                (c, t, a) -> {
                    if (!c.removal() && plugin.canAffect(c.owner, t))
                        plugin.states()
                                .flag(c, t, EntityStateService.Flag.FROZEN, ticks(a, 0, 100));
                });
        registry.register(
                "lock_item_use",
                (c, t, a) -> {
                    if (!c.removal() && plugin.canAffect(c.owner, t))
                        plugin.states()
                                .cancelUse(
                                        c,
                                        t,
                                        UtilityEffects.material(UtilityEffects.required(a, 0)),
                                        ticks(a, 1, 20));
                });
        registry.register(
                "brace_knockback",
                (c, t, a) -> {
                    if (!c.removal())
                        plugin.states()
                                .flag(c, t, EntityStateService.Flag.NO_KNOCKBACK, ticks(a, 0, 20));
                });
        registry.register(
                "traverse_webs",
                (c, t, a) -> {
                    if (c.permanent() && !c.removal())
                        plugin.states().flag(c, t, EntityStateService.Flag.WEB_WALKER, -1);
                });
        registry.register(
                "frost_overlay",
                (c, t, a) -> {
                    if (!c.removal() && plugin.canAffect(c.owner, t))
                        t.setFreezeTicks(ticks(a, 0, 100));
                });
        registry.register(
                "cancel_interaction",
                (c, t, a) -> {
                    if (c.event instanceof Cancellable event) event.setCancelled(true);
                });
        registry.register(
                "inflict_bleed",
                (c, t, a) -> {
                    if (c.removal() || !plugin.canAffect(c.owner, t)) return;
                    plugin.states()
                            .bleed(
                                    c,
                                    t,
                                    LivingEffects.other(c, t),
                                    EffectRegistry.number(a, 0, 1),
                                    integer(a, 1, 5, 1, 1024),
                                    ticks(a, 2, 20));
                });
        registry.register(
                "force_potion",
                (c, t, a) -> {
                    if (c.removal() || !plugin.canAffect(c.owner, t)) return;
                    PotionEffectType type = LivingEffects.potion(UtilityEffects.required(a, 0));
                    if (type == null) throw new IllegalArgumentException("Unknown potion type.");
                    plugin.states()
                            .potion(
                                    c,
                                    t,
                                    type,
                                    integer(a, 1, 0, 0, 255),
                                    c.permanent() || a.length < 3
                                            ? -1
                                            : integer(a, 2, 100, -1, 72000),
                                    true);
                });
        registry.register(
                "preserve_armor",
                (c, t, a) -> {
                    if (!c.removal()) plugin.states().protectArmor(t);
                });
        registry.register(
                "unequip_armor_slot",
                (c, t, a) -> {
                    if (!c.removal() && plugin.canAffect(c.owner, t))
                        removeArmor(t, UtilityEffects.required(a, 0));
                });
        registry.register(
                "play_entity_animation",
                (c, t, a) -> {
                    EntityEffect effect;
                    try {
                        effect =
                                EntityEffect.valueOf(
                                        UtilityEffects.required(a, 0).toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException error) {
                        throw new IllegalArgumentException("Unknown entity effect.");
                    }
                    t.playEffect(effect);
                });
        registry.register(
                "set_min_bite_ticks",
                (c, t, a) -> {
                    if (!(c.event instanceof PlayerFishEvent event)) return;
                    int duration = ticks(a, 0, 100);
                    if (duration > event.getHook().getMaxWaitTime())
                        event.getHook().setMaxWaitTime(duration);
                    event.getHook().setMinWaitTime(duration);
                });
        registry.register(
                "set_max_bite_ticks",
                (c, t, a) -> {
                    if (!(c.event instanceof PlayerFishEvent event)) return;
                    int duration = ticks(a, 0, 600);
                    if (duration < event.getHook().getMinWaitTime())
                        event.getHook().setMinWaitTime(duration);
                    event.getHook().setMaxWaitTime(duration);
                });
    }

    private void speed(
            EffectContext context,
            LivingEntity target,
            String[] arguments,
            boolean flying,
            boolean additive) {
        if (context.removal() || !(target instanceof Player player)) return;
        UtilityEffects.required(arguments, 0);
        plugin.states()
                .speed(
                        context,
                        player,
                        flying,
                        additive,
                        (float) EffectRegistry.number(arguments, 0, 0),
                        arguments.length > 1 ? ticks(arguments, 1, 20) : -1);
    }

    private void removeArmor(LivingEntity entity, String requested) {
        if (entity.getEquipment() == null) return;
        EquipmentSlot slot =
                switch (requested.toUpperCase(Locale.ROOT)) {
                    case "HELMET", "HEAD" -> EquipmentSlot.HEAD;
                    case "CHESTPLATE", "CHEST" -> EquipmentSlot.CHEST;
                    case "LEGGINGS", "LEGS" -> EquipmentSlot.LEGS;
                    case "BOOTS", "FEET" -> EquipmentSlot.FEET;
                    default ->
                            throw new IllegalArgumentException("Unknown armor slot: " + requested);
                };
        ItemStack item = entity.getEquipment().getItem(slot);
        if (ItemData.empty(item)) return;
        boolean removed = false;
        if (entity instanceof Player player && Inventories.fits(player, List.of(item))) {
            Inventories.give(player, List.of(item));
            entity.getEquipment().setItem(slot, null);
            removed = true;
        } else {
            var dropped = entity.getWorld().dropItem(entity.getLocation(), item.clone());
            if (dropped.isValid()) {
                entity.getEquipment().setItem(slot, null);
                removed = true;
            }
        }
        if (removed && entity instanceof Player player) plugin.passive().refresh(player);
    }

    static int ticks(String[] arguments, int index, int fallback) {
        return integer(arguments, index, fallback, 0, 72000);
    }

    static int integer(String[] arguments, int index, int fallback, int minimum, int maximum) {
        double value = EffectRegistry.number(arguments, index, fallback);
        if (value < minimum || value > maximum || value != Math.rint(value))
            throw new IllegalArgumentException(
                    "Effect argument "
                            + (index + 1)
                            + " must be an integer between "
                            + minimum
                            + " and "
                            + maximum
                            + ".");
        return (int) value;
    }
}
