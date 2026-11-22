package com.ira.controller

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun WifiSettingsCard(
    api: ControllerApi,
    status: ControllerStatus?,
    enabled: Boolean,
    onNotice: (String, Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var apSsid by rememberSaveable(status?.apSsid) { mutableStateOf(status?.apSsid ?: "Repair Center") }
    var apPassword by remember { mutableStateOf("") }
    var routerSsid by rememberSaveable { mutableStateOf("") }
    var routerPassword by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Wi-Fi credentials are sent to the controller and are not saved on this phone.") }
    val scope = rememberCoroutineScope()

    WifiPanel {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi configuration", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Configure the ESP32 access point or optional router connection", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        AnimatedVisibility(expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("ESP32 access point", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Saving these settings restarts the access point. Reconnect your phone using the new name and password.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    apSsid, { apSsid = it.take(32) }, enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Access point name") }, singleLine = true,
                )
                OutlinedTextField(
                    apPassword, { apPassword = it.take(63) }, enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("New access point password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    supportingText = { Text("Enter the password to use after restart (8–63 characters). Required by the controller.") }, singleLine = true,
                )
                TactileButton(
                    "Save & restart access point", TactileStyle.SEQUENCE,
                    enabled = enabled && !busy && apSsid.isNotBlank() && (apPassword.isBlank() || apPassword.length >= 8),
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
                ) {
                    scope.launch {
                        busy = true
                        try {
                            if (apPassword.isBlank()) {
                                message = "Enter the access point password to use after restart. The controller does not send saved passwords to the app."
                            } else {
                                api.saveAccessPointWifi(apSsid.trim(), apPassword)
                                apPassword = ""
                                message = "Access point settings saved. Reconnect to $apSsid using the new password."
                                onNotice("Access point restarted; reconnect to $apSsid", false)
                            }
                        } catch (exception: Exception) {
                            message = exception.message ?: "Could not confirm the access point restart. If it restarted, reconnect using the new access point name and password."
                            onNotice(message, true)
                        } finally { busy = false }
                    }
                }

                Text("Router connection (optional)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                Text("This lets the ESP32 join a router while keeping its own access point available. Leave both fields blank to clear saved router credentials.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    routerSsid, { routerSsid = it.take(32) }, enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Router Wi-Fi name") }, singleLine = true,
                )
                OutlinedTextField(
                    routerPassword, { routerPassword = it.take(64) }, enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(), label = { Text("Router password") },
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), singleLine = true,
                )
                TactileButton(
                    "Save router connection", TactileStyle.SEQUENCE,
                    enabled = enabled && !busy && (routerSsid.isBlank() || routerPassword.length <= 64),
                    modifier = Modifier.fillMaxWidth(), cornerRadius = 14.dp,
                ) {
                    scope.launch {
                        busy = true
                        try {
                            api.saveRouterWifi(routerSsid.trim(), routerPassword)
                            routerPassword = ""
                            message = if (routerSsid.isBlank()) "Saved router credentials cleared." else "Router settings saved. The ESP32 will reconnect while its access point remains available."
                            onNotice(message, false)
                        } catch (exception: Exception) {
                            message = exception.message ?: "Could not save router settings."
                            onNotice(message, true)
                        } finally { busy = false }
                    }
                }
                if (!enabled) Text("Connect to the controller before changing Wi-Fi settings.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun WifiPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}
