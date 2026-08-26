package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HourglassFull
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Alarm
import com.example.viewmodel.AlarmViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.delay

enum class ClockTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    ALARM("Alarm", Icons.Filled.Alarm, Icons.Outlined.Alarm),
    WORLD_CLOCK("Clock", Icons.Filled.Schedule, Icons.Outlined.Schedule),
    TIMER("Timer", Icons.Filled.HourglassFull, Icons.Outlined.HourglassEmpty),
    STOPWATCH("Stopwatch", Icons.Filled.Timer, Icons.Outlined.Timer)
}

data class WorldCity(val id: String, val name: String, val country: String, val offset: Double)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAlarmScreen(
    viewModel: AlarmViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val alarmsList by viewModel.alarms.collectAsState()
    val securitySetting by viewModel.securitySetting.collectAsState()

    var activeTab by rememberSaveable { mutableStateOf(ClockTab.ALARM) }

    // Alarm States
    var showCreateDialog by remember { mutableStateOf(false) }
    var showPasscodeDialog by remember { mutableStateOf(false) }
    var alarmToEdit by remember { mutableStateOf<Alarm?>(null) }
    var selectedToneUriStr by remember { mutableStateOf<String?>(null) }
    var selectedToneName by remember { mutableStateOf("Default System Device Tone") }

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI) as? Uri
            }
            if (uri != null) {
                selectedToneUriStr = uri.toString()
                try {
                    val ringtone = RingtoneManager.getRingtone(context, uri)
                    selectedToneName = ringtone?.getTitle(context) ?: "System Ringtone"
                } catch (e: Exception) {
                    selectedToneName = "System Ringtone"
                }
            }
        }
    }

    val localFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            selectedToneUriStr = uri.toString()
            selectedToneName = uri.lastPathSegment ?: "Local Audio File"
        }
    }

    // Dynamic real-time clock state for local time
    var currentTimeString by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            val sdf = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())
            currentTimeString = sdf.format(Date())
            delay(1000)
        }
    }

    // World Clock List States
    val availableCities = remember {
        listOf(
            WorldCity("tok", "Tokyo", "Japan", 9.0),
            WorldCity("lon", "London", "United Kingdom", 1.0),
            WorldCity("ny", "New York", "United States", -4.0),
            WorldCity("par", "Paris", "France", 2.0),
            WorldCity("syd", "Sydney", "Australia", 10.0),
            WorldCity("la", "Los Angeles", "United States", -7.0),
            WorldCity("dxb", "Dubai", "United Arab Emirates", 4.0),
            WorldCity("sin", "Singapore", "Singapore", 8.0),
            WorldCity("bom", "Mumbai", "India", 5.5),
            WorldCity("cai", "Cairo", "Egypt", 3.0)
        )
    }
    val pinnedCityIds = remember { mutableStateListOf("tok", "lon", "ny") }
    var showAddCityDialog by remember { mutableStateOf(false) }

    // Timer States
    var timerInputHours by remember { mutableIntStateOf(0) }
    var timerInputMinutes by remember { mutableIntStateOf(5) }
    var timerInputSeconds by remember { mutableIntStateOf(0) }
    var timerInputDigits by rememberSaveable { mutableStateOf("") }
    var timerRemainingMillis by remember { mutableLongStateOf(0L) }
    var timerTotalMillis by remember { mutableLongStateOf(0L) }
    var isTimerRunning by remember { mutableStateOf(false) }

    // Audio beep trigger when Timer hits indeed zero
    var isTimerAlertActive by remember { mutableStateOf(false) }

    LaunchedEffect(isTimerRunning, timerRemainingMillis) {
        if (isTimerRunning && timerRemainingMillis > 0) {
            delay(100)
            timerRemainingMillis = (timerRemainingMillis - 100).coerceAtLeast(0)
            if (timerRemainingMillis == 0L) {
                isTimerRunning = false
                isTimerAlertActive = true
            }
        }
    }

    // Stopwatch States
    var stopwatchTime by remember { mutableLongStateOf(0L) }
    var isStopwatchRunning by remember { mutableStateOf(false) }
    val stopwatchLaps = remember { mutableStateListOf<Long>() }

    LaunchedEffect(isStopwatchRunning) {
        var lastTime = System.currentTimeMillis()
        while (isStopwatchRunning) {
            delay(10)
            val now = System.currentTimeMillis()
            stopwatchTime += (now - lastTime)
            lastTime = now
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Lock",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Text(
                            text = "MyAlaram",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                ClockTab.values().forEach { tab ->
                    val isSelected = activeTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { activeTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        },
                        alwaysShowLabel = true,
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag("tab_item_${tab.name.lowercase()}")
                    )
                }
            }
        },
        floatingActionButton = {
            if (activeTab == ClockTab.ALARM) {
                FloatingActionButton(
                    onClick = {
                        selectedToneUriStr = null
                        selectedToneName = "Default System Device Tone"
                        showCreateDialog = true
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.testTag("add_alarm_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add Alarm")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                ClockTab.ALARM -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Clock Display Card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            Color(0xFF381E72)
                                        )
                                    )
                                )
                                .padding(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.Start
                            ) {
                                Text(
                                    text = "CURRENT TIME",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.5.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentTimeString.ifBlank { "00:00:00 --" },
                                    color = Color.White,
                                    fontSize = 36.sp,
                                    fontWeight = FontWeight.Light,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Security PIN Card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { showPasscodeDialog = true }
                                .testTag("personalize_passcode_card"),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "PIN Lock",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "SECURITY PIN",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Change 10-Digit PIN",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "Requires previous PIN to update",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Text(
                                    text = "Change ›",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Alarms List Section
                        Text(
                            text = "ALARM SCHEDULES",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                            letterSpacing = 1.sp
                        )

                        if (alarmsList.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "⏰",
                                        fontSize = 48.sp,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    Text(
                                        text = "No Alarms Scheduled",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Press the floating button to configure an wake-alert.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(bottom = 80.dp)
                            ) {
                                items(alarmsList, key = { it.id }) { alarm ->
                                    AlarmItemRow(
                                        alarm = alarm,
                                        onToggle = { viewModel.toggleAlarm(context, alarm) },
                                        onDelete = { viewModel.deleteAlarm(context, alarm) },
                                        onEditClick = {
                                            val resolvedToneName = if (alarm.toneUri != null) {
                                                try {
                                                    val uri = Uri.parse(alarm.toneUri)
                                                    if (uri.scheme == "content") {
                                                        val ringtone = RingtoneManager.getRingtone(context, uri)
                                                        ringtone?.getTitle(context) ?: "Custom System Sound"
                                                    } else {
                                                        uri.lastPathSegment ?: "Custom Audio File"
                                                    }
                                                } catch (e: Exception) {
                                                    "Custom Sound"
                                                }
                                            } else {
                                                "Default System Device Tone"
                                            }
                                            selectedToneUriStr = alarm.toneUri
                                            selectedToneName = resolvedToneName
                                            alarmToEdit = alarm
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                ClockTab.WORLD_CLOCK -> {
                    // World Clock Content Panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                    ) {
                        // Giant local clock card with detailed information
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Your Home Location Time",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentTimeString.ifBlank { "00:00:00 --" },
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "System Default Timezone Offset: GMT ${String.format("%+d", TimeZone.getDefault().rawOffset / 3600000)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "MONITORED CITY CLOCKS",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                fontWeight = FontWeight.Bold
                            )
                            Button(
                                onClick = { showAddCityDialog = true },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("+ Add City", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (pinnedCityIds.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No pinned clocks. Click '+ Add City' to add some!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                items(pinnedCityIds) { cityId ->
                                    val city = availableCities.firstOrNull { it.id == cityId }
                                    if (city != null) {
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                            ),
                                            shape = RoundedCornerShape(16.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = city.name,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = "${city.country} (GMT ${if (city.offset >= 0) "+" else ""}${city.offset})",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                ) {
                                                    Text(
                                                        text = getCityTime(city.offset),
                                                        style = MaterialTheme.typography.titleLarge,
                                                        fontWeight = FontWeight.Black,
                                                        fontFamily = FontFamily.Monospace,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )

                                                    IconButton(onClick = { pinnedCityIds.remove(cityId) }) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Remove City",
                                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                ClockTab.TIMER -> {
                    // Timer Visual Countdown Setup / active state
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (timerRemainingMillis > 0 || isTimerRunning) {
                            // Active Ticking Screen
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(240.dp)
                                ) {
                                    val progress = if (timerTotalMillis > 0) {
                                        timerRemainingMillis.toFloat() / timerTotalMillis
                                    } else {
                                        0f
                                    }
                                    CircularProgressIndicator(
                                        progress = progress,
                                        strokeWidth = 8.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(230.dp)
                                    )

                                    val hours = (timerRemainingMillis / 3600000).toInt()
                                    val minutes = ((timerRemainingMillis % 3600000) / 60000).toInt()
                                    val seconds = ((timerRemainingMillis % 60000) / 1000).toInt()

                                    Text(
                                        text = String.format("%02d:%02d:%02d", hours, minutes, seconds),
                                        style = MaterialTheme.typography.displayMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // +1 minute additions button (classic Google Clock helper)
                                    Button(
                                        onClick = {
                                            timerRemainingMillis += 60000L
                                            timerTotalMillis += 60000L
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                                        )
                                    ) {
                                        Text("+1 Min", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }

                                    // Pause/Resume button
                                    Button(
                                        onClick = { isTimerRunning = !isTimerRunning }
                                    ) {
                                        Text(if (isTimerRunning) "Pause" else "Resume")
                                    }

                                    // Reset/Cancel button
                                    OutlinedButton(
                                        onClick = {
                                            isTimerRunning = false
                                            timerRemainingMillis = 0L
                                            isTimerAlertActive = false
                                        }
                                    ) {
                                        Text("Stop")
                                    }
                                }
                            }
                        } else {
                            // Professional Keypad duration selection format
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "SET COUNTDOWN TIMER DURATION",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(16.dp))

                                val digits = timerInputDigits.padStart(6, '0')
                                val hoursStr = digits.substring(0, 2)
                                val minutesStr = digits.substring(2, 4)
                                val secondsStr = digits.substring(4, 6)

                                val hasHours = hoursStr.toInt() > 0
                                val hasMinutes = minutesStr.toInt() > 0
                                val hasSeconds = secondsStr.toInt() > 0

                                Row(
                                    verticalAlignment = Alignment.Bottom,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp)
                                ) {
                                    Text(
                                        text = hoursStr,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasHours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                                    Text(
                                        text = "h  ",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasHours) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Text(
                                        text = minutesStr,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasHours || hasMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                                    Text(
                                        text = "m  ",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasHours || hasMinutes) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Text(
                                        text = secondsStr,
                                        fontSize = 44.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (hasHours || hasMinutes || hasSeconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
                                    )
                                    Text(
                                        text = "s",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasHours || hasMinutes || hasSeconds) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                }

                                // Quick presets for super fast selection
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(bottom = 12.dp)
                                ) {
                                    listOf(1, 5, 10, 15).forEach { mins ->
                                        SuggestionChip(
                                            onClick = {
                                                timerInputDigits = (mins * 60).toString()
                                            },
                                            label = { Text("${mins} min", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                            modifier = Modifier.testTag("preset_${mins}m")
                                        )
                                    }
                                }

                                // Beautiful compact numbers Grid keypad
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    val keypadRows = listOf(
                                        listOf("1", "2", "3"),
                                        listOf("4", "5", "6"),
                                        listOf("7", "8", "9"),
                                        listOf("CLR", "0", "⌫")
                                    )
                                    
                                    keypadRows.forEach { rowKeys ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            rowKeys.forEach { key ->
                                                val isAction = key == "CLR" || key == "⌫"
                                                val buttonColor = if (isAction) {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                } else {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                }
                                                val textColor = if (isAction) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                }
                                                
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .height(44.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(buttonColor)
                                                        .clickable {
                                                            when (key) {
                                                                "CLR" -> {
                                                                    timerInputDigits = ""
                                                                }
                                                                "⌫" -> {
                                                                    if (timerInputDigits.isNotEmpty()) {
                                                                        timerInputDigits = timerInputDigits.dropLast(1)
                                                                    }
                                                                }
                                                                else -> {
                                                                    if (timerInputDigits.length < 6) {
                                                                        if (timerInputDigits.isEmpty() && key == "0") {
                                                                            // ignore leading zeros
                                                                        } else {
                                                                            timerInputDigits += key
                                                                        }
                                                                    }
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = key,
                                                        fontSize = 16.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = textColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))

                                Button(
                                    onClick = {
                                        val parseDigits = timerInputDigits.padStart(6, '0')
                                        val hours = parseDigits.substring(0, 2).toIntOrNull() ?: 0
                                        val minutes = parseDigits.substring(2, 4).toIntOrNull() ?: 0
                                        val seconds = parseDigits.substring(4, 6).toIntOrNull() ?: 0
                                        val totalSeconds = (hours * 3600) + (minutes * 60) + seconds
                                        if (totalSeconds > 0) {
                                            timerRemainingMillis = totalSeconds * 1000L
                                            timerTotalMillis = totalSeconds * 1000L
                                            isTimerRunning = true
                                            isTimerAlertActive = false
                                        }
                                    },
                                    enabled = timerInputDigits.isNotEmpty(),
                                    shape = RoundedCornerShape(24.dp),
                                    modifier = Modifier
                                        .width(200.dp)
                                        .height(48.dp)
                                        .testTag("start_timer_button")
                                ) {
                                    Text("START COUNTDOWN", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        // Completed Alarm ringing status box
                        if (isTimerAlertActive) {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { isTimerAlertActive = false }
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⏰",
                                        fontSize = 24.sp,
                                        modifier = Modifier.padding(end = 12.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Timer Reached Zero!",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "Tap to dismiss beep alert.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                ClockTab.STOPWATCH -> {
                    // Stopwatch Layout Panel
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // High resolution monospaced timing display
                        val minutes = (stopwatchTime / 60000).toInt()
                        val seconds = ((stopwatchTime % 60000) / 1000).toInt()
                        val centiseconds = ((stopwatchTime % 1000) / 10).toInt()

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = String.format("%02d:%01d%01d.%02d", minutes, seconds / 10, seconds % 10, centiseconds),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag("stopwatch_time_text")
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Controls layout
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Button(
                                onClick = { isStopwatchRunning = !isStopwatchRunning },
                                modifier = Modifier.testTag("stopwatch_toggle_button")
                            ) {
                                Text(if (isStopwatchRunning) "Pause" else "Start")
                            }

                            if (isStopwatchRunning) {
                                Button(
                                    onClick = {
                                        stopwatchLaps.add(0, stopwatchTime)
                                    },
                                    modifier = Modifier.testTag("stopwatch_lap_button"),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                                    )
                                ) {
                                    Text("Lap", color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }

                            Button(
                                onClick = {
                                    isStopwatchRunning = false
                                    stopwatchTime = 0L
                                    stopwatchLaps.clear()
                                },
                                modifier = Modifier.testTag("stopwatch_reset_button"),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text("Reset", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            text = "LAP SPLITS",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.align(Alignment.Start)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        if (stopwatchLaps.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No recorded laps. Tap 'Lap' while active.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f)
                            ) {
                                items(stopwatchLaps.size) { index ->
                                    val lapVal = stopwatchLaps[index]
                                    val lapMinutes = (lapVal / 60000).toInt()
                                    val lapSeconds = ((lapVal % 60000) / 1000).toInt()
                                    val lapCentiseconds = ((lapVal % 1000) / 10).toInt()

                                    // Highlight fastest and slowest if we have > 2 laps
                                    val isFastest = stopwatchLaps.size >= 2 && lapVal == stopwatchLaps.minOrNull()
                                    val isSlowest = stopwatchLaps.size >= 2 && lapVal == stopwatchLaps.maxOrNull()

                                    val itemColor = when {
                                        isFastest -> Color(0xFF81C784)
                                        isSlowest -> Color(0xFFE57373)
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }

                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Lap ${stopwatchLaps.size - index}",
                                                fontWeight = FontWeight.Bold,
                                                color = itemColor
                                            )
                                            Text(
                                                text = String.format("%02d:%02d.%02d", lapMinutes, lapSeconds, lapCentiseconds),
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                                color = itemColor
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: World Clock addition Dialog
    if (showAddCityDialog) {
        AlertDialog(
            onDismissRequest = { showAddCityDialog = false },
            title = {
                Text("Select Pinned timezone", fontWeight = FontWeight.Black)
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(availableCities) { city ->
                        val isPinned = pinnedCityIds.contains(city.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isPinned) {
                                        pinnedCityIds.remove(city.id)
                                    } else {
                                        pinnedCityIds.add(city.id)
                                    }
                                    showAddCityDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = city.name,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = city.country,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            if (isPinned) {
                                Text("✓ Pinned", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            } else {
                                Text("+ Pin", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddCityDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Modal: 10-Digit Passcode Personalization
    if (showPasscodeDialog) {
        var previousPasscode by remember { mutableStateOf("") }
        var newPasscode by remember { mutableStateOf("") }
        var confirmPasscode by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf("") }
        var successMessage by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPasscodeDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Change Security PIN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "To change the alarm deactivation PIN, you must first verify your previous PIN. Without the correct previous PIN, the PIN cannot be changed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 1. Previous PIN Input
                    OutlinedTextField(
                        value = previousPasscode,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                previousPasscode = input
                                errorMessage = ""
                            }
                        },
                        label = { Text("Current / Previous 10-Digit PIN") },
                        placeholder = { Text("e.g. 1234567890") },
                        supportingText = {
                            Text(text = "${previousPasscode.length}/10 digits entered")
                        },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty() && previousPasscode.length < 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("previous_passcode_input")
                    )

                    // 2. New PIN Input
                    OutlinedTextField(
                        value = newPasscode,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                newPasscode = input
                                errorMessage = ""
                            }
                        },
                        label = { Text("New 10-Digit PIN") },
                        placeholder = { Text("Enter 10 digits") },
                        supportingText = {
                            Text(text = "${newPasscode.length}/10 digits entered")
                        },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty() && newPasscode.length < 10,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("passcode_setup_input")
                    )

                    // 3. Confirm New PIN Input
                    OutlinedTextField(
                        value = confirmPasscode,
                        onValueChange = { input ->
                            if (input.length <= 10 && input.all { it.isDigit() }) {
                                confirmPasscode = input
                                errorMessage = ""
                            }
                        },
                        label = { Text("Confirm New 10-Digit PIN") },
                        placeholder = { Text("Re-enter 10 digits") },
                        supportingText = {
                            Text(text = "${confirmPasscode.length}/10 digits entered")
                        },
                        singleLine = true,
                        isError = errorMessage.isNotEmpty() && confirmPasscode != newPasscode,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("confirm_passcode_input")
                    )

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (successMessage.isNotEmpty()) {
                        Text(
                            text = successMessage,
                            color = Color(0xFF10B981),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (previousPasscode.length != 10) {
                            errorMessage = "Please enter your full 10-digit previous PIN."
                            return@Button
                        }
                        if (newPasscode.length != 10) {
                            errorMessage = "New PIN must be exactly 10 digits."
                            return@Button
                        }
                        if (newPasscode != confirmPasscode) {
                            errorMessage = "New PIN and Confirmation PIN do not match."
                            return@Button
                        }

                        viewModel.changePasscodeWithPreviousCheck(
                            previousPasscode = previousPasscode,
                            newPasscode = newPasscode,
                            confirmPasscode = confirmPasscode
                        ) { success, msg ->
                            if (success) {
                                successMessage = msg
                                errorMessage = ""
                                previousPasscode = ""
                                newPasscode = ""
                                confirmPasscode = ""
                                showPasscodeDialog = false
                            } else {
                                errorMessage = msg
                                successMessage = ""
                            }
                        }
                    },
                    modifier = Modifier.testTag("passcode_save_confirm")
                ) {
                    Text("Change PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasscodeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Schedule Alarm setup Dialog
    if (showCreateDialog) {
        val timePickerState = rememberTimePickerState(
            initialHour = 7,
            initialMinute = 30,
            is24Hour = false
        )
        var isDialMode by remember { mutableStateOf(true) }
        var labelInput by remember { mutableStateOf("") }

        val repeatDaysList = remember {
            mutableStateListOf(
                DaySelectorState("Mon", true),
                DaySelectorState("Tue", true),
                DaySelectorState("Wed", true),
                DaySelectorState("Thu", true),
                DaySelectorState("Fri", true),
                DaySelectorState("Sat", false),
                DaySelectorState("Sun", false)
            )
        }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configure Alarm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(
                        onClick = { isDialMode = !isDialMode },
                        modifier = Modifier.testTag("toggle_time_picker_mode_button")
                    ) {
                        Text(
                            text = if (isDialMode) "⌨️" else "⏰",
                            fontSize = 20.sp
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Time Selector
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isDialMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimePicker(
                                        state = timePickerState
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimeInput(
                                        state = timePickerState
                                    )
                                }
                            }

                            val timeRemainingMessage = remember(timePickerState.hour, timePickerState.minute) {
                                val now = Calendar.getInstance()
                                val target = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                if (target.before(now)) {
                                    target.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                val diffMs = target.timeInMillis - now.timeInMillis
                                val diffHours = diffMs / 3600000
                                val diffMinutes = (diffMs % 3600000) / 60000

                                when {
                                    diffHours == 0L && diffMinutes == 0L -> "Alarm sounds in less than a minute"
                                    diffHours == 0L -> "Alarm sounds in $diffMinutes minutes"
                                    diffMinutes == 0L -> if (diffHours == 1L) "Alarm sounds in 1 hour" else "Alarm sounds in $diffHours hours"
                                    else -> {
                                        val hrStr = if (diffHours == 1L) "1 hour" else "$diffHours hours"
                                        val minStr = if (diffMinutes == 1L) "1 minute" else "$diffMinutes minutes"
                                        "Alarm sounds in $hrStr and $minStr"
                                    }
                                }
                            }

                            Text(
                                text = timeRemainingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Label input
                    item {
                        OutlinedTextField(
                            value = labelInput,
                            onValueChange = { labelInput = it },
                            label = { Text("Alarm Label / Quote") },
                            placeholder = { Text("e.g. Focus & Awake!") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alarm_label_input")
                        )
                    }

                    // Days selector
                    item {
                        Column {
                            Text(
                                text = "Weekly Repeats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeatDaysList.forEach { selector ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selector.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                selector.isSelected = !selector.isSelected
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = selector.dayName.take(3),
                                            color = if (selector.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Alarm Tone Selection
                    item {
                        Column {
                            Text(
                                text = "Alarm Sound / Tone",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Selected: $selectedToneName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ElevatedButton(
                                            onClick = {
                                                val ringtonePickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select System Tone")
                                                }
                                                ringtoneLauncher.launch(ringtonePickerIntent)
                                            },
                                            modifier = Modifier.weight(1f).testTag("select_system_tone_button"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("System Tones", fontSize = 11.sp)
                                        }

                                        ElevatedButton(
                                            onClick = {
                                                localFileLauncher.launch("audio/*")
                                            },
                                            modifier = Modifier.weight(1f).testTag("select_local_file_button"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Local Audio", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val repeats = repeatDaysList
                            .filter { it.isSelected }
                            .joinToString(",") { it.dayName }
                            .ifBlank { "Once" }

                        viewModel.addAlarm(
                            context = context,
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            label = labelInput,
                            repeatDays = repeats,
                            toneUri = selectedToneUriStr
                        )
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("alarm_create_confirm")
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Modal: Edit Alarm setup Dialog
    if (alarmToEdit != null) {
        val alarm = alarmToEdit!!
        val timePickerState = rememberTimePickerState(
            initialHour = alarm.hour,
            initialMinute = alarm.minute,
            is24Hour = false
        )
        var isDialMode by remember { mutableStateOf(true) }
        var labelInput by remember { mutableStateOf(alarm.label) }

        // Prepopulate repeat selection states
        val repeatDaysList = remember(alarm) {
            val repeats = alarm.repeatDays.split(",")
            mutableStateListOf(
                DaySelectorState("Mon", "Mon" in repeats),
                DaySelectorState("Tue", "Tue" in repeats),
                DaySelectorState("Wed", "Wed" in repeats),
                DaySelectorState("Thu", "Thu" in repeats),
                DaySelectorState("Fri", "Fri" in repeats),
                DaySelectorState("Sat", "Sat" in repeats),
                DaySelectorState("Sun", "Sun" in repeats)
            )
        }

        AlertDialog(
            onDismissRequest = { alarmToEdit = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Alarm",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    IconButton(
                        onClick = { isDialMode = !isDialMode },
                        modifier = Modifier.testTag("toggle_time_picker_mode_edit_button")
                    ) {
                        Text(
                            text = if (isDialMode) "⌨️" else "⏰",
                            fontSize = 20.sp
                        )
                    }
                }
            },
            text = {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Time Selector
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (isDialMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimePicker(
                                        state = timePickerState
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TimeInput(
                                        state = timePickerState
                                    )
                                }
                            }

                            val timeRemainingMessage = remember(timePickerState.hour, timePickerState.minute) {
                                val now = Calendar.getInstance()
                                val target = Calendar.getInstance().apply {
                                    set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                                    set(Calendar.MINUTE, timePickerState.minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                if (target.before(now)) {
                                    target.add(Calendar.DAY_OF_YEAR, 1)
                                }
                                val diffMs = target.timeInMillis - now.timeInMillis
                                val diffHours = diffMs / 3600000
                                val diffMinutes = (diffMs % 3600000) / 60000

                                when {
                                    diffHours == 0L && diffMinutes == 0L -> "Alarm sounds in less than a minute"
                                    diffHours == 0L -> "Alarm sounds in $diffMinutes minutes"
                                    diffMinutes == 0L -> if (diffHours == 1L) "Alarm sounds in 1 hour" else "Alarm sounds in $diffHours hours"
                                    else -> {
                                        val hrStr = if (diffHours == 1L) "1 hour" else "$diffHours hours"
                                        val minStr = if (diffMinutes == 1L) "1 minute" else "$diffMinutes minutes"
                                        "Alarm sounds in $hrStr and $minStr"
                                    }
                                }
                            }

                            Text(
                                text = timeRemainingMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 8.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Label input
                    item {
                        OutlinedTextField(
                            value = labelInput,
                            onValueChange = { labelInput = it },
                            label = { Text("Alarm Label / Quote") },
                            placeholder = { Text("e.g. Focus & Awake!") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("alarm_edit_label_input")
                        )
                    }

                    // Days selector
                    item {
                        Column {
                            Text(
                                text = "Weekly Repeats",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeatDaysList.forEach { selector ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (selector.isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .clickable {
                                                selector.isSelected = !selector.isSelected
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = selector.dayName.take(3),
                                            color = if (selector.isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Alarm Tone Selection
                    item {
                        Column {
                            Text(
                                text = "Alarm Sound / Tone",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "Selected: $selectedToneName",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        ElevatedButton(
                                            onClick = {
                                                val ringtonePickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALL)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select System Tone")
                                                }
                                                ringtoneLauncher.launch(ringtonePickerIntent)
                                            },
                                            modifier = Modifier.weight(1f).testTag("select_edit_system_tone_button"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("System Tones", fontSize = 11.sp)
                                        }

                                        ElevatedButton(
                                            onClick = {
                                                localFileLauncher.launch("audio/*")
                                            },
                                            modifier = Modifier.weight(1f).testTag("select_edit_local_file_button"),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Local Audio", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val repeats = repeatDaysList
                            .filter { it.isSelected }
                            .joinToString(",") { it.dayName }
                            .ifBlank { "Once" }

                        val updated = alarm.copy(
                            hour = timePickerState.hour,
                            minute = timePickerState.minute,
                            label = labelInput,
                            repeatDays = repeats,
                            toneUri = selectedToneUriStr
                        )

                        viewModel.editAlarm(
                            context = context,
                            alarm = updated
                        )
                        alarmToEdit = null
                    },
                    modifier = Modifier.testTag("alarm_edit_confirm")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { alarmToEdit = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

class DaySelectorState(val dayName: String, val initialSelection: Boolean) {
    var isSelected by mutableStateOf(initialSelection)
}

@Composable
fun NumberBoxSelector(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .size(56.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
    ) {
        IconButton(
            onClick = {
                val next = if (value == range.last) range.first else value + 1
                onValueChange(next)
            },
            modifier = Modifier.size(20.dp)
        ) {
            Text("▲", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }

        Text(
            text = String.format("%02d", value),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        IconButton(
            onClick = {
                val prev = if (value == range.first) range.last else value - 1
                onValueChange(prev)
            },
            modifier = Modifier.size(20.dp)
        ) {
            Text("▼", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun AlarmItemRow(
    alarm: Alarm,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onEditClick() }
            .testTag("alarm_item_${alarm.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (alarm.isEnabled) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
            }
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (alarm.isEnabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Split formattedTime to show AM/PM in smaller superscript
                val timeParts = alarm.formattedTime.split(" ")
                val timeString = timeParts.firstOrNull() ?: ""
                val amPmString = timeParts.getOrNull(1) ?: ""

                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = timeString,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = amPmString,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alarm.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (alarm.isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Beautiful custom weekly days/Once badges
                val isMonSelected = "Mon" in alarm.repeatDays
                val isTueSelected = "Tue" in alarm.repeatDays
                val isWedSelected = "Wed" in alarm.repeatDays
                val isThuSelected = "Thu" in alarm.repeatDays
                val isFriSelected = "Fri" in alarm.repeatDays
                val isSatSelected = "Sat" in alarm.repeatDays
                val isSunSelected = "Sun" in alarm.repeatDays

                if (alarm.repeatDays == "Once") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Once",
                            tint = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Once",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(
                            "M" to isMonSelected,
                            "T" to isTueSelected,
                            "W" to isWedSelected,
                            "T" to isThuSelected,
                            "F" to isFriSelected,
                            "S" to isSatSelected,
                            "S" to isSunSelected
                        ).forEach { (dayInit, isSel) ->
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSel) {
                                            if (alarm.isEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayInit,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSel) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val context = LocalContext.current
                val toneDisplayName = if (alarm.toneUri != null) {
                    remember(alarm.toneUri) {
                        try {
                            val uri = Uri.parse(alarm.toneUri)
                            if (uri.scheme == "content") {
                                val ringtone = RingtoneManager.getRingtone(context, uri)
                                ringtone?.getTitle(context) ?: "Custom System Sound"
                            } else {
                                uri.lastPathSegment ?: "Custom Audio File"
                            }
                        } catch (e: Exception) {
                            "Custom Sound"
                        }
                    }
                } else {
                    "Default Tone"
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🎵 $toneDisplayName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(
                    onClick = { onEditClick() },
                    modifier = Modifier.testTag("alarm_item_edit_${alarm.id}").size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Alarm",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Switch(
                    checked = alarm.isEnabled,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.testTag("alarm_item_switch_${alarm.id}")
                )

                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.testTag("alarm_item_delete_${alarm.id}").size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Alarm",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Helpers
fun getCityTime(offsetHours: Double): String {
    val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
    val cal = Calendar.getInstance()
    val zoneOffset = cal.get(Calendar.ZONE_OFFSET)
    val dstOffset = cal.get(Calendar.DST_OFFSET)
    val utcTimeMillis = cal.timeInMillis - (zoneOffset + dstOffset)
    val targetTimeMillis = utcTimeMillis + (offsetHours * 3600000).toLong()
    val targetCal = Calendar.getInstance()
    targetCal.timeInMillis = targetTimeMillis
    return sdf.format(targetCal.time)
}
