package io.github.lino9999.customenchants.config;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record Settings(
        Map<String, EnchantDefinition> enchants,
        Map<String, Group> groups,
        Map<String, Equipment> sets,
        Map<String, Equipment> weapons,
        Map<String, Kit> kits,
        int slots,
        int maxSlots,
        boolean destroyOnFailure,
        int maxBlocks,
        int maxTargets,
        int maxDelay,
        Set<String> disabledWorlds,
        double tableChance,
        double lootChance,
        double tradeChance,
        int alchemistCost,
        int tinkerReward,
        int periodicTicks,
        Map<String, String> messages,
        TableOptions table,
        List<LootChoice> lootPool,
        List<LootChoice> tradePool,
        int lootMaximum,
        Map<String, String> conditionAliases) {
    public record Group(String id, String name, String color, int cost, String currency) {}

    public record Equipment(
            String id,
            String name,
            String material,
            String requiredSet,
            Map<String, Piece> pieces,
            Map<String, EnchantDefinition.Level> events) {}

    public record Piece(
            String material,
            String name,
            List<String> lore,
            int modelData,
            List<String> enchants) {}

    public record Kit(String id, String name, long cooldownSeconds, List<String> items) {}

    public record TableOptions(
            double bookChance,
            List<List<String>> bookGroups,
            List<Integer> minimumCounts,
            List<Integer> maximumCounts,
            List<Integer> prices) {}

    public record LootChoice(String kind, String id, int weight, int value, String group) {}

    public boolean enabled(String world) {
        return !disabledWorlds.contains(world);
    }
}
