#!/usr/bin/env python3
"""Headless production-client startup smoke test for the packaged Fabric mod.

The CI job starts this script inside xvfb-run. The client is allowed to reach
LWJGL initialization and resource reload, then its process group is stopped
so the smoke check cannot hang waiting for a human to close the title screen.
This is not a test of GPU-specific rendering or interactive gameplay.
"""

from __future__ import annotations

import json
import os
import queue
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RUN_DIR = ROOT / "build" / "production-client-smoke"
LOG_FILE = ROOT / "build" / "production-client-smoke.log"
START_TIMEOUT_SECONDS = 360
READY_HOLD_SECONDS = 8
STOP_TIMEOUT_SECONDS = 20

REQUIRED_MARKERS = (
    "AI Improvements Fabric 26.3 experimental port initialized",
    "Backend library: LWJGL version",
    "Reloading ResourceManager",
)

# Minecraft 26.3 may reject the OpenGL backend on a virtual display and
# successfully fall back to Vulkan/llvmpipe. Accept either selected backend.
GRAPHICS_BACKEND_MARKERS = (
    "Using graphics backend OpenGL",
    "Using graphics backend Vulkan",
)

FATAL_MARKERS = (
    "NoSuchFieldError",
    "NoSuchMethodError",
    "NoClassDefFoundError",
    "Mixin apply failed",
    "Exception in thread",
    "Failed to create backend Vulkan",
)


def pump_lines(stream, output_queue: queue.Queue[str | None]) -> None:
    try:
        for line in stream:
            output_queue.put(line)
    finally:
        output_queue.put(None)


def stop_process_group(process: subprocess.Popen[str]) -> int:
    """Send SIGTERM to the Gradle/client process group, escalating if needed."""
    if process.poll() is None:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass

    try:
        return process.wait(timeout=STOP_TIMEOUT_SECONDS)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(process.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        return process.wait(timeout=10)


def main() -> int:
    if os.name != "posix":
        print("FAIL: the headless client smoke requires the Linux/Xvfb CI runner", file=sys.stderr)
        return 1

    display = os.environ.get("DISPLAY")
    if not display:
        print("FAIL: DISPLAY is missing; launch this script through xvfb-run", file=sys.stderr)
        return 1

    RUN_DIR.mkdir(parents=True, exist_ok=True)

    wrapper = "./gradlew"
    command = [
        wrapper,
        "--no-daemon",
        "--console=plain",
        "prodClientSmoke",
    ]

    process = subprocess.Popen(
        command,
        cwd=ROOT,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
        encoding="utf-8",
        errors="replace",
        bufsize=1,
        start_new_session=True,
    )

    output_queue: queue.Queue[str | None] = queue.Queue()
    reader = threading.Thread(
        target=pump_lines, args=(process.stdout, output_queue), daemon=True
    )
    reader.start()

    lines: list[str] = []
    found: set[str] = set()
    started_at = time.monotonic()
    ready_at: float | None = None
    selected_graphics_backend: str | None = None
    stream_closed = False
    failure: str | None = None

    while not stream_closed:
        now = time.monotonic()
        if ready_at is None and now - started_at > START_TIMEOUT_SECONDS:
            failure = f"Production client did not reach the ready markers within {START_TIMEOUT_SECONDS}s"
            break
        if ready_at is not None and now - ready_at >= READY_HOLD_SECONDS:
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

        for marker in REQUIRED_MARKERS:
            if marker.lower() in line.lower():
                found.add(marker)

        for marker in GRAPHICS_BACKEND_MARKERS:
            if marker.lower() in line.lower():
                selected_graphics_backend = marker.removeprefix("Using graphics backend ")

        for marker in FATAL_MARKERS:
            if marker.lower() in line.lower():
                failure = f"Fatal client startup marker found: {line.strip()}"
                break
        if failure:
            break

        if len(found) == len(REQUIRED_MARKERS) and selected_graphics_backend is not None and ready_at is None:
            ready_at = time.monotonic()
            print("Production client reached LWJGL/resource initialization; holding briefly before controlled shutdown.")

    return_code = stop_process_group(process)
    reader.join(timeout=3)

    combined_log = "".join(lines)
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    LOG_FILE.write_text(combined_log, encoding="utf-8")

    if failure:
        print(f"FAIL: {failure}", file=sys.stderr)
        return 1

    missing = [marker for marker in REQUIRED_MARKERS if marker not in found]
    if missing:
        print("FAIL: production client is missing startup markers: " + ", ".join(missing), file=sys.stderr)
        return 1
    if selected_graphics_backend is None:
        print("FAIL: client did not select an OpenGL or Vulkan graphics backend", file=sys.stderr)
        return 1

    config_path = RUN_DIR / "config" / "aiimprovements.json"
    if not config_path.is_file():
        print("FAIL: client did not create config/aiimprovements.json", file=sys.stderr)
        return 1

    try:
        config = json.loads(config_path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        print(f"FAIL: client-generated configuration is invalid JSON: {error}", file=sys.stderr)
        return 1

    enabled = (
        config.get("entity", {})
        .get("mob", {})
        .get("replace_look_controller", {})
        .get("enabled")
    )
    if enabled is not True:
        print("FAIL: client config does not preserve the default LookControl setting", file=sys.stderr)
        return 1

    print("PASS: production Fabric client loaded the packaged mod")
    print("PASS: LWJGL and resource manager initialized under Xvfb")
    print(f"PASS: client selected graphics backend {selected_graphics_backend}")
    print("PASS: client generated a valid AI Improvements configuration")
    print(f"PASS: client process group was stopped after startup smoke (exit={return_code})")
    print("NOTE: this does not validate physical GPU rendering or interactive gameplay")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
