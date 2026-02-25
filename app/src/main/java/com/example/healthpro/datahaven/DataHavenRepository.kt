package com.example.healthpro.datahaven

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * DataHaven Repository
 *
 * Handles all communication with the DataHaven backend.
 * Provides clean suspend functions for the ViewModel layer.
 *
 * Flow:
 *   1. initBucket() → Called once to create/verify storage bucket
 *   2. uploadPrescription() → Upload a file, returns fileId
 *   3. listPrescriptions() → Get all stored files
 *   4. downloadPrescription() → Download by fileId
 */
class DataHavenRepository(private val context: Context) {

    private val api = DataHavenClient.api

    /**
     * Check backend health and MSP connectivity
     */
    suspend fun checkHealth(): Result<HealthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.healthCheck()
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Health check failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Initialize the storage bucket on DataHaven (call once on first run)
     */
    suspend fun initBucket(
        bucketName: String = "saathi-medical-docs"
    ): Result<InitBucketResponse> = withContext(Dispatchers.IO) {
        try {
            val response = api.initBucket(InitBucketRequest(bucketName))
            if (response.isSuccessful && response.body()?.success == true) {
                Result.success(response.body()!!)
            } else {
                val error = response.body()?.error ?: "Bucket init failed: ${response.code()}"
                Result.failure(Exception(error))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Upload a prescription file from a content URI
     *
     * @param fileUri Content URI from file picker or camera
     * @return fileId to store in local Room DB
     */
    suspend fun uploadPrescription(fileUri: Uri): Result<UploadResponse> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Copy URI content to a temp file (content URIs can't be read directly by Retrofit)
                val tempFile = copyUriToTempFile(fileUri)
                    ?: return@withContext Result.failure(Exception("Failed to read file"))

                val fileName = getFileName(fileUri) ?: tempFile.name
                val mimeType = context.contentResolver.getType(fileUri) ?: "application/octet-stream"

                // 2. Create multipart request body
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val multipartBody = MultipartBody.Part.createFormData(
                    "file",
                    fileName,
                    requestBody
                )

                // 3. Upload to backend → DataHaven
                val response = api.uploadPrescription(multipartBody)

                // 4. Cleanup temp file
                tempFile.delete()

                if (response.isSuccessful && response.body()?.success == true) {
                    Result.success(response.body()!!)
                } else {
                    val error = response.body()?.error ?: "Upload failed: ${response.code()}"
                    Result.failure(Exception(error))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * List all stored prescriptions
     */
    suspend fun listPrescriptions(): Result<PrescriptionListResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.listPrescriptions()
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("List failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Download a prescription by fileId
     *
     * @return File path of the downloaded file
     */
    suspend fun downloadPrescription(
        fileId: String,
        fileName: String
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val response = api.downloadPrescription(fileId)
            if (!response.isSuccessful || response.body() == null) {
                return@withContext Result.failure(
                    Exception("Download failed: ${response.code()}")
                )
            }

            // Save to app's files directory
            val downloadsDir = File(context.filesDir, "prescriptions")
            downloadsDir.mkdirs()
            val outputFile = File(downloadsDir, "${fileId}_$fileName")

            response.body()!!.byteStream().use { input ->
                outputFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(outputFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get detailed info about a prescription
     */
    suspend fun getPrescriptionInfo(fileId: String): Result<PrescriptionInfoResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getPrescriptionInfo(fileId)
                if (response.isSuccessful && response.body() != null) {
                    Result.success(response.body()!!)
                } else {
                    Result.failure(Exception("Info fetch failed: ${response.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ── Helper: Copy content URI to temp file ──────────────────

    private fun copyUriToTempFile(uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile("prescription_", ".tmp", context.cacheDir)
            tempFile.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()
            tempFile
        } catch (e: Exception) {
            null
        }
    }

    // ── Helper: Get filename from content URI ──────────────────

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex)
            }
        }
        return name
    }
}
