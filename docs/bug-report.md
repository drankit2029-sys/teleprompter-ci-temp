# Teleprompter — Bug Report (2026-11-14)

Audited by two independent passes (overlay/stage/settings/persistence; ads/library/editor/theme).
Each bug lists a status: **FIXED**, or **NOT A BUG** with evidence.

## Audit pass 1 (prompter/overlay/settings/persistence)

| ID  | File | Sev    | Bug | Status |
|-----|------|--------|-----|--------|
| P1  | PrompterStage.kt:155 | major | wrappedScript not remembered on `customFontFile` → stale line breaks when switching custom fonts | FIXED |
| P2  | PrompterOverlayView.kt:211 | major | Overlay dirty check misses `customFontFile` → fonts switch never re-renders overlay | FIXED |
| P3  | PrompterOverlayService.kt:704 | major | `ImageReader` closed before sample thread quits → IllegalStateException burst | FIXED |
| P4  | PrompterSession.kt:526, PrompterStage.kt, PrompterOverlayView.kt | major | Compose and StaticLayout line heights differ; both live → scroll jitter / re-scaling | FIXED |
| P5  | StageTypefaces.kt (CUSTOM) | minor | BOLD style computed but never installed for custom font | FIXED |
| P6  | PrompterOverlayView.kt:433 | minor | Side margin ignored on the aligned edge | FIXED |
| P7  | PrompterOverlayView.kt:482 | minor | RTL alignment inverted vs. stage | FIXED |
| P8  | StageTypefaces.kt:30 | minor | Custom-font cache ignores file overwrite | FIXED |
| P9  | PrompterOverlayService.kt:551 | minor | Control-bar drag never calls `noteControlInteraction()` → auto-collapse mid-drag | FIXED |
| P10 | PrompterOverlayService.kt:54 | minor | Dead preCollapseY; expand `y` not clamped | FIXED |
| P11 | StageScreen.kt:137 (PanelResizeHandle) | minor | Resize fraction min can leave chrome taller than the panel | FIXED |
| P12 | StageSettings.kt:95 | minor | NaN/∞ passes sanitize for every float | FIXED |
| P13 | PrompterOverlayView.kt:202 | minor | TP square/panel stranded off-screen after rotation/config change | FIXED |
| P14 | OverlayControlBar.kt:155 | minor | ACTION_CANCEL triggers `performClick()` (fake expand) | FIXED |
| P15 | PrompterOverlayService.kt:244 | minor | Collapse/snap animators + runnable survive `onDestroy` | FIXED |
| P16 | PrompterOverlayView.kt:427 | minor | StaticLayout 1px wrap headroom can still mis-wrap lines | FIXED |

## Audit pass 2 (ads/library/editor/theme/share)

| ID  | File | Sev    | Bug | Status |
|-----|------|--------|-----|--------|
| A1  | EditorScreen.kt:204 | major | "Keyboard occludes editor" — **NOT A BUG**. `windowSoftInputMode="adjustResize"` in manifest + `scaffold.systemBars` re-sizes bottom bar; verified on main display with keyboard open (`screencap`) | NOT A BUG |
| A2  | ScriptSharing.kt:21 | major | `shareText` with `EXTRA_TEXT` blows Binder cap on large scripts; unhandled in `MainActivity`; unused `shareFile` (FileProvider) exists | FIXED |
| A3  | PrompterStage.kt:155 | major | Custom-font switch stale wrap (dup P1) | FIXED |
| A4  | PrompterOverlayView.kt:211 | major | Overlay dirty check misses `customFontFile` (dup P2) | FIXED |
| A5  | EditorScreen.kt:134 | major | Undo snapshots whole document per keystroke (OOM on multi-MB scripts) | FIXED |
| A6  | MainActivity.kt / PrompterSession.kt / seed | major | After process death STAGE route restores demo-script state; user sees demo text, edits discarded | FIXED |
| A7  | StageTypefaces.kt:41 | minor | CUSTOM font weight dead (dup P5) | FIXED |
| A8  | ScriptRepository.kt:343 | minor | `writeScript` re-reads/re-parses whole library per save (quadratic) | FIXED |
| A9  | ScriptRepository.kt:34,192 | minor | Trash never purged (unbounded disk growth of deleted scripts) | FIXED |
| A10 | StageSettings.kt / preset rows | minor | `BLACK`+`INK`, `PAPER`+(`WARM_WHITE/AMBER/MINT`) unreadable combos accepted as presets | FIXED |
| A11 | MainActivity.kt:606 | minor | Sort/Favorites toggle writes the other as `false`/`UPDATED` until DataStore emits | FIXED |
| A12 | MainActivity.kt:511 | minor | Multi-file import swallows partial failures | FIXED |
| A13 | InterstitialAds.kt:90 | minor | `gate.onShown()` runs before `ad.show()` — failed show still burns the budget | FIXED |
| A14 | TeleprompterApp.kt:32 | minor | `MobileAds.initialize` failure → banner stuck on "Loading advertisement…" forever | FIXED |
| A15 | Phase0HomeScreen.kt | minor | Dead spike screen (999 lines), referenced nowhere | FIXED |
| A16 | ScriptSharing.kt | minor | Dead `shareMarkdown`/`shareFile`/`sharePlainFile`/`markdownText` | FIXED |
| A17 | EditorScreen.kt:557 | minor | O(n) regex `matchCount` runs on composition per keystroke | FIXED |
| A18 | TeleprompterTheme.kt:166 | minor | Unused `neoShadow`/`neoBorder` modifiers | FIXED |

## Verified-OK (explicit non-bugs)

- Banner: 60dp cap, resume-only rotation, single AdView per width, `onRelease` destroy — all correct.
- Interstitial: preload, 2-exit gate + 60s throttle, blocked on exit/PiP/overlay/locked, no ad after break page — correct.
- Word-safe wrap: surrogate pairs, overlong word splitting, hydration of `splitLongWord` — correct (tests pass).
- `safeDrawingPadding` in stage/overlay = never eaten by ime (using `systemBars`).
- Overlay area editor drag hit-testing; panels scroll trap; overlay indicator strip; no overlay on secondary display if absent.
- `Zip` restore hardening; `Restore` merges; `Undo` delete path ids; `sortedWith` + `CASE_INSENSITIVE_ORDER`; `stageToJson`/`jsonToStageSettings` round-trip.
- RTL direction: first-strong detection; Compose stage aligns RIGHT via physical `TextAlign.Right`; overlay uses physical `area.left`/`area.right` positioning — direction-independent, consistent between both renderers.

## Device verification (2026-11-14)

On-device after the audit-fix build (installed via `build-on-phone.sh`):

- Library resumes; banner visible with `ADVERTISEMENT` label, 60dp cap,
  test ad served; content rotated while resumed (seen across screenshots).
- Look tab: text/background presets with selection rings, custom swatches,
  edge-fade slider, both mirrors, adaptive-contrast toggle, new
  Translucent panel toggle (toggled ON: script visible through panel).
- Text tab: full system font list (Sans/Serif/Mono/Light/Medium/Condensed/
  Smallcaps/Serif Med/Cursive/Casual), `+ Add font`, weight row (Regular/
  Medium/Bold), alignment row (Center/Left/Right) — tappable, applied.
- RTL Arabic script seeded via `run-as` into the private scripts dir:
  correct bidi reordering, word-safe wrapping, over-wide compound word
  split as designed; lines render in correct order. Seed removed after test.
- Right alignment: RTL block pins to the physical right edge (was
  `TextAlign.End`, which flipped to the left under RTL before this build).
- Stage→library `Exit` verified; library unaffected otherwise (7 scripts).

Not device-verified: HSV custom-color picker dialog end-to-end; SAF custom
`.ttf/.otf` import end-to-end (both compile-verified, code-reviewed);
Phase 5a interstitial only re-checked via unit test this round.
