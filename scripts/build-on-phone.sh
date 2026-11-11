#!/usr/bin/env bash
# build-on-phone — build a Gradle/Compose project on a Termux phone, install
# the APK on any connected adb device, and relaunch it in a scrcpy virtual
# display.
#
# The phone is the build machine; the PC is only the editor. The install
# target may be the build phone itself, an emulator, or another adb device.
# If several devices are connected, the target is chosen once and remembered.
#
# Usage:
#   build-on-phone [options]
#
# Options:
#   -p, --project DIR       Project root (default: current directory or parent)
#   -d, --device SERIAL     adb device to install/launch on; remembered
#   -b, --build-device SER  adb serial of the Termux build phone (default: auto)
#       --display-size SPEC scrcpy --new-display size (default: 1080x2400/420)
#       --no-scrcpy         Skip scrcpy; launch on the main display
#       --forget-device     Forget the remembered install target and exit
#       --install-global    Install this command into the Android SDK bin and exit
#   -h, --help              Show this help
#
# Environment:
#   PROJECT_DIR, TARGET_SERIAL, BUILD_SERIAL
#   TERMUX_HOST, TERMUX_USER (u0_a423), TERMUX_PORT (8022), TERMUX_KEY
#   SCRCPY_DISPLAY_SIZE, SCRCPY_BIN
#   STATE_DIR (default: ${XDG_STATE_HOME:-$HOME/.local/state}/build-on-phone)
#
# Setup (once): on the phone, `pkg install openssh`, `ssh-keygen -A`, `sshd`,
# and install your PC's public key into ~/.ssh/authorized_keys.
set -euo pipefail

SCRIPT_PATH="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/$(basename "${BASH_SOURCE[0]}")"
STATE_DIR="${STATE_DIR:-${XDG_STATE_HOME:-$HOME/.local/state}/build-on-phone}"
STATE_FILE="$STATE_DIR/target-serial"

info() { printf '%s\n' "$*" >&2; }
warn() { printf 'warning: %s\n' "$*" >&2; }
die()  { printf 'error: %s\n' "$*" >&2; exit 1; }

usage() {
    awk 'NR == 1 { next } /^#/ { sub(/^# ?/, ""); print; next } { exit }' "$SCRIPT_PATH"
}

find_project_root() {
    local d="$1"
    while :; do
        if [ -f "$d/settings.gradle" ] || [ -f "$d/settings.gradle.kts" ]; then
            printf '%s\n' "$d"
            return 0
        fi
        [ "$d" = "/" ] && return 1
        d="$(dirname "$d")"
    done
}

PROJECT_OPT=""
TARGET_OPT=""
BUILD_OPT=""
DISPLAY_SIZE="${SCRCPY_DISPLAY_SIZE:-1080x2400/420}"
USE_SCRCPY=1

while [ "$#" -gt 0 ]; do
    case "$1" in
        -p|--project)     PROJECT_OPT="${2:?--project needs a directory}"; shift 2 ;;
        -d|--device)      TARGET_OPT="${2:?--device needs a serial}"; shift 2 ;;
        -b|--build-device) BUILD_OPT="${2:?--build-device needs a serial}"; shift 2 ;;
        --display-size)   DISPLAY_SIZE="${2:?--display-size needs WxH[/DPI]}"; shift 2 ;;
        --no-scrcpy|--no-display) USE_SCRCPY=0; shift ;;
        --forget-device)
            rm -f "$STATE_FILE"
            info "Forgot the remembered install device."
            exit 0
            ;;
        --install-global)
            SDK_BIN="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}/cmdline-tools/latest/bin"
            mkdir -p "$SDK_BIN"
            DEST="$SDK_BIN/build-on-phone"
            if [ "$(readlink -f "$SCRIPT_PATH")" != "$(readlink -f "$DEST" 2>/dev/null || true)" ]; then
                cp "$SCRIPT_PATH" "$DEST"
            fi
            chmod +x "$DEST"
            info "Installed global command: $DEST"
            exit 0
            ;;
        -h|--help) usage; exit 0 ;;
        *) die "unknown option: $1 (try --help)" ;;
    esac
done

case "$DISPLAY_SIZE" in
    ''|*[!0-9x/]*) die "invalid --display-size '$DISPLAY_SIZE' (expected WxH or WxH/DPI)" ;;
esac

if [ -n "$PROJECT_OPT" ]; then
    SRC="$(cd "$PROJECT_OPT" && pwd)" || die "project directory not found: $PROJECT_OPT"
else
    SRC="$(find_project_root "$PWD" || true)"
fi
if [ -z "${SRC:-}" ]; then
    SELF_PARENT="$(cd "$(dirname "$SCRIPT_PATH")/.." 2>/dev/null && pwd || true)"
    [ -n "$SELF_PARENT" ] && SRC="$(find_project_root "$SELF_PARENT" || true)"
fi
[ -n "${SRC:-}" ] || die "no Gradle project found; run inside one or pass --project DIR"
PROJECT_DIR_NAME="$(basename "$SRC")"
PROJECT="${PROJECT_NAME:-$PROJECT_DIR_NAME}"

if ! command -v adb >/dev/null 2>&1; then
    for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk"; do
        if [ -n "$d" ] && [ -x "$d/platform-tools/adb" ]; then
            PATH="$d/platform-tools:$PATH"
            break
        fi
    done
fi
command -v adb >/dev/null 2>&1 || die "adb not found in PATH"

GRADLE_APP=""
for f in "$SRC/app/build.gradle" "$SRC/app/build.gradle.kts"; do
    [ -f "$f" ] && GRADLE_APP="$f" && break
done
[ -n "$GRADLE_APP" ] || die "app/build.gradle not found in $SRC"

APP_ID="$(grep -m1 -E '^[[:space:]]*applicationId' "$GRADLE_APP" | sed -nE 's/.*"([^"]+)".*/\1/p' || true)"
if [ -z "$APP_ID" ]; then
    APP_ID="$(grep -m1 -E '^[[:space:]]*applicationId' "$GRADLE_APP" | sed -nE "s/.*'([^']+)'.*/\1/p" || true)"
fi
if [ -z "$APP_ID" ]; then
    APP_ID="$(grep -m1 -E '^[[:space:]]*namespace' "$GRADLE_APP" | sed -nE 's/.*"([^"]+)".*/\1/p' || true)"
fi
if [ -z "$APP_ID" ]; then
    APP_ID="$(grep -m1 -E '^[[:space:]]*namespace' "$GRADLE_APP" | sed -nE "s/.*'([^']+)'.*/\1/p" || true)"
fi
[ -n "$APP_ID" ] || die "could not detect applicationId/namespace in $GRADLE_APP"
printf '%s' "$APP_ID" | grep -qE '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$' \
    || die "invalid applicationId detected: '$APP_ID'"

list_devices() {
    DEV_SERIALS=()
    DEV_LABELS=()
    local line serial state model kind
    while IFS= read -r line; do
        [ -n "$line" ] || continue
        serial="${line%%[[:space:]]*}"
        state="$(printf '%s\n' "$line" | awk '{print $2}')"
        [ "$state" = "device" ] || continue
        model="$(printf '%s\n' "$line" | sed -nE 's/.*[[:space:]]model:([^[:space:]]+).*/\1/p')"
        kind="phone"
        case "$serial" in emulator-*) kind="emulator" ;; esac
        DEV_SERIALS+=("$serial")
        DEV_LABELS+=("$serial  ${model:-unknown}  [$kind]")
    done < <(adb devices -l | tail -n +2)
}

is_connected() {
    local s
    for s in "${DEV_SERIALS[@]}"; do
        [ "$s" = "$1" ] && return 0
    done
    return 1
}

list_devices
[ "${#DEV_SERIALS[@]}" -gt 0 ] || die "no adb device connected"

# --- choose the install target -------------------------------------------------
TARGET="${TARGET_OPT:-${TARGET_SERIAL:-}}"
if [ -n "$TARGET" ]; then
    is_connected "$TARGET" || die "device '$TARGET' is not connected (see: adb devices -l)"
    if [ -n "$TARGET_OPT" ]; then
        mkdir -p "$STATE_DIR"
        printf '%s\n' "$TARGET" >"$STATE_FILE"
    fi
else
    REMEMBERED="$(cat "$STATE_FILE" 2>/dev/null || true)"
    if [ -n "$REMEMBERED" ] && is_connected "$REMEMBERED"; then
        TARGET="$REMEMBERED"
        info "Install target (remembered): $TARGET"
    elif [ "${#DEV_SERIALS[@]}" -eq 1 ]; then
        TARGET="${DEV_SERIALS[0]}"
        mkdir -p "$STATE_DIR"
        printf '%s\n' "$TARGET" >"$STATE_FILE"
        info "Install target: $TARGET"
    else
        info "Multiple adb devices connected:"
        for i in "${!DEV_SERIALS[@]}"; do
            printf '  %d) %s\n' "$((i + 1))" "${DEV_LABELS[$i]}" >&2
        done
        printf 'Select install device [1-%d]: ' "${#DEV_SERIALS[@]}" >&2
        CHOICE=""
        read -r CHOICE || die "no selection; pass --device SERIAL"
        CHOICE="${CHOICE//[[:space:]]/}"
        case "$CHOICE" in
            ''|*[!0-9]*) die "invalid selection: '$CHOICE'" ;;
        esac
        [ "$CHOICE" -ge 1 ] && [ "$CHOICE" -le "${#DEV_SERIALS[@]}" ] \
            || die "invalid selection: '$CHOICE'"
        TARGET="${DEV_SERIALS[$((CHOICE - 1))]}"
        mkdir -p "$STATE_DIR"
        printf '%s\n' "$TARGET" >"$STATE_FILE"
        info "Install target: $TARGET (remembered for next runs)"
    fi
fi

# --- choose the Termux build phone --------------------------------------------
BUILD="${BUILD_OPT:-${BUILD_SERIAL:-}}"
if [ -z "$BUILD" ] && [ -n "${TERMUX_HOST:-}" ]; then
    for s in "${DEV_SERIALS[@]}"; do
        case "$s" in
            "$TERMUX_HOST":*) BUILD="$s"; break ;;
        esac
    done
fi
if [ -z "$BUILD" ]; then
    PHYSICAL=()
    for s in "${DEV_SERIALS[@]}"; do
        case "$s" in emulator-*) ;; *) PHYSICAL+=("$s") ;; esac
    done
    [ "${#PHYSICAL[@]}" -eq 1 ] && BUILD="${PHYSICAL[0]}"
fi
[ -n "$BUILD" ] || die "could not identify the Termux phone; set BUILD_SERIAL or --build-device"
is_connected "$BUILD" || die "build device '$BUILD' is not connected"

TERMUX_USER="${TERMUX_USER:-u0_a423}"
TERMUX_PORT="${TERMUX_PORT:-8022}"
TERMUX_HOST="${TERMUX_HOST:-}"
if [ -z "$TERMUX_HOST" ]; then
    case "$BUILD" in
        *:*) TERMUX_HOST="${BUILD%%:*}" ;;
        *)
            TERMUX_HOST="127.0.0.1"
            adb -s "$BUILD" forward "tcp:$TERMUX_PORT" "tcp:$TERMUX_PORT" >/dev/null
            info "USB build phone: adb forward tcp:$TERMUX_PORT"
            ;;
    esac
fi

if [ -z "${TERMUX_KEY:-}" ]; then
    for k in "$HOME/.ssh/termux_phone" "$HOME/.ssh/id_ed25519"; do
        [ -f "$k" ] && TERMUX_KEY="$k" && break
    done
fi
[ -n "${TERMUX_KEY:-}" ] || die "no SSH key; set TERMUX_KEY"
SSH=(ssh -i "$TERMUX_KEY" -p "$TERMUX_PORT" -o ConnectTimeout=10 "$TERMUX_USER@$TERMUX_HOST")
"${SSH[@]}" true 2>/dev/null || die "SSH to $TERMUX_USER@$TERMUX_HOST:$TERMUX_PORT failed"

STAGE="/sdcard/Download"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/build-on-phone.XXXXXX")"
TMP="$TMP_DIR/$PROJECT-sync.tar.gz"
TMP_APK="$TMP_DIR/$PROJECT-debug.apk"
EXCLUDE_FILE="$TMP_DIR/excludes"
cleanup() { rm -rf "$TMP_DIR"; }
trap cleanup EXIT

wait_for_target() {
    local waited=0 boot
    boot="$(adb -s "$TARGET" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
    [ "$boot" = "1" ] && return 0
    info "Waiting for $TARGET to finish booting..."
    timeout 180 adb -s "$TARGET" wait-for-device >/dev/null 2>&1 || true
    while [ "$waited" -lt 180 ]; do
        boot="$(adb -s "$TARGET" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r' || true)"
        [ "$boot" = "1" ] && { info "Target ready."; return 0; }
        sleep 2
        waited=$((waited + 2))
    done
    warn "target $TARGET did not report boot_completed; continuing"
}

info "Build phone: $BUILD ($TERMUX_HOST) | install target: $TARGET"

timeout 60 adb -s "$BUILD" wait-for-device || die "build phone $BUILD is not available"

echo "[1/5] Packing source..."
printf '%s\n' \
    "$PROJECT_DIR_NAME/app/build" \
    "$PROJECT_DIR_NAME/build" \
    "$PROJECT_DIR_NAME/.gradle" \
    "$PROJECT_DIR_NAME/local.properties" \
    "$PROJECT_DIR_NAME/.idea" \
    "$PROJECT_DIR_NAME/screenshots" \
    "$PROJECT_DIR_NAME/.kotlin" >"$EXCLUDE_FILE"
tar --exclude-from="$EXCLUDE_FILE" -czf "$TMP" -C "$(dirname "$SRC")" "$PROJECT_DIR_NAME"
echo "     archive: $(du -h "$TMP" | awk '{print $1}')"

echo "[2/5] Pushing to phone..."
adb -s "$BUILD" shell mkdir -p "$STAGE" >/dev/null 2>&1 || true
adb -s "$BUILD" push "$TMP" "$STAGE/$PROJECT-sync.tar.gz" >/dev/null

echo "[3/5] Building on phone (incremental)..."
"${SSH[@]}" "cd \$HOME && tar -xzf '$STAGE/$PROJECT-sync.tar.gz' && cd '$PROJECT_DIR_NAME' && chmod +x ./gradlew && [ -f local.properties ] || echo sdk.dir=\$HOME/android-sdk > local.properties && bash ./gradlew assembleDebug --console=plain"

wait_for_target
echo "[4/5] Installing on $TARGET..."
"${SSH[@]}" "cp \$HOME/'$PROJECT_DIR_NAME'/app/build/outputs/apk/debug/app-debug.apk '$STAGE/$PROJECT-debug.apk'"
adb -s "$BUILD" pull "$STAGE/$PROJECT-debug.apk" "$TMP_APK" >/dev/null
INSTALL_OUT="$(adb -s "$TARGET" install -r "$TMP_APK" 2>&1)" || {
    printf '%s\n' "$INSTALL_OUT" >&2
    die "adb install failed"
}
printf '%s\n' "$INSTALL_OUT" | grep -q 'Success' || {
    printf '%s\n' "$INSTALL_OUT" >&2
    die "adb install failed"
}
echo "     installed"
adb -s "$BUILD" shell rm -f "$STAGE/$PROJECT-sync.tar.gz" "$STAGE/$PROJECT-debug.apk" >/dev/null 2>&1 || true

resolve_activity() {
    adb -s "$TARGET" shell cmd package resolve-activity --brief "$APP_ID" 2>/dev/null \
        | tr -d '\r' | tail -n1
}

launch_main_display() {
    local activity
    activity="$(resolve_activity || true)"
    adb -s "$TARGET" shell am force-stop "$APP_ID" >/dev/null 2>&1 || true
    if [ -n "$activity" ] && [ "$activity" != "No activity found" ]; then
        adb -s "$TARGET" shell am start -W -n "$activity" \
            | grep -E 'Status|Activity:' | head -2 || true
    else
        adb -s "$TARGET" shell monkey -p "$APP_ID" -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1 || true
    fi
}

if [ "$USE_SCRCPY" -eq 1 ]; then
    SCRCPY_BIN="${SCRCPY_BIN:-scrcpy}"
    if command -v "$SCRCPY_BIN" >/dev/null 2>&1; then
        echo "[5/5] Launching in scrcpy --new-display=$DISPLAY_SIZE..."
        SANITIZED="$(printf '%s' "$TARGET" | tr -c 'A-Za-z0-9_.-' '_')"
        PIDFILE="$STATE_DIR/scrcpy-$SANITIZED.pid"
        LOGFILE="$STATE_DIR/scrcpy-$SANITIZED.log"
        mkdir -p "$STATE_DIR"
        if [ -f "$PIDFILE" ]; then
            OLDPID="$(cat "$PIDFILE" 2>/dev/null || true)"
            [ -n "$OLDPID" ] && kill "$OLDPID" 2>/dev/null || true
            rm -f "$PIDFILE"
        fi
        nohup "$SCRCPY_BIN" -s "$TARGET" \
            --new-display="$DISPLAY_SIZE" \
            --start-app="+$APP_ID" \
            --no-audio \
            --max-size=1600 \
            --window-title="$PROJECT [$TARGET]" \
            >"$LOGFILE" 2>&1 &
        SCRCPY_PID=$!
        printf '%s\n' "$SCRCPY_PID" >"$PIDFILE"
        sleep 2
        if kill -0 "$SCRCPY_PID" 2>/dev/null; then
            echo "     scrcpy pid $SCRCPY_PID, log $LOGFILE"
        else
            warn "scrcpy exited; last lines:"
            tail -n 8 "$LOGFILE" >&2 || true
            rm -f "$PIDFILE"
            launch_main_display
        fi
    else
        warn "scrcpy not found; launching on the main display instead"
        launch_main_display
    fi
else
    echo "[5/5] Launching on the main display..."
    launch_main_display
fi

echo
echo "Done: $PROJECT ($APP_ID)"
echo "  build phone: $BUILD"
echo "  installed:   $TARGET"
echo "  state:       $STATE_FILE"
