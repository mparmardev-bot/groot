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
    private val baseUrl = "https://nonguttural-alveolarly-wesley.ngrok-free.dev" // For Android emulator
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
            // Add user input to memory
            memoryManager.addConversation("User: $prompt")

            // Get recent context for better responses
            val context = memoryManager.getRecentContext(3)
            val contextPrompt = if (context.isNotEmpty()) {
                context.joinToString("\n") + "\nUser: $prompt"
            } else {
                prompt
            }

            val jsonBody = JSONObject().apply {
                put("model", "gemma2:2b")
                put("prompt", contextPrompt)
                put("stream", false)
            }

            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$baseUrl/api/generate")
                .post(requestBody)
                .build()

            Log.d(TAG, "Sending request to Ollama: $contextPrompt")

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response")
                }

                val responseBody = response.body?.string() ?: ""
                Log.d(TAG, "Raw response: $responseBody")

                val jsonResponse = JSONObject(responseBody)
                val generatedText = jsonResponse.getString("response")

                // Add AI response to memory
                memoryManager.addConversation("Assistant: $generatedText")

                Log.d(TAG, "Generated response: $generatedText")
                return@withContext generatedText
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
                .url("$baseUrl/api/version")
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                val isSuccessful = response.isSuccessful
                Log.d(TAG, "Connection test: ${if (isSuccessful) "Success" else "Failed"}")
                return@withContext isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Connection test failed", e)
            return@withContext false
        }
    }
}
