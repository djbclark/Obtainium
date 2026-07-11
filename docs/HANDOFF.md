# Handoff — Obtainium Fleet/Headless Profile System

## Project

This is a fork of [ImranR98/Obtainium](https://github.com/ImranR98/Obtainium) at
`github.com/djbclark/Obtainium`. It adds a fleet/headless automation system so that
orchestration tools like [stayturgid](https://github.com/djbclark/stayturgid) can
manage devices without fragile UI automation.

**Latest release:** `v1.6.3` — see [GitHub Releases](https://github.com/djbclark/Obtainium/releases)
for APK downloads.

---

## What has been done

### Fleet profile system (modeled after AutoJs6)

| File | Role |
|------|------|
| `android/.../FleetProfileActivity.kt` | Exported, themeless native Activity — applies a JSON profile to `SharedPreferences` directly (no Flutter boot). Activated via `adb shell am start -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE` with `profile_path` extra or `file://`/`content://` URI. Returns `RESULT_OK`/`RESULT_CANCELED`, shows Toast. |
| `android/.../FleetProfileApplier.kt` | Kotlin singleton `object`. Resolves 44+ key aliases + value aliases for enum settings (`theme: "dark"` → `2`, etc.). Writes bool/int/long/float/string/JSONArray to `context.getSharedPreferences(packageName + "_preferences")` — same store Flutter's `shared_preferences` plugin uses. |
| `lib/providers/fleet_profile_applier.dart` | Dart-side applier with setter-method dispatch map. Used by the deep-link handler for full profile application including complex types (categories map, string lists). |
| `assets/fleet_profile_default.json` | Bundled default profile (Shizuku installer, background updates, parallel downloads). |
| `docs/FLEET_PROFILE.md` | Complete usage docs: quick start, profile format, key/value alias tables, deep-link actions, security notes, limitations. |

### Deep-link actions (all in `lib/pages/home.dart`)

| Action | Example | Parameters |
|--------|---------|------------|
| `update` | `obtainium://update/all?autoInstall=true&headless=true` | `appId`, `forceAll`, `autoInstall`, `headless` |
| `settings/installer` | `obtainium://settings/installer?mode=shizuku&headless=true` | `mode` |
| `settings/background` | `obtainium://settings/background?enabled=true&wifiOnly=true` | `enabled`, `wifiOnly`, `chargingOnly` |
| `settings/updates` | `obtainium://settings/updates?interval=720&checkOnStart=true` | `interval`, `checkOnStart`, `parallel` |
| `profile` | `obtainium://profile?profile=<base64url-json>&headless=true` | `profile` (base64url), `url` (file://), `headless` |
| `app`/`apps` | `obtainium://apps/<json>?confirm=true` | `confirm=true` bypasses import dialog |

### Value alias system (Kotlin + Dart)

Both the native `FleetProfileApplier` and the Dart applier support human-readable
values for enum settings:

| Setting | Aliases |
|---------|---------|
| `theme` | `"system"` (0), `"light"` (1), `"dark"` (2) |
| `colourSchemeMode` | `"standard"` (0), `"vibrant"` (1), `"expressive"` (2), `"materialYou"` (3) |
| `sortColumn` | `"added"` (0), `"nameAuthor"` (1), `"authorName"` (2), `"releaseDate"` (3) |
| `sortOrder` | `"ascending"` (0), `"descending"` (1) |
| `exportSettings` | `"disabled"` (0), `"enabled"` (1), `"overwrite"` (2) |

### Release published

- Tag `v1.6.3` pushed to `github.com/djbclark/Obtainium`
- Release created with 4 APKs following AutoJs6 naming: `obtainium-v1.6.3-{abi}-{crc32}.apk`
- APKs built unsigned (no `key.properties` in repo); signed with Android debug key

---

## Architecture

### Data flow

```
Fleet orchestration tool (adb / Termux / MDM)
  │
  ├─ am start -a APPLY_FLEET_PROFILE -e profile_path /path/to/profile.json
  │   └─ FleetProfileActivity (native, Theme.NoDisplay)
  │       └─ FleetProfileApplier.applyFromPath() → write SharedPreferences
  │       └─ (optional) startActivity(obtainium://...) for complex ops
  │
  └─ am start -d "obtainium://profile?profile=<base64>"
      └─ Flutter app boots → app_links → interpretLink("profile")
          └─ FleetProfileApplier.applyJson() → SettingsProvider setters
```

### Key decisions

- **SharedPreferences store:** `context.getSharedPreferences(packageName + "_preferences")` —
  verified against `shared_preferences_android` 2.4.26 source code (`SharedPreferencesBackend`
  uses `PreferenceManager.getDefaultSharedPreferences()` which resolves to the same file).
- **Native → Flutter relay:** The native activity writes directly to SharedPreferences for
  performanc; it does NOT need Flutter. For complex ops (imports, updates, categories maps),
  pass `-e deep_link "obtainium://..."` or use the deep-link directly.
- **Import auto-confirm:** `?confirm=true` on `obtainium://apps/<json>` bypasses the
  confirmation dialog. Critical for stayturgid's headless catalog import.
- **ABI naming:** Following AutoJs6 pattern exactly — `{shortname}-v{ver}-{abi}-{crc32}.apk`.
  Shortname is `obtainium` (last segment of `dev.imranr.obtainium`). CRC32 is computed per-file
  and appended as 8-char hex. ABIs: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `universal`.

---

## What's left / known issues

### Immediate gaps

1. **Installer dialogs** — The `obtainium://update/all?autoInstall=true` path triggers
   Obtainium's silent install (Shizuku), but if Shizuku is not granted or unavailable,
   the system installer dialog appears. No deep-link mechanism can dismiss OS dialogs.
   Workaround: ensure Shizuku is granted and `installMethod` is set to `"shizuku"` in
   the fleet profile before triggering updates.

2. **Play Protect** — Google Play Protect dialogs during install are an OS issue.
   stayturgid currently handles these via UI automation (VLM/UI-TARS vision gating).
   This cannot be replaced by deep links alone.

3. **List settings** — `searchDeselected` (`List<String>`) is stored with a special
   prefix (`JSON_LIST_PREFIX`) by Flutter's `shared_preferences` plugin. The native
   applier stores it as a raw JSON string, which Flutter reads as `null` (falling back
   to the default). Use the Flutter deep-link path if you need to set this.

4. **Categories as JSON map** — The native applier stores `categories` as a raw JSON
   string, which Flutter reads correctly. The Dart applier handles it via
   `SettingsProvider.setCategories()`. Both paths work.

5. **No iOS support** — Obtainium is Android-only. Fleet profile system is Android-only.

### Enhancement ideas

6. **Add `obtainium://repository` action** — Instead of importing full app catalogs,
   allow pointing to a repository URL that Obtainium fetches periodically. This would
   enable a "live catalog" pattern where the orchestrator updates a JSON file on a
   web server and devices pick it up automatically.

7. **Event-driven updates** — Add a `obtainium://update?notifyOnly=true` or webhook
   listener so that the orchestrator can trigger immediate updates instead of waiting
   for the 15-minute WorkManager interval.

8. **Structured output** — Currently, headless mode shows a snackbar (which may not
   be visible) and then calls `SystemNavigator.pop()`. Consider logging results to
   a file or using `stdout` / notification for fleet visibility.

9. **Per-app settings in fleet profile** — Currently, profiles only set global
   preferences. Adding per-app `additionalSettings` overrides would let orchestrators
   configure APK filter regex, track-only mode, etc. for individual apps.

10. **Version lock** — A `settings/updates` deep-link could support a `lockVersion`
    parameter to pin all apps to their current versions during a fleet rollout.

### Build notes

- Build requires JDK 21 (JDK 25 causes Gradle failure with "25.0.3" error)
- NDK `29.0.14206865` is installed locally; the `build.gradle.kts` was updated from
  the original `28.2.13676358` which was not installed
- No `key.properties` exists; release builds are unsigned
- Build command: `JAVA_HOME=/opt/homebrew/opt/openjdk@21 flutter build apk --split-per-abi --flavor normal --release`

---

## Related projects

| Project | Path | Role |
|---------|------|------|
| AutoJs6 | `~/src/AutoJs6` | Fleet profile pattern inspiration (`FleetProfileActivity`, `FleetProfileApplier`, `FLEET_PROFILE.md`) |
| stayturgid | `~/src/stayturgid` | Fleet orchestration tool that consumes Obtainium's fleet profile + deep-link APIs. Currently uses UI automation; the goal is to replace UI taps with these declarative APIs. |

### stayturgid ↔ Obtainium integration points

| stayturgid action (current) | Replacement (new) |
|-----------------------------|-------------------|
| Push catalog + tap "Continue" import dialog | `obtainium://apps/<json>?confirm=true` |
| Open Settings → toggle Shizuku installer | Fleet profile: `"installMethod": "shizuku"` or `obtainium://settings/installer?mode=shizuku&headless=true` |
| Tap bulk update button + handle installer | `obtainium://update/all?autoInstall=true&headless=true` (requires Shizuku) |
| Enable/disable background update settings | Fleet profile: `"enableBackgroundUpdates": true` etc. |
| Set categories | Fleet profile: `"groupBy": "category"` + `"categories": {"Automation": 1, "stayturgid": 2}` |

---

## Key files reference

| File | Lines | What it does |
|------|-------|-------------|
| `android/.../FleetProfileActivity.kt` | 120 | Native entry point; reads JSON from intent/file/URI, delegates to applier, finishes |
| `android/.../FleetProfileApplier.kt` | 240 | Kotlin singleton: alias maps, value resolution, typed SharedPreferences writes |
| `android/.../AndroidManifest.xml` | 108 | Declares FleetProfileActivity with `APPLY_FLEET_PROFILE` action + file/content VIEW intent filters |
| `android/.../MainActivity.kt` | 119 | Unchanged — share-to-deep-link bridge + external install method channel |
| `android/app/build.gradle.kts` | 135 | NDK version updated to locally-installed `29.0.14206865` |
| `lib/pages/home.dart` | 820 | Deep-link router + `handleUpdateLink`, `handleSettingsLink`, `handleProfileLink` |
| `lib/providers/fleet_profile_applier.dart` | 310 | Dart-side applier with setter function dispatch map + value aliases |
| `lib/providers/settings_provider.dart` | 768 | All settings getters/setters; `notifyListeners()` is protected (must use setters) |
| `assets/fleet_profile_default.json` | 12 | Default fleet profile for unattended deployments |
| `docs/FLEET_PROFILE.md` | 200+ | Full documentation |
| `pubspec.yaml` | 94 | Version `1.6.3`, fleet profile asset declared |

---

## Contact / context

- This fork is part of the stayturgid fleet orchestration ecosystem
- The upstream repo is ImranR98/Obtainium — occasional upstream merges may be needed
- All deep-link URIs are backward-compatible with the upstream version (new actions
  `update`, `settings`, `profile` and `confirm` param on `apps` are additive)
