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
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.arts.R
import com.example.arts.data.ReportDraft
import com.example.arts.utils.AiAssistant
import com.example.arts.utils.GeocodeHelper
import com.example.arts.utils.LocationHelper
import com.example.arts.utils.toast
import com.example.arts.viewmodels.IncidentViewModel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Voice reporting fragment that uses speech-to-text to capture incident details.
 * Analyzes the transcript using AI to suggest incident types and priorities.
 */
class VoiceReportFragment : Fragment() {
    private val incidentViewModel: IncidentViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var transcribed = ""

    // FIX: Registered in onCreate() to avoid IllegalStateException on Android 14+.
    private lateinit var micPerm: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        micPerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startListening()
            else if (isAdded) toast(getString(R.string.mic_permission_needed))
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View =
        inflater.inflate(R.layout.fragment_voice_report, c, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.btn_back)?.setOnClickListener { 
            if (isAdded) findNavController().navigateUp() 
        }
        val tvStatus = view.findViewById<TextView>(R.id.tv_voice_status)
        val tvTranscript = view.findViewById<TextView>(R.id.tv_transcript)
        val btnRecord = view.findViewById<Button>(R.id.btn_record)
        val btnSubmit = view.findViewById<Button>(R.id.btn_submit_voice)

        btnRecord?.setOnClickListener {
            if (isListening) {
                stopListening(tvStatus, btnRecord)
            } else {
                val ctx = context ?: return@setOnClickListener
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    startListening()
                } else {
                    micPerm.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        recognizer = SpeechRecognizer.createSpeechRecognizer(requireContext()).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { 
                    isListening = true
                    tvStatus?.text = getString(R.string.status_listening)
                    btnRecord?.text = getString(R.string.btn_stop)
                }
                override fun onResults(r: Bundle?) {
                    val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    transcribed = text
                    tvTranscript?.text = text
                    tvStatus?.text = getString(R.string.status_ready_submit)
                    btnRecord?.text = getString(R.string.btn_record_again)
                    isListening = false
                }
                override fun onError(code: Int) { 
                    tvStatus?.text = getString(R.string.status_did_not_catch)
                    isListening = false
                    btnRecord?.text = getString(R.string.btn_record)
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(r: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() { 
                    tvStatus?.text = getString(R.string.status_processing) 
                }
                override fun onPartialResults(p: Bundle?) {
                    val partial = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
                    tvTranscript?.text = partial
                }
                override fun onEvent(e: Int, p: Bundle?) {}
            })
        }

        btnSubmit?.setOnClickListener {
            if (transcribed.isBlank()) { 
                toast(getString(R.string.toast_record_first))
                return@setOnClickListener 
            }
            val ctx = context ?: return@setOnClickListener
            btnSubmit.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val suggestion = AiAssistant.analyzeIncident(ctx, transcribed)
                    val location = LocationHelper.currentLocation(ctx)
                    val locationText = if (location != null) {
                        GeocodeHelper.reverseGeocode(ctx, location.latitude, location.longitude) 
                            ?: getString(R.string.location_format, location.latitude, location.longitude)
                    } else {
                        getString(R.string.location_unavailable)
                    }
                    
                    if (!isAdded) return@launch
                    
                    incidentViewModel.submitReport(ReportDraft(
                        type = if (suggestion.type == "Pedestrian") "Pedestrian Incident" else suggestion.type,
                        description = suggestion.refinedDescription,
                        priority = suggestion.priority,
                        location = locationText,
                        latitude = location?.latitude,
                        longitude = location?.longitude
                    ))
                    toast(getString(R.string.toast_voice_report_submitted))
                    if (isAdded) findNavController().navigateUp()
                } catch (e: Exception) {
                    toast(getString(R.string.toast_failed_to_submit, e.message ?: ""))
                    if (isAdded) btnSubmit.isEnabled = true
                }
            }
        }
    }

    private fun startListening() {
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    private fun stopListening(status: TextView?, btn: Button?) {
        recognizer?.stopListening()
        isListening = false
        status?.text = getString(R.string.status_processing)
        btn?.text = getString(R.string.btn_record)
    }

    override fun onDestroyView() { 
        super.onDestroyView()
        recognizer?.destroy()
        recognizer = null 
    }
}
