package com.example.healthpro.auth

import android.content.Context
import android.util.Log

/**
 * Repository for authentication operations.
 *
 * Coordinates between:
 *  - OtpManager (OTP generation + verification)
 *  - AuthPreferences (persistent auth state)
 *
 * In production, this would also handle:
 *  - Email API calls
 *  - Token storage
 *  - Backend communication
 */
class AuthRepository(context: Context) {

    companion object {
        private const val TAG = "AuthRepository"
    }

    val preferences = AuthPreferences(context)
    val otpManager = OtpManager()

    // The email being verified (transient, not persisted until login complete)
    private var pendingEmail: String = ""

    /**
     * Start the email verification process.
     *
     * Returns the generated OTP (for testing/display).
     * In production, this would send the OTP via email and return nothing.
     */
    fun sendOtp(email: String): String {
        pendingEmail = email
        val otp = otpManager.generateAndGetOtp()
        Log.d(TAG, "✉️ OTP sent to $email")
        return otp
    }

    /**
     * Verify the entered OTP.
     */
    fun verifyOtp(otp: String): OtpResult {
        return otpManager.verifyOtp(otp)
    }

    /**
     * Resend OTP for the current pending email.
     *
     * @return the new OTP, or null if cooldown is active
     */
    fun resendOtp(): String? {
        if (!otpManager.canResend()) return null
        val otp = otpManager.generateAndGetOtp()
        Log.d(TAG, "🔄 OTP resent to $pendingEmail")
        return otp
    }

    /**
     * Complete the authentication by saving user data.
     */
    fun completeSetup(preferredName: String) {
        preferences.completeLogin(pendingEmail, preferredName)
        Log.d(TAG, "✅ User setup complete: $preferredName ($pendingEmail)")
    }

    /**
     * Check if the user is already logged in.
     */
    fun isLoggedIn(): Boolean = preferences.isLoggedIn

    /**
     * Get the preferred name for display.
     */
    fun getPreferredName(): String = preferences.preferredName

    /**
     * Log out the current user.
     */
    fun logout() {
        preferences.logout()
        Log.d(TAG, "🚪 User logged out")
    }

    /**
     * Get resend cooldown remaining.
     */
    fun getResendCooldown(): Long = otpManager.getResendCooldownRemaining()
}
