# Fleet / Headless Configuration Profiles

Obtainium supports applying a JSON configuration profile and triggering automation
actions without user interaction. This is intended for fleet/headless deployments
(e.g. Termux + Shizuku + wireless ADB, or stayturgid-style orchestration) where
the settings and update flows would otherwise require fragile UI automation.

## Quick start — native Activity (simple preferences)

Push a profile JSON to the device and apply it in one shot — no Flutter boot needed:

```bash
adb push fleet_profile.json /sdcard/Download/obtainium-fleet.json

adb shell am start \
  -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
  -e profile_path /sdcard/Download/obtainium-fleet.json \
  dev.imranr.obtainium/.FleetProfileActivity
```

Or with a `file://` URI:

```bash
adb shell am start \
  -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
  -d file:///sdcard/Download/obtainium-fleet.json \
  dev.imranr.obtainium/.FleetProfileActivity
```

The activity (theme `Theme.NoDisplay` — no UI) applies the preferences and shows a
brief Toast with the result. Pass `-e silent true` to suppress the Toast.

## Quick start — deep link (full profile + actions)

When the Flutter engine is already running (or you don't mind waiting for it), use
the deep-link route for full profile support including complex settings and actions:

```bash
# Base64-url-encode your profile JSON, then:
adb shell am start \
  -d "obtainium://profile?profile=<base64url-encoded-json>&headless=true" \
  dev.imranr.obtainium/.MainActivity
```

## Profile format

Profiles are plain JSON objects. Each key maps to an Obtainium preference.

```json
{
  "_meta": {
    "name": "Obtainium fleet profile",
    "version": 1,
    "clear_existing": false
  },
  "installMethod": "shizuku",
  "enableBackgroundUpdates": true,
  "bgUpdatesOnWiFiOnly": true,
  "updateInterval": 360,
  "checkOnStart": true,
  "parallelDownloads": true,
  "includePrereleasesByDefault": false,
  "groupBy": "category",
  "theme": "dark"
}
```

### `_meta` section (optional)

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `name` | string | — | Profile label for logging |
| `version` | int | — | Schema version (reserved) |
| `clear_existing` | bool | `false` | Clear all existing prefs before applying |

### Supported value types

| JSON type | SharedPrefs method | Example |
|-----------|--------------------|---------|
| `bool` | `putBoolean` | `true` |
| `string` | `putString` | `"shizuku"` |
| `int` | `putInt` | `360` |
| `long` | `putLong` | `1234567890` |
| `float` / `double` | `putFloat` | `0.5` |
| `array` (string*) | JSON string | `["a", "b"]` (best-effort via native path; use Flutter deep-link for proper list support) |

## Key aliases

All settings use short, readable aliases. The full key table:

| Alias | Maps to | Type |
|-------|---------|------|
| `installMethod` / `installerMode` | `installMethod` | string (`system`, `shizuku`, `external`) |
| `externalInstallerPackage` | `externalInstallerPackage` | string |
| `externalInstallerComponent` | `externalInstallerComponent` | string |
| `useSystemFont` | `useSystemFont` | bool |
| `theme` | `theme` | int (0–2) or alias |
| `themeColor` | `themeColor` | int (ARGB32) |
| `colourSchemeMode` | `colourSchemeMode` | int (0–3) or alias |
| `useBlackTheme` | `useBlackTheme` | bool |
| `updateInterval` | `updateInterval` | int (minutes) |
| `checkOnStart` | `checkOnStart` | bool |
| `sortColumn` | `sortColumn` | int (0–3) or alias |
| `sortOrder` | `sortOrder` | int (0–1) or alias |
| `showAppWebpage` | `showAppWebpage` | bool |
| `pinUpdates` | `pinUpdates` | bool |
| `buryNonInstalled` | `buryNonInstalled` | bool |
| `groupBy` | `groupBy` | string (`none`, `category`, `source`) |
| `hideTrackOnlyWarning` | `hideTrackOnlyWarning` | bool |
| `hideAPKOriginWarning` | `hideAPKOriginWarning` | bool |
| `categories` | `categories` | string (JSON map) |
| `forcedLocale` | `forcedLocale` | string (language tag) |
| `showAppDowngradeError` | `showAppDowngradeError` | bool |
| `tactileFeedbackEnabled` | `tactileFeedbackEnabled` | bool |
| `includePrereleasesByDefault` | `includePrereleasesByDefault` | bool |
| `removeOnExternalUninstall` | `removeOnExternalUninstall` | bool |
| `checkUpdateOnDetailPage` | `checkUpdateOnDetailPage` | bool |
| `disablePageTransitions` | `disablePageTransitions` | bool |
| `reversePageTransitions` | `reversePageTransitions` | bool |
| `enableBackgroundUpdates` | `enableBackgroundUpdates` | bool |
| `bgUpdatesOnWiFiOnly` | `bgUpdatesOnWiFiOnly` | bool |
| `bgUpdatesWhileChargingOnly` | `bgUpdatesWhileChargingOnly` | bool |
| `highlightTouchTargets` | `highlightTouchTargets` | bool |
| `disableSwipeActions` | `disableSwipeActions` | bool |
| `alwaysUsePhoneLayout` | `alwaysUsePhoneLayout` | bool |
| `autoExportOnChanges` | `autoExportOnChanges` | bool |
| `exportSettings` | `exportSettings` | int (0–2) or alias |
| `onlyCheckInstalledOrTrackOnlyApps` | `onlyCheckInstalledOrTrackOnlyApps` | bool |
| `parallelDownloads` | `parallelDownloads` | bool |
| `showActionBannerForUpdateOnly` | `showActionBannerForUpdateOnly` | bool |
| `beforeNewInstallsShareToAppVerifier` | `beforeNewInstallsShareToAppVerifier` | bool |
| `shizukuPretendToBeGooglePlay` | `shizukuPretendToBeGooglePlay` | bool |

## Value aliases

For enum-like settings, you can use human-readable strings instead of numeric indices:

| Setting | Aliases |
|---------|---------|
| `theme` | `"system"` (0), `"light"` (1), `"dark"` (2) |
| `colourSchemeMode` | `"standard"` (0), `"vibrant"` (1), `"expressive"` (2), `"materialYou"` (3) |
| `sortColumn` | `"added"` (0), `"nameAuthor"` (1), `"authorName"` (2), `"releaseDate"` (3) |
| `sortOrder` | `"ascending"` (0), `"descending"` (1) |
| `exportSettings` | `"disabled"` (0), `"enabled"` (1), `"overwrite"` (2) |

## Default profile

A reference profile for unattended fleet deployments is bundled at
`assets/fleet_profile_default.json`. It sets Shizuku installer mode, enables
background updates, parallel downloads, and check-on-start:

```bash
# Copy the default profile from assets and apply it:
adb shell cp /data/app/dev.imranr.obtainium-*/base.apk \
  /sdcard/Download/obtainium-fleet.json   # Not how assets work — see "Bundled profile" below
```

To use the bundled default, either read it via the Dart-side applier or create
your own `fleet_profile_default.json` on device with the same content.

## Deep-link actions

In addition to fleet profiles, these deep-link actions support headless automation:

### Update apps

```bash
# Check all apps for updates and auto-install (headless)
adb shell am start \
  -d "obtainium://update/all?autoInstall=true&headless=true" \
  dev.imranr.obtainium/.MainActivity

# Check specific apps
adb shell am start \
  -d "obtainium://update?appId=app1,app2&autoInstall=true&headless=true" \
  dev.imranr.obtainium/.MainActivity

# Check-only (no install)
adb shell am start \
  -d "obtainium://update?headless=true&forceAll=true" \
  dev.imranr.obtainium/.MainActivity
```

| Parameter | Default | Description |
|-----------|---------|-------------|
| `autoInstall` | `false` (`true` for `/all`) | Download and install updates automatically |
| `forceAll` | `true` | Check all apps regardless of last-check time |
| `headless` / `exit` | `false` (`true` for `/all`) | Exit the app after the operation |
| `appId` | — | Comma-separated list of specific app IDs to check |

### Set installer mode

```bash
adb shell am start \
  -d "obtainium://settings/installer?mode=shizuku&headless=true" \
  dev.imranr.obtainium/.MainActivity
```

### Configure background update settings

```bash
adb shell am start \
  -d "obtainium://settings/background?enabled=true&wifiOnly=false&headless=true" \
  dev.imranr.obtainium/.MainActivity
```

### Configure update settings

```bash
adb shell am start \
  -d "obtainium://settings/updates?interval=720&checkOnStart=true&parallel=true&headless=true" \
  dev.imranr.obtainium/.MainActivity
```

### Import app catalog (headless — auto-confirm)

```bash
# Encode your JSON catalog as a URI component and append ?confirm=true to
# bypass the confirmation dialog:
JSON=$(cat catalog.json | jq -c | jq -sRr @uri)
adb shell am start \
  -d "obtainium://apps/$JSON?confirm=true&headless=true" \
  dev.imranr.obtainium/.MainActivity
```

The `confirm=true` parameter is critical for fleet use — without it, the user
must tap "Continue" on the import confirmation dialog.

## Hybrid native + Flutter

The native `FleetProfileActivity` handles simple preferences (bool, int, string)
directly via `SharedPreferences`, using `PreferenceManager.getDefaultSharedPreferences()`
— the same store that Flutter's `shared_preferences` plugin uses.

For complex operations that require the Flutter engine (app imports, list settings,
update triggers, `categories` as a proper JSON map), use:

1. The deep-link routes above, **or**
2. Pass `-e deep_link "obtainium://..."` to `FleetProfileActivity` to have it
   apply prefs first, then launch Flutter for the follow-up action.

## Security notes

- `FleetProfileActivity` is exported because provisioning tools run outside the
  app. Any app that can start activities can trigger it.
- Profiles should only be placed in locations your provisioning tooling controls.
- This API only writes Obtainium's own `SharedPreferences`; it does **not** grant
  Android runtime permissions. Use `pm grant` / Shizuku / `appops` for those.
- Shizuku installer mode requires granting `moe.shizuku.manager.permission.API_V23`
  to Obtainium — this must be done separately (e.g. via Shizuku or ADB).

## Limitations

- The native `FleetProfileActivity` only writes simple preferences. List settings
  (`searchDeselected`) and the `categories` JSON map are best-effort; use the
  Flutter deep-link path for full support.
- Runtime effects from preference changes apply when the Obtainium process reads
  them — typically on next launch or UI refresh.
- Installer dialogs (Play Protect, package installer confirmation) are an Android
  OS-level concern. The deep-link auto-install path uses Obtainium's silent-install
  capability, which requires Shizuku (or root) and must be granted separately.
- The `obtainium://update` action cannot handle installer dialogs that pop up
  during the install process — those require either Shizuku silent install, or
  external tooling to handle the dialog.

## See also

- [FleetProfileActivity.kt](../android/app/src/main/kotlin/dev/imranr/obtainium/FleetProfileActivity.kt)
- [FleetProfileApplier.kt](../android/app/src/main/kotlin/dev/imranr/obtainium/FleetProfileApplier.kt)
- [FleetProfileApplier.dart](../lib/providers/fleet_profile_applier.dart)
- [Default fleet profile](../assets/fleet_profile_default.json)
- [stayturgid](https://github.com/djbclark/stayturgid) — example fleet orchestration
