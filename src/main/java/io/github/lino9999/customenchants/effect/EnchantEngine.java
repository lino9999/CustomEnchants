package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.api.EnchantActivateEvent;
import io.github.lino9999.customenchants.config.EnchantDefinition;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.enchant.MaterialRules;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EnchantEngine {
    private record Active(EnchantDefinition definition, int level, ItemStack item) {}

    private record Combo(UUID victim, int hits, long expires) {}

    private final CustomEnchants plugin;
    private final Conditions conditions;
    private final EffectRegistry effects;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Combo> combos = new HashMap<>();
    private boolean effectDamage;
    private int activationDepth;
    private int transactions;

    public boolean collecting() {
        return transactions > 0;
    }

    private final Map<UUID, Map<String, Permanent>> permanent = new HashMap<>();

    private static final class Permanent {
        final EffectContext context;
        final EnchantDefinition.Level definition;
        final ItemStack signature;
        boolean seen;

        Permanent(EffectContext context, EnchantDefinition.Level definition) {
            this.context = context.delayed();
            this.definition = definition;
            signature = signature(context.item);
            seen = true;
        }
    }

    public void beginPassives(Player player) {
        permanent
                .computeIfAbsent(player.getUniqueId(), key -> new LinkedHashMap<>())
                .values()
                .forEach(active -> active.seen = false);
    }

    public void endPassives(Player player) {
        Map<String, Permanent> active = permanent.get(player.getUniqueId());
        if (active == null) return;
        for (var entry : new ArrayList<>(active.entrySet()))
            if (!entry.getValue().seen) {
                active.remove(entry.getKey());
                endPermanent(player, entry.getKey(), entry.getValue());
            }
    }

    public void clearPassives(Player player) {
        Map<String, Permanent> active = permanent.remove(player.getUniqueId());
        if (active != null)
            for (var entry : active.entrySet())
                endPermanent(player, entry.getKey(), entry.getValue());
    }

    private void endPermanent(Player player, String scope, Permanent active) {
        plugin.passive().release(player, scope);
        plugin.states().release(active.context);
        active.context.values.put("state.unequipping", true);
        active.context.values.put("state.equipping", false);
        try {
            effects.execute(active.definition.effects(), active.context);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Permanent effect cleanup failed: " + error.getMessage());
        }
    }

    private static ItemStack signature(ItemStack item) {
        if (ItemData.empty(item)) return null;
        ItemStack result = item.clone();
        result.setAmount(1);
        if (result.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable meta) {
            meta.setDamage(0);
            result.setItemMeta(meta);
        }
        return result;
    }

    private void maintain(EffectContext context, String id, EnchantDefinition.Level level) {
        String scope = context.trigger + ":" + id;
        Map<String, Permanent> active =
                permanent.computeIfAbsent(
                        context.owner.getUniqueId(), key -> new LinkedHashMap<>());
        Permanent previous = active.get(scope);
        if (previous != null
                && previous.context.level == context.level
                && previous.definition.equals(level)
                && java.util.Objects.equals(previous.signature, signature(context.item))) {
            previous.seen = true;
            return;
        }
        if (previous != null) {
            active.remove(scope);
            endPermanent(context.owner, scope, previous);
        }
        context.values.put("state.equipping", true);
        context.values.put("permanent scope", scope);
        context.values.put("permanent token", UUID.randomUUID().toString());
        Permanent replacement = new Permanent(context, level);
        if (plugin.passive().capture(context.owner, scope, () -> activateOnce(context, id, level)))
            active.put(scope, replacement);
    }

    public void continuePermanent(EffectContext context, Runnable continuation) {
        String scope = String.valueOf(context.values.get("permanent scope"));
        Map<String, Permanent> active = permanent.get(context.owner.getUniqueId());
        Permanent current = active == null ? null : active.get(scope);
        if (current != null
                && java.util.Objects.equals(
                        current.context.values.get("permanent token"),
                        context.values.get("permanent token")))
            plugin.passive().resume(context.owner, scope, continuation);
    }

    public EnchantEngine(CustomEnchants plugin, Conditions conditions, EffectRegistry effects) {
        this.plugin = plugin;
        this.conditions = conditions;
        this.effects = effects;
    }

    public boolean isEffectDamage() {
        return effectDamage;
    }

    public void effectDamage(Runnable operation) {
        boolean old = effectDamage;
        effectDamage = true;
        try {
            operation.run();
        } finally {
            effectDamage = old;
        }
    }

    public void clear(Player player) {
        clearPassives(player);
        cooldowns.remove(player.getUniqueId());
        combos.remove(player.getUniqueId());
    }

    public void clear() {
        for (UUID id : new ArrayList<>(permanent.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) clearPassives(player);
        }
        permanent.clear();
        cooldowns.clear();
        combos.clear();
    }

    public void combo(Player player, UUID victim) {
        long now = System.currentTimeMillis();
        Combo old = combos.get(player.getUniqueId());
        combos.put(
                player.getUniqueId(),
                new Combo(
                        victim,
                        old != null && old.victim.equals(victim) && old.expires > now
                                ? Math.min(100, old.hits + 1)
                                : 1,
                        now + 5000));
    }

    public int combo(Player player) {
        Combo combo = combos.get(player.getUniqueId());
        return combo != null && combo.expires > System.currentTimeMillis() ? combo.hits : 0;
    }

    public void resetCombo(Player player) {
        combos.remove(player.getUniqueId());
    }

    public void disable(Player player, String id, double seconds) {
        cooldowns
                .computeIfAbsent(player.getUniqueId(), k -> new HashMap<>())
                .put(
                        id,
                        System.currentTimeMillis()
                                + (long) Math.clamp(seconds * 1000, 0, 86400000));
    }

    public void fire(EffectContext context) {
        transactions++;
        try {
            fireCollected(context);
        } finally {
            transactions--;
            if (transactions == 0) plugin.blocks().flush(context);
        }
    }

    private void fireCollected(EffectContext context) {
        if (suppressed(context) || !plugin.settings().enabled(context.owner.getWorld().getName()))
            return;
        Map<String, Active> selected = new LinkedHashMap<>();
        List<ItemStack> candidates = new ArrayList<>();
        candidates.add(context.item);
        ItemStack offhand = context.owner.getInventory().getItemInOffHand();
        if (!ItemData.empty(offhand) && offhand != context.item) candidates.add(offhand);
        if (!context.trigger.equals("HAND_PASSIVE"))
            candidates.addAll(Arrays.asList(context.owner.getInventory().getArmorContents()));
        for (ItemStack item : candidates) {
            if (ItemData.empty(item)) continue;
            boolean armor = MaterialRules.armor(item.getType().name());
            if ((context.trigger.equals("ARMOR_PASSIVE") && !armor)
                    || (context.trigger.equals("HAND_PASSIVE") && armor)) continue;
            for (var entry : plugin.items().enchants(item).entrySet()) {
                EnchantDefinition e = plugin.settings().enchants().get(entry.getKey());
                if (e == null
                        || !e.triggers().contains(context.trigger)
                        || !e.levels().containsKey(entry.getValue())
                        || !MaterialRules.matches(item.getType(), e.materials())) continue;
                Active old = selected.get(e.id());
                if (old == null || old.level < entry.getValue())
                    selected.put(e.id(), new Active(e, entry.getValue(), item));
            }
        }
        for (Active active : selected.values()) {
            EffectContext local =
                    new EffectContext(
                            context.owner,
                            context.attacker,
                            context.victim,
                            active.item,
                            context.block,
                            context.event,
                            context.trigger);
            local.values.putAll(context.values);
            local.level = active.level;
            local.enchantment = active.definition.id();
            activate(local, active.definition.id(), active.definition.levels().get(active.level));
            context.values.putAll(local.values);
        }
        plugin.equipment().activate(context, this::activate);
    }

    public void fireItem(EffectContext context) {
        transactions++;
        try {
            fireItemCollected(context);
        } finally {
            transactions--;
            if (transactions == 0) plugin.blocks().flush(context);
        }
    }

    private void fireItemCollected(EffectContext context) {
        if (suppressed(context)
                || ItemData.empty(context.item)
                || !plugin.settings().enabled(context.owner.getWorld().getName())) return;
        for (var entry : plugin.items().enchants(context.item).entrySet()) {
            EnchantDefinition definition = plugin.settings().enchants().get(entry.getKey());
            if (definition == null
                    || !definition.triggers().contains(context.trigger)
                    || !definition.levels().containsKey(entry.getValue())) continue;
            context.level = entry.getValue();
            context.enchantment = definition.id();
            activate(context, definition.id(), definition.levels().get(entry.getValue()));
        }
    }

    public void activate(EffectContext context, String id, EnchantDefinition.Level level) {
        if (context.permanent()) maintain(context, id, level);
        else activateOnce(context, id, level);
    }

    private boolean activateOnce(EffectContext context, String id, EnchantDefinition.Level level) {
        if (activationDepth >= 32) return false;
        activationDepth++;
        try {
            return runActivation(context, id, level);
        } finally {
            activationDepth--;
        }
    }

    private boolean suppressed(EffectContext context) {
        return effectDamage
                && !java.util.Set.of("PLAYER_SLAY", "MOB_SLAY", "COMBAT_DEATH", "NATURAL_DEATH")
                        .contains(context.trigger);
    }

    private boolean runActivation(EffectContext context, String id, EnchantDefinition.Level level) {
        context.values.remove("chance_bonus");
        context.values.remove("chance_override");
        if (level.effects().stream().anyMatch(effect -> effect.startsWith("prevent_death"))
                && (!(context.event instanceof org.bukkit.event.entity.EntityDamageEvent damage)
                        || damage.getFinalDamage() < context.owner.getHealth())) return false;
        long now = System.currentTimeMillis();
        Map<String, Long> map =
                cooldowns.computeIfAbsent(context.owner.getUniqueId(), k -> new HashMap<>());
        if (map.getOrDefault(id, 0L) > now
                && !context.owner.hasPermission("customenchants.bypass.cooldown")) return false;
        if (!conditions.allow(level.conditions(), context)) return false;
        if (context.block != null) {
            String material = context.block.getType().name();
            if ((!level.whitelist().isEmpty() && !level.whitelist().contains(material))
                    || level.blacklist().contains(material)) return false;
        }
        double bonus = ((Number) context.values.getOrDefault("chance_bonus", 0)).doubleValue();
        double chance =
                ((Number) context.values.getOrDefault("chance_override", level.chance() + bonus))
                        .doubleValue();
        if (!Numbers.roll(chance)) return false;
        EnchantActivateEvent event =
                new EnchantActivateEvent(context.owner, id, context.level, context.trigger);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        if (level.cooldown() > 0) map.put(id, now + (long) (level.cooldown() * 1000));
        context.values.put("whitelist", level.whitelist());
        context.values.put("blacklist", level.blacklist());
        try {
            effects.execute(level.effects(), context);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("Action " + id + " failed: " + error.getMessage());
        }
        if (map.size() > 512) map.entrySet().removeIf(e -> e.getValue() <= now);
        return true;
    }
}
