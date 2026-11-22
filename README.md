# AI Improvements — Fabric 26.3

> **Work in progress. This is an initial Fabric porting scaffold, not a playable or feature-complete release.**

This branch starts the migration of AI Improvements from NeoForge 26.3 to Fabric 26.3. The Minecraft version is intentionally unchanged.

## Current state

- Fabric Loom build configuration for Minecraft 26.3.
- Fabric Loader entrypoint and `fabric.mod.json` metadata.
- Server entity lifecycle bridge using Fabric API's `ServerEntityEvents.ENTITY_LOAD`.
- The original `FastTrig` helper is reused unchanged.
- The original NeoForge source and metadata are preserved under `src/legacy-neoforge/` as a migration reference.

**No AI goals are modified yet.** The current `ModifierSystem` is an intentional placeholder. Do not install this build expecting the original optimization features to work.

## Requirements

- Minecraft 26.3
- Java 25
- Fabric Loader 0.20.1 or newer
- Fabric API 0.166.2+26.3

## Build

On Windows:

```bat
gradlew.bat build
```

On Linux/macOS:

```bash
./gradlew build
```

The resulting remapped mod JAR is produced under `build/libs/`.

## Porting notes

See [`docs/FABRIC_PORTING_STATUS.md`](docs/FABRIC_PORTING_STATUS.md) for the current checklist and [`docs/PORTING_MAP.md`](docs/PORTING_MAP.md) for the NeoForge-to-Fabric subsystem mapping.

## Upstream

Original project and feature design: [Built Broken Modding — AI Improvements](https://github.com/BuiltBrokenModding/AI-Improvements).

This Fabric port is maintained separately from the NeoForge source branch. The original MIT license is retained.
