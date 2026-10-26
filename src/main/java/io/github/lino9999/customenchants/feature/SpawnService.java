package io.github.lino9999.customenchants.feature;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.effect.EffectContext;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.LingeringPotionSplashEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class SpawnService implements Listener {
    private record Blast(Player owner, Location origin, boolean terrain) {}

    private final CustomEnchants plugin;
    private final NamespacedKey ownerKey;
    private final NamespacedKey rainKey;
    private final NamespacedKey spawnKey;
    private final NamespacedKey arrowRainKey;
    private final Deque<Blast> blasts = new ArrayDeque<>();

    public SpawnService(CustomEnchants plugin) {
        this.plugin = plugin;
        ownerKey = new NamespacedKey(plugin, "effect_owner");
        rainKey = new NamespacedKey(plugin, "falling_damage");
        spawnKey = new NamespacedKey(plugin, "spawned_entity");
        arrowRainKey = new NamespacedKey(plugin, "rain_arrow");
    }

    public void own(Entity entity, Player owner) {
        entity.getPersistentDataContainer()
                .set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
    }

    public void entity(EffectContext context, Location location, String requested) {
        EntityType type = type(requested);
        if (!loaded(location)) return;
        location.getWorld()
                .spawn(
                        location,
                        type.getEntityClass(),
                        entity ->
                                entity.getPersistentDataContainer()
                                        .set(spawnKey, PersistentDataType.BYTE, (byte) 1));
    }

    public void projectile(EffectContext context, LivingEntity shooter, String requested) {
        EntityType type = type(requested);
        if (!Projectile.class.isAssignableFrom(type.getEntityClass()))
            throw new IllegalArgumentException("The entity type must be a projectile.");
        launch(context, shooter.getEyeLocation(), shooter, type, false);
    }

    public void fireball(EffectContext context, Location location, LivingEntity shooter) {
        launch(context, location, shooter, EntityType.FIREBALL, true);
    }

    public void lightning(EffectContext context, Location location, boolean real) {
        if (!loaded(location) || plugin.managedEntityCount() >= 128) return;
        LightningStrike bolt =
                real
                        ? location.getWorld().strikeLightning(location)
                        : location.getWorld().strikeLightningEffect(location);
        if (bolt.isValid()) {
            own(bolt, context.owner);
            plugin.trackEntity(bolt, 60);
        }
    }

    public void arrows(EffectContext context, Location location) {
        if (!loaded(location) || plugin.managedEntityCount() + 49 > 128) return;
        Location origin = location.getBlock().getLocation().add(0, 5, 0);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                Location point = origin.clone().add(x, 0, z);
                if (!loaded(point)) continue;
                Arrow arrow =
                        point.getWorld()
                                .spawnArrow(point, new org.bukkit.util.Vector(0, -10, 0), .2f, 0);
                if (!arrow.isValid()) continue;
                arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                arrow.getPersistentDataContainer()
                        .set(arrowRainKey, PersistentDataType.BYTE, (byte) 1);
                own(arrow, context.owner);
                plugin.trackEntity(arrow, 600);
            }
    }

    private void launch(
            EffectContext context,
            Location location,
            LivingEntity shooter,
            EntityType type,
            boolean harmlessTerrain) {
        if (!loaded(location) || plugin.managedEntityCount() >= 128) return;
        Entity spawned =
                location.getWorld()
                        .spawn(
                                location,
                                type.getEntityClass(),
                                entity -> {
                                    Projectile projectile = (Projectile) entity;
                                    projectile.setShooter(shooter);
                                    projectile.setVelocity(location.getDirection().multiply(1.5));
                                    if (projectile instanceof AbstractArrow arrow)
                                        arrow.setPickupStatus(
                                                AbstractArrow.PickupStatus.DISALLOWED);
                                    if (harmlessTerrain
                                            && projectile instanceof Fireball fireball) {
                                        fireball.setYield(0);
                                        fireball.setIsIncendiary(false);
                                    }
                                    own(projectile, context.owner);
                                });
        if (spawned.isValid()) plugin.trackEntity(spawned, 600);
    }

    public void rain(EffectContext context, Location location, Material material, double damage) {
        if (!material.isBlock() || material.isAir())
            throw new IllegalArgumentException("Falling blocks require a non-air block material.");
        amount(damage, 2048, "Falling-block damage");
        if (!loaded(location) || plugin.managedEntityCount() + 49 > 128) return;
        Location origin = location.getBlock().getLocation().add(0, 5, 0);
        for (int x = -3; x <= 3; x++)
            for (int z = -3; z <= 3; z++) {
                Location point = origin.clone().add(x, 0, z);
                if (!loaded(point)) continue;
                FallingBlock block =
                        point.getWorld().spawnFallingBlock(point, material.createBlockData());
                if (!block.isValid()) continue;
                block.setDropItem(false);
                block.setCancelDrop(false);
                block.setHurtEntities(false);
                block.getPersistentDataContainer().set(rainKey, PersistentDataType.DOUBLE, damage);
                own(block, context.owner);
                plugin.trackEntity(block, 400);
            }
    }

    public void explode(
            EffectContext context, Location location, double power, boolean fire, boolean terrain) {
        amount(power, 8, "Explosion power");
        if (!loaded(location)) return;
        if (blasts.size() >= 16)
            throw new IllegalArgumentException("Explosion recursion exceeds its limit.");
        blasts.push(new Blast(context.owner, location.clone(), terrain));
        try {
            plugin.engine()
                    .effectDamage(
                            () ->
                                    location.getWorld()
                                            .createExplosion(
                                                    location, (float) power, fire, terrain));
        } finally {
            blasts.pop();
        }
    }

    public void tnt(EffectContext context, Location location, double damage, double radius) {
        amount(damage, 2048, "TNT damage");
        amount(radius, 32, "TNT radius");
        if (!loaded(location)) return;
        int count = 0;
        for (Entity entity :
                location.getWorld().getNearbyEntities(location, radius, radius, radius)) {
            if (!(entity instanceof LivingEntity target)
                    || !plugin.canAffect(context.owner, target)) continue;
            if (count++ >= plugin.settings().maxTargets()) break;
            plugin.engine().effectDamage(() -> target.damage(damage, context.owner));
        }
        location.getWorld().playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 1, 1);
        location.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, location, 3, 0, 0, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void land(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock block)) return;
        Double damage = block.getPersistentDataContainer().get(rainKey, PersistentDataType.DOUBLE);
        if (damage == null) return;
        boolean allowed = !event.isCancelled();
        event.setCancelled(true);
        Player owner = owner(block);
        if (allowed && owner != null && damage > 0) {
            DamageSource source =
                    DamageSource.builder(DamageType.FALLING_BLOCK)
                            .withDirectEntity(block)
                            .withCausingEntity(owner)
                            .build();
            for (Entity nearby :
                    event.getBlock()
                            .getWorld()
                            .getNearbyEntities(event.getBlock().getLocation(), 1, 1, 1))
                if (nearby instanceof Player player && plugin.canAffect(owner, player))
                    plugin.engine().effectDamage(() -> player.damage(damage, source));
        }
        block.remove();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageEvent event) {
        Entity source = event.getDamageSource().getDirectEntity();
        if (source == null && event instanceof EntityDamageByEntityEvent byEntity)
            source = byEntity.getDamager();
        if (rainArrow(source)
                && (event.getEntity() instanceof ArmorStand
                        || event.getEntity() instanceof ItemFrame)) {
            event.setCancelled(true);
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        Player owner = null;
        if (source != null && plugin.managed(source)) {
            owner = owner(source);
            if (owner == null && marked(source)) {
                event.setCancelled(true);
                return;
            }
        }
        Blast blast = blasts.peek();
        if (owner == null
                && blast != null
                && (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION
                        || event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION))
            owner = blast.owner;
        if (owner != null && !plugin.canAffect(owner, target)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void hanging(HangingBreakByEntityEvent event) {
        if (rainArrow(event.getRemover())) event.setCancelled(true);
    }

    private boolean rainArrow(Entity entity) {
        return entity instanceof Arrow
                && entity.getPersistentDataContainer().has(arrowRainKey, PersistentDataType.BYTE);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void entityExplosion(EntityExplodeEvent event) {
        Player owner = owner(event.getEntity());
        if (owner == null && marked(event.getEntity())) {
            event.blockList().clear();
            return;
        }
        if (owner != null) filter(owner, event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void blockExplosion(BlockExplodeEvent event) {
        Blast blast = blasts.peek();
        if (blast != null) {
            if (blast.terrain) filter(blast.owner, event.blockList());
            else event.blockList().clear();
        }
    }

    private void filter(Player owner, List<Block> blocks) {
        int count = 0;
        for (var iterator = blocks.iterator(); iterator.hasNext(); ) {
            Block block = iterator.next();
            if (!loaded(block.getLocation()) || count++ >= plugin.settings().maxBlocks()) {
                iterator.remove();
                continue;
            }
            if (!plugin.blocks().allowBreak(owner, block)) iterator.remove();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void splash(PotionSplashEvent event) {
        Player owner = owner(event.getPotion());
        if (marked(event.getPotion()))
            for (LivingEntity target : new ArrayList<>(event.getAffectedEntities()))
                if (owner == null || !plugin.canAffect(owner, target))
                    event.setIntensity(target, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void lingering(LingeringPotionSplashEvent event) {
        Player owner = owner(event.getEntity());
        if (marked(event.getEntity()) && (owner == null || plugin.managedEntityCount() >= 128)) {
            event.setCancelled(true);
            return;
        }
        if (owner != null) {
            own(event.getAreaEffectCloud(), owner);
            plugin.trackEntity(event.getAreaEffectCloud(), 600);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void cloud(AreaEffectCloudApplyEvent event) {
        Player owner = owner(event.getEntity());
        if (marked(event.getEntity()))
            event.getAffectedEntities()
                    .removeIf(target -> owner == null || !plugin.canAffect(owner, target));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void combust(EntityCombustByEntityEvent event) {
        Player owner = owner(event.getCombuster());
        if (marked(event.getCombuster())
                && event.getEntity() instanceof LivingEntity target
                && (owner == null || !plugin.canAffect(owner, target))) event.setCancelled(true);
    }

    private Player owner(Entity entity) {
        String value = entity.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (value == null) return null;
        try {
            return Bukkit.getPlayer(UUID.fromString(value));
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private boolean marked(Entity entity) {
        return entity.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING);
    }

    private static EntityType type(String requested) {
        String name = requested.toLowerCase(Locale.ROOT);
        name =
                switch (name) {
                    case "firework" -> "firework_rocket";
                    case "thrown_exp_bottle" -> "experience_bottle";
                    case "fishing_hook" -> "fishing_bobber";
                    default -> name;
                };
        NamespacedKey key = NamespacedKey.fromString(name);
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || !type.isSpawnable() || type.getEntityClass() == null)
            throw new IllegalArgumentException("Unknown or unspawnable entity type: " + requested);
        return type;
    }

    private static void amount(double value, double maximum, String label) {
        if (!Double.isFinite(value) || value < 0 || value > maximum)
            throw new IllegalArgumentException(label + " must be between 0 and " + maximum + ".");
    }

    private static boolean loaded(Location location) {
        return location.getWorld() != null
                && location.getY() >= location.getWorld().getMinHeight()
                && location.getY() < location.getWorld().getMaxHeight()
                && location.getWorld()
                        .isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }
}
