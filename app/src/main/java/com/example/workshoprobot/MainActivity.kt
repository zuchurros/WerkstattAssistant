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
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    // ViewBinding for safe access to UI components
    private lateinit var binding: ActivityMainBinding

    // AI and State Management
    private var geminiJob: Job? = null
    private var aiAssistantFragment: AiAssistantFragment? = null
    private var isMuted = false

    // Core Speech Components
    private lateinit var tts: TextToSpeech
    private lateinit var speechRecognizer: SpeechRecognizer

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

        // Listener to show navigation pane when returning to home
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                binding.navigationPane.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabVoiceCommand.setOnClickListener {
            checkAndRequestMicrophonePermission()
        }

        binding.btnHome.setOnClickListener {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
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
            binding.navigationPane.visibility = View.GONE
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, BigBlueButtonFragment())
                addToBackStack(null)
                commit()
            }
        }

        binding.btnControlRobot.setOnClickListener {
            binding.navigationPane.visibility = View.GONE
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, ControlFragment())
                addToBackStack(null)
                commit()
            }
        }

        binding.btnAiAssistant.setOnClickListener {
            val fragment = AiAssistantFragment()
            aiAssistantFragment = fragment
            supportFragmentManager.beginTransaction().apply {
                replace(binding.contentPane.id, fragment)
                addToBackStack(null)
                commit()
            }
        }

        binding.fabStopSpeaking.setOnClickListener {
            geminiJob?.cancel() // Interrupts the API call
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
        geminiJob?.cancel() // Cancel any old job

        val generativeModel = GenerativeModel("gemini-2.5-flash", BuildConfig.GEMINI_API_KEY)

        geminiJob = CoroutineScope(Dispatchers.Main).launch {
            try {
                speakOut("Einen Moment, ich denke nach...")
                val response = generativeModel.generateContent(query)
                response.text?.let { responseText ->
                    aiAssistantFragment?.updateChat(query, responseText)
                    val textForSpeech = responseText.replace("*", "")
                    tts.language = Locale.GERMAN
                    speakOut(textForSpeech)
                } ?: run {
                    speakOut("Ich habe leider keine Antwort darauf gefunden.")
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d("DEBUG_AI", "Gemini job was cancelled by user.")
                } else {
                    Log.e("GeminiError", "API call failed", e)
                    speakOut("Entschuldigung, es gab ein Problem bei der Verarbeitung meiner Antwort.")
                }
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
                Log.e("TTS", "The specified language (German) is not supported.")
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
        geminiJob?.cancel()
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
