package com.example.f1standings.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.f1standings.data.F1Repository
import com.example.f1standings.data.ScheduleResponse
import com.example.f1standings.data.WidgetState
import com.example.f1standings.data.HistoricalSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class F1ViewModel(private val repository: F1Repository) : ViewModel() {
    
    val baseUrl: StateFlow<String> = repository.baseUrl

    private val _widgetState = MutableStateFlow<WidgetState?>(null)
    val widgetState = _widgetState.asStateFlow()

    private val _schedule = MutableStateFlow<ScheduleResponse?>(null)
    val schedule = _schedule.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    private val _historicalSessions = MutableStateFlow<List<HistoricalSession>>(emptyList())
    val historicalSessions = _historicalSessions.asStateFlow()

    init {
        fetchSchedule()
        startPolling()
        fetchHistoricalSessions()
    }

    fun updateUrl(newUrl: String) {
        repository.updateBaseUrl(newUrl)
        refreshData()
    }

    fun refreshData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _error.value = null
            try {
                val data = repository.getWidgetData()
                _widgetState.value = data
            } catch (e: Exception) {
                _error.value = "Failed to load standings: ${e.localizedMessage}"
            }
            try {
                val sched = repository.getSchedule()
                _schedule.value = sched
            } catch (e: Exception) {
                // Ignore schedule error
            }
            _isRefreshing.value = false
        }
    }

    private fun fetchSchedule() {
        viewModelScope.launch {
            try {
                _schedule.value = repository.getSchedule()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val data = repository.getWidgetData()
                    _widgetState.value = data
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Connection error: ${e.localizedMessage}"
                }
                delay(10000)
            }
        }
    }

    fun startSimulation(sessionKey: Int, speed: Double) {
        viewModelScope.launch {
            try {
                repository.startSimulation(sessionKey, speed)
                delay(1000)
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to start simulation: ${e.localizedMessage}"
            }
        }
    }

    fun startLive(sessionKey: String) {
        viewModelScope.launch {
            try {
                repository.startLive(sessionKey)
                delay(1000)
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to start live: ${e.localizedMessage}"
            }
        }
    }

    fun fetchHistoricalSessions() {
        viewModelScope.launch {
            try {
                _historicalSessions.value = repository.getHistoricalSessions()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    fun pauseSimulation() {
        viewModelScope.launch {
            try {
                repository.pauseSimulation()
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to pause: ${e.localizedMessage}"
            }
        }
    }

    fun resumeSimulation() {
        viewModelScope.launch {
            try {
                repository.resumeSimulation()
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to resume: ${e.localizedMessage}"
            }
        }
    }

    fun seekSimulation(offsetSeconds: Int) {
        viewModelScope.launch {
            try {
                repository.seekSimulation(offsetSeconds)
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to seek: ${e.localizedMessage}"
            }
        }
    }

    fun changeSpeed(speed: Double) {
        viewModelScope.launch {
            try {
                repository.changeSpeed(speed)
                refreshData()
            } catch (e: Exception) {
                _error.value = "Failed to change speed: ${e.localizedMessage}"
            }
        }
    }
}
