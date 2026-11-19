#!/usr/bin/env python3
"""Capture overlay screenshots on a dedicated scrcpy virtual display.

Each invocation targets one (profile, orientation) and creates its own
`scrcpy --new-display=WxH/DPI` virtual display on the runner's emulator.
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
WIDTH = int(os.environ["WIDTH"])
HEIGHT = int(os.environ["HEIGHT"])
DPI = int(os.environ["DPI"])
LOG = f"/tmp/scrcpy-{PROFILE}.log"

os.makedirs(OUT, exist_ok=True)
ADB = ["adb"]
DISPLAY_ID = None
CAPTURE_ID = None


def run(*args, check=True):
    result = subprocess.run(list(args), capture_output=True, text=True)
    if check and result.returncode != 0:
        print(result.stdout, result.stderr)
        raise SystemExit(f"command failed: {' '.join(args)}")
    return result.stdout


def start_virtual_display():
    global DISPLAY_ID, CAPTURE_ID
    with open(LOG, "w") as handle:
        subprocess.Popen(
            ["scrcpy", f"--new-display={WIDTH}x{HEIGHT}/{DPI}",
             "--no-audio", "--window-title", f"TP-{PROFILE}"],
            stdout=handle, stderr=subprocess.STDOUT,
        )
    deadline = time.time() + 60
    while time.time() < deadline:
        text = open(LOG, errors="ignore").read()
        match = re.search(r"New display:.*?\(id=(\d+)\)", text)
        if match:
            DISPLAY_ID = int(match.group(1))
            break
        time.sleep(1)
    if DISPLAY_ID is None:
        print(open(LOG, errors="ignore").read()[-2000:])
        raise SystemExit("scrcpy virtual display did not start")
    # screencap -d accepts a display ID on API 30; fall back to the SF id.
    CAPTURE_ID = DISPLAY_ID
    probe = f"{OUT}/_probe.png"
    with open(probe, "wb") as handle:
        subprocess.run(
            ADB + ["exec-out", "screencap", "-p", "-d", str(CAPTURE_ID)],
            stdout=handle, stderr=subprocess.DEVNULL, check=False,
        )
    try:
        with Image.open(probe) as image:
            if image.size[0] != WIDTH or image.size[1] != HEIGHT:
                raise ValueError("size mismatch")
        os.remove(probe)
    except Exception:
        if os.path.exists(probe):
            os.remove(probe)
        sfid = sfid_for_display()
        if sfid:
            CAPTURE_ID = sfid
        else:
            raise SystemExit("could not capture virtual display")
    print(f"virtual display id={DISPLAY_ID} capture id={CAPTURE_ID}")


def sfid_for_display():
    disp = run(*ADB, "shell", "dumpsys", "display")
    suffix = None
    for line in disp.splitlines():
        if f"displayId={DISPLAY_ID}" in line:
            m = re.search(r"uniqueId='[^']*scrcpy,(\d+)'", line)
            if m:
                suffix = m.group(1)
    if not suffix:
        return None
    sf = run(*ADB, "shell", "dumpsys", "SurfaceFlinger", "--display-id")
    for line in sf.splitlines():
        if f"scrcpy,{suffix}" in line:
            m = re.search(r"Display (\d+) \(Virtual display\)", line)
            if m:
                return m.group(1)
    return None


def adb_input(*args):
    return run(*ADB, "shell", "input", "-d", str(DISPLAY_ID), *args)


def raw_capture(path):
    with open(path, "wb") as handle:
        subprocess.run(
            ADB + ["exec-out", "screencap", "-p", "-d", str(CAPTURE_ID)],
            stdout=handle, check=True,
        )


def screencap(name):
    path = f"{OUT}/{name}.png"
    raw_capture(path)
    with Image.open(path) as image:
        if image.convert("RGB").getbbox() is None:
            raise SystemExit(f"blank screenshot: {path}")
    print("saved", path, Image.open(path).size)


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


def find_desc(xml, text):
    for match in re.finditer(r"<node [^>]*>", xml):
        node = match.group(0)
        desc = re.search(r'content-desc="([^"]*)"', node)
        bounds = re.search(r'bounds="([^"]*)"', node)
        if desc and bounds and desc.group(1) == text:
            x1, y1, x2, y2 = map(int, re.findall(r"-?\d+", bounds.group(1)))
            return ((x1 + x2) // 2, (y1 + y2) // 2)
    return None


def wait_for(text, timeout=60, desc=False):
    deadline = time.time() + timeout
    while time.time() < deadline:
        xml = dump()
        point = find_desc(xml, text) if desc else find(xml, text)
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
    adb_input("tap", str(point[0]), str(point[1]))


def scroll_until(text, timeout=40):
    deadline = time.time() + timeout
    while time.time() < deadline:
        point = find(dump(), text)
        if point:
            return point
        x = WIDTH // 2
        adb_input("swipe", str(x), str(int(HEIGHT * 0.70)),
                  str(x), str(int(HEIGHT * 0.35)), "600")
        time.sleep(2)
    return wait_for(text, timeout=10)


def yellow_center():
    tmp = f"{OUT}/_y.png"
    raw_capture(tmp)
    with Image.open(tmp).convert("RGB") as image:
        w, h = image.size
        pts = [
            (x, y)
            for y in range(int(h * 0.55), h, 2)
            for x in range(0, w, 2)
            if (lambda p: p[0] > 235 and 175 < p[1] < 240 and p[2] < 90)(
                image.getpixel((x, y))
            )
        ]
    os.remove(tmp)
    if not pts:
        return None
    return (sum(p[0] for p in pts) // len(pts), sum(p[1] for p in pts) // len(pts))


def magenta_corner():
    tmp = f"{OUT}/_m.png"
    raw_capture(tmp)
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
    cx, cy = w // 2, h // 2
    return max(pts, key=lambda p: (p[0] - cx) ** 2 + (p[1] - cy) ** 2)


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


def main():
    start_virtual_display()
    run(*ADB, "shell", "settings", "put", "system", "screen_off_timeout", "2147483647")
    run(*ADB, "shell", "settings", "put", "secure", "immersive_mode_confirmations", "confirmed", check=False)
    run(*ADB, "shell", "appops", "set", PKG, "SYSTEM_ALERT_WINDOW", "allow")
    run(*ADB, "shell", "pm", "grant", PKG, "android.permission.POST_NOTIFICATIONS", check=False)
    run(*ADB, "shell", "am", "force-stop", PKG)
    run(*ADB, "shell", "am", "start", "--display", str(DISPLAY_ID), "-W", "-n", ACTIVITY)
    tap(wait_for("Settings", desc=True))
    tap(scroll_until("Start overlay"))
    tap(wait_for("Screen capture for adaptive contrast"))
    tap(wait_for("Use manual colors"))
    time.sleep(4)

    # Wait for the 5s auto-collapse, then capture the collapsed draggable toolbar.
    time.sleep(5)
    screencap(f"overlay-collapsed-{PROFILE}")

    # Expand the bar from the collapsed TP square, then enter resize mode.
    bounds = control_window()
    if not bounds:
        raise SystemExit(f"control window not found on {PROFILE}")
    x, y, w, h = bounds
    tap((x + w // 2, y + h // 2))
    time.sleep(1.5)
    area = yellow_center()
    if area:
        tap(area)
        time.sleep(1)
    corner = magenta_corner()
    if corner:
        adb_input("swipe", str(corner[0]), str(corner[1]),
                  str(max(0, corner[0] - 60)), str(max(0, corner[1] - 60)), "600")
        time.sleep(1)
    screencap(f"overlay-resizing-{PROFILE}")
    print("DONE", PROFILE)


if __name__ == "__main__":
    main()
