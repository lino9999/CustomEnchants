package io.github.lino9999.customenchants.config;

import io.github.lino9999.customenchants.effect.Conditions;
import io.github.lino9999.customenchants.effect.EffectRegistry;
import io.github.lino9999.customenchants.feature.SpecialItems;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

public final class ConfigurationLoader {
    private static final Pattern ID = Pattern.compile("[a-z0-9_]{1,64}");
    private final JavaPlugin plugin;

    public ConfigurationLoader(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Settings load() throws Exception {
        YamlConfiguration config = file("config.yml");
        YamlConfiguration catalog = file("enchantments.yml");
        YamlConfiguration gear = file("equipment.yml");
        YamlConfiguration kitsFile = file("kits.yml");
        YamlConfiguration messagesFile = file("messages.yml");
        Map<String, String> presets = new LinkedHashMap<>();
        ConfigurationSection presetSection = config.getConfigurationSection("presets");
        if (presetSection != null)
            presetSection
                    .getValues(false)
                    .forEach(
                            (name, value) -> {
                                String normalized =
                                        name.replace("{", "")
                                                .replace("}", "")
                                                .trim()
                                                .toLowerCase(Locale.ROOT);
                                if (normalized.isEmpty() || value == null)
                                    throw new IllegalArgumentException("Invalid preset variable.");
                                presets.put(normalized, value.toString());
                                presets.put("preset." + normalized, value.toString());
                            });
        Map<String, Settings.Group> groups = new LinkedHashMap<>();
        ConfigurationSection groupSection = section(config, "rarities");
        if (groupSection.getKeys(false).isEmpty() || groupSection.getKeys(false).size() > 36)
            throw new IllegalArgumentException("Configure between 1 and 36 rarities.");
        for (String id : groupSection.getKeys(false)) {
            ConfigurationSection g = section(groupSection, id);
            String currency = g.getString("currency", "XP").toUpperCase(Locale.ROOT);
            if (!Set.of("XP", "LEVELS", "MONEY").contains(currency))
                throw new IllegalArgumentException("Unknown currency: " + currency);
            groups.put(
                    id,
                    new Settings.Group(
                            id,
                            g.getString("label", id),
                            g.getString("color", "&7"),
                            bounded(g, "cost", 0, 100000000),
                            currency));
        }
        Map<String, EnchantDefinition> enchants = new LinkedHashMap<>();
        for (String id : catalog.getKeys(false)) {
            identifier(id);
            ConfigurationSection e = section(catalog, id);
            String group = e.getString("rarity", "COMMON");
            if (!groups.containsKey(group))
                throw new IllegalArgumentException(id + ": unknown rarity " + group);
            Set<String> triggers = new LinkedHashSet<>(e.getStringList("events"));
            if (triggers.isEmpty() || !EffectRegistry.TRIGGERS.containsAll(triggers))
                throw new IllegalArgumentException(id + ": invalid events " + triggers);
            List<String> materials = e.getStringList("applies-to");
            if (materials.isEmpty())
                throw new IllegalArgumentException(id + ": applies-to cannot be empty.");
            Map<Integer, EnchantDefinition.Level> levels = new TreeMap<>();
            ConfigurationSection ls = section(e, "tiers");
            for (String n : ls.getKeys(false)) {
                int level = Integer.parseInt(n);
                if (level < 1 || level > 100)
                    throw new IllegalArgumentException(id + ": tier outside 1..100.");
                levels.put(level, level(section(ls, n)));
            }
            if (levels.isEmpty()) throw new IllegalArgumentException(id + ": no tiers.");
            enchants.put(
                    id,
                    new EnchantDefinition(
                            id,
                            e.getString("title", id),
                            e.getString("summary", ""),
                            group,
                            Set.copyOf(triggers),
                            List.copyOf(materials),
                            Map.copyOf(levels),
                            List.copyOf(e.getStringList("prerequisites")),
                            List.copyOf(e.getStringList("incompatible")),
                            e.getBoolean("extractable", true),
                            e.getBoolean("discoverable", true)));
        }
        for (EnchantDefinition e : enchants.values()) {
            for (String requirement : e.requires()) {
                String[] pair = requirement.split(":", -1);
                if (!enchants.containsKey(pair[0]) || pair.length > 2)
                    throw new IllegalArgumentException("Unknown prerequisite: " + requirement);
                if (pair.length == 2
                        && !enchants.get(pair[0])
                                .levels()
                                .containsKey(Numbers.integer(pair[1], 1, 100)))
                    throw new IllegalArgumentException("Unknown prerequisite tier: " + requirement);
            }
            for (String conflict : e.conflicts())
                if (!enchants.containsKey(conflict))
                    throw new IllegalArgumentException("Unknown incompatible enchantment: " + conflict);
        }
        Map<String, Settings.Equipment> sets = equipment(gear, "sets", enchants);
        Map<String, Settings.Equipment> weapons = equipment(gear, "weapons", enchants);
        if (sets.size() > 45)
            throw new IllegalArgumentException(
                    "At most 45 armor sets can be shown in the preview menu.");
        for (Settings.Equipment weapon : weapons.values())
            if (!weapon.requiredSet().isEmpty() && !sets.containsKey(weapon.requiredSet()))
                throw new IllegalArgumentException("Unknown required set: " + weapon.requiredSet());
        Map<String, Settings.Kit> kits = new LinkedHashMap<>();
        for (String id : kitsFile.getKeys(false)) {
            identifier(id);
            ConfigurationSection k = section(kitsFile, id);
            kits.put(
                    id,
                    new Settings.Kit(
                            id,
                            k.getString("title", id),
                            bounded(k, "cooldown", 0, 31536000),
                            List.copyOf(k.getStringList("contents"))));
        }
        if (kits.size() > 45)
            throw new IllegalArgumentException("At most 45 kits can be shown in the kit menu.");
        for (Settings.Kit kit : kits.values())
            for (String entry : kit.items()) {
                String[] pair = entry.split(":", 3);
                if (pair.length < 2)
                    throw new IllegalArgumentException("Invalid kit entry: " + entry);
                boolean valid =
                        switch (pair[0]) {
                            case "set" -> sets.containsKey(pair[1]);
                            case "weapon" -> weapons.containsKey(pair[1]);
                            case "tome" -> groups.containsKey(pair[1]);
                            case "item" -> SpecialItems.TYPES.containsKey(pair[1]);
                            default -> false;
                        };
                if (!valid) throw new IllegalArgumentException("Unknown kit entry: " + entry);
                if (pair.length == 3) Numbers.integer(pair[2], 1, 1000000);
            }
        Map<String, String> messages = new HashMap<>();
        for (String key : messagesFile.getKeys(false))
            messages.put(key, messagesFile.getString(key, key));
        int slots = bounded(config, "general.base-slots", 1, 100);
        int maxSlots = bounded(config, "general.max-slots", slots, 100);
        List<List<String>> bookGroups = new ArrayList<>();
        List<Integer> minimumCounts = new ArrayList<>(),
                maximumCounts = new ArrayList<>(),
                prices = new ArrayList<>();
        for (String tier : List.of("tier-1", "tier-2", "tier-3")) {
            String basePath = "enchanting-table.tiers." + tier;
            List<String> selected = config.getStringList(basePath + ".rarities");
            if (selected.isEmpty()) selected = List.of("COMMON");
            if (!groups.keySet().containsAll(selected))
                throw new IllegalArgumentException("Unknown table tome rarity in " + tier);
            bookGroups.add(List.copyOf(selected));
            int minimum = config.getInt(basePath + ".min-enchants", 1);
            int maximum = config.getInt(basePath + ".max-enchants", 1);
            if (minimum < 1 || maximum > 10 || minimum > maximum)
                throw new IllegalArgumentException(
                        "Table enchantment count must be between 1 and 10.");
            minimumCounts.add(minimum);
            maximumCounts.add(maximum);
            int price = config.getInt(basePath + ".level-cost", 0);
            if (price < 0 || price > 1000)
                throw new IllegalArgumentException("Table level cost must be between 0 and 1000.");
            prices.add(price);
        }
        Settings.TableOptions table =
                new Settings.TableOptions(
                        config.getDouble("enchanting-table.tome-chance", 3),
                        List.copyOf(bookGroups),
                        List.copyOf(minimumCounts),
                        List.copyOf(maximumCounts),
                        List.copyOf(prices));
        if (!Double.isFinite(table.bookChance())
                || table.bookChance() < 0
                || table.bookChance() > 100)
            throw new IllegalArgumentException("Invalid table tome chance.");
        List<Settings.LootChoice> lootPool =
                pool(config.getStringList("chest-loot.entries"), groups, enchants);
        List<Settings.LootChoice> tradePool =
                pool(config.getStringList("villager-trades.entries"), groups, enchants);
        int maximumLoot = config.getInt("chest-loot.max-rewards", 2);
        if (maximumLoot < 1 || maximumLoot > 8)
            throw new IllegalArgumentException("Maximum chest loot rewards must be between 1 and 8.");
        return new Settings(
                Collections.unmodifiableMap(enchants),
                Collections.unmodifiableMap(groups),
                Map.copyOf(sets),
                Map.copyOf(weapons),
                Map.copyOf(kits),
                slots,
                maxSlots,
                config.getBoolean("general.break-on-failure", false),
                bounded(config, "limits.max-blocks", 1, 4096),
                bounded(config, "limits.max-targets", 1, 2048),
                bounded(config, "limits.max-delay-ticks", 1, 1200),
                Set.copyOf(config.getStringList("general.excluded-worlds")),
                percent(config, "enchanting-table.roll-chance"),
                percent(config, "chest-loot.roll-chance"),
                percent(config, "villager-trades.roll-chance"),
                bounded(config, "workstations.combiner-xp-cost", 0, 10000000),
                bounded(config, "workstations.recycler-xp-reward", 0, 10000000),
                bounded(config, "limits.pulse-interval-ticks", 10, 1200),
                Map.copyOf(messages),
                table,
                lootPool,
                tradePool,
                maximumLoot,
                Map.copyOf(presets));
    }

    private List<Settings.LootChoice> pool(
            List<String> configured,
            Map<String, Settings.Group> groups,
            Map<String, EnchantDefinition> enchants) {
        List<String> entries =
                configured.isEmpty()
                        ? groups.keySet().stream().map(id -> "tome:" + id + ":1").toList()
                        : configured;
        if (entries.size() > 64)
            throw new IllegalArgumentException("Loot pools may contain at most 64 entries.");
        List<Settings.LootChoice> choices = new ArrayList<>();
        for (String entry : entries) {
            String[] parts = entry.split(":", 5);
            if (parts.length < 3)
                throw new IllegalArgumentException("Invalid loot entry: " + entry);
            String kind = parts[0], id = parts[1];
            boolean valid =
                    kind.equals("tome")
                            ? groups.containsKey(id)
                                    && enchants.values().stream()
                                            .anyMatch(e -> e.group().equals(id) && e.obtainable())
                            : kind.equals("item") && SpecialItems.TYPES.containsKey(id);
            if (!valid) throw new IllegalArgumentException("Unknown loot entry: " + entry);
            int weight = Numbers.integer(parts[2], 1, 10000);
            int value = parts.length > 3 ? Numbers.integer(parts[3], 1, 1000000) : 1;
            String group = parts.length > 4 ? parts[4] : "";
            if (!group.isEmpty() && !groups.containsKey(group))
                throw new IllegalArgumentException("Unknown loot rarity: " + group);
            choices.add(new Settings.LootChoice(kind, id, weight, value, group));
        }
        return List.copyOf(choices);
    }

    private Map<String, Settings.Equipment> equipment(
            ConfigurationSection root, String key, Map<String, EnchantDefinition> enchants) {
        Map<String, Settings.Equipment> result = new LinkedHashMap<>();
        ConfigurationSection items = section(root, key);
        for (String id : items.getKeys(false)) {
            identifier(id);
            ConfigurationSection e = section(items, id);
            Map<String, Settings.Piece> pieces = new LinkedHashMap<>();
            ConfigurationSection ps = section(e, "pieces");
            for (String slot : ps.getKeys(false)) {
                ConfigurationSection p = section(ps, slot);
                String material = p.getString("material", "DIAMOND_SWORD");
                if (Material.matchMaterial(material) == null)
                    throw new IllegalArgumentException("Unknown material: " + material);
                List<String> pieceEnchants = List.copyOf(p.getStringList("enchants"));
                for (String entry : pieceEnchants) {
                    String[] pair = entry.split(":", 2);
                    if (pair.length != 2)
                        throw new IllegalArgumentException("Invalid piece enchant: " + entry);
                    String enchantId = pair[0].toLowerCase(Locale.ROOT);
                    if (!enchants.containsKey(enchantId)
                            && Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantId)) == null)
                        throw new IllegalArgumentException(
                                id + " references unknown enchantment: " + enchantId);
                }
                pieces.put(
                        slot,
                        new Settings.Piece(
                                material,
                                p.getString("title", id),
                                List.copyOf(p.getStringList("lore")),
                                p.getInt("model-data", 0),
                                pieceEnchants));
            }
            Map<String, EnchantDefinition.Level> events = new LinkedHashMap<>();
            ConfigurationSection es = e.getConfigurationSection("bonuses");
            if (es != null)
                for (String trigger : es.getKeys(false)) {
                    if (!EffectRegistry.TRIGGERS.contains(trigger))
                        throw new IllegalArgumentException("Unknown equipment event: " + trigger);
                    events.put(trigger, level(section(es, trigger)));
                }
            result.put(
                    id,
                    new Settings.Equipment(
                            id,
                            e.getString("title", id),
                            "",
                            e.getString("required-set", ""),
                            Map.copyOf(pieces),
                            Map.copyOf(events)));
        }
        return result;
    }

    private EnchantDefinition.Level level(ConfigurationSection section) {
        List<String> effects = section.getStringList("actions");
        if (effects.isEmpty() || effects.size() > 128)
            throw new IllegalArgumentException("Each tier requires 1..128 actions.");
        effects.forEach(EffectRegistry::validate);
        List<String> conditions = section.getStringList("rules");
        conditions.forEach(Conditions::validate);
        double cooldown = section.getDouble("delay", 0);
        if (!Double.isFinite(cooldown) || cooldown < 0 || cooldown > 86400)
            throw new IllegalArgumentException("Invalid delay.");
        return new EnchantDefinition.Level(
                percent(section, "rate"),
                cooldown,
                List.copyOf(conditions),
                List.copyOf(effects),
                Set.copyOf(section.getStringList("allow-blocks")),
                Set.copyOf(section.getStringList("deny-blocks")));
    }

    private YamlConfiguration file(String name) throws Exception {
        File file = new File(plugin.getDataFolder(), name);
        if (!file.exists()) plugin.saveResource(name, false);
        YamlConfiguration result = new YamlConfiguration();
        result.load(file);
        return result;
    }

    private static ConfigurationSection section(ConfigurationSection root, String path) {
        ConfigurationSection section = root.getConfigurationSection(path);
        if (section == null)
            throw new IllegalArgumentException("Missing configuration section: " + path);
        return section;
    }

    private static void identifier(String id) {
        if (!ID.matcher(id).matches())
            throw new IllegalArgumentException("Invalid identifier: " + id);
    }

    private static int bounded(ConfigurationSection config, String key, int min, int max) {
        int value = config.getInt(key);
        if (value < min || value > max)
            throw new IllegalArgumentException(
                    key + " must be between " + min + " and " + max + ".");
        return value;
    }

    private static double percent(ConfigurationSection config, String key) {
        double value = config.getDouble(key, 100);
        if (!Double.isFinite(value) || value < 0 || value > 100)
            throw new IllegalArgumentException(key + " must be between 0 and 100.");
        return value;
    }
}
