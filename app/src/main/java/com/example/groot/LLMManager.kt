package com.example.groot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.ConnectivityException

class LLMManager(private val context: Context) {

    companion object {
        private const val TAG = "LLMManager"
        private const val LOCAL_BASE_URL = "http://10.103.5.234:8000/"
        private const val CLOUD_FALLBACK_URL = "https://api-inference.huggingface.co/"
    }

    private val localService: LLMService by lazy {
        Retrofit.Builder()
            .baseUrl(LOCAL_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LLMService::class.java)
    }

    suspend fun processVoiceCommand(command: String): LLMResponse? {
        return try {
            // Try local LLM first
            val response = localService.processCommand(LLMRequest(command))
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.e(TAG, "Local LLM failed: ${response.code()}")
                createFallbackResponse(command)
            }
        } catch (e: Exception) {
            Log.e(TAG, "LLM request failed", e)
            createFallbackResponse(command)
        }
    }

    private fun createFallbackResponse(command: String): LLMResponse {
        val lowerCommand = command.lowercase()

        return when {
            "call" in lowerCommand -> LLMResponse(
                reply = "Making the call now",
                action = "call",
                target = extractTarget(lowerCommand, "call"),
                emotion = "friendly",
                confidence = 0.8
            )
            "turn on" in lowerCommand && "data" in lowerCommand -> LLMResponse(
                reply = "Turning on mobile data",
                action = "mobile_data",
                target = "on",
                emotion = "helpful",
                confidence = 0.9
            )
            "search" in lowerCommand -> LLMResponse(
                reply = "Searching for that",
                action = "search",
                target = extractTarget(lowerCommand, "search"),
                emotion = "helpful",
                confidence = 0.8
            )
            else -> LLMResponse(
                reply = "I'm processing your request",
                action = "none",
                target = "",
                emotion = "thoughtful",
                confidence = 0.6
            )
        }
    }

    private fun extractTarget(command: String, action: String): String {
        return command.substringAfter(action).trim().takeIf { it.isNotEmpty() } ?: "unknown"
    }
}