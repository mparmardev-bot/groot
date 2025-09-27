package com.example.groot

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*

class GrootService : Service() {
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var llmManager: LLMManager

    companion object {
        private const val TAG = "GrootService"
    }

    override fun onCreate() {
        super.onCreate()
        llmManager = LLMManager()
        Log.d(TAG, "GrootService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "GrootService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        serviceScope.cancel()
        Log.d(TAG, "GrootService destroyed")
        super.onDestroy()
    }

    fun processCommand(command: String, callback: (String) -> Unit) {
        serviceScope.launch {
            try {
                val response = llmManager.generateResponse(command)
                callback(response)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command: ${e.message}")
                callback("I'm sorry, I encountered an error processing your request.")
            }
        }
    }
}