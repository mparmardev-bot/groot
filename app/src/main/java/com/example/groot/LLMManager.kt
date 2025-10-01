package com.example.groot

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class LLMManager {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val baseUrl = "http://192.168.88.39:8000"  // Update with your IP
    private val memoryManager = MemoryManager()

    companion object {
        private const val TAG = "LLMManager"
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            memoryManager.addConversation("User: $prompt")

            val jsonBody = JSONObject().apply {
                put("text", prompt)
                put("context", "mobile_assistant")
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/query")
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()

            Log.d(TAG, "Sending request: $prompt")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "HTTP error: ${response.code}")
                    throw IOException("Server error: ${response.code}")
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Raw response: $responseBody")

                if (responseBody.isEmpty()) {
                    throw IOException("Empty response")
                }

                val jsonResponse = JSONObject(responseBody)
                val reply = jsonResponse.optString("reply", "")
                val action = jsonResponse.optString("action", "none")
                val target = jsonResponse.optString("target", "")

                if (reply.isEmpty()) {
                    throw IOException("No reply in response")
                }

                memoryManager.addConversation("Assistant: $reply")
                Log.d(TAG, "Generated response: $reply (action=$action, target=$target)")

                // Return full JSON string (not just reply)
                return@withContext JSONObject()
                    .put("reply", reply)
                    .put("action", action)
                    .put("target", target)
                    .toString()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            // Return bilingual error message
            val isHindi = prompt.matches(Regex(".*[\\u0900-\\u097F].*"))
            return@withContext if (isHindi) {
                "मुझे खेद है sir ,,,, मुझे अपनी AI.... सर्वर.... और,,, डेटाबेस... से जुड़ने में समस्या हो रही है।... क्या आप एक बार चेक कर सकते हैं कि " +
                 "मैं सर्वर से कनेक्ट हूँ या नहीं? क्योंकि सर्वर से कनेक्ट हुए बिना मैं आपकी मदद नहीं कर पाऊँगा, सर । i am really very sorry sir"
            } else {
                "I'm sorry, I'm having trouble connecting to my AI service."
            }
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            Log.d(TAG, "Testing connection to: $baseUrl/health")

            client.newCall(request).execute().use { response ->
                val isSuccessful = response.isSuccessful
                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "Connection test: ${response.code} - $responseBody")

                if (isSuccessful && responseBody.contains("healthy")) {
                    Log.d(TAG, "Connection successful")
                    return@withContext true
                } else {
                    Log.e(TAG, "Connection failed")
                    return@withContext false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            return@withContext false
        }
    }

    fun clearMemory() {
        memoryManager.clearMemory()
        Log.d(TAG, "Memory cleared")
    }

    suspend fun getConnectionInfo(): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val json = JSONObject(responseBody)
                    val status = json.optString("status", "unknown")
                    val ollama = json.optString("ollama", "unknown")
                    "Server: $status, Ollama: $ollama"
                } else {
                    "Connection failed: ${response.code}"
                }
            }
        } catch (e: Exception) {
            "Connection error: ${e.message}"
        }
    }
}