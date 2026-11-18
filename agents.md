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

**Phase 5b — polish, overlay region, AdMob test ads, and neo-brutalist UI: implemented, not yet accepted.**

- Word-safe wrapping is now hyphen-aware (explicit `WordWrap`), shared by the
  Compose stage and the overlay `StaticLayout`.
- Overlay region selection/resizing is persisted as normalized fractions, with
  a Stage → Window live preview, presets, in-overlay handles, and live updates
  of a running overlay.
- The overlay control bar fits 360dp displays and now auto-collapses into a
  "TP" square pinned to the nearest vertical edge after 5s of inactivity
  (skipped during area adjustment); tapping the square reexpands the bar.
- AdMob test ads: banners are a 60dp inline-adaptive slot labelled
  `ADVERTISEMENT`, 10% of screen height on narrow phones, rotating every 30s
  only while their host is resumed, and never outside Library/Settings.
- Interstitials are policy-hardened: they show **before** the stage→library
  break page (never on app exit or after a page load), at most once per two
  stage-exit actions with a 60s minimum interval, and are skipped during
  PiP/overlay/lock/background. Google's AdMob guidance was consulted; the
  "two user actions" rule is unit-tested.
- Neo-brutalist Library/Settings/Editor/Stage/overlay restyle is implemented;
  scrcpy verification is ongoing; main-display overlay/camera acceptance and
  the acceptance commit are still pending.

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
28. **Banner ads are isolated and separated.** Library/Settings show a 60dp
    inline-adaptive banner in a bordered `ADVERTISEMENT` frame (one AdView
    reused per width, fresh request every 30s only while the host is resumed);
    the slot is hidden during imports/pastes/backups/restores and never
    appears in the editor, stage, PiP, or the transparent overlay.
29. **Interstitials are a page-break ad, not a page-load ad.** They are shown
    before Navigation pops the stage to the library, never on app exit and
    never after the library is returned. `InterstitialGate` enforces at least
    two stage-exit actions between ads and a 60s minimum interval, and the ad
    is skipped when recording-like behavior (PiP, overlay, lock, background)
    is active. Google's "at most one interstitial after every two user
    actions" rule is enforced in `ads/InterstitialGate.kt` and unit-tested.
30. **Overlay controls are self-cleaning.** The floating control bar collapses
    into a "TP" square after 5s of inactivity and pins to the nearest vertical
    screen edge; tapping the square reexpands the bar. Collapse is skipped
    while the overlay area is being adjusted so the adjust UI is not hidden.

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
- 2026-11-13 — Phase 5b implemented: hyphen-aware `WordWrap` (preferred
  hyphen break points for overlong compounds), 60dp inline-adaptive Library
  and Settings banners (`ADVERTISEMENT` label, resumed-only 30s rotation),
  policy-hardened interstitial (before the stage→library break page,
  `InterstitialGate` requires ≥2 stage-exit actions and ≥60s between ads,
  skipped during PiP/overlay/lock/background), and an auto-collapsing overlay
  control bar that pins a "TP" square to the nearest vertical edge after 5s
  of inactivity (reexpand on tap; collapse skipped during area adjustment).
  Verified on a scrcpy 720×1600/320 display: Top-area preset persists across
  restart, interstitial fires on the second exit (and not on a fast double
  exit within 60s), interstitial is skipped while the overlay runs, and full
  phone lint + ktlint pass. Main-display overlay/camera acceptance and the
  acceptance commit remain.

- 2026-11-14 — Reported-bug fixes: inline overlay speed-status chip replaces the
  rate-limited Toast (control bar now register taps reliably); both stage
  and overlay renderers top-align the script; stage panel is resizable via a
  grip on its top edge; the overlay-area editor only consumes gestures
  starting on the region/handles so the Window tab can scroll to the very
  bottom; editor lifts content and Present bar above the IME with
  `imePadding()`; landscape/short-height Library uses a scrollable
  full-width adaptive grid; banner sits above the navigation bar and the
  overlay window respects the display cutout. Verification (main display,
  tablet matrix) pending with the user; commit pending acceptance.

- 2026-11-14 — Audit-fix round applied and re-verified on device: ~24 fixes
  from the two audit passes (see `docs/bug-report.md`). Compose stage RIGHT
  alignment is now physical `TextAlign.Right`. Trash is purged after 30 days
  on repository init; the library cache is updated incrementally on save
  (no more O(library) re-parse); interstitial budget burns only when the ad
  actually shows; the banner degrades to "Advertisement unavailable" on SDK
  init failure; unreadable preset combos (BLACK+INK, PAPER+light text) are
  filtered from the pickers; editor undo is debounced to word boundaries;
  sort/favorites prefs write atomically via `rememberUpdatedState`.
  Verified: scrcpy virtual display (1080x2400/420) for Look/Text tabs,
  translucent panel, banner rotation; main display + uiautomator dump for
  RTL Arabic stage rendering (correct bidi, word-safe wrap), Right alignment,
  banner label, library resume. RTL seed script removed afterwards —
  library holds 7 scripts. NOT device-verified: HSV custom-color picker and
  SAF custom-font import end-to-end; Phase 5a interstitial fired-on-second-exit
  only re-checked via unit test on this round. Full `check.sh --full-lint`
  re-run passed on the phone (lintDebug clean, ktlint clean). Main-display camera/overlay
  acceptance and the acceptance commit remain with the user.
- 2026-11-15 — Play Store screenshot round (not final): normal-library.png and
  normal-stage.png (693-word demo script) are good. compact-stage.png shows an
  empty script at 10%; 7in-stage.png accidentally captured the launcher
  wallpaper; 10in-stage.png accidentally captured the editor (empty script,
  find/replace panels). compact/7in/10in stage shots must be re-captured with
  the 693-word script before the listing is usable. Displays: TP-playstore-
  normal (117), TP-playstore-compact (118), TP-playstore-7in (119),
  TP-playstore-10in (120); scrcpy sessions still running. Multi-agent
  coordination via /mnt/Storage/Projects/talk.txt (agents: TCAgent-done,
  AtlasAgent-AnatomyAtlas AVDs/KVM-blocked, Quill-teleprompter, TP-* window
  titles). Phone drops its Termux sshd intermittently — after a reboot or
  Termux kill, run `sshd` in Termux before build-on-phone works.
- 2026-11-15 — Overlay control bar root-cause fix (implemented, NOT yet
  device-verified): the expand animation pinned the control window at integer
  widths and never reliably re-grew it, so Stop never drew and Area was
  clipped (user report, reproduced on main display in landscape). Fix: the
  window is now always WRAP_CONTENT; the collapse/expand animates only x and
  the crossfade; expand measures the natural width for the x clamp; the
  once-a-second force-WRAP_CONTENT heal remains as a safety net.
  compileDebugKotlin green; build-on-phone blocked until Termux sshd restarts.
- 2026-11-15 — New script buttons made rectangular (`RoundedCornerShape(0)` +
  matching black border, matching neo-brutalist squared corners) across FAB,
  portrait toolbar inline button, tablet rail button and empty-state button;
  FAB hidden on phones (inline toolbar "New script" takes over); Import/Paste
  moved onto the same row as New script (single weighted row, text no longer
  clipped); stray FlowRow vertical divider removed; ad banner `clipToBounds()`;
  banner rotation deferred while the library list is scrolling.
- 2026-11-15 — Overlay control bar RE-ENGINEERED: control window is now a
  full-screen touch-pass-through canvas (FLAG_NOT_TOUCH_MODAL) and the bar
  child moves via translationX/Y — no WindowManager relayout per animation
  frame (was: updateViewLayout every frame → janky + the "Stop clipped /
  fixed-width" failure mode; now structurally impossible). Collapse/expand
  still auto-snap to nearest vertical edge at 5 s idle. Device-verified on the
  main display (portrait): all seven controls render, TP square pins to the
  edge, expand restores the bar. Smoothness of the animation needs the user's
  eye. Note: phone drops `adb` constantly and the task keeps `mDisplayId` of
  dead virtual displays — relaunch with `am start --display 0` to rescue it.
- 2026-11-15 — Script scrubbing improved: dragging the script text with a
  finger now flings with inertia (e.g. a fast flick scrolls on after release
  and decelerates like a list); position slider changes chase the thumb with
  a 150 ms ease instead of stepping. Verified via adb swipe: 0:40 → 0:52 after
  one flick, decays to a stop; position restore/lerp paths unchanged (big jumps
  still snap). Note: phone mounts show the teleprompter task at `mDisplayId=131`
  (dead virtual display) after reinstalls — `am start --display 0` moves it back.
- 2026-11-15 — MULTI-INSTANCE debug flavors added: `agentOne..agentFour`
  (applicationIdSuffix .agent1..4, each with its own `app_name` via flavor
  `strings.xml`, no buildFeature flags — resValue in flavors is blocked by
  AGP without an experimental flag). `base` is the default flavor used by
  build-on-phone (`assembleBaseDebug` / `lintBaseDebug`). All four agents
  build and run CONCURRENTLY on separate virtual displays (user 0, Samsung
  blocks new user types; Secure Folder denies shell; task is single-instance
  per package). Each seeds the Demo script. Agent APKs live in
  `app/build/outputs/apk/agent*/debug/`.
- 2026-11-15 — Library verification at 4 classes × portrait captured to
  `screenshots/` (play-store candidate shots, NOT yet final; banner shows
  test ad). Stage captures by parallel subagents (one per class). scrcpy
  displays die unpredictably on this phone (all wiped at ~22:20); recreate
  with --new-display and re-map displayId/SFID each session.
- 2026-11-15 — Stage text-under-panel bug FIXED: PrompterStage + the
  drag-to-scrub gesture Box are now constrained to the script column when the
  side panel is used (was: full width, text centered under the panel and
  clipped at its edge — visible on Expanded 10in portrait). Rebuild + reinstall
  of all agent flavors done; base build pending on-phone rebuild via
  build-on-phone. Note the 4 screenshot library shots in `screenshots/` were
  taken with the agent builds; the 10in portrait stage shot was taken BEFORE
  this fix (shows the clipping) — re-shoot on a portrait display to verify.
- 2026-11-16 (after server restart) — All 16 screenshots in `screenshots/`
  (4 classes × 2 orientations × library+stage) are DONE, incl. the corrected
  10in-portrait stage (text constrained to the script column, clear of the
  side panel — the earlier capture showed the clipping bug). 10in-landscape
  stage confirmed with ~240px of black clearance between text and panel.
  build-on-phone fixed to detect applicationId only from lines matching
  `applicationId =` (was matching applicationIdSuffix → "invalid applicationId
  detected: '.agent1'"). Phone base rebuild BLOCKED again: Termux sshd died
  (server restart) — user must run `sshd` in Termux once more before
  build-on-phone works. One TP-shot-10in-portrait scrcpy window left open.
- 2026-11-16 — Overlay PNGs + tablet panel default expansion fix. Overlay shots
  captured with the agent1 build (base app still unreinstallable — Termux sshd
  dead): `overlay-home.png` (bus-tram launcher wallpaper) and `overlay-web.png`
  (en.wikipedia.org/wiki/Teleprompter, manual-contrast white text + outline,
  legible over bright content) — both show the draggable bar EXPANDED (full
  controls: drag handle, teal play, restart, −5s/+5s, Area, S, magenta Stop).
  Removed two rejected shots (Settings backdrop shows NO overlay — Android
  force-hides non-system overlays on sensitive screens
  [mIsForceHiddenNonSystemOverlayWindow, e.g. Samsung Settings]; Play Store
  backdrop showed "Item not found" — not suitable for store assets). Overlay
  STOPPED afterwards via `am force-stop` of agent1 (cleanup). NOTE: plain
  `adb shell input tap` routes to the FOCUSED display, NOT display 0 — with the
  phone screen off (display 0 no focused window) taps landed on the scrcpy
  tablet (agent4); use `input -d 0` for the main screen. Physical screen now
  awake (12:00); Chrome left on the Wikipedia article.
- 2026-11-16 — Stage control panel DEFAULT expansion now hugs the controls
  (was fixed 0.46/0.66 fraction leaving a large dead area below the controls
  on tablet portrait): bottom panel Surface uses
  `heightIn(max = panelMaxHeight)` (resizable cap kept via the top grip) and
  the scrollable tab body uses `weight(1f, fill = false)` (fills remaining cap
  but keeps natural height otherwise). 7in (1200x1920) and 10in (1600x2560)
  portrait verified — panel ends flush after the Keep awake row; normal/
  compact (phones) content exceeds the cap → unchanged (cap + footer scroll).
  Portrait stage PNGs re-shot for all four classes with the fix (landscape
  untouched — side panel). Base build on the phone still pending Termux sshd.
- 2026-11-16 — build-on-phone re-ran successfully (Termux sshd restored by user):
  base app on the phone now carries ALL of today's fixes (portrait bottom panel
  always-portrait, panel hugs controls on tablets, overlay work). Note: adb
  dropped the phone once mid-session (Samsung MTP mode) — `adb reconnect`
  recovered it; use `-s RZCY61Z8TPD` everywhere (emulator-5554 present, never
  touch). If display 0 has NO focused window (screen off / user away), plain
  `input tap` is routed to the focused VIRTUAL display, not display 0 — always
  pass `-d 0` (or the target display id).
- 2026-11-16 — Overlay PNGs with the bar EXPANDED captured on ALL FOUR device
  classes (virtual displays): overlay-normal.png (162, agent2),
  overlay-compact.png (160, agent3), overlay-7in.png (161, agent4),
  overlay-10in.png (163, agent1). Backdrop = launcher (virtual displays render
  the launcher fine). Agent POST_NOTIFICATIONS + SYSTEM_ALERT_WINDOW were
  pm-granted/appops-granted by shell (no phone interaction needed); permission
  dialogs that remain (e.g. notification prompt) were answered on-screen.
  Non-system overlays are force-hidden by the platform on sensitive screens
  (Samsung Settings) — cannot be captured there.
- 2026-11-16 — ADAPTIVE CONTRAST shot: overlay-adaptive.png — dark script text
  on the bright Wikipedia article, red projection indicator in the status bar,
  bar expanded, no ads. OneUI 7 MediaProjection flow notes: the consent sheet
  has a DROPDOWN ("Share one app"/"Share entire screen") — blind-tapping app
  tiles must be AVOIDED (they are share targets); pick "Share entire screen"
  then tap the confirmation button ("Share screen"). Projection was stopped
  after the shot (force-stop agent1). Chrome left on the Wikipedia article.
- 2026-11-16 — Ads removed from the STORE SCREENSHOTS (PNGs): the ad banner box
  (ADVERTISEMENT label + Test Ad, full-width black-bordered box) was detected by
  its side borders in all 8 library PNGs and filled with the library background
  (245,245,220); nav bar + gesture pill below the banners left intact;
  verification: zero residual dark pixels in the erased bands, backups in
  /tmp/opencode/screenshots-backup/. The app ITSELF still shows test ads — if
  ad-free app testing is wanted, that needs a code/config change (not done).
- 2026-11-16 — Ad-free store screenshots via build flag (NOT byte edits):
  `AD_FREE` buildConfigField added (defaultConfig=false; agentOne..agentFour=true,
  base keeps ads). Guards: BannerAdSlot returns nothing, MobileAds.initialize is
  skipped, InterstitialAds.preload/maybeShow no-op. Verified on display 168 with
  the fresh agent build — library shows NO banner/ADVERTISEMENT label at all.
  Stage/overlay screens never had ads. NOTE: adb dropped the phone mid-run
  (Samsung MTP / USB flaky again) and later the phone vanished from lsusb
  entirely — device-side capture of the FEATURE screenshot set is BLOCKED until
  the phone is reconnected. The 4 recreated virtual displays (166/167/168/169;
  normal/compact/7in/10in) died with the USB drop — recreate with --new-display
  after the phone is back. emulator-5556 is present and was NOT used.
- 2026-11-16 (crash-recovery session) — Resumed from the exported
  session-c91230ab export (crashed on inline media limits) on the same four
  virtual displays (171 normal/agent2, 174 compact/agent3, 173 7in/agent4,
  172 10in/agent1; scrcpy windows TP-shot-* still alive).
  Feature screenshot set captured on normal class (1080x2340, display 171):
  settings.png + settings-backup-restore.png; editor.png, editor-menu.png,
  editor-find-replace.png; stage-playback.png, stage-text.png,
  stage-text-fonts.png, stage-look.png, stage-look-2.png, stage-window.png,
  stage-window-overlay-area.png (overlay-area editor with magenta handles),
  stage-countdown.png (3s), stage-locked.png, stage-mirror.png, stage-end.png,
  pip.png (PiP WORKS on the virtual display: narrow window over launcher;
  note tapping the PiP window did not restore — use `am start --display <id>`),
  library-search.png, library-favorites.png.
  IMPORTANT coordinate note: after scrolling the Look tab, the Mirror <-> /
  Mirror <-> buttons sit at actual y 1970-2074 (centres ~(289,2021) and
  (790,2021)); the earlier tap at y~1939 missed and produced a false "mirror"
  shot. Correct shot verified — text is visibly reversed.
  Refreshed all four portrait library PNGs from the ad-free builds
  (normal/compact/7in/10in) — verified no banner/ad label. The compact
  full-width dark rows at y~1047 are the Demo card's bottom border (not an ad).
  uiautomator dumps were unusable while the user actively used display 0
  (returned io.skooped.app / com.devquiz.app) — used screencap+PIL only.
  7in + compact feature-view variants delegated to two subagents.
- 2026-11-16 (overlay/edge-case hardening, static review + fixes):
  Double-start overlay = re-entrant `onStartCommand`; views used to stay attached
  to the OLD WindowManager while context/metrics moved (leaks + BadToken paths).
  Fixes: teardown-then-rebuild on display change; addView wrapped (failure rolls
  back, toast, stopSelf, overlayRunning=false); startForeground guarded;
  non-adaptive re-start now calls releaseProjection(); projection creation
  guarded (ImageReader/createVirtualDisplay nullable + try/catch) and on failure
  the FGS is demoted to SPECIAL_USE via stopForeground/startForeground; Studio
  button now launches on the overlay's display; missing requested display =
  toast + stopSelf; sampler join 500->100ms; image listener wrapped in
  runCatching. Data layer: script temp files SAME NAME race -> UUID temp files;
  _scripts merge now atomic flow update{}; backup id path traversal -> id
  pattern allow-list + sha256 fallback in scriptFile; NaN lastPosition/settings
  (speed/font/focus/edge/margin/lineSpacing/OverlayArea) finite-guarded;
  empty script Play is a no-op; restore writes per-entry runCatching + id+content
  dedupe only; SettingsRepository reads/writes finite-sanitized, flushPrompterSettings()
  on ON_PAUSE; DataStore failures logged; editor undo history capped (100 entries
  / 2 MB); find/replace counting capped at 999; scrub locked mid-gesture ->
  scrubbing reset via LaunchedEffect(locked). Build + unit tests green.
  NOT touched: allowBackup=true (flagged to user, product decision), wordCount
  per-edit cost (perf only), library backup allowBackup.
- 2026-11-16 — Base app on the phone rebuilt on THIS build machine via
  Termux sshd (assembleBaseDebug, 2m24s) and pm-installed (pm install -r,
  data preserved). No visual/layout changes shipped: hardening = failure
  handling + state integrity only (idempotent double-start, view rollback,
  projection release/demote, Studio launches on overlay display, NaN/backup
  guards, bounded undo, scrub-lock fix, full-area touch passthrough).
  App NOT force-relaunched (user was mid-use in Dev-Quiz).
- 2026-11-17 — Main-screen (display 0) overlay hardening: control window was a
  MATCH_PARENT transparent canvas swallowing all touches (only FLAG_NOT_TOUCH_MODAL,
  which Samsung/One UI disregards). Changed PrompterOverlayService to a
  WRAP_CONTENT window pinned to barX/barY (relayout per frame during drags/
  animations; relayoutControlWindow() on size changes; teardown sets
  controlParams=null). Bar clipping off the right edge on sw384dp/450dpi seen
  in both builds (never fully verified). Display-0 full-cycle overlay test in
  progress (pass-through unverified at this point). Also: adaptive brightness
  was still on (mode=1) so brightness-0 requests were ignored; fixed with
  screen_brightness_mode=0, screen stays awake, no keyguard.
- 2026-11-17 (night) — Main-screen (display 0) overlay verification PASSED on the
  wrap-content build (installed same day): overlay launches, 2 overlay windows,
  app backgrounds as designed; touch pass-through works both ways (launcher
  Gallery icon opened through the running overlay; in-app +5s advanced 0:00->0:01);
  in-app Stop overlay stops service+windows cleanly; start->stop->start->stop cycle
  clean; Screen capture of display 0 verified working at brightness 0 + adaptive
  brightness OFF (screen_brightness_mode=0) — note the screen still glows even at
  1 nit; KEYCODE_SLEEP (223) really sleeps the panel and screencap returns pure
  black with the panel off (224 wakes) — do NOT sleep the display during capture
  sessions. screen_off_timeout restored to 30000; adaptive contrast restored ON.
  Unresolved: the overlay bar clips beyond the right edge on this 384dp/450dpi
  screen (both builds) — pre-existing overflow, needs a bar-size decision.
  Unverified: adaptive-contrast mediProjection roundtrip on this device (consent
  sheet requires interactive user).
- 2026-11-17 (later) — Google Chrome stayed open on the Wikipedia article;
  adaptive-contrast consent sheet driven by coordinates (uiautomator cannot
  dump the system sheet): "Share one app" dropdown -> "Share entire screen"
  -> "Share screen" button. Adaptive MEDIAPROJECTION roundtrip verified:
  overlay runs, REC indicator in status bar, bar stays interactive. OVERLAY
  BAR OVERFLOWS display 0 on this 384dp/450dpi phone: all 7 buttons + handle
  render past the right edge (Stop unreachable). Fix: compact minWidth
  44dp -> 40dp (handle 44 -> 40). Adaptive contrast codepath confirmed alive
  on-device (system consent -> projection -> overlay running with AUTO chip).
- 2026-11-17 (later yet) — Test residue cleaned: app force-stopped; overlay windows=0,
  service=0. Adaptive-contrast ON-DEVICE verification FAILED TO COMPLETE — the user
  is actively multitasking across Recall / Anatomy Atlas / Teleprompter on display 0
  during testing; each foreground attempt races the user's own app switching and the
  MediaProjection consent; the last attempt showed pid empty / windows 0 / Modiium
  app died silently in the background (system memory). Adaptive contrast remains
  UNVERIFIED on this device. Verified-but-complete on-device: overlay launch, bar
  (40dp compact fits width), pass-through, stop/idempotency.
- 2026-11-18 (launch readiness v1.0.0) — AdMob production IDs wired from env
  (TELEPROMPTER_APP_ID/BANNER/INTERSTITIAL) via BuildConfig + manifest placeholder;
  debug always uses Google test units; release build fails without production IDs
  and keystore.properties. UMP consent flow (ConsentManager) gates all ad requests;
  privacy options entry point in Settings. Privacy policy published at
  https://drankit2029-sys.github.io/teleprompter-privacy/ (public repo
  drankit2029-sys/teleprompter-privacy, GitHub Pages). Private project repo
  drankit2029-sys/teleprompter. Release signing keystore generated
  (keystore/teleprompter-upload.jks, credentials in keystore.properties, both
  gitignored). R8 + resource shrinking enabled; allowBackup=false; cleartext
  disabled; FileProvider narrowed; MediaProjection + overlay disclosure dialogs;
  Back no longer triggers interstitials; banner refresh 60s with in-flight guard;
  capture-specific notification + Stop capture action; share byte-threshold +
  cache cleanup; main-thread IO removed from repository init. AAB:
  app/build/outputs/bundle/baseRelease/app-base-release.aab (8.6 MB, signed,
  versionCode 1, versionName 1.0.0, targetSdk 37).
