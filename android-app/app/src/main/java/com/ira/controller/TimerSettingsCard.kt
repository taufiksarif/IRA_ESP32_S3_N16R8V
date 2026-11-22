package com.ira.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private data class TimerDefinition(val key: String, val label: String, val max: Int, val unit: String)

private val timerDefinitions = listOf(
    TimerDefinition("brake", "Brake hold", 30, "seconds"),
    TimerDefinition("enable", "Enable delay", 30, "seconds"),
    TimerDefinition("direction", "Direction delay", 30, "seconds"),
    TimerDefinition("speed0a", "Speed 0 (first)", 30, "seconds"),
    TimerDefinition("speed1a", "Speed 1 (first)", 30, "seconds"),
    TimerDefinition("speed2", "Speed 2", 30, "seconds"),
    TimerDefinition("speed1b", "Speed 1 (return)", 30, "seconds"),
    TimerDefinition("speed0b", "Speed 0 (return)", 30, "seconds"),
    TimerDefinition("dirOff", "Direction off delay", 30, "seconds"),
    TimerDefinition("enOff", "Enable off delay", 30, "seconds"),
    TimerDefinition("buzzerInterval", "Buzzer pulse interval", 30, "seconds"),
    TimerDefinition("readyDelay", "Ready signal delay", 600, "seconds"),
    TimerDefinition("runModeHours", "Run Mode duration", 24, "hours"),
)

fun defaultTimerValues(): Map<String, String> = timerDefinitions.associate { definition ->
    definition.key to when (definition.key) {
        "readyDelay" -> "30"
        "runModeHours" -> "1"
        else -> "2"
    }
}

fun timerValuesFromApi(values: Map<String, Int>): Map<String, String> = timerDefinitions.associate { definition ->
    definition.key to (values[definition.key] ?: defaultTimerValues()[definition.key] ?: "1").toString()
}

fun parseTimerValues(values: Map<String, String>): Map<String, Int>? {
    val parsed = mutableMapOf<String, Int>()
    for (definition in timerDefinitions) {
        val value = values[definition.key]?.toIntOrNull() ?: return null
        if (value !in 1..definition.max) return null
        parsed[definition.key] = value
    }
    return parsed
}

@Composable
fun TimerSettingsCard(
    values: Map<String, String>,
    onValueChange: (String, String) -> Unit,
    onSave: () -> Unit,
    onReload: () -> Unit,
    message: String,
    enabled: Boolean,
    busy: Boolean,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    TimerPanel {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Sequence timer settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Tap to ${if (expanded) "collapse" else "edit timers"}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("These values are saved on the ESP32 and used by Auto UP, Auto DOWN, and Run Mode.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                timerDefinitions.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        pair.forEach { definition ->
                            Column(Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = values[definition.key].orEmpty(),
                                    onValueChange = { input -> onValueChange(definition.key, input.filter(Char::isDigit).take(3)) },
                                    enabled = enabled && !busy,
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(definition.label, maxLines = 1) },
                                    supportingText = { Text("1–${definition.max} ${definition.unit}") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                )
                            }
                        }
                        if (pair.size == 1) Column(Modifier.weight(1f)) { }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Button(onClick = onSave, enabled = enabled && !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "Saving…" else "Save timers") }
                    Button(onClick = onReload, enabled = enabled && !busy, modifier = Modifier.weight(1f)) { Text("Reload saved") }
                }
            }
        }
    }
}

@Composable
private fun TimerPanel(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
