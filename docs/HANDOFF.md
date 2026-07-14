# Handoff — Obtainium Fleet/Headless Profile System

## Project

This is a fork of [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) at
`github.com/djbclark/Obtainium`. It adds a fleet/headless automation system so that
orchestration tools like [stayturgid](https://github.com/djbclark/stayturgid) can
manage devices without fragile UI automation.

**Latest release:** `v1.6.6` (debug10) — see [GitHub Releases](https://github.com/djbclark/Obtainium/releases)

---

## Release naming & process

### Convention

Every release produces 4 APKs using this naming pattern:

```
obtainium-v{version}-stayturgid-debug{n}-{abi}.apk
```

| Component | Meaning | Example |
|-----------|---------|---------|
| `obtainium` | App name (upstream convention) | `obtainium` |
| `v{version}` | Upstream version from `pubspec.yaml` | `v1.6.6` |
| `stayturgid` | Fork marker differentiating from upstream | `stayturgid` |
| `debug{n}` | **Sequential** debug build number | `debug10` |
| `{abi}` | `arm64-v8a`, `armeabi-v7a`, `x86_64`, or `universal` | `arm64-v8a` |
| `.apk` | Extension | `.apk` |

**Full example:** `obtainium-v1.6.6-stayturgid-debug10-arm64-v8a.apk`

### Debug build number rules

- The `debug{n}` number is a **sequential counter per fork** — never reset per version.
- Each time APKs are rebuilt (bug fix, new feature), increment `n`.
- The current counter is `debug10` for v1.6.6. The next rebuild uses `debug11`.
- Prior releases: v1.6.3 = `debug1`, v1.6.4 = `debug2`, v1.6.5 = `debug3`→`debug9`, v1.6.6 = `debug10`.
- Tags are created fresh per version; same-version rebuilds force-move the tag.

### Release process

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21

# Clean and build split APKs + universal
rm -rf build/ .dart_tool/
flutter pub get
flutter build apk --split-per-abi --flavor normal --release
flutter build apk --flavor normal --release

# Name APKs (edit APP_BUILD to next sequential number)
APP_VERSION="1.6.5" APP_BUILD="10" DIR="build/app/outputs/flutter-apk"
for abi in arm64-v8a armeabi-v7a x86_64; do
  cp "$DIR/app-${abi}-normal-release.apk" \
     "$DIR/obtainium-v${APP_VERSION}-stayturgid-debug${APP_BUILD}-${abi}.apk"
done
cp "$DIR/app-normal-release.apk" \
   "$DIR/obtainium-v${APP_VERSION}-stayturgid-debug${APP_BUILD}-universal.apk"

# Force-move tag, delete old release, create new one
git add -A && git commit -m "..." && git push origin main
git tag -f v1.6.5 HEAD && git push origin v1.6.5 --force
gh release delete v1.6.5
gh release create v1.6.5 --title "..." --notes "..." \
  build/app/outputs/flutter-apk/obtainium-v1.6.5-stayturgid-debug10-*.apk
```

### Existing releases (draft=false)

| Release | Tag | debug{n} | Content |
|---------|-----|----------|---------|
| v1.6.3 | `v1.6.3` | `debug1` | Fleet profile system (native Activity, deep-links, docs) |
| v1.6.4 | `v1.6.4` | `debug2` | Silent-only install, import feedback, per-app settings |
| v1.6.5 | `v1.6.5` | `debug9` | Broadcast results, grantFirst, result file, key prefix fixes |
| v1.6.6 | `v1.6.6` | `debug10` | Fix self-update URL to fork (djbclark/Obtainium) |

---

## What has been done

### Fleet profile system (modeled after AutoJs6)

| File | Role |
|------|------|
| `android/.../FleetProfileActivity.kt` (140 lines) | Exported, themeless native Activity. Reads a profile JSON from `profile_path` extra, `file://` URI, or `content://` URI. Writes result file (`obtainium-fleet-result.json`) alongside the profile. Returns `RESULT_OK`/`RESULT_CANCELED`. Supports `-e silent`, `-e result_path`, `-e deep_link`. |
| `android/.../FleetProfileApplier.kt` (240 lines) | Kotlin singleton `object`. Resolves 44+ key aliases + value aliases for enum settings. Writes to `getSharedPreferences("FlutterSharedPreferences", ...)` using `SharedPreferences.Editor`. **Keys are prefixed with `"flutter."`** to match Flutter's `SharedPreferences` class default prefix. Result data class has `toJson()` for result file writing. |
| `lib/providers/fleet_profile_applier.dart` (340 lines) | Dart-side async applier with setter-method dispatch map. Handles `grantFirst` Shizuku permission check. Used by `obtainium://profile` deep-link. |
| `lib/providers/headless_result.dart` (90 lines) | Writes `headless_result.json` to app storage AND sends `dev.imranr.obtainium.action.HEADLESS_RESULT` broadcast intent with `action`/`success`/`message`/`count`/`updatedCount`/`failedCount`/`skippedCount` extras. |
| `assets/fleet_profile_default.json` | Bundled default profile (Shizuku installer, background updates, parallel downloads, check-on-start). |
| `docs/FLEET_PROFILE.md` | Complete docs: format, aliases, deep-link actions, security notes, scoped storage guidance. |

### Native → Flutter wiring (critical — do not break)

The native `FleetProfileApplier` writes preferences to `FlutterSharedPreferences.xml` using:
```kotlin
val prefs = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
val editor = prefs.edit()
editor.putString("flutter.installMethod", "shizuku")  // ← NOTE the prefix
editor.commit()
```

**Key details that were discovered through painful debugging:**

1. **FlutterSharedPreferences name** — The Obtainium `SettingsProvider` uses `SharedPreferences.getInstance()` which hits the **legacy** `LegacySharedPreferencesPlugin` backend. That backend writes to and reads from `FlutterSharedPreferences.xml` (the XML file, NOT DataStore).

2. **`flutter.` key prefix** — Flutter's `shared_preferences` Dart class (`shared_preferences.dart:22`) has a default prefix `'flutter.'`. So when Dart calls `prefs.getString('installMethod')`, it reads key `flutter.installMethod` from `FlutterSharedPreferences.xml`. The native applier MUST prefix all keys with `"flutter."`. This is done in `putValue()` via `val prefixedKey = FLUTTER_PREFIX + key`.

3. **NOT DataStore** — The `SharedPreferencesPlugin.kt` also registers a DataStore backend (`preferencesDataStore("FlutterSharedPreferences")`), but the app's `SettingsProvider` uses the legacy path, which reads from the XML file. The native applier should NOT write to DataStore.

### Deep-link actions (all in `lib/pages/home.dart` ~900 lines)

| Action | Example URI | Key params |
|--------|------------|------------|
| `update` | `obtainium://update/all?autoInstall=true&headless=true&silentOnly=true` | `appId`, `forceAll`, `autoInstall`, `headless`/`exit`, `silentOnly`/`silent` |
| `settings/installer` | `obtainium://settings/installer?mode=shizuku&headless=true` | `mode` (`system`/`shizuku`/`external`) |
| `settings/background` | `obtainium://settings/background?enabled=true&wifiOnly=true` | `enabled`, `wifiOnly`, `chargingOnly` |
| `settings/updates` | `obtainium://settings/updates?interval=720&checkOnStart=true` | `interval`, `checkOnStart`, `parallel` |
| `profile` | `obtainium://profile?profile=<base64url-json>&headless=true` | `profile` (base64url), `url` (file://), `headless` |
| `app`/`apps` | `obtainium://apps/<json>?confirm=true&headless=true` | `confirm=true` bypasses import dialog |
| `appSettings` | `obtainium://appSettings/<appId>?settings=<base64url-json>&headless=true` | `settings` (base64url JSON to merge into additionalSettings) |

### Fleet profile `_meta` flags

| Flag | Description |
|------|-------------|
| `_meta.grantFirst: true` | Before setting `installMethod: "shizuku"`, calls `ShizukuApkInstaller().checkPermission()`. If Shizuku is already granted, applies normally. If not, skips `installMethod` setting with a warning. |
| `_meta.clear_existing: true` | Clear all existing preferences before applying the profile. |

### Value alias system

Both native (Kotlin) and Dart appliers support human-readable values for enum settings.
Profiles can use `"theme": "dark"` instead of `"theme": 2`.

| Setting | Aliases (→ stored value) |
|---------|---------------------------|
| `theme` | `"system"` (0), `"light"` (1), `"dark"` (2) |
| `colourSchemeMode` | `"standard"` (0), `"vibrant"` (1), `"expressive"` (2), `"materialYou"` (3) |
| `sortColumn` | `"added"` (0), `"nameAuthor"` (1), `"authorName"` (2), `"releaseDate"` (3) |
| `sortOrder` | `"ascending"` (0), `"descending"` (1) |
| `exportSettings` | `"disabled"` (0), `"enabled"` (1), `"overwrite"` (2) |
| `installMethod` | `"system"`, `"shizuku"`, `"external"` |
| `groupBy` | `"none"`, `"category"`, `"source"` |

---

## Architecture

### Data flow (native path)

```
adb push fleet.json /data/local/tmp/obtainium-fleet.json

adb shell am start -W \
  -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
  -e profile_path /data/local/tmp/obtainium-fleet.json \
  dev.imranr.obtainium/.FleetProfileActivity

  └─ FleetProfileActivity.onCreate()
       ├─ FleetProfileApplier.applyFromPath() → applyProfile()
       │    └─ getSharedPreferences("FlutterSharedPreferences")
       │       └─ editor.putString("flutter.installMethod", "shizuku")
       │       └─ editor.commit()
       ├─ writeResultFile() → /data/local/tmp/obtainium-fleet-result.json
       ├─ setResult(RESULT_OK or RESULT_CANCELED)
       └─ finish()

adb shell cat /data/local/tmp/obtainium-fleet-result.json
# → {"success":true,"appliedCount":3,"skippedCount":0,"errors":[],"message":"Applied 3..."}
```

### Data flow (Flutter path)

```
adb shell am start \
  -d "obtainium://profile?profile=<base64url>&headless=true"

  └─ Flutter boots → app_links → interpretLink("profile")
       └─ FleetProfileApplier.applyJson() → SettingsProvider setters
       └─ HeadlessResult.write() → file + broadcast
       └─ maybeExit() → SystemNavigator.pop()
```

### Key decisions & known pitfalls

- **SharedPreferences store:** `getSharedPreferences("FlutterSharedPreferences", MODE_PRIVATE)` — **NOT** `packageName + "_preferences"`.
- **Key prefix:** All keys MUST be prefixed with `"flutter."` — Dart's `shared_preferences` class default prefix. The `FLUTTER_PREFIX` constant in `FleetProfileApplier.kt` handles this.
- **Legacy path (not DataStore):** SettingsProvider uses the legacy `SharedPreferences.getInstance()` → `LegacySharedPreferencesPlugin` → XML file. Do not try to write to DataStore/protobuf.
- **Profile location:** Use `/data/local/tmp/` (no scoped storage restrictions). Do NOT use `/sdcard/Download/` on Android 11+.
- **Result verification:** The native activity writes `obtainium-fleet-result.json` alongside the profile. Use `-e result_path` to override. Use `am start -W` (wait flag) to block until the activity returns its exit code.
- **Self-update URL:** `obtainiumUrl` at `settings_provider.dart:23` was changed from `ImranR98/Obtainium` to `djbclark/Obtainium`. On first run, Obtainium auto-adds itself as a tracked app using this URL. Without this fix, self-update checks fail against upstream's incompatible APK naming.
- **APK naming:** `obtainium-v{version}-stayturgid-debug{n}-{abi}.apk`. `debug{n}` is sequential across all versions, never reset. All releases are `draft=false`.
- **Build toolchain:** JDK 21 required (JDK 25 fails with "25.0.3" error). NDK `29.0.14206865`. No `key.properties` — APKs are unsigned.

---

## What's left / known issues

### Immediate gaps

1. **Installer dialogs** — `obtainium://update/all?autoInstall=true` triggers Shizuku silent install. If Shizuku isn't granted or available, the system installer dialog appears. No deep-link can dismiss OS dialogs. Use `silentOnly=true` to pre-filter.

2. **Play Protect** — OS-level dialog. stayturgid handles this via VLM/UI-TARS vision gating. Not fixable from Obtainium.

3. **List settings** — `searchDeselected` (`List<String>`) uses `JSON_LIST_PREFIX` encoding. The native applier stores it as a plain JSON string. Use the Flutter deep-link path for proper list encoding.

4. **Categories as JSON map** — Works correctly via both paths (stored as JSON string, parsed by SettingsProvider).

5. **No iOS support** — Android-only.

### Enhancement ideas

6. **`obtainium://repository` action** — Live catalog URL that Obtainium fetches periodically (pull-based fleet management).

7. **Event-driven updates** — Webhook listener for immediate update triggers (bypass 15-min WorkManager interval).

8. **Version lock** — `lockVersion` param to pin all apps to current versions during fleet rollout.

9. **Broadcast permissions** — `HEADLESS_RESULT` currently has no permission gate. Could add signature-level protection.

10. **grantFirst for native path** — `grantFirst` only works in Flutter deep-link path (needs Shizuku plugin). Native activity can't call `checkPermission()`.

---

## Build environment

| Item | Value | Note |
|------|-------|------|
| JDK | `/opt/homebrew/opt/openjdk@21` | JDK 25 causes Gradle failure |
| Flutter | `3.44.5` (Homebrew) | `.flutter` submodule is present but not used |
| NDK | `29.0.14206865` | Changed from upstream `28.2.13676358` in `build.gradle.kts` |
| Android SDK | `/Users/djbclark/Library/Android/sdk` | Build-tools 37.0.0, platforms android-37 |
| Signing | None | No `key.properties` in repo; APKs built unsigned |
| Java | `openjdk version 25.0.3` (system default) | Must set `JAVA_HOME` to JDK 21 for builds |

**Build command:**
```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@21 flutter build apk --split-per-abi --flavor normal --release
```

---

## Related projects

| Project | Path | Role |
|---------|------|------|
| AutoJs6 | `~/src/AutoJs6` | Fleet profile pattern: `FleetProfileActivity`, `FleetProfileApplier`, `FLEET_PROFILE.md` |
| stayturgid | `~/src/stayturgid` | Fleet orchestration consumer. Currently uses UI automation to manage Obtainium; goal is to replace with these declarative APIs. |

### stayturgid ↔ Obtainium integration

| stayturgid action (current) | Replacement (new) |
|-----------------------------|-------------------|
| Push catalog + tap "Continue" | `obtainium://apps/<json>?confirm=true&headless=true` |
| Settings → toggle Shizuku | Fleet profile: `"installMethod": "shizuku"` or `obtainium://settings/installer?mode=shizuku` |
| Tap bulk update + handle dialogs | `obtainium://update/all?autoInstall=true&headless=true&silentOnly=true` |
| Background update settings | Fleet profile: `"enableBackgroundUpdates": true`, `"bgUpdatesOnWiFiOnly": true` |
| Set categories | Fleet profile: `"groupBy": "category"` + `"categories": {"Automation": 1}` |
| Per-app APK filter / trackOnly | `obtainium://appSettings/<appId>?settings=<base64url>&headless=true` |
| Verify headless result | `adb shell cat /data/local/tmp/obtainium-fleet-result.json` or `BroadcastReceiver` for `HEADLESS_RESULT` |
| Set Shizuku + grant permission | Fleet profile: `{"_meta": {"grantFirst": true}, "installMethod": "shizuku"}` |

---

## Key files reference

| File | Lines | Role |
|------|-------|------|
| `android/.../FleetProfileActivity.kt` | 140 | Native entry point; reads JSON, writes result file, returns exit code |
| `android/.../FleetProfileApplier.kt` | 245 | Kotlin singleton: alias maps, value resolution, `flutter.`-prefixed SharedPreferences writes, `toJson()` |
| `android/.../AndroidManifest.xml` | 108 | Declares `FleetProfileActivity` with `APPLY_FLEET_PROFILE` + file/content VIEW filters |
| `android/.../MainActivity.kt` | 119 | Share-to-deep-link bridge + external install method channel (unchanged) |
| `android/app/build.gradle.kts` | 135 | NDK `29.0.14206865`; no extra deps beyond `desugar_jdk_libs` |
| `lib/pages/home.dart` | 930+ | Deep-link router: `handleUpdateLink`, `handleSettingsLink`, `handleProfileLink`, `interpretLink` (appSettings, profile, apps, update, settings) |
| `lib/providers/fleet_profile_applier.dart` | 340 | Dart applier: async `applyMap` with `grantFirst`, `_resolveValue`, `FleetProfileResult` |
| `lib/providers/headless_result.dart` | 90 | File + broadcast intent sender |
| `lib/providers/settings_provider.dart` | 768 | Uses legacy `SharedPreferences.getInstance()` (NOT DataStore); all settings getters/setters. `obtainiumUrl` at line 23 changed to `djbclark/Obtainium` for self-update. |
| `assets/fleet_profile_default.json` | 12 | Default profile |
| `docs/FLEET_PROFILE.md` | 270+ | Full docs including scoped storage guidance |
| `pubspec.yaml` | 94 | Version `1.6.6+2345`; Flutter `>=3.44.0` |

---

## Upstream PR plan

The following changes are generally useful (not fleet-specific) and suitable
for submitting to ImranR98/Obtainium. Each PR should thank the upstream for the
project and frame the feature as automation-friendly (Tasker, MacroDroid, ADB,
browser links), not fleet-orchestration.

### PR 1: Deep-link actions for update check, settings, and headless exit

**PR:** [#3071](https://github.com/ImranR98/Obtainium/pull/3071) (draft)
**Branch:** `pr/deep-link-actions`

**Files:** `lib/pages/home.dart` (~80 lines additive, no existing code changed)

**What to submit:**
- `handleUpdateLink` (±45 lines): `obtainium://update/all?autoInstall=true&headless=true`
  with params `appId`, `forceAll`, `autoInstall`, `headless`/`exit`, `silentOnly`
- `handleSettingsLink` (±35 lines): `obtainium://settings/installer?mode=shizuku`,
  `obtainium://settings/background?enabled=true&wifiOnly=true`,
  `obtainium://settings/updates?interval=720&checkOnStart=true`
- `maybeExit(Uri uri)` helper (±8 lines): reads `headless`/`exit` param, calls
  `SystemNavigator.pop()` after 500ms delay to give snackbar time to render
- New `else if` branches in `interpretLink()` for `action == 'update'` and
  `action == 'settings'`

**What to strip before submitting:**
- Remove `fleet_profile_applier.dart` import (fork-only)
- Remove `headless_result.dart` import (fork-only)
- Simplify `maybeExit` to just take `Uri uri` — strip `action`/`success`/`message`/`count`
  named params since `HeadlessResult.write()` doesn't exist in upstream
- Remove `maybeExit` calls with extra params; replace with simple `maybeExit(uri)`
- Keep `_filterSilent` helper — uses `canInstallSilentlyInBackground` which is
  an upstream method on AppsProvider

**Code review notes:**
- `silentOnly` param defaults to off; backward-compatible
- `autoInstall` defaults to true when path is `/all` or `/update`, false otherwise
- `forceAll` defaults to true (string comparison against "false")
- `headless` defaults to true for `/all` path unless explicitly set to `"false"`
- `_filterSilent` uses `Future.wait` — O(n) concurrent checks, fine for typical sizes
- `appNavigatorKey.currentContext` captured before `unawaited` — safe
- Duplicated logic refactored into single flow in commit `6212825`

### PR 2 (resubmitted): Auto-confirm import gated by trusted caller

**PR:** [#3073](https://github.com/ImranR98/Obtainium/pull/3073) (draft)
**Branch:** `pr/confirm-import-trusted`
**Depends on:** #3071

Replaces the withdrawn #3072. `confirm=true` now only takes effect when the
intent came from a privileged caller (ADB shell / `am start`), detected via
`Activity.referrer == null` in `MainActivity.kt`. Browser links always set a
non-null referrer, so phishing links can't bypass the dialog. When privileged,
`MainActivity.kt` appends `confirmedBy=system` to the URI — Dart checks for this
marker.

**Files:** `lib/pages/home.dart` (~8 lines)

**What to submit:**
- Guard around the existing `showDialog` call in the `app`/`apps` action:
  `if (!autoConfirm) { if (await showDialog(...) == null) return; }`
- Read `importHeadless` from URI params for optional exit after import

**Code review notes:**
- `confirm=true` is opt-in — no behavior change for manual users
- Depends on PR 1's `maybeExit` for the exit path. If submitted independently,
  inline the exit: `await Future.delayed(500ms); SystemNavigator.pop();`
- Guard pattern uses early return from `showDialog` cancel — clean, idiomatic

### PR 3: Dropped — NDK version change

The `android/app/build.gradle.kts` NDK version change (`28.2.13676358` →
`29.0.14206865`) is a local build environment issue, not a code bug.
Upstream likely has NDK 28.x in their CI pipeline. Not submitting.

### Fleet-specific code (stays fork-only)

| Feature | Reason |
|---------|--------|
| `obtainium://profile` deep-link | Depends on `fleet_profile_applier.dart` |
| `obtainium://appSettings` deep-link | Per-app config via deep-link; narrow use case |
| `FleetProfileActivity.kt` | Exported Activity for MDM/fleet orchestration |
| `FleetProfileApplier.kt` (Kotlin) | Native applier companion |
| `fleet_profile_applier.dart` (Dart) | Dart applier with `grantFirst` |
| `headless_result.dart` | Broadcast + file result for fleet orchestration |
| `grantFirst` / `_meta` flags | Depends on Shizuku plugin internals |
| `obtainiumUrl` change | Points to fork, not upstream |
