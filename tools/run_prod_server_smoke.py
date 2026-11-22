#!/usr/bin/env python3
"""Production-launcher smoke test for the remapped Fabric mod JAR.

Starts a real Fabric dedicated-server distribution, waits for the world to be
ready, summons representative mobs through the server console, flushes world
saves and shuts the server down cleanly. This is not a substitute for a client
session or a long-duration modpack compatibility test.
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
    "summon minecraft:cow 0 80 0",
    "summon minecraft:chicken 2 80 0",
    "summon minecraft:pig 4 80 0",
    "summon minecraft:sheep 6 80 0",
    "summon minecraft:salmon 8 80 0",
    "summon minecraft:squid 10 80 0",
    "summon minecraft:pufferfish 12 80 0",
)


def write_server_files() -> None:
    RUN_DIR.mkdir(parents=True, exist_ok=True)
    (RUN_DIR / "eula.txt").write_text("eula=true\n", encoding="utf-8")
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


def main() -> int:
    write_server_files()

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

    lines: list[str] = []
    output_queue: queue.Queue[str | None] = queue.Queue()
    reader = threading.Thread(
        target=pump_lines, args=(process.stdout, output_queue), daemon=True
    )
    reader.start()

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
            # A stop still triggers a final synchronous world save.
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
            for summon in SUMMON_COMMANDS:
                send(summon)
            send("save-all flush")
            save_requested = True
            save_deadline = time.monotonic() + 20

        if ready and save_requested and not stop_requested:
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
    combined_log = "".join(lines)
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    LOG_FILE.write_text(combined_log, encoding="utf-8")

    if failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1
    if return_code != 0:
        print(f"FAIL: production server Gradle task exited with {return_code}", file=sys.stderr)
        return 1
    if not ready:
        print("FAIL: Fabric dedicated server never reached the ready state", file=sys.stderr)
        return 1
    if not stop_requested:
        print("FAIL: server did not confirm a clean save/stop sequence", file=sys.stderr)
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

    print("PASS: production Fabric dedicated-server startup")
    print("PASS: AI Improvements initialized with the remapped release JAR")
    print("PASS: seven representative mob entity types were summoned")
    print("PASS: configuration JSON was generated with expected defaults")
    print("PASS: world data was saved and the server shut down")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
