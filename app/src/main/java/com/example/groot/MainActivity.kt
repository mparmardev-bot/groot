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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import com.example.groot.ui.theme.GrootTheme
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Request
import org.json.JSONObject
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
    private var isListeningContinuously = false

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
        var connectionStatus by remember { mutableStateOf("Testing connection...") }
        var lastCommand by remember { mutableStateOf("") }
        var isContinuousMode by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            // Test connection on startup
            connectionStatus = "Connecting to Ollama..."
            isConnected = llmManager.testConnection()
            connectionStatus = if (isConnected) {
                "Connected to Ollama (gemma3:1b)"
            } else {
                "Failed to connect - Check Ollama"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Header
            Text(
                text = "Groot AI Assistant",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Connection Status Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected)
                        Color.Green.copy(alpha = 0.1f)
                    else
                        Color.Red.copy(alpha = 0.1f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isConnected) "🟢" else "🔴",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Main Voice Button
            Button(
                onClick = { startListeningForCommand() },
                enabled = !isListening && !isProcessing && isConnected && !isContinuousMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when {
                        isListening -> Color.Red
                        isProcessing -> Color(0xFFFFA500) // Orange
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Text(
                    text = when {
                        isListening -> "🎤 Listening..."
                        isProcessing -> "⏳ Processing..."
                        !isConnected -> "❌ Not Connected"
                        isContinuousMode -> "🎧 Continuous Mode Active"
                        else -> "🗣️ Tap to Speak"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Continuous Listening Toggle Button
            Button(
                onClick = {
                    isContinuousMode = !isContinuousMode
                    if (isContinuousMode) {
                        startContinuousListening()
                    } else {
                        stopContinuousListening()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isConnected,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isContinuousMode) Color.Green else MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (isContinuousMode) "🔊 Stop Continuous Mode" else "🎧 Enable Continuous Mode")
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        lifecycleScope.launch {
                            connectionStatus = "Testing..."
                            isConnected = llmManager.testConnection()
                            connectionStatus = if (isConnected) {
                                "Connected to Ollama (gemma3:1b)"
                            } else {
                                "Connection failed"
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔄 Test")
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        memoryManager.clearMemory()
                        speak("Memory cleared")
                        messages = emptyList()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🗑️ Clear")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Test Buttons
            Text(
                text = "Quick Tests:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        testVoiceCommand("hello, introduce yourself")
                        lastCommand = "hello, introduce yourself"
                    },
                    enabled = !isProcessing && isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("👋 Hello")
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = {
                        testVoiceCommand("call mom")
                        lastCommand = "call mom"
                    },
                    enabled = !isProcessing && isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📞 Call")
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = {
                        testVoiceCommand("open chrome")
                        lastCommand = "open chrome"
                    },
                    enabled = !isProcessing && isConnected,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🌐 Chrome")
                }
            }

            // Last Command Display
            if (lastCommand.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Last Command:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = lastCommand,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            // Messages Display
            if (messages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Recent Activity:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        messages.takeLast(3).forEach { message ->
                            Text(
                                text = "• $message",
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

                    // Restart continuous listening after error
                    if (isListeningContinuously && !isProcessing) {
                        handler.postDelayed({
                            startVoiceActivatedListening()
                        }, 2000)
                    } else if (!isProcessing) {
                        restartListening()
                    }
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { command ->
                        processVoiceCommand(command)
                    }

                    // Restart listening if in continuous mode
                    if (isListeningContinuously && !isProcessing) {
                        handler.postDelayed({
                            startVoiceActivatedListening()
                        }, 1000)
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    private fun initializeTextToSpeech() {
        tts = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed")
                    // Restart listening if in continuous mode
                    runOnUiThread {
                        if (!isProcessing && isListeningContinuously) {
                            handler.postDelayed({
                                startVoiceActivatedListening()
                            }, 1000)
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error")
                }
            })
        }
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

    private fun startContinuousListening() {
        isListeningContinuously = true
        startVoiceActivatedListening()
        speak("Continuous listening activated. I'm always listening now.")
    }

    private fun stopContinuousListening() {
        isListeningContinuously = false
        speechRecognizer?.stopListening()
        speak("Continuous listening disabled.")
    }

    private fun startVoiceActivatedListening() {
        if (!isListeningContinuously || isProcessing) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000)
        }

        speechRecognizer?.startListening(intent)
    }

    private fun processVoiceCommand(command: String) {
        if (isProcessing) return

        isProcessing = true
        Log.i(TAG, "Processing command: $command")

        lifecycleScope.launch {
            try {
                // Send to FastAPI server
                val jsonBody = JSONObject().apply {
                    put("text", command)
                    put("context", "mobile_assistant")
                }

                val requestBody = jsonBody.toString()
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("http://10.98.214.234:8000/query")
                    .post(requestBody)
                    .addHeader("Content-Type", "application/json")
                    .build()

                llmManager.client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val responseBody = response.body?.string() ?: ""
                        val jsonResponse = JSONObject(responseBody)

                        val reply = jsonResponse.getString("reply")
                        val action = jsonResponse.getString("action")
                        val target = jsonResponse.getString("target")
                        val confidence = jsonResponse.getDouble("confidence")

                        Log.i(TAG, "Server response - Reply: $reply, Action: $action, Target: $target")

                        // Execute action if not "none"
                        if (action != "none") {
                            Log.i(TAG, "Executing action: $action with target: $target")
                            taskAutomationManager.executeAction(action, target, confidence)
                        }

                        // Speak the response
                        speak(reply) {
                            isProcessing = false
                        }
                    } else {
                        speak("Sorry, I couldn't process that request") {
                            isProcessing = false
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                speak("I encountered an error: ${e.message}") {
                    isProcessing = false
                }
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

    private fun handleLLMResponse(response: String) {
        Log.i(TAG, "LLM Response: $response")
        speak(response) {
            isProcessing = false
        }
    }

    private fun testVoiceCommand(command: String) {
        Log.i(TAG, "Testing command: $command")
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

                // Try to find and set a male voice
                val voices = textToSpeech.voices
                val maleVoice = voices?.find { voice ->
                    voice.name.contains("male", ignoreCase = true) &&
                            voice.locale.language == "en"
                }

                if (maleVoice != null) {
                    textToSpeech.voice = maleVoice
                    Log.d(TAG, "Set male voice: ${maleVoice.name}")
                } else {
                    Log.w(TAG, "No male voice found, using pitch adjustment")
                    // Fallback to pitch adjustment
                    textToSpeech.setPitch(0.6f) // Even lower for more masculine sound
                }

                textToSpeech.setSpeechRate(0.9f)

                if (result != TextToSpeech.LANG_MISSING_DATA) {
                    speak("I am Groot, your AI assistant. Ready to help you.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            isListeningContinuously = false
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
