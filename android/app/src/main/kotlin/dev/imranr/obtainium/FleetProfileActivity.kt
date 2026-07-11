package dev.imranr.obtainium

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast

/**
 * Exported, themeless Activity that applies a fleet profile to Obtainium's
 * SharedPreferences and finishes immediately.
 *
 * This is the primary native entry point for fleet orchestration tools
 * (adb, MDM, Termux). It reads a JSON profile from an intent extra, a
 * file URI, or a content URI, applies the settings to Obtainium's default
 * SharedPreferences via [FleetProfileApplier], and exits.
 *
 * ## Usage
 *
 *   adb shell am start \
 *     -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
 *     -e profile_path /sdcard/Download/obtainium-fleet.json \
 *     dev.imranr.obtainium/.FleetProfileActivity
 *
 * Or with a file URI:
 *
 *   adb shell am start \
 *     -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
 *     -d file:///sdcard/Download/obtainium-fleet.json \
 *     dev.imranr.obtainium/.FleetProfileActivity
 *
 * Or with a content URI:
 *
 *   adb shell am start \
 *     -a dev.imranr.obtainium.action.APPLY_FLEET_PROFILE \
 *     -d content://media/external/downloads/1234 \
 *     dev.imranr.obtainium/.FleetProfileActivity
 *
 * Options:
 *   -e silent true          — suppress result Toast
 *   -e deep_link "...""      — also launch a Flutter deep-link after applying
 */
class FleetProfileActivity : Activity() {

    private companion object {
        const val ACTION_APPLY_FLEET_PROFILE =
            "dev.imranr.obtainium.action.APPLY_FLEET_PROFILE"
        const val EXTRA_PROFILE_PATH = "profile_path"
        const val EXTRA_SILENT = "silent"
        const val EXTRA_DEEP_LINK = "deep_link"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val silent = intent.getBooleanExtra(EXTRA_SILENT, false)
        val result = runCatching {
            when (intent.action) {
                ACTION_APPLY_FLEET_PROFILE, Intent.ACTION_VIEW -> {
                    val profileUri = readProfileUri(intent)
                    if (profileUri != null) {
                        when (profileUri.scheme) {
                            "file" -> FleetProfileApplier.applyFromPath(
                                this, profileUri.path!!)
                            "content" -> FleetProfileApplier.applyFromUri(
                                this, profileUri)
                            else -> FleetProfileApplier.applyFromPath(
                                this, profileUri.toString())
                        }
                    } else {
                        FleetProfileApplier.Result(false, 0, 0,
                            listOf("No profile source"),
                            "Provide profile_path extra or file/content URI")
                    }
                }
                else -> FleetProfileApplier.Result(false, 0, 0,
                    listOf("Unknown action: ${intent.action}"),
                    "Use $ACTION_APPLY_FLEET_PROFILE or ACTION_VIEW")
            }
        }.getOrElse { e ->
            FleetProfileApplier.Result(false, 0, 0,
                listOf(e.message ?: "Unknown error"),
                "Fleet profile failed: ${e.message}")
        }

        finishWith(result, silent, intent)
    }

    private fun finishWith(
        result: FleetProfileApplier.Result,
        silent: Boolean,
        intent: Intent,
    ) {
        if (!silent) {
            Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show()
        }

        // Optionally launch a Flutter deep-link for complex operations
        // that the native applier cannot handle (imports, updates, lists).
        val deepLink = intent.getStringExtra(EXTRA_DEEP_LINK)
        if (deepLink != null) {
            try {
                startActivity(Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(deepLink)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) { /* best-effort */ }
        }

        setResult(if (result.success) RESULT_OK else RESULT_CANCELED)
        finish()
    }

    /**
     * Resolve the profile JSON source: check intent extra first, then data URI.
     */
    private fun readProfileUri(intent: Intent): Uri? {
        // 1) profile_path string extra — treated as file path
        intent.getStringExtra(EXTRA_PROFILE_PATH)?.let { path ->
            return Uri.parse("file://$path")
        }
        // 2) data URI (file:// or content://)
        return intent.data
    }
}
