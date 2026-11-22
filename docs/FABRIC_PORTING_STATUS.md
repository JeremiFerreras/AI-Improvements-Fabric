# Fabric 26.3 port — implementation status

**Current phase:** finish the code and project structure. Functional testing is
deferred until the maintainer explicitly asks for it. This document does not
declare the port released or practically stable.

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

## Source and structure review

The original Java implementation inventory is represented in the active Fabric
source tree. The original NeoForge sources are preserved separately under
`src/legacy-neoforge/`; they are not part of the active Fabric source set.

The active implementation includes:

- Fabric initializer and server entity lifecycle registration.
- The original configuration categories and options, represented in the
  Fabric JSON configuration.
- Generic mob, fish, pufferfish, squid, cow, chicken, pig and sheep modifiers.
- The modifier/filter nodes and their dispatch pipeline.
- `FastTrig` and the `FixedLookControl` replacement.
- Fabric metadata and Class Tweaker configuration.
- A preserved GameTest source set for later functional verification.

The port retains the original goal predicates and entity filters. The two
upstream miswired options for pufferfish puff and squid flee are connected to
their intended settings in the Fabric implementation. Adaptive ordering and
goal-removal iteration were also revised in the Fabric implementation; see
[BEHAVIOR_PARITY.md](BEHAVIOR_PARITY.md).

## Current build workflow

The normal build task is `buildFabricPort`. It performs source compilation,
Class Tweaker validation and JAR remapping/packaging. CI also performs static
source-mapping and packaged-JAR inspection.

The normal build workflow intentionally does **not** execute GameTests, launch
Minecraft or perform runtime behavior checks. The GameTest sources remain
available under `src/gametest/` for the later test phase.

The obsolete Travis configuration targeting Java 7 has been removed. GitHub
Actions is the active package workflow for this Fabric branch.

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

Until those checks are requested and performed, code review and successful
packaging must not be presented as proof of practical stability.
