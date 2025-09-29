package com.example.groot

import android.util.Log
import kotlinx.coroutines.delay

class MockLLMManager {
    private val memoryManager = MemoryManager()

    companion object {
        private const val TAG = "MockLLMManager"
    }

    suspend fun generateResponse(prompt: String): String {
        // Simulate network delay
        delay(800)

        Log.d(TAG, "Mock processing: $prompt")

        // Add to memory like real LLM would
        memoryManager.addConversation("User: $prompt")

        val response = when {
            prompt.lowercase().contains("call") -> {
                when {
                    prompt.contains("mom") || prompt.contains("mother") -> "Calling your mom right now!"
                    prompt.contains("dad") || prompt.contains("father") -> "Calling your dad immediately!"
                    else -> "I'll make that call for you!"
                }
            }

            prompt.lowercase().contains("open") -> {
                when {
                    prompt.contains("chrome") -> "Opening Chrome browser for you!"
                    prompt.contains("youtube") -> "Launching YouTube now!"
                    prompt.contains("whatsapp") -> "Opening WhatsApp!"
                    prompt.contains("gmail") -> "Opening Gmail!"
                    else -> "Opening that app now!"
                }
            }

            prompt.lowercase().contains("wifi") -> {
                if (prompt.contains("on") || prompt.contains("enable")) {
                    "Turning WiFi on!"
                } else {
                    "Turning WiFi off!"
                }
            }

            prompt.lowercase().contains("data") -> {
                if (prompt.contains("on") || prompt.contains("enable")) {
                    "Enabling mobile data!"
                } else {
                    "Disabling mobile data!"
                }
            }

            prompt.lowercase().contains("hotspot") -> {
                if (prompt.contains("on") || prompt.contains("enable")) {
                    "Turning hotspot on!"
                } else {
                    "Turning hotspot off!"
                }
            }

            prompt.lowercase().contains("music") || prompt.lowercase().contains("play") -> {
                "Playing music for you!"
            }

            prompt.lowercase().contains("message") || prompt.lowercase().contains("text") -> {
                "Sending that message!"
            }

            prompt.lowercase().contains("search") -> {
                val query = prompt.substringAfter("search", "").trim()
                "Searching for: $query"
            }

            prompt.lowercase().contains("settings") -> {
                "Opening device settings!"
            }

            prompt.lowercase().contains("hello") || prompt.lowercase().contains("hi") -> {
                "Hello! I am Groot, your AI assistant. I'm running in test mode. How can I help you today?"
            }

            prompt.lowercase().contains("introduce") -> {
                "I am Groot, your personal AI assistant! I can help you make calls, open apps, control WiFi, send messages, and much more. Just speak your commands!"
            }

            else -> {
                "I understand your request. Let me help you with that!"
            }
        }

        memoryManager.addConversation("Assistant: $response")
        Log.d(TAG, "Mock response: $response")
        return response
    }

    suspend fun testConnection(): Boolean {
        delay(300)
        Log.d(TAG, "Mock connection test: Success")
        return true
    }

    fun clearMemory() {
        memoryManager.clearMemory()
    }
}