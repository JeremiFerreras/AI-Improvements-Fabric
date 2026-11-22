# NeoForge 26.3 ↔ Fabric 26.3 behavior parity

This document compares the preserved NeoForge implementation in
`src/legacy-neoforge` with the active Fabric implementation in
`src/main`. The purpose is to preserve the same mob-goal selection and
modification behavior, while fixing implementation defects that are not part
of the intended AI rules.

## Entity insertion timing

The NeoForge 26.3 patch posts `EntityJoinLevelEvent` at the head of
`PersistentEntitySectionManager.addEntity(entity, loaded)`, before the
entity is inserted into the manager.

Fabric API 26.3's `PersistentEntitySectionManagerMixin` invokes
`ServerEntityEvents.ALLOW_LOAD` at `@At("HEAD")` of that same
`addEntity(entity, loaded)` method. Both hooks therefore run before
`addEntityUuid` / insertion work, and both receive the same loaded-from-disk
state. The Fabric port deliberately uses `ALLOW_LOAD`, not
`ENTITY_LOAD`, which would run after the entity is already in the level.

The NeoForge event is documented for both logical sides. Fabric's hook is
server-side because it is attached to the server's persistent entity manager.
That is intentional: mob goal selection and AI ticking are authoritative on
the logical server. No client-side goal mutation is required for server mob
behavior.

## AI goal rules

The active Fabric `ModifierSystem` preserves the same entity type filters,
goal classes, and settings for:

- Generic mobs: `LookAtPlayerGoal`, `RandomLookAroundGoal`, and the vanilla
  `LookControl` replacement.
- Fish: `RandomSwimmingGoal`, `AvoidEntityGoal`, `PanicGoal`,
  `FollowFlockLeaderGoal`, and pufferfish puff.
- Squid: random movement and flee.
- Cow, chicken, pig and sheep: float, panic, breed, tempt, follow-parent and
  random-stroll goals.
- Sheep: eat-block goal.

### Two corrected upstream option connections

The preserved NeoForge 26.3 source has two mismatched config references:

| Goal | NeoForge source reference | Fabric port reference |
|---|---|---|
| `PufferfishPuffGoal` | `removeFishFollowLeader` | `removeFishPuff` |
| `SquidFleeGoal` | `removeRandomMove` | `removeSquidFlee` |

These are intentional fixes, not accidental port drift. They make the existing
configuration options control the goals named by those options. With default
configuration (all removal options false), both implementations leave these
goals untouched. When those two options are explicitly enabled, Fabric follows
the advertised setting rather than reproducing the upstream wiring mistakes.

## Goal-edit pipeline

The Fabric port preserves the original pipeline shape:

1. The root filter accepts `Mob` instances.
2. The generic mob editor runs first.
3. Fish, squid, cow, chicken, pig and sheep sub-editors run only for matching
   entity types.
4. Each goal selector is traversed without mutation.
5. Matching goals are collected and removed after iteration.

The Fabric implementation iterates only populated modifier/filter entries.
The old NeoForge arrays also iterated unused slots and had to guard nulls.
This is an allocation/iteration safety improvement; it does not alter which
registered predicates run.

### Adaptive ordering

The old NeoForge `ModifierLayer` increments a modifier's `callCount` only
inside `bubble()`, but calls `bubble()` only after comparing that counter
with the preceding counter. All counters start at zero, so the adaptive order
never gets its first increment. Fabric increments on a successful match and
gates all statistics/reordering behind `enableCallBubbling`.

At the overflow threshold both Fabric layers now sort hottest-first and halve
the counters. The previous normalization divided each counter by the total
and rounded down, collapsing nearly all counters to zero; it also sorted in
ascending order even though the hot node is bubbled toward index zero. These
changes affect predicate evaluation order only, not goal matching or removal
rules.

## Look controller

`FastTrig.java` is byte-for-byte identical to the preserved NeoForge source.
`FixedLookControl` retains the same cached atan2 calls, angle conversion
constant, wanted-position state, cooldown and rotation-speed state. It still
replaces only the exact vanilla `LookControl` class; modded subclasses are
left untouched to avoid overriding another mod's controller.

## Configuration

The Fabric port stores the same option set and defaults in
`config/aiimprovements.json` instead of NeoForge's `ModConfigSpec` TOML.
The `FilteredConfigValue.isFiltered` truth table is retained:

- allowlist: only listed entity IDs are modified;
- denylist: listed entity IDs are excluded.

The JSON format is different, but the option defaults and runtime decisions
are intended to match. The config is read on startup; there is no live reload
or in-game config screen yet.

## Verification limits

The CI parity check verifies the source mapping, active-source isolation,
Class Tweaker entries, default settings and the compiled JAR contents.
It does **not** prove that every vanilla or third-party mob behaves identically
at runtime. A real client and dedicated-server test is still required for
spawning, disk reloads, mod-added goals and high-entity-count performance.
