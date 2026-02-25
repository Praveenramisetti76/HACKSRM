package com.example.healthpro.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Persistent storage for authentication state and user preferences.
 *
 * Stores:
 *  - Login state (is the user authenticated?)
 *  - Email address
 *  - Preferred name (for greetings across the app)
 *  - Created-at timestamp
 *
 * Uses SharedPreferences — lightweight, no Room needed for 4 fields.
 * Sensitive data (OTP) is NEVER stored here.
 */
class AuthPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("sahay_auth", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_PREFERRED_NAME = "preferred_name"
        private const val KEY_CREATED_AT = "created_at"
    }

    // ═══════════════════════════════════════════════════════════════
    // AUTH STATE
    // ═══════════════════════════════════════════════════════════════

    var isLoggedIn: Boolean
        get() = prefs.getBoolean(KEY_IS_LOGGED_IN, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_LOGGED_IN, value).apply()

    // ═══════════════════════════════════════════════════════════════
    // USER DATA
    // ═══════════════════════════════════════════════════════════════

    var email: String
        get() = prefs.getString(KEY_EMAIL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EMAIL, value).apply()

    var preferredName: String
        get() = prefs.getString(KEY_PREFERRED_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PREFERRED_NAME, value).apply()

    var createdAt: Long
        get() = prefs.getLong(KEY_CREATED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CREATED_AT, value).apply()

    // ═══════════════════════════════════════════════════════════════
    // ACTIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Complete the login process after name setup.
     */
    fun completeLogin(email: String, name: String) {
        this.email = email
        this.preferredName = name
        this.createdAt = System.currentTimeMillis()
        this.isLoggedIn = true
    }

    /**
     * Log out and clear all user data.
     */
    fun logout() {
        prefs.edit().clear().apply()
    }
}
