package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.config.EnchantDefinition;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.util.Experience;
import io.github.lino9999.customenchants.util.Inventories;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

public final class MenuService implements Listener {
    public static final class Menu implements InventoryHolder {
        private Inventory inventory;
        final UUID owner;
        final String mode;
        final Map<Integer, Runnable> actions = new HashMap<>();
        final LinkedHashMap<Integer, ItemStack> selected = new LinkedHashMap<>();

        Menu(Player player, String mode) {
            this.owner = player.getUniqueId();
            this.mode = mode;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final CustomEnchants plugin;

    public MenuService(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, String mode) {
        open(player, mode, 0);
    }

    public void open(Player player, String mode, int page) {
        if (!Set.of("shop", "enchants", "admin", "tinkerer", "alchemist", "sets", "kits")
                .contains(mode)) throw new IllegalArgumentException("Unknown menu: " + mode);
        if (!player.hasPermission("customenchants." + mode))
            throw new IllegalArgumentException("You do not have permission to open this menu.");
        Menu menu = create(player, mode, "CustomEnchants | " + SpecialItems.title(mode), 54);
        if (mode.equals("shop")) {
            int slot = 10;
            for (var group : plugin.settings().groups().values()) {
                int target = slot++;
                menu.inventory.setItem(
                        target,
                        icon(
                                Material.ENCHANTED_BOOK,
                                group.color() + group.name(),
                                "&7Cost: " + group.cost() + " " + group.currency(),
                                "&eClick to buy a random tome."));
                menu.actions.put(
                        target,
                        () -> {
                            ItemStack book = plugin.enchants().randomBook(group.id());
                            if (!Inventories.fits(player, List.of(book)))
                                throw new IllegalArgumentException(
                                        "There is not enough inventory space.");
                            PaymentService.charge(player, group);
                            player.getInventory().addItem(book);
                            plugin.message(player, "purchase");
                        });
            }
        } else if (mode.equals("tinkerer") || mode.equals("alchemist")) {
            menu.inventory.setItem(
                    13,
                    icon(
                            Material.BOOK,
                            "&dSelect items in your inventory",
                            mode.equals("tinkerer")
                                    ? "&7Select custom tomes or enchanted equipment."
                                    : "&7Select two identical tomes or matching arcane dust.",
                            "&7Click selected items again to deselect."));
            menu.inventory.setItem(
                    31,
                    icon(
                            Material.LIME_DYE,
                            "&aConfirm exchange",
                            "&7Combiner cost: "
                                    + plugin.settings().alchemistCost()
                                    + " experience points",
                            "&eClick to confirm."));
            menu.actions.put(31, () -> trade(player, menu));
        } else if (mode.equals("enchants") || mode.equals("admin")) {
            List<EnchantDefinition> list = new ArrayList<>(plugin.settings().enchants().values());
            int lastPage = Math.max(0, (list.size() - 1) / 45);
            page = Math.clamp(page, 0, lastPage);
            for (int slot = 0; slot < 45 && page * 45 + slot < list.size(); slot++) {
                EnchantDefinition enchant = list.get(page * 45 + slot);
                ItemStack book = plugin.enchants().book(enchant.id(), enchant.maxLevel(), 100, 0);
                menu.inventory.setItem(slot, book);
                menu.actions.put(
                        slot,
                        () -> {
                            if (mode.equals("admin")) {
                                if (!player.hasPermission("customenchants.admin"))
                                    throw new IllegalArgumentException(
                                            "You do not have permission.");
                                Inventories.give(player, List.of(book));
                            } else info(player, enchant.id());
                        });
            }
            int current = page;
            if (page > 0) {
                menu.inventory.setItem(45, icon(Material.ARROW, "&ePrevious page"));
                menu.actions.put(45, () -> open(player, mode, current - 1));
            }
            if (page < lastPage) {
                menu.inventory.setItem(53, icon(Material.ARROW, "&eNext page"));
                menu.actions.put(53, () -> open(player, mode, current + 1));
            }
        } else if (mode.equals("sets")) {
            int slot = 0;
            for (String id : plugin.settings().sets().keySet()) {
                List<ItemStack> equipment = plugin.equipment().equipment("set", id);
                menu.inventory.setItem(slot, equipment.getFirst());
                menu.actions.put(
                        slot++,
                        () -> {
                            Menu preview = create(player, "preview", "CustomEnchants | " + id, 27);
                            for (int i = 0; i < equipment.size(); i++)
                                preview.inventory.setItem(10 + i, equipment.get(i));
                            player.openInventory(preview.inventory);
                        });
            }
        } else if (mode.equals("kits")) {
            int slot = 0;
            for (var kit : plugin.settings().kits().values()) {
                if (slot >= 45) break;
                menu.inventory.setItem(
                        slot,
                        icon(
                                Material.CHEST,
                                "&d" + kit.name(),
                                "&7Cooldown: " + kit.cooldownSeconds() + " seconds",
                                "&eClick to claim /ce kit " + kit.id()));
                menu.actions.put(
                        slot++,
                        () -> {
                            plugin.equipment().claim(player, kit.id(), false);
                            plugin.message(player, "kit-claimed");
                        });
            }
        }
        player.openInventory(menu.inventory);
    }

    public void info(Player player, String id) {
        EnchantDefinition e = plugin.enchants().definition(id);
        Menu menu = create(player, "info", "CustomEnchants | " + e.name(), 27);
        menu.inventory.setItem(13, plugin.enchants().book(e.id(), e.maxLevel(), 100, 0));
        menu.inventory.setItem(22, icon(Material.ARROW, "&eBack"));
        menu.actions.put(22, () -> open(player, "enchants"));
        player.openInventory(menu.inventory);
    }

    public void editor(Player player, String id) {
        if (!player.hasPermission("customenchants.editor"))
            throw new IllegalArgumentException("You do not have permission to edit enchantments.");
        EnchantDefinition enchantment = plugin.enchants().definition(id);
        Menu menu = create(player, "editor", "CustomEnchants | Edit " + enchantment.name(), 54);
        int slot = 0;
        for (var entry : new TreeMap<>(enchantment.levels()).entrySet()) {
            int level = entry.getKey();
            if (slot >= 45) break;
            menu.inventory.setItem(
                    slot,
                    icon(
                            Material.ENCHANTED_BOOK,
                            "&dTier " + level,
                            "&7Rate: " + entry.getValue().chance() + "%",
                            "&7Delay: " + entry.getValue().cooldown() + " seconds",
                            "&eClick to cycle activation rate by 5%."));
            menu.actions.put(
                    slot++,
                    () -> {
                        if (!player.hasPermission("customenchants.editor"))
                            throw new IllegalArgumentException("You do not have permission.");
                        editChance(id, level, (entry.getValue().chance() + 5) % 105);
                        editor(player, id);
                    });
        }
        player.openInventory(menu.inventory);
    }

    private void editChance(String id, int level, double chance) {
        File file = new File(plugin.getDataFolder(), "enchantments.yml");
        try {
            String previous = Files.readString(file.toPath());
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(previous);
            yaml.set(id + ".tiers." + level + ".rate", chance);
            Path pending = file.toPath().resolveSibling("enchantments.pending.yml");
            Files.writeString(pending, yaml.saveToString());
            Files.move(
                    pending,
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            try {
                plugin.reloadSettings();
            } catch (Exception error) {
                Files.writeString(file.toPath(), previous);
                throw error;
            }
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "The edit could not be saved: " + error.getMessage());
        }
    }

    private void trade(Player player, Menu menu) {
        if (!player.hasPermission("customenchants." + menu.mode))
            throw new IllegalArgumentException("You do not have permission.");
        if (menu.selected.isEmpty()) throw new IllegalArgumentException("Select items first.");
        for (var entry : menu.selected.entrySet())
            if (!Objects.equals(player.getInventory().getItem(entry.getKey()), entry.getValue()))
                throw new IllegalArgumentException(
                        "Your inventory changed. Select the items again.");
        List<ItemStack> selected = new ArrayList<>(menu.selected.values());
        ItemStack reward = null;
        int experience = 0;
        if (menu.mode.equals("tinkerer")) {
            for (ItemStack item : selected) {
                boolean book = plugin.items().text(item, "kind").equals("book");
                if (!book && plugin.items().enchants(item).isEmpty())
                    throw new IllegalArgumentException(
                            "Only custom tomes and enchanted equipment can be recycled.");
                experience =
                        (int)
                                Math.min(
                                        100000000L,
                                        experience
                                                + (long) plugin.settings().tinkerReward()
                                                        * (book
                                                                ? plugin.items()
                                                                        .number(item, "book_level")
                                                                : plugin.items()
                                                                        .enchants(item)
                                                                        .size()));
            }
        } else {
            if (selected.size() != 2)
                throw new IllegalArgumentException("Select exactly two separate items.");
            ItemStack first = selected.get(0), second = selected.get(1);
            String kind = plugin.items().text(first, "kind");
            if (!kind.equals(plugin.items().text(second, "kind")))
                throw new IllegalArgumentException("The item types must match.");
            if (kind.equals("book")) {
                String id = plugin.items().text(first, "book_enchant");
                int level = plugin.items().number(first, "book_level");
                if (!id.equals(plugin.items().text(second, "book_enchant"))
                        || level != plugin.items().number(second, "book_level"))
                    throw new IllegalArgumentException(
                            "Both tomes must have the same enchantment and tier.");
                reward =
                        plugin.enchants()
                                .book(
                                        id,
                                        level + 1,
                                        (plugin.items().number(first, "success")
                                                        + plugin.items().number(second, "success"))
                                                / 2,
                                        (plugin.items().number(first, "destroy")
                                                        + plugin.items().number(second, "destroy"))
                                                / 2);
            } else if (kind.equals("arcane_dust")) {
                String group = plugin.items().text(first, "group");
                if (!group.equals(plugin.items().text(second, "group")))
                    throw new IllegalArgumentException("Both dust rarities must match.");
                reward =
                        plugin.specialItems()
                                .create(
                                        "arcane_dust",
                                        Math.min(
                                                100,
                                                plugin.items().number(first, "value")
                                                        + plugin.items().number(second, "value")),
                                        group);
            } else throw new IllegalArgumentException("Select two matching tomes or arcane dust.");
            if (Experience.total(player) < plugin.settings().alchemistCost())
                throw new IllegalArgumentException(
                        "You need " + plugin.settings().alchemistCost() + " experience points.");
        }
        ItemStack[] before = player.getInventory().getStorageContents();
        ItemStack[] copy =
                Arrays.stream(before)
                        .map(item -> item == null ? null : item.clone())
                        .toArray(ItemStack[]::new);
        for (int slot : menu.selected.keySet()) {
            ItemStack current = player.getInventory().getItem(slot);
            if (current.getAmount() == 1) player.getInventory().setItem(slot, null);
            else {
                current = current.clone();
                current.setAmount(current.getAmount() - 1);
                player.getInventory().setItem(slot, current);
            }
        }
        if (reward != null && !Inventories.fits(player, List.of(reward))) {
            player.getInventory().setStorageContents(copy);
            throw new IllegalArgumentException("There is not enough inventory space.");
        }
        if (reward != null) {
            Experience.spend(player, plugin.settings().alchemistCost());
            player.getInventory().addItem(reward);
        } else player.giveExp(experience);
        menu.selected.clear();
        plugin.message(player, "trade-success");
    }

    @EventHandler
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)
                || !player.getUniqueId().equals(menu.owner)
                || event.getClick() != ClickType.LEFT
                || !ItemData.empty(event.getCursor())) return;
        try {
            if (event.getRawSlot() >= menu.inventory.getSize()
                    && (menu.mode.equals("tinkerer") || menu.mode.equals("alchemist"))
                    && event.getClickedInventory() == player.getInventory()) {
                if (event.getSlot() >= 36) return;
                int slot = event.getSlot();
                ItemStack item = player.getInventory().getItem(slot);
                if (ItemData.empty(item)) return;
                if (menu.selected.remove(slot) == null) menu.selected.put(slot, item.clone());
                player.sendMessage(Text.color("&dSelected items: " + menu.selected.size()));
                return;
            }
            Runnable action = menu.actions.get(event.getRawSlot());
            if (action != null)
                plugin.getServer()
                        .getScheduler()
                        .runTask(
                                plugin,
                                () -> {
                                    if (!player.isOnline()
                                            || player.getOpenInventory().getTopInventory()
                                                    != menu.inventory) return;
                                    try {
                                        if (Set.of(
                                                                "shop",
                                                                "tinkerer",
                                                                "alchemist",
                                                                "admin",
                                                                "sets",
                                                                "kits",
                                                                "enchants",
                                                                "editor")
                                                        .contains(menu.mode)
                                                && !player.hasPermission(
                                                        "customenchants." + menu.mode))
                                            throw new IllegalArgumentException(
                                                    "You do not have permission to use this menu.");
                                        action.run();
                                    } catch (IllegalArgumentException error) {
                                        player.sendMessage(Text.color("&c" + error.getMessage()));
                                    }
                                });
        } catch (IllegalArgumentException error) {
            player.sendMessage(Text.color("&c" + error.getMessage()));
        }
    }

    @EventHandler
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Menu) event.setCancelled(true);
    }

    private Menu create(Player player, String mode, String title, int size) {
        Menu menu = new Menu(player, mode);
        menu.inventory = Bukkit.createInventory(menu, size, Text.color(title));
        return menu;
    }

    public static ItemStack icon(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(Text.color(name));
        meta.setLore(Arrays.stream(lore).map(Text::color).toList());
        item.setItemMeta(meta);
        return item;
    }
}
