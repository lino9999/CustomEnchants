package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Ageable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class GuardService implements Listener {
    public record Guard(UUID owner, UUID target, UUID controller) {}

    private final CustomEnchants plugin;
    private final Map<UUID, Guard> guards = new HashMap<>();

    public GuardService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public Map<UUID, Guard> guards() {
        return Collections.unmodifiableMap(guards);
    }

    public void remove(UUID entity) {
        guards.remove(entity);
    }

    public void clear() {
        for (UUID id : new ArrayList<>(guards.keySet())) {
            Entity entity = Bukkit.getEntity(id);
            if (entity != null) entity.remove();
        }
        guards.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        if (guards.containsKey(event.getEntity().getUniqueId()))
            plugin.forgetEntity(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void prime(ExplosionPrimeEvent event) {
        if (guards.containsKey(event.getEntity().getUniqueId())
                && event.getEntity() instanceof LivingEntity entity)
            for (PotionEffect potion : new ArrayList<>(entity.getActivePotionEffects()))
                entity.removePotionEffect(potion.getType());
    }

    public void spawn(
            EffectContext context,
            LivingEntity selected,
            String requested,
            int seconds,
            int count,
            String name,
            boolean switchRoles) {
        if (seconds < 0 || seconds > 3600)
            throw new IllegalArgumentException(
                    "Guardian duration must be between 0 and 3600 seconds.");
        if (count < 0 || count > 128)
            throw new IllegalArgumentException("Guardian count must be between 0 and 128.");
        if (requested == null || requested.isBlank())
            throw new IllegalArgumentException("A guardian entity type is required.");
        String typeName = requested.toLowerCase(Locale.ROOT);
        boolean baby = typeName.startsWith("baby_");
        if (baby) typeName = typeName.substring(5);
        NamespacedKey key = NamespacedKey.fromString(typeName);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || !type.isSpawnable() || type.getEntityClass() == null)
            throw new IllegalArgumentException("A guardian requires a spawnable entity type.");
        if (baby && !Ageable.class.isAssignableFrom(type.getEntityClass()))
            throw new IllegalArgumentException(
                    "The guardian entity type does not support a baby variant.");
        if (name == null || name.length() > 256)
            throw new IllegalArgumentException("Guardian names cannot exceed 256 characters.");
        if (count == 0 || !ready(selected)) return;
        LivingEntity other = other(context, selected);
        if (context.trigger.equals("HORN_USE") && (other == null || other == selected))
            other = nearest(context, selected);
        LivingEntity owner = switchRoles ? other : selected;
        LivingEntity enemy = switchRoles ? selected : other;
        if (!ready(owner)
                || !ready(enemy)
                || owner.getUniqueId().equals(enemy.getUniqueId())
                || owner.getWorld() != enemy.getWorld()
                || !plugin.canAffect(context.owner, enemy)) return;
        Location origin = enemy.getLocation();
        if (!loaded(origin)) return;
        String formatted = Text.color(name.replace("{guard_owner}", owner.getName()));
        if (formatted.length() > 256)
            throw new IllegalArgumentException("Guardian names cannot exceed 256 characters.");
        int lifetime = seconds * 20;
        Class<? extends Entity> entityClass = type.getEntityClass();
        for (int i = 0; i < count && plugin.managedEntityCount() < 128; i++) {
            Entity entity =
                    origin.getWorld()
                            .spawn(
                                    origin,
                                    entityClass,
                                    spawned -> {
                                        plugin.spawns().own(spawned, context.owner);
                                        if (spawned instanceof LivingEntity living) {
                                            living.setCanPickupItems(false);
                                            living.setRemoveWhenFarAway(true);
                                            if (lifetime > 0)
                                                living.addPotionEffect(
                                                        new PotionEffect(
                                                                PotionEffectType.STRENGTH,
                                                                lifetime,
                                                                1,
                                                                false,
                                                                true));
                                        }
                                        if (spawned instanceof AbstractArrow arrow)
                                            arrow.setPickupStatus(
                                                    AbstractArrow.PickupStatus.DISALLOWED);
                                        if (!formatted.isEmpty()) {
                                            spawned.setCustomName(formatted);
                                            spawned.setCustomNameVisible(true);
                                        }
                                        if (baby) ((Ageable) spawned).setBaby();
                                    });
            if (!entity.isValid()) continue;
            if (plugin.managedEntityCount() >= 128) {
                entity.remove();
                break;
            }
            guards.put(
                    entity.getUniqueId(),
                    new Guard(
                            owner.getUniqueId(), enemy.getUniqueId(), context.owner.getUniqueId()));
            plugin.trackEntity(entity, lifetime);
            if (entity instanceof Mob mob) {
                mob.setTarget(null);
                target(mob, enemy);
            }
        }
    }

    public void steal(EffectContext context, LivingEntity selected, LivingEntity other) {
        if (!ready(selected)
                || !ready(other)
                || selected.getUniqueId().equals(other.getUniqueId())
                || selected.getWorld() != other.getWorld()
                || !plugin.canAffect(context.owner, other)) return;
        for (var entry : new ArrayList<>(guards.entrySet())) {
            Guard previous = entry.getValue();
            if (!previous.owner().equals(other.getUniqueId())) continue;
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (entity == null || !entity.isValid() || entity.isDead()) {
                guards.remove(entry.getKey(), previous);
                continue;
            }
            if (entity.getWorld() != selected.getWorld()) continue;
            Guard replacement =
                    new Guard(
                            selected.getUniqueId(),
                            other.getUniqueId(),
                            context.owner.getUniqueId());
            if (!guards.replace(entry.getKey(), previous, replacement)) continue;
            if (entity instanceof Mob mob) {
                try {
                    if (!target(mob, other)) guards.replace(entry.getKey(), replacement, previous);
                } catch (RuntimeException error) {
                    guards.replace(entry.getKey(), replacement, previous);
                    throw error;
                }
            }
            if (guards.get(entry.getKey()) == replacement)
                plugin.spawns().own(entity, context.owner);
        }
    }

    public void add(Entity entity, Player owner, LivingEntity target, long lifetime) {
        if (lifetime < 0 || lifetime > 72000)
            throw new IllegalArgumentException(
                    "Guardian lifetime must be between 0 and 72000 ticks.");
        if (!entity.isValid()
                || !ready(owner)
                || !ready(target)
                || entity.getWorld() != target.getWorld()) return;
        if (owner.getUniqueId().equals(target.getUniqueId()))
            throw new IllegalArgumentException("A guardian cannot attack its owner.");
        if (!plugin.managed(entity) && plugin.managedEntityCount() >= 128)
            throw new IllegalStateException("The managed entity limit has been reached.");
        if (!plugin.canAffect(owner, target)) return;
        guards.put(
                entity.getUniqueId(),
                new Guard(owner.getUniqueId(), target.getUniqueId(), owner.getUniqueId()));
        plugin.spawns().own(entity, owner);
        plugin.trackEntity(entity, lifetime);
        if (entity instanceof Mob mob) {
            if (mob.getTarget() != null
                    && mob.getTarget().getUniqueId().equals(owner.getUniqueId()))
                mob.setTarget(null);
            target(mob, target);
        }
    }

    private static boolean target(Mob mob, LivingEntity requested) {
        EntityTargetLivingEntityEvent event =
                new EntityTargetLivingEntityEvent(
                        mob, requested, EntityTargetEvent.TargetReason.CUSTOM);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()
                || event.getTarget() == null
                || !event.getTarget().getUniqueId().equals(requested.getUniqueId())
                || !ready(mob)
                || !ready(requested)) return false;
        mob.setTarget(requested);
        return mob.getTarget() != null
                && mob.getTarget().getUniqueId().equals(requested.getUniqueId());
    }

    private LivingEntity nearest(EffectContext context, LivingEntity owner) {
        return owner.getNearbyEntities(8, 4, 8).stream()
                .filter(Monster.class::isInstance)
                .map(LivingEntity.class::cast)
                .filter(
                        entity ->
                                ready(entity)
                                        && !entity.getUniqueId().equals(owner.getUniqueId())
                                        && (guards.get(entity.getUniqueId()) == null
                                                || !guards.get(entity.getUniqueId())
                                                        .owner()
                                                        .equals(owner.getUniqueId())))
                .sorted(
                        Comparator.comparingDouble(
                                entity ->
                                        entity.getLocation().distanceSquared(owner.getLocation())))
                .filter(entity -> plugin.canAffect(context.owner, entity))
                .findFirst()
                .orElse(null);
    }

    private static LivingEntity other(EffectContext context, LivingEntity selected) {
        if (selected == context.attacker) return context.victim;
        if (selected == context.victim) return context.attacker;
        return selected == context.owner
                ? context.attacker == context.owner ? context.victim : context.attacker
                : context.owner;
    }

    private static boolean ready(LivingEntity entity) {
        return entity != null && entity.isValid() && !entity.isDead();
    }

    private static boolean loaded(Location location) {
        return location.getWorld() != null
                && location.getY() >= location.getWorld().getMinHeight()
                && location.getY() < location.getWorld().getMaxHeight()
                && location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
