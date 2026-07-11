package com.example.f1standings.data

import com.google.gson.annotations.SerializedName

data class SessionInfo(
    val name: String,
    val status: String,
    @SerializedName("virtual_time") val virtualTime: String?,
    val flag: String
)

data class DriverStanding(
    @SerializedName("driver_number") val driverNumber: Int,
    @SerializedName("name_acronym") val nameAcronym: String,
    @SerializedName("full_name") val fullName: String,
    @SerializedName("team_name") val teamName: String,
    @SerializedName("team_colour") val teamColour: String,
    val position: Int?,
    @SerializedName("laps_completed") val lapsCompleted: Int,
    @SerializedName("last_lap_time") val lastLapTime: Double?,
    @SerializedName("best_lap_time") val bestLapTime: Double?,
    @SerializedName("tyre_compound") val tyreCompound: String?,
    @SerializedName("tyre_age") val tyreAge: Int
)

data class WidgetState(
    val session: SessionInfo?,
    val standings: List<DriverStanding>?,
    @SerializedName("race_control") val raceControl: List<String>?
)

data class ScheduledSession(
    @SerializedName("session_name") val sessionName: String,
    @SerializedName("date_start") val dateStart: String
)

data class ScheduleResponse(
    val status: String,
    @SerializedName("next_session") val nextSession: ScheduledSession?
)
