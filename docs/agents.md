# Maintenance map

CustomEnchants is an original Bukkit API enchantment and equipment progression plugin by Lino. Java 21 bytecode, Spigot 1.21.11 API baseline; server Java 25 on 26.x.

## Read selectively

- `config`: immutable validated definitions and reload transaction.
- `enchant`: item rules, PDC storage and tome application.
- `effect`: bounded expression parser, rule conditions, event triggers and action registry. `VisualEffects` owns firework metadata/protection and particle-beam sampling.
- `listener`: event routing and protected block operations.
- `feature`: menus, special items, kits, sets and loot. `HealthService` owns direct-health/totem handling; `SpawnService` owns world attacks and protection; `GuardService` owns guardian relationships; `SkillIntegrationService` isolates optional mcMMO skill XP. `EntityStateService` owns scoped entity changes; `PassiveService` reconciles flight/walkers; `EnchantEngine` releases permanent scopes.
- `command`: `/customenchants` and `/ce` parsing and permissions.
- `api`: public cancellable events and extension points.
- `src/main/resources`: English configuration, enchantment catalog, equipment sets/weapons and kits.

Use rg on the relevant folder. Do not scan local untracked directories or `target`.

## Invariants

No source comments. All code identifiers, documentation, logs and player messages are English. Plugin author is Lino. Commands and permissions use `customenchants`. Item identity comes from PDC, never display names or lore. Modify Bukkit objects on the server thread. Respect cancelled events, dispatch cancellable events for extra blocks and damage, bound recursion/area/delays, never force chunk loads. Validate an entire new configuration before replacing the active snapshot. Failed reloads keep old definitions. Inventory purchases and item consumption must be atomic and reject full inventories before charging. Keep cooldown state bounded and remove players on quit. Use public Bukkit APIs in gameplay logic and avoid version strings. The isolated `HurtCooldown` adapter is the documented exception: the 26.x server split `damageCooldownTime` from `invulnerableTime` while the Bukkit setter still addresses the latter. It detects that field by capability and falls back to the public API on older servers.

## Build and verify

Run `mvn -B clean verify` with JDK 21+. Build against the lowest supported API. Check alternate API versions using `-Dbukkit.version=26.3-R0.1-SNAPSHOT` and JDK 25. Record verification results in `docs/verification.md`.

## Extend

Add an action operation in the effect registry with explicit argument validation and a documented capability. Add configuration examples only after every referenced event, action and rule condition is supported. Fail configuration loads for unknown operations. Maintain README commands/permissions and Spigot BBCode when behavior changes.
