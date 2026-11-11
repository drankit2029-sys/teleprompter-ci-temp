# Teleprompter — Product & Implementation Plan

Status: implementation started
Platform: Android (Kotlin + Jetpack Compose)
Package: `com.accessoryTools.teleprompter`
SDK: compileSdk 37, minSdk 28, targetSdk 37

## 1. Product direction

A local-first, offline Android teleprompter for creators, presenters, and
broadcasters. No account or backend is required. Scripts stay on-device;
sharing uses Android's share sheet. The app must remain fast on low-end phones
and adapt to phones, tablets, foldables, and arbitrary screen resolutions.

The app has five main surfaces:

1. **Library** — saved scripts, search, favorites, folders.
2. **Editor** — write/import/edit a script.
3. **Fullscreen prompter** — the normal immersive reading mode.
4. **PiP mode** — system floating window over another app, e.g. a camera app.
5. **Transparent overlay mode** — only the script floats above the screen; the
   underlying app remains visible.

### Platform decision: PiP and transparent mode are separate

| Mode | What the user sees | Extra permissions | Best for |
|---|---|---|---|
| Fullscreen | Normal app screen | None | Normal recording/presentation |
| PiP | Opaque system floating window | None | Reading over a camera app while recording |
| Transparent overlay | Only script text, transparent background | "Display over other apps" | Teleprompting over any camera or app |
| Adaptive transparent | Transparent overlay, text color chosen from screen below | Overlay + screen capture consent | Readability on changing backgrounds |

Android PiP cannot reliably render a genuinely transparent window. Transparent
mode is therefore implemented as a system overlay with a foreground service.
PiP remains the safe, permission-free fallback.

Dynamic contrast requires **MediaProjection** screen capture because Android
offers no other way to inspect another app's pixels. It is optional,
privacy-transparent, and must degrade gracefully when permission is denied or
the underlying app protects its window from capture.

## 2. Feature specification

### 2.1 Library

- Create scripts, import files, or paste from clipboard.
- Multiple saved scripts with title, content, timestamps, word count, reading
  time, and last reading position.
- Search across titles and content.
- Favorites, folders/tags, recent scripts, sorting.
- Duplicate, rename, share, delete with undo/trash.
- Resume from the last reading position.
- Quick "Present" action from a library card.
- Reading templates: presentation, YouTube intro, interview, news, rehearsal.
- Storage usage and backup/restore controls.

### 2.2 Editor

- Plain-text editor with title field.
- Undo/redo, find and replace, copy/paste, select all.
- Autosave with visible save status; survive rotation and process death.
- Word/character count and estimated read time.
- Optional cue syntax:
  - `[pause]` or `[PAUSE 3]` — pause marker.
  - `[note]` — stage direction, dimmed/hidden in the prompter.
  - `#` headings and `**bold**` can be rendered as visual emphasis.
- Import into the current script as replace or append.
- Share/export from the editor.
- Prominent "Present" button.

### 2.3 Import formats

| Format | Plan | Notes |
|---|---|---|
| `.txt` | Built-in | Detect UTF-8/UTF-16 and other encodings using Android ICU `CharsetDetector` |
| `.md`, `.markdown` | Built-in | Preserve as text; support simple cue syntax |
| `.docx` | Built-in | Read the ZIP and parse `word/document.xml` with `XmlPullParser`; flatten tables; ignore images |
| `.odt` | Built-in if low effort | Parse `content.xml` from the ODT ZIP |
| `.html`, `.htm` | Built-in | Convert to readable text with Android's HTML parser |
| `.rtf` | Should-have | Basic text extraction; complex formatting discarded |
| `.pdf` | Should-have | Text PDFs via an optional local extractor; scanned PDFs need OCR and are out of scope |
| `.doc` (legacy) | Not natively supported | Explain that the old binary format has no reliable Android parser; advise saving as `.docx`/`.txt` |
| Google Docs | Via share | "Share → Teleprompter" as text or `.docx` |
| Clipboard | Built-in | Paste into a new script |

Also support:

- Android share-in (`ACTION_SEND` text/files) and "Open with Teleprompter"
  (`ACTION_VIEW`).
- Multiple file import.
- Friendly errors for corrupted, password-protected, or oversized files.
- File-name-derived titles and duplicate-name handling.

### 2.4 Sharing and export

- Share script as plain text via the Android share sheet.
- Share as `.txt`, `.md`, or generated PDF.
- Export a full backup archive (scripts plus settings) to a user-chosen folder.
- Restore a backup with duplicate/conflict handling.
- Copy the entire script to clipboard.
- No share links or cloud uploads in v1.

### 2.5 Prompter controls

- Play/pause, restart, seek backward/forward 5 seconds.
- Speed control in WPM, with advanced px/second display.
- Reading progress, elapsed time, estimated time remaining.
- Countdown before scrolling (off/3/5 seconds).
- Loop at end.
- Keep screen awake.
- Fullscreen immersive mode.
- Control lock to prevent accidental touches on stage.
- Manual scroll while paused; resume from a new position.
- Configurable tap zones.
- Adjustable focus line (default center; movable for eye-line framing).
- End-of-script state with restart and exit.
- Per-script settings plus global defaults.
- Automatic reading-position persistence.

Display controls:

- Text size, font family, weight, optional high-legibility bundled font.
- Line spacing, margins, maximum text width, alignment, letter spacing.
- Text/background colors and background opacity.
- Horizontal and vertical mirroring for beam-splitter rigs.
- Focus gradient strength and focus position.
- Text outline/shadow.
- Optional reading scrim.
- Display presets: Studio, Outdoor, Beam Splitter, Presentation.

Input controls:

- Keyboard: space, arrows, page up/down, `R` restart, `M` mirror.
- Optional volume-key mapping.
- Bluetooth media buttons via a media session.
- Foot-pedal support through keyboard/media key events.
- Voice-follow mode as an experimental later feature.

### 2.6 Scroll-over / scrub controls

Reusable `ScrubControl` for speed, size, spacing, opacity, focus position, etc.

- Swipe/drag vertically over the value to change it.
- Mouse-wheel support through scrcpy/ChromeOS/tablet pointer input, with drag
  as the guaranteed fallback.
- Large `−`/`+` buttons.
- Hold to accelerate; haptic detents.
- Tap the value to type an exact number.
- Long-press to reset.
- Live preview while adjusting.
- Optional pause while the control panel is open.
- Accessibility semantics for TalkBack.
- No gesture conflict with prompter scrolling.

### 2.7 Focus gradient

A true vertical opacity mask over the text layer:

- Fully opaque band around the focus line.
- Smooth fade toward top and bottom.
- Configurable focus position (default vertical middle).
- Configurable minimum edge visibility, so look-ahead text does not vanish.
- Offscreen text layer plus alpha mask, not stepped per-line opacity.
- Outline and gradient combined so contrast survives faded edges.
- Available in fullscreen, PiP, and transparent modes.

### 2.8 PiP / window mode

- Enter PiP from the prompter.
- Optional auto-enter when pressing Home while presenting.
- Hide editing chrome; keep script and minimal controls.
- PiP actions: play/pause, restart, speed down/up.
- Tap to show/hide temporary controls.
- User-selectable aspect ratio where supported.
- Position survives leaving/re-entering PiP.
- No special permission.
- Workflow: open script, enter PiP, then open any camera app; the teleprompter
  floats above the camera while recording.

### 2.9 Transparent overlay mode

- Full-screen transparent overlay with only the script.
- Underlying apps stay visible and usable.
- Script window is not touchable; taps pass through.
- Small draggable control bubble for play/pause, speed, lock, exit, settings.
- Optional layout mode.
- Collapsible bubble.
- Notification with play/pause and stop.
- Onboarding for "Display over other apps".
- Graceful handling when permission is denied, revoked, or an app blocks
  overlays.
- Optional overlay lock.
- Keep screen awake while running.

### 2.10 Adaptive contrast

When enabled:

1. User grants screen-capture consent via the system dialog.
2. Foreground service captures low-resolution screen samples at ~2–4 Hz.
3. Overlay glyph/control regions are excluded from sampling to avoid feedback.
4. Background luminance is computed around the reading band.
5. Text color maximizes contrast:
   - dark background → light text;
   - light background → dark text;
   - WCAG luminance/contrast logic rather than literal complementary hue.
6. Add a contrasting outline; optional local scrim for busy backgrounds.
7. Temporal smoothing and hysteresis prevent flicker.
8. Fall back to user-chosen color + outline when capture is unavailable.

Documented limitations:

- Secure/DRM/camera-protected windows may appear black to capture.
- Some apps intentionally hide overlays.
- Android shows a screen-capture privacy indicator.
- Frames are ephemeral and never stored or transmitted.
- Adaptive contrast is optional; transparent mode still works without it.

### 2.11 Professional extras

Priority order:

1. Backup/restore and trash recovery.
2. Display presets and per-script overrides.
3. Bookmarks/cue points and paragraph jump.
4. Bluetooth remote, keyboard, and foot-pedal support.
5. Full-text search, favorites, folders/tags.
6. Reading-time estimate and time remaining.
7. External display/HDMI prompter via Android Presentation APIs.
8. Voice-follow scrolling with on-device recognition (experimental).
9. OCR import from photos of printed scripts.
10. Built-in front-camera preview/recording.
11. Cloud sync or remote control from another phone.

Items 7–11 are future features. v1 works with any camera app through PiP or
overlay.

## 3. UX flow

```text
Library ── New / Import / Share-in ── Editor
   │                                    │
   ├────────── Quick Present ───────────┤
   │                                    ▼
   └────────────── Prompter ── Fullscreen
                         ├── PiP
                         └── Transparent overlay
                                      └── optional adaptive contrast

Settings / Onboarding / Backup live outside the main flow.
```

Prompter control panel tabs:

- **Playback** — play/pause, rewind, forward, restart, countdown, loop, progress.
- **Speed** — scrub value, presets, WPM/px-per-second, time remaining.
- **Text** — font, size, weight, spacing, margins, alignment.
- **Look** — colors, opacity, gradient, focus position, outline, scrim, mirror.
- **Window** — PiP, transparent overlay, adaptive contrast, permissions.
- **Cues** — bookmarks and jump points.

## 4. Technical architecture

Single-module Compose app with clear package boundaries:

```text
com.accessoryTools.teleprompter/
  MainActivity.kt
  data/          scripts, settings, importers, exporters, backup
  domain/        models, prompter session, scroll engine, contrast logic
  ui/            theme, components, library, editor, prompter, settings
  overlay/       overlay service, control bubble, screen sampler
  platform/      PiP, permissions, notifications, media session
```

Planned dependencies:

- `androidx.navigation:navigation-compose`
- `androidx.lifecycle:lifecycle-viewmodel-compose`
- `androidx.lifecycle:lifecycle-runtime-compose`
- `androidx.datastore:datastore-preferences`
- Optional PDF extraction dependency only after checking APK size/build time.
- Keep the dependency footprint small; avoid Hilt and Room initially.
- Existing `FileProvider` for sharing exported cache files.
- `scripts/check.sh` remains the quality gate after every change.

### Storage

File-first library:

- One `.tpscript` file per script containing JSON metadata plus content.
- Atomic writes (temp file then rename).
- `trash/` folder for deleted scripts.
- Storage Access Framework for imports/exports.
- Global settings in DataStore.
- No Room/KSP overhead initially; portable backups; easy inspection.
- Room/FTS can be introduced behind the repository interface if scale demands.

### Prompter architecture

An application-scoped `PrompterSession` owns:

- script and display settings;
- position, speed, state;
- layout measurements and word/line mapping;
- commands from fullscreen, PiP, overlay, notification, and media keys.

Fullscreen, PiP, and overlay render the same `PrompterStage` composable and read
the same session state.

Line-based renderer:

- measure paragraphs into lines with Compose `TextMeasurer`;
- store line offsets and word counts;
- render only visible lines;
- convert WPM to vertical speed using total height/word count;
- recalculate on font/width/line-height/margin changes;
- seek by offset, paragraph, or bookmark;
- apply the focus gradient with an offscreen layer and `BlendMode.DstIn`;
- avoid allocations in the draw loop;
- support very long scripts without a giant Text composable.

### Overlay architecture

- Foreground service owns overlay windows and script state.
- Script window: `TYPE_APPLICATION_OVERLAY`, translucent, non-touchable,
  full-screen, edge-to-edge.
- Controls window: separate small touchable overlay, draggable, collapsible.
- Notification actions: play/pause/stop.
- Media session for Bluetooth/keyboard control.
- `Settings.canDrawOverlays` check before start.
- Clean handling of revocation, rotation, service destruction.
- PiP and overlay are mutually exclusive.

### Adaptive-contrast architecture

- Projection/sampling component captures a downscaled screen image.
- Renderer reports text bounding boxes so they are excluded.
- Robust luminance statistics around the focus band.
- Choose black/white text + contrasting outline.
- Smooth changes and animate color transitions.
- Fall back to manual color when capture is unavailable.
- Stop projection and release the virtual display when overlay ends.

### Manifest and permissions

Add/update:

- PiP flags on the main activity.
- `SYSTEM_ALERT_WINDOW`.
- `FOREGROUND_SERVICE` plus `specialUse` and `mediaProjection` service types.
- `FOREGROUND_SERVICE_SPECIAL_USE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`.
- `POST_NOTIFICATIONS`.
- Intent filters for opening/sharing text and supported documents.

Remove/reconsider:

- `REQUEST_INSTALL_PACKAGES` is unnecessary and must be removed.
- `INTERNET` and cleartext network config unless a future feature needs network
  access. Local-first privacy is a feature.

All API-level-sensitive calls must be guarded and pass the `NewApi` lint rule.

## 5. Implementation phases

### Phase 0 — Technical spike (highest risk)

- PiP over a real camera app on the target phone.
- Transparent overlay over the camera; non-touchable script plus touchable
  control bubble.
- Foreground service start/stop and notifications.
- Screen sampling with overlay visible, excluding text regions.
- Dynamic black/white text selection, smoothing, fallback.
- Permission denial/revocation on API 28 through 37.

**Done when:** PiP and transparent overlay work over a camera app, and adaptive
contrast is proven viable or explicitly scoped as fallback-only.

### Phase 1 — Library, editor, import, export

- File-first repository, DataStore settings, navigation.
- Library UI with search, favorites, delete/undo, resume state.
- Editor with autosave, undo/redo, find/replace, statistics.
- Import `.txt`, `.md`, `.docx`, clipboard.
- Share/export text, Markdown, backup.
- Receive `ACTION_SEND`/`ACTION_VIEW`.

**Done when:** scripts survive restart/process death; a `.docx` can be imported,
edited, shared, and reopened without loss.

### Phase 2 — Prompter core

- Fullscreen stage and scroll engine.
- Focus gradient, mirror, fonts, colors, margins, spacing.
- Scrub controls for speed/size/settings.
- Countdown, lock, keep-awake, resume, loop, seek.
- Keyboard, volume-key, media-button controls.
- Per-script settings persistence.

**Done when:** long scripts scroll smoothly at 60 fps, controls update live,
gradient focus is correct, and settings survive rotation/restart.

### Phase 3 — PiP and transparent overlay

- PiP entry/exit and RemoteActions.
- Overlay service, control bubble, script window, touch-through.
- Permission onboarding and failure states.
- Notification and media-session controls.
- Mode switching and cleanup.

**Done when:** users can present over a camera in either mode, stop reliably,
and recover from denied/revoked permissions.

### Phase 4 — Adaptive contrast

- MediaProjection consent and foreground service.
- Low-resolution sampling with glyph exclusion.
- WCAG color selection, outline/scrim, smoothing, fallback.
- Battery/privacy controls.

**Done when:** text remains readable over dark, light, and moving backgrounds
without flicker or self-sampling feedback.

### Phase 5 — Professional polish

- Folders/tags, trash, backup/restore, revisions if useful.
- Display presets, bookmarks/cue points, reading time.
- Additional import formats (PDF/ODT/RTF) if build impact is acceptable.
- Accessibility: TalkBack, font scaling, contrast, touch targets.
- Responsive tablet/foldable layouts.
- Icon, splash, onboarding, About/licenses, privacy policy.
- Unit tests for importers, contrast logic, scroll math, storage.
- Release build, R8, Play Store declarations for overlay/MediaProjection/FGS.

## 6. Testing and quality gates

- Run `./scripts/check.sh` after every phase (Lint `NewApi` error + ktlint).
- Kotlin style: 4 spaces, max 120 columns.
- Build/install with `build-on-phone`.
- Unit tests for:
  - TXT encoding and DOCX extraction;
  - file storage/autosave recovery;
  - WPM-to-scroll-speed and seek math;
  - focus-gradient alpha values;
  - WCAG selection and hysteresis.
- Permission matrix: overlay denied, projection denied/revoked, notification
  denied, app backgrounded.
- API 28/30 emulator and real target device.
- Test PiP/overlay on the **main display or physical phone**, not only the
  default scrcpy virtual display; use `build-on-phone --no-scrcpy` when needed.
- Performance: smooth scrolling on low-end phones, low sampling overhead, no
  memory growth during long sessions.
- Accessibility: TalkBack labels, adjustable semantics, usable focus states.
- Responsive verification at phone, tablet, foldable, and unusual aspect ratios.

## 7. Risks and mitigations

| Risk | Mitigation |
|---|---|
| PiP cannot be transparent | Separate transparent overlay; PiP is fallback |
| Camera blocks overlays/capture | Test early; PiP fallback; manual contrast |
| Sampling captures teleprompter itself | Exclude text/control boxes; robust luminance stats |
| Dynamic color flicker | Slow sampling, smoothing, hysteresis, animated transitions |
| Android 14+ FGS restrictions | Correct service types, foreground start, clean revocation |
| Document parsing edge cases | Clear format matrix, progress, readable errors |
| Large scripts jank | Line renderer, visible-only drawing, size limits |
| Overlay/Play policy | In-app disclosure, privacy behavior, special-use FGS declaration |
| Speed feels inaccurate | Document-wide WPM mapping, recalculation, tests |

## 8. Checkpoint policy

The project is restored using Git checkpoints after each successfully tested
phase. A checkpoint is a commit plus an annotated tag:

- `checkpoint/phase-0-baseline` — template before implementation.
- `checkpoint/phase-0` — Phase 0 spike complete and verified.
- `checkpoint/phase-1` — Phase 1 complete and verified.
- and so on.

A phase is checkpointed only after:

1. the code compiles,
2. `scripts/check.sh` passes,
3. the app installs and launches on the target device,
4. the phase's acceptance flow has been exercised.

Restore a checkpoint with:

```bash
git checkout checkpoint/phase-N
```

or create a recovery branch:

```bash
git switch -c recover-phase-N checkpoint/phase-N
```

## 9. Decisions

1. Overlay + screen-capture permissions are accepted for transparent/adaptive
   mode; fullscreen and PiP remain available without them.
2. PDF import is deferred until core phases are stable; extra dependency size
   will be evaluated.
3. No built-in camera recorder in v1; use any camera app with PiP/overlay.
4. Local-only storage with backup/export; no cloud in v1.
5. Focus line defaults to center and is adjustable.
6. Adaptive contrast is optional and has a manual-color fallback.

## 10. Responsive requirements

- Use window size classes/adaptive layouts rather than fixed phone dimensions.
- Handle portrait, landscape, split-screen, foldable, tablet, and unusual aspect
  ratios.
- Respect cutouts, system bars, and safe insets.
- Test at 1080×2400 (420 dpi), 1600×2560 (320 dpi), and a low-resolution phone.
- Prompter text width should be bounded on large displays for readability.
- Controls must remain reachable and legible at all supported sizes.
