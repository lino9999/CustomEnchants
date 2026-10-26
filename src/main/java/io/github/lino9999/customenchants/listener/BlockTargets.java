package io.github.lino9999.customenchants.listener;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.effect.TargetSelector;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class BlockTargets {
    private final CustomEnchants plugin;

    BlockTargets(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    List<Block> select(EffectContext context, TargetSelector selector) {
        Block origin = context.block;
        if (origin == null
                || !origin.getWorld().isChunkLoaded(origin.getX() >> 4, origin.getZ() >> 4)
                || origin.getType().name().equals("FIRE")
                || !allowed(context, origin)) return List.of();
        if (selector.name().equals("vein_area")) return vein(context, selector);
        int width, height, depth;
        boolean custom =
                selector.arguments().containsKey("bounds")
                        || selector.name().equals("tunnel_area");
        if (custom) {
            String[] dimensions =
                    selector.text("bounds", "1x3x5").toLowerCase(Locale.ROOT).split("x");
            if (dimensions.length != 3)
                throw new IllegalArgumentException(
                        "Target dimensions require width x height x depth.");
            width = dimension(dimensions[0]);
            height = dimension(dimensions[1]);
            depth = dimension(dimensions[2]);
        } else {
            width = height = depth = dimension(selector.text("size", "3"));
        }
        var facing =
                facing(
                        context.owner.getLocation().getYaw(),
                        context.owner.getLocation().getPitch());
        Vector forward = facing.getDirection();
        Vector up =
                facing == BlockFace.UP
                        ? new Vector(0, 0, -1)
                        : facing == BlockFace.DOWN ? new Vector(0, 0, 1) : new Vector(0, 1, 0);
        Vector right = up.clone().crossProduct(forward);
        int slope = 0;
        if (selector.name().equals("tunnel_area"))
            slope =
                    switch (selector.text("direction", "STRAIGHT").toUpperCase(Locale.ROOT)) {
                        case "UP" -> 1;
                        case "DOWN" -> -1;
                        case "STRAIGHT" -> 0;
                        case "AUTO" ->
                                context.owner.getLocation().getPitch() < -20
                                        ? 1
                                        : context.owner.getLocation().getPitch() > 20 ? -1 : 0;
                        default -> throw new IllegalArgumentException("Unknown tunnel direction.");
                    };
        List<Block> selected = new ArrayList<>();
        boolean ignoreTool = selector.flag("any_tool", false);
        for (int y = -height / 2; y <= height / 2; y++)
            for (int x = -width / 2; x <= width / 2; x++)
                for (int z = custom ? 0 : -depth / 2; z <= (custom ? depth - 1 : depth / 2); z++) {
                    Vector offset =
                            custom
                                    ? right.clone()
                                            .multiply(x)
                                            .add(up.clone().multiply(y + slope * z))
                                            .add(forward.clone().multiply(z))
                                    : new Vector(x, y, z);
                    Block block =
                            relative(
                                    origin,
                                    offset.getBlockX(),
                                    offset.getBlockY(),
                                    offset.getBlockZ());
                    if (block != null
                            && !block.equals(origin)
                            && suitable(context, block, ignoreTool)) selected.add(block);
                    if (selected.size() >= plugin.settings().maxBlocks())
                        return List.copyOf(selected);
                }
        return List.copyOf(selected);
    }

    private List<Block> vein(EffectContext context, TargetSelector selector) {
        if (plugin.blocks().isPlaced(context.block)) return List.of();
        int count =
                (int)
                        Math.clamp(
                                selector.number("max_blocks", 75),
                                1,
                                plugin.settings().maxBlocks() + 1);
        Set<Block> seen = new HashSet<>();
        ArrayDeque<Block> pending = new ArrayDeque<>();
        List<Block> found = new ArrayList<>();
        pending.push(context.block);
        int matching = 0;
        BlockFace[] faces = BlockFace.values();
        while (!pending.isEmpty() && matching < count) {
            Block block = pending.pop();
            if (!seen.add(block) || block.getType() != context.block.getType()) continue;
            matching++;
            if (!block.equals(context.block)
                    && !plugin.blocks().isPlaced(block)
                    && allowed(context, block)) found.add(block);
            for (int index = faces.length - 1; index >= 0; index--) {
                BlockFace face = faces[index];
                if (face == BlockFace.SELF) continue;
                Block next = relative(block, face.getModX(), face.getModY(), face.getModZ());
                if (next != null && !seen.contains(next)) pending.push(next);
            }
        }
        return List.copyOf(found);
    }

    static boolean suitable(EffectContext context, Block block, boolean ignoreTool) {
        return !block.isEmpty()
                && !block.isLiquid()
                && block.getType().getHardness() >= 0
                && !block.getType().name().endsWith("SHULKER_BOX")
                && !block.hasMetadata("cancelBreak")
                && (ignoreTool || block.isPreferredTool(context.item))
                && allowed(context, block);
    }

    @SuppressWarnings("unchecked")
    private static Set<String> whitelist(EffectContext context) {
        return (Set<String>) context.values.getOrDefault("whitelist", Set.of());
    }

    @SuppressWarnings("unchecked")
    static boolean allowed(EffectContext context, Block block) {
        Set<String> whitelist = whitelist(context);
        Set<String> blacklist = (Set<String>) context.values.getOrDefault("blacklist", Set.of());
        return (whitelist.isEmpty() || whitelist.contains(block.getType().name()))
                && !blacklist.contains(block.getType().name());
    }

    private static int dimension(String input) {
        int value = Integer.parseInt(input);
        if (value < 1 || value > 63 || value % 2 == 0)
            throw new IllegalArgumentException(
                    "Block target dimensions must be odd integers between 1 and 63.");
        return value;
    }

    private static BlockFace facing(float yaw, float pitch) {
        if (pitch <= -80) return BlockFace.UP;
        if (pitch >= 80) return BlockFace.DOWN;
        return List.of(BlockFace.SOUTH, BlockFace.WEST, BlockFace.NORTH, BlockFace.EAST)
                .get(Math.floorMod(Math.round(yaw / 90), 4));
    }

    private static Block relative(Block origin, int x, int y, int z) {
        int absoluteX = origin.getX() + x,
                absoluteY = origin.getY() + y,
                absoluteZ = origin.getZ() + z;
        var world = origin.getWorld();
        if (absoluteY < world.getMinHeight()
                || absoluteY >= world.getMaxHeight()
                || !world.isChunkLoaded(absoluteX >> 4, absoluteZ >> 4)) return null;
        return world.getBlockAt(absoluteX, absoluteY, absoluteZ);
    }
}
