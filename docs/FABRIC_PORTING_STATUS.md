# Fabric 26.3 port — implementation status

**Current phase:** complete the code and repository structure by static review.
Functional testing is deferred until the maintainer explicitly asks for it.
This document does not declare the port released or practically stable.

## Base and target

- Original source branch: `26.3`
- Original base commit: `a51cab76acf89099ea3c41393d858f9e061dbe4b`
- Fabric work branch: `port/fabric-26.3-bootstrap`
- Minecraft: `26.3`
- Java: `25`
- Fabric Loader: `0.20.1`
- Fabric API: `0.166.2+26.3`
- Loom: `1.19-SNAPSHOT`
- Mod version metadata: `0.5.0-fabric-unstable`

## Code and structure inventory

The 18 original runtime Java source files are represented by active Fabric
counterparts. The Fabric initializer and lifecycle adapter replace the
NeoForge mod entrypoint/event subscriber. The original NeoForge source,
metadata and access transformer remain isolated under
`src/legacy-neoforge/`, outside the active Fabric source set.

The active implementation contains:

- Fabric initializer and server entity lifecycle registration.
- The original configuration categories and options, represented in JSON.
- Generic mob, fish, pufferfish, squid, cow, chicken, pig and sheep modifiers.
- The modifier/filter nodes and dispatch pipeline.
- `FastTrig` and the `FixedLookControl` replacement.
- Fabric metadata and Class Tweaker configuration.
- A preserved GameTest source set under `src/gametest/`, not invoked by the
  normal package task.

The old standalone performance benchmark used pre-26.3 Minecraft classes and
referenced a controller that is not part of this port. It has been moved to
`archive/legacy-tools/` for historical reference and is not compiled.

## Implementation corrections

- Pufferfish puff now uses `removeFishPuff`, rather than the miswired
  `removeFishFollowLeader` reference in the original.
- Squid flee now uses `removeSquidFlee`, rather than the miswired
  `removeRandomMove` reference in the original.
- Goal removal remains deferred until after selector iteration.
- Adaptive modifier ordering is explicit and counter normalization also
  handles a single-node layer.

See [BEHAVIOR_PARITY.md](BEHAVIOR_PARITY.md) for the source-level comparison.

## Current package workflow

The normal package task is `buildFabricPort`. It compiles the active Fabric
source, validates the Class Tweaker configuration and creates the remapped
JAR.

The validation workflow additionally runs the Fabric GameTest server and a
production-launcher smoke test. The latter starts the packaged distribution JAR using
Fabric's production dedicated-server launcher, summons representative mobs,
checks startup/config creation, saves the world and shuts down. This is a
short-lived server smoke test, not a long-running multiplayer or modpack test.

The obsolete Travis configuration targeting Java 7 has been removed.
GitHub Actions is the active package workflow for this Fabric branch.

## Functional checks deferred by request

Do not run these checks until the maintainer explicitly requests them:

- GameTests and live goal-selector behavior.
- Natural spawning, spawn eggs and command spawning in normal worlds.
- Chunk unload/reload and disk-loaded entity behavior.
- Compatibility with third-party mods that add, wrap or replace mob goals.
- Look rotation equivalence at edge angles, head limits and rapid target
  changes.
- Performance under high entity counts.
- Normal client startup and a long-running dedicated-server session.

Static source review and package creation must not be presented as proof of
practical stability.
