# Fabric 26.3 port status

**Checkpoint:** `fabric-experimental-0.2`  
**Overall status:** experimental / unstable. The first feature-bearing port is wired, but it is not a release candidate.

## Base and target

- Source branch: `26.3`
- Base commit: `a51cab76acf89099ea3c41393d858f9e061dbe4b`
- Port branch: `port/fabric-26.3-bootstrap`
- Minecraft: `26.3` (unchanged)
- Java: `25`
- Fabric Loader: `0.20.1`
- Loom: `1.19-SNAPSHOT` (resolved by Gradle as 1.19.4 during the first CI run)
- Fabric API: `0.166.2+26.3`
- Gradle: `9.8.2`
- Mod version: `0.5.5-fabric.0.2.0-unstable`

## Implemented in this checkpoint

- Fabric Loom build, metadata and `ModInitializer` entrypoint.
- JSON configuration in `config/aiimprovements.json`, with the original settings and defaults represented.
- Original filter/editor pipeline copied into the active Fabric source set and adapted to Fabric-side configuration types.
- Goal removal pipeline and entity-specific rules for generic mobs, fish, pufferfish, squid, cows, chickens, pigs and sheep.
- Vanilla look-controller replacement using the existing `FixedLookControl` and `FastTrig`.
- Class Tweaker file added for `GoalSelector.availableGoals`, `Mob.lookControl`, the mutable look-control state fields and nested squid/pufferfish goal classes.
- Fabric lifecycle bridge uses `ServerEntityEvents.ALLOW_LOAD`, which executes immediately before entity insertion into `ServerLevel`.
- Fixed two option/goal mapping mistakes in the active Fabric implementation: pufferfish puff uses `remove_puff`; squid flee uses `remove_flee`.
- Original NeoForge sources, Access Transformer and TOML metadata preserved under `src/legacy-neoforge/`.

## Known limitations / unstable behavior

- The Class Tweaker targets are not verified until Loom's `validateAccessWidener` passes against the exact Minecraft 26.3 game jar.
- No client or dedicated-server launch test has completed.
- Entity-load timing must be tested for newly spawned entities, disk-loaded entities and entities added by other mods.
- No profiling has confirmed that goal/filter call-bubbling retains the original performance characteristics.
- Goal replacement is still unsupported, matching the upstream TODO.
- JSON configuration is read at startup; there is no GUI or live reload.
- There are no regression tests yet for every allowlist/denylist branch and entity-specific goal.

## CI history

The bootstrap build initially exposed Gradle configuration-cache issues in resource expansion and JAR license renaming. Both were addressed by capturing task inputs before task execution. CI now explicitly runs `validateAccessWidener` followed by `build`. The current revision's result must be checked in GitHub Actions before treating it as build-verified.

## Next work

1. Validate and correct any Class Tweaker names/descriptors reported by Loom.
2. Fix compile/API mismatches against Minecraft 26.3 named classes.
3. Test lifecycle timing and confirm entities are processed once at the correct point.
4. Test JSON read/write, allowlist/denylist logic and all entity-specific goal settings.
5. Launch client and dedicated server and profile high-entity-count scenes.
