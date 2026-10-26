package io.github.lino9999.customenchants.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public final class EnchantActivateEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final String enchantment;
    private final int level;
    private final String trigger;
    private boolean cancelled;

    public EnchantActivateEvent(Player player, String enchantment, int level, String trigger) {
        this.player = player;
        this.enchantment = enchantment;
        this.level = level;
        this.trigger = trigger;
    }

    public Player getPlayer() {
        return player;
    }

    public String getEnchantment() {
        return enchantment;
    }

    public int getLevel() {
        return level;
    }

    public String getTrigger() {
        return trigger;
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
