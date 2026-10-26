package io.github.lino9999.customenchants.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record EnchantDefinition(
        String id,
        String name,
        String description,
        String group,
        Set<String> triggers,
        List<String> materials,
        Map<Integer, Level> levels,
        List<String> requires,
        List<String> conflicts,
        boolean removable,
        boolean obtainable) {
    public record Level(
            double chance,
            double cooldown,
            List<String> conditions,
            List<String> effects,
            Set<String> whitelist,
            Set<String> blacklist) {}

    public int maxLevel() {
        return levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
    }
}
