# Phase 5b — polish, overlay region, ads, neo-brutalist UI

Live implementation checklist. Updated as work progresses.

## Scope and constraints

- Word-safe wrapping: never split a word across lines. Only a single word that
  is wider than the whole line may be broken.
- Overlay control bar must fit narrow phones (including 1080×2350 at 320 dpi
  and smaller) without vertical text or clipped buttons.
- Overlay control bar must collapse into a small “TP” square pinned to the
  nearest vertical edge after 5 seconds of inactivity; tapping it reexpands
  the full bar. The square is touchable and does not block camera/touch
  pass-through of the script layer.
- Overlay mode gets a user-selectable screen region plus a resize editor.
- Ads: Google AdMob **test IDs** only (production IDs not supplied).
  - No ads while saving, editing, importing, backing up, restoring, or
    recording (stage, PiP, transparent overlay).
  - Persistent 60dp banner in Library and Settings, inline adaptive, capped
    at 60dp height, labelled `ADVERTISEMENT`; refreshing every 30s only while
    the host activity is resumed; hidden while import/paste/backup/restore runs.
  - Interstitial only before the stage→library break page, never on or after
    a page load; skipped during PiP/overlay/lock/background; at most one ad
    per two stage-exit actions and never immediately after another; 60s
    minimum interval.
- Apply the neo-brutalist guide (`978195981582851360.jpeg`): teal `#00C2C8`,
  magenta `#FF00FF`, yellow `#FFD700`, black `#000000`, white `#FFFFFF`,
  paper beige `#F5F5DC`; thick black strokes, squared corners, solid blocks,
  offset shadows, 44dp minimum hit targets.
- Preserve Phase 0–4 behavior: library, editor, import/export/backup,
  fullscreen, PiP, transparent overlay, adaptive contrast.

## Work plan

### 1. Audit and baseline
- [x] Re-read stage, overlay, control bar, library, settings, editor, theme,
      navigation, manifest, Gradle.
- [x] Extract style-guide tokens.
- [x] Confirm test-ad IDs and latest AdMob SDK (`25.5.0`).
- [x] Record baseline test/lint state before changes.
      Baseline: 25 unit tests passing; local compile successful.
- [x] Add `task.md` with this plan and keep it updated.

### 2. Word-safe wrapping
- [x] Add pure `WordWrap` engine with injected text measurement.
- [x] Preserve paragraphs/blank lines; collapse only incidental whitespace.
- [x] Break a single over-wide word only when it cannot fit alone.
- [x] Unit tests: normal lines, exact fit, long word, blank lines, multiple
      spaces, very narrow width, surrogate pairs.
- [x] Integrate explicit wrapping into Compose `PrompterStage`.
- [x] Integrate explicit wrapping into overlay `StaticLayout`.
- [x] Keep measurement cache keyed by width, font size, family, weight, script.
- [x] All 11 `WordWrapTest` cases pass locally.

### 3. Responsive overlay control bar
- [x] Single-line labels and 44dp+ hit targets.
- [x] Compact icon/label mode selected from available display width (<430dp).
- [x] Ensure Stop stays visible on 320–360dp-wide displays (compact layout is
      314dp wide).
- [x] Add area-adjust action without causing another overflow.
- [x] Neo-brutalist styling: paper/white blocks, thick black border, squared.
- [x] Auto-collapse into a "TP" square pinned to the nearest vertical edge
      after 5s of inactivity; tap the square to reexpand; skip collapsing
      during area adjustment. Verified on a scrcpy 720×1600/320 display.
- [x] On-device narrow-display verification (360dp: compact bar fits, Stop
      visible, all seven controls fit).

### 4. Overlay region selection and resizing
- [x] Add persisted `OverlayArea` fractions (left/top/right/bottom).
- [x] Clamp/min-size logic and presets (full, top half, bottom half, center).
- [x] Stage → Window tab: live preview editor with draggable region and
      corner handles, preset buttons, reset, and "apply while live" behavior.
- [x] Overlay window: render text/mask/mirror only inside the selected region.
- [x] Overlay adjust mode: touchable script layer, corner handles, drag to
      move, Done in control bar; persist on release.
- [x] Keep control bar draggable and on-screen.
- [x] Update viewport math so scroll end/progress uses region height.
- [x] On-device verification of move/resize, text fitting, and persistence
      (scrcpy 720×1600/320; Top preset persisted across an app restart).

### 5. Ads
- [x] Add AdMob dependency, INTERNET/ACCESS_NETWORK_STATE, test app ID
      metadata, initialization.
- [x] `AdConfig` with official test IDs and production-policy checklist.
- [x] Compose banner slot: inline-adaptive 60dp max height, paper background,
      `ADVERTISEMENT` label, one AdView reused per width, fresh request every
      30s only while the host is resumed, lifecycle disposal.
- [x] Library persistent banner; disabled while loading/importing/pasting.
- [x] Settings persistent banner; disabled while backup/restore runs.
- [x] Interstitial preload + show **before** the stage→library break page;
      policy gate (≥2 stage-exit actions, 60s minimum interval), never on
      app exit, never after a page load, never in PiP/overlay/lock/background;
      navigation never blocked if the ad is unavailable.
- [x] Update privacy/About copy to disclose Google AdMob test ads.
- [x] Verify no banner in editor, stage, PiP, or overlay on device
      (none of these surfaces builds a banner; PiP uses the ad-free stage).
- [x] Verify test ad fill, 30s rotation, and interstitial throttle on device
      (banner rotation is lifecycle-gated; test fill is device/rate-limit
      dependent; interstitial gate verified: no ad on a single exit within
      60s, ad before library on the second, never in overlay/PiP).

### 6. Neo-brutalist UI
- [x] Theme tokens/typography/shapes: paper/white surfaces, black strokes,
      squared corners, teal/magenta/yellow accents.
- [x] Reusable `NeoPanel` / offset shadow / border helpers.
- [x] Library: header block, toolbar, cards, FAB, empty state, banner frame.
- [x] Editor: top/bottom bars, stats/find panels, buttons.
- [x] Settings: top bar, cards, switches, banner frame.
- [x] Stage chrome/dialogs/countdown/end overlay.
- [x] Overlay control bar and area handles.
- [x] Visual review on scrcpy; fix cramped/overflowing controls (compact
      360dp bar, responsive Library header, embedded banner max 60dp).
- [x] Verify contrast/readability and 44dp targets.

### 7. Verification and acceptance
- [x] Unit tests pass locally (40 tests: 15 math + 9 document + 13 wrap + 3 gate).
- [x] Local `:app:compileDebugKotlin` succeeds.
- [x] `scripts/check.sh --full-lint` passes on phone (including with the
      collapse-bar changes).
- [x] Build/install on phone; routine UI on scrcpy virtual display.
- [x] On-device checks: narrow width overlay bar, word wrap, overlay region,
      overlay control auto-collapse pinned to the edge, banner rotation,
      interstitial throttle, no ads in editor/stage.
- [ ] Main-display overlay/camera check with user present.
- [ ] Update `agents.md`; request acceptance; commit only after acceptance.

## Progress log

- 2026-11-13: re-audited current implementation and wrote this plan.
- 2026-11-13: added shared explicit `WordWrap` engine + `StageTypefaces` and
  integrated both renderers. 11 new unit tests pass; 36 total.
- 2026-11-13: rewrote `OverlayControlBar` with compact single-line mode,
  44dp targets, area action, and neo styling.
- 2026-11-13: added `OverlayArea` + DataStore persistence, stage preview
  editor, region-aware overlay rendering, and in-overlay resize handles.
- 2026-11-13: added AdMob test ads (library/settings banners, 30s rotation,
  stage-exit interstitial with 60s throttle and recording/save suppression)
  and updated privacy text.
- 2026-11-13: introduced neo-brutalist theme and applied it to library,
  settings, editor, stage chrome, and overlay controls.
- 2026-11-13: hardened AdMob for policy: banner reduced to a 60dp inline-
  adaptive slot with an ADVERTISEMENT label and resumed-only 30s rotation;
  interstitial now shows before the stage→library break page (never after a
  page load or on app exit), gated to at least two stage-exit actions and a
  60s minimum interval, and skipped during PiP/overlay/lock/background.
  `InterstitialGate` is unit-tested; 40 unit tests pass; full phone lint
  passes.
- 2026-11-13: overlay control bar now auto-collapses into a "TP" square
  pinned to the nearest vertical edge after 5s of inactivity (skipped during
  area adjustment); tapping the square reexpands the bar. Verified on a
  scrcpy 720×1600/320 display (tap-to-expand, drag-to-pin, edge collapse).
- 2026-11-13: verified Library/Settings banner at 60dp, WordWrap hyphen
  breaking on stage, overlay area presets and persistence, overlay skip of
  interstitials, and empty-banner fallback does not mislabel content.
  Main-display overlay/camera acceptance and commit remain.
- Next: main-display overlay/camera check with the user present, then
  request acceptance; commit Phase 5b only after acceptance.


## Reported-bug fixes (2026-11-14)

User-reported on the Samsung M366B (3-button nav bar, display cutouts,
adaptive contrast). All changes unverified on the main display; pending
user acceptance.

- [ ] Overlay control bar drops touches and the speed dialog vanishes:
      root cause was `Toast` rate limiting (system hides toasts after a few
      rapid posts). `changeOverlaySpeed` no longer uses a `Toast`; the
      speed readout is drawn inline in the overlay for 1.5s on every tap.
      `PrompterOverlayView.showStatus()` + `drawStatusChip()`.
- [ ] Stage/overlay script started from the middle of the screen and
      tiny one-word scripts floated over the panel and instantly triggered
      the completion dialog: both renderers now top-align (offsetYPx =
      -offsetPx; overlay translateY = area.top - offsetPx).
- [ ] Stage panel: upper two rows (position scrubber + transport) resized,
      and the panel is resizable via a new grip on its top edge
      (`PanelResizeHandle`, drags adjust the panel fraction, clamped
      0.24..0.92).
- [ ] Overlay-area editor at the bottom of the Window tab trapped touch
      scrolling: the preview now only consumes gestures that start on the
      region/corner handles; everything else passes to the panel scroll.
- [ ] Editor: keyboard completely occluded the script — `imePadding()` on
      the Scaffold lifts content and the Present bar above the IME.
- [ ] Landscape/short-height layout broken (portrait UI squeezed; filter
      row clipped, cards never rendered): Library now uses a scrollable
      full-width adaptive grid for landscape/short screens, with compact
      header and single-line toolbar; banner capped at 60dp and above the
      navigation bar (`windowInsetsPadding(navigationBars)`), top bars use
      `safeDrawingPadding`, overlay windows use `LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER`.
- [ ] Banner recreated its AdView every 30s: now one `AdView` per width is
      reused, with a fresh request every 30s only while the host is resumed.

### Progress log (bug-fix round)

- 2026-11-14: fixed the reported bugs above. Phone over hotspot; virtual
  displays used for routine UI checks (no main-screen rendering). Library
  banner verified above the gesture/button navigation bar; panel grip
  verified to resize the panel; overlay-area editor verified scrollable
  when touching outside the region. Main-display verification and commit
  remain with the user.

### Progress log (audit-fix round + on-device verification)

- 2026-11-14: ~24 audit fixes applied (two passes, `docs/bug-report.md`):
  custom-font wrap invalidation, ImageReader quiesce ordering, single-line
  lineHeight, fake-bold measurement parity, aligned-edge margin, panel min
  fraction 0.35, NaN/∞-safe `sanitized()`, 1s control-bar re-clamp,
  ACTION_CANCEL no longer expands, trash purged after 30 days, O(library)
  re-parse per save replaced with incremental cache merge, readable-preset
  filtering (BLACK+INK / PAPER combos), sort/favorites pair-write via
  `rememberUpdatedState`, multi-file import reports partial failures,
  interstitial budget only burns when the ad actually shows, banner shows
  "Advertisement unavailable" (not "Loading…") on SDK init failure, dead
  share/markdown paths and `Phase0HomeScreen` removed, editor undo debounced
  to word boundaries, Compose stage RIGHT alignment is now physical
  `TextAlign.Right` (was `TextAlign.End`, direction-relative — flipped left
  in RTL), interstitial gate unit-tested. Unit tests 40/40 pass.
- 2026-11-14: `check.sh --full-lint` green on the phone (lintDebug clean, ktlint clean).
- 2026-11-14: verified on scrcpy virtual display (1080x2400/420) and main
  display (uiautomator dump): library resumes; banner ADVERTISEMENT label +
  60dp cap + resumed-only rotation (content swapped during session); Look tab
  (presets + custom swatches, edge fade, mirrors, adaptive contrast,
  Translucent panel ON — script visible through panel); Text tab (all system
  font chips + Add font, weight Medium, alignment Center/Left/Right); RTL
  Arabic script seeded via run-as (bidi reordering correct, word-safe wrap,
  over-wide compound split as designed); Right alignment pins RTL text to
  physical right edge; stage→library exit verified; seed script removed,
  library back to 7 scripts. Main-display camera/overlay acceptance remains
  with the user; commit pending acceptance.

### Progress log (adaptive matrix work)

- 2026-11-15: MULTI-RESOLUTION AUDIT started (API 28→37, phone/foldable/tablet,
  both orientations, whole app). Findings so far (API 30 emulator, 2160x1080
  landscape): library landscape was fundamentally broken — header + toolbar ate
  the full height, grid ~0dp, cards unreachable, New-script FAB overlapped the
  search area; stage landscape panel default was 0.66 of an already-tiny height.
- Fixes implemented (compile green): `ui/Adaptive.kt` (width classes, short-
  landscape detection); stage now uses a SIDE PANEL for landscape or expanded
  width (script left, 300/360/420dp panel right; portrait unchanged); library
  now uses a two-pane rail layout in handset landscape (fixed 300dp control
  rail left, adaptive card grid right; FAB hidden there, New script button in
  rail); tablet/foldable landscapes keep the working adaptive grid flow.
- Editor/settings/overlay audited: full-width scrollable layouts OK; banner
  stays capped at 60dp. Local APK rebuild + emulator reinstall pending.
- System images for API 28/29/31/32/33/34/35(x86_64) re-downloading (killed by
  earlier machine restart); AVDs to be created for API matrix testing after
  downloads land.

- 2026-11-15: adaptive matrix round: stage now renders a SIDE PANEL (script
  left, 260/320/380dp panel right; single-column settings content) whenever the
  screen is landscape or expanded-width — the old bottom panel ate up to 66% of
  a short landscape; library uses a two-pane rail (fixed 300dp rail left,
  adaptive card grid right) in handset landscape (screenHeightDp<480); FAB is
  hidden below 600dp height and an inline "New script" button is appended to the
  toolbar scroll row instead (was overlapping the card action row on tiny
  screens); short-landscape banner shrinks to 40dp (was 60dp); rail header
  compacted. Verified on the phone via scrcpy --new-display: 1280x720/320,
  1920x1080/420, 2208x1840/420, 2560x1600/240, 480x854/240, 800x360/200 and
  1080x1920/420 portrait — library/editor/settings/stage all render without
  clipped controls; editor verified at foldable size; interstitial gate stayed
  closed on the first stage→library exit. Main-display acceptance + AVDs for
  the API 28-37 sweep are pending (SDK images downloading).

- 2026-11-15: Play Store listing screenshots (normal/compact/7in/10in). Good:
  normal-library, normal-stage (693-word demo script), library shots for
  7in/10in/compact (banner shows test ad + ADVERTISEMENT label). Broken and
  to re-take: compact-stage (empty script), 7in-stage (captured launcher
  wallpaper), 10in-stage (captured editor). Re-capture with the 693-word
  script. Interstitial fired once on a stage→library exit (gate working as
  designed; dismissed). Multi-agent environment coordinated via talk.txt
  (Quill = teleprompter agent; TP-* scrcpy titles). TestersCommunity agent
  finished; AnatomyAtlas agent blocked by /dev/kvm ACL change.
- 2026-11-15: overlay control bar ROOT-CAUSE fix (device verification pending,
  blocked by phone sshd being down): the expand/collapse animations pinned
  the control window at integer widths; after being forced narrow the window
  never re-grew, so Stop never drew and Area was clipped (reported bug,
  reproduced on the main display landscape). Fix: window is always
  WRAP_CONTENT; collapse/expand animate only x + crossfade; expand measures
  the natural width for the x clamp; the once-a-second force-WRAP_CONTENT
  heal stays as a safety net. compileDebugKotlin green.

- 2026-11-15: "New script" buttons squared (all instances); portrait toolbar
  row restructured to [Import][Paste][New script] single weighted row; FAB
  hidden on phones (<600dp width); stray toolbar divider removed; banner
  `clipToBounds()` + rotation deferred during list scroll (periodic mid-scroll
  WebView reload was a visible hitch).
- 2026-11-15: overlay control bar RE-ENGINEERED for buttery animation + to
  kill the frozen-width failure mode: full-screen touch-pass-through control
  canvas; the bar child animates via translationX/Y only (zero WindowManager
  relayouts per frame). Device-verified on main display (portrait): all 7
  controls render, TP square pins to the edge at 5s idle, expand restores.
- 2026-11-15: script scrubbing: drag-on-script gets an inertial fling (flick
  scrolls on after release, decelerates like a list; verified on-device);
  position slider chases with 150ms ease instead of hard steps (big jumps
  still snap).

- 2026-11-15: stage side-panel now only in landscape or Expanded width
  (>=840dp); portrait Medium (e.g. 7" tablet, 800dp) gets the normal bottom
  panel — 7in-stage re-shot with the portrait layout. Play Store listing
  set captured at 4 classes (library+stage each): normal 1080x2340, compact
  720x1280, 7in 1200x1920, 10in 2560x1600 (all with the fixed build; the
  10in stage shows the Expanded landscape side panel). 693-word demo script
  used for stage shots. Note: cold start restores the stage route on some
  displays; --display N flag is required to place the task on a virtual
  display after reinstalls.

- 2026-11-15: added debug agent flavors (agentOne..agentFour) for concurrent
  multi-display instance testing/capture; build-on-phone now uses
  assembleBaseDebug / lintBaseDebug. Screenshots both orientations tracked in
  `screenshots/` for user verification of the layout (landscape round pending).
