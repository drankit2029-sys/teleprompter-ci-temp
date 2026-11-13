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
**Phase 2 — prompter core: complete. Checkpoint `checkpoint/phase-2` created.**

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

Phase 2 implementation and acceptance (routine UI on scrcpy virtual displays;
transparent overlay, camera overlay, and final PiP checks on the main display
with the user present):

- Per-script stage settings are stored in each `.tpscript` `stage` object and
  restored with the script; reading defaults remain in DataStore.
- Stage panel is split into Playback, Text, Look, and Window tabs. Panel height
  is capped at 46% of portrait height and 66% of landscape height so the focus
  line remains visible.
- Verified speed presets/slider, ±5s seek, restart, progress scrubbing, manual
  drag while paused, countdown Off/3s/5s with cancel, loop-at-end auto replay,
  end-of-script Replay/Exit, and reading-progress restoration.
- Verified text size, line spacing, side margins, sans/serif/mono,
  regular/medium/bold, center/left, five text colors, five background presets,
  focus-line position, edge fade, horizontal/vertical mirrors, and adaptive
  contrast while retaining manual colors as fallback.
- Lock hides chrome, blocks stage touches, keeps hardware controls active, and
  unlocks via long-press badge or `L`.
- Hardware controls verified: space/Enter play-pause, arrows seek, volume keys
  adjust speed, Page Up/Down change text size, `R` restart, `M` mirror, `L`
  lock, and media play/pause. A platform `MediaSession` exposes play/pause,
  stop, seek, fast-forward, and rewind.
- Keep-awake toggle verified by checking the window's `FLAG_KEEP_SCREEN_ON`
  bit appears and disappears.
- PiP entry and script rendering verified with the final styling; PiP system
  actions, compact stage rendering, and expanding PiP via the system fullscreen
  button pass on the main display.
- Transparent overlay final acceptance passed: styling matches the stage
  (fonts, weights, colors, margins, spacing, alignment), horizontal/vertical
  mirrors render correctly, and the overlay controls/script layer behave
  correctly over camera and third-party apps.
- Projection-cancel fallback uses manual high-contrast colors with a toast;
  a clean stop removes both overlay windows and the foreground service, and a
  subsequent start works normally.
- Overlay is display-targeted: starting it from the tablet display creates an
  overlay on that display, and starting it from the main display targets the
  main display. Secondary displays use manual colors because adaptive contrast
  is main-display-only.
- Secondary-display stages keep the system bars visible so Back/Home/Recents
  remain reachable; main-display immersive behavior is unchanged.
- `scripts/check.sh --full-lint` passes after suppressing the
  `ComponentActivity.dispatchKeyEvent` restricted-API lint false positive.


## Environment / test device

- Current build/install target: USB serial `RZCY61Z8TPD`. Wi-Fi ADB addresses
  are unreliable across networks; prefer USB when available.
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
  adb -s "$PHONE_SERIAL" forward tcp:8022 tcp:8022
  TERMUX_HOST=127.0.0.1 ./scripts/build-on-phone.sh -d "$PHONE_SERIAL"
  TERMUX_HOST=127.0.0.1 ./scripts/check.sh
  ```

  Termux `sshd` must be running; start it from a dedicated Termux session with
  `sshd`. The SSH user is `u0_a423`; the key is `~/.ssh/termux_phone`.
- If the Termux host key changes after a reinstall, remove the stale key:
  `ssh-keygen -R '[127.0.0.1]:8022'`, then connect once with
  `StrictHostKeyChecking=accept-new`.
- Overlay windows are display-targeted: `MainActivity` passes its display ID
  to `PrompterOverlayService`, which creates a display context so the script
  and control-bar windows attach to the display hosting the app. Adaptive
  contrast remains main-display-only; secondary displays fall back to manual
  colors with a toast. Main-display overlay/camera acceptance still needs the
  user present.
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
21. **Stage settings are per script.** Speed, text size, focus/edge fade,
    margins, spacing, font, weight, alignment, colors, mirrors, countdown, loop,
    and keep-awake live in the optional `stage` object of each `.tpscript`.
    DataStore holds only the defaults for new scripts. This keeps a script's
    look and presentation behavior together and makes backup/restore complete.
22. **One playback state for every surface.** `PrompterSession` remains the
    single source for fullscreen, PiP, and overlay scrolling, countdown, lock,
    mirror, colors, and progress. `StageScreen` and the overlay only render and
    issue commands; this prevents the modes from drifting apart.
23. **Countdown semantics.** A countdown runs only when playback starts from the
    top or after reaching the end. Resuming mid-script starts immediately.
    Cancelling the countdown leaves the script paused at the top. Loop mode
    replays from the top automatically without a countdown.
24. **Lock protects the performance surface.** Locking hides chrome and ignores
    stage touch input, but hardware keys, volume keys, media buttons, and the
    MediaSession stay active. Long-pressing the lock badge or pressing `L`
    unlocks.
25. **Hardware mapping is global on the stage.** `MainActivity` intercepts keys
    before Compose focus handling: space/Enter toggles play, arrows/seek media
    keys seek, volume keys adjust speed, Page Up/Down change text size, `R`
    restarts, `M` mirrors, `L` locks, and media stop pauses. A platform
    `MediaSession` mirrors the same state to headset and system controls.
26. **Stage panel layout.** Playback/Text/Look/Window tabs keep controls
    discoverable without hiding the script. The panel is capped at 46% of
    portrait height and 66% of landscape height so the focus line stays visible;
    speed controls live on the Playback tab and cues are deferred.
27. **Overlay follows the app's display.** `MainActivity` passes its display ID
    and the overlay service creates a display context for that display instead
    of always using the default display. Adaptive contrast stays main-display-
    only, so secondary displays use manual colors. Secondary-display stages also
    keep system bars visible, preserving Back/Home/Recents; the main display
    keeps its immersive stage.

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

## Phase 2 implementation map

- `data/StageSettings.kt` — per-script stage model, sanitized bounds, font/
  weight/alignment enums, and text/background ARGB maps.
- `data/Script.kt` — optional `stage` settings carried with each script.
- `data/ScriptRepository.kt` — `stage` JSON serialization inside `.tpscript`
  and `updateStageSettings` persistence.
- `prompter/PrompterSession.kt` — countdown, loop, lock, keep-awake, seek,
  manual scroll, speed/font nudges, mirror, and per-script persistence hooks.
- `prompter/PrompterStage.kt` — font family/weight, alignment, margins, line
  spacing, colors, background presets, mirror transforms, and focus mask.
- `ui/StageScreen.kt` — full stage chrome, Playback/Text/Look/Window panel,
  progress scrubber, transport, countdown/end overlays, lock badge, and
  appearance controls; replaces `Phase0StageScreen.kt`.
- `MainActivity.kt` — stage-settings load/save, keep-awake window flag,
  hardware-key handling, MediaSession, PiP/stage wiring, display-ID plumbing,
  and secondary-display system-bar handling.
- `overlay/PrompterOverlayService.kt` — display-context overlay windows,
  main-display-only adaptive projection, and manual-color fallback on secondary
  displays.
- `overlay/PrompterOverlayView.kt` — overlay rendering updated for the same
  fonts, weights, colors, margins, spacing, alignment, and mirrors.
- `scripts/check.sh --full-lint` — passes on the phone after the final changes.

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
scrcpy -s "$PHONE_SERIAL" --no-audio
```

Useful UI/test commands:

```bash
adb -s "$PHONE_SERIAL" shell am start -n \
  com.accessoryTools.teleprompter/.MainActivity --ez open_prompter true
adb -s "$PHONE_SERIAL" shell appops set \
  com.accessoryTools.teleprompter SYSTEM_ALERT_WINDOW allow
adb -s "$PHONE_SERIAL" exec-out screencap -p > /tmp/opencode/teleprompter.png
# debug-only PiP resize test:
adb -s "$PHONE_SERIAL" shell am broadcast \
  -a com.accessoryTools.teleprompter.debug.CYCLE_PIP_SIZE \
  -n com.accessoryTools.teleprompter/.debug.PipDebugReceiver
```

## Checkpoints

- `checkpoint/phase-0-baseline` — template + plan before Phase 0 code.
- `checkpoint/phase-0` — Phase 0 spike complete and accepted on the target
  device (2026-11-12).
- `checkpoint/phase-1` — library, editor, import, export, backup, and intent
  intake complete and accepted on the target device (2026-11-12).
- `checkpoint/phase-2` — prompter core, per-script stage settings, mirrors,
  countdown/lock/loop, keep-awake, hardware/media controls, PiP, and
  display-targeted transparent overlay complete and accepted on the target
  device (2026-11-13).
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
- Transparent overlay acceptance is main-display or display-targeted; starting
  from a secondary display places the overlay there with manual colors, and
  adaptive contrast remains main-display-only.
- If the host display is removed while the overlay is running, the overlay
  windows may be destroyed with that display; restart the overlay from the app
  on the target display.

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
- 2026-11-12 — Phase 2 implemented: per-script `stage` settings, shared
  countdown/loop/lock/seek/speed state, Playback/Text/Look/Window stage panel,
  appearance rendering in stage and overlay, keep-awake, hardware keys, and
  MediaSession. End/replay, lock, loop, countdown, seek, manual scroll, mirrors,
  font/color presets, and restart persistence verified on a scrcpy display.
- 2026-11-12 — Phase 2 final checks: `scripts/check.sh --full-lint` passes after
  suppressing the `dispatchKeyEvent` restricted-API false positive. Final build
  installed and launched. Demo script reset to defaults: 140 WPM, 0:00, 46 sp,
  3s countdown, loop off, keep-awake on, warm/black, sans/medium/center, mirrors
  off. PiP system fullscreen expand works poorly on the virtual display; retest
  on the main display. Transparent overlay/adaptive contrast final acceptance
  and `checkpoint/phase-2` remain.
- 2026-11-13 — Display-targeted overlay implemented: `MainActivity` passes its
  display ID, `PrompterOverlayService` builds a display context for secondary
  displays, adaptive projection is main-display-only, and secondary stages keep
  system bars visible. User verified the overlay appears on the tablet display
  when started there and on the main display when started there. Full lint and
  ktlint pass; APK built/installed over USB.
- 2026-11-13 — User completed the remaining Phase 2 acceptance: styling and
  mirrors in the overlay, projection-cancel fallback, clean stop, and PiP
  expand all pass. Demo script and DataStore defaults reset to 140 WPM, 46 sp,
  0:00, 3s countdown, loop off, keep-awake on, warm/black,
  sans/medium/center, mirrors off, adaptive contrast on; verified on a fresh
  scrcpy display. Phase 2 accepted; `checkpoint/phase-2` created.

