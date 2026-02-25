package com.example.healthpro.auth

import android.util.Log
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * OTP generation, hashing, validation, and attempt tracking.
 *
 * Security:
 *  - OTP is 6 digits, generated with SecureRandom
 *  - Only the SHA-256 hash is stored in memory (never plain OTP)
 *  - Expires after 5 minutes
 *  - Maximum 5 verification attempts
 *  - No OTP stored on disk
 *
 * In production, replace [generateAndGetOtp] with an actual email API call
 * that sends the OTP via HTTPS and only the hash is kept locally.
 */
class OtpManager {

    companion object {
        private const val TAG = "OtpManager"
        private const val OTP_LENGTH = 6
        private const val OTP_EXPIRY_MS = 5 * 60 * 1000L  // 5 minutes
        private const val MAX_ATTEMPTS = 5
        private const val RESEND_COOLDOWN_MS = 30 * 1000L  // 30 seconds
    }

    // In-memory only — NEVER persisted to disk
    private var otpHash: String? = null
    private var otpGeneratedAt: Long = 0L
    private var attemptCount: Int = 0
    private var lastResendAt: Long = 0L

    // ═══════════════════════════════════════════════════════════════
    // GENERATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Generate a new 6-digit OTP.
     *
     * @return The plain-text OTP (for displaying to user during testing).
     *         In production, this would be sent via email API and never returned.
     */
    fun generateAndGetOtp(): String {
        val random = SecureRandom()
        val otp = (0 until OTP_LENGTH)
            .map { random.nextInt(10) }
            .joinToString("")

        // Store only the hash
        otpHash = sha256(otp)
        otpGeneratedAt = System.currentTimeMillis()
        attemptCount = 0
        lastResendAt = System.currentTimeMillis()

        Log.d(TAG, "✅ OTP generated (hash stored, expires in 5 min)")
        return otp
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Verify user-entered OTP against stored hash.
     *
     * @return [OtpResult] indicating success, wrong OTP, expired, or max attempts
     */
    fun verifyOtp(enteredOtp: String): OtpResult {
        // Check if OTP was ever generated
        if (otpHash == null) {
            return OtpResult.ERROR("No OTP has been sent. Please request one first.")
        }

        // Check max attempts
        if (attemptCount >= MAX_ATTEMPTS) {
            return OtpResult.MAX_ATTEMPTS_REACHED
        }

        // Check expiry
        val elapsed = System.currentTimeMillis() - otpGeneratedAt
        if (elapsed > OTP_EXPIRY_MS) {
            return OtpResult.EXPIRED
        }

        // Increment attempt
        attemptCount++

        // Verify hash
        val enteredHash = sha256(enteredOtp)
        return if (enteredHash == otpHash) {
            Log.d(TAG, "✅ OTP verified successfully")
            otpHash = null  // Invalidate after use
            OtpResult.SUCCESS
        } else {
            val remaining = MAX_ATTEMPTS - attemptCount
            Log.w(TAG, "❌ Wrong OTP (attempt $attemptCount/$MAX_ATTEMPTS)")
            OtpResult.WRONG_OTP(remaining)
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // RESEND
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if user can resend OTP (30-second cooldown).
     *
     * @return milliseconds remaining, or 0 if ready to resend
     */
    fun getResendCooldownRemaining(): Long {
        val elapsed = System.currentTimeMillis() - lastResendAt
        val remaining = RESEND_COOLDOWN_MS - elapsed
        return if (remaining > 0) remaining else 0
    }

    fun canResend(): Boolean = getResendCooldownRemaining() <= 0

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of OTP verification.
 */
sealed class OtpResult {
    object SUCCESS : OtpResult()
    data class WRONG_OTP(val attemptsRemaining: Int) : OtpResult()
    object EXPIRED : OtpResult()
    object MAX_ATTEMPTS_REACHED : OtpResult()
    data class ERROR(val message: String) : OtpResult()
}
