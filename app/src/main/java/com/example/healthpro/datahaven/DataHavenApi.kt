package com.example.healthpro.datahaven

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

/**
 * DataHaven Backend API Interface
 *
 * All blockchain operations happen on the backend.
 * Android only sends files and receives file IDs.
 *
 * Base URL: Set in DataHavenClient (e.g., http://10.0.2.2:3001/api/)
 */
interface DataHavenApi {

    // ── Health Check ──────────────────────────────────────────
    @GET("health")
    suspend fun healthCheck(): Response<HealthResponse>

    // ── Initialize Storage Bucket ─────────────────────────────
    @POST("initBucket")
    suspend fun initBucket(
        @Body request: InitBucketRequest = InitBucketRequest()
    ): Response<InitBucketResponse>

    // ── Upload Prescription ───────────────────────────────────
    @Multipart
    @POST("uploadPrescription")
    suspend fun uploadPrescription(
        @Part file: MultipartBody.Part
    ): Response<UploadResponse>

    // ── Download Prescription ─────────────────────────────────
    @GET("getPrescription/{fileId}")
    @Streaming
    suspend fun downloadPrescription(
        @Path("fileId") fileId: String
    ): Response<ResponseBody>

    // ── List All Prescriptions ────────────────────────────────
    @GET("prescriptions")
    suspend fun listPrescriptions(): Response<PrescriptionListResponse>

    // ── Get Prescription Info ─────────────────────────────────
    @GET("prescription/{fileId}/info")
    suspend fun getPrescriptionInfo(
        @Path("fileId") fileId: String
    ): Response<PrescriptionInfoResponse>
}

// ── Request Models ────────────────────────────────────────────

data class InitBucketRequest(
    val bucketName: String = "saathi-medical-docs"
)

// ── Response Models ───────────────────────────────────────────

data class HealthResponse(
    val status: String,
    val mspHealth: String?,
    val bucketInitialized: Boolean,
    val filesStored: Int,
    val timestamp: String
)

data class InitBucketResponse(
    val success: Boolean,
    val bucketId: String?,
    val bucketName: String?,
    val alreadyExists: Boolean?,
    val message: String?,
    val error: String?
)

data class UploadResponse(
    val success: Boolean,
    val fileId: String?,
    val fileKey: String?,
    val bucketId: String?,
    val fileName: String?,
    val size: Long?,
    val message: String?,
    val error: String?
)

data class PrescriptionListResponse(
    val success: Boolean,
    val count: Int,
    val files: List<PrescriptionFile>
)

data class PrescriptionFile(
    val fileId: String,
    val fileName: String,
    val size: Long,
    val mimeType: String,
    val uploadedAt: String
)

data class PrescriptionInfoResponse(
    val success: Boolean,
    val file: PrescriptionDetail?,
    val error: String?
)

data class PrescriptionDetail(
    val fileId: String,
    val fileKey: String,
    val bucketId: String,
    val fileName: String,
    val size: Long,
    val mimeType: String,
    val uploadedAt: String
)
