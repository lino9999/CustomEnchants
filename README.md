<p align="center"><img src="assets/customenchants-icon.png" alt="CustomEnchants enchanted tome pixel art icon" width="128"></p>

# CustomEnchants

**Custom enchantments and equipment progression for Minecraft survival, PvP and RPG servers, by Lino.**

Turn ordinary equipment into progression: discover enchantment tomes, improve their binding rates, harvest souls, unlock armor set bonuses and claim timed loadout kits. CustomEnchants includes **182 configurable enchantments**, six rarity tiers, seven armor sets, five special weapons and an English interface.

[Download the JAR](https://github.com/lino9999/CustomEnchants/releases/latest) · [Configuration guide](docs/configuration.md) · [Enchantment catalog](docs/enchantments.md) · [Effect DSL reference](docs/effects.md) · [Verification](docs/verification.md) · [Report an issue](https://github.com/lino9999/CustomEnchants/issues)

## Features

- Combat abilities for swords, axes, bows, crossbows and tridents: soul siphon, critical strikes, poison, lightning, defensive stances and temporary guardians.
- Mining and farming abilities: seam breaking, lumber cleaving, cube and directional tunnel excavation, smelting, automatic drop magnetization, crop sowing and seed cycling.
- Fishing abilities: instant reeling, bonus catches, experience multipliers and hook effects.
- Equipment bonuses: flight, water and lava striding, dark sight, movement speed, vital surge and passive potion effects.
- Enchantment tomes with success and destruction rates, compatible material rules, prerequisites, incompatibilities and slot limits.
- Enchanter shop with XP, experience level or optional Vault money payments; recycler and combiner workstations.
- Aegis and eternal protection scrolls, unbinding scrolls, arcane and sealed dust, rate crystals, slot expanders, equipment cores, name tag scrolls, stat counters and soul crystals.
- Full armor set bonuses, weapons requiring matching sets, configurable loadout kits and persistent per-player kit cooldowns.
- Anvil tome application and equipment merging, grindstone removal, enchanting table tiers, optional chest loot and villager trades.
- Paginated enchantment browser, equipment previews, administrative tome browser and an activation rate editor.
- Cancellable integration events, optional WorldGuard checks, optional mcMMO XP rewards, bounded area effects and atomic reload validation.

The implementation uses Bukkit APIs and persistent item data (`NamespacedKey` under `customenchants`). A small capability-detected adapter handles Minecraft 26.x's separate hurt cooldown without any native compile-time dependency. There are no runtime downloads, remote license checks, marketplace connections or telemetry.

## Install

1. Download `CustomEnchants-1.0.0.jar` from [Releases](https://github.com/lino9999/CustomEnchants/releases).
2. Use Spigot or Paper for Minecraft **1.21.11 through 26.3**. Minecraft 1.21.11 uses Java 21; the 26.x server series uses Java 25.
3. Place the JAR in `plugins/` and restart the server.
4. Edit the files in `plugins/CustomEnchants/`, then run `/ce reload`.
5. Start with `/ce enchants` and `/ce shop`. Administrators can use `/ce admin` to obtain tomes.

WorldGuard, Vault, PlaceholderAPI, ViaVersion, LuckPerms and mcMMO are optional. Currency effects and `MONEY` shop rarities require a Vault economy provider. Permission effects require LuckPerms or a Vault permission provider. Skill XP effects require mcMMO. PlaceholderAPI can resolve additional variables, and ViaVersion supplies the client protocol variable. Normal XP purchases need no extra plugin. Folia is not supported.

## Commands and permissions

The main command is `/customenchants`. Aliases are `/ce` and `/cenchants`. Every command has the permission `customenchants.<subcommand>` shown below. Tab completion respects permissions.

| Command | Purpose | Default |
| --- | --- | --- |
| `/ce help` | List the commands you can use | Everyone |
| `/ce enchants` | Browse all enchantments | Everyone |
| `/ce info <enchantment>` | Show summary and equipment requirements | Everyone |
| `/ce shop` | Buy random enchantment tomes | Everyone |
| `/ce tinkerer` | Recycle tomes or enchanted equipment for XP | Everyone |
| `/ce alchemist` | Upgrade matching tomes or combine matching arcane dust | Everyone |
| `/ce sets` | Preview armor sets | Everyone |
| `/ce kits` | Browse and claim loadout kits | Everyone |
| `/ce kit <id>` | Claim a kit; also requires `customenchants.kit.<id>` | Everyone |
| `/ce rename <name>` | Rename held equipment using a name tag scroll | Everyone |
| `/ce withdraw <amount>` | Extract held item souls into a soul crystal | Everyone |
| `/ce refresh` | Refresh equipped passive effects | Everyone |
| `/ce inspect` | Inspect held item enchantments, slots and souls | Everyone |
| `/ce admin` | Obtain maximum tier tomes from the admin browser | Operator |
| `/ce editor <enchantment>` | Adjust per-tier activation rate in a menu | Operator |
| `/ce enchant <enchantment> <level>` | Apply an enchantment to held equipment | Operator |
| `/ce remove <enchantment>` | Remove an enchantment from held equipment | Operator |
| `/ce givebook <player> <enchantment> <level> [success] [destroy] [amount]` | Give specific tomes; default rates 100/0 | Operator |
| `/ce random <player> <rarity> [amount]` | Give random tomes from a rarity tier | Operator |
| `/ce giveitem <player> <type> [amount] [value] [rarity]` | Give special items | Operator |
| `/ce giveset <player> <id>` | Give all four pieces of an armor set | Operator |
| `/ce giveweapon <player> <id>` | Give a configured special weapon | Operator |
| `/ce givekit <player> <id>` | Give kit contents without its claim cooldown | Operator |
| `/ce resetkit <player> <id>` | Reset a player's kit cooldown | Operator |
| `/ce setsouls <amount>` | Set the soul balance of the held item | Operator |
| `/ce open <player> <menu>` | Open a menu for a player who has its permission | Operator |
| `/ce reload` | Validate and reload all configuration | Operator |
| `/ce effects` | List action operation identifiers | Operator |
| `/ce diagnostics` | Show local plugin, server and Java information | Operator |

Additional permissions:

| Permission | Purpose | Default |
| --- | --- | --- |
| `customenchants.apply` | Apply tomes and combine custom equipment in an anvil | Everyone |
| `customenchants.items` | Use special items | Everyone |
| `customenchants.kit.<id>` | Claim one particular kit | Grant explicitly |
| `customenchants.*` | Grant command and item permissions | Operator |
| `customenchants.bypass.cooldown` | Bypass enchantment delays | Operator |
| `customenchants.bypass.souls` | Bypass soul consumption | Operator |

Use lowercase enchantment, kit, equipment and item IDs. Rarity IDs are uppercase: `COMMON`, `UNCOMMON`, `RARE`, `EPIC`, `MYTHIC`, `CELESTIAL`. Menus accepted by `/ce open` are `shop`, `enchants`, `admin`, `tinkerer`, `alchemist`, `sets` and `kits`.

## Tomes, upgrades and equipment

Pick up a custom tome and left-click a single compatible equipment item in your player inventory. The tome is consumed after a valid attempt. Success binds the enchantment; failure can destroy equipment only when `general.break-on-failure` is enabled. An `aegis_scroll` prevents one failed-tome destruction attempt and is consumed on that failure.

Apply tomes in an anvil for three experience levels. Combine two matching equipment items in an anvil to merge their custom enchantments; equal tiers upgrade by one up to the configured maximum. A grindstone strips custom enchantments.

The combiner (`/ce alchemist`) merges two tomes with the same enchantment and tier, or two `arcane_dust` items with matching rarities. Select separate inventory slots and click **Confirm**. The recycler (`/ce tinkerer`) consumes one item from each selected slot and rewards configured XP. Full inventory purchases do not charge the player.

Armor sets activate only when all four marked pieces are worn. The included set IDs are `wayfarer`, `vanguard`, `wraith`, `warden`, `monarch`, `glacier` and `stormborn`. Weapon IDs are `wayfarer_cleaver`, `vanguard_greataxe`, `vanguard_longsword`, `wraith_reaver` and `monarch_saber`.

## Special items

| Item ID | Function |
| --- | --- |
| `aegis_scroll` | Protect equipment from one failed tome |
| `eternal_scroll` | Retain equipment after one death |
| `unbinding_scroll` | Extract a random extractable enchantment into a tome; value sets success rate |
| `arcane_dust` | Raise tome success and lower destruction chance by value |
| `sealed_dust` | Right-click for a chance of arcane dust |
| `inert_ash` | Right-click to discard inert ash |
| `rate_crystal` | Reroll tome success and destruction rates |
| `slot_expander` | Add value enchantment slots, up to the configured maximum |
| `armor_core`, `weapon_core`, `tool_core` | Add slots to the matching equipment category |
| `name_tag_scroll` | Consume with `/ce rename <name>` |
| `mining_counter`, `angling_counter`, `bounty_counter`, `slayer_counter` | Track the matching activity on equipment |
| `soul_harvester` | Collect souls from player kills and display soul count |
| `soul_crystal` | Imbue value souls onto equipment |
| `lore_sorter` | Refresh sorted custom enchantment lore |
| `mystery_tome` | Right-click to unseal a random tome from its rarity |

Example: `/ce giveitem Lino arcane_dust 1 10 MYTHIC` gives one arcane dust worth 10 percentage points for mythic tomes.

## Configuration and development

The editable configuration files are `config.yml`, `enchantments.yml`, `equipment.yml`, `kits.yml` and `messages.yml`. Settings are generated on first startup; plugin commands and aliases live in the packaged `plugin.yml` descriptor.

Read [configuration](docs/configuration.md), [architecture](docs/architecture.md), [effects](docs/effects.md) and [maintenance instructions](docs/agents.md). The [SpigotMC description](docs/spigot-description.bbcode) and [SpigotMC documentation](docs/spigot-documentation.bbcode) are ready to paste.

Build with JDK 21+ and Maven 3.9+:

```sh
mvn -B clean verify
```

This builds `target/CustomEnchants-1.0.0.jar`.
