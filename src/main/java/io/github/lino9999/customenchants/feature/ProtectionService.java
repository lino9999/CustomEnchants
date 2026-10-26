package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

import java.lang.reflect.Array;

public final class ProtectionService {
    private final CustomEnchants plugin;
    private boolean warned;

    public ProtectionService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public boolean allowed(Player owner, LivingEntity target) {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null) return true;
        try {
            Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard");
            Object instance = worldGuard.getMethod("getInstance").invoke(null);
            Object platform = worldGuard.getMethod("getPlatform").invoke(instance);
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Object query = container.getClass().getMethod("createQuery").invoke(container);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter");
            Object location =
                    adapter.getMethod("adapt", Location.class).invoke(null, target.getLocation());
            Class<?> flagType = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag");
            Object flags = Array.newInstance(flagType, 1);
            Object flag =
                    Class.forName("com.sk89q.worldguard.protection.flags.Flags")
                            .getField(target instanceof Player ? "PVP" : "DAMAGE_ANIMALS")
                            .get(null);
            Array.set(flags, 0, flag);
            return (boolean)
                    query.getClass()
                            .getMethod(
                                    "testState",
                                    Class.forName("com.sk89q.worldedit.util.Location"),
                                    Class.forName(
                                            "com.sk89q.worldguard.protection.association.RegionAssociable"),
                                    flags.getClass())
                            .invoke(query, location, null, flags);
        } catch (ReflectiveOperationException | LinkageError error) {
            if (!warned) {
                plugin.getLogger()
                        .warning(
                                "WorldGuard protection could not be queried; enchantment effects on"
                                        + " other entities are blocked.");
                warned = true;
            }
            return false;
        }
    }
}
