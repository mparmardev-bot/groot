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
    private val offlineIntelligence = OfflineIntelligence()

    companion object {
        private const val TAG = "GrootService"
    }

    inner class LocalBinder : Binder() {
        fun getService(): GrootService = this@GrootService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        llmManager = LLMManager(this)
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
        Log.d( TAG,"original cmd : $command")
        serviceScope.launch {
            try {
                Log.d(TAG, "Processing: $command")

                // STEP 1: Check memory queries (contacts, preferences)
                val memoryResponse = checkMemoryQuery(command)
                if (memoryResponse != null) {
                    Log.i(TAG, "✅ Answered from memory: $memoryResponse")
                    callback(memoryResponse, "none", "")
                    return@launch
                }

                // STEP 2: Try offline intelligence FIRST
                Log.d(TAG, "Trying offline intelligence...")
                val offlineResponse = offlineIntelligence.handleOffline(command)

                if (offlineResponse.handled) {
                    Log.i(TAG, "✅ Handled offline: ${offlineResponse.reply}")

                    // Execute action if needed
                    if (offlineResponse.action != "none") {
                        taskManager.executeAction(
                            offlineResponse.action,
                            offlineResponse.target,
                            confidence = 1.0
                        )
                    }

                    callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target)
                    return@launch
                }

                // STEP 3: Save preferences if user is stating something
                saveUserPreferences(command)

                // STEP 4: Try server (only if offline couldn't handle)
                Log.d(TAG, "Offline couldn't handle, trying server...")
                val response = llmManager.generateResponse(command)
                val json = JSONObject(response)
                val reply = json.optString("reply", "")
                val action = json.optString("action", "none")
                val target = json.optString("target", "")

                if (action != "none") {
                    taskManager.executeAction(action, target, confidence = 1.0)
                }

                callback(reply, action, target)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error: ${e.message}", e)

                // STEP 5: Fallback to offline again
                val offlineResponse = offlineIntelligence.handleOffline(command)
                if (offlineResponse.handled) {
                    Log.i(TAG, "✅ Server failed, using offline fallback")
                    callback(offlineResponse.reply, offlineResponse.action, offlineResponse.target)
                } else {
                    callback("I'm sorry, I couldn't process that. Please try again.", "none", "")
                }
            }
        }
    }

    private fun checkMemoryQuery(command: String): String? {
        val lowerCommand = command.lowercase()

        if (lowerCommand.contains("what is") ||
            lowerCommand.contains("what's") ||
            lowerCommand.contains("tell me")) {

            // Check contacts
            if (lowerCommand.contains("contact") ||
                lowerCommand.contains("number") ||
                lowerCommand.contains("phone")) {

                val words = command.split(" ")
                val nameIndex = words.indexOfFirst {
                    it.lowercase() == "of" || it.lowercase() == "for"
                }

                if (nameIndex != -1 && nameIndex + 1 < words.size) {
                    val contactName = words.subList(nameIndex + 1, words.size).joinToString(" ")
                    val contacts = taskManager.getAllContacts()
                    val cleanName = contactName.trim().lowercase()

                    contacts[cleanName]?.let { number ->
                        return "The contact number for $contactName is $number"
                    }

                    contacts.entries.find { (key, _) ->
                        cleanName.contains(key) || key.contains(cleanName)
                    }?.let { (name, number) ->
                        return "The contact number for $name is $number"
                    }
                }
            }

            // Check preferences
            if (lowerCommand.contains("favorite") || lowerCommand.contains("my")) {
                val memoryManager = llmManager.getMemoryManager()
                val allConversations = memoryManager.getAllConversations()

                allConversations.forEach { entry ->
                    val msg = entry.message.lowercase()
                    if (msg.contains("favorite") || msg.contains("like")) {
                        if (lowerCommand.contains("color") && msg.contains("color")) {
                            val colorWords = listOf("black", "white", "red", "blue", "green", "yellow", "orange", "purple", "pink")
                            colorWords.forEach { color ->
                                if (msg.contains(color)) {
                                    return "Your favorite color is $color, as you told me earlier!"
                                }
                            }
                        }
                    }
                }

                if (lowerCommand.contains("color")) {
                    memoryManager.getUserPreference("favorite_color")?.let { color ->
                        return "Your favorite color is $color!"
                    }
                }
            }
        }

        return null
    }

    private fun saveUserPreferences(command: String) {
        val lowerCommand = command.lowercase()
        val memoryManager = llmManager.getMemoryManager()

        if (lowerCommand.contains("my favorite") ||
            lowerCommand.contains("i like") ||
            lowerCommand.contains("i love")) {

            if (lowerCommand.contains("color")) {
                val colorWords = listOf("black", "white", "red", "blue", "green", "yellow", "orange", "purple", "pink")
                colorWords.forEach { color ->
                    if (lowerCommand.contains(color)) {
                        memoryManager.saveUserPreference("favorite_color", color)
                        Log.d(TAG, "💾 Saved: favorite_color = $color")
                    }
                }
            }
        }
    }
}