package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;

import java.util.Locale;
import java.util.Map;

final class VisualEffects {
    private static final Map<String, Color> COLORS =
            Map.ofEntries(
                    Map.entry("RED", Color.RED), Map.entry("ORANGE", Color.ORANGE),
                    Map.entry("BLUE", Color.BLUE), Map.entry("GREEN", Color.GREEN),
                    Map.entry("YELLOW", Color.YELLOW), Map.entry("PURPLE", Color.PURPLE),
                    Map.entry("BLACK", Color.BLACK), Map.entry("WHITE", Color.WHITE),
                    Map.entry("GRAY", Color.GRAY), Map.entry("AQUA", Color.AQUA),
                    Map.entry("LIME", Color.LIME), Map.entry("SILVER", Color.SILVER));
    private final CustomEnchants plugin;

    VisualEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register("launch_firework", (c, t, a) -> firework(c, t.getLocation(), a));
        registry.registerLocation("launch_firework", this::firework);
        registry.register(
                "trace_particle_beam", (c, t, a) -> line(c, t.getEyeLocation(), t == c.owner, a));
        registry.registerLocation(
                "trace_particle_beam", (c, t, a) -> line(c, t, t.equals(c.owner.getLocation()), a));
    }

    private void firework(EffectContext context, Location location, String[] args) {
        Color color = color(EffectRegistry.argument(args, 0, "RED"));
        Color fade = color(EffectRegistry.argument(args, 1, "ORANGE"));
        FireworkEffect.Type type =
                FireworkEffect.Type.valueOf(
                        EffectRegistry.argument(args, 2, "BALL").toUpperCase(Locale.ROOT));
        int power =
                Math.clamp(
                        StateEffects.integer(args, 3, 1, Integer.MIN_VALUE, Integer.MAX_VALUE),
                        0,
                        4);
        boolean trail = flag(args, 4);
        boolean flicker = flag(args, 5);
        FireworkEffect effect =
                FireworkEffect.builder()
                        .withColor(color)
                        .withFade(fade)
                        .with(type)
                        .trail(trail)
                        .flicker(flicker)
                        .build();
        if (!loaded(location) || plugin.managedEntityCount() >= 128) return;
        Firework firework =
                location.getWorld()
                        .spawn(
                                location,
                                Firework.class,
                                rocket -> {
                                    var meta = rocket.getFireworkMeta();
                                    meta.clearEffects();
                                    meta.addEffect(effect);
                                    meta.setPower(power);
                                    rocket.setFireworkMeta(meta);
                                    rocket.setShooter(context.owner);
                                    plugin.spawns().own(rocket, context.owner);
                                });
        if (firework.isValid()) plugin.trackEntity(firework, 200);
    }

    private void line(EffectContext context, Location destination, boolean main, String[] args) {
        Particle particle =
                Particle.valueOf(UtilityEffects.required(args, 0).toUpperCase(Locale.ROOT));
        UtilityEffects.required(args, 1);
        UtilityEffects.required(args, 2);
        int amount = StateEffects.integer(args, 1, 1, 0, 64);
        int points = StateEffects.integer(args, 2, 1, 1, 256);
        if (Math.max(1, amount) * points > 4096)
            throw new IllegalArgumentException("A particle line cannot exceed 4096 particles.");
        if (particle.getDataType() != Void.class)
            throw new IllegalArgumentException(
                    "Particle lines require a particle without extra data.");
        LivingEntity origin = main ? LivingEffects.other(context, context.owner) : context.owner;
        if (origin == null || origin.getWorld() != destination.getWorld()) return;
        Location start = origin.getLocation();
        if (!loaded(start) || !loaded(destination)) return;
        if (start.distanceSquared(destination) > 16384)
            throw new IllegalArgumentException("A particle line cannot exceed 128 blocks.");
        var step = destination.toVector().subtract(start.toVector()).multiply(1.0 / points);
        for (int i = 0; i < points; i++) {
            Location sample = start.clone().add(step.clone().multiply(i));
            if (loaded(sample))
                sample.getWorld().spawnParticle(particle, sample, amount, 0, 0, 0, 0);
        }
    }

    private static Color color(String input) {
        if (!input.contains(","))
            return COLORS.getOrDefault(input.toUpperCase(Locale.ROOT), Color.RED);
        String[] parts = input.split(",", -1);
        if (parts.length != 3)
            throw new IllegalArgumentException("Colors require three RGB components.");
        return Color.fromRGB(
                Numbers.integer(parts[0].trim(), 0, 255),
                Numbers.integer(parts[1].trim(), 0, 255),
                Numbers.integer(parts[2].trim(), 0, 255));
    }

    private static boolean flag(String[] args, int index) {
        String value = EffectRegistry.argument(args, index, "true");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Firework flags must be true or false.");
        return Boolean.parseBoolean(value);
    }

    private static boolean loaded(Location location) {
        return location.getWorld() != null
                && location.getY() >= location.getWorld().getMinHeight()
                && location.getY() < location.getWorld().getMaxHeight()
                && location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
