package io.github.lino9999.customenchants.enchant;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.api.EnchantApplyEvent;
import io.github.lino9999.customenchants.config.EnchantDefinition;
import io.github.lino9999.customenchants.util.Numbers;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class EnchantService {
    private final CustomEnchants plugin;
    private final ItemData data;

    public EnchantService(CustomEnchants plugin, ItemData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public EnchantDefinition definition(String id) {
        EnchantDefinition result = plugin.settings().enchants().get(id.toLowerCase(Locale.ROOT));
        if (result == null) throw new IllegalArgumentException("Unknown enchantment: " + id);
        return result;
    }

    public ItemStack book(String id, int level, int success, int destroy) {
        EnchantDefinition e = definition(id);
        if (!e.levels().containsKey(level))
            throw new IllegalArgumentException("Invalid level for " + id + ".");
        ItemStack item = new ItemStack(Material.ENCHANTED_BOOK);
        data.text(item, "kind", "book");
        data.text(item, "book_enchant", id);
        data.number(item, "book_level", level);
        data.number(item, "success", Math.clamp(success, 0, 100));
        data.number(item, "destroy", Math.clamp(destroy, 0, 100));
        refreshBook(item);
        return item;
    }

    public void refreshBook(ItemStack item) {
        EnchantDefinition e = definition(data.text(item, "book_enchant"));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(
                Text.color(
                        plugin.settings().groups().get(e.group()).color()
                                + e.name()
                                + " "
                                + Text.roman(data.number(item, "book_level"))));
        meta.setLore(
                List.of(
                        Text.color("&7" + e.description()),
                        Text.color("&aSuccess: " + data.number(item, "success") + "%"),
                        Text.color("&cDestroy: " + data.number(item, "destroy") + "%"),
                        Text.color("&7Applies to: " + String.join(", ", e.materials())),
                        Text.color("&eDrag onto equipment to apply.")));
        item.setItemMeta(meta);
    }

    public ItemStack randomBook(String group) {
        List<EnchantDefinition> available =
                plugin.settings().enchants().values().stream()
                        .filter(e -> e.group().equalsIgnoreCase(group) && e.obtainable())
                        .toList();
        if (available.isEmpty())
            throw new IllegalArgumentException(
                    "No obtainable enchantments in group " + group + ".");
        EnchantDefinition e = available.get(Numbers.random(0, available.size() - 1));
        List<Integer> levels = new ArrayList<>(e.levels().keySet());
        return book(
                e.id(),
                levels.get(Numbers.random(0, levels.size() - 1)),
                Numbers.random(25, 100),
                Numbers.random(0, 50));
    }

    public String check(ItemStack item, EnchantDefinition e, int level) {
        if (ItemData.empty(item)
                || item.getAmount() != 1
                || !MaterialRules.matches(item.getType(), e.materials()))
            return "This enchantment cannot be applied to that item.";
        if (!e.levels().containsKey(level)) return "Invalid enchantment level.";
        Map<String, Integer> existing = data.enchants(item);
        if (existing.getOrDefault(e.id(), 0) >= level)
            return "The item already has this enchantment at an equal or higher level.";
        if (!existing.containsKey(e.id()) && existing.size() >= data.slots(item))
            return "The item has no available enchantment slots.";
        for (String conflict : e.conflicts())
            if (existing.containsKey(conflict)) return "Conflicts with " + conflict + ".";
        for (String id : existing.keySet()) {
            EnchantDefinition other = plugin.settings().enchants().get(id);
            if (other != null && other.conflicts().contains(e.id()))
                return "Conflicts with " + id + ".";
        }
        for (String requirement : e.requires()) {
            String[] pair = requirement.split(":");
            if (existing.getOrDefault(pair[0], 0)
                    < (pair.length > 1 ? Integer.parseInt(pair[1]) : 1))
                return "Requires " + requirement + ".";
        }
        return null;
    }

    public boolean apply(Player player, ItemStack item, String id, int level) {
        EnchantDefinition e = definition(id);
        String problem = check(item, e, level);
        if (problem != null) throw new IllegalArgumentException(problem);
        EnchantApplyEvent event = new EnchantApplyEvent(player, item, id, level);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        Map<String, Integer> values = data.enchants(item);
        values.put(id, level);
        data.enchants(item, values);
        return true;
    }

    public void remove(ItemStack item, String id) {
        Map<String, Integer> values = data.enchants(item);
        if (values.remove(id) == null)
            throw new IllegalArgumentException("This item does not have " + id + ".");
        data.enchants(item, values);
    }
}
