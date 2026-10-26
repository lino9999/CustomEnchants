package io.github.lino9999.customenchants.enchant;

import org.bukkit.Material;

import java.util.List;
import java.util.Set;

public final class MaterialRules {
    private static final Set<String> ARMOR = Set.of("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS");

    private MaterialRules() {}

    public static boolean matches(Material material, List<String> selectors) {
        String name = material.name();
        for (String selector : selectors) {
            if (selector.equals(name) || selector.equals("ANY_EQUIPMENT")) return true;
            boolean matched =
                    switch (selector) {
                        case "SWORDS" -> name.endsWith("_SWORD");
                        case "PICKAXES" -> name.endsWith("_PICKAXE");
                        case "AXES" -> name.endsWith("_AXE");
                        case "SHOVELS" -> name.endsWith("_SHOVEL");
                        case "HOES" -> name.endsWith("_HOE");
                        case "HELMETS" -> name.endsWith("_HELMET");
                        case "CHESTPLATES" -> name.endsWith("_CHESTPLATE");
                        case "LEGGINGS" -> name.endsWith("_LEGGINGS");
                        case "BOOTS" -> name.endsWith("_BOOTS");
                        case "ARMOR" -> armor(name);
                        case "TOOLS" ->
                                name.endsWith("_PICKAXE")
                                        || name.endsWith("_SHOVEL")
                                        || name.endsWith("_AXE")
                                        || name.endsWith("_HOE")
                                        || name.equals("SHEARS")
                                        || name.equals("FISHING_ROD");
                        case "EDIBLE" -> material.isEdible();
                        default ->
                                selector.endsWith("_ARMOR")
                                        && name.startsWith(
                                                selector.substring(0, selector.length() - 5))
                                        && armor(name);
                    };
            if (matched) return true;
        }
        return false;
    }

    public static boolean armor(String name) {
        return ARMOR.stream().anyMatch(part -> name.endsWith("_" + part));
    }

    public static boolean equipment(Material material) {
        String name = material.name();
        return armor(name) || material.getMaxDurability() > 0;
    }
}
