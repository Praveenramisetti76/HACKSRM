package com.example.healthpro.genie

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Result data class for emergency detection.
 */
data class EmergencyResult(
    val trigger_sos: Boolean,
    val confidence: Int
)

/**
 * Repository responsible for evaluating user speech to detect emergency intents.
 * 
 * 3-Layer Architecture:
 * 1. Local Keyword Match (Instant, Primary)
 * 2. Groq Semantic Classification (AI Fallback if no local match)
 * 3. Default Fallback (Safe failure if network/API fails)
 */
class EmergencyDetectionRepository {

    companion object {
        private const val TAG = "EmergencyRepo"
        
        // Exact API Key should be securely injected via BuildConfig or DI in production.
        // For demonstration per request, assuming a BuildConfig value.
        private const val GROQ_API_KEY = "dummy_key_until_build_config"
        
        private const val GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val GROQ_MODEL = "llama3-8b-8192" // Fast, capable model for classification
        private const val TIMEOUT_MS = 3000L // 3-second strict timeout for emergency check
        
        // Layer 1: Local Keywords
        private val EMERGENCY_KEYWORDS = setOf(
            "i need help",
            "help me",
            "emergency",
            "sos",
            "i fell",
            "something is wrong",
            "i am not okay",
            "call an ambulance",
            "i'm hurt",
            "heart attack",
            "save me",
            "call police"
        )
    }

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(2, TimeUnit.SECONDS)
            .readTimeout(2, TimeUnit.SECONDS)
            .writeTimeout(2, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Layer 1 ONLY: Synchronous, local keyword matching.
     * Safe to call from any thread (no network, no coroutines).
     * Used by the always-listening VoiceSOSListenerService for instant detection.
     */
    fun checkEmergencyLocal(text: String): EmergencyResult {
        if (text.isBlank()) return EmergencyResult(false, 0)

        val normalizedText = text.lowercase().trim()
        val isLocalMatch = EMERGENCY_KEYWORDS.any { keyword ->
            normalizedText.contains(keyword)
        }

        return if (isLocalMatch) {
            Log.w(TAG, "Local Check: 🚨 Emergency keyword match found in '$normalizedText'")
            EmergencyResult(trigger_sos = true, confidence = 95)
        } else {
            EmergencyResult(trigger_sos = false, confidence = 0)
        }
    }

    /**
     * Checks if the transcribed text represents an emergency using a 3-layer approach.
     * Runs on the IO dispatcher to avoid blocking the main thread.
     */
    suspend fun checkEmergency(text: String): EmergencyResult = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext EmergencyResult(false, 0)
        
        val normalizedText = text.lowercase().trim()
        Log.d(TAG, "Evaluating speech for emergency: '$normalizedText'")

        // ==========================================
        // Layer 1: Local Keyword Match (Instant)
        // ==========================================
        val localResult = checkEmergencyLocal(text)
        if (localResult.trigger_sos) {
            return@withContext localResult
        }

        // ==========================================
        // Layer 2: Groq Semantic Classification (API)
        // ==========================================
        if (GROQ_API_KEY.isNotBlank() && GROQ_API_KEY != "null") {
            try {
                // Apply strict timeout to the network call
                val aiResult = withTimeoutOrNull(TIMEOUT_MS) {
                    checkGroqApi(normalizedText)
                }
                
                if (aiResult != null) {
                    Log.d(TAG, "Layer 2 (Groq): AI returned Trigger=${aiResult.trigger_sos}, Confidence=${aiResult.confidence}")
                    return@withContext aiResult
                } else {
                    Log.w(TAG, "Layer 2 (Groq): API call timed out after ${TIMEOUT_MS}ms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Layer 2 (Groq): Failed with exception: ${e.message}")
            }
        } else {
            Log.d(TAG, "Layer 2 (Groq): Skipped, API key not available")
        }

        // ==========================================
        // Layer 3: Network/API Fallback
        // ==========================================
        Log.d(TAG, "Layer 3 (Fallback): No emergency detected, or fallback triggered via error/timeout.")
        return@withContext EmergencyResult(trigger_sos = false, confidence = 50)
    }

    /**
     * Performs a semantic check using the Groq API.
     */
    private fun checkGroqApi(text: String): EmergencyResult? {
        val systemPrompt = """
            You are an emergency trigger detection system for an elderly safety Android launcher.
            Your only job is to determine whether the user's speech contains a direct emergency request.

            Trigger emergency if the speech contains phrases such as:
            - I need help
            - Help me
            - Emergency
            - SOS

            Also trigger if the meaning clearly indicates distress, danger, or urgent need for assistance.

            Be strict but safety-prioritized.
            If unsure but it sounds like danger, return true.

            Respond ONLY in JSON. Do not include markdown blocks or any other text.
            {
              "trigger_sos": true or false,
              "confidence": 0-100
            }
        """.trimIndent()

        // Build Groq OpenAI-compatible request body
        val jsonBody = JSONObject().apply {
            put("model", GROQ_MODEL)
            put("temperature", 0.1) // Low temperature for deterministic classification
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", text)
                })
            })
            // Force JSON output
            put("response_format", JSONObject().apply { 
                put("type", "json_object") 
            })
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url(GROQ_API_URL)
            .post(requestBody)
            .addHeader("Authorization", "Bearer $GROQ_API_KEY")
            .addHeader("Content-Type", "application/json")
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.e(TAG, "Groq API error: ${response.code} - ${response.message}")
                return null
            }

            val responseBody = response.body?.string() ?: return null
            
            // Parse OpenAI-compatible chat completion response
            val rootObj = JSONObject(responseBody)
            val choicesArray = rootObj.optJSONArray("choices") ?: return null
            if (choicesArray.length() == 0) return null
            
            val messageObj = choicesArray.getJSONObject(0).optJSONObject("message") ?: return null
            val contentString = messageObj.optString("content", "")

            // Parse our specific JSON structure
            val contentJson = JSONObject(contentString)
            val triggerSos = contentJson.optBoolean("trigger_sos", false)
            val confidence = contentJson.optInt("confidence", 0)

            return EmergencyResult(triggerSos, confidence)
        }
    }
}
