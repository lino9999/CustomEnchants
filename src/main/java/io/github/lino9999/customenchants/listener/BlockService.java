package io.github.lino9999.customenchants.listener;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.effect.TargetSelector;
import io.github.lino9999.customenchants.enchant.ItemData;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockService {
    private final CustomEnchants plugin;
    private final NamespacedKey placedKey;
    private boolean extraBreak;
    private EffectContext extraContext;
    private boolean checkingProtection;

    public BlockService(CustomEnchants plugin) {
        this.plugin = plugin;
        placedKey = new NamespacedKey(plugin, "placed_blocks");
    }

    public boolean extraBreak() {
        return extraBreak;
    }

    public EffectContext extraContext() {
        return extraContext;
    }

    public boolean checkingProtection() {
        return checkingProtection;
    }

    public boolean allowBreak(Player owner, Block block) {
        boolean previous = checkingProtection;
        checkingProtection = true;
        try {
            var event = new BlockBreakEvent(block, owner);
            Bukkit.getPluginManager().callEvent(event);
            return !event.isCancelled();
        } finally {
            checkingProtection = previous;
        }
    }

    @SuppressWarnings("unchecked")
    public void queue(EffectContext context, Block block) {
        var queued =
                (Map<Block, Map<String, Object>>)
                        context.values.computeIfAbsent(
                                "pending blocks", key -> new LinkedHashMap<>());
        if (queued.size() < plugin.settings().maxBlocks())
            queued.putIfAbsent(
                    block,
                    Map.of(
                            "whitelist",
                            context.values.getOrDefault("whitelist", Set.of()),
                            "blacklist",
                            context.values.getOrDefault("blacklist", Set.of())));
    }

    @SuppressWarnings("unchecked")
    public void flush(EffectContext context) {
        Map<Block, Map<String, Object>> queued =
                (Map<Block, Map<String, Object>>) context.values.remove("pending blocks");
        if (queued == null
                || context.event instanceof org.bukkit.event.Cancellable event
                        && event.isCancelled()) return;
        queued.forEach(
                (block, filters) -> {
                    EffectContext selected =
                            new EffectContext(
                                    context.owner,
                                    context.attacker,
                                    context.victim,
                                    context.item,
                                    context.block,
                                    context.event,
                                    context.trigger);
                    selected.values.putAll(context.values);
                    selected.values.putAll(filters);
                    breakSelected(selected, block);
                });
    }

    private long position(Block block) {
        return ((long) (block.getY() - block.getWorld().getMinHeight()) << 8)
                | ((block.getX() & 15) << 4)
                | (block.getZ() & 15);
    }

    public boolean isPlaced(Block block) {
        long[] values =
                block.getChunk()
                        .getPersistentDataContainer()
                        .get(placedKey, PersistentDataType.LONG_ARRAY);
        return values != null && Arrays.binarySearch(values, position(block)) >= 0;
    }

    public void placed(Block block, boolean value) {
        var pdc = block.getChunk().getPersistentDataContainer();
        long[] old = pdc.getOrDefault(placedKey, PersistentDataType.LONG_ARRAY, new long[0]);
        long position = position(block);
        int index = Arrays.binarySearch(old, position);
        if ((index >= 0) == value) return;
        long[] updated;
        if (value) {
            updated = Arrays.copyOf(old, old.length + 1);
            updated[old.length] = position;
            Arrays.sort(updated);
        } else {
            updated = new long[old.length - 1];
            System.arraycopy(old, 0, updated, 0, index);
            System.arraycopy(old, index + 1, updated, index, old.length - index - 1);
        }
        if (updated.length == 0) pdc.remove(placedKey);
        else pdc.set(placedKey, PersistentDataType.LONG_ARRAY, updated);
    }

    public List<Block> select(EffectContext context, TargetSelector selector) {
        return new BlockTargets(plugin).select(context, selector);
    }

    public void area(EffectContext context, boolean tree) {
        queueArea(context, tree);
        flush(context);
    }

    public void queueArea(EffectContext context, boolean tree) {
        if (context.block == null || extraBreak) return;
        String selector = String.valueOf(context.values.getOrDefault("selector", "cube_area[size=3]"));
        if (tree) {
            String material = context.block.getType().name();
            if (!material.endsWith("_LOG")
                    && !material.endsWith("_STEM")
                    && !material.endsWith("_HYPHAE")) return;
            EffectContext selected =
                    new EffectContext(
                            context.owner,
                            context.attacker,
                            context.victim,
                            context.item,
                            context.block,
                            context.event,
                            context.trigger);
            selected.values.putAll(context.values);
            selected.values.put("whitelist", Set.of(material));
            for (Block block :
                    select(
                            selected,
                            TargetSelector.parse(
                                    "vein_area[max_blocks="
                                            + (plugin.settings().maxBlocks() + 1)
                                            + "]"))) {
                queue(context, block);
            }
        } else {
            for (Block block : select(context, TargetSelector.parse(selector)))
                queue(context, block);
        }
    }

    public void breakSelected(EffectContext context, Block block) {
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return;
        if (block.equals(context.block) && context.event instanceof BlockBreakEvent) return;
        if (extraBreak
                || context.owner.getGameMode() != GameMode.SURVIVAL
                || block.isEmpty()
                || block.isLiquid()
                || block.getType().getHardness() < 0
                || !BlockTargets.allowed(context, block)) return;
        if (ItemData.empty(context.owner.getInventory().getItemInMainHand())) return;
        extraBreak = true;
        EffectContext previousContext = extraContext;
        extraContext = context;
        try {
            context.owner.breakBlock(block);
        } finally {
            extraBreak = false;
            extraContext = previousContext;
        }
    }

    public void replace(EffectContext c, String material) {
        if (c.block == null) return;
        Material type = Material.matchMaterial(material);
        if (type == null || !type.isBlock() || type.getHardness() < 0)
            throw new IllegalArgumentException("Invalid replacement block: " + material);
        c.values.put("replacement", type);
    }

    public void replaceAt(EffectContext context, Block block, String material) {
        Material replacement = Material.matchMaterial(material);
        if (replacement == null || !replacement.isBlock() || replacement.getHardness() < 0)
            throw new IllegalArgumentException("Invalid replacement block: " + material);
        Material expected = block.getType();
        plugin.later(
                () -> {
                    if (!context.owner.isOnline()
                            || !block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                            || (block.getType() != expected && !block.isEmpty())) return;
                    boolean previous = extraBreak;
                    extraBreak = true;
                    try {
                        var permission = new BlockBreakEvent(block, context.owner);
                        Bukkit.getPluginManager().callEvent(permission);
                        if (permission.isCancelled()) return;
                        BlockState before = block.getState();
                        block.setType(replacement, false);
                        var placement =
                                new BlockPlaceEvent(
                                        block,
                                        before,
                                        block.getRelative(BlockFace.DOWN),
                                        context.item,
                                        context.owner,
                                        true,
                                        EquipmentSlot.HAND);
                        Bukkit.getPluginManager().callEvent(placement);
                        if (placement.isCancelled() || !placement.canBuild())
                            before.update(true, false);
                        else placed(block, true);
                    } finally {
                        extraBreak = previous;
                    }
                },
                2);
    }

    public void plant(EffectContext c, int radius, String seed) {
        if (c.block == null) return;
        Material seedType = Material.matchMaterial(seed);
        Material crop =
                switch (seed) {
                    case "CARROT" -> Material.CARROTS;
                    case "POTATO" -> Material.POTATOES;
                    case "BEETROOT_SEEDS" -> Material.BEETROOTS;
                    default -> Material.WHEAT;
                };
        if (seedType == null) return;
        if (c.trigger.equals("BLOCK_BREAK")) {
            Block block = c.block;
            plugin.getServer()
                    .getScheduler()
                    .runTask(
                            plugin,
                            () -> {
                                if (c.owner.isOnline()
                                        && block.getWorld()
                                                .isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)
                                        && block.isEmpty())
                                    place(c.owner, block, seedType, crop, c.item);
                            });
            return;
        }
        Block origin =
                c.block.getType() == Material.FARMLAND
                        ? c.block.getRelative(BlockFace.UP)
                        : c.block;
        int budget = plugin.settings().maxBlocks();
        for (int x = -radius; x <= radius && budget > 0; x++)
            for (int z = -radius; z <= radius && budget-- > 0; z++) {
                int absoluteX = origin.getX() + x, absoluteZ = origin.getZ() + z;
                if (origin.getWorld().isChunkLoaded(absoluteX >> 4, absoluteZ >> 4))
                    place(c.owner, origin.getRelative(x, 0, z), seedType, crop, c.item);
            }
    }

    private void place(Player player, Block block, Material seed, Material crop, ItemStack tool) {
        if (!block.isEmpty()
                || block.getRelative(BlockFace.DOWN).getType() != Material.FARMLAND
                || !player.getInventory().contains(seed)) return;
        BlockState before = block.getState();
        block.setType(crop, false);
        BlockPlaceEvent event =
                new BlockPlaceEvent(
                        block,
                        before,
                        block.getRelative(BlockFace.DOWN),
                        new ItemStack(seed),
                        player,
                        true,
                        EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled() || !event.canBuild()) {
            before.update(true, false);
            return;
        }
        if (player.getGameMode() != GameMode.CREATIVE)
            player.getInventory().removeItem(new ItemStack(seed));
        placed(block, true);
    }
}
