package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Collection;
import java.util.Locale;
import java.util.Random;

final class WorldEffects {
    private final CustomEnchants plugin;

    WorldEffects(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    void register(EffectRegistry registry) {
        registry.registerLocation(
                "break_block", (c, t, a) -> plugin.blocks().queue(c, t.getBlock()));
        registry.register("fell_tree", (c, t, a) -> plugin.blocks().queueArea(c, true));
        registry.registerLocation("fell_tree", (c, t, a) -> plugin.blocks().queueArea(c, true));
        registry.register(
                "place_block",
                (c, t, a) -> plugin.blocks().replace(c, EffectRegistry.argument(a, 0, "AIR")));
        registry.registerLocation(
                "place_block",
                (c, t, a) -> {
                    if (c.event instanceof BlockBreakEvent
                            && c.block != null
                            && t.getBlock().equals(c.block))
                        plugin.blocks().replace(c, EffectRegistry.argument(a, 0, "AIR"));
                    else
                        plugin.blocks()
                                .replaceAt(c, t.getBlock(), EffectRegistry.argument(a, 0, "AIR"));
                });
        registry.register(
                "sow_seeds",
                (c, t, a) ->
                        plugin.blocks()
                                .plant(
                                        c,
                                        (int) Math.clamp(EffectRegistry.number(a, 0, 1), 0, 3),
                                        EffectRegistry.argument(a, 1, "WHEAT_SEEDS")));
        registry.register("magnetize_drops", (c, t, a) -> c.values.put("telepathy", true));
        registry.register("smelt_drops", (c, t, a) -> c.values.put("smelt", true));
        registry.register(
                "multiply_drops",
                (c, t, a) -> {
                    if (c.block == null || !plugin.blocks().isPlaced(c.block))
                        c.values.put(
                                "extra_drops",
                                (int) Math.clamp(EffectRegistry.number(a, 0, 1), 0, 8));
                });
        registry.register(
                "set_xp_drop",
                (c, t, a) -> {
                    int value = (int) Math.clamp(EffectRegistry.number(a, 0, 0), 0, 1000000);
                    if (c.event instanceof BlockBreakEvent event) event.setExpToDrop(value);
                    else if (c.event instanceof EntityDeathEvent event) event.setDroppedExp(value);
                    else if (c.event instanceof PlayerFishEvent event) event.setExpToDrop(value);
                });
        registry.register(
                "instant_reel",
                (c, t, a) -> {
                    if (!(c.event instanceof PlayerFishEvent event)
                            || event.getState() != PlayerFishEvent.State.BITE) return;
                    FishHook hook = event.getHook();
                    var random = Bukkit.getLootTable(NamespacedKey.minecraft("gameplay/fishing"));
                    if (random == null) return;
                    var context =
                            new org.bukkit.loot.LootContext.Builder(hook.getLocation())
                                    .killer(c.owner)
                                    .lootedEntity(hook)
                                    .build();
                    Collection<ItemStack> loot = random.populateLoot(new Random(), context);
                    for (ItemStack item : loot)
                        c.owner.getWorld().dropItemNaturally(c.owner.getLocation(), item);
                    c.owner.giveExp(Numbers.random(1, 6));
                    hook.remove();
                });
        registry.register(
                "drop_trophy_head",
                (c, t, a) -> {
                    if (!(t instanceof Player player)) return;
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    meta.setOwningPlayer(player);
                    head.setItemMeta(meta);
                    if (c.event instanceof EntityDeathEvent event) event.getDrops().add(head);
                });
        registry.register(
                "crimson_burst",
                (c, t, a) ->
                        t.getWorld()
                                .spawnParticle(
                                        Particle.BLOCK,
                                        t.getLocation().add(0, 1, 0),
                                        12,
                                        0.3,
                                        0.3,
                                        0.3,
                                        Material.REDSTONE_BLOCK.createBlockData()));
        registry.register("thorn_burst", (c, t, a) -> cactus(t.getLocation()));
        registry.registerLocation("thorn_burst", (c, t, a) -> cactus(t));
        registry.register(
                "emit_particles",
                (c, t, a) -> {
                    String name = EffectRegistry.argument(a, 0, "SMOKE");
                    Particle particle;
                    try {
                        particle = Particle.valueOf(name);
                    } catch (IllegalArgumentException ignored) {
                        particle = Particle.SMOKE;
                    }
                    int count = (int) Math.clamp(EffectRegistry.number(a, 1, 8), 1, 64);
                    if (particle.getDataType() == Void.class)
                        t.getWorld()
                                .spawnParticle(
                                        particle,
                                        t.getLocation().add(0, 1, 0),
                                        count,
                                        0.3,
                                        0.4,
                                        0.3);
                    else
                        t.getWorld()
                                .spawnParticle(
                                        Particle.SMOKE,
                                        t.getLocation().add(0, 1, 0),
                                        count,
                                        0.3,
                                        0.4,
                                        0.3);
                });
        EffectRegistry.Action sound =
                (c, t, a) -> {
                    String name = EffectRegistry.argument(a, 0, "ENTITY_EXPERIENCE_ORB_PICKUP");
                    String key =
                            name.equals("ENCHANT_THORNS_HIT")
                                    ? "enchant.thorns.hit"
                                    : name.toLowerCase(Locale.ROOT).replace('_', '.');
                    Sound soundType = Registry.SOUNDS.get(NamespacedKey.minecraft(key));
                    if (soundType == null) soundType = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                    t.getWorld()
                            .playSound(
                                    t.getLocation(),
                                    soundType,
                                    (float) Math.clamp(EffectRegistry.number(a, 1, 1), 0, 2),
                                    (float) Math.clamp(EffectRegistry.number(a, 2, 1), 0.1, 2));
                };
        registry.register("play_sound", sound);
        registry.register("broadcast_sound", sound);
        registry.register(
                "strike_lightning",
                (c, t, a) -> plugin.spawns().lightning(c, t.getLocation(), real(a)));
        registry.registerLocation(
                "strike_lightning", (c, t, a) -> plugin.spawns().lightning(c, t, real(a)));
        registry.register(
                "detonate_tnt",
                (c, t, a) ->
                        plugin.spawns()
                                .tnt(
                                        c,
                                        t.getLocation(),
                                        EffectRegistry.number(a, 0, 3),
                                        EffectRegistry.number(a, 1, 3)));
        registry.registerLocation(
                "detonate_tnt",
                (c, t, a) ->
                        plugin.spawns()
                                .tnt(
                                        c,
                                        t,
                                        EffectRegistry.number(a, 0, 3),
                                        EffectRegistry.number(a, 1, 3)));
        registry.register(
                "launch_fireball", (c, t, a) -> plugin.spawns().fireball(c, t.getEyeLocation(), t));
        registry.registerLocation(
                "launch_fireball", (c, t, a) -> plugin.spawns().fireball(c, t, c.owner));
        registry.register(
                "volley_arrows",
                (c, t, a) -> plugin.spawns().arrows(c, t.getLocation().add(0, 1, 0)));
        registry.registerLocation("volley_arrows", (c, t, a) -> plugin.spawns().arrows(c, t));
    }

    private static void cactus(Location location) {
        location.getWorld().spawnParticle(Particle.DRIPPING_LAVA, location, 10, 0, 0, 0, 0);
        location.getWorld()
                .playEffect(
                        location.clone().add(0, .8, 0),
                        org.bukkit.Effect.STEP_SOUND,
                        Material.CACTUS);
    }

    private static boolean real(String[] args) {
        String value = EffectRegistry.argument(args, 0, "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException(
                    "Lightning requires a true or false real-strike flag.");
        return Boolean.parseBoolean(value);
    }
}
