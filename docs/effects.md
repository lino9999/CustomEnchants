# Effect DSL reference

CustomEnchants uses a clean, structured domain-specific language (DSL) for enchantment `events`, `rules`, `actions`, target scopes and inline template functions. Every operation and selector is validated when loading `enchantments.yml` and `equipment.yml`.

## Action syntax

Each entry in a tier's `actions` list follows the pipe-and-arrow format:

```text
operation | arg1 | arg2 -> scope[key=value,key2=value2]
```

- Arguments are separated by `|`. Whitespace around `|` and `->` is trimmed automatically.
- If `-> scope` is omitted, the target defaults to `self` (or `hit_block` for block operations during `BLOCK_BREAK`).

## Event triggers

| Event | Activation |
| --- | --- |
| `MELEE_HIT`, `MOB_HIT` | Melee hit against a player or mob |
| `RANGED_HIT`, `RANGED_MOB_HIT` | Ranged projectile hit against a player or mob |
| `DEFEND_HIT`, `DEFEND_MOB`, `DEFEND_PROJECTILE` | Incoming player, mob or projectile damage |
| `PLAYER_SLAY`, `MOB_SLAY` | Credited player or mob kill |
| `COMBAT_DEATH`, `NATURAL_DEATH` | Death with or without a player killer |
| `BLOCK_BREAK` | Survival block break |
| `ARMOR_PASSIVE`, `HAND_PASSIVE` | Equipped armor or held item passive lifecycle |
| `PULSE` | Periodic pulse for equipped/held items |
| `INTERACT_RIGHT`, `HORN_USE` | Right-click interaction or goat horn use |
| `FALL_HURT`, `FIRE_HURT`, `BLAST_HURT` | Environmental fall, fire or explosion damage |
| `ROD_SNARE`, `FISH_CATCH`, `FISH_BITE`, `LINE_CAST` | Fishing rod events |
| `TOOL_BREAK` | Durability hit that would break the item |
| `GLIDE_START` | Starting elytra glide |
| `HAND_SWING`, `SNEAK_TOGGLE`, `JUMP_MOVE` | Arm swing, crouch toggle or ground jump |
| `PLAYER_JOIN`, `PLAYER_QUIT`, `ITEM_CONSUME`, `SHIELD_BLOCK` | Login, logout, item consumption or shield block |

## Target scopes

| Scope | Description |
| --- | --- |
| `self` | The enchantment owner |
| `attacker`, `defender` | Combat participants |
| `all_players` | All online players |
| `player_by_name[filter=Name]` | Specific online player by name |
| `nearest_player[range=10]` | Closest other player within `range` |
| `area[range=4,filter=mobs,max=20]` | Nearby entities within `range` (`filter` accepts `all`, `mobs`, `players`, `living`) |
| `sight_entities[range=20,cone=40,max=5]` | Entities in line of sight within view cone |
| `hit_block` | The event block |
| `eye_pos` | The owner's eye location |
| `offset[x=2,y=0,z=-1]` | Location offset from the owner |
| `sight_block[range=5]` | Block along the owner's line of sight |
| `cube_area[size=3,any_tool=true]` | Cube or oriented volume (`bounds=3x3x5`) around the mined block |
| `tunnel_area[bounds=1x3x5,direction=STRAIGHT]` | Directional mining tunnel (`STRAIGHT`, `UP`, `DOWN`, `AUTO`) |
| `vein_area[max_blocks=75]` | Connected matching ore blocks (requires `allow-blocks` and excludes player-placed blocks) |

## Operations

| Operation | Description |
| --- | --- |
| `grant_potion \| TYPE \| amplifier \| ticks` | Grant a potion effect (0-based amplifier) |
| `clear_potion \| TYPE`, `purge_potion \| TYPE` | Remove a potion effect and its owned lease |
| `restore_health \| amount` | Heal the target up to maximum health |
| `restore_hunger \| amount`, `restore_air \| amount`, `set_air_supply \| amount` | Modify hunger or oxygen reserves |
| `ignite_target \| seconds`, `extinguish_target` | Set fire ticks or extinguish flames |
| `boost_damage \| percent`, `reduce_damage \| percent` | Multiply current event damage by percentage |
| `double_damage`, `halve_damage`, `absorb_damage \| amount` | Double, halve or subtract flat event damage |
| `inflict_damage \| amount`, `deal_damage \| amount` | Deal attributed combat damage |
| `drain_health \| amount` | Deal damage to the opponent and heal by the health removed |
| `cancel_event`, `cancel_interaction` | Cancel the current cancellable event |
| `prevent_death` | Cancel lethal damage and restore health |
| `execute_target` | Deal lethal attributed damage to an allowed target |
| `reduce_health \| amount`, `damage_health \| amount` | Subtract integer health ignoring armor mitigation |
| `reduce_health_totem \| amount`, `damage_health_totem \| amount` | Subtract health while allowing a held totem to save the player |
| `pierce_armor` | Strip armor/resistance mitigation from the current damage event |
| `trigger_totem_buffs [\| regen \| fireRes \| absorption]` | Trigger totem visuals and buffs |
| `propel_entity \| UP \| strength` | Apply velocity impulse |
| `push_away \| strength`, `draw_closer \| strength` | Move target relative to the opponent |
| `blink_behind` | Teleport behind the opponent |
| `enable_flight`, `walk_on_water`, `walk_on_lava` | Passive flight or fluid walking |
| `anchor_position`, `brace_knockback \| ticks` | Suppress knockback |
| `break_block`, `fell_tree` | Bounded, protected area block or tree breaking |
| `place_block \| MATERIAL` | Replace the target block after drop handling |
| `sow_seeds \| radius \| SEED_MATERIAL` | Plant seeds on nearby farmland |
| `magnetize_drops`, `smelt_drops`, `multiply_drops \| extra` | Modify block/mob/fishing drops |
| `set_xp_drop \| amount` | Override dropped XP |
| `instant_reel` | Award fishing loot immediately on bite |
| `drop_trophy_head` | Add a slain player's skull to drops |
| `mend_held_item \| amount`, `mend_source_item \| amount`, `mend_armor \| amount`, `restore_durability` | Repair or damage equipment durability |
| `strip_enchantment \| id`, `imbue_enchant \| id \| tier` | Remove or apply an enchantment on the source item |
| `strip_random_armor`, `unequip_armor_slot \| SLOT`, `disarm_hand` | Move equipped gear into inventory |
| `scramble_hotbar` | Shuffle a player's hotbar slots |
| `retain_on_death` | Keep the item across death (`COMBAT_DEATH` / `NATURAL_DEATH`) |
| `mask_vision \| ticks` | Display a temporary carved pumpkin overlay |
| `harvest_souls \| amount`, `consume_souls \| amount` | Add or spend item souls |
| `suppress_enchant \| id \| seconds`, `reset_combo` | Suppress an enchantment or reset combo counter |
| `summon_guardian \| ENTITY [\| seconds \| count \| name \| switchRoles]`, `convert_guardians` | Summon or convert combat guardians |
| `strike_lightning [\| real]`, `volley_arrows`, `detonate_tnt [\| damage \| radius]`, `launch_fireball` | World combat attacks |
| `create_explosion \| power \| fire \| breakBlocks`, `fire_projectile \| ENTITY`, `summon_mob \| ENTITY`, `rain_falling_blocks \| MATERIAL \| damage` | World spawning and explosions |
| `emit_particles \| TYPE \| count`, `crimson_burst`, `thorn_burst`, `trace_particle_beam \| TYPE \| amount \| points`, `launch_firework` | Visual particle and firework effects |
| `play_sound \| SOUND [\| volume \| pitch]`, `broadcast_sound \| SOUND [\| volume \| pitch]` | Audio effects |
| `send_message \| text`, `send_actionbar \| text`, `show_title \| text`, `show_subtitle \| text`, `announce_all \| text`, `announce_perm \| perm \| text` | Player and broadcast messaging |
| `delay_ticks \| ticks` | Delay subsequent actions |
| `run_console_cmd \| cmd`, `run_player_cmd \| cmd`, `open_workbench`, `open_enderchest` | Command and utility actions |
| `set_state \| name \| value [\| overwrite]`, `toggle_state \| name` | Custom runtime state variables (`{state.name}`) |
| `grant_item \| MATERIAL [\| amount]`, `spawn_item \| MATERIAL [\| amount]`, `drop_hand_item`, `destroy_item [\| amount]`, `consume_item \| MATERIAL [\| amount]` | Inventory and item actions |
| `warp_to` | Teleport owner to a location scope |
| `siphon_xp \| amount`, `deposit_currency \| amount`, `withdraw_currency \| amount`, `siphon_currency \| amount`, `toggle_permission \| perm`, `grant_mcmmo_xp \| skill \| amount` | Economy, XP, permission and mcMMO integrations |
| `set_walk_speed \| speed [\| ticks]`, `modify_walk_speed \| delta [\| ticks]`, `set_fly_speed \| speed [\| ticks]`, `grant_invulnerability`, `snare_movement \| ticks`, `frost_overlay \| ticks`, `lock_item_use \| MATERIAL \| ticks`, `traverse_webs`, `inflict_bleed \| damage \| count \| interval`, `force_potion \| TYPE \| amplifier [\| ticks]`, `preserve_armor`, `play_entity_animation \| EFFECT`, `set_min_bite_ticks \| ticks`, `set_max_bite_ticks \| ticks` | Scoped entity state operations |

## Variables, template functions and rule conditions

Variables use `{domain.property}` notation, such as `{enchant.tier}`, `{event.damage}`, `{event.xp}`, `{item.souls}`, `{item.max_durability}`, `{defender.health}`, `{player.health}`, `{attacker.combo}`, `{player.pitch}`, `{block.type}`, `{block.natural}`, `{block.is_crop}`, `{block.fully_grown}`, `{mob.type}` and `{mob.hostile}`.

Inline template functions use `[func:body]` syntax and may be nested:

- `[rand:min..max]` — random integer in inclusive range (e.g. `[rand:1..4]` or `[rand:-3..-1]`)
- `[pick:a|b|c]` — random choice from pipe-separated options
- `[roll:25]` — inline percentage roll gate
- `[calc:expression]` — decimal mathematical expression
- `[round:value]`, `[floor:value]` — rounding or truncation toward zero
- `[when:expression => outcome]` — inline rule condition
- `[on_equip:text]`, `[on_unequip:text]` — passive lifecycle branches
- `[title:text]`, `[shuffle:text]` — string formatting helpers

Rule conditions in `rules` use `=>`:

```yaml
rules:
  - '{defender.health} > 8 => cancel'
  - '{defender.sneaking} = true => rate +7'
actions:
  - restore_health | [rand:1..3] -> self
  - delay_ticks | 20
  - inflict_damage | [calc:{event.damage} * 0.25] -> attacker
```

Supported rule outcomes after `=>` are `allow`, `cancel`, `force`, `pass` and `rate +N` / `rate -N`.
