package dev.imranr.obtainium

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Applies a fleet/headless configuration profile to Obtainium's default
 * SharedPreferences.
 *
 * This is the native analogue of the Dart-side FleetProfileApplier.
 * It handles simple preferences (bool, int, long, float, string).
 * For complex settings (List<String>, app imports, update triggers)
 * use the Flutter deep-link route (obtainium://profile?profile=...).
 *
 * Profiles are JSON objects mapping preference keys to typed values.
 *
 * Key aliases let you use short names instead of raw preference keys.
 * Value aliases let you use human-readable values for enum-like settings
 * (e.g. "theme": "dark" instead of "theme": 2).
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

    /**
     * Human-readable value aliases for enum-like preference keys.
     * Maps "key" -> { "human_value" -> stored_value }.
     */
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
        val prefs = context.getSharedPreferences(
            context.packageName + "_preferences", Context.MODE_PRIVATE)
        val meta = profile.optJSONObject("_meta")
        val clearExisting = meta?.optBoolean("clear_existing", false) ?: false

        var applied = 0
        var skipped = 0

        val editor = prefs.edit()
        if (clearExisting) {
            editor.clear()
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
                putValue(editor, prefKey, resolvedValue)
                applied++
            } catch (e: Exception) {
                skipped++
                errors.add("$rawKey: ${e.message}")
            }
        }

        editor.commit()

        val message = "Applied $applied preferences, skipped $skipped" +
                if (errors.isEmpty()) "" else " (${errors.size} errors)"

        return Result(errors.isEmpty(), applied, skipped, errors, message)
    }

    private fun resolveKey(rawKey: String): String? = aliasToKey[rawKey]

    private fun resolveValue(prefKey: String, value: Any?): Any? {
        if (value !is String) return value
        return valueAliasToKey[prefKey]?.get(value) ?: value
    }

    @Suppress("DEPRECATION")
    private fun putValue(editor: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is Boolean -> editor.putBoolean(key, value)
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Double -> editor.putFloat(key, value.toFloat())
            is Float -> editor.putFloat(key, value)
            is JSONArray -> {
                // Stored as JSON string; Flutter deep-link path handles List<String> encoding.
                editor.putString(key, value.toString())
            }
            else -> throw IllegalArgumentException("Unsupported type: ${value?.javaClass?.simpleName}")
        }
    }
}
