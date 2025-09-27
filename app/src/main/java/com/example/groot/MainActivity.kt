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
            Manifest.permission.INTERNET
        )
    }

    // Voice components
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    // Core managers
    private lateinit var llmManager: LLMManager
    private lateinit var memoryManager: MemoryManager

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

                override fun onRmsChanged(rmsdB: Float) {
                    // Optional: show voice activity indicator
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d(TAG, "Speech ended")
                    isListening = false
                }

                override fun onError(error: Int) {
                    Log.e(TAG, "Speech recognition error: $error")
                    isListening = false
                    // Restart listening after error
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
        tts = TextToSpeech(this, this).apply {
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "TTS started")
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "TTS completed")
                    // Resume listening after speaking
                    runOnUiThread {
                        if (!isProcessing) {
                            restartListening()
                        }
                    }
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS error")
                    runOnUiThread {
                        if (!isProcessing) {
                            restartListening()
                        }
                    }
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

    private fun processVoiceCommand(command: String) {
        if (isProcessing) return

        isProcessing = true
        Log.i(TAG, "Processing command: $command")

        // Store in memory for context
        memoryManager.addConversation("User: $command")

        lifecycleScope.launch {
            try {
                val response = llmManager.generateResponse(command)
                handleLLMResponse(response, command)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                speak("I encountered an error processing your request")
                isProcessing = false
            }
        }
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

        // Small delay before restarting
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