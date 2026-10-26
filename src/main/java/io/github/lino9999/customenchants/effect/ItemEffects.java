package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ItemEffects {
    private final CustomEnchants plugin;

    ItemEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.register(
                "mend_source_item", (c, t, a) -> repair(c.item, EffectRegistry.number(a, 0, 1)));
        registry.register(
                "mend_held_item",
                (c, t, a) -> {
                    ItemStack item =
                            t == c.owner
                                    ? c.item
                                    : t.getEquipment() == null
                                            ? null
                                            : t.getEquipment().getItemInMainHand();
                    repair(item, EffectRegistry.number(a, 0, 1));
                });
        registry.register(
                "mend_armor",
                (c, t, a) -> {
                    if (t.getEquipment() != null && plugin.canAffect(c.owner, t))
                        mendArmor(t, EffectRegistry.number(a, 0, 1));
                });
        registry.register(
                "restore_durability",
                (c, t, a) ->
                        repair(
                                t == c.owner
                                        ? c.item
                                        : t.getEquipment() == null
                                                ? null
                                                : t.getEquipment().getItemInMainHand(),
                                Integer.MAX_VALUE));
        registry.register(
                "strip_enchantment",
                (c, t, a) -> {
                    if (a.length > 0 && plugin.items().enchants(c.item).containsKey(a[0]))
                        plugin.enchants().remove(c.item, a[0]);
                });
        registry.register(
                "harvest_souls",
                (c, t, a) -> {
                    if (c.trigger.equals("BLOCK_BREAK")
                            && c.block != null
                            && plugin.blocks().isPlaced(c.block)) return;
                    int amount = (int) Math.clamp(EffectRegistry.number(a, 0, 1), 0, 1000000);
                    plugin.items().increment(c.item, "souls", amount);
                    plugin.items().increment(c.item, "count_souls", amount);
                });
        registry.register(
                "consume_souls",
                (c, t, a) -> {
                    if (!c.owner.hasPermission("customenchants.bypass.souls"))
                        plugin.items()
                                .increment(
                                        c.item,
                                        "souls",
                                        -(int)
                                                Math.clamp(
                                                        EffectRegistry.number(a, 0, 1),
                                                        0,
                                                        1000000));
                });
        registry.register(
                "retain_on_death",
                (c, t, a) -> c.values.put("keep:" + System.identityHashCode(c.item), c.item));
        registry.register(
                "disarm_hand",
                (c, t, a) -> {
                    if (t instanceof Player player && plugin.canAffect(c.owner, player))
                        disarm(player, false);
                });
        registry.register(
                "strip_random_armor",
                (c, t, a) -> {
                    if (t instanceof Player player && plugin.canAffect(c.owner, player))
                        disarm(player, true);
                });
        registry.register(
                "scramble_hotbar",
                (c, t, a) -> {
                    if (!(t instanceof Player player) || !plugin.canAffect(c.owner, t)) return;
                    List<ItemStack> hotbar = new ArrayList<>();
                    for (int i = 0; i < 9; i++) hotbar.add(player.getInventory().getItem(i));
                    Collections.shuffle(hotbar);
                    for (int i = 0; i < 9; i++) player.getInventory().setItem(i, hotbar.get(i));
                });
        registry.register(
                "mask_vision",
                (c, t, a) -> {
                    if (!(t instanceof Player player) || !plugin.canAffect(c.owner, t)) return;
                    for (Player viewer : player.getWorld().getPlayers())
                        viewer.sendEquipmentChange(
                                player, EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN));
                    long duration = (long) Math.clamp(EffectRegistry.number(a, 0, 40), 1, 200);
                    plugin.getServer()
                            .getScheduler()
                            .runTaskLater(
                                    plugin,
                                    () -> {
                                        if (player.isOnline())
                                            for (Player viewer : player.getWorld().getPlayers())
                                                viewer.sendEquipmentChange(
                                                        player,
                                                        EquipmentSlot.HEAD,
                                                        player.getInventory().getHelmet());
                                    },
                                    duration);
                });
    }

    private static void repair(ItemStack item, double amount) {
        if (ItemData.empty(item)
                || !(item.getItemMeta() instanceof Damageable damage)
                || damage.isUnbreakable()) return;
        int maximum = item.getType().getMaxDurability();
        damage.setDamage(
                (int) Math.clamp(damage.getDamage() - amount, 0, Math.max(0, maximum - 1)));
        item.setItemMeta(damage);
    }

    private static void mendArmor(org.bukkit.entity.LivingEntity entity, double amount) {
        if (entity.getEquipment() == null) return;
        ItemStack[] armor = entity.getEquipment().getArmorContents();
        boolean changed = false;
        for (int i = 0; i < armor.length; i++) {
            ItemStack item = armor[i];
            if (ItemData.empty(item)
                    || !(item.getItemMeta() instanceof Damageable damage)
                    || damage.isUnbreakable()) continue;
            int maximum = item.getType().getMaxDurability();
            int nextDamage = (int) (damage.getDamage() - amount);
            if (nextDamage >= maximum) {
                armor[i] = null;
                changed = true;
            } else {
                damage.setDamage(Math.clamp(nextDamage, 0, maximum));
                item.setItemMeta(damage);
                changed = true;
            }
        }
        if (changed) {
            entity.getEquipment().setArmorContents(armor);
            if (entity instanceof Player player) player.updateInventory();
        }
    }

    private void disarm(Player player, boolean armor) {
        int slot = player.getInventory().firstEmpty();
        if (slot < 0 || slot >= 36) return;
        if (!armor) {
            ItemStack item = player.getInventory().getItemInMainHand();
            if (ItemData.empty(item)) return;
            player.getInventory().setItem(slot, item);
            player.getInventory().setItemInMainHand(null);
            return;
        }
        ItemStack[] contents = player.getInventory().getArmorContents();
        List<Integer> occupied = new ArrayList<>();
        for (int i = 0; i < contents.length; i++)
            if (!ItemData.empty(contents[i])
                    && !contents[i].containsEnchantment(
                            org.bukkit.enchantments.Enchantment.BINDING_CURSE)) occupied.add(i);
        if (occupied.isEmpty()) return;
        int index =
                occupied.get(
                        java.util.concurrent.ThreadLocalRandom.current().nextInt(occupied.size()));
        player.getInventory().setItem(slot, contents[index]);
        contents[index] = null;
        player.getInventory().setArmorContents(contents);
    }
}
