package com.example.f1standings.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class F1Repository(context: Context) {
    private val sharedPrefs = context.getSharedPreferences("f1_prefs", Context.MODE_PRIVATE)
    
    private val _baseUrl = MutableStateFlow(
        sharedPrefs.getString("backend_url", "https://judicial-hampshire-sox-depot.trycloudflare.com/") ?: "https://judicial-hampshire-sox-depot.trycloudflare.com/"
    )
    val baseUrl: StateFlow<String> = _baseUrl.asStateFlow()

    @Volatile
    private var apiService: F1ApiService = createApiService(_baseUrl.value)

    private fun createApiService(url: String): F1ApiService {
        val formattedUrl = if (url.endsWith("/")) url else "$url/"
        return try {
            Retrofit.Builder()
                .baseUrl(formattedUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(F1ApiService::class.java)
        } catch (e: Exception) {
            Log.e("F1Repository", "Error creating Retrofit with URL: $formattedUrl", e)
            Retrofit.Builder()
                .baseUrl("https://judicial-hampshire-sox-depot.trycloudflare.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(F1ApiService::class.java)
        }
    }

    @Synchronized
    fun updateBaseUrl(newUrl: String) {
        var sanitized = newUrl.trim()
        if (sanitized.isEmpty()) {
            sanitized = "https://judicial-hampshire-sox-depot.trycloudflare.com/"
        } else if (!sanitized.startsWith("http://") && !sanitized.startsWith("https://")) {
            sanitized = "http://$sanitized"
        }
        val withSlash = if (sanitized.endsWith("/")) sanitized else "$sanitized/"
        
        sharedPrefs.edit().putString("backend_url", withSlash).apply()
        _baseUrl.value = withSlash
        apiService = createApiService(withSlash)
    }

    suspend fun getWidgetData(): WidgetState {
        return apiService.getWidgetData()
    }

    suspend fun getSchedule(): ScheduleResponse {
        return apiService.getSchedule()
    }

    suspend fun startSimulation(sessionKey: Int, speed: Double): Map<String, String> {
        return apiService.startSimulation(SimulationStartRequest(sessionKey, speed))
    }

    suspend fun startLive(sessionKey: String): Map<String, String> {
        return apiService.startLive(LiveStartRequest(sessionKey))
    }
}
