package com.example.healthpro.medicine.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume

/**
 * On-device OCR using ML Kit for medical report text extraction.
 * Supports PDF (rendered to bitmaps) and images.
 */
class OCRProcessor(private val context: Context) {

    companion object {
        private const val TAG = "OCRProcessor"
    }

    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Extract text from PDF by rendering pages to bitmaps and running ML Kit OCR.
     */
    suspend fun extractTextFromPdf(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext Result.failure(IOException("Could not open PDF"))

            val renderer = PdfRenderer(pfd)
            val fullText = StringBuilder()

            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val bitmap = Bitmap.createBitmap(
                    page.width * 2,
                    page.height * 2,
                    Bitmap.Config.ARGB_8888
                )
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val pageText = extractTextFromBitmap(bitmap)
                if (pageText.isSuccess) {
                    fullText.append(pageText.getOrThrow())
                    if (i < renderer.pageCount - 1) fullText.append("\n\n")
                }
                bitmap.recycle()
                page.close()
            }
            renderer.close()
            pfd.close()

            Result.success(fullText.toString().trim())
        } catch (e: Exception) {
            Log.e(TAG, "PDF OCR failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Extract text from image URI using ML Kit OCR.
     */
    suspend fun extractTextFromImage(uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(IOException("Could not open image"))
            val bitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            bitmap ?: return@withContext Result.failure(IOException("Could not decode image"))
            val result = extractTextFromBitmap(bitmap)
            bitmap.recycle()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Image OCR failed: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * Extract text from bitmap using ML Kit.
     */
    suspend fun extractTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            textRecognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(Result.success(result.text))
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "ML Kit OCR failed: ${e.message}")
                    cont.resume(Result.failure(e))
                }
        }
    }

    fun close() {
        textRecognizer.close()
    }
}
