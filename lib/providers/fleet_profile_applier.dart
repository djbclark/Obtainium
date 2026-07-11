import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:obtainium/providers/apps_provider.dart';
import 'package:obtainium/providers/logs_provider.dart';
import 'package:obtainium/providers/settings_provider.dart';
import 'package:shizuku_apk_installer/shizuku_apk_installer.dart';

class FleetProfileResult {
  final bool success;
  final int appliedCount;
  final int skippedCount;
  final List<String> errors;
  final String message;

  FleetProfileResult({
    required this.success,
    required this.appliedCount,
    required this.skippedCount,
    required this.errors,
    required this.message,
  });
}

class FleetProfileApplier {
  static void _setInstallMethod(SettingsProvider sp, dynamic v) =>
      sp.installerMode = v as String;
  static void _setExternalInstallerPackage(SettingsProvider sp, dynamic v) =>
      sp.externalInstallerPackage = v as String?;
  static void _setExternalInstallerComponent(SettingsProvider sp, dynamic v) =>
      sp.externalInstallerComponent = v as String?;
  static void _setUseSystemFont(SettingsProvider sp, dynamic v) =>
      sp.useSystemFont = v as bool;
  static void _setTheme(SettingsProvider sp, dynamic v) =>
      sp.theme = ThemeSettings.values[(v as num).toInt()];
  static void _setThemeColor(SettingsProvider sp, dynamic v) =>
      sp.themeColor = Color((v as num).toInt());
  static void _setColourSchemeMode(SettingsProvider sp, dynamic v) =>
      sp.colourSchemeMode = ColourSchemeMode.values[(v as num).toInt()];
  static void _setUseBlackTheme(SettingsProvider sp, dynamic v) =>
      sp.useBlackTheme = v as bool;
  static void _setUpdateInterval(SettingsProvider sp, dynamic v) =>
      sp.updateInterval = (v as num).toInt();
  static void _setCheckOnStart(SettingsProvider sp, dynamic v) =>
      sp.checkOnStart = v as bool;
  static void _setSortColumn(SettingsProvider sp, dynamic v) =>
      sp.sortColumn = SortColumnSettings.values[(v as num).toInt()];
  static void _setSortOrder(SettingsProvider sp, dynamic v) =>
      sp.sortOrder = SortOrderSettings.values[(v as num).toInt()];
  static void _setShowAppWebpage(SettingsProvider sp, dynamic v) =>
      sp.showAppWebpage = v as bool;
  static void _setPinUpdates(SettingsProvider sp, dynamic v) =>
      sp.pinUpdates = v as bool;
  static void _setBuryNonInstalled(SettingsProvider sp, dynamic v) =>
      sp.buryNonInstalled = v as bool;
  static void _setGroupBy(SettingsProvider sp, dynamic v) =>
      sp.groupBy = v as String;
  static void _setHideTrackOnlyWarning(SettingsProvider sp, dynamic v) =>
      sp.hideTrackOnlyWarning = v as bool;
  static void _setHideAPKOriginWarning(SettingsProvider sp, dynamic v) =>
      sp.hideAPKOriginWarning = v as bool;
  static void _setShowAppDowngradeError(SettingsProvider sp, dynamic v) =>
      sp.showAppDowngradeError = v as bool;
  static void _setTactileFeedbackEnabled(SettingsProvider sp, dynamic v) =>
      sp.tactileFeedbackEnabled = v as bool;
  static void _setIncludePrereleasesByDefault(SettingsProvider sp, dynamic v) =>
      sp.includePrereleasesByDefault = v as bool;
  static void _setRemoveOnExternalUninstall(SettingsProvider sp, dynamic v) =>
      sp.removeOnExternalUninstall = v as bool;
  static void _setCheckUpdateOnDetailPage(SettingsProvider sp, dynamic v) =>
      sp.checkUpdateOnDetailPage = v as bool;
  static void _setDisablePageTransitions(SettingsProvider sp, dynamic v) =>
      sp.disablePageTransitions = v as bool;
  static void _setReversePageTransitions(SettingsProvider sp, dynamic v) =>
      sp.reversePageTransitions = v as bool;
  static void _setEnableBackgroundUpdates(SettingsProvider sp, dynamic v) =>
      sp.enableBackgroundUpdates = v as bool;
  static void _setBgUpdatesOnWiFiOnly(SettingsProvider sp, dynamic v) =>
      sp.bgUpdatesOnWiFiOnly = v as bool;
  static void _setBgUpdatesWhileChargingOnly(SettingsProvider sp, dynamic v) =>
      sp.bgUpdatesWhileChargingOnly = v as bool;
  static void _setHighlightTouchTargets(SettingsProvider sp, dynamic v) =>
      sp.highlightTouchTargets = v as bool;
  static void _setDisableSwipeActions(SettingsProvider sp, dynamic v) =>
      sp.disableSwipeActions = v as bool;
  static void _setAlwaysUsePhoneLayout(SettingsProvider sp, dynamic v) =>
      sp.alwaysUsePhoneLayout = v as bool;
  static void _setAutoExportOnChanges(SettingsProvider sp, dynamic v) =>
      sp.autoExportOnChanges = v as bool;
  static void _setExportSettings(SettingsProvider sp, dynamic v) =>
      sp.exportSettings = (v as num).toInt();
  static void _setOnlyCheckInstalledOrTrackOnlyApps(
          SettingsProvider sp, dynamic v) =>
      sp.onlyCheckInstalledOrTrackOnlyApps = v as bool;
  static void _setParallelDownloads(SettingsProvider sp, dynamic v) =>
      sp.parallelDownloads = v as bool;
  static void _setSearchDeselected(SettingsProvider sp, dynamic v) =>
      sp.searchDeselected = (v as List).cast<String>();
  static void _setShowActionBannerForUpdateOnly(SettingsProvider sp, dynamic v) =>
      sp.showActionBannerForUpdateOnly = v as bool;
  static void _setBeforeNewInstallsShareToAppVerifier(
          SettingsProvider sp, dynamic v) =>
      sp.beforeNewInstallsShareToAppVerifier = v as bool;
  static void _setShizukuPretendToBeGooglePlay(SettingsProvider sp, dynamic v) =>
      sp.shizukuPretendToBeGooglePlay = v as bool;
  static void _setCategories(SettingsProvider sp, dynamic v,
          {AppsProvider? appsProvider}) =>
      sp.setCategories(
          (v as Map).map((k, v) => MapEntry(k.toString(), (v as num).toInt())),
          appsProvider: appsProvider);
  static void _setForcedLocale(SettingsProvider sp, dynamic v) =>
      sp.forcedLocale = (v as String?) != null && (v as String).isNotEmpty
          ? _tryParseLocale(v)
          : null;

  static Locale? _tryParseLocale(String? localeString) {
    if (localeString == null) return null;
    final split = localeString.split('-');
    if (split.length == 3) {
      return Locale.fromSubtags(
        languageCode: split[0],
        scriptCode: split[1],
        countryCode: split[2],
      );
    }
    if (split.length == 2) {
      return Locale(split[0], split[1]);
    }
    if (split.isNotEmpty) {
      return Locale(split[0]);
    }
    return null;
  }

  static final Map<String, Map<String, dynamic>> _valueAliases = {
    'theme': {'system': 0, 'light': 1, 'dark': 2},
    'colourSchemeMode': {
      'standard': 0,
      'vibrant': 1,
      'expressive': 2,
      'materialYou': 3,
    },
    'sortColumn': {
      'added': 0,
      'nameAuthor': 1,
      'authorName': 2,
      'releaseDate': 3,
    },
    'sortOrder': {'ascending': 0, 'descending': 1},
    'exportSettings': {'disabled': 0, 'enabled': 1, 'overwrite': 2},
  };

  static dynamic _resolveValue(String key, dynamic value) {
    if (value is! String) return value;
    final aliases = _valueAliases[key];
    if (aliases == null) return value;
    return aliases[value] ?? value;
  }

  static final Map<String, void Function(SettingsProvider, dynamic)>
      _setterMap = {
    'installMethod': _setInstallMethod,
    'installerMode': _setInstallMethod,
    'externalInstallerPackage': _setExternalInstallerPackage,
    'externalInstallerComponent': _setExternalInstallerComponent,
    'useSystemFont': _setUseSystemFont,
    'theme': _setTheme,
    'themeColor': _setThemeColor,
    'colourSchemeMode': _setColourSchemeMode,
    'useBlackTheme': _setUseBlackTheme,
    'updateInterval': _setUpdateInterval,
    'checkOnStart': _setCheckOnStart,
    'sortColumn': _setSortColumn,
    'sortOrder': _setSortOrder,
    'showAppWebpage': _setShowAppWebpage,
    'pinUpdates': _setPinUpdates,
    'buryNonInstalled': _setBuryNonInstalled,
    'groupBy': _setGroupBy,
    'hideTrackOnlyWarning': _setHideTrackOnlyWarning,
    'hideAPKOriginWarning': _setHideAPKOriginWarning,
    'showAppDowngradeError': _setShowAppDowngradeError,
    'tactileFeedbackEnabled': _setTactileFeedbackEnabled,
    'includePrereleasesByDefault': _setIncludePrereleasesByDefault,
    'removeOnExternalUninstall': _setRemoveOnExternalUninstall,
    'checkUpdateOnDetailPage': _setCheckUpdateOnDetailPage,
    'disablePageTransitions': _setDisablePageTransitions,
    'reversePageTransitions': _setReversePageTransitions,
    'enableBackgroundUpdates': _setEnableBackgroundUpdates,
    'bgUpdatesOnWiFiOnly': _setBgUpdatesOnWiFiOnly,
    'bgUpdatesWhileChargingOnly': _setBgUpdatesWhileChargingOnly,
    'highlightTouchTargets': _setHighlightTouchTargets,
    'disableSwipeActions': _setDisableSwipeActions,
    'alwaysUsePhoneLayout': _setAlwaysUsePhoneLayout,
    'autoExportOnChanges': _setAutoExportOnChanges,
    'exportSettings': _setExportSettings,
    'onlyCheckInstalledOrTrackOnlyApps': _setOnlyCheckInstalledOrTrackOnlyApps,
    'parallelDownloads': _setParallelDownloads,
    'searchDeselected': _setSearchDeselected,
    'forcedLocale': _setForcedLocale,
    'showActionBannerForUpdateOnly': _setShowActionBannerForUpdateOnly,
    'beforeNewInstallsShareToAppVerifier':
        _setBeforeNewInstallsShareToAppVerifier,
    'shizukuPretendToBeGooglePlay': _setShizukuPretendToBeGooglePlay,
  };

  /// Whether the profile has a `_meta.grantFirst` flag set.
  static bool _grantFirst(Map<String, dynamic> profile) {
    final meta = profile['_meta'];
    if (meta is! Map) return false;
    return meta['grantFirst'] == true;
  }

  static Future<FleetProfileResult> applyJson(
    SettingsProvider sp, {
    required String json,
    AppsProvider? appsProvider,
  }) async {
    try {
      final parsed = jsonDecode(json);
      if (parsed is! Map) {
        return FleetProfileResult(
          success: false,
          appliedCount: 0,
          skippedCount: 0,
          errors: ['Profile must be a JSON object'],
          message: 'Invalid profile format',
        );
      }
      return await applyMap(sp, parsed.cast<String, dynamic>(),
          appsProvider: appsProvider);
    } catch (e) {
      return FleetProfileResult(
        success: false,
        appliedCount: 0,
        skippedCount: 0,
        errors: [e.toString()],
        message: 'Failed to parse JSON: $e',
      );
    }
  }

  static Future<FleetProfileResult> applyMap(
    SettingsProvider sp,
    Map<String, dynamic> profile, {
    AppsProvider? appsProvider,
  }) async {
    final errors = <String>[];
    var applied = 0;
    var skipped = 0;
    final grantFirst = _grantFirst(profile);

    // If grantFirst is requested, try to grant Shizuku permission before
    // setting installMethod. If it fails, skip installMethod.
    bool? shizukuGranted;
    if (grantFirst && profile['installMethod'] == 'shizuku') {
      try {
        final res = await ShizukuApkInstaller().checkPermission();
        shizukuGranted = res?.startsWith('granted') ?? false;
        if (!shizukuGranted) {
          unawaited(
            LogsProvider().add(
              'Fleet profile: grantFirst enabled but Shizuku permission '
                  'not granted ($res); skipping installMethod',
              level: LogLevel.warning,
            ),
          );
        }
      } catch (e) {
        unawaited(
          LogsProvider().add(
            'Fleet profile: grantFirst Shizuku check failed: $e',
            level: LogLevel.warning,
          ),
        );
        shizukuGranted = false;
      }
    }

    final keys = profile.keys.toList();
    for (final alias in keys) {
      if (alias.startsWith('_')) continue;

      final setter = _setterMap[alias];
      if (setter == null) {
        skipped++;
        errors.add('Unknown setting: $alias');
        continue;
      }

      // Skip installMethod if grantFirst was requested but failed.
      if (alias == 'installMethod' || alias == 'installerMode') {
        if (grantFirst && shizukuGranted == false &&
            profile[alias] == 'shizuku') {
          skipped++;
          errors.add('installMethod: Shizuku permission not granted');
          continue;
        }
      }

      try {
        final resolved = _resolveValue(alias, profile[alias]);
        if (alias == 'categories') {
          _setCategories(sp, resolved, appsProvider: appsProvider);
        } else {
          setter(sp, resolved);
        }
        applied++;
      } catch (e) {
        skipped++;
        errors.add('$alias: $e');
      }
    }

    final message = 'Applied $applied settings, skipped $skipped' +
        (errors.isEmpty ? '' : ' (${errors.length} errors)');

    return FleetProfileResult(
      success: errors.isEmpty,
      appliedCount: applied,
      skippedCount: skipped,
      errors: errors,
      message: message,
    );
  }

  static Future<FleetProfileResult> applyFromFile(
    SettingsProvider sp, {
    required String path,
    AppsProvider? appsProvider,
  }) async {
    try {
      final file = File(path);
      if (!file.existsSync()) {
        return FleetProfileResult(
          success: false,
          appliedCount: 0,
          skippedCount: 0,
          errors: ['File not found: $path'],
          message: 'Failed to read profile from $path',
        );
      }
      final json = file.readAsStringSync();
      return await applyJson(sp, json: json, appsProvider: appsProvider);
    } catch (e) {
      return FleetProfileResult(
        success: false,
        appliedCount: 0,
        skippedCount: 0,
        errors: [e.toString()],
        message: 'Failed to read $path: $e',
      );
    }
  }
}
