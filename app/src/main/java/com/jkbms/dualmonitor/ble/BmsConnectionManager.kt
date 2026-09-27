package com.jkbms.dualmonitor.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.jkbms.dualmonitor.model.TotalBankData
import com.jkbms.dualmonitor.protocol.Jk02Protocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

data class DiscoveredDevice(
    val name: String,
    val address: String,
    val rssi: Int
)

class BleScanner(private val context: Context) {
    private val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val rawName = device.name ?: result.scanRecord?.deviceName ?: ""
            val address = device.address ?: return
            val rssi = result.rssi

            // Match JK BMS by advertised name, service UUID, or C8:47:80 MAC prefix
            val serviceUuids = result.scanRecord?.serviceUuids?.map { it.uuid.toString().lowercase() } ?: emptyList()
            val hasJkService = serviceUuids.any { it.contains("ffe0") }
            val isJkBms = rawName.contains("JK", ignoreCase = true) ||
                          rawName.contains("BMS", ignoreCase = true) ||
                          rawName.contains("BD6A", ignoreCase = true) ||
                          rawName.contains("B2A", ignoreCase = true) ||
                          rawName.contains("100ah", ignoreCase = true) ||
                          rawName.contains("48V", ignoreCase = true) ||
                          rawName.contains("24V", ignoreCase = true) ||
                          hasJkService ||
                          address.startsWith("C8:47:80", ignoreCase = true)

            // Strictly filter out non-JK BLE devices (e.g. Beyond 2, Ultra3, TVs, etc.)
            if (!isJkBms) return

            val displayName = when {
                rawName.isNotBlank() -> rawName
                address.equals("C8:47:80:1C:14:68", ignoreCase = true) -> "48V 100ah #2"
                address.equals("C8:47:80:1B:76:00", ignoreCase = true) -> "48V 100ah #1"
                else -> "JK BMS ($address)"
            }

            val current = _devices.value.toMutableList()
            val index = current.indexOfFirst { it.address.equals(address, ignoreCase = true) }
            val item = DiscoveredDevice(displayName, address, rssi)

            if (index >= 0) {
                current[index] = item
            } else {
                current.add(item)
            }
            _devices.value = current.sortedByDescending { it.rssi }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan(clearExisting: Boolean = false) {
        val leScanner = scanner ?: return

        // If a scan was already running, stop it first to ensure a fresh cycle
        if (_isScanning.value) {
            try {
                leScanner.stopScan(scanCallback)
            } catch (e: Exception) {}
            _isScanning.value = false
        }

        if (clearExisting) {
            _devices.value = emptyList()
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .build()

        try {
            // Null filter so hardware filter doesn't drop packets while connected to peripheral 1
            leScanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
        } catch (e: Exception) {
            _isScanning.value = false
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        if (!_isScanning.value) return
        try {
            scanner?.stopScan(scanCallback)
        } catch (e: Exception) {}
        _isScanning.value = false
    }
}

class BmsConnectionManager(context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val prefs = context.getSharedPreferences("jk_bms_prefs", Context.MODE_PRIVATE)

    val bms1 = BmsConnection("B1", context, scope)
    val bms2 = BmsConnection("B2", context, scope)

    init {
        // Automatically restore and connect previously paired BMS devices
        val savedB1 = prefs.getString("b1_mac", null)
        val savedB2 = prefs.getString("b2_mac", null)
        if (!savedB1.isNullOrBlank()) {
            bms1.connect(savedB1)
        }
        if (!savedB2.isNullOrBlank()) {
            bms2.connect(savedB2)
        }
    }

    fun assignB1(address: String) {
        val clean = address.trim().uppercase()
        if (clean.isBlank()) return
        prefs.edit().putString("b1_mac", clean).apply()
        // Prevent assigning the same physical BMS to both slots
        if (bms2.bmsState.value.macAddress.equals(clean, ignoreCase = true)) {
            prefs.edit().remove("b2_mac").apply()
            bms2.disconnect()
        }
        bms1.connect(clean)
    }

    fun assignB2(address: String) {
        val clean = address.trim().uppercase()
        if (clean.isBlank()) return
        prefs.edit().putString("b2_mac", clean).apply()
        // Prevent assigning the same physical BMS to both slots
        if (bms1.bmsState.value.macAddress.equals(clean, ignoreCase = true)) {
            prefs.edit().remove("b1_mac").apply()
            bms1.disconnect()
        }
        bms2.connect(clean)
    }

    val totalBankState: StateFlow<TotalBankData> = combine(bms1.bmsState, bms2.bmsState) { b1, b2 ->
        val activeBmsList = listOf(b1, b2).filter { it.voltage > 1.0f }

        val totalCurrent = activeBmsList.sumOf { it.current.toDouble() }.toFloat()
        val totalPower = activeBmsList.sumOf { it.power.toDouble() }.toFloat()
        val avgVoltage = if (activeBmsList.isNotEmpty()) activeBmsList.map { it.voltage }.average().toFloat() else 0f

        val totalCap = activeBmsList.sumOf { it.remainingCapacityAh.toDouble() }.toFloat()
        val weightedSoc = if (totalCap > 0f) {
            (activeBmsList.sumOf { (it.remainingCapacityAh * it.soc).toDouble() } / totalCap).toInt()
        } else if (activeBmsList.isNotEmpty()) {
            activeBmsList.map { it.soc }.average().toInt()
        } else {
            0
        }

        TotalBankData(
            voltage = avgVoltage,
            current = totalCurrent,
            power = totalPower,
            capacityWeightedSoc = weightedSoc.coerceIn(0, 100),
            remainingCapacityAh = totalCap
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), TotalBankData())
}
