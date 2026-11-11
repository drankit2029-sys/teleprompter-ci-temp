#!/usr/bin/env bash
# Start (or create then start) an Android emulator for project testing.
#
# Usage:
#   scripts/start-emulator.sh                          # API 30 low-RAM phone (headless)
#   scripts/start-emulator.sh --window                 # show the emulator window
#   scripts/start-emulator.sh --api 37.0 --device pixel_tablet --ram 2048
#   scripts/start-emulator.sh --device "7.6in Foldable" --name fold
#   scripts/start-emulator.sh --width 1080 --height 2400 --density 420
#
# Options:
#   --api <level>      System image API level (default: 30). Android 17 = 37.0
#   --image <variant>  default | google_apis | aosp_atd | google_atd |
#                      google_apis_playstore (default: default)
#   --abi <abi>        x86_64 or arm64-v8a (default: x86_64)
#   --device <id>      AVD device profile: pixel_2, pixel_tablet, resizable,
#                      "7.6in Foldable", "8in Foldable", "10.1in WXGA (Tablet)", ...
#   --name <avd>       AVD name (default: auto, e.g. compose-37_0-pixel_tablet)
#   --ram <mb>         Guest RAM in MB (default: 1536)
#   --cores <n>        vCPUs (default: 2)
#   --width/--height   Custom resolution; --density <dpi> overrides density
#   --window           Show the emulator window (default: headless)
#   --create-only      Create/tune the AVD but do not boot it
#
# Environment:
#   AVD_NAME, AVD_API, AVD_IMAGE, AVD_ABI, AVD_DEVICE, ANDROID_SDK_ROOT
set -euo pipefail

ANDROID_SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_SDK"
export ANDROID_HOME="$ANDROID_SDK"

EMULATOR="$ANDROID_SDK/emulator/emulator"
AVDMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/avdmanager"
SDKMANAGER="$ANDROID_SDK/cmdline-tools/latest/bin/sdkmanager"

# sdkmanager exits after consuming the answers it needs, so `yes` dies with
# SIGPIPE. With `set -o pipefail` that would abort the script even though the
# install succeeded, so disable pipefail for this pipeline.
install_sdk_package() {
  local package="$1" rc=0
  set +o pipefail
  yes | "$SDKMANAGER" --install "$package" || rc=$?
  set -o pipefail
  return "$rc"
}

# adb is not always on PATH on CI images; fall back to the SDK copy.
if [[ ! -x "$ANDROID_SDK/platform-tools/adb" ]]; then
  echo "Installing Android platform-tools..."
  install_sdk_package platform-tools
fi
ADB="$ANDROID_SDK/platform-tools/adb"
if [[ ! -x "$ADB" ]]; then
  ADB="$(command -v adb || true)"
fi
if [[ -z "$ADB" ]]; then
  echo "adb not found. Install platform-tools: sdkmanager --install platform-tools" >&2
  exit 1
fi
export PATH="$ANDROID_SDK/platform-tools:$PATH"

API="${AVD_API:-30}"
IMAGE_VARIANT="${AVD_IMAGE:-default}"
ABI="${AVD_ABI:-x86_64}"
DEVICE="${AVD_DEVICE:-pixel_2}"
NAME="${AVD_NAME:-}"
RAM=1536
CORES=2
WIDTH=""
HEIGHT=""
DENSITY=""
WINDOW=0
CREATE_ONLY=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api) API="$2"; shift 2 ;;
    --image) IMAGE_VARIANT="$2"; shift 2 ;;
    --abi) ABI="$2"; shift 2 ;;
    --device) DEVICE="$2"; shift 2 ;;
    --name) NAME="$2"; shift 2 ;;
    --ram) RAM="$2"; shift 2 ;;
    --cores) CORES="$2"; shift 2 ;;
    --width) WIDTH="$2"; shift 2 ;;
    --height) HEIGHT="$2"; shift 2 ;;
    --density) DENSITY="$2"; shift 2 ;;
    --window) WINDOW=1; shift ;;
    --create-only) CREATE_ONLY=1; shift ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
done

# The emulator enforces per-image guest RAM floors and overwrites config.ini.
if [[ "$API" =~ ^(3[7-9]|[4-9][0-9])(\.|$) ]] && (( RAM < 4096 )); then
  echo "API $API requires at least 4096 MB of guest RAM; using 4096 MB."
  RAM=4096
elif [[ "$API" =~ ^(29|3[0-6])(\.|$) ]] && (( RAM < 2048 )); then
  echo "API $API requires at least 2048 MB of guest RAM; using 2048 MB."
  RAM=2048
fi

[[ -x "$EMULATOR" ]] || { echo "Emulator not installed. Run: sdkmanager --install emulator" >&2; exit 1; }
command -v adb >/dev/null 2>&1 || { echo "adb not found" >&2; exit 1; }
# API levels 34+ are published as minor-versioned packages (37.0, 36.1, ...).
IMAGE="system-images;android-${API};${IMAGE_VARIANT};${ABI}"
if ! "$SDKMANAGER" --list 2>/dev/null | grep -qF "$IMAGE"; then
  if [[ "$API" != *.0 && "$API" =~ ^3[4-9]$ ]]; then
    API="${API}.0"
    IMAGE="system-images;android-${API};${IMAGE_VARIANT};${ABI}"
  fi
fi

# Derive a stable AVD name from the image/device unless one was given.
if [[ -z "$NAME" ]]; then
  if [[ "$API" == "30" && "$DEVICE" == "pixel_2" && -z "$WIDTH" ]]; then
    NAME="compose-lowram"
  else
    SLUG="$(printf '%s' "$DEVICE" | tr -cs 'A-Za-z0-9._-' '-')"
    NAME="compose-${API//./_}-${SLUG}"
  fi
fi

# Honor ANDROID_AVD_HOME (CI images may set it); pin it when unset so
# avdmanager and the emulator agree on where the AVD lives.
AVD_HOME="${ANDROID_AVD_HOME:-$HOME/.android/avd}"
export ANDROID_AVD_HOME="$AVD_HOME"
mkdir -p "$AVD_HOME"

# Install the system image if needed.
if ! "$SDKMANAGER" --list_installed 2>/dev/null | grep -qF "$IMAGE"; then
  echo "Installing system image: $IMAGE"
  install_sdk_package "$IMAGE"
fi

# Create the AVD once.
if ! "$EMULATOR" -list-avds 2>/dev/null | grep -qx "$NAME"; then
  echo "Creating AVD '$NAME' ($IMAGE, device: $DEVICE)"
  echo no | "$AVDMANAGER" create avd \
    --name "$NAME" \
    --package "$IMAGE" \
    --device "$DEVICE" \
    --force
fi

# Tune the AVD config: low RAM, small heap, optional custom screen metrics.
CFG="$AVD_HOME/${NAME}.avd/config.ini"
if [[ ! -f "$CFG" ]]; then
  echo "AVD config not found at $CFG (ANDROID_AVD_HOME=$AVD_HOME)" >&2
  find "$AVD_HOME" "$HOME/.android/avd" -maxdepth 3 -name config.ini 2>/dev/null >&2 || true
  exit 1
fi
set_cfg() {
  local key="$1" value="$2"
  if grep -q "^${key}" "$CFG"; then
    sed -i "s|^${key}.*|${key}=${value}|" "$CFG"
  else
    printf '%s=%s\n' "$key" "$value" >> "$CFG"
  fi
}
set_cfg hw.ramSize "$RAM"
set_cfg vm.heapSize 384
# The API 37 4 GB requirement applies to guest RAM, not the data partition.
# A 2 GB data partition is ample for tests and screenshots and keeps CI
# runners (which have ~7 GB free at boot) from failing the space check.
set_cfg disk.dataPartition.size 2048M
set_cfg hw.audioInput no
set_cfg hw.gps no
set_cfg hw.camera.back none
set_cfg hw.camera.front none
set_cfg hw.keyboard yes
[[ -n "$WIDTH" ]] && set_cfg hw.lcd.width "$WIDTH"
[[ -n "$HEIGHT" ]] && set_cfg hw.lcd.height "$HEIGHT"
[[ -n "$DENSITY" ]] && set_cfg hw.lcd.density "$DENSITY"
# Resizable AVDs must actually be free to resize.
if [[ "$DEVICE" == "resizable" ]]; then
  set_cfg hw.initialOrientation portrait
fi

if [[ "$CREATE_ONLY" -eq 1 ]]; then
  echo "AVD '$NAME' is ready (--create-only; not booting)."
  exit 0
fi

# Start the emulator with RAM-saving flags.
ARGS=(
  -avd "$NAME"
  -memory "$RAM"
  -cores "$CORES"
  -gpu swiftshader_indirect
  -no-boot-anim
  -no-audio
  -no-snapshot
  -no-metrics
  -netdelay none
  -netspeed full
)
if [[ "$WINDOW" -eq 0 ]]; then
  ARGS+=(-no-window)
fi

# Wait up to 5 minutes for an emulator serial to show up in adb.
wait_for_emulator() {
  SERIAL=""
  for _ in $(seq 1 150); do
    SERIAL="$("$ADB" devices | awk '/^emulator-/{print $1; exit}' || true)"
    [[ -n "$SERIAL" ]] && return 0
    sleep 2
  done
  return 1
}

EMU_STARTED=0
EMU_PID=""
if "$ADB" devices | grep -q '^emulator-'; then
  echo "An emulator is already running; reusing it."
else
  echo "Starting emulator: $NAME (${RAM} MB RAM, ${CORES} cores)"
  nohup "$EMULATOR" "${ARGS[@]}" >/tmp/emulator-"$NAME".log 2>&1 &
  EMU_PID=$!
  EMU_STARTED=1
  echo "Emulator log: /tmp/emulator-$NAME.log"
fi

# One retry handles transient emulator startup hangs on CI runners.
if ! wait_for_emulator; then
  if [[ "$EMU_STARTED" -eq 1 ]]; then
    echo "Emulator did not start within 5 minutes; retrying once..." >&2
    tail -5 "/tmp/emulator-$NAME.log" >&2 2>/dev/null || true
    kill "$EMU_PID" 2>/dev/null || true
    pkill -f -- "-avd $NAME" 2>/dev/null || true
    sleep 5
    nohup "$EMULATOR" "${ARGS[@]}" >/tmp/emulator-"$NAME".log 2>&1 &
    EMU_PID=$!
    if ! wait_for_emulator; then
      echo "Emulator did not appear in adb devices after retry. Check /tmp/emulator-$NAME.log" >&2
      tail -30 "/tmp/emulator-$NAME.log" >&2 2>/dev/null || true
      exit 1
    fi
  else
    echo "Emulator did not appear in adb devices. Check /tmp/emulator-$NAME.log" >&2
    exit 1
  fi
fi

echo -n "Waiting for emulator to come online"
for _ in $(seq 1 150); do
  STATE="$("$ADB" -s "$SERIAL" get-state 2>/dev/null | tr -d '\r' || true)"
  [[ "$STATE" == "device" ]] && break
  echo -n "."
  sleep 2
done
if [[ "$("$ADB" -s "$SERIAL" get-state 2>/dev/null | tr -d '\r' || true)" != "device" ]]; then
  echo
  echo "Emulator did not come online. Check /tmp/emulator-$NAME.log" >&2
  tail -20 "/tmp/emulator-$NAME.log" >&2 2>/dev/null || true
  exit 1
fi
echo " done."

echo -n "Waiting for boot to complete"
for _ in $(seq 1 300); do
  BOOTED="$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
  if [[ "$BOOTED" == "1" ]]; then
    echo " done."
    break
  fi
  echo -n "."
  sleep 2
done
if [[ "$("$ADB" -s "$SERIAL" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)" != "1" ]]; then
  echo
  echo "Emulator did not finish booting. Check /tmp/emulator-$NAME.log" >&2
  tail -20 "/tmp/emulator-$NAME.log" >&2 2>/dev/null || true
  exit 1
fi

# Trim runtime load: kill animations, keep the screen awake.
"$ADB" -s "$SERIAL" shell settings put global window_animation_scale 0 2>/dev/null || true
"$ADB" -s "$SERIAL" shell settings put global transition_animation_scale 0 2>/dev/null || true
"$ADB" -s "$SERIAL" shell settings put global animator_duration_scale 0 2>/dev/null || true
"$ADB" -s "$SERIAL" shell svc power stayon true 2>/dev/null || true

echo
echo "Emulator ready: $SERIAL"
echo "Android version: $("$ADB" -s "$SERIAL" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r') (API $("$ADB" -s "$SERIAL" shell getprop ro.build.version.sdk 2>/dev/null | tr -d '\r'))"
echo
echo "Install the app on it with:"
echo "  ANDROID_SERIAL=$SERIAL ./scripts/build-on-phone.sh --no-scrcpy"
echo
echo "If a physical device is also connected, ANDROID_SERIAL is required."
echo "Optional live view without the emulator window: scrcpy -s $SERIAL"
