package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

public final class PassiveService {
    private record OwnedPotion(int amplifier, PotionEffect previous, long since) {}

    private static final class Request {
        final Map<PotionEffectType, Integer> potions = new HashMap<>();
        final Set<PotionEffectType> cures = new HashSet<>();
        final Set<String> walkers = new HashSet<>();
        boolean flight;
    }

    private static final class State {
        final Map<PotionEffectType, OwnedPotion> potions = new HashMap<>();
        final Set<PotionEffectType> wanted = new HashSet<>();
        final Set<String> walkers = new HashSet<>();
        final Map<String, Request> requests = new HashMap<>();
        boolean flightOwned;
        boolean flightWanted;
    }

    private final CustomEnchants plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private String capturing;

    public PassiveService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public void refresh(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), k -> new State());
        state.wanted.clear();
        state.walkers.clear();
        state.flightWanted = false;
        plugin.engine().beginPassives(player);
        if (plugin.settings().enabled(player.getWorld().getName()) && !player.isDead()) {
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    player,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    null,
                                    "ARMOR_PASSIVE"));
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    player,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    null,
                                    "HAND_PASSIVE"));
        }
        plugin.engine().endPassives(player);
        Map<PotionEffectType, Integer> desired = new HashMap<>();
        for (Request request : state.requests.values()) {
            request.potions.forEach((type, amplifier) -> desired.merge(type, amplifier, Math::max));
            request.cures.forEach(player::removePotionEffect);
            state.walkers.addAll(request.walkers);
            state.flightWanted |= request.flight;
        }
        desired.forEach((type, amplifier) -> applyPotion(player, type, amplifier));
        if (state.flightWanted && !player.getAllowFlight()) {
            player.setAllowFlight(true);
            state.flightOwned = true;
        }
        for (PotionEffectType type : new HashSet<>(state.potions.keySet()))
            if (!state.wanted.contains(type)) restore(player, type, state.potions.remove(type));
        if (state.flightOwned && !state.flightWanted) {
            if (player.getGameMode() != GameMode.CREATIVE
                    && player.getGameMode() != GameMode.SPECTATOR) {
                player.setFlying(false);
                player.setAllowFlight(false);
            }
            state.flightOwned = false;
        }
    }

    public void potion(Player player, PotionEffectType type, int amplifier) {
        request(player).potions.merge(type, amplifier, Math::max);
    }

    public void cure(Player player, PotionEffectType type) {
        request(player).cures.add(type);
        player.removePotionEffect(type);
    }

    private void applyPotion(Player player, PotionEffectType type, int amplifier) {
        State state = states.computeIfAbsent(player.getUniqueId(), k -> new State());
        state.wanted.add(type);
        PotionEffect current = player.getPotionEffect(type);
        OwnedPotion owned = state.potions.get(type);
        if (current != null
                && (current.getAmplifier() > amplifier
                        || (owned == null
                                && current.getAmplifier() == amplifier
                                && current.getDuration() > duration()))) return;
        if (owned == null)
            state.potions.put(
                    type, new OwnedPotion(amplifier, current, System.currentTimeMillis()));
        else if (owned.amplifier != amplifier)
            state.potions.put(type, new OwnedPotion(amplifier, owned.previous, owned.since));
        player.addPotionEffect(new PotionEffect(type, duration(), amplifier, true, false));
    }

    public void flight(Player player) {
        request(player).flight = true;
    }

    public void walker(Player player, String fluid) {
        request(player).walkers.add(fluid);
    }

    private Request request(Player player) {
        State state = states.computeIfAbsent(player.getUniqueId(), k -> new State());
        return state.requests.computeIfAbsent(
                capturing == null ? "direct" : capturing, key -> new Request());
    }

    public boolean capture(Player player, String scope, BooleanSupplier action) {
        String previous = capturing;
        String key = player.getUniqueId() + ":" + scope;
        release(player, scope);
        capturing = key;
        boolean accepted = false;
        try {
            accepted = action.getAsBoolean();
            return accepted;
        } finally {
            capturing = previous;
            if (!accepted) release(player, scope);
        }
    }

    public void release(Player player, String scope) {
        String key = player.getUniqueId() + ":" + scope;
        for (State state : states.values()) state.requests.remove(key);
    }

    public void resume(Player player, String scope, Runnable action) {
        String previous = capturing;
        capturing = player.getUniqueId() + ":" + scope;
        try {
            action.run();
        } finally {
            capturing = previous;
        }
    }

    public Location walk(Player player, Location destination) {
        State state = states.get(player.getUniqueId());
        if (state == null
                || state.walkers.isEmpty()
                || player.isSneaking()
                || player.isFlying()
                || player.isInsideVehicle()
                || player.isGliding()
                || player.getVelocity().getY() > 0.1) return destination;
        Location below = destination.clone().subtract(0, 0.05, 0);
        if (!below.getWorld().isChunkLoaded(below.getBlockX() >> 4, below.getBlockZ() >> 4))
            return destination;
        if (!state.walkers.contains(below.getBlock().getType().name())
                || !destination.clone().add(0, 1, 0).getBlock().isPassable()) return destination;
        double surface = below.getBlockY() + 1.01;
        if (destination.getY() < surface - 0.45 || destination.getY() > surface + 0.5)
            return destination;
        destination = destination.clone();
        destination.setY(surface);
        player.setFallDistance(0);
        return destination;
    }

    public void clear(Player player) {
        if (plugin.engine() != null) plugin.engine().clearPassives(player);
        State state = states.remove(player.getUniqueId());
        if (state == null) return;
        state.potions.forEach((type, owned) -> restore(player, type, owned));
        if (state.flightOwned
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }
    }

    private int duration() {
        return plugin.settings().periodicTicks() + 40;
    }

    private void restore(Player player, PotionEffectType type, OwnedPotion owned) {
        PotionEffect current = player.getPotionEffect(type);
        if (current != null
                && current.getAmplifier() == owned.amplifier
                && current.isAmbient()
                && current.getDuration() <= duration()) {
            player.removePotionEffect(type);
            if (owned.previous != null) {
                int left =
                        owned.previous.getDuration()
                                - (int) ((System.currentTimeMillis() - owned.since) / 50);
                if (left > 0)
                    player.addPotionEffect(
                            new PotionEffect(
                                    type,
                                    left,
                                    owned.previous.getAmplifier(),
                                    owned.previous.isAmbient(),
                                    owned.previous.hasParticles(),
                                    owned.previous.hasIcon()));
            }
        }
    }
}
