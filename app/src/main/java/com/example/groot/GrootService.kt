package com.example.groot

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject

class GrootService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var llmManager: LLMManager
    private lateinit var taskManager: TaskAutomationManager

    companion object {
        private const val TAG = "GrootService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): GrootService = this@GrootService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        llmManager = LLMManager()
        taskManager = TaskAutomationManager(this)
        Log.d(TAG, "GrootService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "GrootService started")
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        serviceScope.cancel()
        Log.d(TAG, "GrootService destroyed")
        super.onDestroy()
    }

    fun processCommand(command: String, callback: (String, String, String) -> Unit) {
        serviceScope.launch {
            try {
                val response = llmManager.generateResponse(command)
                val json = JSONObject(response)
                val reply = json.optString("reply", "")
                val action = json.optString("action", "none")
                val target = json.optString("target", "")

                // Execute action if AI returned a recognized command
                if (action != "none") {
                    taskManager.executeAction(action, target, confidence = 1.0)
                }

                callback(reply, action, target)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command: ${e.message}")
                val isHindi = command.matches(Regex(".*[\\u0900-\\u097F].*"))
                val errorMsg = if (isHindi) {
                    "मुझे खेद है sir.., मुझे आपके अनुरोध को संसाधित करने में त्रुटि हुई।"+ "मुझे अपनी AI... सर्वर... और,,, डेटाबेस... से जुड़ने में समस्या हो रही है।... क्या आप एक बार चेक कर सकते हैं कि " +
                 "मैं सर्वर से कनेक्ट हूँ या नहीं? क्योंकि सर्वर से कनेक्ट हुए बिना मैं आपकी मदद नहीं कर पाऊँगा, सर । i am really very sorry sir"
                } else {
                    "I'm sorry, I encountered an error processing your request."
                }
                callback(errorMsg, "none", "")
            }
        }
    }
}
