package dev.imranr.obtainium

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Must match SharedPreferencesPlugin.kt's DataStore name so the native
 * applier writes to the same store that Flutter's shared_preferences reads.
 */
private val Context.fleetPreferencesDataStore by
    preferencesDataStore("FlutterSharedPreferences")

/**
 * Applies a fleet/headless configuration profile to Obtainium's DataStore.
 *
 * Flutter's shared_preferences_android 2.4.26 plugin uses Jetpack DataStore
 * (preferencesDataStore("FlutterSharedPreferences")) as its primary backend,
 * NOT XML SharedPreferences. This applier writes to the same DataStore via
 * the same extension property ([sharedPreferencesDataStore]) that the Flutter
 * plugin uses, so settings are immediately visible to the Dart side.
 *
 * Handles simple preferences (bool, int/long, string). For complex settings
 * (List<String> with JSON_LIST_PREFIX encoding, app imports, update triggers)
 * use the Flutter deep-link route (obtainium://profile?profile=...).
 *
 * Example profile:
 * {
 *   "_meta": { "name": "My profile", "version": 1 },
 *   "installMethod": "shizuku",
 *   "enableBackgroundUpdates": true,
 *   "bgUpdatesOnWiFiOnly": true,
 *   "updateInterval": 360,
 *   "theme": "dark"
 * }
 */
object FleetProfileApplier {

    private val aliasToKey: Map<String, String> = mapOf(
        "installMethod" to "installMethod",
        "installerMode" to "installMethod",
        "externalInstallerPackage" to "externalInstallerPackage",
        "externalInstallerComponent" to "externalInstallerComponent",
        "useSystemFont" to "useSystemFont",
        "theme" to "theme",
        "themeColor" to "themeColor",
        "colourSchemeMode" to "colourSchemeMode",
        "useBlackTheme" to "useBlackTheme",
        "updateInterval" to "updateInterval",
        "checkOnStart" to "checkOnStart",
        "sortColumn" to "sortColumn",
        "sortOrder" to "sortOrder",
        "showAppWebpage" to "showAppWebpage",
        "pinUpdates" to "pinUpdates",
        "buryNonInstalled" to "buryNonInstalled",
        "groupBy" to "groupBy",
        "hideTrackOnlyWarning" to "hideTrackOnlyWarning",
        "hideAPKOriginWarning" to "hideAPKOriginWarning",
        "showAppDowngradeError" to "showAppDowngradeError",
        "tactileFeedbackEnabled" to "tactileFeedbackEnabled",
        "includePrereleasesByDefault" to "includePrereleasesByDefault",
        "removeOnExternalUninstall" to "removeOnExternalUninstall",
        "checkUpdateOnDetailPage" to "checkUpdateOnDetailPage",
        "disablePageTransitions" to "disablePageTransitions",
        "reversePageTransitions" to "reversePageTransitions",
        "enableBackgroundUpdates" to "enableBackgroundUpdates",
        "bgUpdatesOnWiFiOnly" to "bgUpdatesOnWiFiOnly",
        "bgUpdatesWhileChargingOnly" to "bgUpdatesWhileChargingOnly",
        "highlightTouchTargets" to "highlightTouchTargets",
        "disableSwipeActions" to "disableSwipeActions",
        "alwaysUsePhoneLayout" to "alwaysUsePhoneLayout",
        "autoExportOnChanges" to "autoExportOnChanges",
        "exportSettings" to "exportSettings",
        "onlyCheckInstalledOrTrackOnlyApps" to "onlyCheckInstalledOrTrackOnlyApps",
        "parallelDownloads" to "parallelDownloads",
        "showActionBannerForUpdateOnly" to "showActionBannerForUpdateOnly",
        "beforeNewInstallsShareToAppVerifier" to "beforeNewInstallsShareToAppVerifier",
        "shizukuPretendToBeGooglePlay" to "shizukuPretendToBeGooglePlay",
        "categories" to "categories",
        "forcedLocale" to "forcedLocale",
    )

    private val valueAliasToKey: Map<String, Map<String, Any>> = mapOf(
        "installMethod" to mapOf(
            "system" to "system",
            "shizuku" to "shizuku",
            "external" to "external",
        ),
        "groupBy" to mapOf(
            "none" to "none",
            "category" to "category",
            "source" to "source",
        ),
        "theme" to mapOf(
            "system" to 0,
            "light" to 1,
            "dark" to 2,
        ),
        "colourSchemeMode" to mapOf(
            "standard" to 0,
            "vibrant" to 1,
            "expressive" to 2,
            "materialYou" to 3,
        ),
        "sortColumn" to mapOf(
            "added" to 0,
            "nameAuthor" to 1,
            "authorName" to 2,
            "releaseDate" to 3,
        ),
        "sortOrder" to mapOf(
            "ascending" to 0,
            "descending" to 1,
        ),
        "exportSettings" to mapOf(
            "disabled" to 0,
            "enabled" to 1,
            "overwrite" to 2,
        ),
    )

    data class Result(
        val success: Boolean,
        val appliedCount: Int,
        val skippedCount: Int,
        val errors: List<String>,
        val message: String,
    )

    @JvmStatic
    fun applyJson(context: Context, json: String): Result {
        return try {
            val profile = JSONObject(json)
            applyProfile(context, profile)
        } catch (e: Exception) {
            val msg = e.message ?: "Invalid JSON"
            Result(false, 0, 0, listOf(msg), "Profile parse failed: $msg")
        }
    }

    @JvmStatic
    fun applyFromPath(context: Context, path: String): Result {
        return try {
            val json = File(path).readText(Charsets.UTF_8)
            applyJson(context, json)
        } catch (e: Exception) {
            val msg = e.message ?: "Read error"
            Result(false, 0, 0, listOf(msg), "Failed to read $path: $msg")
        }
    }

    @JvmStatic
    fun applyFromUri(context: Context, uri: Uri): Result {
        return try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return Result(false, 0, 0, listOf("Cannot open URI"),
                    "Cannot open URI: $uri")
            val json = stream.use { it.reader(Charsets.UTF_8).readText() }
            applyJson(context, json)
        } catch (e: Exception) {
            val msg = e.message ?: "URI error"
            Result(false, 0, 0, listOf(msg), "Failed to read URI $uri: $msg")
        }
    }

    private fun applyProfile(context: Context, profile: JSONObject): Result {
        val errors = mutableListOf<String>()
        val meta = profile.optJSONObject("_meta")
        val clearExisting = meta?.optBoolean("clear_existing", false) ?: false

        var applied = 0
        var skipped = 0

        runBlocking {
            context.fleetPreferencesDataStore.edit { prefs ->
                if (clearExisting) {
                    prefs.clear()
                }

                val keys = profile.keys()
                while (keys.hasNext()) {
                    val rawKey = keys.next()
                    if (rawKey.startsWith("_")) continue

                    val prefKey = resolveKey(rawKey)
                    if (prefKey == null) {
                        skipped++
                        errors.add("Unknown key: $rawKey")
                        continue
                    }

                    try {
                        val value = profile.get(rawKey)
                        val resolvedValue = resolveValue(prefKey, value)
                        putValue(prefs, prefKey, resolvedValue)
                        applied++
                    } catch (e: Exception) {
                        skipped++
                        errors.add("$rawKey: ${e.message}")
                    }
                }
            }
        }

        val message = "Applied $applied preferences, skipped $skipped" +
                if (errors.isEmpty()) "" else " (${errors.size} errors)"

        return Result(errors.isEmpty(), applied, skipped, errors, message)
    }

    private fun resolveKey(rawKey: String): String? = aliasToKey[rawKey]

    private fun resolveValue(prefKey: String, value: Any?): Any? {
        if (value !is String) return value
        return valueAliasToKey[prefKey]?.get(value) ?: value
    }

    private fun putValue(
        prefs: MutablePreferences,
        key: String,
        value: Any?,
    ) {
        when (value) {
            is Boolean -> prefs[booleanPreferencesKey(key)] = value
            is String -> prefs[stringPreferencesKey(key)] = value
            is Int -> prefs[longPreferencesKey(key)] = value.toLong()
            is Long -> prefs[longPreferencesKey(key)] = value
            is Double -> prefs[stringPreferencesKey(key)] =
                DOUBLE_PREFIX + value.toString()
            is Float -> prefs[stringPreferencesKey(key)] =
                DOUBLE_PREFIX + value.toDouble().toString()
            is JSONArray -> prefs[stringPreferencesKey(key)] = value.toString()
            else -> throw IllegalArgumentException(
                "Unsupported type: ${value?.javaClass?.simpleName}")
        }
    }

    /**
     * Must match SharedPreferencesPlugin.kt DOUBLE_PREFIX so that doubles
     * stored by the native applier are decoded correctly by the Dart side.
     */
    private const val DOUBLE_PREFIX = "VGhpcyBpcyB0aGUgcHJlZml4IGZvciBEb3VibGUu"
}
