package com.ira.controller

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnalyzerScreen(
    api: ControllerApi,
    initialStatus: ControllerStatus?,
    connected: Boolean,
    isForeground: Boolean,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var source by remember { mutableStateOf("can") }
    var paused by remember { mutableStateOf(false) }
    var hexView by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("") }
    var events by remember { mutableStateOf(emptyList<AnalyzerEvent>()) }
    var recorded by remember { mutableStateOf(emptyList<AnalyzerEvent>()) }
    var cursors by remember { mutableStateOf(mapOf("can" to 0L, "serial" to 0L)) }
    var stats by remember { mutableStateOf<AnalyzerSnapshot?>(null) }
    var info by remember { mutableStateOf("CAN and UART receive logs are polled from the controller.") }
    var canRate by remember { mutableStateOf(initialStatus?.canBitrate ?: 500000) }
    var canListen by remember { mutableStateOf(initialStatus?.canListenOnly ?: false) }
    var uartRate by remember { mutableStateOf(initialStatus?.uartBaud ?: 115200) }
    var uartMode by remember { mutableStateOf(initialStatus?.uartMode ?: "modbus") }
    var canId by remember { mutableStateOf("123") }
    var canType by remember { mutableStateOf("std") }
    var canData by remember { mutableStateOf("11 22 33 44") }
    var canArmed by remember { mutableStateOf(false) }
    var uartText by remember { mutableStateOf("") }
    var appendNewline by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(api, source, paused, connected, isForeground) {
        if (!connected) {
            info = "Controller disconnected. Connect to its Wi-Fi and return to the dashboard to reload."
            return@LaunchedEffect
        }
        if (!isForeground) {
            info = "Analyzer polling paused while the app is in the background."
            return@LaunchedEffect
        }
        if (paused) return@LaunchedEffect
        while (true) {
            try {
                val snapshot = api.readAnalyzer(source, cursors[source] ?: 0L)
                stats = snapshot
                if (snapshot.events.isNotEmpty()) {
                    cursors = cursors + (source to maxOf(cursors[source] ?: 0L, snapshot.events.maxOf { it.id }))
                    events = (events + snapshot.events).distinctBy { it.id }.takeLast(300)
                    if (recording) recorded = (recorded + snapshot.events).takeLast(2000)
                }
            } catch (exception: Exception) {
                info = exception.message ?: "Analyzer endpoint unavailable. Check the controller connection."
            }
            delay(750)
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(contentPadding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PanelCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Protocol Analyzer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Buffered receive monitor · event times are milliseconds since ESP32 boot", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                TactileButton("Back", TactileStyle.SEQUENCE, onClick = onBack)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyzerStat("CAN RX", stats?.canRx?.toString() ?: "—", Modifier.weight(1f))
                AnalyzerStat("Modbus RX", stats?.modbusFrames?.toString() ?: "—", Modifier.weight(1f))
                AnalyzerStat("CAN errors", stats?.canErrors?.toString() ?: "—", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnalyzerStat("CAN bus", stats?.canStateText ?: "—", Modifier.weight(1f))
                AnalyzerStat("Dropped logs", stats?.droppedLogs?.toString() ?: "—", Modifier.weight(1f))
                val lastRx = if (source == "can") stats?.canLastRxMs else stats?.uartLastRxMs
                val age = if (lastRx == null || lastRx == 0L) "Never" else "${((stats?.uptimeMs ?: 0L) - lastRx).coerceAtLeast(0L) / 1000}s ago"
                AnalyzerStat("Last RX", age, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TactileButton("CAN Console", TactileStyle.SEQUENCE, enabled = source != "can", modifier = Modifier.weight(1f)) { source = "can" }
                TactileButton("UART / Modbus", TactileStyle.SEQUENCE, enabled = source != "serial", modifier = Modifier.weight(1f)) { source = "serial" }
            }
            if (!connected) {
                Text("Connect to the Repair Center Wi-Fi to view live events or change analyzer settings.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (source == "can") CanAnalyzerSettings(
            rate = canRate, onRate = { canRate = it }, listenOnly = canListen, onListenOnly = { value ->
                canListen = value
                scope.launch {
                    runAnalyzerAction({ api.configureAnalyzer("canmode", if (value) "1" else "0") }, { info = "CAN listen-only ${if (value) "enabled" else "disabled"}." }, { info = it }, { busy = it })
                }
            },
            canId = canId, onCanId = { canId = it }, canType = canType, onCanType = { canType = it },
            canData = canData, onCanData = { canData = it }, armed = canArmed, onArmed = { canArmed = it },
            busy = busy, connected = connected,
            onApplyRate = { value -> scope.launch { runAnalyzerAction({ api.configureAnalyzer("can", value.toString()) }, { info = "CAN bitrate saved. Configure every bus node to the same rate."; canRate = value }, { info = it }, { busy = it }) } },
            onTransmit = { scope.launch { runAnalyzerAction({ api.transmitCan(canId, canType, canData) }, { info = "CAN frame queued for transmission."; canArmed = false }, { info = it }, { busy = it }) } },
        ) else UartAnalyzerSettings(
            rate = uartRate, onRate = { uartRate = it }, mode = uartMode, onMode = { uartMode = it },
            text = uartText, onText = { uartText = it }, appendNewline = appendNewline, onAppendNewline = { appendNewline = it },
            busy = busy, connected = connected,
            onApplyRate = { value -> scope.launch { runAnalyzerAction({ api.configureAnalyzer("serial", value.toString()) }, { info = "UART baud rate updated. Match the connected device baud rate." }, { info = it }, { busy = it }) } },
            onApplyMode = { value -> scope.launch { runAnalyzerAction({ api.setUartMode(value) }, { uartMode = value; info = "UART mode set to $value." }, { info = it }, { busy = it }) } },
            onTransmit = {
                val data = uartText + if (appendNewline) "\r\n" else ""
                scope.launch { runAnalyzerAction({ api.transmitUart(data) }, { info = "UART text transmitted."; uartText = "" }, { info = it }, { busy = it }) }
            },
        )

        PanelCard {
            Text("Receive log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = paused, onCheckedChange = { paused = it })
                    Text("Pause display", modifier = Modifier.padding(top = 12.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Checkbox(checked = hexView, onCheckedChange = { hexView = it })
                    Text("Hex view", modifier = Modifier.padding(top = 12.dp))
                }
            }
            OutlinedTextField(filter, { filter = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Filter text or CAN ID") }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TactileButton(if (recording) "Stop recording" else "Record", if (recording) TactileStyle.STOP else TactileStyle.TOOL, modifier = Modifier.weight(1f)) {
                    recording = !recording
                    if (recording) recorded = emptyList()
                }
                TactileButton("Clear display", TactileStyle.TOOL, modifier = Modifier.weight(1f)) { events = emptyList() }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                TactileButton("Share TXT", TactileStyle.TOOL, modifier = Modifier.weight(1f)) { shareLog(context, (if (recorded.isNotEmpty()) recorded else events).filter { it.source == source }, source, false) }
                TactileButton("Share CSV", TactileStyle.TOOL, modifier = Modifier.weight(1f)) { shareLog(context, (if (recorded.isNotEmpty()) recorded else events).filter { it.source == source }, source, true) }
            }
            Text(if (recording) "Recording on this phone (up to 2,000 events)." else info, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            val shown = events.asReversed().filter { it.source == source && (filter.isBlank() || it.text.contains(filter, true)) }.take(150).asReversed()
            Column(
                Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 440.dp)
                    .background(Color(0xFF020A07), RoundedCornerShape(14.dp)).padding(12.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (shown.isEmpty()) Text("Waiting for ${if (source == "can") "CAN frames" else "UART bytes"}…", color = Color(0xFFB7F7CD), fontFamily = FontFamily.Monospace)
                shown.forEach { event ->
                    val message = if (!hexView && event.source == "serial" && event.text.startsWith("RX HEX: ")) hexToAscii(event.text.removePrefix("RX HEX: ")) else event.text
                    Text("+${event.ms} ms  ${if (source == "can") "CAN" else "UART"}  $message", color = if (source == "can") Color(0xFF7DC8FF) else Color(0xFFB7F7CD), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("CAN transmission requires explicit arming and is intended for controlled bench use. Listen-only mode blocks transmission. UART text transmission is available only in Raw UART mode.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun CanAnalyzerSettings(
    rate: Int, onRate: (Int) -> Unit, listenOnly: Boolean, onListenOnly: (Boolean) -> Unit,
    canId: String, onCanId: (String) -> Unit, canType: String, onCanType: (String) -> Unit,
    canData: String, onCanData: (String) -> Unit, armed: Boolean, onArmed: (Boolean) -> Unit,
    busy: Boolean, connected: Boolean, onApplyRate: (Int) -> Unit, onTransmit: () -> Unit,
) {
    PanelCard {
        Text("CAN settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        ChoiceField("CAN bitrate", rate.toString(), listOf(125000, 250000, 500000, 1000000).map { it.toString() to "${it / 1000} kbit/s" }, { onRate(it.toInt()) })
        TactileButton("Apply CAN bitrate", TactileStyle.SEQUENCE, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { onApplyRate(rate) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Listen-only", fontWeight = FontWeight.SemiBold)
                Text("No ACK and no CAN transmission", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = listenOnly, onCheckedChange = onListenOnly, enabled = connected && !busy)
        }
        Text("CAN transmit · controlled bench testing only", fontWeight = FontWeight.Bold)
        OutlinedTextField(canId, onCanId, modifier = Modifier.fillMaxWidth(), label = { Text("CAN ID (hex)") }, singleLine = true)
        ChoiceField("Frame type", canType, listOf("std" to "Standard (11-bit)", "ext" to "Extended (29-bit)"), onCanType)
        OutlinedTextField(canData, onCanData, modifier = Modifier.fillMaxWidth(), label = { Text("Payload hex, 0–8 bytes") }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Arm CAN transmission", modifier = Modifier.padding(top = 12.dp))
            Switch(checked = armed, onCheckedChange = onArmed, enabled = connected && !busy && !listenOnly)
        }
        TactileButton("Transmit CAN frame", TactileStyle.SEQUENCE, enabled = connected && armed && !busy && !listenOnly, modifier = Modifier.fillMaxWidth(), onClick = onTransmit)
    }
}

@Composable
private fun UartAnalyzerSettings(
    rate: Int, onRate: (Int) -> Unit, mode: String, onMode: (String) -> Unit,
    text: String, onText: (String) -> Unit, appendNewline: Boolean, onAppendNewline: (Boolean) -> Unit,
    busy: Boolean, connected: Boolean, onApplyRate: (Int) -> Unit, onApplyMode: (String) -> Unit, onTransmit: () -> Unit,
) {
    PanelCard {
        Text("UART / Modbus settings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        val baudRates = listOf(1200, 2400, 4800, 9600, 19200, 38400, 57600, 115200, 230400, 460800, 921600)
        ChoiceField("UART baud rate", rate.toString(), baudRates.map { it.toString() to it.toString() }, { onRate(it.toInt()) })
        TactileButton("Apply UART baud", TactileStyle.SEQUENCE, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { onApplyRate(rate) }
        ChoiceField("UART2 mode", mode, listOf("modbus" to "Modbus RTU slave", "monitor" to "Passive Modbus Monitor", "raw" to "Raw UART text"), onMode)
        TactileButton("Apply UART mode", TactileStyle.SEQUENCE, enabled = connected && !busy, modifier = Modifier.fillMaxWidth()) { onApplyMode(mode) }
        OutlinedTextField(text, onText, modifier = Modifier.fillMaxWidth(), label = { Text("Transmit text") }, enabled = mode == "raw", singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Append CR/LF", modifier = Modifier.padding(top = 12.dp))
            Switch(checked = appendNewline, onCheckedChange = onAppendNewline, enabled = mode == "raw")
        }
        TactileButton("Transmit UART text", TactileStyle.SEQUENCE, enabled = connected && mode == "raw" && text.isNotBlank() && !busy, modifier = Modifier.fillMaxWidth(), onClick = onTransmit)
        Text("Modbus slave ID is 1. Passive monitor mode observes traffic without replying.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ChoiceField(label: String, selected: String, choices: List<Pair<String, String>>, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        TactileButton(choices.firstOrNull { it.first == selected }?.second ?: selected, TactileStyle.TOOL, modifier = Modifier.fillMaxWidth()) { expanded = true }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            choices.forEach { (value, title) -> DropdownMenuItem(text = { Text(title) }, onClick = { onSelected(value); expanded = false }) }
        }
    }
}

@Composable
private fun AnalyzerStat(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(9.dp)) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun PanelCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
        content = { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = content) },
    )
}

private suspend fun runAnalyzerAction(
    action: suspend () -> Any?,
    onSuccess: () -> Unit,
    onError: (String) -> Unit,
    setBusy: (Boolean) -> Unit,
) {
    setBusy(true)
    try { action(); onSuccess() } catch (exception: Exception) { onError(exception.message ?: "Analyzer command failed") }
    finally { setBusy(false) }
}

private fun hexToAscii(hex: String): String = hex.trim().split(Regex("\\s+")).mapNotNull { token ->
    token.toIntOrNull(16)?.let { value -> if (value in 32..126) value.toChar() else '.' }
}.joinToString("")

private fun shareLog(context: android.content.Context, events: List<AnalyzerEvent>, source: String, csv: Boolean) {
    val content = if (csv) {
        (listOf("timestamp_ms,source,message") + events.map { event ->
            listOf(event.ms.toString(), event.source, event.text).joinToString(",") { "\"${it.replace("\"", "\"\"")}\"" }
        }).joinToString("\r\n")
    } else events.joinToString("\n") { "${it.ms} ms [${it.source}] ${it.text}" }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (csv) "text/csv" else "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "IRA $source analyzer log")
        putExtra(Intent.EXTRA_TEXT, content)
    }
    context.startActivity(Intent.createChooser(intent, "Share $source analyzer log"))
}
