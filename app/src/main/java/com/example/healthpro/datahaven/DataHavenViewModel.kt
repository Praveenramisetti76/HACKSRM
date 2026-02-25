package com.example.healthpro.datahaven

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * DataHaven ViewModel
 *
 * Manages UI state for the prescription upload/download feature.
 * Connects to DataHavenRepository for all backend operations.
 *
 * Usage in Compose:
 *   val viewModel: DataHavenViewModel = viewModel()
 *   val uiState by viewModel.uiState.collectAsState()
 */
class DataHavenViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataHavenRepository(application.applicationContext)

    // ── UI State ──────────────────────────────────────────────

    data class UiState(
        val isLoading: Boolean = false,
        val isUploading: Boolean = false,
        val isDownloading: Boolean = false,
        val isBucketReady: Boolean = false,
        val uploadProgress: String = "",
        val prescriptions: List<PrescriptionFile> = emptyList(),
        val lastUploadResult: UploadResponse? = null,
        val downloadedFile: File? = null,
        val error: String? = null,
        val successMessage: String? = null
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    // ── Actions ───────────────────────────────────────────────

    /**
     * Initialize the storage bucket (call once on app start / first use)
     */
    fun initBucket() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                uploadProgress = "Initializing secure storage..."
            )

            repository.initBucket().fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        isBucketReady = true,
                        successMessage = response.message,
                        uploadProgress = ""
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Storage init failed: ${error.message}",
                        uploadProgress = ""
                    )
                }
            )
        }
    }

    /**
     * Upload a prescription from file picker URI
     */
    fun uploadPrescription(fileUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isUploading = true,
                error = null,
                successMessage = null,
                uploadProgress = "Uploading to secure decentralized storage..."
            )

            repository.uploadPrescription(fileUri).fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        lastUploadResult = response,
                        successMessage = "Prescription stored securely on DataHaven!",
                        uploadProgress = ""
                    )
                    // Refresh the list
                    loadPrescriptions()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isUploading = false,
                        error = "Upload failed: ${error.message}",
                        uploadProgress = ""
                    )
                }
            )
        }
    }

    /**
     * Load all stored prescriptions
     */
    fun loadPrescriptions() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            repository.listPrescriptions().fold(
                onSuccess = { response ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        prescriptions = response.files
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load prescriptions: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Download a prescription by ID
     */
    fun downloadPrescription(fileId: String, fileName: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isDownloading = true,
                error = null,
                uploadProgress = "Downloading from decentralized storage..."
            )

            repository.downloadPrescription(fileId, fileName).fold(
                onSuccess = { file ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        downloadedFile = file,
                        successMessage = "Prescription downloaded: ${file.name}",
                        uploadProgress = ""
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isDownloading = false,
                        error = "Download failed: ${error.message}",
                        uploadProgress = ""
                    )
                }
            )
        }
    }

    /**
     * Check backend health
     */
    fun checkHealth() {
        viewModelScope.launch {
            repository.checkHealth().fold(
                onSuccess = { health ->
                    _uiState.value = _uiState.value.copy(
                        isBucketReady = health.bucketInitialized,
                        successMessage = "Backend connected: ${health.status}"
                    )
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Backend unreachable: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Clear error/success messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            error = null,
            successMessage = null
        )
    }
}
