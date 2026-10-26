package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.util.Expression;
import io.github.lino9999.customenchants.util.Numbers;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiFunction;

public final class Variables {
    private final CustomEnchants plugin;
    private final Map<String, String> custom = new HashMap<>();
    private final Map<String, BiFunction<LivingEntity, EffectContext, String>> providers =
            new HashMap<>();
    private int lastRandom;
    private boolean placeholderWarning;

    public Variables(CustomEnchants plugin) {
        this.plugin = plugin;
    }

    public static void validate(String input) {
        TemplateFunctions.validate(input);
    }

    public void register(String name, BiFunction<LivingEntity, EffectContext, String> provider) {
        providers.put(name.toLowerCase(Locale.ROOT), Objects.requireNonNull(provider));
    }

    public void set(String name, String value, boolean overwrite) {
        if (!name.matches("[A-Za-z0-9_.-]{1,128}") || value.length() > 4096)
            throw new IllegalArgumentException("Invalid state variable name or value.");
        String key = name.toLowerCase(Locale.ROOT);
        if (!custom.containsKey(key) && custom.size() >= 10000)
            throw new IllegalArgumentException("Too many state variables.");
        if (overwrite) custom.put(key, value);
        else custom.putIfAbsent(key, value);
    }

    public void invert(String name) {
        String value = custom.getOrDefault(name.toLowerCase(Locale.ROOT), "false");
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false"))
            throw new IllegalArgumentException("Only boolean variables can be toggled.");
        set(name, Boolean.toString(!Boolean.parseBoolean(value)), true);
    }

    public Map<String, String> customValues() {
        return Map.copyOf(custom);
    }

    public String resolve(String input, EffectContext context) {
        String result = renderEffect(input, context);
        return result == null ? "" : result;
    }

    public String renderEffect(String input, EffectContext context) {
        return TemplateFunctions.render(
                input,
                new TemplateFunctions.Environment() {
                    @Override
                    public String variable(String name) {
                        return value(name, context);
                    }

                    @Override
                    public boolean active(String name) {
                        return switch (name) {
                            case "on_equip" ->
                                    context.permanent() && context.started() && !context.removal();
                            case "on_unequip" -> context.permanent() && context.removal();
                            default -> true;
                        };
                    }

                    @Override
                    public String function(String name, String body) {
                        return switch (name) {
                            case "rand" -> {
                                int result =
                                        TemplateFunctions.randomNumber(
                                                body, ThreadLocalRandom.current());
                                lastRandom = result;
                                context.values.put("random", result);
                                yield Integer.toString(result);
                            }
                            case "roll" -> Numbers.roll(Expression.evaluate(body)) ? "" : null;
                            case "when" ->
                                    new Conditions(Variables.this).inline(body, context)
                                            ? ""
                                            : null;
                            case "on_equip" ->
                                    context.permanent() && context.started() && !context.removal()
                                            ? body
                                            : "";
                            case "on_unequip" -> context.permanent() && context.removal() ? body : "";
                            default ->
                                    TemplateFunctions.textFunction(
                                            name, body, ThreadLocalRandom.current());
                        };
                    }
                });
    }

    private String value(String input, EffectContext c) {
        String name = input.toLowerCase(Locale.ROOT).trim();
        Object supplied = c.values.get(name);
        if (supplied != null) return supplied.toString();
        String alias = plugin.settings().conditionAliases().get(name);
        if (alias != null) return alias;
        if (name.startsWith("var.")) return custom.getOrDefault(name.substring(4), "0");
        if (name.equals("level")) return Integer.toString(c.level);
        if (name.equals("random")) return Integer.toString(lastRandom);
        if (name.equals("time.epoch_seconds"))
            return Long.toString(System.currentTimeMillis() / 1000);
        if (name.equals("time.millis")) return Long.toString(System.currentTimeMillis());
        if (name.equals("event.damage"))
            return c.event instanceof EntityDamageEvent event
                    ? Integer.toString((int) event.getFinalDamage())
                    : "0";
        if (name.equals("event.raw_damage"))
            return c.event instanceof EntityDamageEvent event
                    ? Integer.toString((int) event.getDamage())
                    : "0";
        if (name.equals("event.cause"))
            return c.event instanceof EntityDamageEvent event ? event.getCause().name() : "NONE";
        if (name.equals("event.xp")) {
            if (c.event instanceof BlockBreakEvent event)
                return Integer.toString(event.getExpToDrop());
            if (c.event instanceof EntityDeathEvent event)
                return Integer.toString(event.getDroppedExp());
            if (c.event instanceof PlayerFishEvent event)
                return Integer.toString(event.getExpToDrop());
            return "0";
        }
        if (name.equals("block.type")) return c.block == null ? "AIR" : c.block.getType().name();
        if (name.equals("block.seed_item")) return seed(c);
        if (name.equals("block.natural"))
            return Boolean.toString(c.block != null && !plugin.blocks().isPlaced(c.block));
        if (name.equals("block.interactable"))
            return Boolean.toString(c.block != null && c.block.getType().isInteractable());
        if (name.equals("block.tags")) return blockTags(c);
        if (name.equals("block.is_crop"))
            return Boolean.toString(c.block != null && c.block.getBlockData() instanceof Ageable);
        if (name.equals("block.is_mature"))
            return Boolean.toString(
                    c.block != null
                            && c.block.getBlockData() instanceof Ageable age
                            && age.getAge() == age.getMaximumAge());
        if (name.equals("item.max_durability"))
            return c.item == null ? "0" : Integer.toString(c.item.getType().getMaxDurability());
        if (name.equals("item.type")) return material(c.item);
        if (name.equals("item.slot")) return sourceSlot(c);
        if (name.equals("item.souls"))
            return Integer.toString(plugin.items().number(c.item, "souls"));
        if (name.equals("fish.caught_type"))
            return c.event instanceof PlayerFishEvent event && event.getCaught() != null
                    ? event.getCaught().getType().name()
                    : c.victim == null ? "UNKNOWN" : c.victim.getType().name();
        if (name.equals("mob.type") || name.equals("mob.hostile")) {
            LivingEntity mob =
                    c.victim != null && !(c.victim instanceof Player)
                            ? c.victim
                            : c.attacker != null && !(c.attacker instanceof Player)
                                    ? c.attacker
                                    : c.owner;
            return name.equals("mob.type")
                    ? mob.getType().name()
                    : Boolean.toString(mob instanceof Monster);
        }
        if (name.equals("event.trigger")) return c.trigger;
        if (name.equals("state.unequipping")) return Boolean.toString(c.removal());
        LivingEntity target = c.owner;
        String property = name;
        if (name.startsWith("attacker.")) {
            target = c.attacker == null ? c.owner : c.attacker;
            property = name.substring(9);
        } else if (name.startsWith("defender.")) {
            target = c.victim == null ? c.owner : c.victim;
            property = name.substring(9);
        } else if (name.startsWith("victim.")) {
            target = c.victim == null ? c.owner : c.victim;
            property = name.substring(7);
        } else if (name.startsWith("player.")) property = name.substring(7);
        BiFunction<LivingEntity, EffectContext, String> provider = providers.get(property);
        if (provider != null) return provider.apply(target, c);
        String dynamic = dynamic(property, target);
        if (dynamic != null) return dynamic;
        Player player = target instanceof Player p ? p : null;
        var location = target.getLocation();
        return switch (property) {
            case "name" -> target.getName();
            case "uuid" -> target.getUniqueId().toString();
            case "custom_name" -> target.getCustomName() == null ? "" : target.getCustomName();
            case "health" -> Double.toString(target.getHealth());
            case "max_health" -> Double.toString(maxHealth(target));
            case "health_pct" -> Double.toString(target.getHealth() * 100 / maxHealth(target));
            case "food" -> player == null ? "0" : Integer.toString(player.getFoodLevel());
            case "blocking" -> Boolean.toString(player != null && player.isBlocking());
            case "flying" -> Boolean.toString(player != null && player.isFlying());
            case "sneaking", "crouching" ->
                    Boolean.toString(
                            player != null
                                    ? player.isSneaking()
                                    : target.getPose().name().equals("SNEAKING"));
            case "sprinting" -> Boolean.toString(player != null && player.isSprinting());
            case "gliding" -> Boolean.toString(target.isGliding());
            case "riding" -> Boolean.toString(target.isInsideVehicle());
            case "passengers" ->
                    target.getPassengers().stream()
                            .map(entity -> entity.getType().name())
                            .toList()
                            .toString();
            case "burning" -> Boolean.toString(target.getFireTicks() > 0);
            case "submerged" ->
                    Boolean.toString(target.getEyeLocation().getBlock().isLiquid());
            case "night" ->
                    Boolean.toString(
                            target.getWorld().getTime() >= 13000
                                    && target.getWorld().getTime() <= 23000);
            case "hostile" -> Boolean.toString(target instanceof Monster);
            case "is_player" -> Boolean.toString(target instanceof Player);
            case "damageable" -> Boolean.toString(!target.isInvulnerable() && !target.isDead());
            case "critical" ->
                    Boolean.toString(
                            target.getFallDistance() > 0
                                    && !target.isOnGround()
                                    && !target.isInWater()
                                    && !target.isInsideVehicle());
            case "headshot" -> Boolean.toString(Boolean.TRUE.equals(c.values.get("headshot")));
            case "mainhand" -> material(item(target, EquipmentSlot.HAND));
            case "offhand" -> material(item(target, EquipmentSlot.OFF_HAND));
            case "helmet" -> material(item(target, EquipmentSlot.HEAD));
            case "chestplate" -> material(item(target, EquipmentSlot.CHEST));
            case "leggings" -> material(item(target, EquipmentSlot.LEGS));
            case "boots" -> material(item(target, EquipmentSlot.FEET));
            case "item_type" -> material(c.item);
            case "item_slot" -> sourceSlot(c);
            case "entity_type" -> target.getType().name();
            case "combo" -> Integer.toString(player == null ? 0 : plugin.engine().combo(player));
            case "world" -> target.getWorld().getName();
            case "world_time" -> Long.toString(target.getWorld().getTime());
            case "weather" -> target.getWorld().isClearWeather() ? "CLEAR" : "RAIN";
            case "x" -> Integer.toString(location.getBlockX());
            case "y" -> Integer.toString(location.getBlockY());
            case "z" -> Integer.toString(location.getBlockZ());
            case "x_exact" -> Double.toString(location.getX());
            case "y_exact" -> Double.toString(location.getY());
            case "z_exact" -> Double.toString(location.getZ());
            case "yaw" -> Float.toString(location.getYaw());
            case "pitch" -> Float.toString(location.getPitch());
            case "block_below" -> location.clone().subtract(0, 1, 0).getBlock().getType().name();
            case "nearby_mobs" ->
                    Long.toString(
                            target.getNearbyEntities(10, 10, 10).stream()
                                    .filter(LivingEntity.class::isInstance)
                                    .filter(entity -> !(entity instanceof Player))
                                    .filter(
                                            entity ->
                                                    entity.getLocation().distanceSquared(location)
                                                            <= 100)
                                    .count());
            case "nearby_players" ->
                    Long.toString(
                            target.getNearbyEntities(10, 10, 10).stream()
                                    .filter(Player.class::isInstance)
                                    .filter(
                                            entity ->
                                                    entity.getLocation().distanceSquared(location)
                                                            <= 100)
                                    .count());
            case "permissions" ->
                    player == null
                            ? ""
                            : player.getEffectivePermissions().stream()
                                    .filter(PermissionAttachmentInfo::getValue)
                                    .map(PermissionAttachmentInfo::getPermission)
                                    .sorted()
                                    .toList()
                                    .toString();
            case "is_op" -> Boolean.toString(player != null && player.isOp());
            case "inventory_full" ->
                    Boolean.toString(player != null && player.getInventory().firstEmpty() == -1);
            case "can_break" ->
                    Boolean.toString(
                            c.block != null
                                    && (!(c.event instanceof Cancellable event)
                                            || !event.isCancelled()));
            case "client_version" -> clientVersion(player);
            case "bleeding" -> Boolean.toString(plugin.states().bleeding(target));
            default -> placeholder(target, property, input);
        };
    }

    private String dynamic(String property, LivingEntity target) {
        if (property.startsWith("has_potion.")) {
            PotionEffectType type = LivingEffects.potion(property.substring("has_potion.".length()));
            return Boolean.toString(type != null && target.hasPotionEffect(type));
        }
        if (property.startsWith("potion_level.")) {
            PotionEffectType type =
                    LivingEffects.potion(property.substring("potion_level.".length()));
            PotionEffect effect = type == null ? null : target.getPotionEffect(type);
            return Integer.toString(effect == null ? 0 : effect.getAmplifier());
        }
        if (property.startsWith("hand_enchant_level."))
            return Integer.toString(
                    enchantLevel(
                            item(target, EquipmentSlot.HAND),
                            property.substring("hand_enchant_level.".length())));
        if (property.startsWith("has_hand_enchant."))
            return hasEnchant(
                    item(target, EquipmentSlot.HAND),
                    property.substring("has_hand_enchant.".length()));
        if (property.startsWith("has_slot_enchant.")) {
            String rest = property.substring("has_slot_enchant.".length());
            int dot = rest.indexOf('.');
            if (dot < 0) return "false";
            EquipmentSlot slot = slot(rest.substring(0, dot));
            return slot == null ? "false" : hasEnchant(item(target, slot), rest.substring(dot + 1));
        }
        return null;
    }

    private String hasEnchant(ItemStack item, String request) {
        String[] parts = request.split(":", 2);
        int actual = enchantLevel(item, parts[0]);
        return Boolean.toString(
                actual > 0 && (parts.length == 1 || actual == Integer.parseInt(parts[1])));
    }

    private int enchantLevel(ItemStack item, String id) {
        if (ItemData.empty(item)) return 0;
        Integer customLevel = plugin.items().enchants(item).get(id.toLowerCase(Locale.ROOT));
        if (customLevel != null) return customLevel;
        NamespacedKey key = NamespacedKey.fromString(id.toLowerCase(Locale.ROOT));
        var enchantment = key == null ? null : Registry.ENCHANTMENT.get(key);
        return enchantment == null ? 0 : item.getEnchantmentLevel(enchantment);
    }

    private String placeholder(LivingEntity target, String property, String original) {
        if (target instanceof Player player
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            try {
                Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                String value =
                        (String)
                                api.getMethod("setPlaceholders", OfflinePlayer.class, String.class)
                                        .invoke(null, player, "%" + property + "%");
                if (!value.equals("%" + property + "%")) return value;
            } catch (ReflectiveOperationException | LinkageError error) {
                if (!placeholderWarning) {
                    plugin.getLogger()
                            .warning(
                                    "PlaceholderAPI variables could not be resolved: "
                                            + error.getMessage());
                    placeholderWarning = true;
                }
            }
        }
        return "{" + original + "}";
    }

    private static String clientVersion(Player player) {
        if (player == null || !Bukkit.getPluginManager().isPluginEnabled("ViaVersion")) return "0";
        try {
            Object api =
                    Class.forName("com.viaversion.viaversion.api.Via")
                            .getMethod("getAPI")
                            .invoke(null);
            return Class.forName("com.viaversion.viaversion.api.ViaAPI")
                    .getMethod("getPlayerVersion", UUID.class)
                    .invoke(api, player.getUniqueId())
                    .toString();
        } catch (ReflectiveOperationException | LinkageError error) {
            return "0";
        }
    }

    private static ItemStack item(LivingEntity entity, EquipmentSlot slot) {
        return entity.getEquipment() == null ? null : entity.getEquipment().getItem(slot);
    }

    private static String material(ItemStack item) {
        return ItemData.empty(item) ? "AIR" : item.getType().name();
    }

    private static double maxHealth(LivingEntity entity) {
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        return attribute == null ? 20 : Math.max(0.001, attribute.getValue());
    }

    private static EquipmentSlot slot(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "hand", "mainhand" -> EquipmentSlot.HAND;
            case "offhand", "off_hand" -> EquipmentSlot.OFF_HAND;
            case "helmet", "head" -> EquipmentSlot.HEAD;
            case "chestplate", "chest" -> EquipmentSlot.CHEST;
            case "leggings", "legs" -> EquipmentSlot.LEGS;
            case "boots", "feet" -> EquipmentSlot.FEET;
            default -> null;
        };
    }

    private static String sourceSlot(EffectContext context) {
        for (String name :
                List.of("HAND", "OFFHAND", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) {
            ItemStack equipped = item(context.owner, slot(name));
            if (!ItemData.empty(equipped) && equipped.isSimilar(context.item)) return name;
        }
        return "HAND";
    }

    private static String seed(EffectContext context) {
        if (context.block == null) return "WHEAT_SEEDS";
        return switch (context.block.getType().name()) {
            case "CARROTS" -> "CARROT";
            case "POTATOES" -> "POTATO";
            case "BEETROOTS" -> "BEETROOT_SEEDS";
            case "COCOA" -> "COCOA_BEANS";
            case "NETHER_WART" -> "NETHER_WART";
            case "TORCHFLOWER_CROP" -> "TORCHFLOWER_SEEDS";
            case "PITCHER_CROP" -> "PITCHER_POD";
            default -> "WHEAT_SEEDS";
        };
    }

    private static String blockTags(EffectContext context) {
        if (context.block == null) return "[]";
        List<String> names = new ArrayList<>();
        for (var tag : Bukkit.getTags("blocks", Material.class))
            if (tag.isTagged(context.block.getType()))
                names.add(tag.getKey().toString().replace(':', '@'));
        return names.toString();
    }
}
