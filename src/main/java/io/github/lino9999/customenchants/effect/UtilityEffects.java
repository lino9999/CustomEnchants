package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.feature.EconomyService;
import io.github.lino9999.customenchants.feature.PermissionService;
import io.github.lino9999.customenchants.util.Experience;
import io.github.lino9999.customenchants.util.Text;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;

final class UtilityEffects {
    private final CustomEnchants plugin;

    UtilityEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register(
                "send_actionbar",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        player.spigot()
                                .sendMessage(
                                        ChatMessageType.ACTION_BAR,
                                        TextComponent.fromLegacyText(
                                                Text.color(String.join(" | ", a))));
                });
        registry.register(
                "announce_all",
                (c, t, a) -> Bukkit.broadcastMessage(Text.color(String.join(" | ", a))));
        registry.register(
                "announce_perm",
                (c, t, a) -> {
                    String permission = required(a, 0);
                    String message =
                            Text.color(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
                    for (Player player : Bukkit.getOnlinePlayers())
                        if (player.hasPermission(permission)) player.sendMessage(message);
                });
        registry.register(
                "show_title",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        player.sendTitle(Text.color(String.join(" | ", a)), "", 10, 70, 20);
                });
        registry.register(
                "show_subtitle",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        player.sendTitle("", Text.color(String.join(" | ", a)), 10, 70, 20);
                });
        registry.register(
                "run_console_cmd",
                (c, t, a) -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command(a)));
        registry.register(
                "run_player_cmd",
                (c, t, a) -> {
                    if (t instanceof Player player) Bukkit.dispatchCommand(player, command(a));
                });
        registry.register(
                "open_workbench",
                (c, t, a) -> {
                    if (t instanceof Player player) player.openWorkbench(null, true);
                });
        registry.register(
                "open_enderchest",
                (c, t, a) -> {
                    if (t instanceof Player player) player.openInventory(player.getEnderChest());
                });
        registry.register(
                "set_state",
                (c, t, a) ->
                        registry.variables()
                                .set(
                                        required(a, 0),
                                        required(a, 1),
                                        a.length < 3 || booleanArgument(a[2])));
        registry.register(
                "toggle_state", (c, t, a) -> registry.variables().invert(required(a, 0)));
        registry.register(
                "set_air_supply",
                (c, t, a) ->
                        t.setRemainingAir(
                                (int)
                                        Math.clamp(
                                                EffectRegistry.number(a, 0, t.getMaximumAir()),
                                                0,
                                                t.getMaximumAir())));
        registry.register(
                "grant_item",
                (c, t, a) -> {
                    if (!(t instanceof Player player)) return;
                    ItemStack item = stack(a);
                    for (ItemStack overflow : player.getInventory().addItem(item).values())
                        player.getWorld().dropItem(player.getLocation(), overflow);
                });
        registry.register(
                "spawn_item", (c, t, a) -> t.getWorld().dropItem(t.getLocation(), stack(a)));
        registry.registerLocation("spawn_item", (c, t, a) -> t.getWorld().dropItem(t, stack(a)));
        registry.registerLocation("grant_item", (c, t, a) -> t.getWorld().dropItem(t, stack(a)));
        registry.registerLocation(
                "warp_to",
                (c, t, a) -> {
                    var orientation = c.owner.getLocation();
                    t.setYaw(orientation.getYaw());
                    t.setPitch(orientation.getPitch());
                    c.owner.teleport(t);
                });
        registry.register(
                "drop_hand_item",
                (c, t, a) -> {
                    if (!(t instanceof Player player) || !plugin.canAffect(c.owner, t)) return;
                    ItemStack held = player.getInventory().getItemInMainHand();
                    if (ItemData.empty(held)) return;
                    var dropped = player.getWorld().dropItem(player.getLocation(), held.clone());
                    if (dropped.isValid()) player.getInventory().setItemInMainHand(null);
                });
        registry.register(
                "destroy_item",
                (c, t, a) -> {
                    if (t instanceof Player player
                            && !ItemData.empty(c.item)
                            && plugin.canAffect(c.owner, t))
                        remove(player, c.item, amount(a, 0), true);
                });
        registry.register(
                "consume_item",
                (c, t, a) -> {
                    if (t instanceof Player player && plugin.canAffect(c.owner, t))
                        remove(
                                player,
                                new ItemStack(material(required(a, 0))),
                                amount(a, 1),
                                false);
                });
        registry.register(
                "imbue_enchant",
                (c, t, a) -> {
                    if (ItemData.empty(c.item)) return;
                    String name = required(a, 0).toLowerCase(Locale.ROOT);
                    int level = (int) Math.clamp(EffectRegistry.number(a, 1, 1), 1, 255);
                    NamespacedKey key = NamespacedKey.fromString(name);
                    var vanilla = key == null ? null : Registry.ENCHANTMENT.get(key);
                    if (vanilla != null) c.item.addUnsafeEnchantment(vanilla, level);
                    else plugin.enchants().apply(c.owner, c.item, name, level);
                });
        registry.register(
                "siphon_xp",
                (c, t, a) -> {
                    if (!(t instanceof Player recipient)
                            || !(LivingEffects.other(c, t) instanceof Player victim)
                            || victim == recipient
                            || !plugin.canAffect(c.owner, victim)) return;
                    int value = Math.min(Experience.total(victim), amount(a, 0));
                    Experience.spend(victim, value);
                    recipient.giveExp(value);
                });
        registry.register(
                "deposit_currency",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        new EconomyService().deposit(player, EffectRegistry.number(a, 0, 0));
                });
        registry.register(
                "withdraw_currency",
                (c, t, a) -> {
                    if (t instanceof Player player && plugin.canAffect(c.owner, t))
                        new EconomyService().withdraw(player, EffectRegistry.number(a, 0, 0));
                });
        registry.register(
                "siphon_currency",
                (c, t, a) -> {
                    if (!(t instanceof Player recipient)
                            || !(LivingEffects.other(c, t) instanceof Player source)
                            || recipient == source
                            || !plugin.canAffect(c.owner, source)) return;
                    EconomyService economy = new EconomyService();
                    double amount =
                            Math.min(
                                    Math.floor(economy.balance(source)),
                                    Math.max(0, (int) EffectRegistry.number(a, 0, 0)));
                    economy.transfer(source, recipient, amount);
                });
        registry.register(
                "toggle_permission",
                (c, t, a) -> {
                    if (t instanceof Player player)
                        PermissionService.toggle(player, required(a, 0));
                });
    }

    private static void remove(Player player, ItemStack prototype, int count, boolean exact) {
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getSize() && count > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (ItemData.empty(current)
                    || (exact
                            ? !current.isSimilar(prototype)
                            : current.getType() != prototype.getType())) continue;
            int removed = Math.min(count, current.getAmount());
            count -= removed;
            if (removed == current.getAmount()) inventory.setItem(slot, null);
            else current.setAmount(current.getAmount() - removed);
        }
    }

    static ItemStack stack(String[] arguments) {
        return new ItemStack(material(required(arguments, 0)), amount(arguments, 1));
    }

    static Material material(String name) {
        Material material = Material.matchMaterial(name);
        if (material == null || !material.isItem() || material.isAir())
            throw new IllegalArgumentException("Invalid item material: " + name);
        return material;
    }

    static int amount(String[] arguments, int index) {
        return (int) Math.clamp(EffectRegistry.number(arguments, index, 1), 0, 4096);
    }

    static String required(String[] arguments, int index) {
        if (index >= arguments.length || arguments[index].isBlank())
            throw new IllegalArgumentException("Missing effect argument " + (index + 1) + ".");
        return arguments[index];
    }

    private static boolean booleanArgument(String value) {
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Expected a boolean effect argument.");
        return Boolean.parseBoolean(value);
    }

    private static String command(String[] arguments) {
        String command = String.join(" | ", arguments).trim();
        if (command.startsWith("/")) command = command.substring(1);
        if (command.isBlank() || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0)
            throw new IllegalArgumentException("Invalid effect command.");
        return command;
    }
}
