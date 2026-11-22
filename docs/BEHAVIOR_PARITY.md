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

## Deferred GameTest source

The Minecraft 26.3 GameTest source remains under `src/gametest/` as material
for the later functional-validation phase. The current `buildFabricPort`
task and GitHub Actions package workflow do not invoke it or launch Minecraft.
No current code-completion claim in this document relies on a new GameTest run.

An earlier CI revision did execute a focused GameTest matrix and reported
`All 2 required tests passed` in run
[45050485699](https://github.com/JeremiFerreras/AI-Improvements-Fabric/actions/runs/45050485699).
That is historical evidence for those listed cases only, not a current test
run and not proof of full practical stability.

## Remaining practical verification

Natural spawning, spawn eggs, chunk reloads, disk-loaded entities,
third-party AI compatibility, LookControl rotation over time, high-entity
count performance, normal client startup and long-running dedicated-server
behavior are intentionally deferred until the maintainer requests testing.
