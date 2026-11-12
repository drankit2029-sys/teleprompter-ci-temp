# Teleprompter — Agent Notes

This file is the working memory for implementation. Update it after every
meaningful milestone, decision, test run, or checkpoint.

## Goal

Build a professional, local-first Android teleprompter with:

- saved script library and editor;
- TXT/Markdown/DOCX (and later more) import;
- sharing/export;
- fullscreen prompter with speed/text-size controls, scrubbable values, mirror,
  countdown, lock, and focus gradient;
- picture-in-picture floating mode for reading over a camera app;
- transparent system overlay with a non-touchable script layer and touchable
  floating control bar;
- optional adaptive text contrast sampled from the screen below;
- responsive support for phones, tablets, foldables, and unusual resolutions.

Target: `compileSdk 37`, `minSdk 28`, `targetSdk 37`.

Full plan: `plan.md`.

## Current status

**Phase 0 — technical spike: complete. Checkpoint `checkpoint/phase-0` created.**
**Phase 1 — library, editor, import, export: complete. Checkpoint `checkpoint/phase-1` created.**

Phase 0 acceptance completed on the physical target:

- Fullscreen stage verified: playback, progress/position readout, restart,
  seek to 0–99%, end-of-script display, chrome hide/show.
- Stage measurement fixed: the script is measured with unbounded height and
  pinned to the top, so the whole document scrolls instead of being clipped.
- PiP entry verified; native double-tap resize and the explicit aspect cycle
  `9:16 → 3:4 → 1:1 → 4:3 → 16:9` verified. `setSeamlessResizeEnabled(false)`
  keeps text crisp during resizes.
- PiP over the camera app verified on the target device.
- Transparent overlay verified over the camera and over third-party apps:
  script visible, touches pass through the script layer, floating control bar
  visible and draggable.
- Adaptive contrast verified over dark and bright camera scenes; no
  self-sampling flicker observed.
- Foreground-service notification verified, including expanded
  `Play/pause` and `Stop` actions.
- Projection-denied fallback, overlay permission denial/revocation, and the
  remaining notification-state matrix are implemented; edge-case hardening
  moves to the Phase 3/4 permission matrix.
- Responsive home verified at compact 480×800/240, 720×1280/320, 1280×720/320
  and wide 1600×2560/320, 2560×1600/320. Wide stage chrome is centered,
  limited to 1100dp, uses a two-column control panel, and is opaque.
- Text wrapping fixed: the demo script no longer contains hard source line
  breaks; Compose uses `LineBreak.Simple` + `Hyphens.None` and the overlay uses
  `LineBreak.BREAK_STRATEGY_SIMPLE`, so ordinary words are no longer split or
  hyphenated across lines.
- `scripts/check.sh --full-lint` passes on the phone.

Phase 1 acceptance completed on the target (routine UI on a scrcpy virtual
display):

- Scripts survive process death and are stored as atomic `.tpscript` JSON files
  in app-private storage; the library reseeds one demo script on first launch.
- Library search, favorites filter, recent/title/newest sorting, empty states,
  stats, present/edit/share/duplicate, and delete/undo verified.
- Editor title/content autosave, undo/redo, find/replace (including replace all),
  statistics, share text, and Markdown/TXT export verified.
- Import verified for TXT, HTML, and DOCX through the file picker and
  `ACTION_VIEW`; clipboard paste and `ACTION_SEND` text intake verified.
- Backup ZIP restore reports the correct count and now skips exact copies;
  conflicting script IDs are restored with a `(restored)` title suffix instead
  of duplicating an existing title.
- Present/stage works after the navigation refactor, restores saved progress,
  exits back to the library, and PiP entry still works. The transparent overlay
  service is unchanged from Phase 0; its settings status/permission flow renders
  correctly.
- `scripts/check.sh` and `scripts/check.sh --full-lint` pass on the phone.


## Environment / test device

- Last known build/install target: `10.94.150.40:5555` over Wi-Fi.
- USB serial: `RZCY61Z8TPD` (use USB when available; it has lower latency).
- Device: Samsung SM-M366B
- Android 17 / API 37
- Physical display: 1080×2340, 450 dpi
- Primary build command: `build-on-phone`
- Routine UI tests run on a dedicated scrcpy virtual display created by
  `build-on-phone` (`--new-display`). Do not launch or test on the main display
  while the user is working there; use `--no-scrcpy` only when the main display
  is explicitly needed for overlay/camera acceptance.
- The user may be working in Termux on the main display. Use a separate Termux
  session for build/SSH setup; do not type into the user's active session.
- For Wi-Fi ADB, SSH to Termux goes through an adb forward:

  ```bash
  adb -s 10.94.150.40:5555 forward tcp:8022 tcp:8022
  TERMUX_HOST=127.0.0.1 ./scripts/build-on-phone.sh
  TERMUX_HOST=127.0.0.1 ./scripts/check.sh
  ```

  Termux `sshd` must be running; start it from a dedicated Termux session with
  `sshd`. The SSH user is `u0_a423`; the key is `~/.ssh/termux_phone`.
- If the Termux host key changes after a reinstall, remove the stale key:
  `ssh-keygen -R '[127.0.0.1]:8022'`, then connect once with
  `StrictHostKeyChecking=accept-new`.
- Important: overlay windows attach to the default display. Transparent-overlay
  and camera-overlay acceptance must be done on the main display or physical
  phone, with the user present, not on a scrcpy virtual display.
- `scripts/check.sh` runs `lintDebug` + ktlint **on the phone** via
  `build-on-phone.sh --check`; it does not use the PC for Gradle work.
- `scripts/check.sh --full-lint` runs the complete lint suite instead of the
  scoped `NewApi` / `InlinedApi` / `OldTargetApi` checks.
- The repo script is the source of truth. After editing it, run
  `./scripts/build-on-phone.sh --install-global` so the global
  `~/Android/Sdk/cmdline-tools/latest/bin/build-on-phone` command matches.
- Builds are always done on the phone. Installing a locally built APK causes
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE` (different signing keystore).

## Key decisions

1. **PiP and transparency are separate modes.** Android PiP is opaque and
   system-managed. Transparent mode is a `TYPE_APPLICATION_OVERLAY` foreground
   service with a touch-through script window.
2. **Adaptive contrast requires MediaProjection.** Capture is low-resolution,
   sampled at ~1.4 Hz, processed in memory, never stored, and optional. Manual
   high-contrast colors are the fallback.
3. **One shared `PrompterSession`** drives fullscreen, PiP, and overlay. It uses
   `Choreographer` so scrolling is frame-synced without a Compose frame clock
   in a service.
4. **Phase 0 overlay uses a custom Android `View` for text**, not Compose.
   This keeps the foreground-service overlay simple and gives precise control
   over touch flags, gradients, outlines, and sampling. Shared Compose rendering
   can replace it later if the abstraction is worth it.
5. **Phase 0 UI is intentionally a spike** with sliders and simple buttons.
   Phase 2 replaces them with scrubbable value controls.
6. **File-first storage** planned for Phase 1; no Room/KSP initially.
7. **Ktlint `function-naming` is disabled** because Compose entry points use
   PascalCase.
8. **Focus gradient** masks only the text layer in an offscreen layer; focus
   defaults to the vertical center and edges retain configurable visibility.
9. **Adaptive color** chooses white text on dark scrims and black text on light
   scrims, using median luminance and hysteresis. Outline contrast is always
   drawn.
10. **PiP resizing has two paths**: system pinch/double-tap and an explicit
    in-window "Resize" action cycling `9:16 → 3:4 → 1:1 → 4:3 → 16:9`.
    `setSeamlessResizeEnabled(false)` keeps text crisp.
11. **PiP actions bypass Compose.** The resize action sends an app-local
    broadcast that `MainActivity` handles directly, because a paused PiP
    activity may not recompose.
12. **Debug-only adb hook.** `PipDebugReceiver` is exported only in the debug
    source set so PiP actions can be tested without weakening the shipped,
    non-exported `PrompterCommandReceiver`.
13. **Responsive home breakpoint.** Use the two-pane tablet dashboard only when
    width ≥600dp **and** height ≥700dp. Short landscape phones keep the compact
    bottom-bar layout; height alone must not force the tablet composition.
14. **Bounded reading measure.** Stage text and overlay text are centered and
    capped at 1000dp; wide chrome is centered and capped at 1100dp, so tablets
    do not stretch lines across the full 2560px panel.
15. **Checks run on the phone.** `scripts/check.sh` delegates to
    `build-on-phone.sh --check`; lint and ktlint run in parallel on the target
    device. Routine lint is scoped to `NewApi`, `InlinedApi`, and `OldTargetApi`;
    `--full-lint` runs the complete suite.
16. **No mid-word line breaking.** Stage text uses Compose `LineBreak.Simple`
    with `Hyphens.None`; overlay text uses
    `LineBreaker.BREAK_STRATEGY_SIMPLE` with hyphenation disabled. Scripts are
    stored as continuous paragraphs, not hard-wrapped source lines.
17. **File-first script storage.** Each script is a `.tpscript` JSON file under
    `files/scripts`; writes go to a temporary file and are moved into place
    atomically. Deletes move files to `files/trash` so Undo can republish them.
    Settings use DataStore preferences.
18. **Import is defensive.** TXT/Markdown use charset fallback; HTML is stripped;
    DOCX reads `word/document.xml`; ODT reads `content.xml`. Backups and imports
    enforce entry-count, size, and title limits. SAF URIs keep the original file
    untouched.
19. **Backup restore merges instead of replacing.** Restore skips scripts whose
    content already exists, assigns a new ID when the incoming ID collides, and
    suffixes the title with `(restored)` when needed. This makes repeated
    restores idempotent without deleting user edits.
20. **Navigation owns screen state.** `MainActivity` hosts Navigation Compose
    routes `library`, `editor/{scriptId}`, `settings`, and `stage`, and handles
    incoming share/open intents plus lifecycle progress persistence. The
    repositories live in `TeleprompterApp`, so every screen sees the same state.

## Phase 0 implementation map

- `MainActivity.kt` — permissions, PiP params/actions/aspect cycling, stage/home
  switching, immersive mode, overlay launch flow.
- `TeleprompterApp.kt` — application entry.
- `prompter/PrompterSession.kt` — shared state, ticker, WPM→pixel speed,
  gradient/layout state, adaptive colors.
- `prompter/PrompterStage.kt` — Compose text stage + focus mask.
- `ui/TeleprompterTheme.kt` — dark Material 3 theme.
- `ui/Phase0HomeScreen.kt` — demo script, settings, overlay/permission cards.
- `ui/Phase0StageScreen.kt` — fullscreen/PiP chrome and basic controls.
- `overlay/PrompterOverlayService.kt` — FGS, notification, overlay windows,
  projection lifecycle, sampler.
- `overlay/PrompterOverlayView.kt` — transparent canvas text overlay.
- `overlay/OverlayControlBar.kt` — draggable touchable control bar.
- `platform/PrompterCommandReceiver.kt` — PiP media-key/remote commands and
  resize forwarding.
- `debug/PipDebugReceiver.kt` — debug-only exported adb hook for PiP resize
  tests; not present in release builds.

## Phase 1 implementation map

- `data/Script.kt` — persisted script model, progress, favorites, statistics.
- `data/ScriptRepository.kt` — atomic `.tpscript` storage, trash/undo, backup
  ZIP creation and merge restore.
- `data/ScriptImporter.kt` — TXT/Markdown/HTML/DOCX/ODT extraction and charset
  fallback with size limits.
- `data/ScriptSharing.kt` — text/file sharing and SAF export.
- `data/SettingsRepository.kt` — DataStore reading defaults and library sort.
- `ui/library/LibraryScreen.kt` — adaptive library grid, search, favorites,
  sorting, import, paste, cards, duplicate/delete/undo, empty states.
- `ui/editor/EditorScreen.kt` — title/content editor, debounced autosave,
  undo/redo, find/replace, stats, share, Markdown/TXT export, Present.
- `ui/settings/SettingsScreen.kt` — reading defaults, adaptive contrast,
  overlay status/permission/start-stop, backup/restore.
- `MainActivity.kt` — Navigation Compose host, intent intake, PiP, progress
  persistence.
- `TeleprompterApp.kt` — repository ownership and one-time demo seed.
- `prompter/PrompterSession.kt` — loads scripts, applies persisted settings,
  saves and restores reading progress.

## Permission / manifest notes

- `SYSTEM_ALERT_WINDOW`
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_SPECIAL_USE`
- `FOREGROUND_SERVICE_MEDIA_PROJECTION`
- `POST_NOTIFICATIONS`
- Service declares `specialUse|mediaProjection` and includes the Play Store
  special-use subtype property.
- `MainActivity` declares PiP support and config changes.
- Legacy `REQUEST_INSTALL_PACKAGES` and `INTERNET` are removed.
- The old `network_security_config.xml` is no longer referenced.

## Build / test commands

```bash
./scripts/check.sh
./scripts/check.sh --full-lint
# tablet portrait / landscape, small phone, normal phone, landscape phone:
build-on-phone --display-size 1600x2560/320
build-on-phone --display-size 2560x1600/320
build-on-phone --display-size 480x800/240
build-on-phone --display-size 720x1280/320
build-on-phone --display-size 1280x720/320
# window/overlay testing on main display:
build-on-phone --no-scrcpy
# or mirror main display:
scrcpy -s 10.232.147.72:5555 --no-audio
```

Useful UI/test commands:

```bash
adb -s 10.232.147.72:5555 shell am start -n \
  com.accessoryTools.teleprompter/.MainActivity --ez open_prompter true
adb -s 10.232.147.72:5555 shell appops set \
  com.accessoryTools.teleprompter SYSTEM_ALERT_WINDOW allow
adb -s 10.232.147.72:5555 exec-out screencap -p > /tmp/opencode/teleprompter.png
# debug-only PiP resize test:
adb -s 10.232.147.72:5555 shell am broadcast \
  -a com.accessoryTools.teleprompter.debug.CYCLE_PIP_SIZE \
  -n com.accessoryTools.teleprompter/.debug.PipDebugReceiver
```

## Checkpoints

- `checkpoint/phase-0-baseline` — template + plan before Phase 0 code.
- `checkpoint/phase-0` — Phase 0 spike complete and accepted on the target
  device (2026-11-12).
- `checkpoint/phase-1` — library, editor, import, export, backup, and intent
  intake complete and accepted on the target device (2026-11-12).
- Create future checkpoints only after compile, `scripts/check.sh`, install,
  launch, and the phase acceptance flow pass.

Restore:

```bash
git switch -c recover-phase-0 checkpoint/phase-0
```

## Known risks / open items

- PiP aspect ratio and behavior vary by OEM; validate on the Samsung target.
- Camera apps may hide application overlays; if so, transparent mode cannot be
  guaranteed over that specific camera and PiP remains the fallback.
- Screen capture of camera previews may be protected/black; adaptive contrast
  then falls back to manual colors.
- Current adaptive sampler uses median luminance over the full downscaled frame
  and does not yet exclude overlay glyph boxes. Watch for feedback/flicker.
- Overlay text is anti-aliased and drawn into an offscreen layer; verify that
  `DST_IN` masking works in the device’s hardware-accelerated overlay window.
- The current overlay redraw loop is active only while playing; validate control
  labels and initial draw in all state transitions.
- Foreground-service notification may require POST_NOTIFICATIONS permission on
  Android 13+; the launch flow requests it.
- Phone/tablet resolution and orientation matrix passes on virtual displays;
  split-screen and foldable posture move to the Phase 3/5 posture matrix.
- Existing lint warnings remain (app icon, unused XML resources, touch
  accessibility, string resources); address during Phase 5 polish.
- DOCX/ODT import extracts document text only; embedded images, tables, and
  styling are intentionally out of scope.
- Phase 1 restore is merge-oriented and never deletes existing scripts; users
  who want a clean restore must clear app data first.
- Transparent overlay acceptance remains a main-display test; the Phase 1
  settings entry point was verified, but the overlay service itself is unchanged
  from its Phase 0 acceptance.

## Update log

- 2026-11-11 — Baseline checkpoint created. Phase 0 code implemented and locally
  compiled/linted. `agents.md` added. Device verification next.
- 2026-11-11 — Device build/install via `build-on-phone` verified. Fullscreen
  playback, seek, restart, progress and end-of-script checked.
- 2026-11-11 — Fixed clipped stage: unbounded-height text measurement, top
  alignment. Whole script now scrolls and seek matches rendered position.
- 2026-11-11 — Added PiP aspect-cycle action, app-local broadcast routing, and
  debug-only adb resize hook. `scripts/check.sh` passes. Resize verified via
  debug broadcast (9:16 → 3:4, bounds updated).
- 2026-11-11 — Documented SSH-to-Termux recovery and build-signing constraint.
  Overlay/camera acceptance remains next.
- 2026-11-11 — PiP resize verified end-to-end (9:16 → 3:4, window bounds
  changed). Attempted main-display overlay launch; blocked because the phone
  locked itself and requires a secure unlock. Main-display overlay/camera
  tests paused until the device is unlocked.
- 2026-11-11 — Added responsive home layouts (full-height two-pane tablet
  dashboard, compact bottom-bar phones) and bounded reading/chrome widths.
  Verified compact 480×800/240, 720×1280/320, 1280×720/320; wide 1600×2560/320,
  2560×1600/320. Stage chrome made opaque for text legibility.
- 2026-11-11 — Optimized `build-on-phone` (no fixed scrcpy wait; same-device
  internal copy + `pm install`; Gradle configuration cache) and scoped routine
  phone-side lint. No-change ~8–13s; source edits roughly 25–46s.
- 2026-11-12 — User completed the remaining physical acceptance checks:
  overlay over camera/third-party apps, touch-through, draggable control bar,
  and adaptive contrast over bright/dark scenes. Phase 0 accepted.
- 2026-11-12 — Fixed mid-word line breaking: removed hard source wrapping from
  the demo script, set Compose `LineBreak.Simple` + `Hyphens.None`, and set the
  overlay `StaticLayout` to `LineBreaker.BREAK_STRATEGY_SIMPLE`. Verified the
  fullscreen stage keeps words intact. Full lint passes.
- 2026-11-12 — Created `checkpoint/phase-0` (commit + annotated tag). Started
  Phase 1.
- 2026-11-12 — Phase 1 implemented: file-first `.tpscript` repository with
  atomic writes/trash, DataStore settings, Navigation Compose routes, adaptive
  library, editor with autosave/undo/find-replace, TXT/HTML/DOCX/ODT import,
  clipboard paste, share/export, backup/restore, and `ACTION_SEND`/`ACTION_VIEW`
  intake.
- 2026-11-12 — Phase 1 acceptance: library search/favorites/sort/delete-undo,
  editor undo/redo and find/replace, paste, TXT/HTML/DOCX import, share/export,
  restore merge, Present/stage, progress resume, and PiP all verified. Fixed
  delete not republishing the library and restore duplicating identical scripts.
  `check.sh` and `--full-lint` pass. Created `checkpoint/phase-1`.

