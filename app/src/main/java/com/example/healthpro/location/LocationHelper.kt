package com.example.healthpro.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Production-grade location fetcher with retry logic.
 *
 * Uses FusedLocationProviderClient for high-accuracy GPS.
 * Implements 3-attempt retry with progressive fallback:
 *   Attempt 1: High accuracy current location
 *   Attempt 2: Balanced accuracy current location
 *   Attempt 3: Last known location fallback
 */
class LocationHelper(context: Context) {

    companion object {
        private const val TAG = "LocationHelper"
        private const val MAX_RETRIES = 3
        private const val RETRY_DELAY_MS = 1500L

        /**
         * Generate a Google Maps link from coordinates.
         */
        fun generateMapsLink(latitude: Double, longitude: Double): String {
            return "https://maps.google.com/?q=$latitude,$longitude"
        }
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Fetch current location with automatic retry (up to 3 attempts).
     *
     * @return Location object or null if all attempts fail
     * @throws SecurityException if location permission not granted
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        for (attempt in 1..MAX_RETRIES) {
            Log.d(TAG, "Location attempt $attempt/$MAX_RETRIES")

            try {
                val location = when (attempt) {
                    1 -> fetchCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY)
                    2 -> {
                        delay(RETRY_DELAY_MS)
                        fetchCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    }
                    else -> {
                        delay(RETRY_DELAY_MS)
                        getLastKnownLocation()
                    }
                }

                if (location != null) {
                    Log.d(TAG, "Location obtained: ${location.latitude}, ${location.longitude}")
                    return location
                }
            } catch (e: Exception) {
                Log.e(TAG, "Location attempt $attempt failed: ${e.message}")
            }
        }

        Log.e(TAG, "All $MAX_RETRIES location attempts failed")
        return null
    }

    /**
     * Get current location with specified priority.
     */
    @SuppressLint("MissingPermission")
    private suspend fun fetchCurrentLocation(priority: Int): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                val cancellationToken = CancellationTokenSource()

                fusedLocationClient.getCurrentLocation(
                    priority,
                    cancellationToken.token
                ).addOnSuccessListener { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }.addOnFailureListener { exception ->
                    if (continuation.isActive) {
                        continuation.resumeWithException(exception)
                    }
                }

                continuation.invokeOnCancellation {
                    cancellationToken.cancel()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchCurrentLocation failed: ${e.message}")
            null
        }
    }

    /**
     * Get last known location as final fallback.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastKnownLocation(): Location? {
        return try {
            suspendCancellableCoroutine { continuation ->
                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (continuation.isActive) {
                            continuation.resume(location)
                        }
                    }
                    .addOnFailureListener {
                        if (continuation.isActive) {
                            continuation.resume(null)
                        }
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "getLastKnownLocation failed: ${e.message}")
            null
        }
    }
}
