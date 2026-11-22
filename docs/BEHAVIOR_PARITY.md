# NeoForge 26.3 ↔ Fabric 26.3 code parity

This document compares the preserved NeoForge implementation in
`src/legacy-neoforge` with the active Fabric implementation in `src/main`.
The goal is to preserve the original goal selection and modification rules
while adapting loader integration and correcting implementation defects.

## Entity insertion timing

The NeoForge 26.3 patch posts `EntityJoinLevelEvent` at the head of
`PersistentEntitySectionManager.addEntity(entity, loaded)`, before insertion.

Fabric API 26.3 invokes `ServerEntityEvents.ALLOW_LOAD` at `@At("HEAD")` of
the same method. The Fabric port uses this pre-insertion callback rather than
the later `ENTITY_LOAD` callback. The Fabric callback is server-side because
mob goal selection and AI ticking are authoritative on the logical server.

## AI goal rules

The active Fabric `ModifierSystem` carries over the same entity type filters,
goal classes and settings for generic mobs, fish, pufferfish, squid, cows,
chickens, pigs and sheep. Sheep's separate eat-block option is retained.

### Corrected upstream option connections

The preserved NeoForge source contains two mismatched option references:

| Goal | NeoForge reference | Fabric reference |
|---|---|---|
| `PufferfishPuffGoal` | `removeFishFollowLeader` | `removeFishPuff` |
| `SquidFleeGoal` | `removeRandomMove` | `removeSquidFlee` |

The Fabric connections follow the option names. With the original default
configuration (all removal options false), these differences do not remove
additional goals.

## Goal-edit pipeline

The Fabric port preserves the pipeline shape:

1. The root filter accepts `Mob` instances.
2. The generic mob editor runs first.
3. Fish, squid, cow, chicken, pig and sheep sub-editors run only for matching
   entity types.
4. Goal selectors are traversed without mutation.
5. Matching goals are collected and removed after iteration.

The Fabric implementation iterates only populated modifier/filter entries.
The original NeoForge arrays also visited unused slots and needed null guards.
This does not change which registered predicates run.

## Adaptive ordering and counter safety

The old NeoForge `ModifierLayer` increments a modifier's `callCount` only
inside `bubble()`, but calls `bubble()` only after comparing that counter to
the preceding counter. Since all counters start at zero, this path never
records its first hit. Fabric records successful matches and gates modifier
statistics/reordering behind `enableCallBubbling`.

Both Fabric layers order frequent matches first and halve counters at the
overflow threshold. The old normalization divided by the total and rounded
down, collapsing almost all counters to zero. The Fabric implementation also
normalizes a layer containing only one node; that edge case must not be
skipped because its counter still needs to be prevented from overflowing.

These changes affect evaluation order, not the registered goal predicates.

## Look controller

`FastTrig.java` is byte-for-byte identical to the preserved NeoForge source.
`FixedLookControl` retains the cached atan2 calls, angle conversion constant,
wanted-position state, cooldown and rotation-speed state. It replaces only
the exact vanilla `LookControl` class; modded subclasses are left untouched.

The Fabric implementation also checks that the old controller is non-null
before copying its state. The original replacement condition allowed a null
controller but then dereferenced it unconditionally.

## Configuration

The Fabric port stores the same option groups and defaults in
`config/aiimprovements.json` instead of NeoForge's `ModConfigSpec` TOML.
The JSON structure is intentionally different; there is no automatic import
of an existing NeoForge TOML file. The allowlist/denylist decision is
preserved:

- allowlist: only listed entity IDs are modified;
- denylist: listed entity IDs are excluded.

Configuration is read at startup. There is no live reload or in-game config
screen.

## Automated behavioral verification

The active GameTest suite runs in the GitHub Actions validation workflow. It
checks the vanilla goal baselines before removal, every configured goal option
for cows, chickens, pigs and sheep, fish and squid options, generic look-goal
filters, allowlist/denylist behavior, and the independence of the pufferfish
puff/swim and squid flee/random-movement settings.

The LookControl comparison checks 29 immediate target geometries, including
all yaw quadrants, exact axes, near-zero offsets, vertical targets, zero-offset
targets and values on both sides of vanilla's 1.0E-5 undefined-angle threshold.
It then compares vanilla and FixedLookControl through five changing targets
over 40 gradual rotation ticks each, at a limited rotation speed. The test
normalizes both mobs to the same initial body/head yaw and pitch before gradual
turns. The acceptance tolerance remains 0.75 degrees for yaw and pitch.

The production-server smoke runs the packaged JAR with 12 AI-enabled mobs per
species (84 total) across cows, chickens, pigs, sheep, salmon, squid and
pufferfish. It verifies horizontal movement for every species during a
60-second natural AI window. After chunk unload/reload, the four farm-animal
groups receive a small `minecraft:in_fire` hit, followed by an invulnerable
state and sustained fire. In 26.3, `minecraft:in_fire` is included in
`#minecraft:panic_environmental_causes`, which is nested in
`#minecraft:panic_causes`; this activates the normal `PanicGoal` predicate.
Fish and squid retain their usual swim/movement goals. Movement is checked
again for all seven groups, followed by world save, dedicated-server restart
and verification of all populations after disk loading. The client smoke
launches the packaged JAR under Xvfb; it initializes LWJGL/resources and selects
Vulkan with Mesa llvmpipe in the virtual display.

The full validation passed on commit
`10b9ea59e1cc651eefa0b189a2f475e3f64d8605` in CI run
[45072561414](https://github.com/JeremiFerreras/AI-Improvements-Fabric/actions/runs/45072561414).
It includes the panic-causing damage stimulus, all seven post-reload movement
markers, disk persistence, the expanded LookControl cases and client startup.

## Remaining practical verification

Interactive player-driven behavior, natural spawning across all mob families,
third-party AI compatibility, long-duration multiplayer, large-modpack
interactions, physical GPU rendering and performance under substantially
higher entity counts remain unverified. CI smoke tests are bounded integration
checks and must not be presented as proof of universal modpack stability.
