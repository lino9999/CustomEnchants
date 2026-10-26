package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class TargetResolver {
    private final CustomEnchants plugin;

    public TargetResolver(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public List<EffectTarget> resolve(String input, EffectContext context) {
        TargetSelector selector = TargetSelector.parse(input);
        return switch (selector.name()) {
            case "self" -> entity(context.owner);
            case "attacker" -> entity(context.attacker);
            case "defender", "victim" -> entity(context.victim);
            case "all_players" ->
                    Bukkit.getOnlinePlayers().stream()
                            .limit(plugin.settings().maxTargets())
                            .map(EffectTarget::entity)
                            .toList();
            case "player_by_name" -> entity(Bukkit.getPlayerExact(selector.text("filter", "")));
            case "eye_pos" -> location(context.owner.getEyeLocation());
            case "hit_block" ->
                    context.block == null ? List.of() : location(context.block.getLocation());
            case "offset" ->
                    location(
                            context.owner
                                    .getLocation()
                                    .add(
                                            selector.number("x", 0),
                                            selector.number("y", 0),
                                            selector.number("z", 0)));
            case "sight_block" -> {
                int distance = (int) Math.clamp(selector.number("range", 1), 1, 128);
                Location eye = context.owner.getEyeLocation();
                Location destination =
                        context.owner.getLocation().add(eye.getDirection().multiply(distance));
                for (double step = 0; step <= distance; step += 0.2) {
                    Location point = eye.clone().add(eye.getDirection().multiply(step));
                    if (location(point).isEmpty()) break;
                    if (!point.getBlock().isPassable()) {
                        destination = point.getBlock().getLocation();
                        break;
                    }
                }
                yield location(destination);
            }
            case "area" -> nearby(context, selector, selector.number("size", 1), false, false);
            case "nearest_player" ->
                    nearby(context, selector, selector.number("size", 0), true, false);
            case "sight_entities" ->
                    nearby(context, selector, selector.number("range", 20), false, true);
            case "cube_area", "tunnel_area", "vein_area" ->
                    plugin.blocks().select(context, selector).stream()
                            .map(block -> EffectTarget.at(block.getLocation()))
                            .toList();
            default -> throw new IllegalArgumentException("Unsupported target scope: " + selector.name());
        };
    }

    private List<EffectTarget> nearby(
            EffectContext context,
            TargetSelector selector,
            double requested,
            boolean nearest,
            boolean sight) {
        double radius = Math.clamp(requested, 0, 128);
        if (radius == 0) return List.of();
        String kind = nearest ? "PLAYERS" : selector.text("filter", "ALL").toUpperCase(Locale.ROOT);
        if (kind.equals("LIVING")) kind = "ALL";
        if (!List.of("ALL", "MOBS", "PLAYERS").contains(kind))
            throw new IllegalArgumentException("Unknown target filter: " + kind);
        String filterKind = kind;
        Location origin = context.owner.getLocation();
        int limit =
                nearest
                        ? 1
                        : (int)
                                Math.clamp(
                                        selector.number("max", sight ? 5 : 20),
                                        0,
                                        plugin.settings().maxTargets());
        double angle = selector.number("cone", 40) / 100;
        var stream =
                context.owner.getNearbyEntities(radius, radius, radius).stream()
                        .filter(LivingEntity.class::isInstance)
                        .map(LivingEntity.class::cast)
                        .filter(
                                entity ->
                                        entity != context.owner
                                                && !entity.isDead()
                                                && entity.getLocation().distanceSquared(origin)
                                                        <= radius * radius)
                        .filter(
                                entity ->
                                        filterKind.equals("ALL")
                                                || filterKind.equals("PLAYERS")
                                                        == (entity instanceof Player));
        if (sight)
            stream =
                    stream.filter(
                            entity -> {
                                var direction =
                                        entity.getLocation()
                                                .toVector()
                                                .subtract(
                                                        context.owner.getEyeLocation().toVector());
                                return direction.lengthSquared() > 0
                                        && context.owner.hasLineOfSight(entity)
                                        && context.owner
                                                        .getEyeLocation()
                                                        .getDirection()
                                                        .angle(direction)
                                                < angle;
                            });
        if (nearest)
            stream =
                    stream.sorted(
                            Comparator.comparingDouble(
                                    entity -> entity.getLocation().distanceSquared(origin)));
        return stream.limit(limit).map(EffectTarget::entity).toList();
    }

    private static List<EffectTarget> entity(LivingEntity entity) {
        return entity == null ? List.of() : List.of(EffectTarget.entity(entity));
    }

    private static List<EffectTarget> location(Location location) {
        if (location.getWorld() == null
                || !Double.isFinite(location.getX())
                || !Double.isFinite(location.getY())
                || !Double.isFinite(location.getZ()))
            throw new IllegalArgumentException("Invalid target location.");
        if (location.getY() < location.getWorld().getMinHeight()
                || location.getY() >= location.getWorld().getMaxHeight()
                || !location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4))
            return List.of();
        return List.of(EffectTarget.at(location));
    }
}
