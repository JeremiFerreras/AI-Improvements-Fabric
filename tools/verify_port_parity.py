#!/usr/bin/env python3
"""Static source/structure guard for the NeoForge 26.3 -> Fabric 26.3 port.

This script inspects source mapping, configuration coverage, metadata and an
optional packaged JAR. It does not launch Minecraft or execute GameTests.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LEGACY = ROOT / "src/legacy-neoforge/java/com/builtbroken/ai/improvements"
FABRIC = ROOT / "src/main/java/com/builtbroken/ai/improvements"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def read(path: Path) -> str:
    require(path.is_file(), f"Missing required file: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def check_source_inventory() -> None:
    # Every original runtime Java class has an active Fabric counterpart.
    source_pairs = {
        "AIImprovements.java": "AIImprovementsFabric.java",
        "ConfigMain.java": "ConfigMain.java",
        "FastTrig.java": "FastTrig.java",
        "FixedLookControl.java": "FixedLookControl.java",
        "modifier/ModifierLevel.java": "modifier/ModifierLevel.java",
        "modifier/ModifierSystem.java": "modifier/ModifierSystem.java",
        "modifier/editor/ConfigDrivenEdit.java": "modifier/editor/ConfigDrivenEdit.java",
        "modifier/editor/FilteredRemove.java": "modifier/editor/FilteredRemove.java",
        "modifier/editor/GenericRemove.java": "modifier/editor/GenericRemove.java",
        "modifier/editor/IEntityAiModifier.java": "modifier/editor/IEntityAiModifier.java",
        "modifier/editor/InstanceCheck.java": "modifier/editor/InstanceCheck.java",
        "modifier/editor/ModifierLayer.java": "modifier/editor/ModifierLayer.java",
        "modifier/editor/ModifierNode.java": "modifier/editor/ModifierNode.java",
        "modifier/filters/FilterLayer.java": "modifier/filters/FilterLayer.java",
        "modifier/filters/FilterNode.java": "modifier/filters/FilterNode.java",
        "modifier/filters/FilterResult.java": "modifier/filters/FilterResult.java",
        "modifier/filters/IFilterNode.java": "modifier/filters/IFilterNode.java",
        "modifier/filters/NoopNode.java": "modifier/filters/NoopNode.java",
    }
    for original, fabric in source_pairs.items():
        read(LEGACY / original)
        read(FABRIC / fabric)

    original_fast_trig = LEGACY / "FastTrig.java"
    fabric_fast_trig = FABRIC / "FastTrig.java"
    require(
        original_fast_trig.read_bytes() == fabric_fast_trig.read_bytes(),
        "FastTrig.java diverged from the preserved NeoForge source",
    )

    active_java = list((ROOT / "src/main/java").rglob("*.java"))
    for path in active_java:
        source = path.read_text(encoding="utf-8")
        for obsolete in ("net.neoforged.", "net.minecraft.entity.", "FixedEntityLookHelper"):
            require(obsolete not in source,
                    f"Obsolete NeoForge/legacy API reference remains in {path.relative_to(ROOT)}")


def check_goal_mapping() -> None:
    legacy_system = read(LEGACY / "modifier/ModifierSystem.java")
    fabric_system = read(FABRIC / "modifier/ModifierSystem.java")

    rule_pairs = [
        ("LookAtPlayerGoal", "removeLookGoal"),
        ("RandomLookAroundGoal", "removeLookRandom"),
        ("RandomSwimmingGoal", "removeFishSwim"),
        ("AvoidEntityGoal", "removeFishAvoidPlayer"),
        ("PanicGoal", "removeFishPanic"),
        ("FollowFlockLeaderGoal", "removeFishFollowLeader"),
        ("SquidRandomMovementGoal", "removeRandomMove"),
        ("FloatGoal", "removeFloat"),
        ("BreedGoal", "removeBreed"),
        ("TemptGoal", "removeTempt"),
        ("FollowParentGoal", "removeFollowParent"),
        ("WaterAvoidingRandomStrollGoal", "removeStroll"),
        ("EatBlockGoal", "removeSheepEatBlock"),
    ]
    for goal_type, setting in rule_pairs:
        require(goal_type in legacy_system, f"NeoForge source lacks expected rule {goal_type}")
        require(goal_type in fabric_system, f"Fabric port lacks expected rule {goal_type}")
        require(setting in legacy_system, f"NeoForge source lacks expected setting {setting}")
        require(setting in fabric_system, f"Fabric source lacks expected setting {setting}")

    require(
        "Pufferfish.PufferfishPuffGoal, ConfigMain.CONFIG.removeFishFollowLeader" in legacy_system,
        "Expected original NeoForge puff-setting wiring changed; review parity notes",
    )
    require(
        "Pufferfish.PufferfishPuffGoal, ConfigMain.CONFIG.removeFishPuff" in fabric_system,
        "Fabric pufferfish puff goal is not controlled by removeFishPuff",
    )
    require(
        "Squid.SquidFleeGoal, ConfigMain.CONFIG.removeRandomMove" in legacy_system,
        "Expected original NeoForge squid-flee wiring changed; review parity notes",
    )
    require(
        "Squid.SquidFleeGoal, ConfigMain.CONFIG.removeSquidFlee" in fabric_system,
        "Fabric squid flee goal is not controlled by removeSquidFlee",
    )


def check_configuration_coverage() -> None:
    config = read(FABRIC / "ConfigMain.java")

    require(
        "isAllowlist.get() != filterList.get().contains(entityId)" in config,
        "Allowlist/denylist decision changed",
    )

    defaults = (
        "allowRemoveCalls = bool(true)",
        "enableCallBubbling = bool(true)",
        "removeLookGoal = filtered(false)",
        "removeLookRandom = filtered(false)",
        "replaceLookController = filtered(true)",
        "removeFishSwim = bool(false)",
        "removeFishAvoidPlayer = bool(false)",
        "removeFishPanic = bool(false)",
        "removeFishFollowLeader = bool(false)",
        "removeFishPuff = bool(false)",
        "removeSquidFlee = bool(false)",
        "removeRandomMove = bool(false)",
        "removeSheepEatBlock = bool(false)",
    )
    for default in defaults:
        require(default in config, f"Default configuration value missing: {default}")

    boolean_paths = (
        "entity.general.allow_remove_calls",
        "entity.general.enable_call_bubbling",
        "entity.fish.remove_swim",
        "entity.fish.remove_panic",
        "entity.fish.remove_avoid_player",
        "entity.fish.remove_follow_leader",
        "entity.fish.remove_puff",
        "entity.squid.remove_flee",
        "entity.squid.remove_random_move",
        "entity.sheep.remove_eat_block",
    )
    for path in boolean_paths:
        require(f'"{path}"' in config, f"Configuration load/save path missing: {path}")

    filtered_paths = (
        "entity.mob.remove_look_goal",
        "entity.mob.remove_look_random",
        "entity.mob.replace_look_controller",
    )
    for path in filtered_paths:
        # ConfigMain constructs these nested paths by appending suffixes to
        # the base path, rather than embedding the full JSON path as a literal.
        require('path + ".enabled"' in config, f"Filtered setting enabled path builder missing: {path}")
        require('path + ".is_allowlist"' in config, f"Filtered allowlist path builder missing: {path}")
        require('path + ".filter_list"' in config, f"Filtered list path builder missing: {path}")

    for animal in ("cow", "chicken", "pig", "sheep"):
        require(f'"entity.{animal}"' in config, f"Animal config {animal} is not loaded/saved")
    for suffix in (
        "remove_float", "remove_panic", "remove_breed",
        "remove_tempt", "remove_follow_parent", "remove_stroll",
    ):
        require(f'path + ".{suffix}"' in config,
                f"Animal config path builder missing: {suffix}")
    require("bool(false), bool(false), bool(false)" in config,
            "Animal option defaults no longer initialize to false")


def check_runtime_wiring() -> None:
    look = read(FABRIC / "FixedLookControl.java")
    require("FastTrig.atan2(a, b)" in look, "FixedLookControl no longer uses FastTrig")
    require("57.2957763671875D" in look and "- 90.0F" in look,
            "FixedLookControl angle conversion changed")
    for field in ("wantedX", "wantedY", "wantedZ", "lookAtCooldown", "xMaxRotAngle", "yMaxRotSpeed"):
        require(field in look, f"FixedLookControl no longer copies {field}")
    require("if (oldHelper != null)" in read(FABRIC / "modifier/ModifierSystem.java"),
            "FixedLookControl state copy must guard a null old controller")

    initializer = read(FABRIC / "AIImprovementsFabric.java")
    require("FastTrig.init()" in initializer, "FastTrig cache is not initialized")
    require("ConfigMain.load()" in initializer, "Fabric configuration is not loaded at startup")
    require("ModifierSystem.init()" in initializer, "Modifier system is not initialized")
    require("FabricEntityLifecycle.register()" in initializer, "Fabric lifecycle callback is not registered")

    lifecycle = read(FABRIC / "fabric/FabricEntityLifecycle.java")
    require("ServerEntityEvents.ALLOW_LOAD" in lifecycle,
            "Fabric must use the pre-insertion ALLOW_LOAD event")
    require("ServerEntityEvents.ENTITY_LOAD" not in lifecycle,
            "Post-insertion ENTITY_LOAD is not the selected parity hook")
    require("ModifierSystem.handleEntityJoinLevel(entity, level)" in lifecycle,
            "Entity-load callback is not connected to ModifierSystem")
    require("return true;" in lifecycle, "ALLOW_LOAD callback must allow entity insertion")


def check_pipeline_structure() -> None:
    modifier_layer = read(FABRIC / "modifier/editor/ModifierLayer.java")
    filter_layer = read(FABRIC / "modifier/filters/FilterLayer.java")
    require("ConfigMain.CONFIG.enableCallBubbling.get()" in modifier_layer,
            "Modifier call statistics are not gated by enableCallBubbling")
    require("Comparator.comparingInt((ModifierNode node) -> node.callCount).reversed()" in modifier_layer,
            "ModifierLayer overflow ordering is not hottest-first")
    require("Comparator.comparingInt((FilterNode node) -> node.callCount).reversed()" in filter_layer,
            "FilterLayer overflow ordering is not hottest-first")
    require("callCount >>>= 1" in modifier_layer and "callCount >>>= 1" in filter_layer,
            "Overflow normalization no longer preserves relative counter magnitude")
    require("if (size > 1)" in modifier_layer and "if (size > 1)" in filter_layer,
            "Single-node counter normalization must not be skipped")
    require("goalsToRemove.add(goal)" in modifier_layer and "selector.removeGoal(goal)" in modifier_layer,
            "Goal removals must remain deferred until after selector iteration")


def check_fabric_metadata() -> None:
    metadata = json.loads(read(ROOT / "src/main/resources/fabric.mod.json"))
    require(metadata.get("id") == "aiimprovements", "Wrong Fabric mod id")
    require(metadata.get("depends", {}).get("minecraft") == "26.3",
            "Fabric metadata targets a different Minecraft version")
    require(metadata.get("depends", {}).get("java") == ">=25",
            "Fabric metadata has the wrong Java requirement")
    require(metadata.get("accessWidener") == "aiimprovements.classtweaker",
            "Fabric metadata does not register the Class Tweaker resource")
    require(
        "com.builtbroken.ai.improvements.AIImprovementsFabric"
        in metadata.get("entrypoints", {}).get("main", []),
        "Fabric initializer entrypoint is missing",
    )

    tweaker = read(ROOT / "src/main/resources/aiimprovements.classtweaker")
    require(tweaker.startswith("classTweaker v1 official"),
            "Class Tweaker header must use the 26.3 official namespace")
    for target in (
        "GoalSelector availableGoals",
        "Mob goalSelector",
        "Mob targetSelector",
        "Mob lookControl",
        "LookControl xMaxRotAngle",
        "LookControl yMaxRotSpeed",
        "LookControl lookAtCooldown",
        "Squid$SquidRandomMovementGoal",
        "Squid$SquidFleeGoal",
        "Pufferfish$PufferfishPuffGoal",
    ):
        require(target in tweaker, f"Missing Class Tweaker target: {target}")

    build = read(ROOT / "build.gradle")
    require('accessWidenerPath = file("src/main/resources/aiimprovements.classtweaker")' in build,
            "Loom does not point to the Class Tweaker resource")
    require('tasks.register("buildFabricPort")' in build,
            "Dedicated non-test Fabric package task is missing")
    require('dependsOn("validateAccessWidener", "build")' in build,
            "Fabric package task must validate and remap without invoking GameTests")
    require('tasks.register("prodServerSmoke", net.fabricmc.loom.task.prod.ServerProductionRunTask)' in build,
            "Production dedicated-server smoke task is missing")
    require('mods.from(tasks.named("jar").flatMap { it.archiveFile })' in build,
            "Production smoke must run the remapped distribution JAR")
    require('productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:${project.fabric_api_version}"' in build,
            "Production smoke launcher must include the declared Fabric API mod dependency")
    require("outputs.upToDateWhen { false }" in build,
            "Production smoke phases must relaunch the server instead of being skipped as UP-TO-DATE")
    smoke = read(ROOT / "tools/run_prod_server_smoke.py")
    for marker in (
        "MOB_GROUPS",
        "AI_TICK_WINDOW_SECONDS = 20",
        "EXPECTED_MOBS_PER_TYPE = 12",
        "population_check_commands",
        'f"AIIMP_{prefix}_{name.upper()}_COUNT_OK"',
        "AIIMP_ACTIVE_AI_TICK_WINDOW_STARTED",
        "AIIMP_ACTIVE_AI_TICK_WINDOW_COMPLETE",
        "mode=\"reload\"",
        "PersistenceRequired:1b",
        "minecraft:stone",
        "minecraft:glass",
        "fill 17 80 1 30 82 14 minecraft:water",
        "forceload add 1 0",
    ):
        require(marker in smoke, f"Production server smoke coverage missing: {marker}")
    require("NoAI:1b" not in smoke,
            "Production server mobs must run their normal AI goals during the tick window")
    for entity_name in ("cow", "chicken", "pig", "sheep", "salmon", "squid", "pufferfish"):
        require(f'"{entity_name}", "minecraft:{entity_name}"' in smoke,
                f"Production server population is missing {entity_name}")
        require(f"AIIMP_PERSIST_{entity_name.upper()}" in smoke,
                f"Production server persistence tag is missing {entity_name}")


    client_build = read(ROOT / "build.gradle")
    require('tasks.register("prodClientSmoke", net.fabricmc.loom.task.prod.ClientProductionRunTask)' in client_build,
            "Production client smoke task is missing")
    require("useXVFB.set(false)" in client_build,
            "Client smoke must use the outer CI Xvfb display")
    client_smoke = read(ROOT / "tools/run_prod_client_smoke.py")
    for marker in (
        "AI Improvements Fabric 26.3 experimental port initialized",
        "Backend library: LWJGL version",
        "Reloading ResourceManager",
        "GRAPHICS_BACKEND_MARKERS",
        "Using graphics backend OpenGL",
        "Using graphics backend Vulkan",
        "Failed to create backend Vulkan",
        "aiimprovements.json",
    ):
        require(marker in client_smoke, f"Production client smoke coverage missing: {marker}")

    workflow = read(ROOT / ".github/workflows/build.yml")
    require("./gradlew --no-daemon validateAccessWidener build" in workflow,
            "GitHub Actions must compile, execute the configured GameTests and build the mod")
    require("execute GameTests" in workflow,
            "GitHub Actions validation step must explicitly declare GameTests")
    require(not (ROOT / "src/tools").exists(),
            "Pre-26.3 utility code must remain outside the active src tree")


def check_source_parity() -> None:
    check_source_inventory()
    check_goal_mapping()
    check_configuration_coverage()
    check_runtime_wiring()
    check_pipeline_structure()
    check_fabric_metadata()


def check_jar(path: Path) -> None:
    require(path.is_file(), f"JAR does not exist: {path}")
    with zipfile.ZipFile(path) as jar:
        names = set(jar.namelist())
        require("fabric.mod.json" in names, "Built JAR is missing fabric.mod.json")
        require("aiimprovements.classtweaker" in names, "Built JAR is missing Class Tweaker file")

        metadata = json.loads(jar.read("fabric.mod.json").decode("utf-8"))
        require(metadata.get("id") == "aiimprovements", "Built JAR has the wrong mod id")
        require(metadata.get("depends", {}).get("minecraft") == "26.3",
                "Built JAR targets a different Minecraft version")
        require(metadata.get("depends", {}).get("java") == ">=25",
                "Built JAR has the wrong Java requirement")

        required_classes = (
            "com/builtbroken/ai/improvements/AIImprovementsFabric.class",
            "com/builtbroken/ai/improvements/modifier/ModifierSystem.class",
            "com/builtbroken/ai/improvements/modifier/ModifierLevel.class",
            "com/builtbroken/ai/improvements/modifier/editor/ModifierLayer.class",
            "com/builtbroken/ai/improvements/modifier/filters/FilterLayer.class",
            "com/builtbroken/ai/improvements/FixedLookControl.class",
            "com/builtbroken/ai/improvements/FastTrig.class",
        )
        for entry in required_classes:
            require(entry in names, f"Built JAR is missing {entry}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="Optional compiled remapped Fabric JAR to inspect")
    args = parser.parse_args()

    check_source_parity()
    print("PASS: source inventory, configuration, wiring and Fabric structure")

    if args.jar:
        check_jar(args.jar)
        print(f"PASS: Fabric JAR metadata and classes ({args.jar.name})")

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AssertionError, OSError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
