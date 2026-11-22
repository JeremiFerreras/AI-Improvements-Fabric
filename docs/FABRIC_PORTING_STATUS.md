# Fabric 26.3 port — implementation and validation status

**Current phase:** implementation is complete for the original runtime feature
set; automated functional validation is active. This is still an unstable
development build, not a release or a claim of full modpack stability.

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

## Implementation

The 18 original runtime Java source files have active Fabric counterparts.
NeoForge-only entrypoint, lifecycle, configuration and access-transformation
code is isolated under `src/legacy-neoforge/`; it is not part of the active
Fabric source set.

The Fabric implementation contains the initializer, pre-insertion server
entity callback, JSON configuration, generic and species-specific goal filters,
adaptive modifier/filter pipeline, `FastTrig`, `FixedLookControl`, Fabric
metadata and Class Tweaker configuration.

Known upstream wiring defects are corrected: pufferfish puff uses
`removeFishPuff`, and squid flee uses `removeSquidFlee`. Goal selector
mutation is deferred until after traversal. Adaptive ordering only changes
predicate evaluation order and normalizes counters safely.

## Validation now running in CI

The workflow on this branch performs:

1. Static source inventory, option mapping, metadata and packaging audit.
2. Java 25 compilation and Fabric Class Tweaker validation.
3. Minecraft 26.3 GameTests for all exposed goal-removal options, allowlist /
   denylist behavior, and vanilla-versus-FixedLookControl rotation.
4. Production dedicated-server launch using the packaged remapped JAR and
   Fabric API, not just the development classpath.
5. A persistent-mob scenario with seven species in separate land/water pens.
   The script counts each group, verifies actual horizontal movement during
   AI ticking, removes force-load tickets, proves all seven chunks unloads,
   reloads them, and checks that every species moves again afterward.
6. World save, clean server shutdown, second server startup and disk-load
   population checks.
7. Production client launch using the packaged JAR under Xvfb, checking mod
   initialization, LWJGL, resource reload, graphics backend selection and
   generated configuration.
8. Final inspection of the remapped distribution JAR and uploaded smoke logs.

The latest verified CI run before the current movement-test expansion is
[45062439870](https://github.com/JeremiFerreras/AI-Improvements-Fabric/actions/runs/45062439870).
It passed GameTests, dedicated-server 60-second AI windows, in-session chunk
reload, disk reload, and headless production-client startup. Its population
checks proved entity persistence, not individual goal execution; the
movement assertions are being added to close that gap.

## Limits not established by CI

- Interactive gameplay with a human player and normal client input.
- Physical GPU rendering (the CI client uses Xvfb/virtualized graphics).
- Third-party mods that wrap, replace or inject mob goals/controllers.
- Natural spawn conditions and spawn-egg behavior across all entity types.
- Long-duration multiplayer sessions and large modpack interactions.
- High-load performance beyond the bounded CI mob population.

The JSON config is read at startup; there is no config GUI or live reload.
Goal replacement remains unsupported, matching the upstream TODO.
