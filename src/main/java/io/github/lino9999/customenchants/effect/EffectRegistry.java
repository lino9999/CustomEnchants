package io.github.lino9999.customenchants.effect;

import io.github.lino9999.customenchants.CustomEnchants;
import io.github.lino9999.customenchants.util.Expression;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class EffectRegistry {
    public static final Set<String> TRIGGERS =
            Set.of(
                    "MELEE_HIT",
                    "MOB_HIT",
                    "DEFEND_HIT",
                    "DEFEND_MOB",
                    "DEFEND_PROJECTILE",
                    "RANGED_HIT",
                    "RANGED_MOB_HIT",
                    "BLOCK_BREAK",
                    "ARMOR_PASSIVE",
                    "HAND_PASSIVE",
                    "PULSE",
                    "INTERACT_RIGHT",
                    "PLAYER_SLAY",
                    "MOB_SLAY",
                    "COMBAT_DEATH",
                    "NATURAL_DEATH",
                    "FALL_HURT",
                    "FIRE_HURT",
                    "BLAST_HURT",
                    "ROD_SNARE",
                    "FISH_CATCH",
                    "FISH_BITE",
                    "TOOL_BREAK",
                    "GLIDE_START",
                    "HORN_USE",
                    "HAND_SWING",
                    "SNEAK_TOGGLE",
                    "PLAYER_JOIN",
                    "JUMP_MOVE",
                    "LINE_CAST",
                    "ITEM_CONSUME",
                    "SHIELD_BLOCK",
                    "PLAYER_QUIT");
    public static final Set<String> OPERATIONS =
            Set.of(
                    "grant_potion",
                    "clear_potion",
                    "purge_potion",
                    "boost_damage",
                    "reduce_damage",
                    "double_damage",
                    "halve_damage",
                    "absorb_damage",
                    "inflict_damage",
                    "deal_damage",
                    "cancel_event",
                    "drain_health",
                    "restore_health",
                    "restore_hunger",
                    "ignite_target",
                    "extinguish_target",
                    "restore_air",
                    "propel_entity",
                    "push_away",
                    "draw_closer",
                    "blink_behind",
                    "enable_flight",
                    "walk_on_water",
                    "walk_on_lava",
                    "delay_ticks",
                    "send_message",
                    "emit_particles",
                    "crimson_burst",
                    "play_sound",
                    "broadcast_sound",
                    "strike_lightning",
                    "detonate_tnt",
                    "launch_fireball",
                    "volley_arrows",
                    "summon_guardian",
                    "execute_target",
                    "thorn_burst",
                    "break_block",
                    "fell_tree",
                    "place_block",
                    "sow_seeds",
                    "magnetize_drops",
                    "smelt_drops",
                    "set_xp_drop",
                    "multiply_drops",
                    "drop_trophy_head",
                    "instant_reel",
                    "mend_held_item",
                    "mend_source_item",
                    "mend_armor",
                    "restore_durability",
                    "strip_random_armor",
                    "disarm_hand",
                    "mask_vision",
                    "strip_enchantment",
                    "retain_on_death",
                    "prevent_death",
                    "anchor_position",
                    "scramble_hotbar",
                    "harvest_souls",
                    "consume_souls",
                    "suppress_enchant",
                    "reset_combo",
                    "send_actionbar",
                    "announce_all",
                    "announce_perm",
                    "show_title",
                    "show_subtitle",
                    "run_console_cmd",
                    "run_player_cmd",
                    "open_workbench",
                    "open_enderchest",
                    "set_state",
                    "toggle_state",
                    "set_air_supply",
                    "grant_item",
                    "spawn_item",
                    "drop_hand_item",
                    "destroy_item",
                    "consume_item",
                    "imbue_enchant",
                    "siphon_xp",
                    "deposit_currency",
                    "withdraw_currency",
                    "siphon_currency",
                    "toggle_permission",
                    "warp_to",
                    "set_walk_speed",
                    "modify_walk_speed",
                    "set_fly_speed",
                    "grant_invulnerability",
                    "snare_movement",
                    "lock_item_use",
                    "brace_knockback",
                    "traverse_webs",
                    "frost_overlay",
                    "cancel_interaction",
                    "inflict_bleed",
                    "force_potion",
                    "preserve_armor",
                    "unequip_armor_slot",
                    "play_entity_animation",
                    "set_min_bite_ticks",
                    "set_max_bite_ticks",
                    "reduce_health",
                    "damage_health",
                    "reduce_health_totem",
                    "damage_health_totem",
                    "pierce_armor",
                    "trigger_totem_buffs",
                    "launch_firework",
                    "trace_particle_beam",
                    "summon_mob",
                    "fire_projectile",
                    "create_explosion",
                    "rain_falling_blocks",
                    "grant_mcmmo_xp",
                    "convert_guardians");
    private final CustomEnchants plugin;
    private final Variables variables;
    private final Map<String, Action> actions = new HashMap<>();
    private final Map<String, LocationAction> locationActions = new HashMap<>();
    private final TargetResolver targetResolver;

    @FunctionalInterface
    public interface Action {
        void run(EffectContext context, LivingEntity target, String[] arguments);
    }

    @FunctionalInterface
    public interface LocationAction {
        void run(EffectContext context, Location target, String[] arguments);
    }

    public EffectRegistry(CustomEnchants plugin, Variables variables, Conditions conditions) {
        this.plugin = plugin;
        this.variables = variables;
        targetResolver = new TargetResolver(plugin);
        new DamageEffects(plugin).register(this);
        new LivingEffects(plugin).register(this);
        new ItemEffects(plugin).register(this);
        new WorldEffects(plugin).register(this);
        new UtilityEffects(plugin).register(this);
        new StateEffects(plugin).register(this);
        new VisualEffects(plugin).register(this);
        new SpawnEffects(plugin).register(this);
        new IntegrationEffects().register(this);
        new GuardEffects(plugin).register(this);
    }

    public void register(String name, Action action) {
        if (actions.putIfAbsent(name, action) != null)
            throw new IllegalArgumentException("Duplicate action: " + name);
    }

    public void registerLocation(String name, LocationAction action) {
        if (locationActions.putIfAbsent(name, action) != null)
            throw new IllegalArgumentException("Duplicate location action: " + name);
    }

    public static void validate(String effect) {
        if (effect.length() > 4096) throw new IllegalArgumentException("Action is too long.");
        Variables.validate(effect);
        String inspected =
                TemplateFunctions.render(
                        effect,
                        new TemplateFunctions.Environment() {
                            @Override
                            public String variable(String name) {
                                return "1";
                            }

                            @Override
                            public String function(String name, String body) {
                                return switch (name) {
                                    case "roll", "when" -> "";
                                    case "rand", "calc", "round", "floor" -> "1";
                                    default -> body;
                                };
                            }
                        });
        if (inspected == null || inspected.isBlank())
            throw new IllegalArgumentException("Action cannot be empty.");
        String[] targetSplit = splitTarget(inspected);
        String commandPart = targetSplit[0];
        String name = commandPart.split("\\|", 2)[0].trim();
        if (!OPERATIONS.contains(name))
            throw new IllegalArgumentException("Unknown action: " + name);
        if (!targetSplit[1].isEmpty()) TargetSelector.parse(targetSplit[1]);
    }

    private static String[] splitTarget(String line) {
        int arrow = line.lastIndexOf(" -> ");
        if (arrow < 0) return new String[] {line.trim(), ""};
        String scope = line.substring(arrow + 4).trim();
        return new String[] {line.substring(0, arrow).trim(), scope};
    }

    public void execute(List<String> effects, EffectContext context) {
        try {
            execute(effects, 0, context, 0);
        } finally {
            if (!plugin.engine().collecting()) plugin.blocks().flush(context);
        }
    }

    private void execute(List<String> effects, int start, EffectContext context, int elapsed) {
        if (context.block != null
                && !context.block
                        .getWorld()
                        .isChunkLoaded(context.block.getX() >> 4, context.block.getZ() >> 4))
            return;
        for (int i = start; i < effects.size(); i++) {
            if (!context.removal()
                    && (!context.owner.isOnline()
                            || !plugin.settings().enabled(context.owner.getWorld().getName())))
                return;
            String raw = effects.get(i);
            if (raw.startsWith("summon_guardian"))
                raw = raw.replace("{player.name}", "{guard_owner}");
            String rendered = variables.renderEffect(raw, context);
            if (rendered == null || rendered.isBlank()) continue;
            String[] targetSplit = splitTarget(rendered);
            String line = targetSplit[0];
            String selector = targetSplit[1].isEmpty() ? "self" : targetSplit[1];
            String[] parts =
                    Arrays.stream(line.split("\\|", -1))
                            .map(String::trim)
                            .toArray(String[]::new);
            String operation = parts[0];
            if (operation.equals("delay_ticks")) {
                int ticks = (int) Math.clamp(number(parts, 1, 1), 1, plugin.settings().maxDelay());
                if (elapsed + ticks > plugin.settings().maxDelay()) return;
                int next = i + 1;
                EffectContext delayed = context.delayed();
                plugin.later(
                        () -> {
                            Runnable continuation =
                                    () -> {
                                        try {
                                            execute(effects, next, delayed, elapsed + ticks);
                                        } finally {
                                            if (!plugin.engine().collecting())
                                                plugin.blocks().flush(delayed);
                                        }
                                    };
                            if (delayed.permanent() && !delayed.removal())
                                plugin.engine().continuePermanent(delayed, continuation);
                            else continuation.run();
                        },
                        ticks);
                return;
            }
            context.values.put("selector", selector);
            Action action = actions.get(operation);
            LocationAction locationAction = locationActions.get(operation);
            if (action == null && locationAction == null)
                throw new IllegalArgumentException("Unregistered action: " + operation);
            String[] args = Arrays.copyOfRange(parts, 1, parts.length);
            for (EffectTarget selected : targetResolver.resolve(selector, context)) {
                LivingEntity entity = selected.entity();
                if (entity == null) {
                    if (locationAction != null)
                        locationAction.run(context, selected.position(), args);
                    continue;
                }
                if (!context.removal()
                        && (!entity.isValid() || entity.isDead())
                        && !(context.trigger.contains("DEATH")
                                || context.trigger.endsWith("_SLAY"))) continue;
                if (action != null) action.run(context, entity, args);
            }
        }
    }

    public static double number(String[] args, int index, double fallback) {
        return index >= args.length || args[index].isBlank()
                ? fallback
                : Expression.evaluate(args[index]);
    }

    public static String argument(String[] args, int index, String fallback) {
        return index < args.length && !args[index].isBlank() ? args[index] : fallback;
    }

    public Variables variables() {
        return variables;
    }

    public TargetResolver targetResolver() {
        return targetResolver;
    }
}
