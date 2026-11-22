# Fabric 26.3 port status

**Checkpoint:** `fabric-experimental-0.4`  
**Overall status:** experimental / unstable. Source parity, Class Tweaker
validation, build packaging and server-side GameTests are now part of CI.
Runtime GameTests must pass before considering a stable release.

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
- Mod version: `0.5.5-fabric.0.4.0-unstable`

## Code reviewed and improved

- Compared the original ZIP's Java source with the preserved NeoForge tree
  and the active Fabric source.
- Confirmed `FastTrig.java` is byte-for-byte identical.
- Compared the exact entity-manager insertion hooks: NeoForge's
  `EntityJoinLevelEvent` and Fabric's `ServerEntityEvents.ALLOW_LOAD` both
  run at the head of `PersistentEntitySectionManager.addEntity`, before
  entity-manager insertion.
- Preserved the original entity subtype filters, goal types, and default
  config values.
- Corrected the two upstream option miswirings for pufferfish puff and
  squid flee; details are in `docs/BEHAVIOR_PARITY.md`.
- Improved adaptive modifier ordering and overflow normalization without
  changing which goal predicates are registered.
- Kept goal removal deferred until after selector iteration.
- Added server GameTests that spawn actual mobs and inspect their goal
  selectors after the entity insertion callback.
- The first runtime test exposed a bad assumption in the test itself: the
  vanilla 26.3 Cow did not contain the FloatGoal the test expected at tick 0.
  The suite has been changed to record the actual goal-selector inventory
  for Cow, Sheep, Pufferfish and Squid before defining assertions. This avoids
  importing outdated AI assumptions from older Minecraft versions.

## Automated server GameTests

The GameTest suite runs inside the Minecraft 26.3 server test environment
through Fabric Loom. The first run caught a wrong test assumption: vanilla
Cow in this Minecraft version did not contain the FloatGoal expected by the
initial assertion. That failure was in the test expectation, not in the
mod's goal-removal code.

The current diagnostic test records the real goal-selector contents and
LookControl class for Cow, Sheep, Pufferfish and Squid with all relevant
removal flags disabled. The next step is to build assertions only around
goals confirmed by this runtime inventory, then rerun the tests with each
option toggled independently.

The test temporarily changes in-memory settings and restores all touched
values in a finally block. It uses one GameTest method to avoid parallel
tests racing over the singleton configuration.

## CI verification

The workflow runs:

1. Static source parity checks.
2. Loom `validateAccessWidener`.
3. Minecraft server GameTests through the `build` task.
4. Java compilation and JAR packaging.
5. JAR metadata/class-content verification.
6. Uploads test reports on failure and JAR artifacts on success.

## Not yet proven in-game

- Natural spawning, spawn eggs and command spawning across all dimensions.
- Chunk unload/reload and disk-loaded entity behavior.
- Compatibility with third-party mods that add or replace goals.
- Runtime look rotation under edge angles and rotation limits.
- Performance under high entity counts.
- Client startup and dedicated-server startup with a normal production profile.

A passing GameTest suite is stronger than a compile-only check, but it is not
proof of complete behavior parity with every modded mob. Manual comparison
against NeoForge remains necessary before declaring the port stable.
