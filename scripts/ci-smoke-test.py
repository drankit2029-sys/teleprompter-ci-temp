#!/usr/bin/env python3
"""End-to-end smoke test for the Teleprompter debug build on an emulator.

Drives the app through uiautomator dumps (no hardcoded coordinates), verifies
the overlay lifecycle, and fails on any app crash. Screenshots are written to
ci-artifacts/screenshots for the workflow artifact.
"""

import os
import re
import subprocess
import sys
import time

PKG = "com.accessoryTools.teleprompter"
ACTIVITY = f"{PKG}/com.accessoryTools.teleprompter.MainActivity"
OUT = "ci-artifacts/screenshots"
os.makedirs(OUT, exist_ok=True)

ADB = ["adb"]


def run(*args, check=True, binary=False):
    result = subprocess.run(
        list(args),
        capture_output=True,
        text=not binary,
    )
    if check and result.returncode != 0:
        print(result.stdout if not binary else "", result.stderr if not binary else "")
        raise SystemExit(f"command failed: {' '.join(args)}")
    return result.stdout


def dump():
    for _ in range(8):
        run(*ADB, "shell", "rm", "-f", "/sdcard/ui.xml", check=False)
        out = run(
            *ADB,
            "shell",
            "uiautomator",
            "dump",
            "--compressed",
            "/sdcard/ui.xml",
            check=False,
        )
        if "ui.xml" in out or "Hierarchy" in out:
            run(*ADB, "pull", "/sdcard/ui.xml", "/tmp/ui.xml", check=False)
            try:
                with open("/tmp/ui.xml", encoding="utf-8") as handle:
                    return handle.read()
            except FileNotFoundError:
                pass
        else:
            print("dump:", out.strip()[:200])
        time.sleep(2)
    return ""


def find(xml, text):
    for match in re.finditer(r"<node [^>]*>", xml):
        node = match.group(0)
        label = re.search(r'text="([^"]*)"', node)
        bounds = re.search(r'bounds="([^"]*)"', node)
        if label and bounds and label.group(1) == text:
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", bounds.group(1)))
            return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def wait_for(text, timeout=45):
    deadline = time.time() + timeout
    while time.time() < deadline:
        xml = dump()
        point = find(xml, text)
        if point:
            return point
        # Dismiss system hints that can cover the app (immersive-mode "GOT IT",
        # notification permission "Allow") before retrying.
        for hint in ("GOT IT", "Allow"):
            hint_point = find(xml, hint)
            if hint_point:
                tap(hint_point)
                time.sleep(1)
                break
        time.sleep(2)
    label = text.replace(" ", "-").lower()
    shot(f"timeout-{label}")
    with open(f"{OUT}/timeout-{label}.xml", "w", encoding="utf-8") as handle:
        handle.write(dump())
    raise SystemExit(f"timed out waiting for {text!r}")


def tap(point):
    run(*ADB, "shell", "input", "tap", str(point[0]), str(point[1]))


def tap_text(text):
    tap(wait_for(text))


def tap_until(text, next_text, attempts=3, timeout=60, label=None):
    """Tap a control and wait for the next screen, retrying on slow emulators."""
    for attempt in range(attempts):
        point = wait_for(text)
        tap(point)
        deadline = time.time() + timeout
        while time.time() < deadline:
            if find(dump(), next_text):
                return
            time.sleep(2)
        print(f"retry {attempt + 1}: {text!r} did not reach {next_text!r}")
        time.sleep(2)
    shot(f"fail-{label or text.replace(' ', '-').lower()}")
    raise SystemExit(f"timed out waiting for {next_text!r} after tapping {text!r}")


def scroll_until(text, timeout=40):
    deadline = time.time() + timeout
    while time.time() < deadline:
        point = find(dump(), text)
        if point:
            return point
        # Slow swipe avoids a long fling that keeps the UI non-idle for dumps.
        run(*ADB, "shell", "input", "swipe", "540", "1800", "540", "900", "800")
        time.sleep(2)
    label = text.replace(" ", "-").lower()
    shot(f"timeout-{label}")
    raise SystemExit(f"timed out scrolling to {text!r}")


def control_window_y():
    out = run(*ADB, "shell", "dumpsys", "window", "windows")
    for line in out.splitlines():
        if "(wrapxwrap)" in line and "APPLICATION_OVERLAY" in line:
            match = re.search(r"\{\((-?\d+),(-?\d+)\)\(wrapxwrap\)", line)
            if match:
                return int(match.group(2))
    return None


def shot(name):
    with open(f"{OUT}/{name}.png", "wb") as handle:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=handle, check=True)


def overlay_windows():
    out = run(*ADB, "shell", "dumpsys", "window", "windows")
    count = 0
    ours = False
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Window #"):
            ours = PKG in stripped
        elif "ty=APPLICATION_OVERLAY" in stripped and ours:
            count += 1
    return count


def overlay_window_lines():
    out = run(*ADB, "shell", "dumpsys", "window", "windows")
    lines = []
    for line in out.splitlines():
        if "ty=APPLICATION_OVERLAY" in line or "Window #" in line and "accessoryTools" in line:
            lines.append(line.strip())
    return lines


def assert_no_crash():
    out = run(*ADB, "logcat", "-d", "-b", "crash", "-t", "200", check=False)
    if PKG in out and "FATAL EXCEPTION" in out:
        print(out)
        raise SystemExit("app crash detected in logcat")


def main():
    run(*ADB, "shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
    # Suppress the API 31+ immersive-mode confirmation that covers the stage.
    run(*ADB, "shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed", check=False)
    # Pre-grant the notification permission on API 33+ so the system dialog does
    # not block the scripted overlay flow (the app requests it contextually).
    run(*ADB, "shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
    run(*ADB, "shell", "am", "force-stop", PKG)
    run(*ADB, "shell", "am", "start", "-W", "-n", ACTIVITY)
    wait_for("Present", timeout=90)
    shot("01-library")
    print("PASS library")

    tap_until("Present", "Playback", label="present")
    shot("02-stage")
    print("PASS stage")

    tap_until("Window", "Transparent overlay", label="window-tab")
    shot("03-window-tab")
    print("PASS window tab")

    # Overlay disclosure must appear before the system permission screen.
    tap_until("Transparent overlay", "Draw over other apps", label="overlay-disclosure")
    shot("04-overlay-disclosure")
    tap_text("Not now")
    print("PASS overlay disclosure")

    # Grant the overlay app-op the way a user would in system settings.
    run(*ADB, "shell", "appops", "set", PKG, "SYSTEM_ALERT_WINDOW", "allow")
    run(*ADB, "shell", "input", "keyevent", "3")
    time.sleep(1)
    run(*ADB, "shell", "am", "start", "-W", "-n", ACTIVITY)
    wait_for("Window", timeout=60)
    tap_until("Window", "Transparent overlay", label="window-tab-2")
    tap_until("Transparent overlay", "Screen capture for adaptive contrast", label="projection-disclosure")
    shot("05-projection-disclosure")
    tap_text("Use manual colors")
    time.sleep(4)
    windows = overlay_windows()
    if windows != 2:
        for line in overlay_window_lines():
            print("overlay-window:", line)
        raise SystemExit(f"expected 2 overlay windows, found {windows}")
    print("wm size:", run(*ADB, "shell", "wm", "size").strip())
    print("wm density:", run(*ADB, "shell", "wm", "density").strip())
    size_match = re.search(r"(\d+)x(\d+)", run(*ADB, "shell", "wm", "size"))
    screen_height = int(size_match.group(2)) if size_match else 0
    window_dump = run(*ADB, "shell", "dumpsys", "window", "windows")
    for line in window_dump.splitlines():
        if "(wrapxwrap)" in line or "Requested w=" in line:
            print("window:", line.strip())
    bar_y = control_window_y()
    if bar_y is None or screen_height <= 0 or bar_y < screen_height // 2:
        raise SystemExit(
            f"overlay bar not positioned in the lower half (y={bar_y}, screen={screen_height})",
        )
    shot("06-overlay-running")
    print("PASS overlay running")

    # The bar must be interactive: expand if collapsed and stop the overlay.
    run(*ADB, "shell", "am", "start", "-W", "-n", ACTIVITY)
    if find(dump(), "Stop overlay") is None:
        # Some API levels recreate the task; navigate back to the stage.
        if find(dump(), "Present"):
            tap_until("Present", "Playback", label="present-2")
            tap_until("Window", "Transparent overlay", label="window-tab-3")
    wait_for("Stop overlay", timeout=60)
    shot("07-overlay-live-in-app")
    tap_text("Stop overlay")
    time.sleep(3)
    windows = overlay_windows()
    if windows != 0:
        raise SystemExit(f"overlay did not stop, windows={windows}")
    print("PASS overlay stopped")

    # Settings: privacy policy + ad privacy entry points.
    # Disable network so the banner ad fails fast and the UI can go idle for
    # uiautomator dumps (API 28 WebView ads can keep the hierarchy busy).
    run(*ADB, "shell", "svc", "wifi", "disable", check=False)
    run(*ADB, "shell", "svc", "data", "disable", check=False)
    run(*ADB, "shell", "am", "force-stop", PKG)
    run(*ADB, "shell", "am", "start", "-W", "-n", ACTIVITY)
    wait_for("Present", timeout=90)
    # Open settings via the gear content description.
    xml = dump()
    gear = None
    for match in re.finditer(r"<node [^>]*>", xml):
        node = match.group(0)
        desc = re.search(r'content-desc="([^"]*)"', node)
        bounds = re.search(r'bounds="([^"]*)"', node)
        if desc and bounds and "Settings" in desc.group(1):
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", bounds.group(1)))
            gear = ((x1 + x2) // 2, (y1 + y2) // 2)
            break
    if not gear:
        raise SystemExit("settings gear not found")
    tap(gear)
    scroll_until("Privacy policy")
    shot("08-settings")
    print("PASS settings privacy entry points")

    assert_no_crash()
    print("ALL CHECKS PASSED")


if __name__ == "__main__":
    main()
