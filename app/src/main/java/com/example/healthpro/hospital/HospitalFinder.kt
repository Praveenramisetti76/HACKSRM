package com.example.healthpro.hospital

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Finds the nearest hospital using Google Places Nearby Search API.
 *
 * Flow:
 *   1. Call Google Places API with location + type=hospital
 *   2. Parse response → extract name, address, phone (via place details)
 *   3. Auto-send ambulance request SMS + WhatsApp
 *   4. Auto-call the hospital
 *
 * Fallback: If API fails → open Google Maps with "hospital" search
 */
class HospitalFinder(private val context: Context) {

    companion object {
        private const val TAG = "HospitalFinder"
        // ⚠️ Replace with your actual Google Maps API key
        private const val PLACES_API_KEY = "YOUR_GOOGLE_PLACES_API_KEY"
        private const val NEARBY_SEARCH_URL =
            "https://maps.googleapis.com/maps/api/place/nearbysearch/json"
        private const val PLACE_DETAILS_URL =
            "https://maps.googleapis.com/maps/api/place/details/json"
        private const val SEARCH_RADIUS = 5000 // 5 km radius
    }

    /**
     * Find nearest hospital using Google Places Nearby Search API.
     * Returns HospitalInfo or null if nothing found.
     */
    suspend fun findNearestHospital(latitude: Double, longitude: Double): HospitalInfo? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$NEARBY_SEARCH_URL?" +
                        "location=$latitude,$longitude" +
                        "&radius=$SEARCH_RADIUS" +
                        "&type=hospital" +
                        "&rankby=prominence" +
                        "&key=$PLACES_API_KEY"

                Log.d(TAG, "Searching hospitals near: $latitude, $longitude")

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    Log.e(TAG, "Places API returned code: $responseCode")
                    return@withContext null
                }

                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = JSONObject(response)
                val results = json.optJSONArray("results")

                if (results == null || results.length() == 0) {
                    Log.w(TAG, "No hospitals found in API response")
                    return@withContext null
                }

                // Get the first (nearest) result
                val firstResult = results.getJSONObject(0)
                val name = firstResult.optString("name", "Hospital")
                val address = firstResult.optString("vicinity", "")
                val location = firstResult.optJSONObject("geometry")
                    ?.optJSONObject("location")
                val lat = location?.optDouble("lat") ?: latitude
                val lng = location?.optDouble("lng") ?: longitude
                val placeId = firstResult.optString("place_id", "")

                // Try to get phone number from Place Details API
                var phoneNumber = ""
                if (placeId.isNotBlank()) {
                    phoneNumber = getPlacePhoneNumber(placeId)
                }

                val hospital = HospitalInfo(
                    name = name,
                    address = address,
                    phoneNumber = phoneNumber,
                    latitude = lat,
                    longitude = lng,
                    distanceMeters = calculateDistance(latitude, longitude, lat, lng)
                )

                Log.d(TAG, "Found hospital: ${hospital.name}, phone: ${hospital.phoneNumber}")
                hospital

            } catch (e: Exception) {
                Log.e(TAG, "Hospital search API failed: ${e.message}")
                null
            }
        }
    }

    /**
     * Get phone number for a specific place using Place Details API.
     */
    private fun getPlacePhoneNumber(placeId: String): String {
        return try {
            val url = "$PLACE_DETAILS_URL?" +
                    "place_id=$placeId" +
                    "&fields=formatted_phone_number,international_phone_number" +
                    "&key=$PLACES_API_KEY"

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            val responseCode = connection.responseCode
            if (responseCode != 200) return ""

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(response)
            val result = json.optJSONObject("result")

            result?.optString("international_phone_number", "")
                ?.ifBlank { result.optString("formatted_phone_number", "") }
                ?: ""

        } catch (e: Exception) {
            Log.e(TAG, "Place details API failed: ${e.message}")
            ""
        }
    }

    /**
     * Fallback: Open Google Maps with hospital search when API fails.
     */
    fun openMapsHospitalSearch(latitude: Double, longitude: Double) {
        try {
            val uri = Uri.parse("geo:$latitude,$longitude?q=hospital")
            val mapIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            if (mapIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(mapIntent)
            } else {
                // Fallback: browser
                val webUri = Uri.parse(
                    "https://www.google.com/maps/search/hospital/@$latitude,$longitude,15z"
                )
                val webIntent = Intent(Intent.ACTION_VIEW, webUri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(webIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open maps: ${e.message}")
            Toast.makeText(context, "Could not open Maps", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Auto-send ambulance request SMS to hospital.
     * Uses SmsManager for SILENT auto-send (no user interaction).
     */
    @SuppressLint("MissingPermission")
    fun sendHospitalSMS(hospitalPhone: String, mapsLink: String) {
        if (hospitalPhone.isBlank()) return

        val message = """
🚑 MEDICAL EMERGENCY 🚑
I need immediate medical help. Please send an ambulance.
My live location: $mapsLink
        """.trimIndent()

        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                hospitalPhone, null, parts, null, null
            )
            Log.d(TAG, "Hospital SMS sent to: $hospitalPhone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send hospital SMS: ${e.message}")
            // Fallback to SMS intent
            try {
                val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:$hospitalPhone")
                    putExtra("sms_body", message)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(smsIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "SMS intent fallback also failed: ${ex.message}")
            }
        }
    }

    /**
     * Auto-send ambulance request via WhatsApp to hospital.
     */
    fun sendHospitalWhatsApp(hospitalPhone: String, mapsLink: String) {
        if (hospitalPhone.isBlank()) return
        if (!isWhatsAppInstalled()) return

        val message = """
🚑 MEDICAL EMERGENCY 🚑
I need immediate medical help. Please send an ambulance.
My live location: $mapsLink
        """.trimIndent()

        try {
            val cleanNumber = hospitalPhone.replace(Regex("[^\\d+]"), "").removePrefix("+")
            val encodedMessage = Uri.encode(message)
            val url = "https://wa.me/$cleanNumber?text=$encodedMessage"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Hospital WhatsApp sent to: $cleanNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send hospital WhatsApp: ${e.message}")
        }
    }

    /**
     * Auto-call the hospital phone number.
     */
    fun callHospital(phoneNumber: String) {
        if (phoneNumber.isBlank()) {
            // Fallback: call India emergency 112
            callNumber("112")
            return
        }
        callNumber(phoneNumber)
    }

    /**
     * Place a phone call to a number.
     */
    private fun callNumber(number: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "Calling: $number")
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e(TAG, "Cannot call $number: ${ex.message}")
                Toast.makeText(context, "Cannot make call", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Check if WhatsApp is installed on device.
     */
    private fun isWhatsAppInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo("com.whatsapp", PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Calculate distance between two lat/lng points in meters (Haversine formula).
     */
    private fun calculateDistance(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double
    ): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLng / 2) * Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }
}
