package com.example.healthpro.photos

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.location.Geocoder
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.util.Locale

/**
 * Photos Manager — Fetches device photos and groups them by location (place names).
 *
 * Flow:
 *   1. Query MediaStore for all images with metadata
 *   2. Read EXIF GPS coordinates where available
 *   3. Reverse geocode lat/lng → city/place name
 *   4. Cluster photos into location-based albums
 *
 * Note: For Google Photos integration, you'd need Google Photos Library API
 * with OAuth. This implementation uses the local MediaStore which captures
 * all photos on the device (including synced Google Photos).
 */
class PhotosManager(private val context: Context) {

    companion object {
        private const val TAG = "PhotosManager"
    }

    /**
     * Represents a single photo with metadata.
     */
    data class PhotoItem(
        val id: Long,
        val uri: Uri,
        val displayName: String,
        val dateAdded: Long,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val placeName: String? = null,
        val width: Int = 0,
        val height: Int = 0
    )

    /**
     * Represents a location-based album.
     */
    data class PhotoAlbum(
        val placeName: String,
        val photos: List<PhotoItem>,
        val coverPhotoUri: Uri?,
        val photoCount: Int
    )

    /**
     * Fetch all photos from device MediaStore.
     */
    suspend fun fetchAllPhotos(): List<PhotoItem> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<PhotoItem>()

        try {
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT
            )

            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val dateColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val widthColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val date = it.getLong(dateColumn)
                    val width = it.getInt(widthColumn)
                    val height = it.getInt(heightColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )

                    // Read EXIF data for GPS
                    val (lat, lng) = readExifLocation(contentUri)

                    photos.add(
                        PhotoItem(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            dateAdded = date,
                            latitude = lat,
                            longitude = lng,
                            width = width,
                            height = height
                        )
                    )
                }
            }

            Log.d(TAG, "Fetched ${photos.size} photos from MediaStore")
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching photos: ${e.message}")
        }

        photos
    }

    /**
     * Read GPS coordinates from EXIF data of a photo.
     */
    private fun readExifLocation(uri: Uri): Pair<Double?, Double?> {
        return try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return Pair(null, null)

            val exif = ExifInterface(inputStream)
            val latLng = exif.latLong

            inputStream.close()

            if (latLng != null) {
                Pair(latLng[0], latLng[1])
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Reverse geocode coordinates to a place name.
     */
    suspend fun reverseGeocode(latitude: Double, longitude: Double): String = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext "Unknown Location"

            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                // Return city name, or locality, or sub-admin area
                address.locality
                    ?: address.subAdminArea
                    ?: address.adminArea
                    ?: address.countryName
                    ?: "Unknown Location"
            } else {
                "Unknown Location"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Reverse geocoding failed: ${e.message}")
            "Unknown Location"
        }
    }

    /**
     * Group photos into location-based albums.
     *
     * Photos with GPS data → grouped by place name (city level)
     * Photos without GPS → grouped into "Uncategorized"
     */
    suspend fun createLocationAlbums(photos: List<PhotoItem>): List<PhotoAlbum> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, MutableList<PhotoItem>>()

        for (photo in photos) {
            val placeName = if (photo.latitude != null && photo.longitude != null) {
                reverseGeocode(photo.latitude, photo.longitude)
            } else {
                "Other Memories"
            }

            val updatedPhoto = photo.copy(placeName = placeName)
            albumMap.getOrPut(placeName) { mutableListOf() }.add(updatedPhoto)
        }

        albumMap.map { (place, photoList) ->
            PhotoAlbum(
                placeName = place,
                photos = photoList.sortedByDescending { it.dateAdded },
                coverPhotoUri = photoList.firstOrNull()?.uri,
                photoCount = photoList.size
            )
        }.sortedByDescending { it.photoCount }
    }

    /**
     * Get recent photos (last N).
     */
    suspend fun getRecentPhotos(limit: Int = 20): List<PhotoItem> {
        return fetchAllPhotos().take(limit)
    }
}
