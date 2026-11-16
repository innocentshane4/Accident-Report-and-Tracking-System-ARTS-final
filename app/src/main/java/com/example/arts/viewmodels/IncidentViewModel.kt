package com.example.arts.viewmodels

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.arts.data.ChatMessage
import com.example.arts.data.Incident
import com.example.arts.data.ReportDraft
import com.example.arts.data.SubmitResult
import com.example.arts.repository.IncidentRepository
import com.example.arts.utils.AiAssistant
import com.example.arts.utils.PrefsHelper
import kotlinx.coroutines.launch

private const val TAG = "ARTS-IncidentVM"

class IncidentViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IncidentRepository()
    private val _searchQuery = MutableLiveData("")
    private val _statusFilter = MutableLiveData("All")

    val allIncidents: LiveData<List<Incident>> = repository.observeMyIncidents()

    val recentIncidents: LiveData<List<Incident>> = MediatorLiveData<List<Incident>>().apply {
        addSource(allIncidents) { incidents -> 
            value = incidents?.take(3) ?: emptyList()
        }
    }

    private val _filteredIncidents = MediatorLiveData<List<Incident>>()
    val filteredIncidents: LiveData<List<Incident>> = _filteredIncidents

    private val _submitResult = MutableLiveData<SubmitResult?>()
    val submitResult: LiveData<SubmitResult?> = _submitResult

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _errorMessage = MutableLiveData<String?>()
    val errorMessage: LiveData<String?> = _errorMessage

    // --- AI Chat History ---
    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory

    init {
        _filteredIncidents.addSource(allIncidents) { applyFilter() }
        _filteredIncidents.addSource(_searchQuery) { applyFilter() }
        _filteredIncidents.addSource(_statusFilter) { applyFilter() }
    }

    private fun applyFilter() {
        val incidents = allIncidents.value ?: return
        val query = _searchQuery.value ?: ""
        val status = _statusFilter.value ?: "All"

        var result = incidents
        if (status != "All") result = result.filter { it.status == status }
        if (query.isNotEmpty()) {
            result = result.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.location.contains(query, ignoreCase = true) ||
                    it.type.contains(query, ignoreCase = true)
            }
        }
        _filteredIncidents.value = result
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStatusFilter(status: String) { _statusFilter.value = status }

    fun submitReport(draft: ReportDraft) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val context = getApplication<Application>().applicationContext
                val name = PrefsHelper.getUserName(context)
                _submitResult.value = repository.submitReport(draft, name)
            } catch (e: Exception) {
                Log.w(TAG, "Submit failed: ${e.message}")
                _errorMessage.value = "Could not send the report: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearSubmitResult() { _submitResult.value = null }
    fun getIncidentById(id: String): LiveData<Incident?> = repository.observeIncident(id)
    fun clearError() { _errorMessage.value = null }

    // --- Chat Methods ---
    fun addChatMessage(text: String, fromUser: Boolean) {
        val current = _chatHistory.value.orEmpty().toMutableList()
        val time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
        current.add(ChatMessage(text, fromUser, time))
        _chatHistory.value = current
    }

    fun clearChat() { _chatHistory.value = emptyList() }
}
