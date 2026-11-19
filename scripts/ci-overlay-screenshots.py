#!/usr/bin/env python3
"""Capture the floating overlay in collapsed-toolbar and resizing states.

Runs once per (profile, orientation) on a GitHub runner emulator. The emulator's
display is resized to the requested profile with `wm size` / `wm density`.
"""

import os
import re
import subprocess
import time

from PIL import Image

PKG = "com.accessoryTools.teleprompter"
ACTIVITY = f"{PKG}/com.accessoryTools.teleprompter.MainActivity"
OUT = "ci-screenshots"
PROFILE = os.environ["PROFILE"]
WIDTH = os.environ["WIDTH"]
HEIGHT = os.environ["HEIGHT"]
DPI = os.environ["DPI"]
os.makedirs(OUT, exist_ok=True)

ADB = ["adb"]


def run(*args, check=True):
    result = subprocess.run(list(args), capture_output=True, text=True)
    if check and result.returncode != 0:
        print(result.stdout, result.stderr)
        raise SystemExit(f"command failed: {' '.join(args)}")
    return result.stdout


def dump():
    for _ in range(8):
        run(*ADB, "shell", "rm", "-f", "/sdcard/ui.xml", check=False)
        out = run(
            *ADB, "shell", "uiautomator", "dump", "--compressed", "/sdcard/ui.xml",
            check=False,
        )
        if "ui.xml" in out or "Hierarchy" in out:
            run(*ADB, "pull", "/sdcard/ui.xml", "/tmp/ui.xml", check=False)
            try:
                with open("/tmp/ui.xml", encoding="utf-8") as handle:
                    return handle.read()
            except FileNotFoundError:
                pass
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


def wait_for(text, timeout=60):
    deadline = time.time() + timeout
    while time.time() < deadline:
        xml = dump()
        point = find(xml, text)
        if point:
            return point
        for hint in ("GOT IT", "Allow"):
            hp = find(xml, hint)
            if hp:
                tap(hp)
                time.sleep(1)
                break
        time.sleep(2)
    raise SystemExit(f"timed out waiting for {text!r} on {PROFILE}")


def tap(point):
    run(*ADB, "shell", "input", "tap", str(point[0]), str(point[1]))


def tap_until(text, next_text, attempts=3, timeout=60):
    for _ in range(attempts):
        tap(wait_for(text))
        deadline = time.time() + timeout
        while time.time() < deadline:
            if find(dump(), next_text):
                return
            time.sleep(2)
    raise SystemExit(f"{text!r} did not reach {next_text!r} on {PROFILE}")


def screencap(name):
    path = f"{OUT}/{name}.png"
    with open(path, "wb") as handle:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=handle, check=True)
    with Image.open(path) as image:
        if image.convert("RGB").getbbox() is None:
            raise SystemExit(f"blank screenshot: {path}")
    print("saved", path)


def control_window():
    out = run(*ADB, "shell", "dumpsys", "window", "windows")
    ours = False
    x = y = w = h = None
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.startswith("Window #"):
            ours = PKG in stripped
            x = y = w = h = None
        elif ours and "(wrapxwrap)" in stripped:
            m = re.search(r"\{\((-?\d+),(-?\d+)\)\(wrapxwrap\)", stripped)
            if m:
                x, y = int(m.group(1)), int(m.group(2))
        elif ours and "Requested w=" in stripped and x is not None:
            m = re.search(r"Requested w=(\d+) h=(\d+)", stripped)
            if m:
                w, h = int(m.group(1)), int(m.group(2))
            if None not in (x, y, w, h):
                return x, y, w, h
    return None


def yellow_center():
    """Center of the yellow Area button in the expanded bar, from pixels."""
    tmp = f"{OUT}/_probe.png"
    with open(tmp, "wb") as handle:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=handle, check=True)
    with Image.open(tmp).convert("RGB") as image:
        w, h = image.size
        top = int(h * 0.55)
        pts = [
            (x, y)
            for y in range(top, h, 2)
            for x in range(0, w, 2)
            if (lambda p: p[0] > 235 and 175 < p[1] < 240 and p[2] < 90)(
                image.getpixel((x, y))
            )
        ]
    os.remove(tmp)
    if not pts:
        return None
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return (sum(xs) // len(xs), sum(ys) // len(ys))


def magenta_corner():
    tmp = f"{OUT}/_probe2.png"
    with open(tmp, "wb") as handle:
        subprocess.run(ADB + ["exec-out", "screencap", "-p"], stdout=handle, check=True)
    with Image.open(tmp).convert("RGB") as image:
        w, h = image.size
        pts = [
            (x, y)
            for y in range(0, h, 3)
            for x in range(0, w, 3)
            if (lambda p: p[0] > 200 and p[1] < 90 and p[2] > 200)(
                image.getpixel((x, y))
            )
        ]
    os.remove(tmp)
    if not pts:
        return None
    # Prefer a corner: the point farthest from the screen center.
    cx, cy = w // 2, h // 2
    return max(pts, key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2)


def main():
    run(*ADB, "shell", "wm", "size", f"{WIDTH}x{HEIGHT}")
    run(*ADB, "shell", "wm", "density", DPI)
    time.sleep(3)
    run(*ADB, "shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
    run(*ADB, "shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed", check=False)
    run(*ADB, "shell", "appops", "set", PKG, "SYSTEM_ALERT_WINDOW", "allow")
    run(*ADB, "shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
    run(*ADB, "shell", "am", "force-stop", PKG)
    run(*ADB, "shell", "am", "start", "-W", "-n", ACTIVITY)
    wait_for("Present")
    tap_until("Present", "Playback")
    tap_until("Window", "Transparent overlay")
    tap_until("Transparent overlay", "Screen capture for adaptive contrast")
    tap(wait_for("Use manual colors"))
    time.sleep(4)

    # Wait for the 5s auto-collapse, then capture the collapsed draggable toolbar.
    time.sleep(5)
    screencap(f"overlay-collapsed-{PROFILE}")

    # Expand the bar via the collapsed TP square (bounds from the window manager).
    bounds = control_window()
    if not bounds:
        raise SystemExit(f"control window not found on {PROFILE}")
    x, y, w, h = bounds
    tap((x + w // 2, y + h // 2))
    time.sleep(1.5)

    # Tap the yellow Area button to enter resize mode, then drag a corner.
    area = yellow_center()
    if area:
        tap(area)
        time.sleep(1)
    corner = magenta_corner()
    if corner:
        run(*ADB, "shell", "input", "swipe", str(corner[0]), str(corner[1]),
            str(max(0, corner[0] - 60)), str(max(0, corner[1] - 60)), "600")
        time.sleep(1)
    screencap(f"overlay-resizing-{PROFILE}")
    print("DONE", PROFILE)


if __name__ == "__main__":
    main()
