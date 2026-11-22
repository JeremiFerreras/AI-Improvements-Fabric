#!/usr/bin/env python3
"""Production-launcher smoke test for the packaged Fabric mod JAR.

The Fabric server launcher performs a first-run install and writes eula.txt.
This script accepts that bootstrap phase, enables the EULA, launches a real
server, summons representative mobs, saves and shuts down, then reopens the
same world to verify the entities can be loaded back from disk.
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

SUMMON_COMMANDS = (
    "forceload add 0 0",
    "fill -1 79 -1 15 82 1 minecraft:water",
    "summon minecraft:cow 0 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:chicken 2 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:pig 4 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:sheep 6 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:salmon 8 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:squid 10 80 0 {NoAI:1b,PersistenceRequired:1b}",
    "summon minecraft:pufferfish 12 80 0 {NoAI:1b,PersistenceRequired:1b}",
)

RELOAD_CHECK_COMMANDS = (
    ("cow", "execute if entity @e[type=minecraft:cow,limit=1] run say AIIMP_RELOAD_COW"),
    ("chicken", "execute if entity @e[type=minecraft:chicken,limit=1] run say AIIMP_RELOAD_CHICKEN"),
    ("pig", "execute if entity @e[type=minecraft:pig,limit=1] run say AIIMP_RELOAD_PIG"),
    ("sheep", "execute if entity @e[type=minecraft:sheep,limit=1] run say AIIMP_RELOAD_SHEEP"),
    ("salmon", "execute if entity @e[type=minecraft:salmon,limit=1] run say AIIMP_RELOAD_SALMON"),
    ("squid", "execute if entity @e[type=minecraft:squid,limit=1] run say AIIMP_RELOAD_SQUID"),
    ("pufferfish", "execute if entity @e[type=minecraft:pufferfish,limit=1] run say AIIMP_RELOAD_PUFFERFISH"),
)


def prepare_run_directory() -> None:
    RUN_DIR.mkdir(parents=True, exist_ok=True)


def write_server_files(eula_path: Path) -> None:
    eula_path.write_text("eula=true\n", encoding="utf-8")
    properties = {
        "motd": "AI Improvements Fabric production smoke",
        "server-ip": "127.0.0.1",
        "server-port": "25575",
        "online-mode": "false",
        "max-players": "1",
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
                for summon in SUMMON_COMMANDS:
                    send(summon)
                send("save-all flush")
                save_requested = True
                save_deadline = time.monotonic() + 20
            elif mode == "reload":
                send("forceload add 0 0")
                # Allow the persistent chunk ticket to materialize its entities
                # before querying the selectors.
                time.sleep(4)
                for _, reload_command in RELOAD_CHECK_COMMANDS:
                    send(reload_command)
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

    for entity_name, _ in RELOAD_CHECK_COMMANDS:
        marker = f"AIIMP_RELOAD_{entity_name.upper()}"
        if marker.lower() not in combined_log.lower():
            print(f"FAIL: {entity_name} was not found after reopening the saved world", file=sys.stderr)
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
        "Saved the game",
        "AIIMP_RELOAD_COW",
        "AIIMP_RELOAD_CHICKEN",
        "AIIMP_RELOAD_PIG",
        "AIIMP_RELOAD_SHEEP",
        "AIIMP_RELOAD_SALMON",
        "AIIMP_RELOAD_SQUID",
        "AIIMP_RELOAD_PUFFERFISH",
    )
    for marker in required_markers:
        if marker.lower() not in combined_log.lower():
            print(f"FAIL: production log is missing marker: {marker}", file=sys.stderr)
            return 1

    bad_markers = (
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
    print("PASS: seven representative mob entity types were summoned")
    print("PASS: configuration JSON was generated with expected defaults")
    print("PASS: world data was saved and the server shut down")
    print("PASS: second production server startup reopened the saved world")
    print("PASS: all seven entity types were found after disk reload")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
