package com.ira.controller

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Bundle
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DeepGreen = Color(0xFF041B13)
private val PanelGreen = Color(0xFF0D3625)
private val PanelDark = Color(0xFF051C14)
private val LineGreen = Color(0xFF285B43)
private val MutedGreen = Color(0xFFA7CBB2)
private val TextGreen = Color(0xFFEAFFF1)
private val BlueTop = Color(0xFF68C5FF)
private val BlueMid = Color(0xFF2588F4)
private val BlueBottom = Color(0xFF1553C7)
private val RedTop = Color(0xFFFF858B)
private val RedMid = Color(0xFFF04452)
private val RedBottom = Color(0xFFB91C32)
private val YellowTop = Color(0xFFFFF1A1)
private val YellowMid = Color(0xFFFFD43B)
private val YellowBottom = Color(0xFFE7A900)
private val ActiveGreen = Color(0xFF37D878)

private data class QueuedToast(val message: String, val warning: Boolean = false)

class MainActivity : ComponentActivity() {
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private var appInForeground by mutableStateOf(false)
    private var controllerWifiNetwork by mutableStateOf<Network?>(null)
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IraNativeApp(
                isForeground = appInForeground,
                networkProvider = { controllerWifiNetwork },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        appInForeground = true
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                controllerWifiNetwork = network
            }

            override fun onLost(network: Network) {
                if (controllerWifiNetwork == network) controllerWifiNetwork = null
            }

            override fun onUnavailable() {
                controllerWifiNetwork = null
            }
        }
        try {
            connectivityManager.requestNetwork(request, callback)
            wifiNetworkCallback = callback
        } catch (_: SecurityException) {
            controllerWifiNetwork = null
        }
    }

    override fun onStop() {
        appInForeground = false
        wifiNetworkCallback?.let { callback ->
            runCatching { connectivityManager.unregisterNetworkCallback(callback) }
        }
        wifiNetworkCallback = null
        controllerWifiNetwork = null
        super.onStop()
    }
}

@Composable
private fun IraNativeApp(isForeground: Boolean, networkProvider: () -> Network?) {
    val context = LocalContext.current
    val preferences = remember { context.getSharedPreferences("ira-app", 0) }
    var address by rememberSaveable { mutableStateOf(preferences.getString("controller-address", "10.10.10.10") ?: "10.10.10.10") }
    var activeAddress by rememberSaveable { mutableStateOf(address) }
    var lightTheme by remember { mutableStateOf(preferences.getBoolean("light-theme", false)) }
    var demoMode by rememberSaveable { mutableStateOf(false) }
    var liveStatus by remember { mutableStateOf<ControllerStatus?>(null) }
    var demoStatus by remember { mutableStateOf(createDemoStatus()) }
    var connectionError by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var runModeHours by remember { mutableStateOf(1) }
    var timersLoaded by remember { mutableStateOf(false) }
    var timerValues by remember { mutableStateOf(defaultTimerValues()) }
    var timerMessage by remember { mutableStateOf("Connect to the controller to load saved timer settings.") }
    var timerBusy by remember { mutableStateOf(false) }
    var showAnalyzer by rememberSaveable { mutableStateOf(false) }
    val api = remember(activeAddress) { ControllerApi(activeAddress, networkProvider) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val toastQueue = remember { Channel<QueuedToast>(Channel.UNLIMITED) }
    var visibleToast by remember { mutableStateOf<QueuedToast?>(null) }
    val isConnected = liveStatus != null && connectionError == null
    val displayedStatus = if (demoMode) demoStatus else liveStatus
    val colors = if (lightTheme) {
        lightColorScheme(
            primary = Color(0xFF2588F4), secondary = Color(0xFF16834D),
            background = Color(0xFFDCEFE1), surface = Color(0xFFF8FFF9),
            surfaceVariant = Color(0xFFE6F5E9), onBackground = Color(0xFF173727),
            onSurface = Color(0xFF173727), onSurfaceVariant = Color(0xFF507560),
            outline = Color(0xFFB6D9C1),
        )
    } else {
        darkColorScheme(
            primary = BlueMid, secondary = ActiveGreen, background = DeepGreen,
            surface = PanelGreen, surfaceVariant = Color(0xFF082219),
            onBackground = TextGreen,
            onSurface = TextGreen,
            onSurfaceVariant = MutedGreen, outline = LineGreen,
        )
    }

    fun toast(message: String, warning: Boolean = false) {
        toastQueue.trySend(QueuedToast(message, warning))
    }

    fun beginRealSequence(command: String) {
        scope.launch {
            sendSequence(command, api, { busy = it }, ::toast) { liveStatus = api.readStatus() }
        }
    }

    fun applyManualOutput(name: String, state: Boolean) {
        scope.launch {
            busy = true
            try {
                api.setOutput(name, state)
                liveStatus = api.readStatus()
            } catch (exception: Exception) {
                toast(exception.message ?: "Output command rejected", true)
            } finally { busy = false }
        }
    }

    LaunchedEffect(toastQueue) {
        for (next in toastQueue) {
            visibleToast = next
            delay(1700)
            visibleToast = null
            delay(350)
        }
    }

    LaunchedEffect(api, demoMode, isForeground) {
        if (!demoMode && isForeground) {
            var previousSta: Boolean? = null
            var previousReady: Boolean? = null
            var previousResetCount: Long? = null
            while (true) {
                try {
                    val fresh = api.readStatus()
                    liveStatus = fresh
                    connectionError = null
                    lastUpdated = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                    if (previousSta != null && previousSta != fresh.staConnected) {
                        toast(if (fresh.staConnected) "Router Wi-Fi connected" else "Router Wi-Fi disconnected", !fresh.staConnected)
                    }
                    if (previousReady == false && fresh.readyRelayOn) toast("Ready signal is ON")
                    if (previousResetCount?.let { fresh.physicalResetPressCount > it } == true) {
                        toast("Physical reset button pressed", true)
                    }
                    previousSta = fresh.staConnected
                    previousReady = fresh.readyRelayOn
                    previousResetCount = fresh.physicalResetPressCount
                    if (!timersLoaded) {
                        runCatching { api.readTimers() }.onSuccess { saved ->
                            timerValues = timerValuesFromApi(saved)
                            runModeHours = saved["runModeHours"] ?: 1
                            timerMessage = "Loaded saved timer settings from the controller."
                        }.onFailure { exception ->
                            timerMessage = exception.message ?: "Could not load timer settings."
                        }
                        timersLoaded = true
                    }
                } catch (exception: Exception) {
                    connectionError = exception.message ?: "Controller not reachable"
                }
                delay(if (liveStatus?.sequenceRunning == true) 500L else 5000L)
            }
        }
    }

    MaterialTheme(colorScheme = colors) {
        Box(Modifier.fillMaxSize().background(
            if (lightTheme) Brush.linearGradient(listOf(Color(0xFFDCEFE1), Color(0xFFF8FFF9)))
            else Brush.linearGradient(listOf(DeepGreen, Color(0xFF0A2A1D), DeepGreen)),
        )) {
            Scaffold(
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    TactileButton(
                        text = "Stop sequence / Run Mode",
                        style = TactileStyle.STOP,
                        enabled = demoMode || isConnected,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
                        cornerRadius = 16.dp,
                    ) {
                        if (demoMode) {
                            demoStatus = createDemoStatus()
                            toast("Stop command sent", true)
                        } else beginRealSequence("stop")
                    }
                },
            ) { contentPadding ->
                if (showAnalyzer) {
                    AnalyzerScreen(
                        api = api,
                        initialStatus = liveStatus,
                        connected = isConnected,
                        isForeground = isForeground,
                        contentPadding = contentPadding,
                        onBack = { showAnalyzer = false },
                    )
                } else Column(
                    modifier = Modifier.fillMaxSize().padding(contentPadding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Header(lightTheme = lightTheme, onToggleTheme = {
                        lightTheme = !lightTheme
                        preferences.edit().putBoolean("light-theme", lightTheme).apply()
                    })

                    SafetyCard(
                        status = displayedStatus,
                        offline = !isConnected && !demoMode,
                        demoMode = demoMode,
                        onOpenAnalyzer = { showAnalyzer = true },
                    )
                    MetricsGrid(status = displayedStatus)

                    ConnectionLine(
                        connected = isConnected,
                        demoMode = demoMode,
                        error = connectionError,
                        lastUpdated = lastUpdated,
                    )

                    if (!isConnected) {
                        ConnectionCard(
                            address = address,
                            onAddressChange = { address = it },
                            error = connectionError,
                            onReconnect = {
                                activeAddress = address.trim().ifBlank { "10.10.10.10" }
                                preferences.edit().putString("controller-address", activeAddress).apply()
                                liveStatus = null
                                connectionError = null
                                timersLoaded = false
                            },
                            demoMode = demoMode,
                            onDemoChange = { demoMode = it },
                        )
                    }

                    WifiSettingsCard(
                        api = api,
                        status = liveStatus,
                        enabled = isConnected && !demoMode,
                        onNotice = ::toast,
                    )

                    ControlsCard(
                        status = displayedStatus,
                        enabled = demoMode || isConnected,
                        busy = busy,
                        runModeHours = runModeHours,
                        onOutput = { name, value ->
                            if (demoMode) {
                                val updated = demoStatus.outputs.toMutableMap().apply { put(name, value) }
                                demoStatus = demoStatus.copy(outputs = updated)
                            } else applyManualOutput(name, value)
                        },
                        onSequence = { command ->
                            if (demoMode) {
                                demoStatus = when (command) {
                                    "stop" -> createDemoStatus().copy(sequenceStatus = "Stopped — outputs OFF")
                                    "run" -> demoStatus.copy(sequenceRunning = true, runModeActive = true, safetyState = "RUNNING", sequenceStatus = "Run Mode: Auto UP", sequenceDirection = "UP", stepDurationMs = 2000, stepElapsedMs = 300, stepRemainingMs = 1700)
                                    "down" -> demoStatus.copy(sequenceRunning = true, runModeActive = false, safetyState = "RUNNING", sequenceStatus = "Auto DOWN: Brake hold", sequenceDirection = "DOWN", stepDurationMs = 2000, stepElapsedMs = 300, stepRemainingMs = 1700)
                                    else -> demoStatus.copy(sequenceRunning = true, runModeActive = false, safetyState = "RUNNING", sequenceStatus = "Auto UP: Brake hold", sequenceDirection = "UP", stepDurationMs = 2000, stepElapsedMs = 300, stepRemainingMs = 1700)
                                }
                                toast(if (command == "run") "Run Mode accepted" else if (command == "down") "Auto DOWN accepted" else "Auto UP accepted")
                            } else beginRealSequence(command)
                        },
                    )

                    TimerSettingsCard(
                        values = timerValues,
                        onValueChange = { key, value -> timerValues = timerValues + (key to value) },
                        onSave = {
                            val parsed = parseTimerValues(timerValues)
                            if (parsed == null) timerMessage = "Enter valid whole-number values within each displayed range."
                            else scope.launch {
                                timerBusy = true
                                try {
                                    api.saveTimers(parsed)
                                    runModeHours = parsed["runModeHours"] ?: runModeHours
                                    timerMessage = "Timer settings saved to the ESP32."
                                    toast("Sequence timers saved")
                                } catch (exception: Exception) {
                                    timerMessage = exception.message ?: "Could not save timer settings."
                                    toast(timerMessage, true)
                                } finally { timerBusy = false }
                            }
                        },
                        onReload = {
                            scope.launch {
                                timerBusy = true
                                try {
                                    val saved = api.readTimers()
                                    timerValues = timerValuesFromApi(saved)
                                    runModeHours = saved["runModeHours"] ?: 1
                                    timerMessage = "Reloaded saved timer settings from the ESP32."
                                } catch (exception: Exception) {
                                    timerMessage = exception.message ?: "Could not reload timer settings."
                                } finally { timerBusy = false }
                            }
                        },
                        message = if (!isConnected) "Connect to the controller to load or save timer settings. $timerMessage" else timerMessage,
                        enabled = isConnected && !demoMode,
                        busy = timerBusy,
                    )

                    SequenceProgress(status = displayedStatus)
                    ReadyInfoCard(status = displayedStatus)
                    if (displayedStatus != null) DiagnosticsCard(status = displayedStatus)
                    Spacer(Modifier.height(8.dp))
                }
            }

            AnimatedVisibility(
                visible = visibleToast != null,
                modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                enter = slideInVertically { -it } + fadeIn(),
                exit = slideOutVertically { -it } + fadeOut(),
            ) {
                ToastCard(visibleToast)
            }
        }
    }
}

@Composable
private fun Header(lightTheme: Boolean, onToggleTheme: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
            Box(
                Modifier.width(40.dp).height(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF17683E), Color(0xFF0A3322))))
                    .border(1.dp, Color(0xFF48B878), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Text("⌁", color = Color(0xFFD6FFE3), fontSize = 26.sp, fontWeight = FontWeight.Bold) }
            Column {
                Text(
                    "Repair Center",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("I.R.A · Interactive Relay Automation", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        TactileButton(
            text = if (lightTheme) "🌙 Dark" else "☀️ Light",
            style = TactileStyle.TOOL,
            modifier = Modifier.width(106.dp),
            onClick = onToggleTheme,
        )
    }
}

@Composable
private fun SafetyCard(status: ControllerStatus?, offline: Boolean, demoMode: Boolean, onOpenAnalyzer: () -> Unit) {
    val state = if (demoMode) "DEMO" else status?.safetyState ?: "UNKNOWN"
    val color = when (state) {
        "READY" -> ActiveGreen
        "RUNNING" -> Color(0xFFE8B322)
        "FAULT" -> Color(0xFFF04452)
        "DEMO" -> Color(0xFFB56BFF)
        else -> Color(0xFFE8B322)
    }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(PanelGreen, PanelDark)))
            .border(1.dp, LineGreen, RoundedCornerShape(18.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(32.dp).height(32.dp).clip(CircleShape).background(color.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(if (state == "FAULT") "!" else if (state == "READY") "✓" else "●", color = color, fontWeight = FontWeight.Black, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(if (offline) "Controller state · offline" else "Controller state", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status?.runModeActive == true) Text("RUN MODE", color = Color(0xFFFFD43B), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            Text(state, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
        }
        TactileButton(
            text = "Analyzer",
            style = TactileStyle.SEQUENCE,
            modifier = Modifier.width(88.dp),
            cornerRadius = 999.dp,
            minimumHeight = 38.dp,
            horizontalPadding = 10.dp,
            verticalPadding = 7.dp,
            onClick = onOpenAnalyzer,
        )
    }
}

@Composable
private fun MetricsGrid(status: ControllerStatus?) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Wi-Fi STA", status?.let { if (it.staConnected) "Connected" else "Not connected" } ?: "Checking…", if (status?.staConnected == true) "good" else "warn", "⌁", Modifier.weight(1f))
            val current = (status?.freeHeap ?: 0L) / 1024f
            val minimum = (status?.minFreeHeap ?: 0L) / 1024f
            MetricCard("Free RAM / minimum", if (status == null) "—" else "%.1f / %.1f KB".format(Locale.getDefault(), current, minimum), "normal", "▦", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Access point IP", status?.apIp ?: "10.10.10.10", "good", "⌖", Modifier.weight(1f))
            MetricCard("Clients", status?.let { "${it.apClients} connected" } ?: "—", if ((status?.apClients ?: 0) > 0) "good" else "normal", "◎", Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, tone: String, glyph: String, modifier: Modifier = Modifier) {
    val glyphColor = when (tone) {
        "good" -> ActiveGreen
        "warn" -> Color(0xFFE8B322)
        "bad" -> Color(0xFFF04452)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier.clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp)).padding(horizontal = 10.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.width(28.dp).height(28.dp).clip(CircleShape).background(glyphColor.copy(alpha = .16f)), contentAlignment = Alignment.Center) {
            Text(glyph, color = glyphColor, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConnectionLine(connected: Boolean, demoMode: Boolean, error: String?, lastUpdated: String?) {
    val color = if (demoMode) Color(0xFFB56BFF) else if (connected) ActiveGreen else Color(0xFFF04452)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(start = 3.dp)) {
        Box(Modifier.width(8.dp).height(8.dp).clip(CircleShape).background(color))
        Text(
            when {
                demoMode -> "Demo preview · no hardware commands are sent"
                connected -> "Live · updated ${lastUpdated ?: "just now"}"
                else -> error?.let { "Connection lost · retrying" } ?: "Connecting to controller…"
            },
            color = if (!connected && !demoMode) MaterialTheme.colorScheme.onSurfaceVariant else color,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun ConnectionCard(
    address: String,
    onAddressChange: (String) -> Unit,
    error: String?,
    onReconnect: () -> Unit,
    demoMode: Boolean,
    onDemoChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    PanelCard {
        Text("Connect to your controller", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            if (demoMode) "Preview mode is active. Commands stay on this phone and do not reach hardware."
            else error ?: "Connect your phone to the Repair Center Wi-Fi.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (!demoMode) Text(
            "This Wi-Fi has no internet, so Android may switch to mobile data. In Wi-Fi settings, choose the controller network and select Stay connected / Use this network if prompted. The app sends controller traffic over the Wi-Fi connection.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = address, onValueChange = onAddressChange, modifier = Modifier.weight(1f), label = { Text("Controller IP") }, singleLine = true)
            TactileButton(if (error == null) "Connect" else "Retry", TactileStyle.SEQUENCE, modifier = Modifier.width(96.dp), onClick = onReconnect)
        }
        TactileButton(
            "Open Wi-Fi settings", TactileStyle.TOOL, modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
        ) {
            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Preview dashboard", fontWeight = FontWeight.SemiBold)
                Text("Try the interface without hardware; actions stay on this phone.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = demoMode, onCheckedChange = onDemoChange)
        }
    }
}

@Composable
private fun ControlsCard(
    status: ControllerStatus?,
    enabled: Boolean,
    busy: Boolean,
    runModeHours: Int,
    onOutput: (String, Boolean) -> Unit,
    onSequence: (String) -> Unit,
) {
    PanelCard {
        Text("Controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Tap an output to toggle it. Run Mode alternates Auto UP and Auto DOWN for the configured duration.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        val names = listOf("Brake", "Enable", "Up", "Down", "Speed 0", "Speed 1", "Speed 2")
        names.chunked(2).forEach { rowNames ->
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
                rowNames.forEach { name ->
                    val isOn = status?.outputs?.get(name) == true
                    TactileButton(
                        text = "$name${if (isOn) " · ON" else ""}",
                        style = if (isOn) TactileStyle.ON else TactileStyle.NEUTRAL,
                        enabled = enabled && !busy && status?.sequenceRunning != true,
                        modifier = Modifier.weight(1f),
                    ) { onOutput(name, !isOn) }
                }
                if (rowNames.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        TactileButton(
            text = "Run Mode ($runModeHours ${if (runModeHours == 1) "hour" else "hours"})",
            style = TactileStyle.RUN,
            enabled = enabled && !busy && status?.sequenceRunning != true,
            modifier = Modifier.fillMaxWidth(),
        ) { onSequence("run") }
        if (status?.sequenceRunning == true) {
            Text("Output controls are locked during an automatic sequence. Press Stop to abort.", color = Color(0xFFE8B322), style = MaterialTheme.typography.bodySmall)
        }
        Text("Automatic sequences", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
            TactileButton("Auto UP", TactileStyle.SEQUENCE, enabled && !busy && status?.sequenceRunning != true, Modifier.weight(1f)) { onSequence("up") }
            TactileButton("Auto DOWN", TactileStyle.SEQUENCE, enabled && !busy && status?.sequenceRunning != true, Modifier.weight(1f)) { onSequence("down") }
        }
    }
}

@Composable
private fun SequenceProgress(status: ControllerStatus?) {
    val remaining = status?.stepRemainingMs ?: 0L
    val duration = status?.stepDurationMs ?: 0L
    val elapsed = status?.stepElapsedMs ?: 0L
    val progress = if (duration > 0) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f
    PanelCard {
        Text("Sequence progress", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (status?.sequenceRunning == true) status.sequenceStatus else "Waiting for sequence", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (status?.sequenceRunning == true) "${(remaining + 999) / 1000} s left" else "—", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        }
        Box(Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(LineGreen)) {
            Box(Modifier.fillMaxWidth(if (status?.sequenceRunning == true) progress else 0f).height(8.dp).background(Brush.horizontalGradient(listOf(Color(0xFF16834D), Color(0xFF4EE58A)))))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Direction: ${status?.sequenceDirection?.ifBlank { "—" } ?: "—"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            Text(if (status?.sequenceRunning == true) "Elapsed ${elapsed / 1000} s of ${(duration + 999) / 1000} s" else "Progress appears while running", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ReadyInfoCard(status: ControllerStatus?) {
    PanelCard {
        Text("Ready signal & buzzer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("The Ready relay activates after its configured delay while the ready button is held. AP clients trigger two beeps; after Run Mode, the buzzer pulses until Stop is pressed.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Ready relay", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (status?.readyRelayOn == true) "ON" else "OFF", color = if (status?.readyRelayOn == true) ActiveGreen else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiagnosticsCard(status: ControllerStatus) {
    PanelCard {
        Text("Controller health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthTile("CAN bus", when (status.canBusState) { 1 -> "Running"; 2 -> "BUS-OFF"; 3 -> "Recovering"; else -> "Unavailable" }, Modifier.weight(1f))
            HealthTile("CAN RX / errors", "${status.canRx} / ${status.canErrors}", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HealthTile("Control task", if (status.controlTaskHealthy) "Running" else "Heartbeat stale", Modifier.weight(1f))
            HealthTile("Reset button", if (status.resetButtonPressed) "Pressed" else "Released", Modifier.weight(1f))
        }
        Text("Software status only · not a safety certification", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HealthTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(13.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(13.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp), content = content)
    }
}

internal enum class TactileStyle { NEUTRAL, ON, SEQUENCE, RUN, STOP, TOOL }

@Composable
internal fun TactileButton(
    text: String,
    style: TactileStyle,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 999.dp,
    minimumHeight: Dp = 46.dp,
    horizontalPadding: Dp = 13.dp,
    verticalPadding: Dp = 11.dp,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressDepth by animateFloatAsState(
        targetValue = if (pressed && enabled) 1f else 0f,
        animationSpec = spring(stiffness = 900f),
        label = "buttonPressDepth",
    )
    val view: View = LocalView.current
    val shape = RoundedCornerShape(cornerRadius)
    val palette = when (style) {
        TactileStyle.ON -> Triple(listOf(Color(0xFFFF777C), RedMid, Color(0xFFBD1F35)), Color(0xFFFF9CA1), Color.White)
        TactileStyle.SEQUENCE -> Triple(listOf(BlueTop, BlueMid, BlueBottom), Color(0xFF9BDCFF), Color.White)
        TactileStyle.RUN -> Triple(listOf(YellowTop, YellowMid, YellowBottom), Color(0xFFFFF0A0), Color(0xFF342400))
        TactileStyle.STOP -> Triple(listOf(RedTop, RedMid, RedBottom), Color(0xFFFFB0B3), Color.White)
        TactileStyle.TOOL -> Triple(listOf(PanelGreen, PanelDark), LineGreen, MaterialTheme.colorScheme.onSurface)
        TactileStyle.NEUTRAL -> Triple(listOf(Color(0xFF40516B), Color(0xFF27364D), Color(0xFF1C293E)), Color(0xFF53667F), Color.White)
    }
    LaunchedEffect(pressed) {
        if (pressed) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
    Box(
        modifier = modifier.heightIn(min = minimumHeight)
            .graphicsLayer {
                translationY = 4.dp.toPx() * pressDepth
                val scale = 1f - .025f * pressDepth
                scaleX = scale
                scaleY = scale
            }
            .shadow(5.dp - 4.dp * pressDepth, shape)
            .clip(shape)
            .background(Brush.verticalGradient(palette.first))
            .border(1.dp, palette.second, shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = palette.third.copy(alpha = if (enabled) 1f else .55f), fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToastCard(toast: QueuedToast?) {
    if (toast == null) return
    val border = if (toast.warning) Color(0xFFE8B322).copy(alpha = .65f) else Color(0xFF7FEBB1).copy(alpha = .42f)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, border),
        colors = CardDefaults.cardColors(containerColor = if (MaterialTheme.colorScheme.background == Color(0xFFDCEFE1)) Color(0xFFF7FFF9) else Color(0xFF113627)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
    ) {
        Row(Modifier.padding(horizontal = 15.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(23.dp).height(23.dp).clip(CircleShape).background(if (toast.warning) Color(0xFFFFC42B).copy(alpha = .2f) else ActiveGreen.copy(alpha = .2f)), contentAlignment = Alignment.Center) {
                Text(if (toast.warning) "!" else "✓", color = if (toast.warning) Color(0xFFFFD34F) else Color(0xFF64ED9A), fontWeight = FontWeight.Black)
            }
            Text(toast.message, color = if (MaterialTheme.colorScheme.background == Color(0xFFDCEFE1)) Color(0xFF173727) else TextGreen, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private suspend fun sendSequence(
    command: String,
    api: ControllerApi,
    setBusy: (Boolean) -> Unit,
    toast: (String, Boolean) -> Unit,
    refresh: suspend () -> Unit,
) {
    setBusy(true)
    try {
        api.sendSequence(command)
        toast(when (command) { "up" -> "Auto UP accepted"; "down" -> "Auto DOWN accepted"; "run" -> "Run Mode accepted"; else -> "Stop command sent" }, command == "stop")
        refresh()
    } catch (exception: Exception) {
        toast(exception.message ?: "Sequence command rejected", true)
    } finally {
        setBusy(false)
    }
}

private fun createDemoStatus(): ControllerStatus = ControllerStatus(
    apSsid = "Repair Center", apIp = "10.10.10.10", apClients = 1,
    staConnected = false, staIp = "", freeHeap = 172_400, minFreeHeap = 154_800,
    safetyState = "READY", canBusState = 1, canDriverReady = true, canRx = 128, canErrors = 0,
    sequenceStatus = "Idle", sequenceRunning = false, runModeActive = false,
    sequenceDirection = "", stepDurationMs = 0, stepElapsedMs = 0, stepRemainingMs = 0,
    readyRelayOn = false, physicalResetPressCount = 0, resetButtonPressed = false,
    controlTaskHealthy = true, canBitrate = 500000, uartBaud = 115200,
    canListenOnly = false, uartMode = "modbus",
    outputs = listOf("Brake", "Enable", "Up", "Down", "Speed 0", "Speed 1", "Speed 2").associateWith { false },
)
