package com.example.f1standings.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.f1standings.data.DriverStanding
import com.example.f1standings.data.F1Repository
import com.example.f1standings.data.WidgetState
import com.example.f1standings.data.ScheduleResponse
import com.example.f1standings.data.ScheduledSession
import com.example.f1standings.data.HistoricalSession
import com.example.f1standings.data.SessionInfo
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val viewModel: F1ViewModel = viewModel {
        F1ViewModel(F1Repository(context))
    }
    val widgetState by viewModel.widgetState.collectAsStateWithLifecycle()
    val schedule by viewModel.schedule.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val historicalSessions by viewModel.historicalSessions.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var showReplayPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "F1 LIVE DASHBOARD",
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF15151F)
                )
            )
        },
        containerColor = Color(0xFF0F0F14)
    ) { paddingValues ->
        Column(
            modifier = modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            // Track status banner
            TrackStatusBanner(widgetState = widgetState)

            // Replay Controls (Only shown during simulation mode)
            if (widgetState?.session?.status == "simulation") {
                ReplayControlPanel(
                    sessionInfo = widgetState?.session,
                    onPlayPauseToggle = {
                        val isPaused = widgetState?.session?.paused == true
                        if (isPaused) viewModel.resumeSimulation() else viewModel.pauseSimulation()
                    },
                    onSeek = { offset ->
                        viewModel.seekSimulation(offset)
                    },
                    onSpeedChange = { speed ->
                        viewModel.changeSpeed(speed)
                    }
                )
            }

            // Connection Error Banner
            AnimatedVisibility(visible = error != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Main Standings List or Next Session Info
            Box(modifier = Modifier.weight(1f)) {
                if (widgetState == null && isRefreshing) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFE10600))
                    }
                } else if (widgetState == null || widgetState?.standings.isNullOrEmpty()) {
                    // Show next session info if available
                    NoActiveSessionScreen(
                        schedule = schedule,
                        baseUrl = baseUrl,
                        onSettingsClick = { showSettings = true },
                        onReplayClick = {
                            viewModel.fetchHistoricalSessions()
                            showReplayPicker = true
                        }
                    )
                } else {
                    StandingsList(widgetState!!)
                }
            }

            // Race control logs ticker at bottom
            widgetState?.raceControl?.let { logs ->
                if (logs.isNotEmpty()) {
                    RaceControlTicker(logs = logs)
                }
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            currentUrl = baseUrl,
            onDismiss = { showSettings = false },
            onSave = { newUrl ->
                viewModel.updateUrl(newUrl)
                showSettings = false
            },
            onStartSimulation = { session, speed ->
                viewModel.startSimulation(session, speed)
                showSettings = false
            },
            onStartLive = { session ->
                viewModel.startLive(session)
                showSettings = false
            }
        )
    }

    if (showReplayPicker) {
        HistoricalSessionPickerDialog(
            sessions = historicalSessions,
            onDismiss = { showReplayPicker = false },
            onSelectSession = { sessionKey ->
                viewModel.startSimulation(sessionKey, 1.0)
                showReplayPicker = false
            }
        )
    }
}

@Composable
fun TrackStatusBanner(widgetState: WidgetState?) {
    val flag = widgetState?.session?.flag ?: "UNKNOWN"
    val sessionName = widgetState?.session?.name ?: "No Active Session"
    val status = widgetState?.session?.status ?: "idle"

    val (bg, fg) = when (flag.uppercase()) {
        "GREEN" -> Color(0xFF388E3C) to Color.White
        "YELLOW", "DOUBLE YELLOW" -> Color(0xFFFBC02D) to Color.Black
        "RED" -> Color(0xFFD32F2F) to Color.White
        "BLUE" -> Color(0xFF1976D2) to Color.White
        "SAFETY CAR" -> Color(0xFFE65100) to Color.White
        "VIRTUAL SAFETY CAR" -> Color(0xFFFF6D00) to Color.White
        "CHECKERED" -> Color(0xFF37474F) to Color.White
        else -> Color(0xFF1E1E28) to Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "TRACK STATUS: $flag",
                fontWeight = FontWeight.Bold,
                color = fg,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = status.uppercase(),
                fontSize = 11.sp,
                color = fg.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .border(1.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = sessionName,
                color = fg,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            widgetState?.session?.virtualTime?.let { vt ->
                // Clean ISO string timestamp representation e.g. "15:51:19"
                val timePart = try {
                    vt.substringAfter("T").substringBefore(".")
                } catch (e: Exception) {
                    ""
                }
                if (timePart.isNotEmpty()) {
                    Text(
                        text = "VIRTUAL TIME: $timePart",
                        color = fg.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StandingsList(state: WidgetState) {
    val standings = state.standings ?: emptyList()
    
    // Determine overall fastest lap time
    val overallFastest = standings.filter { it.bestLapTime != null && it.bestLapTime > 0.0 }
        .minOfOrNull { it.bestLapTime!! }

    LazyColumn(
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(standings, key = { it.driverNumber }) { driver ->
            val isFastest = driver.bestLapTime != null && driver.bestLapTime == overallFastest
            DriverRow(driver = driver, isFastestLap = isFastest)
        }
    }
}

@Composable
fun DriverRow(driver: DriverStanding, isFastestLap: Boolean) {
    val parsedColor = remember(driver.teamColour) {
        try {
            Color(android.graphics.Color.parseColor("#${driver.teamColour}"))
        } catch (e: Exception) {
            Color.Gray
        }
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth()
        ) {
            // Team color bar indicator
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(6.dp)
                    .background(parsedColor)
            )

            Row(
                modifier = Modifier
                    .padding(10.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Position and Driver Number
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(36.dp)
                ) {
                    Text(
                        text = (driver.position ?: "--").toString(),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        color = Color.White
                    )
                    Text(
                        text = "#${driver.driverNumber}",
                        fontSize = 10.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Driver details
                Column(modifier = Modifier.weight(1.3f)) {
                    Text(
                        text = driver.nameAcronym,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = driver.fullName,
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = driver.teamName,
                        fontSize = 10.sp,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tyre Information
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(0.9f)
                ) {
                    TyreCompoundBadge(driver.tyreCompound)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${driver.tyreAge} laps old",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }

                // Laps & Timings
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.weight(1.3f)
                ) {
                    Text(
                        text = "Laps: ${driver.lapsCompleted}",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "LAST: ${formatLapTime(driver.lastLapTime)}",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "BEST: ${formatLapTime(driver.bestLapTime)}",
                        fontSize = 11.sp,
                        color = if (isFastestLap) Color(0xFFD087F2) else Color.Gray,
                        fontWeight = if (isFastestLap) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun TyreCompoundBadge(compound: String?) {
    val display = when (compound?.uppercase()) {
        "SOFT" -> "S" to Color(0xFFE10600)
        "MEDIUM" -> "M" to Color(0xFFFFD700)
        "HARD" -> "H" to Color(0xFFFFFFFF)
        "INTERMEDIATE" -> "I" to Color(0xFF39AD48)
        "WET" -> "W" to Color(0xFF0090FF)
        else -> "?" to Color.Gray
    }

    val isDark = display.first == "H" || display.first == "M"
    val txtColor = if (isDark) Color.Black else Color.White

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(display.second)
    ) {
        Text(
            text = display.first,
            color = txtColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun RaceControlTicker(logs: List<String>) {
    Card(
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F)),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.DarkGray, RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "RACE CONTROL MESSAGES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Red,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Reverse list to show newest on top
            val newestLogs = logs.asReversed()
            newestLogs.take(3).forEach { message ->
                Text(
                    text = "• $message",
                    fontSize = 10.sp,
                    color = Color.LightGray,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(vertical = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun NoActiveSessionScreen(
    schedule: ScheduleResponse?,
    baseUrl: String,
    onSettingsClick: () -> Unit,
    onReplayClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "🏎️",
            fontSize = 64.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Active Session",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The backend is currently idle. Connect or start a simulation in the settings panel.",
            color = Color.Gray,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        schedule?.nextSession?.let { ns ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "NEXT UPCOMING SESSION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE10600),
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = ns.sessionName,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = ns.dateStart.substringBefore("+").replace("T", " "),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSettingsClick,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE10600)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open App Settings", color = Color.White)
        }
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedButton(
            onClick = onReplayClick,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
            border = BorderStroke(1.dp, Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Replay a Past Race", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Currently connected to:\n$baseUrl",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun SettingsDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onStartSimulation: (Int, Double) -> Unit,
    onStartLive: (String) -> Unit
) {
    var urlInput by remember { mutableStateOf(currentUrl) }
    var sessionKeyInput by remember { mutableStateOf("9472") }
    var speedInput by remember { mutableStateOf("10.0") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "SETTINGS & CONTROL",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Base URL Section
                Text(
                    text = "Backend Base URL:",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFE10600),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { onSave(urlInput) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE10600)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save & Apply URL", color = Color.White)
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp), color = Color.DarkGray)

                // Developer Session Simulation Trigger Section
                Text(
                    text = "Trigger Simulation (9472 = Bahrain 2024):",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = sessionKeyInput,
                        onValueChange = { sessionKeyInput = it },
                        label = { Text("Session Key", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = speedInput,
                        onValueChange = { speedInput = it },
                        label = { Text("Speed (x)", color = Color.Gray) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 4.dp),
                        singleLine = true
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        val session = sessionKeyInput.toIntOrNull() ?: 9472
                        val speed = speedInput.toDoubleOrNull() ?: 10.0
                        onStartSimulation(session, speed)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Simulation", color = Color.White)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", color = Color.LightGray)
                    }
                }
            }
        }
    }
}

// Utility to format lap times in seconds to F1 display style e.g. "1:35.771"
fun formatLapTime(seconds: Double?): String {
    if (seconds == null || seconds <= 0.0) return "--.---"
    val mins = (seconds / 60).toInt()
    val secs = seconds % 60
    return if (mins > 0) {
        String.format("%d:%06.3f", mins, secs)
    } else {
        String.format("%.3f", secs)
    }
}

@Composable
fun ReplayControlPanel(
    sessionInfo: SessionInfo?,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Int) -> Unit,
    onSpeedChange: (Double) -> Unit
) {
    if (sessionInfo == null) return

    val dateStartStr = sessionInfo.dateStart ?: ""
    val dateEndStr = sessionInfo.dateEnd ?: ""
    val virtualTimeStr = sessionInfo.virtualTime ?: ""
    val isPaused = sessionInfo.paused == true
    val currentSpeed = sessionInfo.speed ?: 1.0

    // Parse ISO-8601 helper
    fun parseIso(isoStr: String): Long {
        if (isoStr.isEmpty()) return 0L
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.OffsetDateTime.parse(isoStr).toEpochSecond()
            } else {
                val clean = isoStr.replace("Z", "+00:00")
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                (sdf.parse(clean)?.time ?: 0L) / 1000
            }
        } catch (e: Exception) {
            0L
        }
    }

    val startTime = remember(dateStartStr) { parseIso(dateStartStr) }
    val endTime = remember(dateEndStr) { parseIso(dateEndStr) }
    val virtualTime = remember(virtualTimeStr) { parseIso(virtualTimeStr) }

    val totalDuration = (endTime - startTime).coerceAtLeast(0L)
    val elapsed = (virtualTime - startTime).coerceAtLeast(0L).coerceAtMost(totalDuration)

    // Local slider state
    var sliderPosition by remember { mutableStateOf(elapsed.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }

    LaunchedEffect(elapsed) {
        if (!isScrubbing) {
            sliderPosition = elapsed.toFloat()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Card(
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C24)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Seekbar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = formatDuration(sliderPosition.toLong()),
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(55.dp)
                    )

                    Slider(
                        value = sliderPosition,
                        onValueChange = {
                            isScrubbing = true
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            isScrubbing = false
                            onSeek(sliderPosition.toInt())
                        },
                        valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color(0xFFE10600),
                            inactiveTrackColor = Color.DarkGray,
                            thumbColor = Color(0xFFE10600)
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    Text(
                        text = formatDuration(totalDuration),
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(55.dp),
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Playback controls row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Play / Pause button
                    Button(
                        onClick = onPlayPauseToggle,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPaused) Color(0xFF388E3C) else Color(0xFFE10600)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = if (isPaused) "PLAY" else "PAUSE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Speed controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SPEED: ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace
                        )
                        listOf(1.0, 2.0, 5.0, 10.0, 20.0).forEach { speedVal ->
                            val isSelected = currentSpeed == speedVal
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) Color(0xFFE10600) else Color(0xFF2C2C35))
                                    .clickable { onSpeedChange(speedVal) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${speedVal.toInt()}x",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) Color.White else Color.LightGray,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(color = Color.DarkGray, thickness = 1.dp)
    }
}

@Composable
fun HistoricalSessionPickerDialog(
    sessions: List<HistoricalSession>,
    onDismiss: () -> Unit,
    onSelectSession: (Int) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E28)),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Text(
                    text = "REPLAY A PAST RACE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (sessions.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No completed races found.",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sessions) { session ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C35)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSession(session.sessionKey) }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = session.sessionName,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            text = session.sessionType.uppercase(),
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFFE10600),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${session.location}, ${session.countryName}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp
                                    )
                                    val date = try {
                                        session.dateStart.substringBefore("T")
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    if (date.isNotEmpty()) {
                                        Text(
                                            text = "Date: $date",
                                            color = Color.Gray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.LightGray)
                    }
                }
            }
        }
    }
}

fun formatDuration(totalSeconds: Long): String {
    val hrs = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val secs = totalSeconds % 60
    return if (hrs > 0) {
        String.format("%d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
