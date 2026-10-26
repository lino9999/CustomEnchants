package io.github.lino9999.customenchants.feature;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class SkillIntegrationService {
    private static final String MCMMO_API = "com.gmail.nossr50.api.ExperienceAPI";

    private static final class Provider {
        final Plugin plugin;
        final Map<String, Method> methods = new HashMap<>();

        Provider(Plugin plugin) {
            this.plugin = plugin;
        }

        Class<?> type(String name) throws ClassNotFoundException {
            return Class.forName(name, false, plugin.getClass().getClassLoader());
        }

        Method method(String type, String name, boolean global, Class<?>... parameters)
                throws ReflectiveOperationException {
            String key = type + ":" + name;
            Method result = methods.get(key);
            if (result == null) {
                result = type(type).getMethod(name, parameters);
                if (Modifier.isStatic(result.getModifiers()) != global)
                    throw new NoSuchMethodException("Unexpected API method binding: " + name);
                methods.put(key, result);
            }
            return result;
        }
    }

    private final Map<String, Provider> providers = new HashMap<>();

    public void addExperience(Player player, String skill, int amount) {
        skill = skill(skill).toUpperCase(Locale.ROOT);
        nonnegative(amount);
        if (amount == 0) return;
        Provider provider = provider("mcMMO");
        try {
            provider.method(
                            MCMMO_API,
                            "addXP",
                            true,
                            Player.class,
                            String.class,
                            int.class,
                            String.class)
                    .invoke(null, player, skill, amount, "UNKNOWN");
        } catch (ReflectiveOperationException | LinkageError error) {
            throw failure("mcMMO", "add skill experience", error);
        }
    }

    private Provider provider(String name) {
        if (!Bukkit.isPrimaryThread())
            throw new IllegalStateException("Skill integrations must run on the server thread.");
        Plugin plugin = Bukkit.getPluginManager().getPlugin(name);
        if (plugin == null || !plugin.isEnabled()) {
            providers.remove(name);
            throw new IllegalArgumentException(
                    name + " must be installed and enabled for this action.");
        }
        Provider current = providers.get(name);
        if (current == null || current.plugin != plugin) {
            current = new Provider(plugin);
            providers.put(name, current);
        }
        return current;
    }

    private static String skill(String name) {
        if (name == null
                || name.isBlank()
                || name.length() > 256
                || name.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("A valid skill identifier is required.");
        return name;
    }

    private static void nonnegative(int amount) {
        if (amount < 0) throw new IllegalArgumentException("Skill rewards cannot be negative.");
    }

    private static IllegalArgumentException failure(
            String provider, String action, Throwable error) {
        Throwable cause =
                error instanceof InvocationTargetException invoked ? invoked.getCause() : error;
        String message =
                error instanceof ClassNotFoundException || error instanceof NoSuchMethodException
                        ? provider + " does not expose the required API to " + action + "."
                        : provider + " could not " + action + ".";
        return new IllegalArgumentException(message, cause);
    }
}
