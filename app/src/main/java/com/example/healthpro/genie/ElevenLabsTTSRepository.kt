package com.example.healthpro.genie

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.example.healthpro.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Repository responsible for Text-to-Speech (TTS) from ElevenLabs.
 * Simplified for 100% stability: Requests MP3, saves to a temporary file, and plays via MediaPlayer.
 * Zero crash risk, strict timeouts, and safe cancellation.
 */
class ElevenLabsTTSRepository(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsTTS"
        private const val API_KEY = BuildConfig.ELEVEN_LABS_API_KEY

        // Strict timeout limits for hackathon stability
        private const val NETWORK_TIMEOUT_SECONDS = 5L
        private const val OVERALL_COROUTINE_TIMEOUT_MS = 15_000L // Max 15s to generate and play

        // Default voice ID (used if none provided)
        private const val VOICE_ID = "21m00Tcm4TlvDq8ikWAM"
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    private var currentCall: Call? = null
    private var mediaPlayer: MediaPlayer? = null
    @Volatile private var isPlaying = false

    private var fallbackTts: TextToSpeech? = null
    private var isFallbackReady = false

    init {
        fallbackTts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                fallbackTts?.language = Locale.US
                isFallbackReady = true
            }
        }
    }

    /**
     * Plays the TTS audio for the given text using an MP3 temp file and MediaPlayer.
     * Suspends until playback is completely finished or cancelled.
     *
     * @param stability  Higher = calmer, steadier pacing.
     * @param similarityBoost  Lower = gentler, warmer timbre.
     */
    suspend fun play(
        text: String,
        voiceId: String = VOICE_ID,
        stability: Double = 0.85,
        similarityBoost: Double = 0.4
    ) = withContext(Dispatchers.IO) {
        // Enforce strict sequential playback
        stop()

        if (text.isBlank()) return@withContext
        isPlaying = true

        // Safety Net: The entire operation (network + playback) MUST complete within this time
        val success = withTimeoutOrNull(OVERALL_COROUTINE_TIMEOUT_MS) {
            try {
                // 1. Download the MP3
                val tempFile = downloadMp3FromElevenLabs(text, voiceId, stability, similarityBoost)
                
                if (tempFile != null && tempFile.exists()) {
                    // 2. Play the MP3 using MediaPlayer (suspends until done)
                    playWithMediaPlayer(tempFile)
                } else {
                    Log.e(TAG, "Failed to download MP3. Falling back.")
                    playFallback(text)
                }
                true // indicates withTimeoutOrNull completed successfully
            } catch (e: Exception) {
                if (isPlaying) {
                    Log.e(TAG, "Exception during TTS playback: ${e.message}", e)
                    playFallback(text)
                }
                true
            }
        }

        if (success == null) {
            // withTimeoutOrNull timed out
            Log.e(TAG, "Overall TTS operation TIMED OUT (> ${OVERALL_COROUTINE_TIMEOUT_MS}ms). Falling back.")
            playFallback(text)
        }

        // Cleanup
        isPlaying = false
        cleanupMediaPlayer()
        currentCall = null
    }

    /**
     * Calls ElevenLabs API to generate MP3 and saves it to a temporary file.
     * Returns the File on success, or null on failure.
     */
    private suspend fun downloadMp3FromElevenLabs(
        text: String,
        voiceId: String,
        stability: Double,
        similarityBoost: Double
    ): File? = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("text", text)
            put("model_id", "eleven_multilingual_v2")
            put("voice_settings", JSONObject().apply {
                put("stability", stability)
                put("similarity_boost", similarityBoost)
                put("style", 0.15)
                put("use_speaker_boost", false)
            })
        }
        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val url = HttpUrl.Builder()
            .scheme("https")
            .host("api.elevenlabs.io")
            .addPathSegments("v1/text-to-speech/$voiceId")
            // Crucial: Request standard MP3 format instead of PCM
            .addQueryParameter("output_format", "mp3_44100_128")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .addHeader("xi-api-key", API_KEY)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "audio/mpeg")
            .build()

        currentCall = okHttpClient.newCall(request)
        
        try {
            val response = currentCall?.execute()
            if (response != null && response.isSuccessful) {
                val inputStream = response.body?.byteStream()
                if (inputStream != null) {
                    val tempFile = File(context.cacheDir, "sahay_tts_temp.mp3")
                    val outputStream = FileOutputStream(tempFile)
                    inputStream.use { input ->
                        outputStream.use { output ->
                            input.copyTo(output)
                        }
                    }
                    return@withContext tempFile
                }
            } else {
                Log.e(TAG, "API Error: ${response?.code} - ${response?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network call failed: ${e.message}")
        }
        return@withContext null
    }

    /**
     * Plays the audio file using MediaPlayer.
     * Suspends cleanly until playback finishes. Supports coroutine cancellation.
     */
    private suspend fun playWithMediaPlayer(file: File) = suspendCancellableCoroutine<Unit> { continuation ->
        try {
            cleanupMediaPlayer()
            
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener {
                    // Resume coroutine when audio naturally finishes
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                    if (continuation.isActive) {
                        continuation.resume(Unit) // Resume anyway to unblock, let fallback handle if needed
                    }
                    true
                }
                prepare()
                start()
            }

            // Immediately handle cancellation (e.g., SOS triggered, new intent)
            continuation.invokeOnCancellation {
                Log.d(TAG, "TTS coroutine cancelled, stopping MediaPlayer")
                cleanupMediaPlayer()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up MediaPlayer: ${e.message}")
            if (continuation.isActive) {
                continuation.resume(Unit)
            }
        }
    }

    private fun playFallback(text: String) {
        if (isFallbackReady && isPlaying) {
            Log.w(TAG, "Playing Android TTS fallback: $text")
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "fallback_tts")
        }
    }

    /**
     * Stops current operations immediately: cancels network, stops media, stops fallback.
     */
    fun stop() {
        Log.d(TAG, "Stopping all TTS operations")
        isPlaying = false
        currentCall?.cancel()
        currentCall = null
        fallbackTts?.stop()
        cleanupMediaPlayer()
    }

    private fun cleanupMediaPlayer() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up MediaPlayer: ${e.message}")
        } finally {
            mediaPlayer = null
        }
    }
    
    fun release() {
        stop()
        fallbackTts?.shutdown()
        fallbackTts = null
    }
}
