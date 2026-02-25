package com.example.healthpro.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.healthpro.MainActivity
import com.example.healthpro.data.contacts.SavedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * SOS Missed Call Manager
 *
 * Places a 20-second "missed call" to each emergency contact sequentially.
 * The call is placed via ACTION_CALL, then the app immediately returns
 * to the foreground so the dialer is NOT visible. The call continues
 * ringing in the background for 20 seconds, then is auto-disconnected.
 *
 * Flow (per contact):
 *   1. Clean the phone number
 *   2. Place ACTION_CALL (dialer appears for ~1 second)
 *   3. Immediately bring app back to foreground (dialer goes to background)
 *   4. Call rings in background for 20 seconds
 *   5. Auto-disconnect via TelecomManager.endCall()
 *   6. Wait 3 seconds gap
 *   7. Move to next contact
 *
 * Result: User stays in the app, calls happen silently in background.
 */
class SosCallManager(private val context: Context) {

    companion object {
        private const val TAG = "SosCallManager"
        private const val RING_DURATION_MS = 20_000L    // 20 seconds
        private const val GAP_BETWEEN_CALLS_MS = 3_000L // 3 seconds gap
        private const val BRING_BACK_DELAY_MS = 1_000L  // 1 second before bringing app back
    }

    /**
     * Result of the missed call sequence.
     */
    data class CallResult(
        val totalContacts: Int,
        val callsPlaced: Int,
        val callsDisconnected: Int
    )

    // ═══════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════

    /**
     * Place sequential 20-second missed calls to all emergency contacts.
     * Calls happen in the BACKGROUND — the app returns to foreground
     * immediately after each call is placed.
     *
     * @param contacts List of emergency contacts to call
     * @return [CallResult] with stats
     */
    suspend fun placeSequentialMissedCalls(contacts: List<SavedContact>): CallResult {
        if (contacts.isEmpty()) {
            Log.w(TAG, "No contacts to call — skipping missed call sequence")
            return CallResult(0, 0, 0)
        }

        if (!hasCallPermission()) {
            Log.e(TAG, "❌ CALL_PHONE permission not granted — cannot place calls")
            return CallResult(contacts.size, 0, 0)
        }

        Log.d(TAG, "📞 ═══ Starting BACKGROUND missed call sequence for ${contacts.size} contacts ═══")

        var callsPlaced = 0
        var callsDisconnected = 0

        for ((index, contact) in contacts.withIndex()) {
            val rawNumber = contact.phoneNumber.trim()

            if (rawNumber.isBlank()) {
                Log.w(TAG, "⏭️ Skipping '${contact.name}' — blank phone number")
                continue
            }

            val cleanNumber = cleanPhoneNumber(rawNumber)
            if (cleanNumber.isBlank()) {
                Log.w(TAG, "⏭️ Skipping '${contact.name}' — invalid number: '$rawNumber'")
                continue
            }

            Log.d(TAG, "📞 [${index + 1}/${contacts.size}] Background call to ${contact.name} ($cleanNumber)")

            // Step 1: Place the call on Main thread
            val callPlaced = withContext(Dispatchers.Main) {
                placeCall(cleanNumber)
            }

            if (callPlaced) {
                callsPlaced++

                // Step 2: Wait briefly for call to initiate
                delay(BRING_BACK_DELAY_MS)

                // Step 3: Bring our app BACK to foreground (dialer goes to background)
                withContext(Dispatchers.Main) {
                    bringAppToForeground()
                }

                Log.d(TAG, "✅ Call placed to ${contact.name} — app returned to foreground, call ringing in background")

                // Step 4: Wait remaining ring duration (20s total minus the bring-back delay)
                delay(RING_DURATION_MS - BRING_BACK_DELAY_MS)

                // Step 5: End the call
                val disconnected = withContext(Dispatchers.Main) {
                    endCurrentCall()
                }
                if (disconnected) {
                    callsDisconnected++
                    Log.d(TAG, "✅ Call to ${contact.name} auto-disconnected after ${RING_DURATION_MS / 1000}s")
                } else {
                    Log.w(TAG, "⚠️ Could not auto-disconnect call to ${contact.name}")
                }

                // Step 6: Gap before next call
                if (index < contacts.size - 1) {
                    delay(GAP_BETWEEN_CALLS_MS)
                }
            } else {
                Log.e(TAG, "❌ Failed to place call to ${contact.name} ($cleanNumber)")
            }
        }

        Log.d(TAG, "📞 ═══ Missed call sequence COMPLETE: $callsPlaced placed, $callsDisconnected disconnected ═══")
        return CallResult(contacts.size, callsPlaced, callsDisconnected)
    }

    // ═══════════════════════════════════════════════════════════════
    // PHONE NUMBER CLEANING
    // ═══════════════════════════════════════════════════════════════

    /**
     * Clean a phone number for dialing.
     * Removes ALL non-digit characters EXCEPT a leading '+'.
     *
     * "+91 98765 43210"  →  "+919876543210"
     * "(098) 765-4321"   →  "09876543210"
     */
    private fun cleanPhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    // ═══════════════════════════════════════════════════════════════
    // CALL OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Place a phone call using ACTION_CALL.
     * The dialer will briefly appear, then bringAppToForeground() hides it.
     */
    @SuppressLint("MissingPermission")
    private fun placeCall(phoneNumber: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "✅ ACTION_CALL launched for: $phoneNumber")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException calling $phoneNumber: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception calling $phoneNumber: ${e.message}", e)
            false
        }
    }

    /**
     * Bring our app (MainActivity) back to the foreground.
     * This pushes the dialer/phone app to the background while the call
     * continues ringing. The user sees our app, not the dialer.
     */
    private fun bringAppToForeground() {
        try {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(intent)
            Log.d(TAG, "✅ App brought back to foreground — dialer hidden")
        } catch (e: Exception) {
            Log.e(TAG, "⚠️ Could not bring app to foreground: ${e.message}")
        }
    }

    /**
     * End the current active call using TelecomManager.
     * Works on API 28+ (Android P and above).
     */
    @SuppressLint("MissingPermission")
    private fun endCurrentCall(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                if (telecomManager != null) {
                    @Suppress("DEPRECATION")
                    val ended = telecomManager.endCall()
                    Log.d(TAG, "TelecomManager.endCall() returned: $ended")
                    ended
                } else {
                    Log.w(TAG, "⚠️ TelecomManager not available")
                    false
                }
            } else {
                Log.w(TAG, "⚠️ API < 28 — cannot auto-end call")
                false
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ SecurityException ending call: ${e.message}", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception ending call: ${e.message}", e)
            false
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // PERMISSION CHECKS
    // ═══════════════════════════════════════════════════════════════

    private fun hasCallPermission(): Boolean {
        val granted = ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        Log.d(TAG, "CALL_PHONE permission: ${if (granted) "✅ GRANTED" else "❌ DENIED"}")
        return granted
    }

    fun hasEndCallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.ANSWER_PHONE_CALLS
            ) == PackageManager.PERMISSION_GRANTED
        } else true
    }
}
