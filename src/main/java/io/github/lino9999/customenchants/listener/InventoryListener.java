package io.github.lino9999.customenchants.listener;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.feature.MenuService;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public final class InventoryListener implements Listener {
    private final CustomEnchants plugin;

    public InventoryListener(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || event.getView().getTopInventory().getHolder() instanceof MenuService.Menu)
            return;
        if (event.getView().getTopInventory() instanceof AnvilInventory anvil
                && event.getRawSlot() == 2
                && customAnvil(anvil)) {
            event.setCancelled(true);
            if (event.getClick() != ClickType.LEFT || !ItemData.empty(event.getCursor())) return;
            try {
                ItemStack base = anvil.getItem(0), book = anvil.getItem(1);
                if (ItemData.empty(base) || ItemData.empty(book) || player.getLevel() < 3)
                    throw new IllegalArgumentException(
                            "You need equipment, a book and 3 experience levels.");
                if (!player.hasPermission("customenchants.apply"))
                    throw new IllegalArgumentException(
                            "You do not have permission to apply enchantments.");
                ItemStack result;
                boolean enchantmentBook = plugin.items().text(book, "kind").equals("book");
                if (enchantmentBook) result = plugin.specialItems().apply(player, book, base);
                else {
                    result = combine(base, book, event.getCurrentItem());
                    for (var entry : plugin.items().enchants(result).entrySet()) {
                        if (plugin.items().enchants(base).getOrDefault(entry.getKey(), 0)
                                >= entry.getValue()) continue;
                        var apply =
                                new io.github.lino9999.customenchants.api.EnchantApplyEvent(
                                        player, base, entry.getKey(), entry.getValue());
                        org.bukkit.Bukkit.getPluginManager().callEvent(apply);
                        if (apply.isCancelled())
                            throw new IllegalArgumentException("Application was cancelled.");
                    }
                }
                if (result == null) return;
                anvil.setItem(0, null);
                book.setAmount(book.getAmount() - 1);
                anvil.setItem(1, ItemData.empty(book) ? null : book);
                anvil.setItem(2, null);
                event.setCursor(result);
                if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) player.giveExpLevels(-3);
            } catch (IllegalArgumentException error) {
                player.sendMessage(Text.color("&c" + error.getMessage()));
            }
            return;
        }
        if (event.getClickedInventory() != player.getInventory()
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)
                || ItemData.empty(event.getCursor())
                || ItemData.empty(event.getCurrentItem())) return;
        if (plugin.items().text(event.getCursor(), "kind").isEmpty()) return;
        event.setCancelled(true);
        if (!plugin.settings().enabled(player.getWorld().getName())) return;
        try {
            ItemStack result =
                    plugin.specialItems().apply(player, event.getCursor(), event.getCurrentItem());
            if (result != null) {
                event.setCurrentItem(result);
                ItemStack cursor = event.getCursor().clone();
                cursor.setAmount(cursor.getAmount() - 1);
                event.setCursor(cursor.getAmount() == 0 ? null : cursor);
                player.updateInventory();
                plugin.getServer()
                        .getScheduler()
                        .runTask(plugin, () -> plugin.passive().refresh(player));
            }
        } catch (IllegalArgumentException error) {
            player.sendMessage(Text.color("&c" + error.getMessage()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack book = inventory.getItem(1), item = inventory.getItem(0);
        if (!customAnvil(inventory) || ItemData.empty(item)) return;
        try {
            if (!plugin.items().text(book, "kind").equals("book")) {
                event.setResult(combine(item, book, event.getResult()));
                event.getView().setRepairCost(3);
                return;
            }
            String id = plugin.items().text(book, "book_enchant");
            int level = plugin.items().number(book, "book_level");
            if (plugin.enchants().check(item, plugin.enchants().definition(id), level) != null) {
                event.setResult(null);
                return;
            }
            ItemStack result = item.clone();
            var enchants = plugin.items().enchants(result);
            enchants.put(id, level);
            plugin.items().enchants(result, enchants);
            event.setResult(result);
            event.getView().setRepairCost(3);
        } catch (IllegalArgumentException error) {
            event.setResult(null);
        }
    }

    private boolean customAnvil(AnvilInventory inventory) {
        ItemStack second = inventory.getItem(1);
        return plugin.items().text(second, "kind").equals("book")
                || !plugin.items().enchants(second).isEmpty();
    }

    private ItemStack combine(ItemStack base, ItemStack second, ItemStack vanilla) {
        if (ItemData.empty(base)
                || ItemData.empty(second)
                || base.getType() != second.getType()
                || base.getAmount() != 1
                || second.getAmount() != 1)
            throw new IllegalArgumentException("Combine two single items of the same material.");
        ItemStack result = ItemData.empty(vanilla) ? base.clone() : vanilla.clone();
        plugin.items().enchants(result, plugin.items().enchants(base));
        for (var entry : plugin.items().enchants(second).entrySet()) {
            var definition = plugin.enchants().definition(entry.getKey());
            int current = plugin.items().enchants(result).getOrDefault(entry.getKey(), 0);
            int desired =
                    current == entry.getValue()
                            ? Math.min(current + 1, definition.maxLevel())
                            : Math.max(current, entry.getValue());
            if (desired == current) continue;
            String problem = plugin.enchants().check(result, definition, desired);
            if (problem != null) throw new IllegalArgumentException(problem);
            var values = plugin.items().enchants(result);
            values.put(entry.getKey(), desired);
            plugin.items().enchants(result, values);
        }
        return result;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void grindstone(PrepareGrindstoneEvent event) {
        ItemStack result = event.getResult();
        if (!ItemData.empty(result) && !plugin.items().enchants(result).isEmpty()) {
            result = result.clone();
            plugin.items().enchants(result, new HashMap<>());
            event.setResult(result);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void enchant(org.bukkit.event.enchantment.EnchantItemEvent event) {
        if (!plugin.settings().enabled(event.getEnchanter().getWorld().getName())) return;
        int tier = Math.clamp(event.whichButton(), 0, 2);
        if (event.getItem().getType() == Material.BOOK
                && io.github.lino9999.customenchants.util.Numbers.roll(
                        plugin.settings().table().bookChance())) {
            var groups = plugin.settings().table().bookGroups().get(tier);
            String group =
                    groups.get(
                            io.github.lino9999.customenchants.util.Numbers.random(
                                    0, groups.size() - 1));
            ItemStack reward = plugin.enchants().randomBook(group);
            String token = java.util.UUID.randomUUID().toString();
            plugin.items().text(event.getItem(), "pending_table_book", token);
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (event.isCancelled()) {
                                    plugin.items().text(event.getItem(), "pending_table_book", "");
                                    return;
                                }
                                ItemStack current = event.getInventory().getItem(0);
                                if (plugin.items()
                                        .text(current, "pending_table_book")
                                        .equals(token)) event.getInventory().setItem(0, reward);
                                else
                                    for (int slot = 0; slot < 36; slot++) {
                                        if (plugin.items()
                                                .text(
                                                        event.getEnchanter()
                                                                .getInventory()
                                                                .getItem(slot),
                                                        "pending_table_book")
                                                .equals(token)) {
                                            event.getEnchanter()
                                                    .getInventory()
                                                    .setItem(slot, reward);
                                            break;
                                        }
                                    }
                            });
            return;
        }
        if (!io.github.lino9999.customenchants.util.Numbers.roll(plugin.settings().tableChance()))
            return;
        int count =
                io.github.lino9999.customenchants.util.Numbers.random(
                        plugin.settings().table().minimumCounts().get(tier),
                        plugin.settings().table().maximumCounts().get(tier));
        for (int i = 0; i < count; i++) {
            var candidates =
                    plugin.settings().enchants().values().stream()
                            .filter(
                                    e ->
                                            e.obtainable()
                                                    && plugin.enchants()
                                                                    .check(event.getItem(), e, 1)
                                                            == null)
                            .toList();
            if (candidates.isEmpty()) return;
            var selected =
                    candidates.get(
                            io.github.lino9999.customenchants.util.Numbers.random(
                                    0, candidates.size() - 1));
            int level =
                    Math.max(
                            1,
                            Math.min(
                                    selected.maxLevel(),
                                    (int)
                                            Math.ceil(
                                                    event.getExpLevelCost()
                                                            * selected.maxLevel()
                                                            / 30.0)));
            if (!selected.levels().containsKey(level)) level = 1;
            plugin.enchants().apply(event.getEnchanter(), event.getItem(), selected.id(), level);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void prices(org.bukkit.event.enchantment.PrepareItemEnchantEvent event) {
        if (!plugin.settings().enabled(event.getEnchanter().getWorld().getName())) return;
        var offers = event.getOffers();
        for (int i = 0; i < offers.length && i < 3; i++)
            if (offers[i] != null && plugin.settings().table().prices().get(i) > 0)
                offers[i].setCost(plugin.settings().table().prices().get(i));
    }
}
