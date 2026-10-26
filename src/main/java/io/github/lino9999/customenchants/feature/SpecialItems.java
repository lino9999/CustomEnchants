package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.enchant.MaterialRules;
import io.github.lino9999.customenchants.util.Inventories;
import io.github.lino9999.customenchants.util.Numbers;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

public final class SpecialItems {
    public static final Map<String, Material> TYPES;

    static {
        Map<String, Material> types = new LinkedHashMap<>();
        types.put("aegis_scroll", Material.PAPER);
        types.put("eternal_scroll", Material.PAPER);
        types.put("unbinding_scroll", Material.INK_SAC);
        types.put("arcane_dust", Material.SUGAR);
        types.put("sealed_dust", Material.GUNPOWDER);
        types.put("inert_ash", Material.GLOWSTONE_DUST);
        types.put("rate_crystal", Material.PAPER);
        types.put("name_tag_scroll", Material.NAME_TAG);
        types.put("slot_expander", Material.NETHER_STAR);
        types.put("armor_core", Material.MAGMA_CREAM);
        types.put("weapon_core", Material.MAGMA_CREAM);
        types.put("tool_core", Material.MAGMA_CREAM);
        types.put("mining_counter", Material.COMPASS);
        types.put("angling_counter", Material.COMPASS);
        types.put("bounty_counter", Material.COMPASS);
        types.put("slayer_counter", Material.COMPASS);
        types.put("soul_harvester", Material.ENDER_EYE);
        types.put("soul_crystal", Material.AMETHYST_SHARD);
        types.put("lore_sorter", Material.PAPER);
        types.put("mystery_tome", Material.BOOK);
        TYPES = Collections.unmodifiableMap(types);
    }

    private final CustomEnchants plugin;

    public SpecialItems(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(String type, int value, String group) {
        Material material = TYPES.get(type);
        if (material == null) throw new IllegalArgumentException("Unknown special item: " + type);
        if (!group.isEmpty() && !plugin.settings().groups().containsKey(group))
            throw new IllegalArgumentException("Unknown rarity: " + group);
        ItemStack item = new ItemStack(material);
        plugin.items().text(item, "kind", type);
        plugin.items().number(item, "value", Math.clamp(value, 1, 1000000));
        plugin.items().text(item, "group", group);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color("&d" + title(type)));
        meta.setLore(
                List.of(
                        Text.color("&7Value: " + value),
                        Text.color("&7Rarity: " + (group.isEmpty() ? "Any" : group)),
                        Text.color(
                                type.equals("sealed_dust")
                                                || type.equals("inert_ash")
                                                || type.equals("mystery_tome")
                                        ? "&eRight-click to open."
                                        : "&eDrag onto an item to apply.")));
        item.setItemMeta(meta);
        return item;
    }

    public boolean open(Player player, ItemStack item) {
        String kind = plugin.items().text(item, "kind");
        ItemStack reward;
        switch (kind) {
            case "mystery_tome" -> reward = plugin.enchants().randomBook(group(item));
            case "sealed_dust" ->
                    reward =
                            create(
                                    Numbers.roll(25) ? "arcane_dust" : "inert_ash",
                                    Numbers.random(1, 15),
                                    plugin.items().text(item, "group"));
            case "inert_ash" -> {
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
                player.updateInventory();
                plugin.message(player, "dust-empty");
                return true;
            }
            default -> {
                return false;
            }
        }
        if (!Inventories.fits(player, List.of(reward)))
            throw new IllegalArgumentException("There is not enough inventory space.");
        item.setAmount(item.getAmount() - 1);
        if (item.getAmount() <= 0) player.getInventory().setItemInMainHand(null);
        player.getInventory().addItem(reward);
        player.updateInventory();
        return true;
    }

    public ItemStack apply(Player player, ItemStack cursor, ItemStack original) {
        String kind = plugin.items().text(cursor, "kind");
        if (kind.isEmpty()) return null;
        if (ItemData.empty(original) || original.getAmount() != 1)
            throw new IllegalArgumentException("Select one equipment item or tome.");
        ItemStack target = original.clone();
        ItemData data = plugin.items();
        int value = data.number(cursor, "value");
        if (kind.equals("book")) {
            if (!player.hasPermission("customenchants.apply"))
                throw new IllegalArgumentException("You do not have permission to apply tomes.");
            String id = data.text(cursor, "book_enchant");
            int level = data.number(cursor, "book_level");
            String problem =
                    plugin.enchants().check(target, plugin.enchants().definition(id), level);
            if (problem != null) throw new IllegalArgumentException(problem);
            if (Numbers.roll(data.number(cursor, "success"))) {
                if (!plugin.enchants().apply(player, target, id, level))
                    throw new IllegalArgumentException("Application was cancelled.");
                plugin.message(player, "book-success");
            } else {
                if (data.number(target, "aegis_scroll") > 0) data.number(target, "aegis_scroll", 0);
                else if (plugin.settings().destroyOnFailure()
                        && Numbers.roll(data.number(cursor, "destroy")))
                    target = new ItemStack(Material.AIR);
                if (!ItemData.empty(target)) data.render(target);
                plugin.message(player, "book-failed");
            }
            return target;
        }
        if (!player.hasPermission("customenchants.items"))
            throw new IllegalArgumentException("You do not have permission to use special items.");
        if (kind.equals("arcane_dust") || kind.equals("rate_crystal")) {
            if (!data.text(target, "kind").equals("book"))
                throw new IllegalArgumentException("This item requires an enchantment tome.");
            var enchantment = plugin.enchants().definition(data.text(target, "book_enchant"));
            String group = data.text(cursor, "group");
            if (!group.isEmpty() && !group.equals(enchantment.group()))
                throw new IllegalArgumentException("The rarities must match.");
            if (kind.equals("arcane_dust")) {
                if (data.number(target, "success") >= 100)
                    throw new IllegalArgumentException("The tome already has 100% success.");
                data.number(
                        target, "success", Math.min(100, data.number(target, "success") + value));
                data.number(target, "destroy", Math.max(0, data.number(target, "destroy") - value));
            } else {
                data.number(target, "success", Numbers.random(1, 100));
                data.number(target, "destroy", Numbers.random(0, 100));
            }
            plugin.enchants().refreshBook(target);
            return target;
        }
        if (!MaterialRules.equipment(target.getType()))
            throw new IllegalArgumentException("This item requires equipment.");
        switch (kind) {
            case "aegis_scroll", "eternal_scroll" -> {
                if (data.number(target, kind) > 0)
                    throw new IllegalArgumentException("The item is already protected.");
                data.number(target, kind, 1);
            }
            case "slot_expander", "armor_core", "weapon_core", "tool_core" -> {
                String name = target.getType().name();
                if (kind.equals("armor_core") && !MaterialRules.armor(name))
                    throw new IllegalArgumentException("Select an armor piece.");
                if (kind.equals("weapon_core")
                        && !(name.endsWith("_SWORD")
                                || name.endsWith("_AXE")
                                || name.endsWith("BOW")
                                || name.equals("TRIDENT")
                                || name.equals("MACE")
                                || name.endsWith("_SPEAR")))
                    throw new IllegalArgumentException("Select a weapon.");
                if (kind.equals("tool_core")
                        && !(name.endsWith("_PICKAXE")
                                || name.endsWith("_SHOVEL")
                                || name.endsWith("_HOE")
                                || name.equals("FISHING_ROD")))
                    throw new IllegalArgumentException("Select a tool.");
                int remaining = plugin.settings().maxSlots() - data.slots(target);
                if (remaining <= 0)
                    throw new IllegalArgumentException(
                            "The item already has the maximum number of slots.");
                data.increment(target, "extra_slots", Math.min(value, remaining));
            }
            case "soul_crystal" -> data.increment(target, "souls", value);
            case "soul_harvester" -> {
                if (data.number(target, "tracker_souls") > 0)
                    throw new IllegalArgumentException("The item already has a soul harvester.");
                data.number(target, "tracker_souls", 1);
            }
            case "mining_counter", "angling_counter", "bounty_counter", "slayer_counter" -> {
                String tracker = kind.substring(0, kind.length() - 8);
                if (data.number(target, "tracker_" + tracker) > 0)
                    throw new IllegalArgumentException("The item already has that counter.");
                data.number(target, "tracker_" + tracker, 1);
            }
            case "unbinding_scroll" -> {
                List<String> removable =
                        data.enchants(target).keySet().stream()
                                .filter(
                                        id ->
                                                plugin.settings().enchants().containsKey(id)
                                                        && plugin.enchants()
                                                                .definition(id)
                                                                .removable())
                                .toList();
                if (removable.isEmpty())
                    throw new IllegalArgumentException("The item has no extractable enchantments.");
                String id = removable.get(Numbers.random(0, removable.size() - 1));
                ItemStack book =
                        plugin.enchants()
                                .book(id, data.enchants(target).get(id), Math.min(100, value), 0);
                if (!Inventories.fits(player, List.of(book)))
                    throw new IllegalArgumentException("There is not enough inventory space.");
                plugin.enchants().remove(target, id);
                player.getInventory().addItem(book);
            }
            case "lore_sorter" -> data.render(target);
            case "name_tag_scroll" ->
                    throw new IllegalArgumentException(
                            "Hold the equipment and use /ce rename <name> with a name tag scroll"
                                    + " in your inventory.");
            default -> throw new IllegalArgumentException("Right-click this item to use it.");
        }
        data.render(target);
        return target;
    }

    public void rename(Player player, String name) {
        if (name.isBlank() || name.length() > 64)
            throw new IllegalArgumentException("The name must contain 1 to 64 characters.");
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemData.empty(item) || !MaterialRules.equipment(item.getType()))
            throw new IllegalArgumentException("Hold an equipment item.");
        int tag = -1;
        for (int i = 0; i < 36; i++)
            if (plugin.items()
                    .text(player.getInventory().getItem(i), "kind")
                    .equals("name_tag_scroll")) {
                tag = i;
                break;
            }
        if (tag < 0)
            throw new IllegalArgumentException("You need a name tag scroll in your inventory.");
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        item.setItemMeta(meta);
        ItemStack token = player.getInventory().getItem(tag);
        if (token != null) {
            if (token.getAmount() <= 1) player.getInventory().setItem(tag, null);
            else {
                token.setAmount(token.getAmount() - 1);
                player.getInventory().setItem(tag, token);
            }
        }
        player.updateInventory();
    }

    private String group(ItemStack item) {
        String group = plugin.items().text(item, "group");
        return group.isEmpty() ? "COMMON" : group;
    }

    public static String title(String input) {
        StringJoiner result = new StringJoiner(" ");
        for (String word : input.split("_"))
            result.add(word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
        return result.toString();
    }
}
