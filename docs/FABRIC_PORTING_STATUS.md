# Fabric 26.3 port status

**Checkpoint:** `fabric-experimental-0.5`  
**Overall status:** experimental / unstable. Source parity checks, Class Tweaker
validation, server GameTests, compilation and JAR inspection pass in CI.
Normal client startup, compatibility with third-party AI mods and performance
under load have not yet been proven.

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
- Mod version: `0.5.0-fabric-unstable`

## Code reviewed and improved

- Compared the full original NeoForge 26.3 configuration and modifier sources
  against the active Fabric implementation.
- Confirmed every original config category and setting is represented:
  global, generic mob filters, fish, squid, cow, chicken, pig, sheep and
  sheep's separate eat-block option.
- Confirmed `FastTrig.java` is byte-for-byte identical.
- Compared the exact entity-manager insertion hooks: NeoForge's
  `EntityJoinLevelEvent` and Fabric's `ServerEntityEvents.ALLOW_LOAD` both
  run at the head of `PersistentEntitySectionManager.addEntity`, before
  entity-manager insertion.
- Preserved the original entity subtype filters and goal predicate classes.
- Corrected the two upstream option miswirings for pufferfish puff and
  squid flee; details are in `docs/BEHAVIOR_PARITY.md`.
- Improved adaptive modifier ordering and overflow normalization without
  changing which goal predicates are registered.
- Kept goal removal deferred until after selector iteration.
- Preserved the allowlist/denylist truth table and default values.
- Added server GameTests using normal `helper.spawn` calls. The tests inspect
  live goal selectors immediately after entity insertion, before the first AI
  tick.

## Runtime vanilla goal baselines observed on Minecraft 26.3

With removal settings disabled, the GameTest server reported:

- Cow: 8 goals — Breed, Float, FollowParent, LookAtPlayer, Panic,
  RandomLookAround, Tempt and WaterAvoidingRandomStroll.
- Sheep: 9 goals — the cow set plus EatBlock.
- Pufferfish: 4 goals — AvoidEntity, Panic, FishSwim and PufferfishPuff.
- Squid: 2 goals — SquidFlee and SquidRandomMovement.

The initial diagnostic attempt used `spawnWithNoFreeWill`, which deliberately
disables AI and produced empty selectors. The test was corrected to use normal
AI-enabled spawns; those are the baselines recorded above.

## Automated server GameTests

The current server GameTest matrix verifies:

- Cow `remove_float` removes FloatGoal but preserves BreedGoal.
- `allow_remove_calls=false` prevents the same removal.
- Chicken `remove_breed` removes BreedGoal.
- Pig `remove_tempt` removes TemptGoal but preserves BreedGoal.
- `remove_look_goal` and `remove_look_random` are independent.
- Generic look-goal options obey allowlist and denylist decisions.
- FixedLookControl is installed only when enabled and allowed by its entity
  filter.
- Pufferfish puff, swimming, avoid-player and panic settings are independent.
- Squid flee and random movement settings are independent.
- Sheep `remove_eat_block` removes EatBlockGoal but preserves FloatGoal.

The tests capture and restore all touched in-memory options and list filters
in a `finally` block. One test method is used to avoid parallel mutation of
the singleton configuration.

## CI verification

The full workflow on the experimental branch runs:

1. Static NeoForge/Fabric source-parity checks.
2. Loom `validateAccessWidener` against Minecraft 26.3.
3. Minecraft 26.3 server GameTests.
4. Java compilation and JAR packaging.
5. JAR metadata and required-class verification.
6. Upload of the main and sources JAR artifacts.

The expanded GameTest matrix passed with the output
`All 2 required tests passed`; the build reported `BUILD SUCCESSFUL`.

## Not yet proven

- Natural spawning, spawn eggs and command spawning in normal worlds.
- Chunk unload/reload and disk-loaded entity behavior.
- Compatibility with third-party mods that add, wrap or replace mob goals.
- Look rotation equivalence under edge angles, head limits and rapid target
  changes.
- Performance under high entity counts compared with NeoForge.
- Normal client startup and a long-running dedicated-server session.

A passing GameTest suite proves the listed cases in a Fabric 26.3 test server.
It does not prove complete behavior parity for every vanilla/third-party mob
or stable performance in a real modpack. Keep the branch unstable until those
manual checks are completed.
