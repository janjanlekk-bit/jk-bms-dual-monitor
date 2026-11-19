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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class GatewayConnectionMode {
    OFFLINE,
    LOCAL_WIFI,
    FIREBASE_CLOUD
}

class Esp32GatewayClient(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onDataReceived: (b1: BmsData, b2: BmsData, bank: TotalBankData, daily: DailyEnergyRecord) -> Unit,
    private val onHistoryReceived: ((yesterday: DailyEnergyRecord) -> Unit)? = null,
    private val onBatchHistoryReceived: ((records: List<DailyEnergyRecord>) -> Unit)? = null
) {
    private val prefs = context.getSharedPreferences("jk_bms_prefs", Context.MODE_PRIVATE)

    private val _gatewayIp = MutableStateFlow(prefs.getString("esp32_ip", "192.168.31.111") ?: "192.168.31.111")
    val gatewayIp = _gatewayIp.asStateFlow()

    private val _firebaseUrl = MutableStateFlow(
        prefs.getString("firebase_url", "https://jkbms-monitor-963f7-default-rtdb.asia-southeast1.firebasedatabase.app")
            ?: "https://jkbms-monitor-963f7-default-rtdb.asia-southeast1.firebasedatabase.app"
    )
    val firebaseUrl = _firebaseUrl.asStateFlow()

    private val _firebaseAuth = MutableStateFlow(
        prefs.getString("firebase_auth", "5VfH1ajdcxr3ohBzIc8pyT13wycrsBN6yzKdT6k0")
            ?: "5VfH1ajdcxr3ohBzIc8pyT13wycrsBN6yzKdT6k0"
    )
    val firebaseAuth = _firebaseAuth.asStateFlow()

    private val _connectionMode = MutableStateFlow(GatewayConnectionMode.OFFLINE)
    val connectionMode = _connectionMode.asStateFlow()

    private val _isGatewayConnected = MutableStateFlow(false)
    val isGatewayConnected = _isGatewayConnected.asStateFlow()

    private val _blePaused = MutableStateFlow(false)
    val blePaused = _blePaused.asStateFlow()

    private val _bleRemainingSec = MutableStateFlow(0)
    val bleRemainingSec = _bleRemainingSec.asStateFlow()

    private val _telemetryTimestamp = MutableStateFlow(0L)
    val telemetryTimestamp = _telemetryTimestamp.asStateFlow()

    private val _uptimeSec = MutableStateFlow(0L)
    val uptimeSec = _uptimeSec.asStateFlow()

    private val _watchdogOfflineSec = MutableStateFlow(0)
    val watchdogOfflineSec = _watchdogOfflineSec.asStateFlow()

    private var pollJob: Job? = null
    private var lastB1: BmsData? = null
    private var lastB2: BmsData? = null
    private var lastBank: TotalBankData? = null
    private var lastDaily: DailyEnergyRecord? = null
    private var consecutiveFailures = 0

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

    fun setFirebaseUrl(url: String) {
        val clean = cleanFirebaseUrl(url)
        if (clean.isNotBlank()) {
            _firebaseUrl.value = clean
            prefs.edit().putString("firebase_url", clean).apply()
        }
    }

    fun setFirebaseAuth(auth: String) {
        val clean = auth.trim()
        _firebaseAuth.value = clean
        prefs.edit().putString("firebase_auth", clean).apply()
    }

    private fun cleanFirebaseUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }

    suspend fun refresh(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                var usedCloud = false
                // 1. Try local Wi-Fi first (fast 1200ms timeout)
                var statusJson = fetchJson("http://${_gatewayIp.value}/api/status", timeoutMs = 1200)

                // 2. If local fails (e.g. mobile data outside), fallback to Firebase Realtime Database
                if (statusJson == null && _firebaseUrl.value.isNotBlank()) {
                    val cloudUrl = "${cleanFirebaseUrl(_firebaseUrl.value)}/telemetry.json"
                    val cloudJson = fetchJson(cloudUrl, timeoutMs = 3500)
                    if (cloudJson != null && cloudJson != "null" && cloudJson.contains("bank")) {
                        statusJson = cloudJson
                        usedCloud = true
                    }
                }

                if (statusJson != null) {
                    consecutiveFailures = 0
                    parseAndDispatch(statusJson)
                    _isGatewayConnected.value = true
                    _connectionMode.value = if (usedCloud) GatewayConnectionMode.FIREBASE_CLOUD else GatewayConnectionMode.LOCAL_WIFI
                    fetchAndDispatchHistory(usedCloud)
                    true
                } else {
                    handleGatewayFailure()
                    false
                }
            } catch (e: Exception) {
                handleGatewayFailure()
                false
            }
        }
    }

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch(Dispatchers.IO) {
            var pollCount = 0
            while (isActive) {
                try {
                    var usedCloud = false
                    // 1. First attempt local Wi-Fi gateway (192.168.31.111) with short timeout (1200ms)
                    var statusJson = fetchJson("http://${_gatewayIp.value}/api/status", timeoutMs = 1200)

                    // 2. If local Wi-Fi unreachable (e.g. outside on cellular data), fallback to Firebase Realtime Database
                    if (statusJson == null && _firebaseUrl.value.isNotBlank()) {
                        val cloudUrl = "${cleanFirebaseUrl(_firebaseUrl.value)}/telemetry.json"
                        val cloudJson = fetchJson(cloudUrl, timeoutMs = 3000)
                        if (cloudJson != null && cloudJson != "null" && cloudJson.contains("bank")) {
                            statusJson = cloudJson
                            usedCloud = true
                        }
                    }

                    if (statusJson != null) {
                        consecutiveFailures = 0
                        parseAndDispatch(statusJson)
                        _isGatewayConnected.value = true
                        _connectionMode.value = if (usedCloud) GatewayConnectionMode.FIREBASE_CLOUD else GatewayConnectionMode.LOCAL_WIFI

                        pollCount++
                        if (pollCount % 15 == 1) {
                            fetchAndDispatchHistory(usedCloud)
                        }
                    } else {
                        handleGatewayFailure()
                    }
                } catch (e: Exception) {
                    handleGatewayFailure()
                }
                delay(1500) // Poll every 1.5 seconds
            }
        }
    }

    private fun fetchAndDispatchHistory(isCloud: Boolean = false) {
        try {
            val histUrl = if (isCloud) {
                "${cleanFirebaseUrl(_firebaseUrl.value)}/history.json"
            } else {
                "http://${_gatewayIp.value}/api/history"
            }
            val histJson = fetchJson(histUrl, timeoutMs = 3000)
            if (histJson != null && histJson != "null") {
                val hRoot = JSONObject(histJson)
                val yObj = hRoot.optJSONObject("yesterday")
                if (yObj != null) {
                    val yDate = yObj.optString("date", "")
                    if (yDate.isNotBlank()) {
                        val yRecord = DailyEnergyRecord(
                            date = yDate,
                            chargedAh = yObj.optDouble("solarChargedAh", 0.0).toFloat(),
                            chargedKwh = yObj.optDouble("solarChargedKwh", 0.0).toFloat(),
                            dischargedAh = yObj.optDouble("loadConsumedAh", 0.0).toFloat(),
                            dischargedKwh = yObj.optDouble("loadConsumedKwh", 0.0).toFloat(),
                            minSoc = yObj.optInt("minSoc", 0),
                            maxSoc = yObj.optInt("maxSoc", 0),
                            minAh = yObj.optDouble("minAh", 0.0).toFloat(),
                            maxAh = yObj.optDouble("maxAh", 0.0).toFloat(),
                            lastUpdated = System.currentTimeMillis()
                        )
                        onHistoryReceived?.invoke(yRecord)
                    }
                }
            }

            // Sync 1-year archive from Firebase Realtime Database
            if (isCloud && _firebaseUrl.value.isNotBlank()) {
                val fullUrl = "${cleanFirebaseUrl(_firebaseUrl.value)}/daily_history.json"
                val fullJson = fetchJson(fullUrl, timeoutMs = 4000)
                if (fullJson != null && fullJson != "null" && fullJson.startsWith("{")) {
                    val fullRoot = JSONObject(fullJson)
                    val list = mutableListOf<DailyEnergyRecord>()
                    val keys = fullRoot.keys()
                    while (keys.hasNext()) {
                        val dateKey = keys.next()
                        val dObj = fullRoot.optJSONObject(dateKey) ?: continue
                        list.add(
                            DailyEnergyRecord(
                                date = dateKey,
                                chargedAh = dObj.optDouble("solarChargedAh", 0.0).toFloat(),
                                chargedKwh = dObj.optDouble("solarChargedKwh", 0.0).toFloat(),
                                dischargedAh = dObj.optDouble("loadConsumedAh", 0.0).toFloat(),
                                dischargedKwh = dObj.optDouble("loadConsumedKwh", 0.0).toFloat(),
                                minSoc = dObj.optInt("minSoc", 0),
                                maxSoc = dObj.optInt("maxSoc", 0),
                                minAh = dObj.optDouble("minAh", 0.0).toFloat(),
                                maxAh = dObj.optDouble("maxAh", 0.0).toFloat(),
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                    if (list.isNotEmpty()) {
                        onBatchHistoryReceived?.invoke(list)
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun handleGatewayFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= 2) {
            _isGatewayConnected.value = false
            _connectionMode.value = GatewayConnectionMode.OFFLINE
            // Mark both BMS slots as DISCONNECTED with 0 current and 0 power so UI updates to OFFLINE
            val offlineB1 = (lastB1 ?: BmsData(id = "B1", displayName = "48V 100Ah #1")).copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                current = 0f,
                power = 0f
            )
            val offlineB2 = (lastB2 ?: BmsData(id = "B2", displayName = "48V 100Ah #2")).copy(
                connectionStatus = ConnectionStatus.DISCONNECTED,
                current = 0f,
                power = 0f
            )
            val offlineBank = (lastBank ?: TotalBankData()).copy(
                current = 0f,
                power = 0f
            )
            onDataReceived(offlineB1, offlineB2, offlineBank, lastDaily ?: DailyEnergyRecord())
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
            val rawNom = bankObj.optDouble("nominalAh", 200.0).toFloat()
            val nomAh = if (rawNom == 190.0f || rawNom <= 10.0f) 200.0f else rawNom
            TotalBankData(
                voltage = bankObj.optDouble("voltage", 0.0).toFloat(),
                current = bankObj.optDouble("current", 0.0).toFloat(),
                power = bankObj.optDouble("power", 0.0).toFloat(),
                capacityWeightedSoc = bankObj.optInt("weightedSoc", 0),
                remainingCapacityAh = bankObj.optDouble("remainingAh", 0.0).toFloat(),
                nominalCapacityAh = nomAh
            )
        } else {
            TotalBankData()
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val dailyRecord = if (bankObj != null) {
            DailyEnergyRecord(
                date = bankObj.optString("date", todayStr).ifBlank { todayStr },
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
            DailyEnergyRecord(date = todayStr)
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

        // 4. Timestamp & Watchdog info
        _telemetryTimestamp.value = root.optLong("timestamp", 0L)
        _uptimeSec.value = root.optLong("uptime", 0L)

        // 5. BLE pause & watchdog status
        val bleObj = root.optJSONObject("ble")
        if (bleObj != null) {
            _blePaused.value = bleObj.optBoolean("paused", false)
            _bleRemainingSec.value = bleObj.optInt("remainingSec", 0)
            _watchdogOfflineSec.value = bleObj.optInt("offlineSec", 0)
        } else {
            _blePaused.value = false
            _bleRemainingSec.value = 0
            _watchdogOfflineSec.value = 0
        }

        lastB1 = b1Data
        lastB2 = b2Data
        lastBank = bankData
        lastDaily = dailyRecord

        onDataReceived(b1Data, b2Data, bankData, dailyRecord)
    }

    private fun parseBms(id: String, defaultName: String, mac: String, obj: JSONObject): BmsData {
        val isConn = obj.optBoolean("connected", false)
        val v = obj.optDouble("voltage", 0.0).toFloat()
        val c = obj.optDouble("current", 0.0).toFloat()
        val p = obj.optDouble("power", 0.0).toFloat()
        val soc = obj.optInt("soc", 0)
        val remAh = obj.optDouble("remainingAh", 0.0).toFloat()
        val rawNomAh = obj.optDouble("nominalAh", 100.0).toFloat()
        val nomAh = if (rawNomAh == 90.0f || rawNomAh <= 10.0f) 100.0f else rawNomAh
        val deltaMv = obj.optInt("deltaMv", 0)
        val cycles = obj.optInt("cycles", 0)
        val rawTempBatt = obj.optDouble("tempBatt", 0.0).toFloat()
        val rawTempMos = obj.optDouble("tempMos", 0.0).toFloat()
        val tempBatt = if (rawTempBatt > -90f && rawTempBatt < 120f) rawTempBatt else 25.0f
        val tempMos = if (rawTempMos > -90f && rawTempMos < 120f) rawTempMos else 28.0f
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
            temperature = tempBatt,
            tempMos = tempMos,
            tempBatt = tempBatt,
            cycleCount = cycles,
            isChargeMosOn = isConn,
            isDischargeMosOn = isConn,
            isBalanceOn = deltaMv > 5,
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

    suspend fun sendFirebaseCommand(action: String, extraJson: String = ""): Boolean = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val authParam = if (_firebaseAuth.value.isNotBlank()) "?auth=${_firebaseAuth.value}" else ""
            val urlStr = "${cleanFirebaseUrl(_firebaseUrl.value)}/command.json$authParam"
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 4000
            conn.readTimeout = 4000

            val payload = if (extraJson.isNotBlank()) {
                """{"action":"$action","timestamp":${System.currentTimeMillis()},$extraJson}"""
            } else {
                """{"action":"$action","timestamp":${System.currentTimeMillis()}}"""
            }
            conn.outputStream.use { os ->
                os.write(payload.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            code in 200..299
        } catch (e: Exception) {
            Log.e("Esp32GatewayClient", "Failed to send Firebase command: $action", e)
            false
        } finally {
            conn?.disconnect()
        }
    }

    suspend fun rebootGateway(): Boolean = withContext(Dispatchers.IO) {
        if (_connectionMode.value == GatewayConnectionMode.FIREBASE_CLOUD) {
            sendFirebaseCommand("reboot")
        } else {
            try {
                val url = URL("http://${_gatewayIp.value}/api/reboot")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) true else sendFirebaseCommand("reboot")
            } catch (e: Exception) {
                sendFirebaseCommand("reboot")
            }
        }
    }

    suspend fun forceReconnectBle(): Boolean = withContext(Dispatchers.IO) {
        if (_connectionMode.value == GatewayConnectionMode.FIREBASE_CLOUD) {
            sendFirebaseCommand("reconnect_ble")
        } else {
            try {
                val url = URL("http://${_gatewayIp.value}/api/ble/resume")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) true else sendFirebaseCommand("reconnect_ble")
            } catch (e: Exception) {
                sendFirebaseCommand("reconnect_ble")
            }
        }
    }

    suspend fun releaseBle(seconds: Int = 600): Boolean = withContext(Dispatchers.IO) {
        if (_connectionMode.value == GatewayConnectionMode.FIREBASE_CLOUD) {
            sendFirebaseCommand("pause_ble", """"sec":$seconds""")
        } else {
            try {
                val url = URL("http://${_gatewayIp.value}/api/ble/release?sec=$seconds")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) true else sendFirebaseCommand("pause_ble", """"sec":$seconds""")
            } catch (e: Exception) {
                sendFirebaseCommand("pause_ble", """"sec":$seconds""")
            }
        }
    }

    suspend fun resumeBle(): Boolean = withContext(Dispatchers.IO) {
        if (_connectionMode.value == GatewayConnectionMode.FIREBASE_CLOUD) {
            sendFirebaseCommand("resume_ble")
        } else {
            try {
                val url = URL("http://${_gatewayIp.value}/api/ble/resume")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                if (conn.responseCode == 200) true else sendFirebaseCommand("resume_ble")
            } catch (e: Exception) {
                sendFirebaseCommand("resume_ble")
            }
        }
    }

    private fun fetchJson(urlString: String, timeoutMs: Int = 1500): String? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(urlString)
            conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
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
