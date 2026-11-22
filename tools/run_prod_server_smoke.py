#!/usr/bin/env python3
"""Production-launcher smoke test for the packaged Fabric mod JAR.

The script bootstraps the actual production server, then runs 84 persistent,
AI-enabled mobs across seven representative entity types inside bounded land
and water pens. It lets their AI tick, counts each population, saves and stops
the server, then reopens the same world and verifies all populations survived
disk loading. This is a bounded CI smoke, not a long-duration benchmark.
"""

from __future__ import annotations

import json
import os
import queue
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN_DIR = ROOT / "build" / "production-server-smoke"
LOG_FILE = ROOT / "build" / "production-server-smoke.log"
START_TIMEOUT_SECONDS = 240
STOP_TIMEOUT_SECONDS = 90

AI_TICK_WINDOW_SECONDS = 60
POST_CHUNK_RELOAD_AI_TICK_SECONDS = 30
CHUNK_UNLOAD_WAIT_SECONDS = 12
CHUNK_RELOAD_WAIT_SECONDS = 5
COMMAND_PACING_SECONDS = 0.05
EXPECTED_MOBS_PER_TYPE = 12

# Each species gets its own fully enclosed 16x16 room and force-loaded chunk.
# This keeps terrestrial groups apart and avoids accidental entity cramming.
SCOREBOARD_COMMANDS = (
    "scoreboard objectives add AIIMP_COUNT dummy",
    "scoreboard objectives add AIIMP_X0 dummy",
    "scoreboard objectives add AIIMP_Z0 dummy",
    "scoreboard objectives add AIIMP_X1 dummy",
    "scoreboard objectives add AIIMP_Z1 dummy",
    "scoreboard objectives add AIIMP_MOVED dummy",
)

def mob_grid(x_start: int, z_start: int, y: int) -> tuple[tuple[int, int, int], ...]:
    return tuple(
        (x_start + column * 2, y, z_start + row * 2)
        for row in range(3)
        for column in range(4)
    )


MOB_GROUPS = (
    ("cow", "minecraft:cow", "AIIMP_PERSIST_COW", mob_grid(131, 3, 80)),
    ("chicken", "minecraft:chicken", "AIIMP_PERSIST_CHICKEN", mob_grid(147, 3, 80)),
    ("pig", "minecraft:pig", "AIIMP_PERSIST_PIG", mob_grid(163, 3, 80)),
    ("sheep", "minecraft:sheep", "AIIMP_PERSIST_SHEEP", mob_grid(179, 3, 80)),
    ("salmon", "minecraft:salmon", "AIIMP_PERSIST_SALMON", mob_grid(195, 3, 81)),
    ("squid", "minecraft:squid", "AIIMP_PERSIST_SQUID", mob_grid(211, 3, 81)),
    ("pufferfish", "minecraft:pufferfish", "AIIMP_PERSIST_PUFFERFISH", mob_grid(227, 3, 81)),
)


def pen_setup_commands() -> tuple[str, ...]:
    commands = []
    for _, entity_type, _, positions in MOB_GROUPS:
        chunk_x = (positions[0][0] // 16) * 16
        commands.extend((
            f"forceload add {chunk_x} 0",
            f"fill {chunk_x} 79 0 {chunk_x + 15} 79 15 minecraft:stone",
            f"fill {chunk_x} 80 0 {chunk_x} 82 15 minecraft:glass",
            f"fill {chunk_x + 15} 80 0 {chunk_x + 15} 82 15 minecraft:glass",
            f"fill {chunk_x + 1} 80 0 {chunk_x + 14} 82 0 minecraft:glass",
            f"fill {chunk_x + 1} 80 15 {chunk_x + 14} 82 15 minecraft:glass",
            f"fill {chunk_x + 1} 83 1 {chunk_x + 14} 83 14 minecraft:glass",
        ))
        if entity_type in ("minecraft:salmon", "minecraft:squid", "minecraft:pufferfish"):
            commands.append(
                f"fill {chunk_x + 1} 80 1 {chunk_x + 14} 82 14 minecraft:water"
            )
    commands.extend(SCOREBOARD_COMMANDS)
    return tuple(commands)


def force_load_commands() -> tuple[str, ...]:
    return tuple(
        f"forceload add {(positions[0][0] // 16) * 16} 0"
        for _, _, _, positions in MOB_GROUPS
    )


def summon_commands() -> tuple[str, ...]:
    commands = []
    for _, entity_type, tag, positions in MOB_GROUPS:
        for x, y, z in positions:
            commands.append(
                f'summon {entity_type} {x} {y} {z} '
                f'{{PersistenceRequired:1b,Tags:["{tag}"]}}'
            )
    return tuple(commands)


def population_check_commands(prefix: str) -> tuple[str, ...]:
    commands = []
    for name, entity_type, tag, _ in MOB_GROUPS:
        fake_player = f"#{name}"
        commands.extend((
            f"scoreboard players set {fake_player} AIIMP_COUNT 0",
            f"execute as @e[type={entity_type},tag={tag}] "
            f"run scoreboard players add {fake_player} AIIMP_COUNT 1",
            f"execute if score {fake_player} AIIMP_COUNT "
            f"matches {EXPECTED_MOBS_PER_TYPE}.. run say AIIMP_{prefix}_{name.upper()}_COUNT_OK",
        ))
    return tuple(commands)


def movement_snapshot_commands() -> tuple[str, ...]:
    """Capture each mob's starting X/Z coordinates in entity scoreboard values."""
    commands = []
    for _, entity_type, tag, _ in MOB_GROUPS:
        commands.extend((
            f"execute as @e[type={entity_type},tag={tag}] "
            "store result score @s AIIMP_X0 run data get entity @s Pos[0] 1000",
            f"execute as @e[type={entity_type},tag={tag}] "
            "store result score @s AIIMP_Z0 run data get entity @s Pos[2] 1000",
        ))
    return tuple(commands)


def post_reload_stimulus_commands() -> tuple[str, ...]:
    """Trigger the normal PanicGoal on farm animals after their chunks reload.

    Random strolling is intentionally probabilistic and can leave a healthy
    group stationary during a short sample. On-fire is a normal PanicGoal
    trigger; Invulnerable prevents the diagnostic stimulus from killing mobs.
    """
    return tuple(
        f"execute as @e[type={entity_type},tag={tag}] "
        # Fire is measured in ticks: 1,200 keeps PanicGoal's normal
        # on-fire trigger active throughout the 30-second observation window.
        "run data merge entity @s {Fire:1200s,Invulnerable:1b}"
        for _, entity_type, tag, _ in MOB_GROUPS[:4]
    )


def movement_check_commands(prefix: str) -> tuple[str, ...]:
    """Require at least one real horizontal position change in every species group."""
    commands = []
    for name, entity_type, tag, _ in MOB_GROUPS:
        fake_player = f"#{name}"
        selector = f"@e[type={entity_type},tag={tag}]"
        commands.extend((
            f"scoreboard players set {fake_player} AIIMP_MOVED 0",
            f"execute as {selector} store result score @s AIIMP_X1 "
            "run data get entity @s Pos[0] 1000",
            f"execute as {selector} store result score @s AIIMP_Z1 "
            "run data get entity @s Pos[2] 1000",
            f"execute as {selector} unless score @s AIIMP_X0 = @s AIIMP_X1 "
            f"run scoreboard players add {fake_player} AIIMP_MOVED 1",
            f"execute as {selector} unless score @s AIIMP_Z0 = @s AIIMP_Z1 "
            f"run scoreboard players add {fake_player} AIIMP_MOVED 1",
            f"execute if score {fake_player} AIIMP_MOVED matches 1.. "
            f"run say AIIMP_{prefix}_{name.upper()}_MOVEMENT_OK",
        ))
    return tuple(commands)


def unloaded_check_commands() -> tuple[str, ...]:
    return tuple(
        f"execute unless entity @e[type={entity_type},tag={tag},limit=1] "
        f"run say AIIMP_UNLOADED_{name.upper()}_OK"
        for name, entity_type, tag, _ in MOB_GROUPS
    )


def prepare_run_directory() -> None:
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    # Pre-seed configuration files so the launcher's bootstrap does not report
    # missing-file errors; the first server run will enforce the EULA gate.
    eula = RUN_DIR / "eula.txt"
    if not eula.exists():
        eula.write_text("eula=false\n", encoding="utf-8")
    properties = RUN_DIR / "server.properties"
    if not properties.exists():
        write_server_properties()


def write_server_properties() -> None:
    properties = {
        "motd": "AI Improvements Fabric production smoke",
        "server-ip": "127.0.0.1",
        "server-port": "25575",
        "online-mode": "false",
        "max-players": "1",
        # CI has no player client connected. Disable Minecraft's default
        # 60-second empty-server pause so the AI stress window keeps ticking.
        "pause-when-empty-seconds": "0",
        "spawn-protection": "0",
        "view-distance": "4",
        "simulation-distance": "4",
        "level-name": "world",
        "level-type": "minecraft:normal",
        "difficulty": "normal",
        "gamemode": "survival",
        "generate-structures": "false",
        "enable-command-block": "false",
    }
    (RUN_DIR / "server.properties").write_text(
        "".join(f"{key}={value}\n" for key, value in properties.items()),
        encoding="utf-8",
    )


def write_server_files(eula_path: Path) -> None:
    eula_path.write_text("eula=true\n", encoding="utf-8")
    write_server_properties()


def pump_lines(stream, output_queue: queue.Queue[str | None]) -> None:
    try:
        for line in stream:
            output_queue.put(line)
    finally:
        output_queue.put(None)


def run_server_phase(mode: str) -> tuple[int, bool, bool, list[str], str | None]:
    if mode not in {"bootstrap", "spawn", "reload"}:
        raise ValueError(f"Unsupported production server phase: {mode}")
    wrapper = "gradlew.bat" if os.name == "nt" else "./gradlew"
    command = [
        wrapper,
        "--no-daemon",
        "--console=plain",
        "prodServerSmoke",
    ]

    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
    )

    output_queue: queue.Queue[str | None] = queue.Queue()
    reader = threading.Thread(
        target=pump_lines, args=(process.stdout, output_queue), daemon=True
    )
    reader.start()

    lines: list[str] = []
    started_at = time.monotonic()
    ready = False
    save_requested = False
    stop_requested = False
    stream_closed = False
    save_deadline: float | None = None
    ai_tick_deadline: float | None = None
    chunk_unload_deadline: float | None = None
    chunk_reload_deadline: float | None = None
    post_chunk_reload_ai_deadline: float | None = None
    failure: str | None = None

    def send(command_text: str) -> None:
        if process.stdin is None or process.poll() is not None:
            raise RuntimeError("Dedicated server process is no longer accepting commands")
        process.stdin.write(command_text + "\n")
        process.stdin.flush()

    while not stream_closed:
        now = time.monotonic()
        if not ready and now - started_at > START_TIMEOUT_SECONDS:
            failure = f"Server did not become ready within {START_TIMEOUT_SECONDS}s"
            break
        if ai_tick_deadline is not None and now >= ai_tick_deadline and not save_requested:
            send("say AIIMP_ACTIVE_AI_TICK_WINDOW_COMPLETE")
            for count_command in population_check_commands("ACTIVE"):
                send(count_command)
            for movement_command in movement_check_commands("ACTIVE"):
                send(movement_command)
            send("forceload remove all")
            send("say AIIMP_CHUNK_UNLOAD_REQUESTED")
            chunk_unload_deadline = time.monotonic() + CHUNK_UNLOAD_WAIT_SECONDS
            ai_tick_deadline = None

        if chunk_unload_deadline is not None and now >= chunk_unload_deadline and not save_requested:
            # These entity selectors only see loaded entities. Their success
            # markers therefore prove the seven remote pens really unloaded.
            for unload_check in unloaded_check_commands():
                send(unload_check)
            send("say AIIMP_CHUNK_UNLOAD_CHECK_COMPLETE")
            for force_load in force_load_commands():
                send(force_load)
            send("say AIIMP_CHUNK_RELOAD_REQUESTED")
            chunk_reload_deadline = time.monotonic() + CHUNK_RELOAD_WAIT_SECONDS
            chunk_unload_deadline = None

        if chunk_reload_deadline is not None and now >= chunk_reload_deadline and not save_requested:
            for count_command in population_check_commands("CHUNK_RELOAD"):
                send(count_command)
            for movement_command in movement_snapshot_commands():
                send(movement_command)
            for stimulus_command in post_reload_stimulus_commands():
                send(stimulus_command)
                time.sleep(COMMAND_PACING_SECONDS)
            send("say AIIMP_POST_CHUNK_RELOAD_PANIC_STIMULUS_APPLIED")
            send("say AIIMP_POST_CHUNK_RELOAD_AI_WINDOW_STARTED")
            post_chunk_reload_ai_deadline = time.monotonic() + POST_CHUNK_RELOAD_AI_TICK_SECONDS
            chunk_reload_deadline = None

        if (post_chunk_reload_ai_deadline is not None
                and now >= post_chunk_reload_ai_deadline and not save_requested):
            send("say AIIMP_POST_CHUNK_RELOAD_AI_WINDOW_COMPLETE")
            for count_command in population_check_commands("POST_CHUNK_RELOAD"):
                send(count_command)
            for movement_command in movement_check_commands("POST_CHUNK_RELOAD"):
                send(movement_command)
            send("save-all flush")
            save_requested = True
            save_deadline = time.monotonic() + 20
            post_chunk_reload_ai_deadline = None

        if save_deadline is not None and now > save_deadline and not stop_requested:
            send("stop")
            stop_requested = True
        if ready and now - started_at > START_TIMEOUT_SECONDS + STOP_TIMEOUT_SECONDS:
            failure = "Dedicated server did not shut down within the timeout"
            break

        try:
            line = output_queue.get(timeout=0.5)
        except queue.Empty:
            if process.poll() is not None and output_queue.empty():
                break
            continue

        if line is None:
            stream_closed = True
            continue

        lines.append(line)
        print(line, end="", flush=True)

        lower = line.lower()
        if "done (" in lower and not ready:
            ready = True
            if mode == "spawn":
                # Pace setup and summon commands so the test harness itself
                # does not create an artificial single-tick command backlog.
                for command_text in pen_setup_commands():
                    send(command_text)
                    time.sleep(COMMAND_PACING_SECONDS)
                for command_text in summon_commands():
                    send(command_text)
                    time.sleep(COMMAND_PACING_SECONDS)
                for movement_command in movement_snapshot_commands():
                    send(movement_command)
                    time.sleep(COMMAND_PACING_SECONDS)
                send("say AIIMP_ACTIVE_AI_TICK_WINDOW_STARTED")
                # Keep the actual dedicated server alive while all 84 mobs
                # execute their normal AI goals inside the force-loaded pens.
                ai_tick_deadline = time.monotonic() + AI_TICK_WINDOW_SECONDS
            elif mode == "reload":
                for force_load in force_load_commands():
                    send(force_load)
                # Allow the persistent chunk tickets to materialize entities
                # before re-counting each saved population.
                time.sleep(4)
                for count_command in population_check_commands("RELOAD"):
                    send(count_command)
                send("save-all flush")
                save_requested = True
                save_deadline = time.monotonic() + 20

        if mode in {"spawn", "reload"} and ready and save_requested and not stop_requested:
            if "saved the game" in lower or "saved the world" in lower:
                send("stop")
                stop_requested = True

        if "exception in thread" in lower or "no such method error" in lower:
            failure = f"Runtime exception detected: {line.strip()}"
            break

    if failure is not None and process.poll() is None:
        process.terminate()

    try:
        return_code = process.wait(timeout=20)
    except subprocess.TimeoutExpired:
        process.kill()
        return_code = process.wait(timeout=10)
        failure = failure or "Gradle/server process had to be force-killed"

    reader.join(timeout=2)
    return return_code, ready, stop_requested, lines, failure


def find_eula_file() -> Path | None:
    expected = RUN_DIR / "eula.txt"
    if expected.is_file():
        return expected
    matches = list(RUN_DIR.rglob("eula.txt"))
    return matches[0] if matches else None


def main() -> int:
    prepare_run_directory()

    # Phase 1: Fabric's executable server launcher installs its files and
    # writes an EULA file before refusing to start the actual game server.
    bootstrap_code, bootstrap_ready, _, bootstrap_lines, bootstrap_failure = run_server_phase(
        mode="bootstrap"
    )
    combined_lines = list(bootstrap_lines)
    bootstrap_log = "".join(bootstrap_lines)

    if bootstrap_failure:
        print(f"FAIL: bootstrap phase failed: {bootstrap_failure}", file=sys.stderr)
        return 1

    if not bootstrap_ready:
        if "eula" not in bootstrap_log.lower():
            print(
                f"FAIL: bootstrap server did not reach ready state or report the EULA gate "
                f"(exit {bootstrap_code})",
                file=sys.stderr,
            )
            return 1

        eula_file = find_eula_file()
        if eula_file is None:
            print("FAIL: Fabric server launcher did not create eula.txt", file=sys.stderr)
            return 1

        write_server_files(eula_file)
        print(f"Production launcher bootstrap complete; accepted EULA at {eula_file}")

    # Phase 2: run the real server after its first-run setup and EULA gate.
    server_code, ready, stop_requested, server_lines, failure = run_server_phase(
        mode="spawn"
    )
    combined_lines.extend(server_lines)
    combined_log = "".join(combined_lines)

    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    LOG_FILE.write_text(combined_log, encoding="utf-8")

    if failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    if server_code != 0:
        print(f"FAIL: production server Gradle task exited with {server_code}", file=sys.stderr)
        return 1
    if not ready:
        print("FAIL: Fabric dedicated server never reached the ready state", file=sys.stderr)
        return 1
    if not stop_requested:
        print("FAIL: server did not confirm a clean save/stop sequence", file=sys.stderr)
        return 1

    # Phase 3: reopen the exact world directory with the packaged distribution
    # JAR and ask the server to find each entity loaded from the saved chunk.
    reload_code, reload_ready, reload_stopped, reload_lines, reload_failure = run_server_phase(
        mode="reload"
    )
    combined_lines.extend(reload_lines)
    combined_log = "".join(combined_lines)
    LOG_FILE.write_text(combined_log, encoding="utf-8")

    if reload_failure:
        print(f"FAIL: disk-reload phase failed: {reload_failure}", file=sys.stderr)
        return 1
    if reload_code != 0 or not reload_ready or not reload_stopped:
        print("FAIL: second production server session did not start, save and stop cleanly", file=sys.stderr)
        return 1

    for entity_name, _, _, _ in MOB_GROUPS:
        marker = f"AIIMP_RELOAD_{entity_name.upper()}_COUNT_OK"
        if marker.lower() not in combined_log.lower():
            print(
                f"FAIL: fewer than {EXPECTED_MOBS_PER_TYPE} {entity_name} entities "
                "survived the world reload",
                file=sys.stderr,
            )
            return 1

    required_markers = (
        "AI Improvements Fabric 26.3 experimental port initialized",
        "Summoned new Cow",
        "Summoned new Chicken",
        "Summoned new Pig",
        "Summoned new Sheep",
        "Summoned new Salmon",
        "Summoned new Squid",
        "Summoned new Pufferfish",
        "AIIMP_ACTIVE_AI_TICK_WINDOW_STARTED",
        "AIIMP_ACTIVE_AI_TICK_WINDOW_COMPLETE",
        "AIIMP_CHUNK_UNLOAD_REQUESTED",
        "AIIMP_CHUNK_UNLOAD_CHECK_COMPLETE",
        "AIIMP_CHUNK_RELOAD_REQUESTED",
        "AIIMP_POST_CHUNK_RELOAD_PANIC_STIMULUS_APPLIED",
        "AIIMP_POST_CHUNK_RELOAD_AI_WINDOW_STARTED",
        "AIIMP_POST_CHUNK_RELOAD_AI_WINDOW_COMPLETE",
        "Saved the game",
    ) + tuple(
        f"AIIMP_ACTIVE_{name.upper()}_COUNT_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_UNLOADED_{name.upper()}_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_CHUNK_RELOAD_{name.upper()}_COUNT_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_POST_CHUNK_RELOAD_{name.upper()}_COUNT_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_ACTIVE_{name.upper()}_MOVEMENT_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_POST_CHUNK_RELOAD_{name.upper()}_MOVEMENT_OK" for name, _, _, _ in MOB_GROUPS
    ) + tuple(
        f"AIIMP_RELOAD_{name.upper()}_COUNT_OK" for name, _, _, _ in MOB_GROUPS
    )
    for marker in required_markers:
        if marker.lower() not in combined_log.lower():
            print(f"FAIL: production log is missing marker: {marker}", file=sys.stderr)
            return 1

    bad_markers = (
        "Server empty for 60 seconds, pausing",
        "NoSuchFieldError",
        "NoSuchMethodError",
        "Mixin apply failed",
        "Could not install FixedLookControl",
        "Exception in thread",
    )
    for marker in bad_markers:
        if marker.lower() in combined_log.lower():
            print(f"FAIL: production log contains {marker}", file=sys.stderr)
            return 1

    config_path = RUN_DIR / "config" / "aiimprovements.json"
    if not config_path.is_file():
        print("FAIL: Fabric server did not create aiimprovements.json", file=sys.stderr)
        return 1

    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"FAIL: generated mod configuration is invalid JSON: {error}", file=sys.stderr)
        return 1

    replace_config = (
        config.get("entity", {})
        .get("mob", {})
        .get("replace_look_controller", {})
    )
    if replace_config.get("enabled") is not True:
        print("FAIL: generated config does not preserve the default LookControl setting", file=sys.stderr)
        return 1

    world_file = RUN_DIR / "world" / "level.dat"
    if not world_file.is_file():
        print("FAIL: dedicated server did not persist a world level.dat", file=sys.stderr)
        return 1

    print("PASS: production Fabric dedicated-server bootstrap and EULA setup")
    print("PASS: production server loaded AI Improvements and Fabric API")
    print(f"PASS: {len(MOB_GROUPS) * EXPECTED_MOBS_PER_TYPE} AI-enabled mobs were spawned in seven independent pens")
    print(f"PASS: all seven entity populations survived {AI_TICK_WINDOW_SECONDS}s of normal AI ticking")
    print("PASS: all seven remote entity chunks unloaded after force-load tickets were removed")
    print("PASS: all seven entity populations survived an in-session chunk reload")
    print(f"PASS: all seven entity populations survived another {POST_CHUNK_RELOAD_AI_TICK_SECONDS}s of AI after chunk reload")
    print("PASS: every species showed real horizontal movement during its AI tick window")
    print("PASS: every species resumed horizontal movement after in-session chunk reload")
    print("PASS: configuration JSON was generated with expected defaults")
    print("PASS: world data was saved and the server shut down")
    print("PASS: second production server startup reopened the saved world")
    print(f"PASS: all seven populations of {EXPECTED_MOBS_PER_TYPE} entities were found after disk reload")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
