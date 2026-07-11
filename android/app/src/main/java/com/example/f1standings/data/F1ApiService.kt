package com.example.f1standings.data

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface F1ApiService {
    @GET("api/widget")
    suspend fun getWidgetData(): WidgetState

    @GET("api/schedule")
    suspend fun getSchedule(): ScheduleResponse

    @POST("api/simulation/start")
    suspend fun startSimulation(@Body body: SimulationStartRequest): Map<String, String>

    @POST("api/live/start")
    suspend fun startLive(@Body body: LiveStartRequest): Map<String, String>

    @GET("api/simulation/sessions")
    suspend fun getHistoricalSessions(): List<HistoricalSession>

    @POST("api/simulation/pause")
    suspend fun pauseSimulation(): Map<String, String>

    @POST("api/simulation/resume")
    suspend fun resumeSimulation(): Map<String, String>

    @POST("api/simulation/seek")
    suspend fun seekSimulation(@Body body: SeekRequest): Map<String, String>

    @POST("api/simulation/speed")
    suspend fun changeSpeed(@Body body: SpeedRequest): Map<String, String>
}

data class SeekRequest(val offset_seconds: Int)
data class SpeedRequest(val speed: Double)

data class SimulationStartRequest(
    val session_key: Int,
    val speed: Double = 1.0
)

data class LiveStartRequest(
    val session_key: String = "latest"
)
