package com.example.workshoprobot

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.workshoprobot.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

// Suspend extension function to bridge OkHttp callbacks with coroutine
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                // Don't resume with exception if coroutine was cancelled
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            cancel()
        }
    }
}

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ViewBinding for safe access to UI components
    private lateinit var binding: ActivityMainBinding

    // AI and State Management
    private var llmJob: Job? = null
    private var isMuted = false
    val chatHistory = mutableListOf<Pair<String, Boolean>>() // true for user, false for AI

    // Core Speech Components
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

    // OkHttp Client
    private val client = OkHttpClient()

    // Activity result launcher for microphone permission
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startListening()
            } else {
                Toast.makeText(this, "Für Sprachbefehle ist die Mikrofonberechtigung erforderlich.", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Inflate the layout using View Binding
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Text-to-Speech and Speech-to-Text
        tts = TextToSpeech(this, this)
        setupSpeechRecognizer()

        // Setup listeners for all buttons using the safe 'binding' object
        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.fabVoiceCommand.setOnClickListener {
            checkAndRequestMicrophonePermission()
        }

        binding.btnHome.setOnClickListener {
            // Clear the back stack to ensure we return to the initial state
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
            // Hide the content pane to show the initial home screen content
            binding.contentPane.visibility = View.VISIBLE
        }

        binding.btnAusstattung.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, MachinesFragment())
                addToBackStack(null)
                commit()
            }
        }


        binding.btnMap.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, MapFragment())
                addToBackStack(null)
                commit()
            }
        }

        binding.btnBigbluebutton.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, BigBlueButtonFragment())
                addToBackStack(null)
                commit()
            }
        }

        binding.btnAiAssistant.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, AiAssistantFragment(), "AI_ASSISTANT_FRAGMENT_TAG")
                addToBackStack(null)
                commit()
            }
        }

        binding.btnControlRobot.setOnClickListener {
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, MqttControlFragment())
                addToBackStack(null)
                commit()
            }
        }

        binding.fabStopSpeaking.setOnClickListener {
            llmJob?.cancel() // Interrupts the API call
            if (::tts.isInitialized && tts.isSpeaking) {
                tts.stop() // Silences the voice
            }
            it.visibility = View.GONE // Hides the stop button
        }

        binding.fabMuteToggle.setOnClickListener {
            isMuted = !isMuted
            if (isMuted) {
                if (::tts.isInitialized && tts.isSpeaking) tts.stop()
                binding.fabMuteToggle.setImageResource(android.R.drawable.ic_lock_silent_mode)
                Toast.makeText(this, "Stumm geschaltet", Toast.LENGTH_SHORT).show()
            } else {
                binding.fabMuteToggle.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                Toast.makeText(this, "Stummschaltung aufgehoben", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSpokenCommand(command: String) {
        Log.d("DEBUG_AI", "handleSpokenCommand called with: '$command'")
        Toast.makeText(this, "Du hast gesagt: $command", Toast.LENGTH_SHORT).show()
        processAiQuery(command)
    }

    private fun processAiQuery(query: String) {
        Log.d("DEBUG_AI", "processAiQuery called with: '$query'")
        llmJob?.cancel() // Cancel any old job

        // Add user message to history immediately and update UI
        chatHistory.add(Pair(query, true))
        updateChatUi()

        llmJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                speakOut("Einen Moment, ich denke nach...")

                // Construct the JSON body according to OpenAI's API structure
                val messagesArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", query)
                    })
                }
                val jsonObject = JSONObject().apply {
                    put("messages", messagesArray)
                    put("temperature", 0.7)
                }
                val jsonBody = jsonObject.toString()

                val request = Request.Builder()
                    .url("http://172.16.2.238:1234/v1/chat/completions") // LM Studio endpoint
                    .post(jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .build()

                // Execute request and handle response on the correct threads
                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).await()
                }

                if (response.isSuccessful) {
                    val responseBody = response.body?.string()
                    var responseText = "Ich habe leider keine Antwort darauf gefunden."
                    if (responseBody != null) {
                        try {
                            val responseJson = JSONObject(responseBody)
                            val choices = responseJson.getJSONArray("choices")
                            if (choices.length() > 0) {
                                val message = choices.getJSONObject(0).getJSONObject("message")
                                responseText = message.getString("content")
                            }
                        } catch (e: JSONException) {
                            Log.e("OkHttpParseError", "Failed to parse OpenAI-compatible response", e)
                        }
                    }
                    
                    chatHistory.add(Pair(responseText, false))
                    updateChatUi()

                    val textForSpeech = responseText.replace("*", "")
                    tts.language = Locale.GERMAN
                    speakOut(textForSpeech)
                } else {
                    Log.e("OkHttpError", "API call not successful: ${response.code} ${response.message}")
                    val errorMsg = "Entschuldigung, der Server hat einen Fehler zurückgegeben."
                    chatHistory.add(Pair(errorMsg, false))
                    updateChatUi()
                    speakOut(errorMsg)
                }

            } catch (e: Exception) {
                when (e) {
                    is kotlinx.coroutines.CancellationException -> {
                        Log.d("DEBUG_AI", "LLM job was cancelled by user.")
                    }
                    is IOException -> {
                        Log.e("OkHttpError", "API call failed", e)
                        val errorMsg = "Entschuldigung, es gab ein Problem bei der Verbindung zum lokalen LLM."
                        chatHistory.add(Pair(errorMsg, false))
                        updateChatUi()
                        speakOut(errorMsg)
                    }
                    else -> {
                        Log.e("LLMError", "Processing failed", e)
                        val errorMsg = "Entschuldigung, es gab ein Problem bei der Verarbeitung meiner Antwort."
                        chatHistory.add(Pair(errorMsg, false))
                        updateChatUi()
                        speakOut(errorMsg)
                    }
                }
            }
        }
    }

    private fun updateChatUi() {
        val aiFragment = supportFragmentManager.findFragmentByTag("AI_ASSISTANT_FRAGMENT_TAG") as? AiAssistantFragment
        if (aiFragment != null && aiFragment.isVisible) {
            aiFragment.onNewMessage()
        } else {
            // If the fragment is not visible, we switch to it. 
            // performClick() handles the fragment transaction and tag assignment.
            val currentFragment = supportFragmentManager.findFragmentById(binding.contentPane.id)
            if (currentFragment !is AiAssistantFragment) {
                binding.btnAiAssistant.performClick()
            }
        }
    }

    fun speakOut(text: String) {
        if (isMuted || !::tts.isInitialized) {
            runOnUiThread { binding.fabStopSpeaking.visibility = View.GONE }
            return
        }
        binding.fabStopSpeaking.visibility = View.VISIBLE
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) { runOnUiThread { binding.fabStopSpeaking.visibility = View.GONE } }
            override fun onError(utteranceId: String?) { runOnUiThread { binding.fabStopSpeaking.visibility = View.GONE } }
        })
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "UniqueID")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.GERMAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The specified language is not supported.")
            }
        } else {
            Log.e("TTS", "Initialization failed.")
        }
    }

    private fun setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.e("STT", "Speech recognition is not available on this device.")
            return
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                Log.d("DEBUG_AI", "onResults was called.")
                results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let {
                    handleSpokenCommand(it)
                } ?: Log.d("DEBUG_AI", "STT returned empty or null matches.")
            }
            override fun onError(error: Int) { Log.e("DEBUG_AI", "STT Error Code: $error") }
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    private fun checkAndRequestMicrophonePermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startListening()
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    private fun startListening() {
        // Defensive Actions to prevent audio conflicts
        if (::tts.isInitialized && tts.isSpeaking) {
            tts.stop()
        }
        speechRecognizer.cancel() // Forcefully cancel any previous session

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "de-DE") // Explicitly set German
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Zuhören...")
        }

        // A small delay helps the system release audio hardware properly
        binding.root.postDelayed({
            try {
                speechRecognizer.startListening(intent)
                Log.d("DEBUG_AI", "startListening command issued.")
            } catch (e: Exception) {
                Log.e("DEBUG_AI", "startListening failed", e)
            }
        }, 100)
    }

    override fun onDestroy() {
        llmJob?.cancel()
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        if (::speechRecognizer.isInitialized) {
            speechRecognizer.destroy()
        }
        super.onDestroy()
    }
}
