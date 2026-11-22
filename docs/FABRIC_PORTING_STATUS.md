# Fabric 26.3 port status

**Checkpoint:** `fabric-bootstrap-0.1`  
**Overall status:** scaffolding only — not functional as an AI-improvement mod.

## Base and target

- Source repository: `JeremiFerreras/AI-Improvements-Fabric`
- Base branch: `26.3`
- Base commit: `a51cab76acf89099ea3c41393d858f9e061dbe4b`
- Original mod version: `0.5.5`
- Original loader: NeoForge `26.3.0.44-beta`
- Target game: Minecraft `26.3` (unchanged)
- Target Java: `25`
- Initial Fabric stack: Loader `0.20.1`, Loom `1.19-SNAPSHOT`, Fabric API `0.166.2+26.3`, Gradle `9.8.2`

## Completed in this checkpoint

- Replaced the NeoForge ModDevGradle build with the Fabric Loom plugin and Fabric dependencies.
- Added Fabric Loader metadata and a `ModInitializer` entrypoint.
- Added a server-side entity lifecycle registration through `ServerEntityEvents.ENTITY_LOAD`.
- Reused `FastTrig` unchanged; it is initialized but its optimized look controller is not connected.
- Preserved all original NeoForge Java sources, `neoforge.mods.toml`, and Access Transformer under `src/legacy-neoforge/` so they remain available during migration and are not compiled as Fabric sources.
- Added a GitHub Actions build workflow using Java 25.

## Not migrated yet

- `ConfigMain` / `ModConfigSpec`: no Fabric configuration persistence or UI exists yet.
- `ModifierSystem`: the active Fabric class is a no-op placeholder; the original goal/filter pipeline is only in the legacy snapshot.
- Goal selectors: access to `GoalSelector.availableGoals` has not been widened for Fabric 26.3.
- `Mob.lookControl` and the mutable fields in `LookControl`: no Class Tweaker or accessor Mixin is wired yet.
- Private nested goals for Squid and Pufferfish: visibility and class signatures still need verification against the exact 26.3 named game jar.
- The original configuration bugs and goal-to-option wiring have not been changed in the legacy snapshot.

## Important event-timing note

The old source subscribes to NeoForge `EntityJoinLevelEvent`. The Fabric bridge currently uses `ServerEntityEvents.ENTITY_LOAD`, whose documented contract is that the entity is **already in the `ServerLevel`** when the callback runs. The event names are not proof of identical timing or coverage. Before enabling the editor, compare behavior for newly spawned entities, entities loaded from disk, and entities added by other mods. If the timing is too late for the required goal edits, use a narrowly scoped Mixin at the appropriate server-side insertion point instead of assuming equivalence.

## Verification status

- Metadata JSON and source-set separation: checked locally during preparation.
- Full Gradle build: not yet verified in this workspace because the available runtime is Java 21, while Minecraft 26.3 requires Java 25. CI is configured to run with Java 25 after the branch is pushed.
- Client launch: not tested.
- Dedicated-server launch: not tested.
- AI behavior / performance: not tested; no AI edits are active yet.

## Next porting stages

1. Inspect the exact Minecraft 26.3 named signatures for goal selectors, look control fields, and private inner goal classes.
2. Add the required `.classtweaker` entries and accessor Mixin(s), then validate them with Loom.
3. Replace `ModConfigSpec` with a Fabric-side JSON config while preserving every original option and default.
4. Port the generic editor/filter classes without changing their algorithmic behavior.
5. Port entity-type-specific registrations and fix the known miswired settings only with explicit regression checks.
6. Build and launch both client and dedicated server; then run behavior and profiling checks.
