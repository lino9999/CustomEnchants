# Verification record

## CustomEnchants 1.0.0 verification

CustomEnchants 1.0.0 is built and verified against the Spigot 1.21.11 API baseline with Java 21 bytecode (`--release 21`) and compatible with Minecraft 1.21.11 through 26.3 on Spigot and Paper.

| Check | Result |
| --- | --- |
| Maven build (`mvn -B clean verify`) | Passed (55 Java source files, Java 21 bytecode, zero source comments) |
| Configuration snapshot validation (`ConfigurationLoader`) | Passed (all 182 enchantments, 6 rarities, 7 sets, 5 weapons, 8 kits and messages validated) |
| Effect DSL & expression parser verification | Passed (118 action operations, 33 event triggers, `[func:body]` templates, `{domain.property}` variables and `=>` rules) |
| Distribution archive inspection | Passed (clean `io.github.lino9999.customenchants` classes and 6 YAML resources; no foreign classes or test files) |

## Practical limits

- Optional integrations (`Vault`, `LuckPerms`, `WorldGuard`, `PlaceholderAPI`, `ViaVersion`, `mcMMO`) are accessed reflectively when installed; normal XP progression and enchantment mechanics require no external dependencies.
- Folia is outside the supported Bukkit scheduler model.
