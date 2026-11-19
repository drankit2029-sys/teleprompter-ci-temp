#!/usr/bin/env python3
"""Capture overlay screenshots on a dedicated scrcpy virtual display.

Each invocation targets one (profile, orientation) with its own display.
Navigation uses OCR of the virtual-display screenshot (uiautomator dump always
targets the emulator's primary display, not the scrcpy display).
"""

import os
import re
import subprocess
import time

from PIL import Image
import pytesseract

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
             "--no-audio", "--render-driver=software", "--window-title", f"TP-{PROFILE}"],
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
    CAPTURE_ID = DISPLAY_ID
    probe = f"{OUT}/_probe.png"
    with open(probe, "wb") as handle:
        subprocess.run(
            ADB + ["exec-out", "screencap", "-p", "-d", str(CAPTURE_ID)],
            stdout=handle, stderr=subprocess.DEVNULL, check=False,
        )
    try:
        with Image.open(probe) as image:
            if image.size[:2] != (WIDTH, HEIGHT):
                raise ValueError("size mismatch")
        os.remove(probe)
    except Exception:
        if os.path.exists(probe):
            os.remove(probe)
        sfid = sfid_for_display()
        if not sfid:
            raise SystemExit("could not capture virtual display")
        CAPTURE_ID = sfid
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


def ocr_find(phrase):
    """Center of a text phrase in the virtual-display screenshot."""
    tmp = f"{OUT}/_ocr.png"
    raw_capture(tmp)
    try:
        with Image.open(tmp).convert("RGB") as image:
            image = image.resize((image.width * 2, image.height * 2))
            data = pytesseract.image_to_data(
                image, output_type=pytesseract.Output.DICT, config="--psm 11",
            )
        lines = {}
        for i, word in enumerate(data["text"]):
            word = word.strip()
            if not word or int(float(data["conf"][i] or -1)) < 10:
                continue
            key = (data["block_num"][i], data["par_num"][i], data["line_num"][i])
            lines.setdefault(key, []).append(
                (word, int(data["left"][i]), int(data["top"][i]),
                 int(data["width"][i]), int(data["height"][i]))
            )
        target = phrase.lower()
        for parts in lines.values():
            text = " ".join(p[0] for p in parts).lower()
            if target in text or any(p[0].lower() == target for p in parts):
                xs = [p[1] + p[3] / 2 for p in parts]
                ys = [p[2] + p[4] / 2 for p in parts]
                return (int(sum(xs) / len(xs) / 2), int(sum(ys) / len(ys) / 2))
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)
    return None


def tap(point):
    adb_input("tap", str(point[0]), str(point[1]))


def wait_ocr(phrase, timeout=60, scroll=False):
    deadline = time.time() + timeout
    attempt = 0
    while time.time() < deadline:
        point = ocr_find(phrase)
        if point:
            return point
        for hint in ("GOT IT", "Allow"):
            hp = ocr_find(hint)
            if hp:
                tap(hp)
                time.sleep(1)
                break
        if scroll and attempt >= 2:
            x = WIDTH // 2
            if attempt % 4 < 4:  # swipe down => scroll toward the top
                adb_input("swipe", str(x), str(int(HEIGHT * 0.35)),
                          str(x), str(int(HEIGHT * 0.75)), "600")
            else:  # swipe up => scroll toward the bottom
                adb_input("swipe", str(x), str(int(HEIGHT * 0.75)),
                          str(x), str(int(HEIGHT * 0.35)), "600")
        attempt += 1
        time.sleep(2)
    screencap(f"timeout-{phrase.replace(' ', '-').lower()}")
    raise SystemExit(f"OCR timed out for {phrase!r} on {PROFILE}")


def yellow_center():
    tmp = f"{OUT}/_y.png"
    raw_capture(tmp)
    try:
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
    finally:
        if os.path.exists(tmp):
            os.remove(tmp)
    if not pts:
        return None
    return (sum(p[0] for p in pts) // len(pts), sum(p[1] for p in pts) // len(pts))


def magenta_corner():
    tmp = f"{OUT}/_m.png"
    raw_capture(tmp)
    try:
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
    finally:
        if os.path.exists(tmp):
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
    time.sleep(3)

    # Gear icon is top-right of the yellow header (icon, not OCR text).
    tap((int(WIDTH * 0.87), int(HEIGHT * 0.05)))
    tap(wait_ocr("Start overlay", timeout=30, scroll=True))
    tap(wait_ocr("Screen capture for adaptive contrast", timeout=30))
    tap(wait_ocr("Use manual colors", timeout=30))
    time.sleep(4)

    # Wait for the 5s auto-collapse, then capture the collapsed draggable toolbar.
    time.sleep(5)
    screencap(f"overlay-collapsed-{PROFILE}")

    # Expand the bar via the collapsed TP square, then enter resize mode.
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
