package com.example.arts.fragments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.arts.R
import com.example.arts.adapters.ChatAdapter
import com.example.arts.data.ChatMessage
import com.example.arts.data.ReportDraft
import com.example.arts.utils.AiAssistant
import com.example.arts.utils.GeocodeHelper
import com.example.arts.utils.LocationHelper
import com.example.arts.utils.toast
import com.example.arts.viewmodels.IncidentViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full multi-turn chat screen backed by Gemini.
 *
 * Changes from the original version:
 *
 * 1. The greeting is warmer and conversational rather than a list of commands.
 * 2. Typing indicator shows the assistant name ("Aria is typing...") to reinforce
 *    the persona defined in the system instruction.
 * 3. The SOS card is hidden again after a new non-emergency message so it does
 *    not persist across unrelated follow-up turns.
 * 4. Voice recognition feedback uses a mic-active indicator in the input field
 *    rather than a toast, so the UI feels more responsive.
 * 5. Error messages in the chat bubble are friendlier and consistent with Aria's
 *    tone rather than the previous terse technical strings.
 */
class AiAssistantFragment : Fragment() {

    private val incidentViewModel: IncidentViewModel by viewModels()
    private var adapter: ChatAdapter? = null
    private var rvChat: RecyclerView? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false

    // FIX: Registered in onCreate() — see SettingsFragment for explanation.
    private lateinit var micPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startListening()
            else if (isAdded) toast(getString(R.string.mic_permission_needed))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_ai_assistant, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.btn_back)?.setOnClickListener {
            if (isAdded) findNavController().navigateUp()
        }

        rvChat = view.findViewById(R.id.rv_chat)
        val ctx = context ?: return

        if (adapter == null) adapter = ChatAdapter()
        rvChat?.layoutManager = LinearLayoutManager(ctx).apply { stackFromEnd = true }
        rvChat?.adapter = adapter

        // Opening greeting — warm and open-ended, not a command list.
        if (adapter?.itemCount == 0) {
            addAiMessage(
                "Hey, I'm Aria — your ARTS assistant. " +
                "What's going on? Whether you've been in an accident, had a breakdown, " +
                "or just need help with something in the app, I'm here."
            )
        }

        view.findViewById<View>(R.id.btn_voice_input)?.setOnClickListener {
            if (isListening) stopListening() else requestMicAndListen()
        }

        view.findViewById<View>(R.id.btn_send)?.setOnClickListener { sendMessage() }

        view.findViewById<EditText>(R.id.et_message)?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
        }

        view.findViewById<View>(R.id.btn_send_panic)?.setOnClickListener {
            sendEmergencyFromAi()
        }
    }

    // -------------------------------------------------------------------------
    // Sending messages
    // -------------------------------------------------------------------------

    private fun sendMessage() {
        val et   = view?.findViewById<EditText>(R.id.et_message) ?: return
        val text = et.text.toString().trim()
        if (text.isEmpty()) return

        et.text.clear()
        addUserMessage(text)
        view?.findViewById<View>(R.id.card_panic_suggestion)?.visibility = View.GONE
        setThinking(true)

        // FIX: Capture context and history synchronously on the main thread BEFORE
        // launching the coroutine. On Android 14/15 accessing context or the adapter
        // from inside a resumed coroutine after a brief lifecycle pause can return
        // null and cause a NullPointerException that was previously uncaught.
        val ctx     = context ?: run { setThinking(false); return }
        val history = adapter?.getHistory() ?: emptyList()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val (reply, suggestion) = AiAssistant.chat(ctx, history, text)
                if (!isAdded) return@launch
                addAiMessage(reply)
                if (suggestion != null && suggestion.priority == "High") {
                    view?.findViewById<View>(R.id.card_panic_suggestion)?.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                if (isAdded) {
                    addAiMessage(
                        "I ran into a problem there — sorry about that. " +
                        "If this is urgent please call 112 directly."
                    )
                }
            } finally {
                if (isAdded) setThinking(false)
            }
        }
    }

    // -------------------------------------------------------------------------
    // Emergency SOS from chat
    // -------------------------------------------------------------------------

    private fun sendEmergencyFromAi() {
        view?.findViewById<View>(R.id.card_panic_suggestion)?.visibility = View.GONE
        addAiMessage("On it — getting your location now. Hold tight.")

        viewLifecycleOwner.lifecycleScope.launch {
            val ctx = context ?: return@launch
            val location = LocationHelper.currentLocation(ctx)

            if (location == null) {
                if (isAdded) addAiMessage(
                    "I couldn't get a GPS fix right now. " +
                    "Please call 112 directly and tell them your location."
                )
                return@launch
            }

            if (!isAdded) return@launch

            val address = GeocodeHelper.reverseGeocode(ctx, location.latitude, location.longitude)
                ?: "${location.latitude}, ${location.longitude}"

            val details = adapter?.getLastUserMessage() ?: "Emergency SOS via ARTS AI Assistant"

            incidentViewModel.submitReport(
                ReportDraft(
                    type        = "Emergency",
                    description = details,
                    priority    = "High",
                    location    = address,
                    latitude    = location.latitude,
                    longitude   = location.longitude,
                    panic       = true
                )
            )

            if (isAdded) addAiMessage(
                "Done. Help is being dispatched to $address. " +
                "Stay where you are if it's safe, keep your phone on, " +
                "and move away from traffic if you can. You're going to be okay."
            )
        }
    }

    // -------------------------------------------------------------------------
    // Voice input
    // -------------------------------------------------------------------------

    private fun requestMicAndListen() {
        val ctx = context ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startListening() {
        val ctx = context ?: return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            if (isAdded) toast(getString(R.string.error_voice_unavailable))
            return
        }

        isListening = true
        setMicActive(true)

        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onResults(results: Bundle?) {
                    val text = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    if (isAdded) {
                        view?.findViewById<EditText>(R.id.et_message)?.setText(text)
                    }
                    stopListening()
                }
                override fun onError(error: Int) {
                    stopListening()
                    if (isAdded && error != SpeechRecognizer.ERROR_NO_MATCH) {
                        toast(getString(R.string.error_voice_timeout))
                    }
                }
                override fun onReadyForSpeech(params: Bundle?) { setMicActive(true) }
                override fun onEndOfSpeech()                   { setMicActive(false) }
                override fun onBeginningOfSpeech()             {}
                override fun onRmsChanged(rmsdB: Float)        {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onPartialResults(partialResults: Bundle?) {
                    // Show partial results in the input field for real-time feedback.
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    if (isAdded) {
                        view?.findViewById<EditText>(R.id.et_message)?.setText(partial)
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                             RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                }
            )
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        setMicActive(false)
    }

    // -------------------------------------------------------------------------
    // UI helpers
    // -------------------------------------------------------------------------

    private fun addUserMessage(text: String) {
        adapter?.add(ChatMessage(text, fromUser = true, time = now()))
        scrollToEnd()
    }

    private fun addAiMessage(text: String) {
        adapter?.add(ChatMessage(text, fromUser = false, time = now()))
        scrollToEnd()
    }

    private fun scrollToEnd() {
        val count = adapter?.itemCount ?: 0
        if (count > 0) rvChat?.scrollToPosition(count - 1)
    }

    private fun setThinking(show: Boolean) {
        view?.findViewById<View>(R.id.layout_thinking)?.visibility =
            if (show) View.VISIBLE else View.GONE
    }

    private fun setMicActive(active: Boolean) {
        // Tint the mic button green when recording, default tint otherwise.
        val mic = view?.findViewById<android.widget.ImageView>(R.id.btn_voice_input) ?: return
        val ctx = context ?: return
        mic.imageTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(
                ctx,
                if (active) R.color.alert_red else R.color.accent_green
            )
        )
        // Also update the input field hint so the user knows what's happening.
        view?.findViewById<EditText>(R.id.et_message)?.hint =
            if (active) getString(R.string.hint_listening) else getString(R.string.hint_type_message)
    }

    private fun now(): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

    override fun onDestroyView() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        rvChat = null
        super.onDestroyView()
    }
}
