package com.example.healthpro.genie

import android.content.Context
import android.content.SharedPreferences

/**
 * Feature flags for Genie. Gates automation behind a kill switch.
 *
 * Can be toggled from Settings. When disabled, all automation
 * is turned off and only deep-link mode is available.
 */
object FeatureFlags {

    private const val PREFS_NAME = "genie_feature_flags"
    private const val KEY_AUTOMATION_ENABLED = "automation_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Master kill switch for AccessibilityService automation.
     * When false: all automation disabled, deep-link fallback only.
     */
    fun isAutomationEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_AUTOMATION_ENABLED, true)

    fun setAutomationEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit()
            .putBoolean(KEY_AUTOMATION_ENABLED, enabled)
            .apply()
    }
}
