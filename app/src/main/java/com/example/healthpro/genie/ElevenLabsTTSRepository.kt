package com.example.healthpro.genie

import android.content.Context
import com.example.healthpro.BuildConfig
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Repository responsible for streaming Text-to-Speech (TTS) from ElevenLabs.
 * It immediately plays audio as chunks arrive to minimize latency.
 */
class ElevenLabsTTSRepository(private val context: Context) {

    companion object {
        private const val TAG = "ElevenLabsTTS"
        private const val API_KEY = BuildConfig.ELEVEN_LABS_API_KEY
        
        // Example voice ID, change to the desired voice.
        private const val VOICE_ID = "21m00Tcm4TlvDq8ikWAM" 
        private const val TTS_URL = "https://api.elevenlabs.io/v1/text-to-speech/$VOICE_ID/stream"
        
        // Audio format for ElevenLabs when output_format=pcm_44100
        private const val SAMPLE_RATE = 44100
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS) // Safety net: prevent infinite hang on stalled streams
            .build()
    }

    private var currentCall: Call? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var isPlaying = false

    private var fallbackTts: android.speech.tts.TextToSpeech? = null
    private var isFallbackReady = false

    init {
        fallbackTts = android.speech.tts.TextToSpeech(context) { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                fallbackTts?.language = java.util.Locale.US
                isFallbackReady = true
            }
        }
    }

    /**
     * Plays the TTS audio for the given text. Streams and plays immediately.
     */
    suspend fun play(text: String, voiceId: String = VOICE_ID) = withContext(Dispatchers.IO) {
        // Stop any ongoing playback before starting a new one
        stop()

        if (text.isBlank()) return@withContext
        isPlaying = true

        try {
            // Configure Request Body
            val jsonBody = JSONObject().apply {
                put("text", text)
                put("model_id", "eleven_monolingual_v1")
                put("voice_settings", JSONObject().apply {
                    put("stability", 0.5)
                    put("similarity_boost", 0.75)
                })
            }
            val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

            val url = HttpUrl.Builder()
                .scheme("https")
                .host("api.elevenlabs.io")
                .addPathSegments("v1/text-to-speech/$voiceId/stream")
                .addQueryParameter("output_format", "pcm_44100")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .addHeader("xi-api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "audio/mpeg")
                .build()

            currentCall = okHttpClient.newCall(request)
            val response = currentCall?.execute()

            response?.use { res ->
                if (res.isSuccessful) {
                    res.body?.byteStream()?.let { inputStream ->
                        streamAudioToTrack(inputStream)
                    } ?: run {
                        Log.e(TAG, "Response body is null, falling back to Android TTS")
                        playFallback(text)
                    }
                } else {
                    Log.e(TAG, "ElevenLabs API Error: ${res.code} - ${res.message}. Falling back.")
                    playFallback(text)
                }
            } ?: run {
                Log.e(TAG, "Network call failed, no response. Falling back.")
                playFallback(text)
            }
        } catch (e: Exception) {
            if (isPlaying) { 
                Log.e(TAG, "Exception during TTS playback: ${e.message}. Falling back.", e)
                playFallback(text)
            }
        } finally {
            isPlaying = false
            cleanupAudio()
            currentCall = null
        }
    }

    private fun playFallback(text: String) {
        if (isFallbackReady && isPlaying) {
            Log.d(TAG, "Playing Android TTS fallback: $text")
            fallbackTts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "fallback_tts")
        }
    }

    /**
     * Reads bytes from the InputStream and writes them directly to the AudioTrack.
     */
    private fun streamAudioToTrack(inputStream: InputStream) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()

        val buffer = ByteArray(minBufferSize)
        try {
            while (isPlaying) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead == -1) break
                audioTrack?.write(buffer, 0, bytesRead)
            }
        } catch (e: Exception) {
            if (isPlaying) {
                Log.e(TAG, "Error reading/writing audio stream: ${e.message}", e)
            }
        }
    }

    /**
     * Stops the current TTS playback and cancels any network request.
     */
    fun stop() {
        Log.d(TAG, "Stopping TTS playback")
        isPlaying = false
        currentCall?.cancel()
        currentCall = null
        fallbackTts?.stop()
        cleanupAudio()
    }

    /**
     * Releases AudioTrack resources safely.
     */
    private fun cleanupAudio() {
        try {
            audioTrack?.let { track ->
                if (track.state == AudioTrack.STATE_INITIALIZED) {
                    track.pause()
                    track.flush()
                }
                track.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up AudioTrack: ${e.message}")
        } finally {
            audioTrack = null
        }
    }
    
    fun release() {
        stop()
        fallbackTts?.shutdown()
        fallbackTts = null
    }
}
