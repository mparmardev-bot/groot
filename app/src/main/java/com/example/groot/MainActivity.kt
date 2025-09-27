package com.example.groot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.groot.ui.theme.GrootTheme
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.INTERNET,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE
        )
    }

    // Voice components
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    // Core managers
    private lateinit var llmManager: LLMManager
    private lateinit var memoryManager: MemoryManager
    private lateinit var taskAutomationManager: TaskAutomationManager

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

    // State
    private var isListening = false
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeManagers()
        requestPermissions()
        initializeVoiceComponents()

        // Set up Compose UI
        setContent {
            GrootTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    GrootUI()
                }
            }
        }

        // Start the main service
        startService(Intent(this, GrootService::class.java))
    }

    @Composable
    private fun GrootUI() {
        var messages by remember { mutableStateOf(listOf<String>()) }
        var isConnected by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Test connection on startup
            isConnected = llmManager.testConnection()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Groot AI Assistant",
                style = MaterialTheme.typography.headlineMedium
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (isConnected) "Connected to Ollama" else "Connecting to Ollama...",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { startListeningForCommand() },
                enabled = !isListening && !isProcessing
            ) {
                Text(if (isListening) "Listening..." else "Tap to Speak")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { testLLMConnection() }
            ) {
                Text("Test AI Connection")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Test buttons for different commands
            Row {
                Button(onClick = { testVoiceCommand("call mom") }) {
                    Text("Test Call")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { testVoiceCommand("open chrome") }) {
                    Text("Test App")
                }
            }

            if (messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Recent Messages:", style = MaterialTheme.typography.titleSmall)
                        messages.takeLast(3).forEach { message ->
                            Text(
                                text = message,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun initializeManagers() {
        llmManager = LLMManager()
        memoryManager = MemoryManager()
        taskAutomationManager = TaskAutomationManager(this)
    }

    private fun requestPermissions() {
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        }
    }

    private fun initializeVoiceComponents() {
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    private fun initializeSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d(TAG, "Speech started")
                    isListening = true
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    isListening = false
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    isListening = false
                    if (!isProcessing) {
                        restartListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { command ->
                        processVoiceCommand(command)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(this, this)
    }

    private fun startListeningForCommand() {
        if (isListening || isProcessing) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceCommand(command: String) {
        if (isProcessing) return

        isProcessing = true
        Log.i(TAG, "Processing command: $command")

        // Store in memory for context
        memoryManager.addConversation("User: $command")

        lifecycleScope.launch {
            try {
                // Get AI response
                val aiResponse = llmManager.generateResponse(buildSmartPrompt(command))

                // Parse the response for actions
                val actionData = parseActionFromResponse(command, aiResponse)

                if (actionData.action.isNotEmpty()) {
                    // Execute the action
                    taskAutomationManager.executeAction(
                        actionData.action,
                        actionData.target,
                        actionData.confidence
                    )
                    speak("${actionData.confirmation} $aiResponse") {
                        isProcessing = false
                    }
                } else {
                    // Just respond with AI text
                    handleLLMResponse(aiResponse, command)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                speak("I encountered an error processing your request")
                isProcessing = false
            }
        }
    }

    private fun buildSmartPrompt(command: String): String {
        return """
        You are Groot, a helpful AI assistant. Analyze this command: "$command"
        
        If it's an action request (call, open app, toggle wifi/data, etc.), respond briefly and helpfully.
        If it's a question or conversation, respond naturally as Groot would.
        
        Command: $command
        """.trimIndent()
    }

    data class ActionData(
        val action: String = "",
        val target: String = "",
        val confidence: Double = 0.0,
        val confirmation: String = ""
    )

    private fun parseActionFromResponse(command: String, response: String): ActionData {
        val lowerCommand = command.lowercase()

        return when {
            lowerCommand.contains("call") -> {
                val target = extractTarget(lowerCommand, listOf("mom", "dad", "mother", "father"))
                ActionData("call", target, 0.9, "Calling")
            }
            lowerCommand.contains("open") -> {
                val target = extractTarget(lowerCommand, listOf("chrome", "youtube", "whatsapp", "gmail"))
                ActionData("open_app", target, 0.9, "Opening")
            }
            lowerCommand.contains("wifi") -> {
                val target = if (lowerCommand.contains("on") || lowerCommand.contains("enable")) "on" else "off"
                ActionData("wifi", target, 0.8, "Toggling WiFi")
            }
            lowerCommand.contains("data") -> {
                val target = if (lowerCommand.contains("on") || lowerCommand.contains("enable")) "on" else "off"
                ActionData("mobile_data", target, 0.8, "Toggling mobile data")
            }
            lowerCommand.contains("hotspot") -> {
                val target = if (lowerCommand.contains("on") || lowerCommand.contains("enable")) "on" else "off"
                ActionData("hotspot", target, 0.8, "Toggling hotspot")
            }
            lowerCommand.contains("settings") -> {
                ActionData("settings", "", 0.9, "Opening settings")
            }
            lowerCommand.contains("search") -> {
                val query = command.substringAfter("search", "").trim()
                ActionData("search", query, 0.8, "Searching for")
            }
            else -> ActionData() // No action, just conversation
        }
    }

    private fun extractTarget(command: String, possibleTargets: List<String>): String {
        return possibleTargets.find { command.contains(it) } ?: ""
    }

    private fun handleLLMResponse(response: String, originalCommand: String) {
        Log.i(TAG, "LLM Response: $response")

        // Store response in memory
        memoryManager.addConversation("Assistant: $response")

        // Speak the response
        speak(response) {
            isProcessing = false
        }
    }

    private fun testVoiceCommand(command: String) {
        processVoiceCommand(command)
    }

    private fun speak(text: String, onComplete: (() -> Unit)? = null) {
        tts?.let { textToSpeech ->
            val utteranceId = UUID.randomUUID().toString()

            if (onComplete != null) {
                textToSpeech.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onDone(id: String?) {
                        if (id == utteranceId) {
                            onComplete()
                        }
                    }
                    override fun onError(id: String?) {
                        if (id == utteranceId) {
                            onComplete()
                        }
                    }
                })
            }

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        }
    }

    private fun restartListening() {
        if (isProcessing) return

        handler.postDelayed({
            if (!isProcessing && !isListening) {
                Log.d(TAG, "Restarting listening")
            }
        }, 1000)
    }

    private fun testLLMConnection() {
        lifecycleScope.launch {
            try {
                val isConnected = llmManager.testConnection()
                val message = if (isConnected) {
                    "Connection successful! Testing AI response..."
                } else {
                    "Connection failed. Make sure Ollama is running."
                }

                speak(message)

                if (isConnected) {
                    val response = llmManager.generateResponse("Hello, please introduce yourself as Groot.")
                    speak(response)
                }
            } catch (e: Exception) {
                speak("Connection test failed: ${e.message}")
                Log.e(TAG, "Connection test error", e)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language not supported")
                } else {
                    speak("Groot is ready. Tap the button to speak.")
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            speechRecognizer?.destroy()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error in cleanup", e)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                Log.i(TAG, "All permissions granted")
                initializeVoiceComponents()
            } else {
                Log.w(TAG, "Some permissions denied")
                speak("I need all permissions to work properly")
            }
        }
    }
}