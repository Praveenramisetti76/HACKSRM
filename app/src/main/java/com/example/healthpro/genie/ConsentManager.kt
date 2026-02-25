package com.example.healthpro.genie

import android.content.Context
import android.content.SharedPreferences
import android.os.Build

/**
 * Manages user consent for AccessibilityService automation.
 *
 * - Persistent consent record (timestamp + app version)
 * - Per-session runtime confirmation tracking
 * - Resets consent on major version upgrades
 */
object ConsentManager {

    private const val PREFS_NAME = "genie_consent"
    private const val KEY_CONSENT_GRANTED = "consent_granted"
    private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
    private const val KEY_CONSENT_APP_VERSION = "consent_app_version"
    private const val KEY_SESSION_CONFIRMED = "session_confirmed"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Legal Disclaimer Text ────────────────────────────────

    const val LEGAL_DISCLAIMER = """Genie uses Android Accessibility to automate actions in other apps on your behalf — including searching for items, adding them to your cart, and navigating to the checkout page.

IMPORTANT:
• Genie will NEVER complete a payment without your explicit tap.
• You are responsible for reviewing and confirming all orders.
• Genie does not store payment information, addresses, or personal data.
• You can disable Genie automation at any time from Settings.

By enabling this feature, you consent to Genie interacting with other apps on your behalf."""

    // ── Consent Status ───────────────────────────────────────

    fun hasConsent(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_CONSENT_GRANTED, false)) return false

        // Check if consent was given for current major version
        val consentVersion = p.getString(KEY_CONSENT_APP_VERSION, "") ?: ""
        val currentVersion = getAppVersion(context)
        if (consentVersion.isNotBlank() && !isSameMajorVersion(consentVersion, currentVersion)) {
            // Major version changed — reset consent
            revokeConsent(context)
            return false
        }
        return true
    }

    fun grantConsent(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_GRANTED, true)
            .putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            .putString(KEY_CONSENT_APP_VERSION, getAppVersion(context))
            .apply()
    }

    fun revokeConsent(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_CONSENT_GRANTED, false)
            .remove(KEY_CONSENT_TIMESTAMP)
            .remove(KEY_CONSENT_APP_VERSION)
            .putBoolean(KEY_SESSION_CONFIRMED, false)
            .apply()
    }

    fun getConsentTimestamp(context: Context): Long =
        prefs(context).getLong(KEY_CONSENT_TIMESTAMP, 0L)

    // ── Per-Session Runtime Guard ────────────────────────────

    /**
     * Must be confirmed once per app session before first automation.
     * Prevents accidental triggers.
     */
    fun isSessionConfirmed(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SESSION_CONFIRMED, false)

    fun confirmSession(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SESSION_CONFIRMED, true)
            .apply()
    }

    fun resetSessionConfirmation(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_SESSION_CONFIRMED, false)
            .apply()
    }

    // ── Helpers ──────────────────────────────────────────────

    private fun getAppVersion(context: Context): String {
        return try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isSameMajorVersion(v1: String, v2: String): Boolean {
        val major1 = v1.split(".").firstOrNull()?.toIntOrNull() ?: 0
        val major2 = v2.split(".").firstOrNull()?.toIntOrNull() ?: 0
        return major1 == major2
    }
}
