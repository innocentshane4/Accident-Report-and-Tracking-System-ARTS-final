package com.example.arts.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.arts.data.AppAlert
import com.example.arts.repository.AlertRepository
import kotlinx.coroutines.launch

/**
 * The Alerts screen: the signed-in person's messages, updating live.
 *
 * FIX: Because AlertRepository.allAlerts and unreadCount are now computed
 * properties (not vals set at construction time), we must observe them through
 * a stable LiveData reference. We re-fetch the LiveData from the repository
 * on each access so the ViewModel always points at the current user's data.
 */
class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = AlertRepository()

    val allNotifications: LiveData<List<AppAlert>>
        get() = repository.allAlerts

    val unreadCount: LiveData<Int>
        get() = repository.unreadCount

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun markAsRead(id: String) {
        viewModelScope.launch { runCatching { repository.markAsRead(id) } }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            runCatching { repository.markAllAsRead(allNotifications.value.orEmpty()) }
        }
    }

    fun deleteNotification(alert: AppAlert) {
        viewModelScope.launch { runCatching { repository.delete(alert.id) } }
    }

    fun addNotification(title: String, message: String, type: String) {
        viewModelScope.launch { runCatching { repository.add(title, message, type) } }
    }
}
