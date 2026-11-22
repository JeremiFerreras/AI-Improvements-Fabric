# NeoForge 26.3 → Fabric 26.3 mapping

| NeoForge source feature | Fabric implementation | State |
|---|---|---|
| ModDevGradle | Fabric Loom | Replaced |
| `neoforge.mods.toml` | `fabric.mod.json` | Added |
| `@Mod` | `ModInitializer` | Added |
| NeoForge event bus / `@SubscribeEvent` | Explicit Fabric API event registration | Added |
| `EntityJoinLevelEvent` | `ServerEntityEvents.ALLOW_LOAD` | Wired; runtime timing needs validation |
| `ModConfigSpec` | Gson JSON config in Fabric config directory | Ported |
| Access Transformer | Fabric Class Tweaker file | Added; pending Loom validation |
| `ModifierSystem` | Active Fabric-side goal/filter pipeline | Ported |
| `ModifierLayer` / editor/filter nodes | Active Fabric-side implementation | Ported; iteration bounds tightened |
| `FixedLookControl` / `FastTrig` | Active Fabric-side implementation | Reused and wired |

## Internal access

The NeoForge Access Transformer exposed `GoalSelector.availableGoals`, `Mob.lookControl`, three `LookControl` fields and private nested Squid/Pufferfish goal classes.

The Fabric branch uses a `.classtweaker` file with the Minecraft 26.3 `official` namespace. Loom's `validateAccessWidener` task must verify the names and descriptors against the exact versioned game jar. Do not treat these entries as verified until the CI check passes.

## Lifecycle timing

Fabric's `ServerEntityEvents.ALLOW_LOAD` runs immediately before an entity is loaded into a `ServerLevel`; this is a closer candidate for NeoForge's join-level hook than the post-load `ENTITY_LOAD` callback. In-game regression checks are still required for newly spawned entities, disk-loaded entities and compatibility with other mods that add goals during entity load.

## Behavior preservation

The active port keeps the upstream goal/filter architecture. Two incorrect setting references found in the NeoForge source were corrected on the Fabric side: Pufferfish's puff goal is controlled by `remove_puff`, and Squid's flee goal by `remove_flee`. The preserved legacy snapshot remains unchanged for comparison.
