# AI Improvements — Fabric 26.3

This branch contains the Fabric 26.3 port of the original NeoForge 26.3
implementation. The work is still in the implementation phase; no release or
practical-stability designation is being made.

## Ported code

- Fabric Loom build, Loader entrypoint and metadata.
- Pre-insertion server entity hook using `ServerEntityEvents.ALLOW_LOAD`.
- JSON configuration at `config/aiimprovements.json`, carrying the original
  option groups and default values.
- Generic mob, fish, pufferfish, squid, cow, chicken, pig and sheep goal rules.
- Original modifier/filter pipeline, adapted to Fabric.
- Cached `FastTrig` math and `FixedLookControl` replacement.
- Fabric Class Tweaker entries for the vanilla fields/classes exposed by the
  NeoForge Access Transformer.
- Original NeoForge Java sources and metadata retained under
  `src/legacy-neoforge/`.

The two incorrect upstream option connections for pufferfish puff and squid
flee are corrected in the Fabric implementation. See
[behavior parity](docs/BEHAVIOR_PARITY.md).

## Build and package

Requirements: Minecraft 26.3, Java 25, Fabric Loader 0.20.1 or newer, and
Fabric API 0.166.2+26.3.

Windows:

```bat
build-local.bat
```

Linux/macOS:

```bash
./gradlew --no-daemon buildFabricPort
```

This task compiles the Fabric source, validates the Class Tweaker configuration
and creates the remapped JAR. It deliberately does **not** run GameTests or
launch a client/server.

The CI validation workflow also runs the GameTest server and the remapped JAR
through Fabric's production dedicated-server launcher. The production smoke
spawns representative mob types, checks generated config, saves the world and
shuts the server down.

## Deferred functional checks

The GameTest source suite remains under `src/gametest/`, but execution is
paused until the maintainer explicitly requests testing. Natural spawning,
spawn eggs, disk-loaded entities, third-party AI compatibility, look rotation,
high-mob-count performance, normal client startup and long-running server
behavior remain unverified in practice.

The JSON configuration is read at startup; there is no GUI or live reload.
Goal replacement remains unsupported, matching the upstream TODO.

## Porting references

- [Behavior parity review](docs/BEHAVIOR_PARITY.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Implementation status](docs/FABRIC_PORTING_STATUS.md)
- [NeoForge-to-Fabric mapping](docs/PORTING_MAP.md)

## Upstream

Original project and feature design: [Built Broken Modding — AI Improvements](https://github.com/BuiltBrokenModding/AI-Improvements).

The original MIT license is retained. This Fabric port is maintained separately
from the NeoForge source branch.
