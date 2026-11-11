# Teleprompter

Blank Compose project (Android Studio "Empty Activity" style) wired for this
toolchain.

## Layout
- PC is the editor. The phone is the build machine.
- `build-on-phone` (global) or `./scripts/build-on-phone.sh` runs the loop:
  pack -> push -> incremental build on the phone (over SSH) -> install on the
  selected adb device -> relaunch.
- With several adb devices (phone + emulator), the install target is chosen
  once and remembered in `~/.local/state/build-on-phone/target-serial`.
- By default the app opens in a scrcpy virtual display sized `1080x2400/420`.
  Override with `--display-size 1600x2560/320`, or use `--no-scrcpy`.
- `new-avd` (global) interactively creates and boots an emulator: API level ->
  device profile -> RAM, with download/boot progress bars.
- First build creates `local.properties` on the phone and rebuilds the phone
  project copy if it is missing.
- `scripts/start-emulator.sh` boots a low-RAM API 30 AVD for quick checks.
- `scripts/check.sh` runs Android Lint (NewApi) + ktlint.

## One-time phone setup
1. On the phone: `pkg install openssh`, `ssh-keygen -A`, `sshd`.
2. Put this PC's SSH public key into the phone's `~/.ssh/authorized_keys`.

## Everyday loop
Edit, then from the project root (or any subdirectory):

    build-on-phone

or use the in-project copy:

    ./scripts/build-on-phone.sh

Useful options:

    build-on-phone --device emulator-5554        # pick target, remembered
    build-on-phone --forget-device               # forget the target
    build-on-phone --display-size 1600x2560/320  # custom virtual display
    build-on-phone --no-scrcpy                   # main display instead

Create a new emulator (downloads the system image if needed):

    new-avd
    new-avd --api 30 --device medium_phone --ram 2048

Typical edit-rebuild-install cycle is ~15-20s on this device.
