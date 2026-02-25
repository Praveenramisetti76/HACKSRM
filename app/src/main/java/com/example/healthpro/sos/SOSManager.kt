package com.example.healthpro.sos

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import com.example.healthpro.data.contacts.SavedContact

/**
 * SOS Manager — Handles ALL emergency auto-send operations.
 *
 * ⚠️ AUTO-SEND MODE: All messages are dispatched INSTANTLY with ZERO user interaction.
 *
 * Operations:
 *   1. sendEmergencySMS()    → SmsManager.sendMultipartTextMessage (SILENT, no UI)
 *   2. sendEmergencyWhatsApp() → WhatsApp deep link auto-launch
 *   3. callEmergencyNumber() → ACTION_CALL (auto-dials, no confirmation)
 */
class SOSManager(private val context: Context) {

    companion object {
        private const val TAG = "SOSManager"

        /**
         * Emergency SOS message template.
         */
        fun buildSOSMessage(mapsLink: String): String {
            return """
🚨 EMERGENCY ALERT 🚨
I am in danger. Please help me immediately.
My live location: $mapsLink
            """.trimIndent()
        }

        /**
         * Hospital ambulance request message template.
         */
        fun buildHospitalMessage(mapsLink: String): String {
            return """
🚑 MEDICAL EMERGENCY 🚑
I need immediate medical help. Please send an ambulance.
My live location: $mapsLink
            """.trimIndent()
        }

        /**
         * Inactivity detection SOS message template.
         * Includes the reason for automatic trigger.
         */
        fun buildInactivitySOSMessage(mapsLink: String): String {
            return """
🚨 AUTOMATIC SOS ALERT 🚨
⚠️ Automatic SOS triggered due to inactivity detection.
No activity has been detected for an extended period.
The user may need immediate assistance.
Live location: $mapsLink
            """.trimIndent()
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // SMS — SILENT AUTO-SEND via SmsManager (NO user interaction)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Auto-send emergency SMS to ALL contacts using SmsManager.
     * ❗ Uses SmsManager.sendMultipartTextMessage — sends SILENTLY in background.
     * ❗ NO SMS app opens, NO send button, ZERO friction.
     *
     * @param contacts List of emergency contacts to alert
     * @param mapsLink Google Maps live location link
     * @return Number of contacts successfully sent to
     */
    @SuppressLint("MissingPermission")
    fun sendEmergencySMS(contacts: List<SavedContact>, mapsLink: String): Int {
        val message = buildSOSMessage(mapsLink)
        var successCount = 0

        contacts.forEach { contact ->
            try {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,   // service center (default)
                    parts,
                    null,   // sent intents (null = fire-and-forget)
                    null    // delivery intents
                )
                successCount++
                Log.d(TAG, "✅ SMS sent to: ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ SMS failed for ${contact.name}: ${e.message}")
                // Fallback: try SMS intent (opens SMS app as last resort)
                try {
                    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("smsto:${contact.phoneNumber}")
                        putExtra("sms_body", message)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(smsIntent)
                    successCount++
                    Log.d(TAG, "📱 SMS intent fallback for: ${contact.name}")
                } catch (ex: Exception) {
                    Log.e(TAG, "❌❌ SMS completely failed for ${contact.name}: ${ex.message}")
                }
            }
        }

        Log.d(TAG, "SMS dispatch: $successCount/${contacts.size} successful")
        return successCount
    }

    /**
     * Auto-send a custom SMS message to ALL contacts.
     * Used by SafetyMonitoringService for inactivity-triggered SOS.
     *
     * @param contacts List of emergency contacts to alert
     * @param message Pre-built message (e.g., inactivity SOS with reason)
     * @return Number of contacts successfully sent to
     */
    @SuppressLint("MissingPermission")
    fun sendEmergencySMSCustom(contacts: List<SavedContact>, message: String): Int {
        var successCount = 0

        contacts.forEach { contact ->
            try {
                @Suppress("DEPRECATION")
                val smsManager = SmsManager.getDefault()
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null, parts, null, null
                )
                successCount++
                Log.d(TAG, "✅ Custom SMS sent to: ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Custom SMS failed for ${contact.name}: ${e.message}")
            }
        }

        Log.d(TAG, "Custom SMS dispatch: $successCount/${contacts.size} successful")
        return successCount
    }

    // ═══════════════════════════════════════════════════════════════
    // WHATSAPP — AUTO-LAUNCH via deep link
    // ═══════════════════════════════════════════════════════════════

    /**
     * Auto-send emergency WhatsApp message to ALL contacts.
     *
     * Uses Intent.ACTION_SEND targeted to WhatsApp package with the
     * contact's phone number. The SOS message (with live location link)
     * is pre-filled and sent automatically.
     *
     * If WhatsApp is not installed → shows Toast and returns false.
     * SMS flow continues regardless.
     *
     * @param contacts List of emergency contacts
     * @param mapsLink Google Maps live location link
     * @return true if WhatsApp messages were sent, false if not installed
     */
    fun sendEmergencyWhatsApp(contacts: List<SavedContact>, mapsLink: String): Boolean {
        // Determine which WhatsApp package is available
        val whatsappPackage = getWhatsAppPackage()
        if (whatsappPackage == null) {
            Log.w(TAG, "WhatsApp not installed — skipping WhatsApp alerts")
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            return false
        }

        val message = buildSOSMessage(mapsLink)
        var successCount = 0

        contacts.forEach { contact ->
            try {
                // Clean number: digits only, no +, no spaces, no dashes
                val cleanNumber = contact.phoneNumber
                    .replace(Regex("[^0-9]"), "")

                // Use ACTION_SEND with WhatsApp's jid format for direct send
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    setPackage(whatsappPackage)
                    putExtra(Intent.EXTRA_TEXT, message)
                    // WhatsApp's internal contact identifier
                    putExtra("jid", "$cleanNumber@s.whatsapp.net")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(intent)
                successCount++
                Log.d(TAG, "✅ WhatsApp message sent to: ${contact.name} ($cleanNumber)")
            } catch (e: Exception) {
                Log.e(TAG, "❌ WhatsApp failed for ${contact.name}: ${e.message}")
            }
        }

        // Bring app back to foreground after WhatsApp sends
        try {
            val bringBackIntent = Intent(context, Class.forName("com.example.healthpro.MainActivity")).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(bringBackIntent)
            Log.d(TAG, "✅ App returned to foreground after WhatsApp sends")
        } catch (e: Exception) {
            Log.w(TAG, "⚠️ Could not bring app back to foreground: ${e.message}")
        }

        Log.d(TAG, "WhatsApp dispatch: $successCount/${contacts.size} successful")
        return successCount > 0
    }

    /**
     * Get the installed WhatsApp package name.
     * Checks for regular WhatsApp first, then WhatsApp Business.
     *
     * @return Package name if installed, null otherwise
     */
    private fun getWhatsAppPackage(): String? {
        val packages = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (pkg in packages) {
            try {
                context.packageManager.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Try next
            }
        }
        return null
    }

    // ═══════════════════════════════════════════════════════════════
    // PHONE CALL — AUTO-DIAL emergency number
    // ═══════════════════════════════════════════════════════════════

    /**
     * Auto-call an emergency number (default: 112 India emergency).
     * Uses ACTION_CALL for instant dialing (no confirmation dialog).
     */
    fun callEmergencyNumber(phoneNumber: String = "112") {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "☎️ Auto-calling: $phoneNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Call failed, falling back to DIAL: ${e.message}")
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot call $phoneNumber: ${ex.message}")
                Toast.makeText(context, "Cannot make call", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // UTILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if WhatsApp (or WhatsApp Business) is installed on the device.
     */
    fun isWhatsAppInstalled(): Boolean {
        return getWhatsAppPackage() != null
    }
}
