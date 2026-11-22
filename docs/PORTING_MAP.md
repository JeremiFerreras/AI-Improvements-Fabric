# NeoForge 26.3 → Fabric 26.3 mapping

| NeoForge source feature | Fabric implementation | State |
|---|---|---|
| ModDevGradle | Fabric Loom | Replaced |
| `neoforge.mods.toml` | `fabric.mod.json` | Added |
| `@Mod` | `ModInitializer` | Added |
| NeoForge event bus / `@SubscribeEvent` | Explicit Fabric API event registration | Added |
| `EntityJoinLevelEvent` | `ServerEntityEvents.ALLOW_LOAD` | Same pre-insertion manager hook; server side |
| `ModConfigSpec` | Gson JSON config in Fabric config directory | Ported |
| Access Transformer | Fabric Class Tweaker file | Validated against 26.3 |
| `ModifierSystem` | Active Fabric-side goal/filter pipeline | Ported |
| `ModifierLayer` / filter nodes | Active Fabric-side implementation | Ported and overflow behavior corrected |
| `FixedLookControl` / `FastTrig` | Active Fabric-side implementation | Reused and wired |

## Event insertion point

NeoForge 26.3 posts `EntityJoinLevelEvent` at the head of
`PersistentEntitySectionManager.addEntity(entity, loaded)`, before entity
insertion. Fabric API 26.3 invokes `ServerEntityEvents.ALLOW_LOAD` from a
Mixin at `@At("HEAD")` of the same method. The Fabric port uses this
pre-insertion event rather than the later `ENTITY_LOAD` callback.

## Adaptive ordering

The port increments `ModifierLayer` hit counts only when adaptive ordering is
enabled. At the overflow guard it sorts higher-frequency predicates first and
halves counters to preserve their relative order. `FilterLayer` uses the same
hottest-first overflow treatment. These changes affect evaluation order, not
the set of goal types modified.

## Intentional upstream bug fixes

- Pufferfish puff now reads `removeFishPuff` rather than the original
  `removeFishFollowLeader` reference.
- Squid flee now reads `removeSquidFlee` rather than the original
  `removeRandomMove` reference.

See [BEHAVIOR_PARITY.md](BEHAVIOR_PARITY.md) for the detailed rationale and
remaining runtime validation requirements.
