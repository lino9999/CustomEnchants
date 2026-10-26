package io.github.lino9999.customenchants.listener;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

public final class GameplayListener implements Listener {
    private record Shot(ItemStack item, long expires) {}

    private record DropKey(UUID player, UUID world, int x, int y, int z) {}

    private final CustomEnchants plugin;
    private final Map<UUID, Shot> shots = new HashMap<>();
    private final Map<DropKey, Map<String, Object>> drops = new HashMap<>();

    public GameplayListener(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combat(EntityDamageByEntityEvent event) {
        if (plugin.engine().isEffectDamage() || !(event.getEntity() instanceof LivingEntity victim))
            return;
        LivingEntity attacker =
                event.getDamager() instanceof LivingEntity living
                        ? living
                        : event.getDamager() instanceof Projectile projectile
                                        && projectile.getShooter() instanceof LivingEntity living
                                ? living
                                : null;
        var guard = plugin.guards().get(event.getDamager().getUniqueId());
        if (guard == null
                && event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Entity source)
            guard = plugin.guards().get(source.getUniqueId());
        if (guard != null) {
            Player owner = Bukkit.getPlayer(guard.controller());
            if (owner == null
                    || !guard.target().equals(victim.getUniqueId())
                    || !plugin.canAffect(owner, victim)) event.setCancelled(true);
            return;
        }
        boolean ranged = event.getDamager() instanceof Projectile;
        if (attacker instanceof Player player
                && attacker != victim
                && !plugin.managed(event.getDamager())
                && plugin.canAffect(player, victim)) {
            Shot shot = shots.get(event.getDamager().getUniqueId());
            ItemStack source =
                    ranged
                            ? shot == null ? null : shot.item
                            : player.getInventory().getItemInMainHand();
            if (!ranged) plugin.engine().combo(player, victim.getUniqueId());
            EffectContext context =
                    new EffectContext(
                            player,
                            attacker,
                            victim,
                            source,
                            null,
                            event,
                            ranged
                                    ? victim instanceof Player ? "RANGED_HIT" : "RANGED_MOB_HIT"
                                    : victim instanceof Player ? "MELEE_HIT" : "MOB_HIT");
            context.values.put(
                    "headshot",
                    ranged
                            && event.getDamager().getLocation().getY()
                                    >= victim.getEyeLocation().getY() - 0.3);
            plugin.engine().fire(context);
            if (event.getDamager() instanceof Trident trident && source != null)
                trident.setItem(source);
        }
        if (!event.isCancelled()
                && victim instanceof Player player
                && attacker != null
                && attacker != victim) {
            if (player.isBlocking())
                plugin.engine()
                        .fire(
                                new EffectContext(
                                        player,
                                        attacker,
                                        victim,
                                        player.getInventory().getItemInMainHand(),
                                        null,
                                        event,
                                        "SHIELD_BLOCK"));
            String trigger =
                    ranged
                            ? "DEFEND_PROJECTILE"
                            : attacker instanceof Player ? "DEFEND_HIT" : "DEFEND_MOB";
            plugin.engine()
                    .fire(
                            new EffectContext(
                                     player,
                                    attacker,
                                    victim,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    event,
                                    trigger));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        if (plugin.engine().isEffectDamage() || !(event.getEntity() instanceof Player player))
            return;
        String trigger =
                switch (event.getCause()) {
                    case FALL -> "FALL_HURT";
                    case FIRE, FIRE_TICK, LAVA, HOT_FLOOR -> "FIRE_HURT";
                    case BLOCK_EXPLOSION, ENTITY_EXPLOSION -> "BLAST_HURT";
                    default -> null;
                };
        if (trigger != null)
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    null,
                                    player,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    event,
                                    trigger));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void shoot(EntityShootBowEvent event) {
        if (event.getEntity() instanceof Player && event.getBow() != null && shots.size() < 8192)
            shots.put(
                    event.getProjectile().getUniqueId(),
                    new Shot(event.getBow(), System.currentTimeMillis() + 120000));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void launch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof Trident trident
                && event.getEntity().getShooter() instanceof Player
                && shots.size() < 8192)
            shots.put(
                    event.getEntity().getUniqueId(),
                    new Shot(trident.getItem(), System.currentTimeMillis() + 120000));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void death(EntityDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (plugin.managed(victim)) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            return;
        }
        Player killer = victim.getKiller();
        if (killer != null && killer != victim) {
            ItemStack item = killer.getInventory().getItemInMainHand();
            if (victim.getLastDamageCause() instanceof EntityDamageByEntityEvent damage
                    && damage.getDamager() instanceof Projectile projectile) {
                Shot shot = shots.get(projectile.getUniqueId());
                if (shot != null) item = shot.item;
            }
            EffectContext context =
                    new EffectContext(
                            killer,
                            killer,
                            victim,
                            item,
                            null,
                            event,
                            victim instanceof Player ? "PLAYER_SLAY" : "MOB_SLAY");
            context.values.put("event.xp", event.getDroppedExp());
            plugin.engine().fire(context);
            int extra = ((Number) context.values.getOrDefault("extra_drops", 0)).intValue();
            if (extra > 0) {
                List<ItemStack> original = event.getDrops().stream().map(ItemStack::clone).toList();
                for (int i = 0; i < extra && event.getDrops().size() < 256; i++)
                    for (ItemStack drop : original) {
                        if (event.getDrops().size() >= 256) break;
                        event.getDrops().add(drop.clone());
                    }
            }
            track(item, victim instanceof Player ? "bounty" : "slayer");
            if (plugin.items().number(item, "tracker_souls") > 0) {
                plugin.items().increment(item, "souls", 1);
                plugin.items().increment(item, "count_souls", 1);
            }
        }
        if (event instanceof PlayerDeathEvent playerDeath) keep(playerDeath);
    }

    private void keep(PlayerDeathEvent event) {
        Player player = event.getEntity();
        plugin.passive().clear(player);
        if (event.getKeepInventory()) return;
        YamlConfiguration saved = new YamlConfiguration();
        List<ItemStack> kept = new ArrayList<>();
        String pending =
                player.getPersistentDataContainer()
                        .get(plugin.items().key("kept_items"), PersistentDataType.STRING);
        if (pending != null) {
            try {
                YamlConfiguration previous = new YamlConfiguration();
                previous.loadFromString(pending);
                var section = previous.getConfigurationSection("items");
                if (section != null)
                    for (String key : section.getKeys(false)) {
                        ItemStack item = section.getItemStack(key);
                        if (!ItemData.empty(item)) kept.add(item);
                    }
            } catch (Exception error) {
                plugin.getLogger()
                        .warning(
                                "Existing retained items could not be decoded; new retention was"
                                        + " skipped for "
                                        + player.getUniqueId()
                                        + ".");
                return;
            }
        }
        for (ItemStack item : player.getInventory().getContents()) {
            if (kept.size() >= 1024) break;
            if (ItemData.empty(item)) continue;
            boolean protect = plugin.items().number(item, "eternal_scroll") > 0;
            EffectContext context =
                    new EffectContext(
                            player,
                            player.getKiller(),
                            player,
                            item,
                            null,
                            event,
                            player.getKiller() == null ? "NATURAL_DEATH" : "COMBAT_DEATH");
            plugin.engine().fireItem(context);
            if (!protect && !context.values.containsKey("keep:" + System.identityHashCode(item)))
                continue;
            for (ListIterator<ItemStack> iterator = event.getDrops().listIterator();
                    iterator.hasNext(); ) {
                ItemStack drop = iterator.next();
                if (!drop.isSimilar(item) || drop.getAmount() < item.getAmount()) continue;
                if (drop.getAmount() == item.getAmount()) iterator.remove();
                else drop.setAmount(drop.getAmount() - item.getAmount());
                ItemStack restored = item.clone();
                if (protect) {
                    plugin.items().number(restored, "eternal_scroll", 0);
                    plugin.items().render(restored);
                }
                kept.add(restored);
                break;
            }
        }
        if (!kept.isEmpty()) {
            for (int i = 0; i < kept.size(); i++) saved.set("items." + i, kept.get(i));
            player.getPersistentDataContainer()
                    .set(
                            plugin.items().key("kept_items"),
                            PersistentDataType.STRING,
                            saved.saveToString());
        }
    }

    public void restoreKeptItems() {
        for (Player player : Bukkit.getOnlinePlayers()) if (!player.isDead()) restore(player);
    }

    private void restore(Player player) {
        String encoded =
                player.getPersistentDataContainer()
                        .get(plugin.items().key("kept_items"), PersistentDataType.STRING);
        if (encoded == null || player.isDead()) return;
        try {
            YamlConfiguration saved = new YamlConfiguration();
            saved.loadFromString(encoded);
            var section = saved.getConfigurationSection("items");
            if (section == null) return;
            List<ItemStack> remaining = new ArrayList<>();
            for (String key : section.getKeys(false)) {
                ItemStack item = section.getItemStack(key);
                if (ItemData.empty(item)) continue;
                remaining.addAll(player.getInventory().addItem(item).values());
            }
            if (remaining.isEmpty())
                player.getPersistentDataContainer().remove(plugin.items().key("kept_items"));
            else {
                YamlConfiguration pending = new YamlConfiguration();
                for (int i = 0; i < remaining.size(); i++)
                    pending.set("items." + i, remaining.get(i));
                player.getPersistentDataContainer()
                        .set(
                                plugin.items().key("kept_items"),
                                PersistentDataType.STRING,
                                pending.saveToString());
            }
        } catch (Exception error) {
            plugin.getLogger()
                    .warning("Kept items could not be restored for " + player.getUniqueId() + ".");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void mining(BlockBreakEvent event) {
        if (plugin.blocks().checkingProtection()) return;
        Player player = event.getPlayer();
        if (plugin.blocks().extraBreak()) {
            EffectContext parent = plugin.blocks().extraContext();
            if (parent != null) {
                Map<String, Object> flags = new HashMap<>(parent.values);
                flags.remove("replacement");
                flags.remove("pending blocks");
                drops.put(key(player, event.getBlock()), flags);
            }
            return;
        }
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        EffectContext context =
                new EffectContext(
                        player,
                        player,
                        null,
                        player.getInventory().getItemInMainHand(),
                        event.getBlock(),
                        event,
                        "BLOCK_BREAK");
        context.values.put("event.xp", event.getExpToDrop());
        plugin.engine().fire(context);
        if (!event.isCancelled())
            drops.put(key(player, event.getBlock()), new HashMap<>(context.values));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void mined(BlockBreakEvent event) {
        if (plugin.blocks().checkingProtection()) return;
        plugin.blocks().placed(event.getBlock(), false);
        if (event.getPlayer().getGameMode() == GameMode.SURVIVAL)
            track(event.getPlayer().getInventory().getItemInMainHand(), "mining");
        DropKey key = key(event.getPlayer(), event.getBlock());
        plugin.getServer().getScheduler().runTask(plugin, () -> drops.remove(key));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockDrops(BlockDropItemEvent event) {
        Map<String, Object> flags = drops.remove(key(event.getPlayer(), event.getBlock()));
        if (flags == null) return;
        int extra = ((Number) flags.getOrDefault("extra_drops", 0)).intValue();
        boolean smelt = Boolean.TRUE.equals(flags.get("smelt"));
        boolean telepathy = Boolean.TRUE.equals(flags.get("telepathy"));
        for (Item drop : event.getItems()) {
            ItemStack item = drop.getItemStack();
            if (smelt) item = smelt(item);
            int total = Math.min(512, item.getAmount() * (1 + extra));
            item.setAmount(Math.min(item.getMaxStackSize(), total));
            drop.setItemStack(item);
            int remaining = total - item.getAmount();
            if (telepathy) {
                var leftovers = event.getPlayer().getInventory().addItem(item);
                if (leftovers.isEmpty()) drop.remove();
                else drop.setItemStack(leftovers.values().iterator().next());
            }
            while (remaining > 0) {
                ItemStack added = item.clone();
                added.setAmount(Math.min(remaining, added.getMaxStackSize()));
                remaining -= added.getAmount();
                var leftover =
                        telepathy
                                ? event.getPlayer().getInventory().addItem(added)
                                : Map.of(0, added);
                for (ItemStack rest : leftover.values())
                    event.getPlayer()
                            .getWorld()
                            .dropItemNaturally(event.getBlock().getLocation(), rest);
            }
        }
        if (flags.get("replacement") instanceof Material replacement) {
            Block block = event.getBlock();
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (!block.getWorld()
                                                .isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                                         || !block.isEmpty()) return;
                                var before = block.getState();
                                block.setType(replacement, false);
                                BlockPlaceEvent place =
                                        new BlockPlaceEvent(
                                                block,
                                                before,
                                                block,
                                                event.getPlayer()
                                                        .getInventory()
                                                        .getItemInMainHand(),
                                                event.getPlayer(),
                                                true,
                                                EquipmentSlot.HAND);
                                Bukkit.getPluginManager().callEvent(place);
                                if (place.isCancelled() || !place.canBuild())
                                    before.update(true, false);
                                else plugin.blocks().placed(block, true);
                            });
        }
    }

    private ItemStack smelt(ItemStack input) {
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            var recipe = iterator.next();
            if (recipe instanceof FurnaceRecipe furnace && furnace.getInputChoice().test(input)) {
                ItemStack result = furnace.getResult();
                result.setAmount(Math.min(64, input.getAmount() * result.getAmount()));
                return result;
            }
        }
        return input;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void place(BlockPlaceEvent event) {
        plugin.blocks().placed(event.getBlockPlaced(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !(event.getAction() == Action.RIGHT_CLICK_AIR
                        || event.getAction() == Action.RIGHT_CLICK_BLOCK)) return;
        Player player = event.getPlayer();
        try {
            if (player.hasPermission("customenchants.items")
                    && plugin.specialItems().open(player, event.getItem())) {
                event.setCancelled(true);
                return;
            }
        } catch (IllegalArgumentException error) {
            player.sendMessage(Text.color("&c" + error.getMessage()));
            return;
        }
        plugin.engine()
                .fire(
                        new EffectContext(
                                player,
                                player,
                                player,
                                event.getItem(),
                                event.getClickedBlock(),
                                event,
                                "INTERACT_RIGHT"));
        if (event.getItem() != null && event.getItem().getType() == Material.GOAT_HORN)
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    null,
                                    event.getItem(),
                                    event.getClickedBlock(),
                                    event,
                                    "HORN_USE"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void fish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        LivingEntity victim = event.getCaught() instanceof LivingEntity living ? living : null;
        String trigger =
                switch (event.getState()) {
                    case BITE -> "FISH_BITE";
                    case CAUGHT_FISH -> "FISH_CATCH";
                    case CAUGHT_ENTITY -> "ROD_SNARE";
                    case FISHING -> "LINE_CAST";
                    default -> null;
                };
        if (trigger == null) return;
        EffectContext context =
                new EffectContext(
                        player,
                        player,
                        victim,
                        player.getInventory().getItemInMainHand(),
                        null,
                        event,
                        trigger);
        context.values.put("event.xp", event.getExpToDrop());
        plugin.engine().fire(context);
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && !event.isCancelled()) {
            track(context.item, "angling");
            if (event.getCaught() instanceof Item item) {
                ItemStack stack = item.getItemStack();
                stack.setAmount(
                        Math.min(
                                64,
                                stack.getAmount()
                                        * (1
                                                + ((Number)
                                                                context.values.getOrDefault(
                                                                        "extra_drops", 0))
                                                        .intValue())));
                item.setItemStack(stack);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void breakItem(PlayerItemDamageEvent event) {
        if (!(event.getItem().getItemMeta() instanceof Damageable damage)
                || damage.getDamage() + event.getDamage()
                        < event.getItem().getType().getMaxDurability()) return;
        var context =
                new EffectContext(
                        event.getPlayer(),
                        event.getPlayer(),
                        event.getPlayer(),
                        event.getItem(),
                        null,
                        event,
                        "TOOL_BREAK");
        plugin.engine().fireItem(context);
        if (event.getItem().getItemMeta() instanceof Damageable after
                && after.getDamage() < damage.getDamage()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void join(PlayerJoinEvent event) {
        plugin.getServer()
                .getScheduler()
                .runTask(
                        plugin,
                        () -> {
                            Player player = event.getPlayer();
                            restore(player);
                            plugin.passive().refresh(player);
                            plugin.engine()
                                    .fire(
                                            new EffectContext(
                                                    player,
                                                    player,
                                                    player,
                                                    player.getInventory().getItemInMainHand(),
                                                    null,
                                                    null,
                                                    "PLAYER_JOIN"));
                        });
    }

    @EventHandler
    public void respawn(PlayerRespawnEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> restore(event.getPlayer()));
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        plugin.engine()
                .fire(
                        new EffectContext(
                                player,
                                player,
                                player,
                                player.getInventory().getItemInMainHand(),
                                null,
                                null,
                                "PLAYER_QUIT"));
        plugin.passive().clear(player);
        plugin.engine().clear(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void move(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        event.setTo(plugin.passive().walk(event.getPlayer(), event.getTo()));
        Player player = event.getPlayer();
        if (!player.isFlying()
                && !player.isGliding()
                && event.getTo().getY() - event.getFrom().getY() > 0.3
                && !event.getFrom().clone().subtract(0, 0.05, 0).getBlock().isPassable())
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    player,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    event,
                                    "JUMP_MOVE"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent event) {
        plugin.passive().refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void glide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player)
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    player,
                                    player.getInventory().getChestplate(),
                                    null,
                                    event,
                                    "GLIDE_START"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void consume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        plugin.engine()
                .fire(
                        new EffectContext(
                                player,
                                player,
                                player,
                                event.getItem(),
                                null,
                                event,
                                "ITEM_CONSUME"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent event) {
        Player player = event.getPlayer();
        plugin.engine()
                .fire(
                        new EffectContext(
                                player,
                                player,
                                player,
                                player.getInventory().getItemInMainHand(),
                                null,
                                event,
                                "HAND_SWING"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void sneak(PlayerToggleSneakEvent event) {
        if (event.isSneaking()) {
            Player player = event.getPlayer();
            plugin.engine()
                    .fire(
                            new EffectContext(
                                    player,
                                    player,
                                    player,
                                    player.getInventory().getItemInMainHand(),
                                    null,
                                    event,
                                    "SNEAK_TOGGLE"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void target(EntityTargetLivingEntityEvent event) {
        var guard = plugin.guards().get(event.getEntity().getUniqueId());
        if (guard != null
                && event.getTarget() != null
                && !guard.target().equals(event.getTarget().getUniqueId()))
            event.setCancelled(true);
    }

    public void prune() {
        long now = System.currentTimeMillis();
        shots.entrySet().removeIf(e -> e.getValue().expires < now);
        for (Player player : Bukkit.getOnlinePlayers()) if (!player.isDead()) restore(player);
    }

    private DropKey key(Player player, Block block) {
        return new DropKey(
                player.getUniqueId(),
                block.getWorld().getUID(),
                block.getX(),
                block.getY(),
                block.getZ());
    }

    private void track(ItemStack item, String tracker) {
        if (plugin.items().number(item, "tracker_" + tracker) > 0)
            plugin.items().increment(item, "count_" + tracker, 1);
    }
}
