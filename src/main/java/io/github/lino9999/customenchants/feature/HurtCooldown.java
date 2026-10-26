package io.github.lino9999.customenchants.feature;

import org.bukkit.entity.LivingEntity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

final class HurtCooldown {
    private record Access(Method handle, Field ticks) {}

    private static final ClassValue<Access> ACCESS =
            new ClassValue<>() {
                @Override
                protected Access computeValue(Class<?> type) {
                    try {
                        Method handle = type.getMethod("getHandle");
                        Field ticks = handle.getReturnType().getField("damageCooldownTime");
                        return ticks.getType() == int.class
                                ? new Access(handle, ticks)
                                : new Access(null, null);
                    } catch (NoSuchMethodException | NoSuchFieldException error) {
                        return new Access(null, null);
                    }
                }
            };

    private HurtCooldown() {}

    static int get(LivingEntity entity) {
        Access access = ACCESS.get(entity.getClass());
        if (access.ticks == null) return entity.getNoDamageTicks();
        try {
            return access.ticks.getInt(access.handle.invoke(entity));
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("The server hurt cooldown could not be read.", error);
        }
    }

    static void set(LivingEntity entity, int ticks) {
        Access access = ACCESS.get(entity.getClass());
        if (access.ticks == null) {
            entity.setNoDamageTicks(ticks);
            return;
        }
        try {
            access.ticks.setInt(access.handle.invoke(entity), ticks);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException(
                    "The server hurt cooldown could not be updated.", error);
        }
    }
}
