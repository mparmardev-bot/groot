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

class LLMManager {
    val client = OkHttpClient()
    private val baseUrl = "http://192.168.88.39:8000" // For Android emulator
    private val memoryManager = MemoryManager()

    companion object {
        private const val TAG = "LLMManager"
    }

    data class LLMRequest(
        val model: String,
        val prompt: String,
        val stream: Boolean = false
    )

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.IO) {
        try {
            memoryManager.addConversation("User: $prompt")
            val context = memoryManager.getRecentContext(3)
            val contextPrompt = if (context.isNotEmpty()) {
                context.joinToString("\n") + "\nUser: $prompt"
            } else {
                prompt
            }

            val jsonBody = JSONObject().apply {
                put("text", contextPrompt)  // Matches Command.text in FastAPI
                put("context", "mobile_assistant")
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("${baseUrl}/query")  // Changed to FastAPI endpoint
                .post(requestBody)
                .build()

            Log.d(TAG, "Sending request to FastAPI: $contextPrompt")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code ${response.code}")
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Raw response: $responseBody")

                val jsonResponse = JSONObject(responseBody)
                val reply = jsonResponse.getString("reply")  // Match SmithResponse
                memoryManager.addConversation("Assistant: $reply")
                return@withContext reply
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            return@withContext "I'm sorry, I'm having trouble connecting to my AI service. Please make sure Ollama is running and try again."
        }
    }

    fun clearMemory() {
        memoryManager.clearMemory()
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/health")
                .get()
                .addHeader("Accept", "application/json")
                .build()

            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Connection test: ${response.code} - ${response.body?.string()}")
                return@withContext response.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed: ${e.message}", e)
            return@withContext false
        }
    }
}
