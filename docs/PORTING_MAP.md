# NeoForge 26.3 → Fabric 26.3 mapping

This file records the migration choices for this mod. It is not a claim that every subsystem has already been ported.

| Original NeoForge subsystem | Fabric 26.3 direction | Current checkpoint |
|---|---|---|
| `net.neoforged.moddev` / ModDevGradle | `net.fabricmc.fabric-loom` | Replaced in `build.gradle` |
| `neoforge.mods.toml` | `fabric.mod.json` | Fabric metadata added; old TOML archived |
| `@Mod` | `ModInitializer` | Fabric entrypoint added |
| `@EventBusSubscriber` / `@SubscribeEvent` | Explicit Fabric API event registration | Entity lifecycle bridge added |
| `EntityJoinLevelEvent` | `ServerEntityEvents.ENTITY_LOAD` as the first candidate | Registered, but event timing still needs regression validation |
| `ModConfigSpec` / `ModContainer.registerConfig` | Mod-owned JSON configuration (or a small Fabric config library if justified) | Not implemented |
| `META-INF/accesstransformer.cfg` | Fabric 26.3 Class Tweaker (`.classtweaker`) and/or accessor Mixin | Not implemented; targets need checking against 26.3 named bytecode |
| `FMLCommonSetupEvent` | Direct initialization from `ModInitializer` after dependencies are available | Bootstrap moved; feature initialization is not active |

## Why the access conversion is not copied mechanically

The original Access Transformer exposes `GoalSelector.availableGoals`, `Mob.lookControl`, three `LookControl` fields, and private nested Squid/Pufferfish goal classes. Fabric 26.3 documents access widening through Class Tweaker files, while accessor Mixins are often safer for individual members. The exact field descriptors, mutability needs, and inner-class names must be read from the 26.3 game jar before adding entries; carrying over NeoForge AT syntax would be invalid.

## Compatibility rule

Keep the Minecraft target pinned to `26.3`. Do not “solve” loader migration by moving the project to another Minecraft release. Keep the original engine code as a reference until each class has a Fabric equivalent and tests.
