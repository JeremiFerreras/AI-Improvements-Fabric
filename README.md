# AI Improvements — Fabric 26.3

> **Experimental / unstable port.** The first AI editing pipeline is wired, but this branch is not a stable release and has not yet passed in-game behavior testing.

This branch ports the NeoForge 26.3 version to Fabric 26.3. The Minecraft version is intentionally unchanged.

## Current experimental features

- Fabric Loom build, Loader entrypoint and metadata.
- Server-side entity insertion hook using `ServerEntityEvents.ALLOW_LOAD`.
- JSON configuration at `config/aiimprovements.json`.
- Original filter and modifier-layer pipeline, now active in Fabric source.
- Generic mob, fish, pufferfish, squid, cow, chicken, pig and sheep goal rules.
- Cached `FastTrig` look math and `FixedLookControl` replacement.
- Fabric Class Tweaker targets for the vanilla internals previously opened by the NeoForge Access Transformer.
- Original NeoForge Java source and metadata retained under `src/legacy-neoforge/` as a comparison reference.

## Known limitations

- Class Tweaker targets must pass the 26.3 Loom validation task.
- Entity insertion timing and compatibility with other entity/AI mods need in-game verification.
- The JSON configuration is read on startup; there is no configuration screen or live reload.
- Goal replacement is not implemented (the upstream implementation also leaves this as a TODO).
- Client and dedicated-server launch tests and performance profiling are still required.

**Do not use this branch as a stable release.** Settings can remove AI goals and change entity behavior.

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.20.1 or newer
- Fabric API 0.166.2+26.3

## Build

Windows:

```bat
gradlew.bat validateAccessWidener build
```

Linux/macOS:

```bash
./gradlew validateAccessWidener build
```

The remapped mod JAR is produced under `build/libs/`.

## Porting notes

- [Port status and verification](docs/FABRIC_PORTING_STATUS.md)
- [NeoForge-to-Fabric mapping](docs/PORTING_MAP.md)

## Upstream

Original project and feature design: [Built Broken Modding — AI Improvements](https://github.com/BuiltBrokenModding/AI-Improvements).

The original MIT license is retained. This Fabric port is maintained separately from the NeoForge source branch.
