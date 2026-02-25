package com.example.healthpro.auth

import android.app.Application
import android.util.Log
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ViewModel for the 3-step authentication flow:
 *  1. Email Input  →  2. OTP Verification  →  3. Name Setup
 *
 * Manages all state (Loading / Success / Error) for the UI.
 * No business logic in Activities or Composables.
 */
class AuthViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "AuthViewModel"
    }

    private val repository = AuthRepository(application.applicationContext)

    // ═══════════════════════════════════════════════════════════════
    // AUTH STATE
    // ═══════════════════════════════════════════════════════════════

    val isLoggedIn: Boolean get() = repository.isLoggedIn()

    // Current screen state
    private val _authState = MutableStateFlow<AuthScreenState>(AuthScreenState.Idle)
    val authState: StateFlow<AuthScreenState> = _authState.asStateFlow()

    // Email → OTP screen communication
    private var pendingEmail: String = ""
    private var currentOtp: String = ""  // For testing display only

    // ═══════════════════════════════════════════════════════════════
    // STEP 1: EMAIL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate email and send OTP.
     */
    fun submitEmail(email: String) {
        val trimmed = email.trim()

        // Validation
        if (trimmed.isEmpty()) {
            _authState.value = AuthScreenState.Error("Please enter your email address")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()) {
            _authState.value = AuthScreenState.Error("Please enter a valid email address")
            return
        }

        _authState.value = AuthScreenState.Loading

        pendingEmail = trimmed
        currentOtp = repository.sendOtp(trimmed)

        Log.d(TAG, "📧 OTP sent to $trimmed")
        _authState.value = AuthScreenState.OtpSent(currentOtp)
    }

    // ═══════════════════════════════════════════════════════════════
    // STEP 2: OTP VERIFICATION
    // ═══════════════════════════════════════════════════════════════

    /**
     * Verify the entered OTP.
     */
    fun verifyOtp(enteredOtp: String) {
        val trimmed = enteredOtp.trim()

        if (trimmed.length != 6) {
            _authState.value = AuthScreenState.Error("Please enter the 6-digit OTP")
            return
        }

        _authState.value = AuthScreenState.Loading

        when (val result = repository.verifyOtp(trimmed)) {
            is OtpResult.SUCCESS -> {
                Log.d(TAG, "✅ OTP verified")
                _authState.value = AuthScreenState.OtpVerified
            }
            is OtpResult.WRONG_OTP -> {
                _authState.value = AuthScreenState.Error(
                    "Wrong OTP. ${result.attemptsRemaining} attempts remaining."
                )
            }
            is OtpResult.EXPIRED -> {
                _authState.value = AuthScreenState.Error(
                    "OTP has expired. Please request a new one."
                )
            }
            is OtpResult.MAX_ATTEMPTS_REACHED -> {
                _authState.value = AuthScreenState.Error(
                    "Too many attempts. Please request a new OTP."
                )
            }
            is OtpResult.ERROR -> {
                _authState.value = AuthScreenState.Error(result.message)
            }
        }
    }

    /**
     * Resend OTP.
     */
    fun resendOtp(): Boolean {
        val newOtp = repository.resendOtp()
        if (newOtp != null) {
            currentOtp = newOtp
            _authState.value = AuthScreenState.OtpSent(currentOtp)
            Log.d(TAG, "🔄 OTP resent")
            return true
        }
        return false
    }

    /**
     * Get remaining cooldown for resend button (in ms).
     */
    fun getResendCooldown(): Long = repository.getResendCooldown()

    // ═══════════════════════════════════════════════════════════════
    // STEP 3: NAME SETUP
    // ═══════════════════════════════════════════════════════════════

    /**
     * Validate and save preferred name, completing the auth flow.
     */
    fun submitName(name: String) {
        val trimmed = name.trim()

        // Validation
        if (trimmed.isEmpty()) {
            _authState.value = AuthScreenState.Error("Please enter your name")
            return
        }
        if (trimmed.length < 2) {
            _authState.value = AuthScreenState.Error("Name must be at least 2 characters")
            return
        }
        if (trimmed.length > 30) {
            _authState.value = AuthScreenState.Error("Name must be 30 characters or less")
            return
        }
        if (!trimmed.matches(Regex("^[a-zA-Z ]+$"))) {
            _authState.value = AuthScreenState.Error("Name can only contain letters and spaces")
            return
        }

        _authState.value = AuthScreenState.Loading

        repository.completeSetup(trimmed)
        _authState.value = AuthScreenState.SetupComplete

        Log.d(TAG, "🎉 Auth complete: $trimmed")
    }

    // ═══════════════════════════════════════════════════════════════
    // LOGOUT
    // ═══════════════════════════════════════════════════════════════

    fun logout() {
        repository.logout()
        _authState.value = AuthScreenState.Idle
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _authState.value = AuthScreenState.Idle
    }
}

/**
 * Sealed class representing all possible auth screen states.
 */
sealed class AuthScreenState {
    object Idle : AuthScreenState()
    object Loading : AuthScreenState()
    data class Error(val message: String) : AuthScreenState()
    data class OtpSent(val otp: String) : AuthScreenState()  // otp exposed for testing display
    object OtpVerified : AuthScreenState()
    object SetupComplete : AuthScreenState()
}
