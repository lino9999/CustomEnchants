# Configuration

CustomEnchants by Lino reads five editable YAML files from `plugins/CustomEnchants/`: `config.yml`, `enchantments.yml`, `equipment.yml`, `kits.yml` and `messages.yml`. Use spaces for indentation. `/ce reload` builds a complete replacement snapshot; syntax errors and unknown operations leave the active configuration unchanged.

## General settings (`config.yml`)

`config.yml` controls base and maximum enchantment slots, tome failure destruction, excluded worlds, area and delay limits, enchanting table rates, optional chest loot and villager trade pools, workstation XP costs/rewards, preset variables and rarity tiers.

```yaml
general:
  base-slots: 9
  max-slots: 20
  break-on-failure: false
  excluded-worlds: []

limits:
  max-blocks: 256
  max-targets: 256
  max-delay-ticks: 400
  pulse-interval-ticks: 20
```

`general.excluded-worlds` contains exact world names and disables enchantment activation there without removing item metadata. Both `chest-loot.roll-chance` and `villager-trades.roll-chance` default to zero. Their weighted `entries` use `tome:RARITY:weight` or `item:type:weight:value:RARITY`. `chest-loot.max-rewards` bounds the number of rewards per container to 1–8.

`enchanting-table.roll-chance` defaults to 45 percent. Its `tiers.tier-1`, `tier-2` and `tier-3` sections configure `min-enchants`, `max-enchants`, an optional offer `level-cost` and `rarities`. `tome-chance` defaults to three percent and replaces an enchanted vanilla book with a custom tome after the normal enchant transaction completes.

Each rarity under `rarities` has `label`, `color`, `cost` and an optional `currency` (`XP`, `LEVELS` or `MONEY`):

```yaml
rarities:
  COMMON:
    label: Common
    color: '&7'
    cost: 400
    currency: XP
```

`presets` in `config.yml` defines optional `{preset.name}` variables that can be referenced inside rule expressions and actions.

## Enchantments (`enchantments.yml`)

Each top-level key in `enchantments.yml` is a lowercase identifier (`[a-z0-9_]+`), with 1–100 as valid tier numbers under `tiers`. Titles are cosmetic; item identity uses persistent data.

```yaml
seraph_grace:
  title: Seraph Grace
  summary: Gradually mends your health after taking combat damage.
  rarity: EPIC
  events:
    - DEFEND_HIT
    - DEFEND_MOB
  applies-to:
    - CHESTPLATES
  extractable: true
  discoverable: true
  prerequisites: []
  incompatible: []
  tiers:
    1:
      rate: 15
      delay: 4
      rules: []
      actions:
        - restore_health | 2 -> self
```

- `prerequisites` accepts enchantment IDs or `id:min-tier`.
- `incompatible` contains conflicting enchantment IDs; conflict checks work in both directions.
- `extractable: false` prevents `unbinding_scroll` extraction.
- `discoverable: false` excludes an enchantment from random tome generation and enchanting tables.
- Optional `allow-blocks` and `deny-blocks` lists filter block-breaking targets at the enchantment or tier level.
- `applies-to` accepts Bukkit material names or category selectors: `SWORDS`, `PICKAXES`, `AXES`, `SHOVELS`, `HOES`, `HELMETS`, `CHESTPLATES`, `LEGGINGS`, `BOOTS`, `ARMOR`, `TOOLS`, `EDIBLE`, `ANY_EQUIPMENT`.
- `rate` is an activation percentage from 0 to 100. `delay` is a cooldown in seconds.

## Equipment and kits (`equipment.yml` and `kits.yml`)

`equipment.yml` contains `sets` and `weapons`. Each definition has `title`, `required-set`, `pieces` and `bonuses`:

```yaml
wayfarer:
  title: Wayfarer
  required-set: ''
  pieces:
    helmet:
      material: DIAMOND_HELMET
      title: '&dWayfarer Helmet'
      enchants:
        - protection:4
        - unbreaking:3
        - plate_mastery:1-4
  bonuses:
    FALL_HURT:
      rate: 100
      delay: 0
      actions:
        - cancel_event
```

`kits.yml` defines `title`, `cooldown` (in seconds) and a `contents` list (`set:id`, `weapon:id`, `tome:RARITY` or `item:type:value`):

```yaml
arcanist:
  title: Arcanist Kit
  cooldown: 3600
  contents:
    - tome:COMMON
    - item:aegis_scroll:1
    - item:arcane_dust:5
```

Grant `customenchants.kit.<id>` to allow claims. Cooldowns are stored in player persistent data and survive restarts.
