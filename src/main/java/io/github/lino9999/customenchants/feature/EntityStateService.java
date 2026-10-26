package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;

public final class EntityStateService implements Listener {
    public enum Flag {
        FROZEN,
        NO_KNOCKBACK,
        WEB_WALKER
    }

    private record Source(UUID owner, String scope, String token) {}

    private record Lease<T>(Source source, long expires, T value) {}

    private record Speed(boolean flying, boolean additive, float amount) {}

    private record PotionRequest(
            PotionEffectType type, int amplifier, boolean force, boolean freezing) {}

    private record OwnedPotion(PotionEffect previous, long since, int amplifier, boolean ambient) {}

    private static final class State {
        final LivingEntity entity;
        final Map<Long, Lease<Speed>> speeds = new LinkedHashMap<>();
        final Map<Long, Lease<Flag>> flags = new LinkedHashMap<>();
        final Map<Long, Lease<Material>> uses = new LinkedHashMap<>();
        final Map<Long, Lease<Boolean>> immunity = new LinkedHashMap<>();
        final Map<Long, Lease<PotionRequest>> potions = new LinkedHashMap<>();
        final Map<PotionEffectType, OwnedPotion> ownedPotions = new HashMap<>();
        final Map<EquipmentSlot, ItemStack> armorBefore = new LinkedHashMap<>();
        long armorUntil;
        Float walkBefore, walkApplied, flyBefore, flyApplied;
        Boolean immunityBefore, immunityApplied;
        Integer coldBefore, coldApplied;
        long coldSince;

        State(LivingEntity entity) {
            this.entity = entity;
        }

        boolean empty() {
            return speeds.isEmpty()
                    && flags.isEmpty()
                    && uses.isEmpty()
                    && immunity.isEmpty()
                    && potions.isEmpty()
                    && armorBefore.isEmpty();
        }

        int size() {
            return speeds.size() + flags.size() + uses.size() + immunity.size() + potions.size();
        }
    }

    private static final class Bleeding {
        final EffectContext context;
        final LivingEntity target;
        final LivingEntity attacker;
        final Source source;
        final double damage;
        final int interval;
        int remaining;
        long next;

        Bleeding(
                EffectContext context,
                LivingEntity target,
                LivingEntity attacker,
                Source source,
                double damage,
                int count,
                int interval,
                long tick) {
            this.context = context.delayed();
            this.target = target;
            this.attacker = attacker;
            this.source = source;
            this.damage = damage;
            this.remaining = count;
            this.interval = interval;
            next = tick + interval;
        }
    }

    private final CustomEnchants plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<Long, Bleeding> bleeds = new LinkedHashMap<>();
    private long tick;
    private long sequence;

    public EntityStateService(CustomEnchants plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    public void speed(
            EffectContext context,
            Player player,
            boolean flying,
            boolean additive,
            float value,
            int duration) {
        if (!Float.isFinite(value) || value < -1 || value > 1)
            throw new IllegalArgumentException("Speed must be between -1 and 1.");
        State state = state(player);
        Source origin = source(context);
        if (!context.permanent() && duration == -1) {
            origin =
                    new Source(
                            context.owner.getUniqueId(), "manual speed", flying ? "fly" : "walk");
            Source requested = origin;
            state.speeds.values().removeIf(lease -> lease.source.equals(requested));
            value =
                    additive
                            ? Math.clamp(
                                    (flying ? player.getFlySpeed() : player.getWalkSpeed()) + value,
                                    -1,
                                    1)
                            : value;
            additive = false;
        }
        state.speeds.put(
                ++sequence,
                new Lease<>(origin, expires(duration), new Speed(flying, additive, value)));
        apply(state);
    }

    public void flag(EffectContext context, LivingEntity entity, Flag flag, int duration) {
        State state = duration == 0 ? states.get(entity.getUniqueId()) : state(entity);
        if (state == null) {
            if (duration == 0 && flag == Flag.FROZEN && !(entity instanceof Player))
                entity.setFreezeTicks(0);
            return;
        }
        if (duration == 0) {
            state.flags.values().removeIf(lease -> lease.value == flag);
            if (flag == Flag.FROZEN) {
                state.potions.values().removeIf(lease -> lease.value.freezing);
                if (!(entity instanceof Player)) entity.setFreezeTicks(0);
            }
        } else state.flags.put(++sequence, new Lease<>(source(context), expires(duration), flag));
        if (flag == Flag.FROZEN && !(entity instanceof Player) && duration > 0) {
            state.potions.put(
                    ++sequence,
                    new Lease<>(
                            source(context),
                            expires(duration),
                            new PotionRequest(PotionEffectType.SLOWNESS, 127, false, true)));
        }
        apply(state);
    }

    public void cancelUse(
            EffectContext context, LivingEntity entity, Material material, int duration) {
        State state = duration == 0 ? states.get(entity.getUniqueId()) : state(entity);
        if (state == null) return;
        if (duration == 0) state.uses.values().removeIf(lease -> lease.value == material);
        else state.uses.put(++sequence, new Lease<>(source(context), expires(duration), material));
    }

    public void invincible(EffectContext context, LivingEntity entity) {
        Source source =
                context.permanent()
                        ? source(context)
                        : new Source(context.owner.getUniqueId(), "toggle", "invincible");
        State state = states.get(entity.getUniqueId());
        boolean removed =
                state != null
                        && state.immunity.values().removeIf(lease -> lease.source.equals(source));
        if (context.permanent() || !removed) {
            if (!removed) state = state(entity);
            state.immunity.put(
                    ++sequence,
                    new Lease<>(
                            source,
                            Long.MAX_VALUE,
                            context.permanent() || !entity.isInvulnerable()));
        }
        apply(state);
    }

    public void potion(
            EffectContext context,
            LivingEntity entity,
            PotionEffectType type,
            int amplifier,
            int duration,
            boolean force) {
        if (amplifier < 0 || amplifier > 255)
            throw new IllegalArgumentException("Potion amplifier must be between 0 and 255.");
        State state = state(entity);
        state.potions.put(
                ++sequence,
                new Lease<>(
                        source(context),
                        expires(duration),
                        new PotionRequest(type, amplifier, force, false)));
        apply(state);
    }

    public void protectArmor(LivingEntity entity) {
        if (entity.getEquipment() == null) return;
        State state = state(entity);
        state.armorUntil = tick + 1;
        for (EquipmentSlot slot :
                java.util.List.of(
                        EquipmentSlot.HEAD,
                        EquipmentSlot.CHEST,
                        EquipmentSlot.LEGS,
                        EquipmentSlot.FEET)) {
            ItemStack item = entity.getEquipment().getItem(slot);
            if (item != null && item.getItemMeta() instanceof Damageable)
                state.armorBefore.putIfAbsent(slot, item.clone());
        }
    }

    public void cure(LivingEntity entity, PotionEffectType type) {
        State state = states.get(entity.getUniqueId());
        if (state != null) {
            state.potions.values().removeIf(lease -> lease.value.type.equals(type));
            state.ownedPotions.remove(type);
        }
        entity.removePotionEffect(type);
    }

    public void bleed(
            EffectContext context,
            LivingEntity target,
            LivingEntity attacker,
            double damage,
            int count,
            int interval) {
        if (!Double.isFinite(damage)
                || damage <= 0
                || damage > 2048
                || count < 1
                || count > 1024
                || interval < 1
                || interval > 72000
                || (long) count * interval > 72000)
            throw new IllegalArgumentException(
                    "Bleed requires positive damage, count and interval with a total duration up to"
                            + " 72000 ticks.");
        if (bleeds.size() >= 4096)
            throw new IllegalArgumentException("Too many active bleeding effects.");
        bleeds.put(
                ++sequence,
                new Bleeding(
                        context, target, attacker, source(context), damage, count, interval, tick));
    }

    public boolean bleeding(LivingEntity entity) {
        return bleeds.values().stream()
                .anyMatch(bleed -> bleed.target.getUniqueId().equals(entity.getUniqueId()));
    }

    public boolean active(LivingEntity entity, Flag flag) {
        State state = states.get(entity.getUniqueId());
        return state != null
                && state.flags.values().stream()
                        .anyMatch(lease -> lease.value == flag && lease.expires > tick);
    }

    public void release(EffectContext context) {
        Source source = source(context);
        removeSources(candidate -> candidate.equals(source));
    }

    public void clearOwned(UUID owner) {
        removeSources(source -> source.owner.equals(owner));
    }

    public void clearTarget(LivingEntity entity) {
        State state = states.remove(entity.getUniqueId());
        if (state != null) restore(state);
        bleeds.values().removeIf(bleed -> bleed.target.getUniqueId().equals(entity.getUniqueId()));
    }

    public void clear() {
        for (State state : states.values()) restore(state);
        states.clear();
        bleeds.clear();
    }

    private void removeSources(Predicate<Source> predicate) {
        for (State state : new ArrayList<>(states.values())) {
            state.speeds.values().removeIf(lease -> predicate.test(lease.source));
            state.flags.values().removeIf(lease -> predicate.test(lease.source));
            state.uses.values().removeIf(lease -> predicate.test(lease.source));
            state.immunity.values().removeIf(lease -> predicate.test(lease.source));
            state.potions.values().removeIf(lease -> predicate.test(lease.source));
            apply(state);
            if (state.empty()) states.remove(state.entity.getUniqueId());
        }
        bleeds.values().removeIf(bleed -> predicate.test(bleed.source));
    }

    private State state(LivingEntity entity) {
        if (states.values().stream().mapToInt(State::size).sum() >= 16384)
            throw new IllegalArgumentException("Too many active entity effects.");
        if (!states.containsKey(entity.getUniqueId()) && states.size() >= 2048)
            throw new IllegalArgumentException("Too many entities with active effects.");
        State state = states.computeIfAbsent(entity.getUniqueId(), ignored -> new State(entity));
        if (state.size() >= 512)
            throw new IllegalArgumentException("Too many overlapping entity effects.");
        return state;
    }

    private Source source(EffectContext context) {
        if (!context.permanent())
            return new Source(context.owner.getUniqueId(), "", UUID.randomUUID().toString());
        String scope =
                String.valueOf(
                        context.values.computeIfAbsent(
                                "permanent scope",
                                ignored -> context.trigger + ":" + context.enchantment));
        String token =
                String.valueOf(
                        context.values.computeIfAbsent(
                                "permanent token", ignored -> UUID.randomUUID().toString()));
        return new Source(context.owner.getUniqueId(), scope, token);
    }

    private long expires(int duration) {
        if (duration < -1 || duration > 72000)
            throw new IllegalArgumentException(
                    "Effect duration must be -1 or between 0 and 72000 ticks.");
        return duration == -1 ? Long.MAX_VALUE : tick + duration;
    }

    private void tick() {
        tick++;
        for (State state : new ArrayList<>(states.values())) {
            if (!state.entity.isValid() || state.entity.isDead()) {
                clearTarget(state.entity);
                continue;
            }
            if (!state.armorBefore.isEmpty() && state.armorUntil <= tick) restoreArmor(state);
            state.speeds.values().removeIf(lease -> lease.expires <= tick);
            state.flags.values().removeIf(lease -> lease.expires <= tick);
            state.uses.values().removeIf(lease -> lease.expires <= tick);
            state.immunity.values().removeIf(lease -> lease.expires <= tick);
            state.potions.values().removeIf(lease -> lease.expires <= tick);
            apply(state);
            if (state.empty()) states.remove(state.entity.getUniqueId());
        }
        for (var entry : new ArrayList<>(bleeds.entrySet())) {
            Bleeding bleed = entry.getValue();
            if (!bleed.target.isValid()
                    || bleed.target.isDead()
                    || !bleed.context.owner.isOnline()
                    || !plugin.settings().enabled(bleed.target.getWorld().getName())) {
                bleeds.remove(entry.getKey());
                continue;
            }
            if (tick < bleed.next) continue;
            bleed.next = tick + bleed.interval;
            if (plugin.canAffect(bleed.context.owner, bleed.target)) {
                plugin.engine()
                        .effectDamage(
                                () ->
                                        bleed.target.damage(
                                                bleed.damage,
                                                bleed.attacker == null
                                                        ? bleed.context.owner
                                                        : bleed.attacker));
                bleed.target
                        .getWorld()
                        .spawnParticle(
                                Particle.BLOCK,
                                bleed.target.getLocation().add(0, 0.8, 0),
                                10,
                                0.2,
                                0.2,
                                0.2,
                                Material.REDSTONE_BLOCK.createBlockData());
                bleed.target
                        .getWorld()
                        .spawnParticle(
                                Particle.DRIPPING_LAVA,
                                bleed.target.getLocation(),
                                10,
                                0.2,
                                0.2,
                                0.2);
            }
            if (--bleed.remaining == 0) bleeds.remove(entry.getKey());
        }
    }

    private void apply(State state) {
        LivingEntity entity = state.entity;
        if (!entity.isValid() && !(entity instanceof Player)) return;
        if (entity instanceof Player player) {
            speed(state, player, false);
            speed(state, player, true);
        }
        applyCold(state);
        if (state.immunity.isEmpty()) {
            if (state.immunityBefore != null
                    && Objects.equals(state.immunityApplied, entity.isInvulnerable()))
                entity.setInvulnerable(state.immunityBefore);
            state.immunityBefore = null;
            state.immunityApplied = null;
        } else {
            if (state.immunityBefore == null
                    || state.immunityApplied != null
                            && state.immunityApplied != entity.isInvulnerable())
                state.immunityBefore = entity.isInvulnerable();
            boolean value = false;
            for (Lease<Boolean> lease : state.immunity.values()) value = lease.value;
            if (entity.isInvulnerable() != value) entity.setInvulnerable(value);
            state.immunityApplied = value;
        }
        Map<PotionEffectType, Lease<PotionRequest>> desired = new LinkedHashMap<>();
        for (Lease<PotionRequest> lease : state.potions.values())
            desired.merge(
                    lease.value.type,
                    lease,
                    (old, newer) ->
                            newer.value.force
                                            || !old.value.force
                                                    && newer.value.amplifier >= old.value.amplifier
                                    ? newer
                                    : old);
        for (PotionEffectType type : new ArrayList<>(state.ownedPotions.keySet()))
            if (!desired.containsKey(type)) restorePotion(state, type);
        desired.forEach((type, lease) -> applyPotion(state, type, lease));
    }

    private void applyCold(State state) {
        if (state.entity instanceof Player) return;
        long until =
                state.flags.values().stream()
                        .filter(lease -> lease.value == Flag.FROZEN)
                        .mapToLong(Lease::expires)
                        .max()
                        .orElse(0);
        if (until > tick) {
            if (state.coldBefore == null) {
                state.coldBefore = state.entity.getFreezeTicks();
                state.coldSince = tick;
            }
            int amount = (int) Math.min(72000, until == Long.MAX_VALUE ? 72000 : until - tick);
            state.entity.setFreezeTicks(amount);
            state.coldApplied = amount;
        } else if (state.coldBefore != null) {
            int current = state.entity.getFreezeTicks();
            if (state.coldApplied != null
                    && current <= state.coldApplied
                    && current >= Math.max(0, state.coldApplied - 2)) {
                long remaining =
                        state.coldBefore
                                - Math.min(Integer.MAX_VALUE, (tick - state.coldSince) * 2);
                state.entity.setFreezeTicks((int) Math.max(0, remaining));
            }
            state.coldBefore = null;
            state.coldApplied = null;
        }
    }

    private void speed(State state, Player player, boolean flying) {
        float current = flying ? player.getFlySpeed() : player.getWalkSpeed();
        Float previous = flying ? state.flyBefore : state.walkBefore;
        Float applied = flying ? state.flyApplied : state.walkApplied;
        boolean present =
                state.speeds.values().stream().anyMatch(lease -> lease.value.flying == flying);
        if (!present) {
            if (previous != null && applied != null && Float.compare(current, applied) == 0) {
                if (flying) player.setFlySpeed(previous);
                else player.setWalkSpeed(previous);
            }
            if (flying) {
                state.flyBefore = null;
                state.flyApplied = null;
            } else {
                state.walkBefore = null;
                state.walkApplied = null;
            }
            return;
        }
        if (previous == null || applied != null && Float.compare(current, applied) != 0)
            previous = current;
        float value = previous;
        for (Lease<Speed> lease : state.speeds.values())
            if (lease.value.flying == flying)
                value =
                        lease.value.additive
                                ? Math.clamp(value + lease.value.amount, -1, 1)
                                : lease.value.amount;
        if (flying) {
            if (Float.compare(player.getFlySpeed(), value) != 0) player.setFlySpeed(value);
            state.flyBefore = previous;
            state.flyApplied = value;
        } else {
            if (Float.compare(player.getWalkSpeed(), value) != 0) player.setWalkSpeed(value);
            state.walkBefore = previous;
            state.walkApplied = value;
        }
    }

    private void applyPotion(State state, PotionEffectType type, Lease<PotionRequest> lease) {
        PotionEffect current = state.entity.getPotionEffect(type);
        int duration =
                lease.expires == Long.MAX_VALUE ? -1 : (int) Math.max(1, lease.expires - tick);
        PotionRequest request = lease.value;
        OwnedPotion owned = state.ownedPotions.get(type);
        boolean ours =
                owned != null
                        && current != null
                        && current.getAmplifier() == owned.amplifier
                        && current.isAmbient() == owned.ambient;
        if (!request.force
                && current != null
                && !ours
                && (current.getAmplifier() > request.amplifier
                        || current.getAmplifier() == request.amplifier
                                && (current.isInfinite()
                                        || duration != -1 && current.getDuration() > duration)))
            return;
        if (current != null
                && current.getAmplifier() == request.amplifier
                && current.isAmbient() == !request.force
                && (current.isInfinite()
                        || duration >= 0 && current.getDuration() >= Math.min(duration, 40)))
            return;
        PotionEffect previous = owned == null || !ours ? current : owned.previous;
        long since = owned == null || !ours ? tick : owned.since;
        if (current != null
                && (request.force || ours && current.getAmplifier() != request.amplifier))
            state.entity.removePotionEffect(type);
        state.entity.addPotionEffect(
                new PotionEffect(type, duration, request.amplifier, !request.force, false));
        state.ownedPotions.put(
                type, new OwnedPotion(previous, since, request.amplifier, !request.force));
    }

    private void restorePotion(State state, PotionEffectType type) {
        OwnedPotion owned = state.ownedPotions.remove(type);
        PotionEffect current = state.entity.getPotionEffect(type);
        if (current != null
                && (current.getAmplifier() != owned.amplifier
                        || current.isAmbient() != owned.ambient)) return;
        if (current != null) state.entity.removePotionEffect(type);
        PotionEffect previous = owned.previous;
        if (previous == null) return;
        int remaining =
                previous.isInfinite()
                        ? -1
                        : previous.getDuration()
                                - (int) Math.min(Integer.MAX_VALUE, tick - owned.since);
        if (remaining > 0 || remaining == -1 && previous.isInfinite())
            state.entity.addPotionEffect(
                    new PotionEffect(
                            type,
                            remaining,
                            previous.getAmplifier(),
                            previous.isAmbient(),
                            previous.hasParticles(),
                            previous.hasIcon()));
    }

    private void restore(State state) {
        restoreArmor(state);
        state.speeds.clear();
        state.flags.clear();
        state.uses.clear();
        state.immunity.clear();
        state.potions.clear();
        apply(state);
    }

    private static ItemStack withoutDamage(ItemStack item) {
        ItemStack copy = item.clone();
        if (copy.getItemMeta() instanceof Damageable meta) {
            meta.setDamage(0);
            copy.setItemMeta(meta);
        }
        return copy;
    }

    private void restoreArmor(State state) {
        if (state.entity.getEquipment() != null)
            state.armorBefore.forEach(
                    (slot, previous) -> {
                        ItemStack current = state.entity.getEquipment().getItem(slot);
                        if (current == null
                                || !withoutDamage(previous).isSimilar(withoutDamage(current))
                                || !(current.getItemMeta() instanceof Damageable meta)) return;
                        meta.setDamage(
                                Math.min(
                                        meta.getDamage(),
                                        ((Damageable) previous.getItemMeta()).getDamage()));
                        current.setItemMeta(meta);
                    });
        state.armorBefore.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void armorDamage(PlayerItemDamageEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null || state.armorUntil <= tick) return;
        for (var entry : state.armorBefore.entrySet()) {
            ItemStack equipped = event.getPlayer().getInventory().getItem(entry.getKey());
            if (equipped != null
                    && equipped.isSimilar(event.getItem())
                    && withoutDamage(equipped).isSimilar(withoutDamage(entry.getValue()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void damage(EntityDamageEvent event) {
        State state = states.get(event.getEntity().getUniqueId());
        if (state != null
                && !state.immunity.isEmpty()
                && Boolean.TRUE.equals(state.immunityApplied)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void knockback(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity entity && active(entity, Flag.NO_KNOCKBACK))
            plugin.later(
                    () -> {
                        if (entity.isValid() && !entity.isDead()) entity.setVelocity(new Vector());
                    },
                    1);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void use(PlayerInteractEvent event) {
        if (event.getItem() != null && blocked(event.getPlayer(), event.getItem().getType())) {
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        if (blocked(event.getPlayer(), event.getItem().getType())) event.setCancelled(true);
    }

    public boolean blocked(Player player, Material material) {
        State state = states.get(player.getUniqueId());
        return state != null
                && state.uses.values().stream()
                        .anyMatch(lease -> lease.value == material && lease.expires > tick);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null) return;
        Player player = event.getPlayer();
        if (active(player, Flag.FROZEN)
                && (event.getFrom().getX() != to.getX()
                        || event.getFrom().getY() != to.getY()
                        || event.getFrom().getZ() != to.getZ())) {
            Location stationary = event.getFrom().clone();
            stationary.setYaw(to.getYaw());
            stationary.setPitch(to.getPitch());
            event.setTo(stationary);
            return;
        }
        if (!active(player, Flag.WEB_WALKER)
                || !to.getWorld().isChunkLoaded(to.getBlockX() >> 4, to.getBlockZ() >> 4)
                || to.getBlock().getType() != Material.COBWEB) return;
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current == null || current.getAmplifier() == 8)
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 10, 8));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void death(EntityDeathEvent event) {
        clearTarget(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void quit(PlayerQuitEvent event) {
        clearOwned(event.getPlayer().getUniqueId());
        clearTarget(event.getPlayer());
    }
}
