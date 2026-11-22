#!/usr/bin/env python3
"""Static parity guard for the NeoForge 26.3 -> Fabric 26.3 port.

This does not replace runtime GameTests. It catches source-level drift between
the preserved NeoForge implementation and the active Fabric port.
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


def check_source_parity() -> None:
    original_fast_trig = LEGACY / "FastTrig.java"
    fabric_fast_trig = FABRIC / "FastTrig.java"
    require(
        original_fast_trig.read_bytes() == fabric_fast_trig.read_bytes(),
        "FastTrig.java diverged from the preserved NeoForge source",
    )

    active_java = list((ROOT / "src/main/java").rglob("*.java"))
    for path in active_java:
        source = path.read_text(encoding="utf-8")
        require("net.neoforged." not in source, f"NeoForge import remains active in {path}")

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
        require(setting in fabric_system, f"Fabric port lacks expected setting {setting}")

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

    look = read(FABRIC / "FixedLookControl.java")
    require("FastTrig.atan2(a, b)" in look, "FixedLookControl no longer uses FastTrig")
    require("57.2957763671875D" in look and "- 90.0F" in look,
            "FixedLookControl angle conversion changed")
    for field in ("wantedX", "wantedY", "wantedZ", "lookAtCooldown", "xMaxRotAngle", "yMaxRotSpeed"):
        require(field in look, f"FixedLookControl no longer copies {field}")

    lifecycle = read(FABRIC / "fabric/FabricEntityLifecycle.java")
    require("ServerEntityEvents.ALLOW_LOAD" in lifecycle,
            "Fabric must use the pre-insertion ALLOW_LOAD event")
    require("ServerEntityEvents.ENTITY_LOAD" not in lifecycle,
            "Post-insertion ENTITY_LOAD is not the selected parity hook")
    require("ModifierSystem.handleEntityJoinLevel(entity, level)" in lifecycle,
            "Entity-load callback is not connected to ModifierSystem")

    config = read(FABRIC / "ConfigMain.java")
    require(
        "isAllowlist.get() != filterList.get().contains(entityId)" in config,
        "Allowlist/denylist decision changed",
    )
    for key in (
        "allowRemoveCalls = bool(true)",
        "enableCallBubbling = bool(true)",
        "removeLookGoal = filtered(false)",
        "removeLookRandom = filtered(false)",
        "replaceLookController = filtered(true)",
        "removeFishPuff = bool(false)",
        "removeSquidFlee = bool(false)",
    ):
        require(key in config, f"Default config value changed or missing: {key}")

    tweaker = read(ROOT / "src/main/resources/aiimprovements.classtweaker")
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
    require("goalsToRemove.add(goal)" in modifier_layer and "selector.removeGoal(goal)" in modifier_layer,
            "Goal removals must remain deferred until after selector iteration")

    game_test = read(ROOT / "src/gametest/java/com/builtbroken/ai/improvements/gametest/AIImprovementsGameTests.java")
    for marker in (
        "goalOptionsOnlyChangeTheirTargetGoals",
        "helper.spawn(",
        "FloatGoal.class",
        "BreedGoal.class",
        "LookAtPlayerGoal.class",
        "Pufferfish.PufferfishPuffGoal.class",
        "RandomSwimmingGoal.class",
        "Squid.SquidFleeGoal.class",
        "Squid.SquidRandomMovementGoal.class",
        "EatBlockGoal.class",
        "RandomLookAroundGoal.class",
        "config.chicken.removeBreed().set(true)",
        "config.pig.removeTempt().set(true)",
        "config.removeFishAvoidPlayer.set(true)",
        "config.removeFishPanic.set(true)",
        "allowRemoveCalls.set(false)",
    ):
        require(marker in game_test, f"GameTest is missing regression case: {marker}")
    require("spawnWithNoFreeWill" not in game_test,
            "Mob behavior tests must spawn entities with AI enabled")
    game_test_metadata = json.loads(read(ROOT / "src/gametest/resources/fabric.mod.json"))
    require(
        "com.builtbroken.ai.improvements.gametest.AIImprovementsGameTests"
        in game_test_metadata.get("entrypoints", {}).get("fabric-gametest", []),
        "GameTest suite is not registered in its test mod metadata",
    )


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
            "com/builtbroken/ai/improvements/modifier/editor/ModifierLayer.class",
            "com/builtbroken/ai/improvements/modifier/filters/FilterLayer.class",
        )
        for entry in required_classes:
            require(entry in names, f"Built JAR is missing {entry}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jar", type=Path, help="Optional compiled remapped Fabric JAR to inspect")
    args = parser.parse_args()

    check_source_parity()
    print("PASS: source-level NeoForge/Fabric behavior mapping")

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
