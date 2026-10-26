package io.github.lino9999.customenchants;

import io.github.lino9999.customenchants.api.EnchantAffectEvent;
import io.github.lino9999.customenchants.command.EnchantCommand;
import io.github.lino9999.customenchants.config.ConfigurationLoader;
import io.github.lino9999.customenchants.config.Settings;
import io.github.lino9999.customenchants.effect.Conditions;
import io.github.lino9999.customenchants.effect.EffectContext;
import io.github.lino9999.customenchants.effect.EffectRegistry;
import io.github.lino9999.customenchants.effect.EnchantEngine;
import io.github.lino9999.customenchants.effect.Variables;
import io.github.lino9999.customenchants.enchant.EnchantService;
import io.github.lino9999.customenchants.enchant.ItemData;
import io.github.lino9999.customenchants.feature.EntityStateService;
import io.github.lino9999.customenchants.feature.EquipmentService;
import io.github.lino9999.customenchants.feature.GuardService;
import io.github.lino9999.customenchants.feature.HealthService;
import io.github.lino9999.customenchants.feature.MenuService;
import io.github.lino9999.customenchants.feature.PassiveService;
import io.github.lino9999.customenchants.feature.ProtectionService;
import io.github.lino9999.customenchants.feature.SpawnService;
import io.github.lino9999.customenchants.feature.SpecialItems;
import io.github.lino9999.customenchants.listener.BlockService;
import io.github.lino9999.customenchants.listener.GameplayListener;
import io.github.lino9999.customenchants.listener.InventoryListener;
import io.github.lino9999.customenchants.listener.LootListener;
import io.github.lino9999.customenchants.util.Text;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CustomEnchants extends JavaPlugin {
    private Settings settings;
    private ItemData items;
    private EnchantService enchants;
    private EnchantEngine engine;
    private EffectRegistry effects;
    private EquipmentService equipment;
    private SpecialItems specialItems;
    private MenuService menus;
    private PassiveService passive;
    private EntityStateService states;
    private HealthService health;
    private SpawnService spawns;
    private BlockService blocks;
    private ProtectionService protection;
    private GameplayListener gameplay;
    private BukkitTask periodic;
    private final Map<UUID, Entity> managedEntities = new HashMap<>();
    private GuardService guards;
    private int pending;

    @Override
    public void onEnable() {
        try {
            settings = new ConfigurationLoader(this).load();
            items = new ItemData(this);
            enchants = new EnchantService(this, items);
            equipment = new EquipmentService(this);
            specialItems = new SpecialItems(this);
            menus = new MenuService(this);
            passive = new PassiveService(this);
            states = new EntityStateService(this);
            health = new HealthService(this);
            spawns = new SpawnService(this);
            guards = new GuardService(this);
            getServer().getPluginManager().registerEvents(guards, this);
            getServer().getPluginManager().registerEvents(spawns, this);
            blocks = new BlockService(this);
            protection = new ProtectionService(this);
            Variables variables = new Variables(this);
            Conditions conditions = new Conditions(variables);
            effects = new EffectRegistry(this, variables, conditions);
            engine = new EnchantEngine(this, conditions, effects);
            gameplay = new GameplayListener(this);
            getServer().getPluginManager().registerEvents(gameplay, this);
            getServer().getPluginManager().registerEvents(states, this);
            getServer().getPluginManager().registerEvents(health, this);
            getServer().getPluginManager().registerEvents(menus, this);
            getServer().getPluginManager().registerEvents(new InventoryListener(this), this);
            getServer().getPluginManager().registerEvents(new LootListener(this), this);
            EnchantCommand command = new EnchantCommand(this);
            Objects.requireNonNull(getCommand("customenchants")).setExecutor(command);
            Objects.requireNonNull(getCommand("customenchants")).setTabCompleter(command);
            schedule();
            getLogger()
                    .info(
                            "CustomEnchants by Lino enabled with "
                                    + settings.enchants().size()
                                    + " enchantments.");
        } catch (Exception error) {
            getLogger().severe("CustomEnchants could not start: " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (passive != null) for (Player player : Bukkit.getOnlinePlayers()) passive.clear(player);
        if (states != null) states.clear();
        for (Player player : Bukkit.getOnlinePlayers())
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuService.Menu)
                player.closeInventory();
        if (gameplay != null) gameplay.restoreKeptItems();
        managedEntities.values().forEach(Entity::remove);
        managedEntities.clear();
        if (guards != null) guards.clear();
        Bukkit.getScheduler().cancelTasks(this);
    }

    public void reloadSettings() throws Exception {
        Settings replacement = new ConfigurationLoader(this).load();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof MenuService.Menu)
                player.closeInventory();
            passive.clear(player);
        }
        settings = replacement;
        states.clear();
        engine.clear();
        schedule();
    }

    private void schedule() {
        if (periodic != null) periodic.cancel();
        periodic =
                Bukkit.getScheduler()
                        .runTaskTimer(
                                this,
                                () -> {
                                    for (Player player : Bukkit.getOnlinePlayers()) {
                                        passive.refresh(player);
                                        if (!player.isDead())
                                            engine.fire(
                                                    new EffectContext(
                                                            player,
                                                            player,
                                                            player,
                                                            player.getInventory()
                                                                    .getItemInMainHand(),
                                                            null,
                                                            null,
                                                            "PULSE"));
                                    }
                                    if (gameplay != null) gameplay.prune();
                                },
                                1,
                                settings.periodicTicks());
    }

    public void message(CommandSender target, String key) {
        target.sendMessage(
                Text.color(
                        settings.messages().getOrDefault("prefix", "&d[CustomEnchants] &r")
                                + settings.messages().getOrDefault(key, key)));
    }

    public boolean canAffect(Player owner, LivingEntity target) {
        if (owner == target) return true;
        if (target.isInvulnerable() || target.getWorld() != owner.getWorld()) return false;
        GuardService.Guard guard = guards.guards().get(target.getUniqueId());
        if (guard != null && guard.owner().equals(owner.getUniqueId())) return false;
        if (target instanceof Player player) {
            if (!owner.getWorld().getPVP()
                    || player.getGameMode() == GameMode.CREATIVE
                    || player.getGameMode() == GameMode.SPECTATOR) return false;
            var team = owner.getScoreboard().getEntryTeam(owner.getName());
            if (team != null && team.hasEntry(player.getName()) && !team.allowFriendlyFire())
                return false;
        }
        if (!protection.allowed(owner, target)) return false;
        EnchantAffectEvent event = new EnchantAffectEvent(owner, target);
        Bukkit.getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    public void trackEntity(Entity entity, long lifetime) {
        entity.setPersistent(false);
        managedEntities.put(entity.getUniqueId(), entity);
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            managedEntities.remove(entity.getUniqueId());
                            guards.remove(entity.getUniqueId());
                            entity.remove();
                        },
                        lifetime);
    }

    public void guard(Entity entity, Player owner, LivingEntity target, long lifetime) {
        guards.add(entity, owner, target, lifetime);
    }

    public Map<UUID, GuardService.Guard> guards() {
        return guards.guards();
    }

    public GuardService guardService() {
        return guards;
    }

    public int managedEntityCount() {
        return managedEntities.size();
    }

    public void forgetEntity(UUID id) {
        managedEntities.remove(id);
        guards.remove(id);
    }

    public boolean managed(Entity entity) {
        return managedEntities.containsKey(entity.getUniqueId());
    }

    public void later(Runnable action, long delay) {
        if (pending >= 4096) return;
        pending++;
        Bukkit.getScheduler()
                .runTaskLater(
                        this,
                        () -> {
                            pending--;
                            action.run();
                        },
                        delay);
    }

    public Settings settings() {
        return settings;
    }

    public ItemData items() {
        return items;
    }

    public EnchantService enchants() {
        return enchants;
    }

    public EnchantEngine engine() {
        return engine;
    }

    public EquipmentService equipment() {
        return equipment;
    }

    public SpecialItems specialItems() {
        return specialItems;
    }

    public MenuService menus() {
        return menus;
    }

    public PassiveService passive() {
        return passive;
    }

    public EntityStateService states() {
        return states;
    }

    public HealthService health() {
        return health;
    }

    public SpawnService spawns() {
        return spawns;
    }

    public BlockService blocks() {
        return blocks;
    }

    public EffectRegistry effects() {
        return effects;
    }
}
