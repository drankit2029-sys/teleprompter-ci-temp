# Teleprompter — v1.0.0 Launch Package

Developer: **Nexdert** · Contact: **Noxedus2008@gmail.com**
Last updated: 2026-11-18

---

## 1. Artifacts

| Item | Value |
|---|---|
| Signed release AAB | `app/build/outputs/bundle/baseRelease/app-base-release.aab` |
| Size | ~8.6 MB |
| SHA-256 | `c20571133c1487d8563dbf382e9c553f23b344a88b65aba2f10d403310f24777` (rebuild after any fix) |
| versionCode / versionName | `1` / `1.0.0` |
| minSdk / targetSdk / compileSdk | 28 / 37 / 37 (37.1) |
| Application ID | `com.accessoryTools.teleprompter` |
| Upload key | `keystore/teleprompter-upload.jks` (alias `upload`) |
| Key credentials | `keystore.properties` (gitignored) |

> **Back up `keystore/teleprompter-upload.jks` and `keystore.properties` now.**
> Losing the upload key blocks all future updates to the listing.

### Build commands

```bash
export TELEPROMPTER_APP_ID="ca-app-pub-6974413429688784~1896079052"
export TELEPROMPTER_BANNER="ca-app-pub-6974413429688784/9644501747"
export TELEPROMPTER_INTERSTITIAL="ca-app-pub-6974413429688784/2933295720"
./gradlew :app:bundleBaseRelease        # signed AAB
./gradlew :app:assembleBaseRelease      # signed APK for device testing
./gradlew :app:assembleBaseDebug        # debug APK (Google test ads)
```

Release builds **fail** without the three env vars and `keystore.properties`.
Debug builds always use Google's official test ad units.

---

## 2. Repositories and URLs

| Item | URL |
|---|---|
| Private source repo | https://github.com/drankit2029-sys/teleprompter |
| Public privacy policy repo | https://github.com/drankit2029-sys/teleprompter-privacy |
| **Privacy policy (Play Console URL)** | **https://drankit2029-sys.github.io/teleprompter-privacy/** |
| CI — Android verification | `.github/workflows/android-verify.yml` |
| CI — Release AAB | `.github/workflows/release.yml` |

CI runs are manual-only in the private repo to conserve Actions minutes;
the API matrix was executed on a temporary public mirror (now private, pending
deletion). CI secrets configured: `ADMOB_APP_ID`, `ADMOB_BANNER`, `ADMOB_INTERSTITIAL`,
`KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS`,
`KEYSTORE_KEY_PASSWORD`.

---

## 3. AdMob wiring

| Constant | Source | Debug | Release |
|---|---|---|---|
| App ID | `TELEPROMPTER_APP_ID` | Google test app ID | `ca-app-pub-6974413429688784~1896079052` |
| Banner unit | `TELEPROMPTER_BANNER` | Google test banner | `ca-app-pub-6974413429688784/9644501747` |
| Interstitial unit | `TELEPROMPTER_INTERSTITIAL` | Google test interstitial | `ca-app-pub-6974413429688784/2933295720` |

- Banner: inline adaptive, 60 s refresh with in-flight guard.
- Interstitial: only on explicit **Exit** (never on system Back), ≥2 actions and
  ≥60 s apart, never during overlay/PiP/locked sessions.
- UMP consent (`ConsentManager`) runs before any ad request; `canRequestAds()`
  gates banners and interstitials. **Ad privacy options** entry point lives in
  Settings → About (shown when UMP requires it).
- AdMob console still needs **GDPR** and **US states** messages under
  *Privacy & messaging*; link the app; add `app-ads.txt` if a developer website
  is used.

---

## 4. Privacy policy summary

Published at the URL above. Covers:

1. Local-first storage (scripts, settings, progress on device).
2. No accounts, analytics, or crash SDKs.
3. Google AdMob: advertising ID, IP-derived approximate location, device/app
   info; shared with Google for advertising.
4. Legal basis + consent (EEA/UK/CH) and withdrawal via Ad privacy options.
5. US state privacy rights.
6. Adaptive contrast screen capture: on-device, in-memory, never stored or
   transmitted; stops with the overlay; Stop capture notification action.
7. Display-over-other-apps permission scope.
8. Notifications.
9. Backup/export/sharing (system backup disabled; cache cleared on start).
10. Third-party services (Google AdMob / Play services).
11. Retention/deletion (in-app delete, 30-day trash, uninstall).
12. Children (not directed under 13).
13. Security (app-private storage, TLS, cleartext disabled).
14. Changes and contact.

---

## 5. Policy hardening implemented

- Prominent in-app disclosure before **MediaProjection** consent.
- Prominent in-app disclosure before **SYSTEM_ALERT_WINDOW** permission.
- No interstitial on system Back; explicit Exit only.
- Ads never shown in editor, save flows, stage, PiP, or overlay sessions.
- Capture-specific foreground notification with **Stop capture** action.
- `allowBackup="false"` (scripts never go to Google cloud backup).
- Cleartext network traffic disabled; network security config referenced.
- FileProvider narrowed to `cache/exports/`; share cache cleared on start.
- Share threshold measured in UTF-8 bytes; large scripts stream via file.
- R8 + resource shrinking enabled; debug logging gated by `BuildConfig.DEBUG`.
- `mavenLocal()` removed from dependency resolution.
- Agent (screenshot) flavors cannot produce release artifacts.

---

## 6. CI verification

`Android verification` runs on GitHub-hosted 4-core/16 GB runners:

- Unit tests + debug APK build.
- Emulator smoke test matrix: **API 28, 29, 30, 31, 33, 34, 35, 36**
  (pixel_5 profile, KVM, parallel) — **all 8 levels pass** (final run
  44388084563).
- Scripted flow (`scripts/ci-smoke-test.py`): library → stage → Window tab →
  overlay disclosure → overlay running (bar in lower half) → overlay stopped →
  Settings privacy entry points → no app crashes.
- Screenshots uploaded per API as `emulator-screenshots-api-<level>`.

`Release AAB` builds the signed bundle from the latest `main` using repo
secrets and uploads `teleprompter-v1.0.0-aab`.

---

## 7. Play Console checklist

### App setup
1. Create app → default language, app name **Teleprompter**, app/game **App**,
   free.
2. Upload the AAB to **Internal testing** first; enroll in **Play App Signing**
   (upload key = the keystore above).
3. Add testers, run the pre-launch report, then promote to production.

### Store listing
- App name: **Teleprompter**
- Short description (≤80 chars) and full description.
- App icon: `play-store/icon-512.png`
- Feature graphic: `play-store/feature-graphic-1024x500.png`
- Phone screenshots: 2–8 from `play-store/screenshots/` (ad-free)
- Category: **Productivity** (or Tools)
- Contact: **Nexdert**, **Noxedus2008@gmail.com**
- Privacy policy: `https://drankit2029-sys.github.io/teleprompter-privacy/`

### App content
- **Ads:** Yes, contains ads.
- **App access:** all functionality available without restrictions (no login).
- **Content rating:** complete IARC questionnaire (utility; ads yes; no user
  content, violence, etc.).
- **Target audience:** 13+; not child-directed; not Designed for Families.
- **Data safety:** see the table below.
- **Foreground service types:** declare `specialUse` (subtype text is in the
  manifest) and `mediaProjection`; describe the overlay/adaptive-contrast use
  case (a short demo video may be requested).
- **Government / financial / health / news:** No.
- **Advertising ID:** Yes (AdMob merges `com.google.android.gms.permission.AD_ID`).

### Data safety answers

| Question | Answer |
|---|---|
| Collects or shares required user data? | **Yes** |
| Data types | **Device or other IDs** (advertising ID); **Approximate location** (IP-derived via AdMob) |
| Shared with | **Google AdMob** |
| Purpose | **Advertising or marketing** |
| Optional? | **Required** (ads are core; no paid removal) |
| Encrypted in transit? | **Yes** (TLS; cleartext disabled) |
| Deletion | In-app script delete + uninstall; contact **Noxedus2008@gmail.com** |
| Personal info / Files / Messages / Photos / App activity / Diagnostics | **Not collected** |
| Contains ads | **Yes** |
| Target audience | **13+**, not child-directed |

---

## 8. Remaining manual checks

1. **Adaptive-contrast roundtrip on a physical device** (2 minutes):
   stage → Window → Transparent overlay → Continue → *Share entire screen* →
   *Share screen* → confirm text flips color over a bright/dark app; stop via
   notification **Stop capture**.
2. **AdMob console messages** (GDPR + US states) must exist for UMP to show the
   form in regulated regions.
3. **Play Console forms** above (data safety, content rating, FGS declarations).
4. **Back up the keystore** and keep `keystore.properties` private.
5. Bump `versionCode` for every future upload.

---

## 9. Known limitations

- CI emulators are AOSP images without Google Play services, so ads/UMP are not
  exercised there; they were verified on a physical device (test ad + consent
  flow).
- The 384dp/450dpi phone bar uses the compact layout (40dp buttons); verified
  fitting on CI pixel_5 and on the physical device.
- `allowBackup=false`: scripts are not cloud-backed up; use the in-app
  backup/export.
