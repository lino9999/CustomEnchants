package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.config.EnchantDefinition;
import io.github.lino9999.customenchants.config.Settings;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.enchant.MaterialRules;
import io.github.lino9999.customenchants.util.Inventories;
import io.github.lino9999.customenchants.util.Numbers;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class EquipmentService {
    @FunctionalInterface
    public interface Activation {
        void run(EffectContext context, String id, EnchantDefinition.Level level);
    }

    private final CustomEnchants plugin;

    public EquipmentService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public String activeSet(Player player) {
        String id = null;
        String[] expected = {"boots", "leggings", "chestplate", "helmet"};
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            if (ItemData.empty(armor[i])) return "";
            String current = plugin.items().text(armor[i], "armor_set");
            if (current.isEmpty()
                    || !plugin.items().text(armor[i], "piece").equals(expected[i])
                    || !armor[i].getType()
                            .name()
                            .endsWith("_" + expected[i].toUpperCase(Locale.ROOT))) return "";
            if (id == null) id = current;
            else if (!id.equals(current)) return "";
        }
        return id == null ? "" : id;
    }

    public void activate(EffectContext context, Activation activate) {
        String setId = activeSet(context.owner);
        Settings.Equipment set = plugin.settings().sets().get(setId);
        if (set != null && set.events().containsKey(context.trigger))
            activate.run(context, "set:" + setId, set.events().get(context.trigger));
        String weaponId = plugin.items().text(context.item, "weapon");
        Settings.Equipment weapon = plugin.settings().weapons().get(weaponId);
        if (weapon != null
                && (weapon.requiredSet().isEmpty() || weapon.requiredSet().equals(setId))
                && weapon.events().containsKey(context.trigger))
            activate.run(context, "weapon:" + weaponId, weapon.events().get(context.trigger));
    }

    public List<ItemStack> equipment(String type, String id) {
        Settings.Equipment equipment =
                (type.equals("set") ? plugin.settings().sets() : plugin.settings().weapons())
                        .get(id);
        if (equipment == null) throw new IllegalArgumentException("Unknown " + type + ": " + id);
        List<ItemStack> result = new ArrayList<>();
        equipment
                .pieces()
                .forEach(
                        (slot, piece) -> {
                            ItemStack item =
                                    new ItemStack(
                                            Objects.requireNonNull(
                                                    Material.matchMaterial(piece.material())));
                            ItemMeta meta = item.getItemMeta();
                            meta.setDisplayName(Text.color(piece.name()));
                            meta.setLore(
                                    piece.lore().isEmpty()
                                            ? List.of(
                                                    Text.color("&7" + equipment.name()),
                                                    Text.color(
                                                            type.equals("set")
                                                                    ? "&7Wear the full set to"
                                                                            + " activate its bonus."
                                                                    : "&7Required set: "
                                                                            + (equipment
                                                                                            .requiredSet()
                                                                                            .isEmpty()
                                                                                    ? "None"
                                                                                    : equipment
                                                                                            .requiredSet())))
                                            : piece.lore().stream().map(Text::color).toList());
                            if (piece.modelData() > 0) meta.setCustomModelData(piece.modelData());
                            item.setItemMeta(meta);
                            plugin.items()
                                    .text(item, type.equals("set") ? "armor_set" : "weapon", id);
                            plugin.items().text(item, "piece", slot);
                            Map<String, Integer> enchants = new LinkedHashMap<>();
                            for (String entry : piece.enchants()) {
                                String[] pair = entry.split(":", 2);
                                if (pair.length < 2) continue;
                                String[] range = pair[1].split("-", 2);
                                int level =
                                        Numbers.random(
                                                Integer.parseInt(range[0]),
                                                Integer.parseInt(range[range.length - 1]));
                                EnchantDefinition custom =
                                        plugin.settings().enchants().get(pair[0]);
                                if (custom != null
                                        && MaterialRules.matches(
                                                item.getType(), custom.materials()))
                                    enchants.put(pair[0], Math.min(level, custom.maxLevel()));
                                else {
                                    var vanilla =
                                            Registry.ENCHANTMENT.get(
                                                    NamespacedKey.minecraft(pair[0]));
                                    if (vanilla != null && vanilla.canEnchantItem(item))
                                        item.addEnchantment(
                                                vanilla,
                                                Math.clamp(level, 1, vanilla.getMaxLevel()));
                                }
                            }
                            plugin.items().enchants(item, enchants);
                            result.add(item);
                        });
        return result;
    }

    public List<ItemStack> kitItems(String id) {
        Settings.Kit kit = plugin.settings().kits().get(id);
        if (kit == null) throw new IllegalArgumentException("Unknown kit: " + id);
        List<ItemStack> items = new ArrayList<>();
        for (String entry : kit.items()) {
            String[] pair = entry.split(":", 3);
            switch (pair[0]) {
                case "set", "weapon" -> items.addAll(equipment(pair[0], pair[1]));
                case "tome" -> items.add(plugin.enchants().randomBook(pair[1]));
                case "item" ->
                        items.add(
                                plugin.specialItems()
                                        .create(
                                                pair[1],
                                                pair.length > 2 ? Integer.parseInt(pair[2]) : 1,
                                                ""));
                default -> throw new IllegalArgumentException("Unknown kit item type: " + pair[0]);
            }
        }
        return items;
    }

    public void claim(Player player, String id, boolean bypass) {
        Settings.Kit kit = plugin.settings().kits().get(id);
        if (kit == null) throw new IllegalArgumentException("Unknown kit: " + id);
        if (!bypass && !player.hasPermission("customenchants.kit." + id))
            throw new IllegalArgumentException("You do not have permission to claim this kit.");
        NamespacedKey key = plugin.items().key("kit_" + id);
        long ready =
                player.getPersistentDataContainer().getOrDefault(key, PersistentDataType.LONG, 0L);
        long now = System.currentTimeMillis();
        if (!bypass && ready > now)
            throw new IllegalArgumentException(
                    "This kit is available in " + ((ready - now + 999) / 1000) + " seconds.");
        List<ItemStack> items = kitItems(id);
        Inventories.give(player, items);
        if (!bypass)
            player.getPersistentDataContainer()
                    .set(key, PersistentDataType.LONG, now + kit.cooldownSeconds() * 1000);
    }

    public void reset(Player player, String id) {
        if (!plugin.settings().kits().containsKey(id))
            throw new IllegalArgumentException("Unknown kit: " + id);
        player.getPersistentDataContainer().remove(plugin.items().key("kit_" + id));
    }
}
