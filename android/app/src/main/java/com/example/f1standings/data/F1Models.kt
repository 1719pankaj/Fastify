package com.example.f1standings.data

import com.google.gson.annotations.SerializedName

data class SessionInfo(
    val name: String,
    val status: String,
    @SerializedName("virtual_time") val virtualTime: String?,
    val flag: String,
    @SerializedName("date_start") val dateStart: String?,
    @SerializedName("date_end") val dateEnd: String?,
    val speed: Double?,
    val paused: Boolean?
)

data class HistoricalSession(
    @SerializedName("session_key") val sessionKey: Int,
    @SerializedName("session_name") val sessionName: String,
    @SerializedName("session_type") val sessionType: String,
    val location: String,
    @SerializedName("country_name") val countryName: String,
    @SerializedName("date_start") val dateStart: String,
    @SerializedName("date_end") val dateEnd: String
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
    @SerializedName("tyre_age") val tyreAge: Int,
    @SerializedName("gap_to_leader") val gapToLeader: String?,
    @SerializedName("interval") val interval: String?
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
