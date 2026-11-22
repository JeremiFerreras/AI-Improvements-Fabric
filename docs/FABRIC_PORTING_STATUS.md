# Fabric 26.3 port status

**Checkpoint:** `fabric-experimental-0.3`  
**Overall status:** experimental / unstable. The code passes a source-level
parity check and a Fabric build, but runtime behavior still requires testing.

## Base and target

- Source branch: `26.3`
- Base commit: `a51cab76acf89099ea3c41393d858f9e061dbe4b`
- Port branch: `port/fabric-26.3-bootstrap`
- Minecraft: `26.3` (unchanged)
- Java: `25`
- Fabric Loader: `0.20.1`
- Loom: `1.19-SNAPSHOT` (resolved by Gradle as 1.19.4)
- Fabric API: `0.166.2+26.3`
- Gradle: `9.8.2`
- Mod version: `0.5.5-fabric.0.3.0-unstable`

## Code reviewed and improved

- Compared the original ZIP's complete Java source against the active Fabric
  source and the preserved NeoForge source tree.
- Confirmed `FastTrig.java` is byte-for-byte identical.
- Confirmed goal-selection predicates and entity subtype filters remain
  represented in the Fabric `ModifierSystem`.
- Compared NeoForge's event patch with Fabric API's 26.3
  `PersistentEntitySectionManagerMixin`: both dispatch before entity-manager
  insertion. Fabric uses the server-side callback, which is appropriate for
  server-authoritative mob AI.
- Corrected the adaptive modifier counter so it increments only when
  `enableCallBubbling` is enabled.
- Corrected counter overflow handling: hot predicates sort first and counters
  are halved, preserving relative frequency instead of collapsing to zero.
- Kept goal removal deferred until after `GoalSelector.availableGoals` has
  been traversed.
- Preserved the original allowlist/denylist truth table and default settings.
- Kept the two explicit corrections for pufferfish puff and squid flee option
  wiring; these are documented in `docs/BEHAVIOR_PARITY.md`.

## CI verification

The workflow runs:

1. `python tools/verify_port_parity.py`
2. `./gradlew --no-daemon validateAccessWidener build`
3. JAR metadata/class-content verification using the same parity script.

A successful build confirms source compatibility and package contents. It
does not confirm in-game behavior.

## Not yet proven in-game

- Mob spawning through natural spawners, spawn eggs and commands.
- Entity reload from disk and chunk unload/reload cycles.
- Goal changes made by other mods before or after entity insertion.
- Client view versus dedicated-server authoritative AI.
- Animal, fish, pufferfish, squid and sheep behavior with every removal option
  enabled individually.
- Look controller parity under rotation limits, target changes and edge angles.
- Performance under a large number of mobs.

## Next validation phase

1. Launch a dedicated Minecraft 26.3 Fabric server with this JAR.
2. Run controlled spawn/reload tests for each supported entity family.
3. Compare goal-selector contents against the NeoForge 26.3 build with the
   same options and entity types.
4. Profile entity insertion cost and high-mob-count tick behavior.
5. Only then consider a stable version.
