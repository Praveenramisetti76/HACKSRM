package com.example.healthpro.medicine.datahaven

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * DataHaven upload service.
 *
 * Sends prescription images to saathi-datahaven-backend,
 * which stores them on the DataHaven decentralized network.
 */
object DataHavenUploader {

    private const val TAG = "DataHavenUploader"

    // ── Configuration ──
    // For emulator use 10.0.2.2 (maps to host's localhost)
    // For real device on same WiFi, use the machine's LAN IP
    private const val BASE_URL = "http://10.0.2.2:3001"

    /**
     * Upload response from the backend.
     */
    data class UploadResponse(
        val success: Boolean,
        val fileKey: String?,
        val bucketId: String?,
        val txHash: String?,
        val fingerprint: String?,
        val fileSize: Int?,
        val uploadStatus: String?,
        val durationSeconds: Double?,
        val error: String?
    )

    /**
     * Backend health/status response.
     */
    data class StatusResponse(
        val message: String?,
        val phase: Int?,
        val ready: Boolean?,
        val bucketId: String?,
        val error: String?
    )

    /**
     * Retrofit API interface for saathi-datahaven-backend.
     */
    interface DataHavenApi {

        @Multipart
        @POST("/api/upload-prescription")
        suspend fun uploadPrescription(
            @Part file: MultipartBody.Part
        ): UploadResponse
    }

    // OkHttp client with generous timeouts (uploads can take ~50s)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)  // Upload + on-chain confirmation
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(httpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    private val api: DataHavenApi = retrofit.create(DataHavenApi::class.java)

    /**
     * Upload a prescription image to DataHaven.
     *
     * @param context Application context for reading content URIs
     * @param imageUri The content:// or file:// URI of the image
     * @param fileName Display name for the file (e.g. "rx_1234.jpg")
     * @return UploadResponse with fileKey on success, or error message on failure
     */
    suspend fun uploadPrescription(
        context: Context,
        imageUri: Uri,
        fileName: String
    ): UploadResponse = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting upload: $fileName")

            // Copy content:// URI to a temp file (Retrofit needs a real File)
            val tempFile = copyUriToTempFile(context, imageUri, fileName)
                ?: throw Exception("Failed to read image from URI: $imageUri")

            try {
                // Build multipart request
                val mimeType = context.contentResolver.getType(imageUri)
                    ?: "image/jpeg"
                val requestBody = tempFile.asRequestBody(mimeType.toMediaTypeOrNull())
                val part = MultipartBody.Part.createFormData("file", fileName, requestBody)

                // Call backend
                val response = api.uploadPrescription(part)
                Log.d(TAG, "Upload result: success=${response.success}, fileKey=${response.fileKey}")
                response
            } finally {
                // Clean up temp file
                tempFile.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            UploadResponse(
                success = false,
                fileKey = null,
                bucketId = null,
                txHash = null,
                fingerprint = null,
                fileSize = null,
                uploadStatus = null,
                durationSeconds = null,
                error = e.message ?: "Unknown upload error"
            )
        }
    }

    /**
     * Copy a content:// URI into a temp file that Retrofit/OkHttp can read.
     */
    private fun copyUriToTempFile(context: Context, uri: Uri, fileName: String): File? {
        return try {
            val tempDir = File(context.cacheDir, "datahaven_uploads")
            if (!tempDir.exists()) tempDir.mkdirs()

            val tempFile = File(tempDir, "upload_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }
            tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy URI to temp file: ${e.message}")
            null
        }
    }
}
