package io.github.lino9999.customenchants.enchant;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.config.EnchantDefinition;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

public final class ItemData {
    private final CustomEnchants plugin;
    private final NamespacedKey enchants;
    private final NamespacedKey rendered;

    public ItemData(CustomEnchants plugin) {
        this.plugin = plugin;
        enchants = key("enchants");
        rendered = key("rendered_lore");
    }

    public NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    public Map<String, Integer> enchants(ItemStack item) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (empty(item) || !item.hasItemMeta()) return result;
        String value =
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .getOrDefault(enchants, PersistentDataType.STRING, "");
        if (value.length() > 16384) return result;
        for (String entry : value.split(";")) {
            String[] pair = entry.split("=");
            if (pair.length != 2) continue;
            try {
                int level = Integer.parseInt(pair[1]);
                if (level > 0 && level <= 100 && pair[0].matches("[a-z0-9_]{1,64}"))
                    result.put(pair[0], level);
            } catch (NumberFormatException ignored) {
            }
        }
        return result;
    }

    public void enchants(ItemStack item, Map<String, Integer> values) {
        ItemMeta meta = item.getItemMeta();
        StringJoiner encoded = new StringJoiner(";");
        values.forEach((id, level) -> encoded.add(id + "=" + level));
        meta.getPersistentDataContainer()
                .set(enchants, PersistentDataType.STRING, encoded.toString());
        item.setItemMeta(meta);
        render(item);
    }

    public String text(ItemStack item, String key) {
        if (empty(item) || !item.hasItemMeta()) return "";
        return item.getItemMeta()
                .getPersistentDataContainer()
                .getOrDefault(key(key), PersistentDataType.STRING, "");
    }

    public void text(ItemStack item, String key, String value) {
        ItemMeta meta = item.getItemMeta();
        if (value.isEmpty()) meta.getPersistentDataContainer().remove(key(key));
        else meta.getPersistentDataContainer().set(key(key), PersistentDataType.STRING, value);
        item.setItemMeta(meta);
    }

    public int number(ItemStack item, String key) {
        if (empty(item) || !item.hasItemMeta()) return 0;
        return Math.max(
                0,
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .getOrDefault(key(key), PersistentDataType.INTEGER, 0));
    }

    public void number(ItemStack item, String key, int value) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer()
                .set(key(key), PersistentDataType.INTEGER, Math.max(0, value));
        item.setItemMeta(meta);
    }

    public void increment(ItemStack item, String key, int delta) {
        number(item, key, (int) Math.clamp((long) number(item, key) + delta, 0, Integer.MAX_VALUE));
        render(item);
    }

    public int slots(ItemStack item) {
        return Math.min(
                plugin.settings().maxSlots(),
                plugin.settings().slots() + number(item, "extra_slots"));
    }

    public void render(ItemStack item) {
        if (empty(item)) return;
        ItemMeta meta = item.getItemMeta();
        List<String> lore =
                meta.hasLore()
                        ? new ArrayList<>(Objects.requireNonNull(meta.getLore()))
                        : new ArrayList<>();
        String old =
                meta.getPersistentDataContainer()
                        .getOrDefault(rendered, PersistentDataType.STRING, "");
        if (!old.isEmpty()) for (String line : old.split("\n")) lore.remove(line);
        List<String> generated = new ArrayList<>();
        enchants(item).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(
                        entry -> {
                            EnchantDefinition definition =
                                    plugin.settings().enchants().get(entry.getKey());
                            if (definition != null) {
                                String color =
                                        plugin.settings().groups().get(definition.group()).color();
                                generated.add(
                                        Text.color(
                                                color
                                                        + definition.name()
                                                        + " "
                                                        + Text.roman(entry.getValue())));
                            }
                        });
        if (number(item, "aegis_scroll") > 0)
            generated.add(Text.color("&fProtected from failed tomes"));
        if (number(item, "eternal_scroll") > 0) generated.add(Text.color("&eRetained on death"));
        if (number(item, "souls") > 0)
            generated.add(Text.color("&bSouls: " + number(item, "souls")));
        for (String tracker : List.of("mining", "angling", "bounty", "slayer", "souls")) {
            if (number(item, "tracker_" + tracker) > 0)
                generated.add(
                        Text.color(
                                "&7"
                                        + tracker.substring(0, 1).toUpperCase(Locale.ROOT)
                                        + tracker.substring(1)
                                        + ": "
                                        + number(item, "count_" + tracker)));
        }
        lore.addAll(generated);
        meta.setLore(lore.isEmpty() ? null : lore);
        meta.getPersistentDataContainer()
                .set(rendered, PersistentDataType.STRING, String.join("\n", generated));
        item.setItemMeta(meta);
    }

    public static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() < 1;
    }
}
