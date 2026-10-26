package io.github.lino9999.customenchants.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collection;

public final class Inventories {
    private Inventories() {}

    public static boolean fits(Player player, Collection<ItemStack> items) {
        Inventory simulation = Bukkit.createInventory(null, 36);
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int i = 0; i < storage.length; i++)
            if (storage[i] != null) simulation.setItem(i, storage[i].clone());
        for (ItemStack item : items) if (!simulation.addItem(item.clone()).isEmpty()) return false;
        return true;
    }

    public static void give(Player player, Collection<ItemStack> items) {
        if (!fits(player, items))
            throw new IllegalArgumentException("There is not enough inventory space.");
        for (ItemStack item : items) player.getInventory().addItem(item.clone());
    }
}
