package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;

import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Set;
import java.util.WeakHashMap;

public final class HealthService implements Listener {
    private static final class Hit {
        final LivingEntity target;
        final int amount;
        final boolean allowTotem;
        EntityDamageEvent event;
        boolean manualResurrection;
        boolean saved;

        Hit(LivingEntity target, int amount, boolean allowTotem) {
            this.target = target;
            this.amount = amount;
            this.allowTotem = allowTotem;
        }
    }

    private final CustomEnchants plugin;
    private final Deque<Hit> hits = new ArrayDeque<>();
    private final Set<EntityDamageEvent> bypass = Collections.newSetFromMap(new WeakHashMap<>());

    public HealthService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public void remove(
            EffectContext context,
            LivingEntity target,
            LivingEntity attacker,
            int amount,
            boolean animation,
            boolean allowTotem) {
        if (amount < 0) throw new IllegalArgumentException("Removed health must be nonnegative.");
        if (amount == 0
                || !target.isValid()
                || target.isDead()
                || !plugin.canAffect(context.owner, target)) return;
        if (hits.size() >= 16)
            throw new IllegalArgumentException("Health effect recursion exceeds its limit.");
        Hit hit = new Hit(target, amount, allowTotem);
        int previousTicks = HurtCooldown.get(target);
        double previousDamage = target.getLastDamage();
        int preparedTicks =
                animation ? 0 : Math.max(previousTicks, target.getMaximumNoDamageTicks() / 2 + 1);
        HurtCooldown.set(target, preparedTicks);
        target.setLastDamage(0);
        DamageSource.Builder source = DamageSource.builder(DamageType.GENERIC_KILL);
        if (attacker != null) source.withCausingEntity(attacker).withDirectEntity(attacker);
        hits.push(hit);
        try {
            plugin.engine().effectDamage(() -> target.damage(amount, source.build()));
        } finally {
            hits.pop();
            if (!target.isDead() && (!animation || hit.event == null || hit.event.isCancelled())) {
                if (HurtCooldown.get(target) == preparedTicks)
                    HurtCooldown.set(
                            target,
                            hit.saved && animation
                                    ? Math.max(previousTicks, target.getMaximumNoDamageTicks())
                                    : previousTicks);
                if (target.getLastDamage() == amount || target.getLastDamage() == 0)
                    target.setLastDamage(previousDamage);
            }
        }
    }

    public void ignoreArmor(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        bypass.add(event);
        removeMitigation(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void prepare(EntityDamageEvent event) {
        Hit hit = hits.peek();
        if (hit == null
                || hit.event != null
                || event.getEntity() != hit.target
                || !event.getDamageSource().getDamageType().equals(DamageType.GENERIC_KILL)) return;
        hit.event = event;
        if (!event.isCancelled()) event.setDamage(hit.amount);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(EntityDamageEvent event) {
        boolean marked = bypass.remove(event);
        Hit hit = hits.peek();
        boolean direct = hit != null && hit.event == event;
        if (event.isCancelled() || !marked && !direct) return;
        removeMitigation(event);
        if (direct
                && hit.allowTotem
                && hit.target instanceof Player
                && event.getFinalDamage() >= hit.target.getHealth()) {
            EquipmentSlot hand = totemHand(hit.target);
            if (hand != null && consumeTotem(hit, hand)) {
                hit.saved = true;
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("deprecation")
    private static void removeMitigation(EntityDamageEvent event) {
        for (EntityDamageEvent.DamageModifier modifier :
                EntityDamageEvent.DamageModifier.values()) {
            if (modifier != EntityDamageEvent.DamageModifier.BASE
                    && modifier != EntityDamageEvent.DamageModifier.ABSORPTION
                    && event.isApplicable(modifier)) event.setDamage(modifier, 0);
        }
        if (event.getEntity() instanceof LivingEntity target
                && event.isApplicable(EntityDamageEvent.DamageModifier.ABSORPTION))
            event.setDamage(
                    EntityDamageEvent.DamageModifier.ABSORPTION,
                    -Math.min(target.getAbsorptionAmount(), Math.max(0, event.getDamage())));
    }

    private boolean consumeTotem(Hit hit, EquipmentSlot hand) {
        ItemStack before = hit.target.getEquipment().getItem(hand).clone();
        EntityResurrectEvent event = new EntityResurrectEvent(hit.target, hand);
        hit.manualResurrection = true;
        try {
            Bukkit.getPluginManager().callEvent(event);
        } finally {
            hit.manualResurrection = false;
        }
        ItemStack current = hit.target.getEquipment().getItem(hand);
        if (event.isCancelled()
                || hit.target.isDead()
                || !hit.target.isValid()
                || current == null
                || current.getAmount() < 1
                || !current.isSimilar(before)) return false;
        if (current.getAmount() == 1) hit.target.getEquipment().setItem(hand, null);
        else current.setAmount(current.getAmount() - 1);
        totem(hit.target, 900, 800, 100);
        hit.target.setHealth(
                Math.min(
                        1,
                        hit.target
                                .getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)
                                .getValue()));
        return true;
    }

    public void totem(LivingEntity target, int regeneration, int fireResistance, int absorption) {
        if (!target.isValid() || target.isDead()) return;
        for (int ticks : new int[] {regeneration, fireResistance, absorption})
            if (ticks < 0 || ticks > 72000)
                throw new IllegalArgumentException(
                        "Totem durations must be between 0 and 72000 ticks.");
        for (PotionEffect effect : new ArrayList<>(target.getActivePotionEffects()))
            plugin.states().cure(target, effect.getType());
        target.setAbsorptionAmount(0);
        target.playEffect(EntityEffect.TOTEM_RESURRECT);
        if (regeneration > 0)
            target.addPotionEffect(
                    new PotionEffect(PotionEffectType.REGENERATION, regeneration, 2));
        if (fireResistance > 0)
            target.addPotionEffect(
                    new PotionEffect(PotionEffectType.FIRE_RESISTANCE, fireResistance, 1));
        if (absorption > 0)
            target.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, absorption, 2));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void resurrect(EntityResurrectEvent event) {
        Hit hit = hits.peek();
        if (hit != null && hit.target == event.getEntity() && !hit.manualResurrection)
            event.setCancelled(true);
    }

    private static EquipmentSlot totemHand(LivingEntity target) {
        if (target.getEquipment() == null) return null;
        for (EquipmentSlot hand : new EquipmentSlot[] {EquipmentSlot.OFF_HAND, EquipmentSlot.HAND})
            if (target.getEquipment().getItem(hand).getType() == Material.TOTEM_OF_UNDYING)
                return hand;
        return null;
    }
}
