# AI Improvements — Fabric 26.3

> **Experimental / unstable port.** This branch has a source-level parity
> check and a compiled Fabric JAR, but it is not yet a stable release.

This branch ports the NeoForge 26.3 implementation to Fabric 26.3. The
Minecraft version is intentionally unchanged.

## Current experimental features

- Fabric Loom build, Loader entrypoint and metadata.
- Pre-insertion server entity hook using `ServerEntityEvents.ALLOW_LOAD`.
- JSON configuration at `config/aiimprovements.json`.
- Original filter and modifier pipeline adapted to Fabric.
- Generic mob, fish, pufferfish, squid, cow, chicken, pig and sheep goal rules.
- Cached `FastTrig` math and `FixedLookControl` replacement.
- Fabric Class Tweaker entries for the vanilla fields/classes exposed by the
  NeoForge Access Transformer.
- Original NeoForge Java sources and metadata retained under
  `src/legacy-neoforge/`.

## Parity and known limitations

- The exact event insertion point has been compared in Fabric API and NeoForge
  26.3 sources.
- A source-level parity script checks registered goal rules, defaults, access
  widening, FastTrig identity and safe deferred goal removal.
- Two mismatched upstream configuration references are corrected for
  pufferfish puff and squid flee; see [behavior parity](docs/BEHAVIOR_PARITY.md).
- No in-game regression suite has yet proved identical behavior for every
  vanilla or third-party mob.
- The JSON configuration is read at startup; there is no GUI or live reload.
- Goal replacement remains unsupported, matching the upstream TODO.

**Do not use this branch as a stable release.** Incorrect goal-removal settings
can change mob behavior.

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.20.1 or newer
- Fabric API 0.166.2+26.3

## Build and verify

Windows:

```bat
gradlew.bat verifyPortParity
gradlew.bat validateAccessWidener build
```

Linux/macOS:

```bash
./gradlew verifyPortParity validateAccessWidener build
```

The remapped mod JAR is produced under `build/libs/`.

## Porting documents

- [Behavior parity review](docs/BEHAVIOR_PARITY.md)
- [Configuration reference](docs/CONFIGURATION.md)
- [Port status](docs/FABRIC_PORTING_STATUS.md)
- [NeoForge-to-Fabric mapping](docs/PORTING_MAP.md)

## Upstream

Original project and feature design: [Built Broken Modding — AI Improvements](https://github.com/BuiltBrokenModding/AI-Improvements).

The original MIT license is retained. This Fabric port is maintained separately
from the NeoForge source branch.
