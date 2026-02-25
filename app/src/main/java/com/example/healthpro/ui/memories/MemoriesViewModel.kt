package com.example.healthpro.ui.memories

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.healthpro.photos.PhotosManager
import kotlinx.coroutines.launch

/**
 * ViewModel for Memories screen.
 *
 * Manages photo loading, location-based album creation, and slideshow state.
 */
class MemoriesViewModel : ViewModel() {

    companion object {
        private const val TAG = "MemoriesViewModel"
    }

    private var photosManager: PhotosManager? = null

    var isLoading by mutableStateOf(false)
        private set

    var allPhotos by mutableStateOf<List<PhotosManager.PhotoItem>>(emptyList())
        private set

    var albums by mutableStateOf<List<PhotosManager.PhotoAlbum>>(emptyList())
        private set

    var recentPhotos by mutableStateOf<List<PhotosManager.PhotoItem>>(emptyList())
        private set

    var selectedAlbum by mutableStateOf<PhotosManager.PhotoAlbum?>(null)

    var isSlideShowActive by mutableStateOf(false)
    var slideShowIndex by mutableStateOf(0)

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var hasPhotosPermission by mutableStateOf(false)

    fun init(context: Context) {
        if (photosManager == null) {
            photosManager = PhotosManager(context.applicationContext)
            checkPermission(context)
            if (hasPhotosPermission) {
                loadPhotos()
            }
        }
    }

    fun checkPermission(context: Context) {
        hasPhotosPermission = ContextCompat.checkSelfPermission(
            context,
            if (android.os.Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_IMAGES
            else
                Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun loadPhotos() {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                val photos = photosManager?.fetchAllPhotos() ?: emptyList()
                allPhotos = photos
                recentPhotos = photos.take(20)

                // Create location-based albums
                if (photos.isNotEmpty()) {
                    albums = photosManager?.createLocationAlbums(photos) ?: emptyList()
                }

                Log.d(TAG, "Loaded ${photos.size} photos, ${albums.size} albums")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading photos: ${e.message}")
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    fun openAlbum(album: PhotosManager.PhotoAlbum) {
        selectedAlbum = album
    }

    fun closeAlbum() {
        selectedAlbum = null
    }

    fun startSlideShow(photos: List<PhotosManager.PhotoItem>, startIndex: Int = 0) {
        slideShowIndex = startIndex
        isSlideShowActive = true
    }

    fun stopSlideShow() {
        isSlideShowActive = false
    }

    fun nextSlide(maxIndex: Int) {
        if (slideShowIndex < maxIndex - 1) slideShowIndex++
    }

    fun prevSlide() {
        if (slideShowIndex > 0) slideShowIndex--
    }
}
