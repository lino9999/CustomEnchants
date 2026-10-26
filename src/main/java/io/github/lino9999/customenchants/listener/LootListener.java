package io.github.lino9999.customenchants.listener;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.config.Settings;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.ArrayList;
import java.util.List;

public final class LootListener implements Listener {
    private final CustomEnchants plugin;

    public LootListener(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void loot(LootGenerateEvent event) {
        if (!event.isPlugin()
                && plugin.settings().enabled(event.getWorld().getName())
                && Numbers.roll(plugin.settings().lootChance())) {
            List<ItemStack> items = new ArrayList<>(event.getLoot());
            int count = Numbers.random(1, plugin.settings().lootMaximum());
            for (int i = 0; i < count; i++) items.add(reward(plugin.settings().lootPool()));
            event.setLoot(items);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void trade(VillagerAcquireTradeEvent event) {
        if (plugin.settings().enabled(event.getEntity().getWorld().getName())
                && Numbers.roll(plugin.settings().tradeChance())) {
            MerchantRecipe recipe = new MerchantRecipe(reward(plugin.settings().tradePool()), 8);
            recipe.addIngredient(new ItemStack(Material.EMERALD, Numbers.random(8, 32)));
            recipe.addIngredient(new ItemStack(Material.BOOK));
            event.setRecipe(recipe);
        }
    }

    private ItemStack reward(List<Settings.LootChoice> pool) {
        int choice = Numbers.random(1, pool.stream().mapToInt(Settings.LootChoice::weight).sum());
        for (var entry : pool) {
            choice -= entry.weight();
            if (choice <= 0)
                return entry.kind().equals("tome")
                        ? plugin.enchants().randomBook(entry.id())
                        : plugin.specialItems().create(entry.id(), entry.value(), entry.group());
        }
        throw new IllegalArgumentException("Invalid loot pool.");
    }
}
