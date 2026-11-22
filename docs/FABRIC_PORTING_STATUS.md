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
   denylist behavior, and vanilla-versus-FixedLookControl rotation. The
   controller comparison checks 29 immediate geometries (including both sides
   of vanilla's 1.0E-5 undefined-angle threshold) plus five changing targets
   over 40 gradual ticks each, with a 0.75-degree yaw/pitch tolerance.
4. Production dedicated-server launch using the packaged remapped JAR and
   Fabric API, not just the development classpath.
5. A persistent-mob scenario with seven species in separate land/water pens.
   The script counts each group and checks horizontal movement during a
   60-second natural AI window. It removes force-load tickets, verifies that
   all seven chunks unload, reloads them, then applies a temporary invulnerable
   on-fire stimulus to the four farm-animal groups to activate vanilla
   PanicGoal deterministically. Fish and squid retain their normal swimming /
   movement behavior. Movement is checked again for each species.
6. World save, clean server shutdown, second server startup and disk-load
   population checks.
7. Production client launch using the packaged JAR under Xvfb, checking mod
   initialization, LWJGL, resource reload, graphics backend selection and
   generated configuration.
8. Final inspection of the remapped distribution JAR and uploaded smoke logs.

The complete automated validation, including the deterministic
post-reload PanicGoal stimulus, passed in CI run
[45071550721](https://github.com/JeremiFerreras/AI-Improvements-Fabric/actions/runs/45071550721)
on code commit `0570c77978761c496f528a1856c9509f547ad6e5`. The later branch
commits through `139a520f1f1527f3eb82a45b2e191d32224637b6` change only README
and documentation files; no runtime source or test code changed after the
successful run.

## Limits not established by CI

- Interactive gameplay with a human player and normal client input.
- Physical GPU rendering (the CI client uses Xvfb/virtualized graphics).
- Third-party mods that wrap, replace or inject mob goals/controllers.
- Natural spawn conditions and spawn-egg behavior across all entity types.
- Long-duration multiplayer sessions and large modpack interactions.
- High-load performance beyond the bounded CI mob population.

The JSON config is read at startup; there is no config GUI or live reload.
Goal replacement remains unsupported, matching the upstream TODO.
