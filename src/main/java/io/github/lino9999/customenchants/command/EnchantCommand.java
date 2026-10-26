package io.github.lino9999.customenchants.command;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectRegistry;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.feature.SpecialItems;
import io.github.lino9999.customenchants.util.Inventories;
import io.github.lino9999.customenchants.util.Numbers;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public final class EnchantCommand implements TabExecutor {
    private static final List<String> COMMANDS =
            List.of(
                    "help",
                    "enchants",
                    "info",
                    "shop",
                    "tinkerer",
                    "alchemist",
                    "sets",
                    "kits",
                    "kit",
                    "rename",
                    "withdraw",
                    "refresh",
                    "inspect",
                    "admin",
                    "editor",
                    "enchant",
                    "remove",
                    "givebook",
                    "random",
                    "giveitem",
                    "giveset",
                    "giveweapon",
                    "givekit",
                    "resetkit",
                    "setsouls",
                    "open",
                    "reload",
                    "effects",
                    "diagnostics");
    private final CustomEnchants plugin;

    public EnchantCommand(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        try {
            if (!COMMANDS.contains(action))
                throw new IllegalArgumentException("Unknown command. Use /ce help.");
            if (!sender.hasPermission("customenchants." + action))
                throw new IllegalArgumentException(
                        "You do not have permission to use this command.");
            switch (action) {
                case "help" -> {
                    sender.sendMessage(Text.color("&dCustomEnchants &7by Lino"));
                    for (String entry : COMMANDS)
                        if (sender.hasPermission("customenchants." + entry))
                            sender.sendMessage(Text.color("&e/ce " + usage(entry)));
                }
                case "shop", "enchants", "admin", "tinkerer", "alchemist", "sets", "kits" ->
                        plugin.menus().open(player(sender), action);
                case "info" -> {
                    require(args, 2, action);
                    var e = plugin.enchants().definition(args[1]);
                    if (sender instanceof Player player) plugin.menus().info(player, e.id());
                    else
                        sender.sendMessage(
                                e.name()
                                        + " | "
                                        + e.group()
                                        + " | max tier "
                                        + e.maxLevel()
                                        + " | "
                                        + e.description());
                }
                case "enchant" -> {
                    require(args, 3, action);
                    Player player = player(sender);
                    if (plugin.enchants()
                            .apply(
                                    player,
                                    held(player),
                                    args[1].toLowerCase(Locale.ROOT),
                                    Numbers.integer(args[2], 1, 100)))
                        plugin.message(sender, "enchant-success");
                }
                case "remove" -> {
                    require(args, 2, action);
                    plugin.enchants().remove(held(player(sender)), args[1]);
                    plugin.message(sender, "enchant-removed");
                }
                case "givebook" -> {
                    require(args, 4, action);
                    Player target = online(args[1]);
                    ItemStack book =
                            plugin.enchants()
                                    .book(
                                            args[2].toLowerCase(Locale.ROOT),
                                            Numbers.integer(args[3], 1, 100),
                                            args.length > 4
                                                    ? Numbers.integer(args[4], 0, 100)
                                                    : 100,
                                            args.length > 5 ? Numbers.integer(args[5], 0, 100) : 0);
                    book.setAmount(args.length > 6 ? Numbers.integer(args[6], 1, 64) : 1);
                    Inventories.give(target, List.of(book));
                    plugin.message(sender, "given");
                }
                case "random" -> {
                    require(args, 3, action);
                    Player target = online(args[1]);
                    int count = args.length > 3 ? Numbers.integer(args[3], 1, 64) : 1;
                    List<ItemStack> books = new ArrayList<>();
                    for (int i = 0; i < count; i++)
                        books.add(plugin.enchants().randomBook(args[2].toUpperCase(Locale.ROOT)));
                    Inventories.give(target, books);
                    plugin.message(sender, "given");
                }
                case "giveitem" -> {
                    require(args, 3, action);
                    Player target = online(args[1]);
                    String type = args[2].toLowerCase(Locale.ROOT);
                    int count = args.length > 3 ? Numbers.integer(args[3], 1, 64) : 1;
                    int value = args.length > 4 ? Numbers.integer(args[4], 1, 1000000) : 1;
                    ItemStack item =
                            plugin.specialItems()
                                    .create(
                                            type,
                                            value,
                                            args.length > 5
                                                    ? args[5].toUpperCase(Locale.ROOT)
                                                    : "");
                    item.setAmount(count);
                    Inventories.give(target, List.of(item));
                    plugin.message(sender, "given");
                }
                case "giveset", "giveweapon" -> {
                    require(args, 3, action);
                    Inventories.give(
                            online(args[1]),
                            plugin.equipment()
                                    .equipment(
                                            action.equals("giveset") ? "set" : "weapon", args[2]));
                    plugin.message(sender, "given");
                }
                case "kit" -> {
                    require(args, 2, action);
                    plugin.equipment().claim(player(sender), args[1], false);
                    plugin.message(sender, "kit-claimed");
                }
                case "givekit" -> {
                    require(args, 3, action);
                    plugin.equipment().claim(online(args[1]), args[2], true);
                    plugin.message(sender, "given");
                }
                case "resetkit" -> {
                    require(args, 3, action);
                    plugin.equipment().reset(online(args[1]), args[2]);
                    plugin.message(sender, "kit-reset");
                }
                case "setsouls" -> {
                    require(args, 2, action);
                    plugin.items()
                            .number(
                                    held(player(sender)),
                                    "souls",
                                    Numbers.integer(args[1], 0, 1000000000));
                    plugin.items().render(held(player(sender)));
                    plugin.message(sender, "souls-set");
                }
                case "withdraw" -> {
                    require(args, 2, action);
                    Player player = player(sender);
                    ItemStack item = held(player);
                    int amount = Numbers.integer(args[1], 1, 1000000);
                    if (plugin.items().number(item, "souls") < amount)
                        throw new IllegalArgumentException("The item does not have enough souls.");
                    ItemStack gem = plugin.specialItems().create("soul_crystal", amount, "");
                    if (!Inventories.fits(player, List.of(gem)))
                        throw new IllegalArgumentException("There is not enough inventory space.");
                    plugin.items().increment(item, "souls", -amount);
                    player.getInventory().addItem(gem);
                    plugin.message(sender, "souls-withdrawn");
                }
                case "rename" -> {
                    require(args, 2, action);
                    plugin.specialItems()
                            .rename(
                                    player(sender),
                                    String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    plugin.message(sender, "renamed");
                }
                case "refresh" -> {
                    plugin.passive().refresh(player(sender));
                    plugin.message(sender, "refreshed");
                }
                case "inspect" -> {
                    ItemStack item = held(player(sender));
                    sender.sendMessage("Enchantments: " + plugin.items().enchants(item));
                    sender.sendMessage(
                            "Slots: "
                                    + plugin.items().slots(item)
                                    + " | Souls: "
                                    + plugin.items().number(item, "souls"));
                }
                case "open" -> {
                    require(args, 3, action);
                    plugin.menus().open(online(args[1]), args[2]);
                    plugin.message(sender, "opened");
                }
                case "editor" -> {
                    require(args, 2, action);
                    plugin.menus().editor(player(sender), args[1]);
                }
                case "reload" -> {
                    plugin.reloadSettings();
                    plugin.message(sender, "reloaded");
                }
                case "effects" ->
                        sender.sendMessage(
                                String.join(", ", new TreeSet<>(EffectRegistry.OPERATIONS)));
                case "diagnostics" ->
                        sender.sendMessage(
                                "CustomEnchants "
                                        + plugin.getDescription().getVersion()
                                        + " | "
                                        + Bukkit.getBukkitVersion()
                                        + " | Java "
                                        + Runtime.version().feature()
                                        + " | Enchantments "
                                        + plugin.settings().enchants().size()
                                        + " | Sets "
                                        + plugin.settings().sets().size()
                                        + " | Managed entities "
                                        + plugin.managedEntityCount());
                default -> throw new IllegalArgumentException("Unknown command.");
            }
        } catch (IllegalArgumentException error) {
            sender.sendMessage(Text.color("&c" + error.getMessage()));
        } catch (Exception error) {
            sender.sendMessage(Text.color("&cThe operation failed: " + error.getMessage()));
            plugin.getLogger().warning(action + " failed: " + error.getMessage());
        }
        return true;
    }

    public static String usage(String command) {
        return command
                + switch (command) {
                    case "info", "editor", "remove" -> " <enchantment>";
                    case "enchant" -> " <enchantment> <tier>";
                    case "givebook" -> " <player> <enchantment> <tier> [success] [destroy] [amount]";
                    case "random" -> " <player> <rarity> [amount]";
                    case "giveitem" -> " <player> <type> [amount] [value] [rarity]";
                    case "giveset", "giveweapon", "givekit", "resetkit" -> " <player> <id>";
                    case "kit" -> " <id>";
                    case "rename" -> " <name>";
                    case "setsouls", "withdraw" -> " <amount>";
                    case "open" -> " <player> <menu>";
                    default -> "";
                };
    }

    private static void require(String[] args, int length, String command) {
        if (args.length < length)
            throw new IllegalArgumentException("Usage: /ce " + usage(command));
    }

    private static Player player(CommandSender sender) {
        if (sender instanceof Player player) return player;
        throw new IllegalArgumentException("This command must be used by a player.");
    }

    private static Player online(String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) throw new IllegalArgumentException("That player is not online.");
        return player;
    }

    private static ItemStack held(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (ItemData.empty(item)) throw new IllegalArgumentException("Hold an equipment item.");
        return item;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1)
            options.addAll(
                    COMMANDS.stream()
                            .filter(name -> sender.hasPermission("customenchants." + name))
                            .toList());
        else if (args.length >= 2 && sender.hasPermission("customenchants." + args[0])) {
            String action = args[0];
            if (args.length == 2) {
                if (Set.of("info", "editor", "enchant", "remove").contains(action))
                    options.addAll(plugin.settings().enchants().keySet());
                else if (Set.of(
                                "givebook",
                                "random",
                                "giveitem",
                                "giveset",
                                "giveweapon",
                                "givekit",
                                "resetkit",
                                "open")
                        .contains(action))
                    options.addAll(
                            Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                else if (action.equals("kit")) options.addAll(plugin.settings().kits().keySet());
            } else if (args.length == 3) {
                switch (action) {
                    case "givebook" -> options.addAll(plugin.settings().enchants().keySet());
                    case "random" -> options.addAll(plugin.settings().groups().keySet());
                    case "giveitem" -> options.addAll(SpecialItems.TYPES.keySet());
                    case "giveset" -> options.addAll(plugin.settings().sets().keySet());
                    case "giveweapon" -> options.addAll(plugin.settings().weapons().keySet());
                    case "givekit", "resetkit" -> options.addAll(plugin.settings().kits().keySet());
                    case "open" ->
                            options.addAll(
                                    List.of(
                                            "shop",
                                            "enchants",
                                            "tinkerer",
                                            "alchemist",
                                            "sets",
                                            "kits"));
                    default -> {}
                }
            }
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream()
                .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(prefix))
                .sorted()
                .toList();
    }
}
