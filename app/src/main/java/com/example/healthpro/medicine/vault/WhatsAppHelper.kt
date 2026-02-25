package com.example.healthpro.medicine.vault

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.healthpro.data.contacts.ContactsRepository
import com.example.healthpro.data.contacts.SavedContact
import java.io.File

/**
 * WhatsApp helper for Medicine Manager module.
 * Sends prescription images with messages to the top emergency contact.
 *
 * Reuses the same WhatsApp intent pattern as SOSManager but scoped to
 * medicine-specific messages. Does NOT modify SOSManager.
 */
class WhatsAppHelper(private val context: Context) {

    companion object {
        private const val TAG = "WhatsAppHelper"
    }

    /**
     * Send a prescription image with a message via WhatsApp to the top emergency contact.
     *
     * Uses content:// URI with FLAG_GRANT_READ_URI_PERMISSION to avoid FileUriExposedException.
     * If the stored URI is inaccessible, copies image to cache and uses FileProvider.
     *
     * @param imageUri URI of the prescription image to attach (content:// or FileProvider URI)
     * @param message Pre-filled message text
     * @return true if WhatsApp intent was launched, false if WhatsApp not installed or no contact
     */
    fun sendPrescriptionToFamily(imageUri: Uri, message: String): Boolean {
        val contact = getTopEmergencyContact()
        if (contact == null) {
            Toast.makeText(context, "No emergency contact saved", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "No emergency contact found")
            return false
        }

        val whatsappPackage = getWhatsAppPackage()
        if (whatsappPackage == null) {
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            Log.w(TAG, "WhatsApp not installed")
            return false
        }

        return try {
            val cleanNumber = formatPhoneForWhatsApp(contact.phoneNumber)

            // Ensure we have a valid shareable content:// URI
            val shareableUri = getShareableImageUri(imageUri)
            if (shareableUri == null) {
                Log.w(TAG, "Prescription image not accessible: $imageUri, sending text only")
                return sendTextToFamily(message)
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/*"
                setPackage(whatsappPackage)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra(Intent.EXTRA_STREAM, shareableUri)
                putExtra("jid", "$cleanNumber@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Log.d(TAG, "WhatsApp message with image sent to: ${contact.name} ($cleanNumber)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp send with image failed: ${e.message}")
            Toast.makeText(context, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Send a text-only WhatsApp message to the top emergency contact.
     */
    fun sendTextToFamily(message: String): Boolean {
        val contact = getTopEmergencyContact()
        if (contact == null) {
            Toast.makeText(context, "No emergency contact saved", Toast.LENGTH_SHORT).show()
            return false
        }

        val whatsappPackage = getWhatsAppPackage()
        if (whatsappPackage == null) {
            Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
            return false
        }

        return try {
            val cleanNumber = formatPhoneForWhatsApp(contact.phoneNumber)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                setPackage(whatsappPackage)
                putExtra(Intent.EXTRA_TEXT, message)
                putExtra("jid", "$cleanNumber@s.whatsapp.net")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(intent)
            Log.d(TAG, "WhatsApp text sent to: ${contact.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "WhatsApp text send failed: ${e.message}")
            Toast.makeText(context, "Failed to open WhatsApp", Toast.LENGTH_SHORT).show()
            false
        }
    }

    /**
     * Get the first emergency contact (top priority family member).
     */
    private fun getTopEmergencyContact(): SavedContact? {
        val repo = ContactsRepository(context)
        val emergency = repo.getEmergencyContacts()
        if (emergency.isNotEmpty()) return emergency.first()
        val family = repo.getFamilyContacts()
        return family.firstOrNull()
    }

    /**
     * Get the installed WhatsApp package name.
     * Checks for regular WhatsApp first, then WhatsApp Business.
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

    /**
     * Format phone number for WhatsApp jid (requires country code, no + prefix).
     * E.g., "+91 98765 43210" → "919876543210"
     * E.g., "9876543210" → "919876543210" (Indian default)
     */
    private fun formatPhoneForWhatsApp(phone: String): String {
        var digits = phone.replace(Regex("[^0-9]"), "")
        // If 10 digits, assume Indian number, prepend 91
        if (digits.length == 10) {
            digits = "91$digits"
        }
        return digits
    }

    /**
     * Get a shareable content:// URI for the prescription image.
     *
     * - If the URI is already content:// and accessible → use directly
     * - If the URI is file:// → convert via FileProvider
     * - If the image is stale → copy to cache, return fresh FileProvider URI
     *
     * Returns null only if the image truly cannot be read.
     */
    private fun getShareableImageUri(imageUri: Uri): Uri? {
        return try {
            val scheme = imageUri.scheme

            when {
                // Already a content:// URI (from gallery or FileProvider) — verify readable
                scheme == "content" -> {
                    val inputStream = context.contentResolver.openInputStream(imageUri)
                    if (inputStream != null) {
                        inputStream.close()
                        Log.d(TAG, "Image URI is accessible content:// URI")
                        imageUri
                    } else {
                        // Content URI is stale, copy to cache
                        Log.w(TAG, "Content URI stale, copying to cache")
                        copyToCacheAndGetUri(imageUri)
                    }
                }

                // file:// URI — NOT safe to share directly on Android 7+
                // Must convert via FileProvider to avoid FileUriExposedException
                scheme == "file" -> {
                    Log.d(TAG, "Converting file:// URI to content:// via FileProvider")
                    val file = File(imageUri.path ?: return null)
                    if (file.exists()) {
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                    } else {
                        Log.w(TAG, "File does not exist: ${imageUri.path}")
                        null
                    }
                }

                else -> {
                    Log.w(TAG, "Unknown URI scheme: $scheme")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get shareable URI: ${e.message}")
            null
        }
    }

    /**
     * Copy image from a stale content:// URI to internal cache, return a fresh FileProvider URI.
     * Ensures the copied file lives in the "prescriptions/" directory that file_paths.xml declares.
     */
    private fun copyToCacheAndGetUri(sourceUri: Uri): Uri? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri) ?: return null
            val cacheDir = File(context.filesDir, "prescriptions")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cacheFile = File(cacheDir, "share_rx_${System.currentTimeMillis()}.jpg")
            cacheFile.outputStream().use { output -> inputStream.copyTo(output) }
            inputStream.close()

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                cacheFile
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy image to cache: ${e.message}")
            null
        }
    }
}
