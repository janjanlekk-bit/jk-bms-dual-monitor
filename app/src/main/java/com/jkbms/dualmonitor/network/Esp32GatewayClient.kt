package com.jkbms.dualmonitor.network

import android.content.Context
import android.util.Log
import com.jkbms.dualmonitor.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class Esp32GatewayClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (b1: BmsData, b2: BmsData, bank: TotalBankData, daily: DailyEnergyRecord) -> Unit
) {
    private val prefs = context.getSharedPreferences("jk_bms_prefs", Context.MODE_PRIVATE)

    private val _gatewayIp = MutableStateFlow(prefs.getString("esp32_ip", "192.168.31.111") ?: "192.168.31.111")
    val gatewayIp = _gatewayIp.asStateFlow()

    private val _isGatewayConnected = MutableStateFlow(false)
    val isGatewayConnected = _isGatewayConnected.asStateFlow()

    private val _blePaused = MutableStateFlow(false)
    val blePaused = _blePaused.asStateFlow()

    private val _bleRemainingSec = MutableStateFlow(0)
    val bleRemainingSec = _bleRemainingSec.asStateFlow()

    private var pollJob: Job? = null

    init {
        startPolling()
    }

    fun setGatewayIp(ip: String) {
        val clean = ip.trim().removePrefix("http://").removeSuffix("/")
        if (clean.isNotBlank()) {
            _gatewayIp.value = clean
            prefs.edit().putString("esp32_ip", clean).apply()
        }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val statusJson = fetchJson("http://${_gatewayIp.value}/api/status")
                    if (statusJson != null) {
                        parseAndDispatch(statusJson)
                        _isGatewayConnected.value = true
                    } else {
                        _isGatewayConnected.value = false
                    }
                } catch (e: Exception) {
                    _isGatewayConnected.value = false
                }
                delay(1200) // Poll every 1.2 seconds
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        _isGatewayConnected.value = false
    }

    private fun parseAndDispatch(jsonStr: String) {
        val root = JSONObject(jsonStr)

        // 1. Bank
        val bankObj = root.optJSONObject("bank")
        val bankData = if (bankObj != null) {
            TotalBankData(
                voltage = bankObj.optDouble("voltage", 0.0).toFloat(),
                current = bankObj.optDouble("current", 0.0).toFloat(),
                power = bankObj.optDouble("power", 0.0).toFloat(),
                capacityWeightedSoc = bankObj.optInt("weightedSoc", 0),
                remainingCapacityAh = bankObj.optDouble("remainingAh", 0.0).toFloat(),
                nominalCapacityAh = bankObj.optDouble("nominalAh", 190.0).toFloat()
            )
        } else {
            TotalBankData()
        }

        val dailyRecord = if (bankObj != null) {
            DailyEnergyRecord(
                chargedAh = bankObj.optDouble("solarChargedAh", 0.0).toFloat(),
                chargedKwh = bankObj.optDouble("solarChargedKwh", 0.0).toFloat(),
                dischargedAh = bankObj.optDouble("loadConsumedAh", 0.0).toFloat(),
                dischargedKwh = bankObj.optDouble("loadConsumedKwh", 0.0).toFloat(),
                minSoc = bankObj.optInt("minSoc", 0),
                maxSoc = bankObj.optInt("maxSoc", 0),
                minAh = bankObj.optDouble("minAh", 0.0).toFloat(),
                maxAh = bankObj.optDouble("maxAh", 0.0).toFloat(),
                lastUpdated = System.currentTimeMillis()
            )
        } else {
            DailyEnergyRecord()
        }

        // 2. Battery 1
        val b1Obj = root.optJSONObject("b1")
        val b1Data = if (b1Obj != null) {
            parseBms(id = "B1", defaultName = "48V 100Ah #1", mac = "C8:47:80:1B:76:00", obj = b1Obj)
        } else {
            BmsData(id = "B1", displayName = "48V 100Ah #1")
        }

        // 3. Battery 2
        val b2Obj = root.optJSONObject("b2")
        val b2Data = if (b2Obj != null) {
            parseBms(id = "B2", defaultName = "48V 100Ah #2", mac = "C8:47:80:1C:14:68", obj = b2Obj)
        } else {
            BmsData(id = "B2", displayName = "48V 100Ah #2")
        }

        // 4. BLE pause status
        val bleObj = root.optJSONObject("ble")
        if (bleObj != null) {
            _blePaused.value = bleObj.optBoolean("paused", false)
            _bleRemainingSec.value = bleObj.optInt("remainingSec", 0)
        } else {
            _blePaused.value = false
            _bleRemainingSec.value = 0
        }

        onDataReceived(b1Data, b2Data, bankData, dailyRecord)
    }

    private fun parseBms(id: String, defaultName: String, mac: String, obj: JSONObject): BmsData {
        val isConn = obj.optBoolean("connected", false)
        val v = obj.optDouble("voltage", 0.0).toFloat()
        val c = obj.optDouble("current", 0.0).toFloat()
        val p = obj.optDouble("power", 0.0).toFloat()
        val soc = obj.optInt("soc", 0)
        val remAh = obj.optDouble("remainingAh", 0.0).toFloat()
        val nomAh = obj.optDouble("nominalAh", 100.0).toFloat()
        val deltaMv = obj.optInt("deltaMv", 0)
        val cycles = obj.optInt("cycles", 0)
        val rawName = obj.optString("name", defaultName)
        val name = rawName.replace(" (24S)", "").replace(" (20S)", "").ifBlank { defaultName }

        val cellsArray = obj.optJSONArray("cells")
        val cellsList = mutableListOf<CellData>()
        if (cellsArray != null) {
            for (i in 0 until cellsArray.length()) {
                cellsList.add(CellData(index = i + 1, voltage = cellsArray.getDouble(i).toFloat()))
            }
        }

        val avgCell = if (cellsList.isNotEmpty()) cellsList.map { it.voltage }.average().toFloat() else 0f
        val minCell = cellsList.minOfOrNull { it.voltage } ?: 0f
        val maxCell = cellsList.maxOfOrNull { it.voltage } ?: 0f
        val minIdx = cellsList.indexOfFirst { it.voltage == minCell }.takeIf { it >= 0 }?.plus(1) ?: 0
        val maxIdx = cellsList.indexOfFirst { it.voltage == maxCell }.takeIf { it >= 0 }?.plus(1) ?: 0

        return BmsData(
            id = id,
            displayName = name,
            macAddress = mac,
            connectionStatus = if (isConn) ConnectionStatus.CONNECTED else ConnectionStatus.DISCONNECTED,
            voltage = v,
            current = c,
            power = p,
            soc = soc,
            remainingCapacityAh = remAh,
            nominalCapacityAh = nomAh,
            totalChargingCycleAh = cycles.toFloat(),
            cells = cellsList,
            averageCellVoltage = avgCell,
            minCellVoltage = minCell,
            maxCellVoltage = maxCell,
            minCellNumber = minIdx,
            maxCellNumber = maxIdx,
            deltaVoltageMv = deltaMv,
            lastUpdate = System.currentTimeMillis()
        )
    }

    suspend fun releaseBle(seconds: Int = 600): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${_gatewayIp.value}/api/ble/release?sec=$seconds")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    suspend fun resumeBle(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("http://${_gatewayIp.value}/api/ble/resume")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.responseCode == 200
        } catch (e: Exception) {
            false
        }
    }

    private fun fetchJson(urlString: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 2500
            conn.readTimeout = 2500
            if (conn.responseCode == 200) {
                BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }
}
