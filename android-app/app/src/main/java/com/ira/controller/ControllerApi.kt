package com.ira.controller

import android.net.Network
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class ControllerStatus(
    val apSsid: String,
    val apIp: String,
    val apClients: Int,
    val staConnected: Boolean,
    val staIp: String,
    val freeHeap: Long,
    val minFreeHeap: Long,
    val safetyState: String,
    val canBusState: Int,
    val canDriverReady: Boolean,
    val canRx: Long,
    val canErrors: Long,
    val sequenceStatus: String,
    val sequenceRunning: Boolean,
    val runModeActive: Boolean,
    val sequenceDirection: String,
    val stepDurationMs: Long,
    val stepElapsedMs: Long,
    val stepRemainingMs: Long,
    val readyRelayOn: Boolean,
    val physicalResetPressCount: Long,
    val resetButtonPressed: Boolean,
    val controlTaskHealthy: Boolean,
    val canBitrate: Int,
    val uartBaud: Int,
    val canListenOnly: Boolean,
    val uartMode: String,
    val outputs: Map<String, Boolean>,
)

data class AnalyzerEvent(val id: Long, val ms: Long, val source: String, val text: String)

data class AnalyzerSnapshot(
    val canRx: Long,
    val modbusFrames: Long,
    val canErrors: Long,
    val droppedLogs: Long,
    val canLastRxMs: Long,
    val uartLastRxMs: Long,
    val uptimeMs: Long,
    val canStateText: String,
    val events: List<AnalyzerEvent>,
)

class ControllerApi(address: String, private val networkProvider: () -> Network? = { null }) {
    private val baseUrl = "http://${address.trim().trimEnd('/')}/"

    suspend fun readStatus(): ControllerStatus = withContext(Dispatchers.IO) {
        val json = request("api/status")
        val outputsJson = json.optJSONObject("outputs") ?: JSONObject()
        val outputNames = listOf("Brake", "Enable", "Up", "Down", "Speed 0", "Speed 1", "Speed 2")
        ControllerStatus(
            apSsid = json.optString("apSsid", "Repair Center"),
            apIp = json.optString("apIp", "10.10.10.10"),
            apClients = json.optInt("apClients"),
            staConnected = json.optBoolean("staConnected"),
            staIp = json.optString("staIp", ""),
            freeHeap = json.optLong("freeHeap"),
            minFreeHeap = json.optLong("minFreeHeap"),
            safetyState = json.optString("safetyState", "UNKNOWN"),
            canBusState = json.optInt("canBusState"),
            canDriverReady = json.optBoolean("canDriverReady"),
            canRx = json.optLong("canRx"),
            canErrors = json.optLong("canErrors"),
            sequenceStatus = json.optString("sequenceStatus", "Idle"),
            sequenceRunning = json.optBoolean("sequenceRunning"),
            runModeActive = json.optBoolean("runModeActive"),
            sequenceDirection = json.optString("sequenceDirection", ""),
            stepDurationMs = json.optLong("sequenceStepDurationMs"),
            stepElapsedMs = json.optLong("sequenceStepElapsedMs"),
            stepRemainingMs = json.optLong("sequenceStepRemainingMs"),
            readyRelayOn = json.optBoolean("readyRelayOn"),
            physicalResetPressCount = json.optLong("physicalResetPressCount"),
            resetButtonPressed = json.optBoolean("resetButtonPressed"),
            controlTaskHealthy = json.optBoolean("controlTaskHealthy"),
            canBitrate = json.optInt("canBitrate", 500000),
            uartBaud = json.optInt("uartBaud", 115200),
            canListenOnly = json.optBoolean("canListenOnly"),
            uartMode = json.optString("uartMode", "modbus"),
            outputs = outputNames.associateWith { outputsJson.optBoolean(it) },
        )
    }

    suspend fun readTimers(): Map<String, Int> = withContext(Dispatchers.IO) {
        val json = request("api/timers")
        buildMap {
            val keys = listOf("brake", "enable", "direction", "speed0a", "speed1a", "speed2", "speed1b", "speed0b", "dirOff", "enOff", "buzzerInterval", "readyDelay", "runModeHours")
            keys.forEach { key -> put(key, json.optInt(key)) }
        }
    }

    suspend fun sendSequence(command: String) = withContext(Dispatchers.IO) {
        request("api/sequence?cmd=${encode(command)}", method = "POST")
    }

    suspend fun setOutput(name: String, state: Boolean) = withContext(Dispatchers.IO) {
        request("api/output?name=${encode(name)}&state=${if (state) 1 else 0}", method = "POST")
    }

    suspend fun saveTimers(values: Map<String, Int>) = postForm("api/timers", values.mapValues { it.value.toString() })

    suspend fun saveRouterWifi(ssid: String, password: String) = postFormPage("wifi", mapOf("ssid" to ssid, "password" to password))
    suspend fun saveAccessPointWifi(ssid: String, password: String) = postFormPage("apwifi", mapOf("apSsid" to ssid, "apPassword" to password))

    suspend fun readAnalyzer(source: String, since: Long): AnalyzerSnapshot = withContext(Dispatchers.IO) {
        val json = request("api/analyzer?source=${encode(source)}&since=$since")
        val eventsJson = json.optJSONArray("events")
        val events = buildList {
            if (eventsJson != null) for (index in 0 until eventsJson.length()) {
                val event = eventsJson.optJSONObject(index) ?: continue
                add(AnalyzerEvent(event.optLong("id"), event.optLong("ms"), event.optString("source"), event.optString("text")))
            }
        }
        AnalyzerSnapshot(
            canRx = json.optLong("canRx"), modbusFrames = json.optLong("modbusFrames"),
            canErrors = json.optLong("canErrors"), droppedLogs = json.optLong("droppedLogs"),
            canLastRxMs = json.optLong("canLastRxMs"), uartLastRxMs = json.optLong("uartLastRxMs"),
            uptimeMs = json.optLong("uptimeMs"), canStateText = json.optString("canStateText", "Unknown"), events = events,
        )
    }

    suspend fun configureAnalyzer(kind: String, value: String) = postForm("api/analyzer/config", mapOf("kind" to kind, "value" to value))
    suspend fun setUartMode(mode: String) = postForm("api/analyzer/mode", mapOf("mode" to mode))
    suspend fun transmitUart(data: String) = postForm("api/analyzer/tx", mapOf("data" to data))
    suspend fun transmitCan(id: String, type: String, data: String) = postForm(
        "api/analyzer/can-tx", mapOf("armed" to "1", "id" to id, "type" to type, "data" to data),
    )

    private suspend fun postForm(path: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        val connection = openConnection(URL(baseUrl + path)).apply {
            requestMethod = "POST"
            connectTimeout = 2500
            readTimeout = 2500
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val body = values.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = if (responseBody.isBlank()) JSONObject() else JSONObject(responseBody)
            if (code !in 200..299) throw IllegalStateException(response.optString("error", "Controller returned HTTP $code"))
            response
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun postFormPage(path: String, values: Map<String, String>) = withContext(Dispatchers.IO) {
        val connection = openConnection(URL(baseUrl + path)).apply {
            requestMethod = "POST"
            connectTimeout = 4000
            readTimeout = 5000
            useCaches = false
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        try {
            val body = values.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val error = runCatching { JSONObject(responseBody).optString("error") }.getOrNull()
                throw IllegalStateException(error?.takeIf { it.isNotBlank() } ?: "Controller returned HTTP $code")
            }
            responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun request(path: String, method: String = "GET"): JSONObject {
        val connection = openConnection(URL(baseUrl + path)).apply {
            requestMethod = method
            connectTimeout = 2500
            readTimeout = 2500
            useCaches = false
        }
        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val response = if (body.isBlank()) JSONObject() else JSONObject(body)
            if (code !in 200..299) {
                throw IllegalStateException(response.optString("error", "Controller returned HTTP $code"))
            }
            return response
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL): HttpURLConnection {
        val selectedNetwork = networkProvider()
            ?: throw IOException("ESP32 Wi-Fi is not connected. Open Wi-Fi settings and choose the controller access point.")
        return selectedNetwork.openConnection(url) as HttpURLConnection
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
