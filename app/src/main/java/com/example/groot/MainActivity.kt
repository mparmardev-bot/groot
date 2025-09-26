package com.example.groot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.RecognitionListener
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import ai.picovoice.porcupine.*
import java.util.*

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSIONS_REQUEST_CODE = 1001
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.WRITE_SETTINGS
        )
    }

    // Voice components
    private var porcupineManager: PorcupineManager? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null

    // Core managers
    private lateinit var llmManager: LLMManager
    private lateinit var taskAutomationManager: TaskAutomationManager
    private lateinit var memoryManager: MemoryManager

    // State
    private var isListening = false
    private var isProcessing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeManagers()
        requestPermissions()
        initializeVoiceComponents()

        // Start the main service
        startService(Intent(this, GrootService::class.java))
    }

    private fun initializeManagers() {
        llmManager = LLMManager(this)
        taskAutomationManager = TaskAutomationManager(this)
        memoryManager = MemoryManager(this)
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
        initializeHotwordDetection()
        initializeSpeechRecognizer()
        initializeTextToSpeech()
    }

    private fun initializeHotwordDetection() {
        try {
            porcupineManager = PorcupineManager.Builder()
                .setKeywordPaths(arrayOf("hey_groot_android.ppn")) // Place in assets
                .setSensitivities(floatArrayOf(0.7f))
                .setCallback { keywordIndex ->
                    runOnUiThread {
                        onHotwordDetected()
                    }
                }
                .build(this)

            porcupineManager?.start()
            Log.i(TAG, "Hotword detection started")

        } catch (e: Exception) {
            Log.e(TAG, "Hotword detection failed", e)
            // Fallback: continuous listening without hotword
            startContinuousListening()
        }
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

    private fun onHotwordDetected() {
        Log.i(TAG, "Hey Groot detected!")
        startListeningForCommand()
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
        memoryManager.addConversation(command, "")

        lifecycleScope.launch {
            try {
                val response = llmManager.processVoiceCommand(command)
                response?.let {
                    handleLLMResponse(it, command)
                } ?: run {
                    speak("I'm sorry, I couldn't process that command")
                    isProcessing = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing command", e)
                speak("I encountered an error processing your request")
                isProcessing = false
            }
        }
    }

    private fun handleLLMResponse(response: LLMResponse, originalCommand: String) {
        Log.i(TAG, "LLM Response: $response")

        // Store response in memory
        memoryManager.addConversation(originalCommand, response.reply)

        // Set emotional TTS parameters
        setEmotionalTTS(response.emotion)

        // Speak the response
        speak(response.reply) {
            // Execute action after speaking
            executeAction(response)
        }
    }

    private fun executeAction(response: LLMResponse) {
        lifecycleScope.launch {
            try {
                taskAutomationManager.executeAction(
                    response.action,
                    response.target,
                    response.confidence
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error executing action", e)
                speak("I couldn't complete that action")
            } finally {
                isProcessing = false
            }
        }
    }

    private fun setEmotionalTTS(emotion: String) {
        tts?.apply {
            when (emotion.lowercase()) {
                "happy", "excited" -> {
                    setPitch(1.2f)
                    setSpeechRate(1.1f)
                }
                "sad", "apologetic" -> {
                    setPitch(0.8f)
                    setSpeechRate(0.9f)
                }
                "confident", "helpful" -> {
                    setPitch(1.0f)
                    setSpeechRate(1.0f)
                }
                "calm", "thoughtful" -> {
                    setPitch(0.9f)
                    setSpeechRate(0.8f)
                }
                else -> {
                    setPitch(1.0f)
                    setSpeechRate(1.0f)
                }
            }
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
                try {
                    porcupineManager?.stop()
                    porcupineManager?.start()
                } catch (e: Exception) {
                    Log.e(TAG, "Error restarting hotword detection", e)
                }
            }
        }, 1000)
    }

    private fun startContinuousListening() {
        // Fallback mode without hotword - tap to speak or continuous listening
        Log.i(TAG, "Starting continuous listening mode")
        // Implementation depends on your preference
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.let { textToSpeech ->
                val result = textToSpeech.setLanguage(Locale.US)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e(TAG, "TTS language not supported")
                } else {
                    speak("Groot is ready. Say Hey Groot to begin.")
                }
            }
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        try {
            porcupineManager?.stop()
            porcupineManager?.delete()
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