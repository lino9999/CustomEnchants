package io.github.lino9999.customenchants.effect;

import org.bukkit.block.Block;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public final class EffectContext {
    public final Player owner;
    public final LivingEntity attacker;
    public final LivingEntity victim;
    public final ItemStack item;
    public final Block block;
    public final Event event;
    public final String trigger;
    public final Map<String, Object> values;
    public int level;
    public String enchantment = "";

    public EffectContext(
            Player owner,
            LivingEntity attacker,
            LivingEntity victim,
            ItemStack item,
            Block block,
            Event event,
            String trigger) {
        this(owner, attacker, victim, item, block, event, trigger, new HashMap<>());
    }

    private EffectContext(
            Player owner,
            LivingEntity attacker,
            LivingEntity victim,
            ItemStack item,
            Block block,
            Event event,
            String trigger,
            Map<String, Object> values) {
        this.owner = owner;
        this.attacker = attacker;
        this.victim = victim;
        this.item = item;
        this.block = block;
        this.event = event;
        this.trigger = trigger;
        this.values = values;
    }

    public EffectContext delayed() {
        EffectContext context =
                new EffectContext(
                        owner, attacker, victim, item, block, null, trigger, new HashMap<>(values));
        if (event instanceof org.bukkit.event.entity.EntityDamageEvent damage) {
            context.values.put("event.damage", (int) damage.getFinalDamage());
            context.values.put("event.raw_damage", (int) damage.getDamage());
            context.values.put("event.cause", damage.getCause().name());
        }
        context.level = level;
        context.values.remove("pending blocks");
        context.enchantment = enchantment;
        return context;
    }

    public boolean permanent() {
        return trigger.equals("ARMOR_PASSIVE") || trigger.equals("HAND_PASSIVE");
    }

    public boolean removal() {
        return Boolean.TRUE.equals(values.get("state.unequipping"));
    }

    public boolean started() {
        return !Boolean.FALSE.equals(values.get("state.equipping"));
    }
}
