package io.github.lino9999.customenchants.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

public final class EnchantApplyEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final ItemStack item;
    private final String enchantment;
    private final int level;
    private boolean cancelled;

    public EnchantApplyEvent(Player player, ItemStack item, String enchantment, int level) {
        this.player = player;
        this.item = item.clone();
        this.enchantment = enchantment;
        this.level = level;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemStack getItem() {
        return item.clone();
    }

    public String getEnchantment() {
        return enchantment;
    }

    public int getLevel() {
        return level;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean value) {
        cancelled = value;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
