package io.github.lino9999.customenchants.effect;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

public record EffectTarget(LivingEntity entity, Location position) {
    public EffectTarget {
        if ((entity == null) == (position == null))
            throw new IllegalArgumentException(
                    "An effect target requires exactly one entity or position.");
        if (position != null) position = position.clone();
    }

    public static EffectTarget entity(LivingEntity entity) {
        return new EffectTarget(entity, null);
    }

    public static EffectTarget at(Location location) {
        return new EffectTarget(null, location);
    }

    @Override
    public Location position() {
        return entity == null ? position.clone() : entity.getLocation();
    }
}
